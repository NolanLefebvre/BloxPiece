package nolan.bloxpiece.haki;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import nolan.bloxpiece.handcuffs.Handcuffs;
import nolan.bloxpiece.network.CooldownPayload;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Le système de Haki : les trois Hakis de base (HakiType), leurs touches (UseHakiPayload), leurs recharges (affichées par le
 * HUD client avec les mêmes clés que les attaques de fruit), le déblocage (attachment HAKI_UNLOCKED : tout est débloqué dès
 * l'arrivée du joueur dans cette première version) et les règles communes : impossible menotté ou évanoui, coupé à la mort.
 * Chaque Haki gère son propre effet (Haki).
 */
public final class HakiManager {

    public static final int ALL_UNLOCKED = 0b111;

    private static final Map<HakiType, Haki> HAKIS = new EnumMap<>(HakiType.class);
    private static final Map<UUID, Map<HakiType, Long>> COOLDOWNS = new HashMap<>();

    static {
        HAKIS.put(HakiType.ARMAMENT, new ArmamentHaki());
        HAKIS.put(HakiType.OBSERVATION, new ObservationHaki());
        HAKIS.put(HakiType.CONQUEROR, new ConquerorHaki());
    }

    private HakiManager() {
    }

    public static Haki get(HakiType type) {
        return HAKIS.get(type);
    }

    /** Aucun Haki n'est débloqué par défaut : ils se débloquent un par un (commande /haki unlock, plus tard des quêtes). */
    public static boolean isUnlocked(LivingEntity player, HakiType type) {
        Integer bits = player.getAttached(ModAttachments.HAKI_UNLOCKED);
        return bits != null && (bits & type.bit()) != 0;
    }

    /**
     * Débloque (ou reverrouille) un Haki. L'attachment est renvoyé tout de suite au joueur : son HUD et sa page Haki se mettent
     * à jour d'eux-mêmes. Reverrouillé : le Haki est coupé s'il était actif. Renvoie false si rien n'a changé.
     */
    public static boolean setUnlocked(ServerPlayer player, HakiType type, boolean unlocked) {
        Integer bits = player.getAttached(ModAttachments.HAKI_UNLOCKED);
        int before = bits == null ? 0 : bits;
        int after = unlocked ? before | type.bit() : before & ~type.bit();
        if (after == before) {
            return false;
        }
        if (!unlocked && get(type).isActive(player)) {
            get(type).stop(player);
        }
        player.setAttached(ModAttachments.HAKI_UNLOCKED, after);
        return true;
    }

    /** Touche d'un Haki appuyée ({@code slot} = ordre de HakiType). Tout est vérifié ici, côté serveur. */
    public static void press(ServerPlayer player, int slot) {
        HakiType type = HakiType.bySlot(slot);
        if (type == null || !player.isAlive() || player.isSpectator() || !isUnlocked(player, type)) {
            return;
        }
        if (Unconscious.is(player)) {
            return;
        }
        if (Handcuffs.isCuffed(player)) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.haki.cuffed"), true);
            return;
        }
        Haki haki = get(type);
        boolean wasActive = haki.isActive(player);
        if (!wasActive && onCooldown(player, type)) {
            return; // le temps restant est affiché dans le HUD
        }
        if (!wasActive && !HakiEnergy.canActivate(player, type)) {
            return; // jauge de Haki trop basse
        }
        haki.press(player);
        if (!wasActive && haki.isActive(player)) {
            HakiEnergy.onActivated(player, type);
            player.sendSystemMessage(Component.translatable(type.nameKey()), true); // nom du Haki dans la barre d'action
        }
    }

    /**
     * Touche maintenue (le client mesure le maintien et envoie l'action HOLD) : seul l'Armement s'en sert (corps entier).
     * Mêmes conditions qu'un appui si le Haki n'était pas encore actif.
     */
    public static void hold(ServerPlayer player, int slot) {
        HakiType type = HakiType.bySlot(slot);
        if (type != HakiType.ARMAMENT || !player.isAlive() || player.isSpectator() || !isUnlocked(player, type)
                || Unconscious.is(player) || Handcuffs.isCuffed(player)) {
            return;
        }
        ArmamentHaki armament = (ArmamentHaki) get(type);
        boolean wasActive = armament.isActive(player);
        if (!wasActive && (onCooldown(player, type) || !HakiEnergy.canActivate(player, type))) {
            return;
        }
        armament.hold(player);
        if (!wasActive && armament.isActive(player)) {
            HakiEnergy.onActivated(player, type);
            player.sendSystemMessage(Component.translatable(type.nameKey()), true);
        }
    }

    public static void release(ServerPlayer player, int slot) {
        HakiType type = HakiType.bySlot(slot);
        if (type != null) {
            get(type).release(player);
        }
    }

    public static boolean onCooldown(ServerPlayer player, HakiType type) {
        Map<HakiType, Long> map = COOLDOWNS.get(player.getUUID());
        return map != null && player.level().getGameTime() < map.getOrDefault(type, 0L);
    }

    public static void startCooldown(ServerPlayer player, HakiType type, int ticks) {
        COOLDOWNS.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).put(type, player.level().getGameTime() + ticks);
        ServerPlayNetworking.send(player, new CooldownPayload(type.cooldownKey(), ticks));
    }

    /**
     * Remet à zéro les recharges des TROIS Hakis (Armement, Observation, Rois) et prévient le HUD du joueur pour chacun.
     * Renvoie le nombre de Hakis qui étaient réellement en recharge. Utilisé par /bloxpiece resetcooldowns.
     */
    public static int clearCooldowns(ServerPlayer player) {
        Map<HakiType, Long> removed = COOLDOWNS.remove(player.getUUID());
        long now = player.level().getGameTime();
        int active = 0;
        for (HakiType type : HakiType.values()) {
            Long readyAt = removed == null ? null : removed.get(type);
            if (readyAt != null && readyAt > now) {
                active++;
            }
            ServerPlayNetworking.send(player, new CooldownPayload(type.cooldownKey(), 0));
        }
        return active;
    }

    /** Coupe tous les Hakis actifs (menottes, évanouissement, mort, déconnexion). */
    public static void stopAll(ServerPlayer player) {
        for (Haki haki : HAKIS.values()) {
            if (haki.isActive(player)) {
                haki.stop(player);
            }
        }
    }

    /** Multiplicateur des dégâts subis (Armement sur tout le corps). Appelé par LivingEntityMixin. */
    public static float incomingDamageMultiplier(LivingEntity target, DamageSource source) {
        return ArmamentHaki.mode(target) == ArmamentHaki.FULL_BODY ? ArmamentHaki.FULL_BODY_DAMAGE_TAKEN : 1.0F;
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean blocked = Handcuffs.isCuffed(player) || Unconscious.is(player) || !player.isAlive();
            for (Haki haki : HAKIS.values()) {
                if (blocked && haki.isActive(player)) {
                    haki.stop(player);
                }
                haki.tick(player);
            }
            HakiEnergy.tick(player, HAKIS); // jauge : consommation des Hakis actifs, ou remplissage
            nolan.bloxpiece.job.Jobs.tick(player); // job : bonus des poings du Bagarreur
        }
        Unconscious.tick(server);
    }

    private static void syncCooldowns(ServerPlayer player) {
        Map<HakiType, Long> map = COOLDOWNS.get(player.getUUID());
        if (map == null) {
            return;
        }
        long now = player.level().getGameTime();
        map.forEach((type, readyAt) -> {
            if (readyAt > now) {
                ServerPlayNetworking.send(player, new CooldownPayload(type.cooldownKey(), (int) (readyAt - now)));
            }
        });
    }

    public static void initialize() {
        Unconscious.initialize();
        ServerTickEvents.END_SERVER_TICK.register(HakiManager::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player.getAttached(ModAttachments.HAKI_UNLOCKED) == null) {
                player.setAttached(ModAttachments.HAKI_UNLOCKED, 0); // nouveau joueur : aucun Haki (à débloquer)
            }
            syncCooldowns(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            stopAll(handler.getPlayer());
            HakiEnergy.forget(handler.getPlayer());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            stopAll(newPlayer);
            HakiEnergy.forget(oldPlayer);
            HakiEnergy.fill(newPlayer); // jauge pleine à la réapparition
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            COOLDOWNS.clear();
            Unconscious.clear();
            HakiEnergy.clear();
        });
        // Observation : esquive des attaques
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player
                    && ((ObservationHaki) get(HakiType.OBSERVATION)).tryDodge(player, source, amount)) {
                return false;
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                stopAll(player);
            }
        });
    }
}
