package nolan.bloxpiece.haki;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import nolan.bloxpiece.registry.ModAttachments;

/**
 * Jauge de Haki (énergie). Tant qu'un Haki est actif, elle se vide en continu ; plusieurs Hakis actifs EN MÊME TEMPS la vident
 * plus vite que la somme de chacun (chaque Haki en plus augmente la consommation de tous de {@link #STACK_BONUS}) :
 * 1 Haki = x1, 2 Hakis = x3 (2 x 1,5), 3 Hakis = x6 (3 x 2). À zéro, tous les Hakis se coupent et une courte recharge démarre.
 * Elle se remplit toute seule {@link #REGEN_DELAY} ticks après le dernier Haki actif. Le Haki des Rois coûte en plus
 * {@link #CONQUEROR_COST} d'un coup à l'activation.
 *
 * La valeur exacte (décimale) vit ici côté serveur ; l'attachment HAKI_ENERGY (entier) n'est renvoyé au client que lorsqu'il
 * change, pour le HUD.
 */
public final class HakiEnergy {

    public static final int MAX = 1000;

    /** Un seul Haki de poids 1 vide la jauge pleine en 40 s. */
    private static final float BASE_DRAIN = MAX / (40.0F * 20.0F);
    /** +50 % de consommation par Haki actif en plus. */
    private static final float STACK_BONUS = 0.5F;
    /** Jauge vide -> pleine en 25 s. */
    private static final float REGEN = MAX / (25.0F * 20.0F);
    private static final int REGEN_DELAY = 40;

    /** Minimum pour activer un Haki (5 %). */
    public static final int MIN_TO_ACTIVATE = 50;
    /** Coût immédiat du Haki des Rois (25 %). */
    public static final int CONQUEROR_COST = 250;
    /** Recharge de chaque Haki coupé par une jauge vide. */
    private static final int EXHAUSTED_COOLDOWN = 20 * 5;

    private static final Map<UUID, Float> VALUE = new HashMap<>();
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();

    private HakiEnergy() {
    }

    public static float get(ServerPlayer player) {
        Float cached = VALUE.get(player.getUUID());
        if (cached != null) {
            return cached;
        }
        Integer saved = player.getAttached(ModAttachments.HAKI_ENERGY);
        float v = saved == null ? (float) MAX : (float) Math.max(0, Math.min(MAX, saved.intValue()));
        VALUE.put(player.getUUID(), v);
        return v;
    }

    private static void set(ServerPlayer player, float value) {
        float v = Math.max(0.0F, Math.min(MAX, value));
        VALUE.put(player.getUUID(), v);
        int rounded = (int) Math.ceil(v);
        Integer synced = player.getAttached(ModAttachments.HAKI_ENERGY);
        if (synced == null || synced != rounded) {
            player.setAttached(ModAttachments.HAKI_ENERGY, rounded);
        }
    }

    public static void fill(ServerPlayer player) {
        LAST_USE.remove(player.getUUID());
        set(player, MAX);
    }

    /** Assez d'énergie pour activer ce Haki ? Sinon, message dans la barre d'action. */
    public static boolean canActivate(ServerPlayer player, HakiType type) {
        int needed = type == HakiType.CONQUEROR ? CONQUEROR_COST : MIN_TO_ACTIVATE;
        if (get(player) >= needed) {
            return true;
        }
        player.sendSystemMessage(Component.translatable("message.bloxpiece.haki.no_energy"), true);
        return false;
    }

    /** Appelé juste après l'activation d'un Haki. */
    public static void onActivated(ServerPlayer player, HakiType type) {
        LAST_USE.put(player.getUUID(), player.level().getGameTime());
        if (type == HakiType.CONQUEROR) {
            set(player, get(player) - CONQUEROR_COST);
        }
    }

    /** Poids de chaque Haki actif (le corps entier d'Armement coûte plus que le bras seul). */
    private static float weight(ServerPlayer player, HakiType type) {
        if (type == HakiType.ARMAMENT) {
            return ArmamentHaki.mode(player) == ArmamentHaki.FULL_BODY ? 1.4F : 1.0F;
        }
        return 1.0F;
    }

    /** Une fois par tick serveur et par joueur, après les Hakis. */
    static void tick(ServerPlayer player, Map<HakiType, Haki> hakis) {
        int count = 0;
        float weights = 0.0F;
        for (Haki haki : hakis.values()) {
            if (haki.isActive(player)) {
                count++;
                weights += weight(player, haki.type());
            }
        }
        long now = player.level().getGameTime();
        float energy = get(player);
        if (count > 0) {
            LAST_USE.put(player.getUUID(), now);
            float drain = BASE_DRAIN * weights * (1.0F + STACK_BONUS * (count - 1));
            energy -= drain;
            set(player, energy);
            if (energy <= 0.0F) {
                exhaust(player, hakis);
            }
            return;
        }
        Long last = LAST_USE.get(player.getUUID());
        if (energy < MAX && (last == null || now - last >= REGEN_DELAY)) {
            set(player, energy + REGEN);
        }
    }

    private static void exhaust(ServerPlayer player, Map<HakiType, Haki> hakis) {
        for (Haki haki : hakis.values()) {
            if (haki.isActive(player)) {
                haki.stop(player);
                HakiManager.startCooldown(player, haki.type(), EXHAUSTED_COOLDOWN);
            }
        }
        player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 0.8F, 0.7F);
        player.sendSystemMessage(Component.translatable("message.bloxpiece.haki.exhausted"), true);
    }

    static void forget(ServerPlayer player) {
        VALUE.remove(player.getUUID());
        LAST_USE.remove(player.getUUID());
    }

    static void clear() {
        VALUE.clear();
        LAST_USE.clear();
    }
}
