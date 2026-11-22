package nolan.bloxpiece.race;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModItems;

/**
 * Système de races côté SERVEUR (le seul qui décide) : attribution à la première connexion, bonus passifs, reroll.
 * <ul>
 *   <li>Première connexion : un joueur sans race enregistrée en reçoit une, tirée selon les chances de Races, UNE seule fois. Elle
 *       est gardée avec le joueur (attachment RACE : sauvegardé, conservé à la mort, synchronisé avec son client), donc après une
 *       déconnexion ou un redémarrage.</li>
 *   <li>Bonus d'attributs : chaque bonus de la race est un modificateur d'attribut À PART (« bloxpiece:race/&lt;clé du bonus&gt; »),
 *       qui s'ajoute aux autres (armes, Haki, fruits, niveau, objets) : changer de race retire seulement ces modificateurs-là.
 *       Ils sont recalculés depuis la race enregistrée à la connexion, à la réapparition, au reroll et chaque seconde
 *       (idempotent : rien ne change s'ils sont déjà bons).</li>
 *   <li>Bonus spéciaux : respiration sous l'eau (Fishman), armure en plus quand ses flammes sont actives (Lunarian, voir
 *       {@link #flamesActive}), poison et faim qui s'estompent plus vite (Cyborg).</li>
 *   <li>Reroll (bouton du menu Race, RaceRerollPayload) : il faut un Race Reroll Token dans l'inventaire ; la race est tirée ICI
 *       (20 % chacune), un seul token est consommé, la race et les bonus sont appliqués, PUIS le résultat est envoyé au client
 *       (RaceRollResultPayload) qui joue la roulette jusqu'à elle. Une demande toutes les {@link #REROLL_COOLDOWN} ticks au plus :
 *       un double clic ou un clic pendant la roulette ne consomme jamais un deuxième token.</li>
 * </ul>
 */
public final class RaceManager {

    /**
     * Délai minimal entre deux rerolls d'un même joueur (ticks) : couvre toute la roulette du menu ; double clic, clics pendant
     * l'animation ou paquets répétés sont ignorés (aucun token consommé).
     */
    private static final int REROLL_COOLDOWN = 100;
    /** Durée de la roulette du menu (ticks) : le message du chat arrive après la révélation. */
    private static final int REVEAL_DELAY = 80;
    private static final Map<UUID, Object[]> PENDING_MESSAGE = new HashMap<>();
    private static final Map<UUID, Long> LAST_REROLL = new HashMap<>();
    /** Modificateur d'armure du bonus « flammes actives » (Lunarian). */
    private static final Identifier FLAME_GUARD_ID = BloxPiece.id("race/flame_guard");

    private RaceManager() {
    }

    // ------------------------------------------------------------------ attribution

    /** Première connexion : tire et enregistre une race si le joueur n'en a pas encore. */
    public static void ensureRace(ServerPlayer player) {
        if (Races.hasRace(player)) {
            return; // déjà une race : jamais réattribuée automatiquement
        }
        Race race = Races.roll(player.getRandom(), null);
        player.setAttached(ModAttachments.RACE, race.id());
        player.sendSystemMessage(Component.translatable("message.bloxpiece.race.assigned",
                Component.translatable(race.translationKey()).withColor(race.color())).withStyle(ChatFormatting.GOLD));
    }

    // ------------------------------------------------------------------ bonus

    /** Identifiant du modificateur d'un bonus de race (« bloxpiece:race/<clé du bonus> »). */
    private static Identifier modifierId(RaceBonus bonus) {
        return BloxPiece.id("race/" + bonus.key());
    }

    /** Tous les modificateurs que les races peuvent poser (pour retirer ceux de l'ancienne race) : identifiant -> attribut. */
    private static Map<Identifier, Holder<Attribute>> raceModifiers() {
        Map<Identifier, Holder<Attribute>> map = new LinkedHashMap<>();
        for (Race race : Races.all()) {
            for (RaceBonus bonus : race.bonuses()) {
                if (bonus.kind() == RaceBonus.Kind.ATTRIBUTE) {
                    map.putIfAbsent(modifierId(bonus), bonus.attribute());
                }
            }
        }
        return map;
    }

    /** Remet les bonus d'attributs du joueur exactement à ceux de SA race (ne touche à aucun autre modificateur). */
    public static void apply(ServerPlayer player) {
        Race race = Races.of(player);
        for (Map.Entry<Identifier, Holder<Attribute>> entry : raceModifiers().entrySet()) {
            RaceBonus wanted = null;
            for (RaceBonus bonus : race.bonuses()) {
                if (bonus.kind() == RaceBonus.Kind.ATTRIBUTE && modifierId(bonus).equals(entry.getKey())) {
                    wanted = bonus;
                }
            }
            setModifier(player, entry.getValue(), entry.getKey(), wanted == null ? 0.0 : wanted.amount(),
                    wanted == null ? AttributeModifier.Operation.ADD_VALUE : wanted.operation());
        }
        // Lunarian : armure en plus tant que ses flammes sont actives
        double guard = flamesActive(player) ? race.value(RaceBonus.Kind.FLAME_GUARD) : 0.0;
        setModifier(player, Attributes.ARMOR, FLAME_GUARD_ID, guard, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void setModifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount,
                                    AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current != null && current.amount() == amount && current.operation() == operation) {
            return;
        }
        if (current != null) {
            instance.removeModifier(id);
        }
        if (amount != 0.0) {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
        if (attribute.equals(Attributes.MAX_HEALTH) && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth()); // moins de PV max (changement de race) : pas de PV en trop
        }
    }

    /**
     * Les flammes du Lunarian sont-elles actives ? Pour l'instant : le joueur est en feu. Point d'entrée de la future mécanique
     * de flammes des Lunarians (il suffira de changer ce test).
     */
    public static boolean flamesActive(ServerPlayer player) {
        return player.isOnFire();
    }

    /** Bonus spéciaux qui agissent en continu (chaque tick serveur). */
    private static void tick(ServerPlayer player, long now) {
        Race race = Races.of(player);
        if (race.has(RaceBonus.Kind.WATER_BREATHING) && player.isUnderWater() && player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(player.getMaxAirSupply()); // Fishman : respire sous l'eau
        }
        if (now % 20 != 0) {
            return;
        }
        apply(player);
        double factor = race.value(RaceBonus.Kind.EFFECT_RESIST);
        if (factor > 1.0) {
            // Cyborg : chaque seconde, poison et faim perdent (factor - 1) secondes de plus
            shorten(player, MobEffects.POISON, (int) Math.round(20 * (factor - 1.0)));
            shorten(player, MobEffects.HUNGER, (int) Math.round(20 * (factor - 1.0)));
        }
    }

    private static void shorten(ServerPlayer player, Holder<MobEffect> effect, int ticks) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() <= ticks || current.isInfiniteDuration()) {
            return;
        }
        MobEffectInstance shorter = new MobEffectInstance(effect, current.getDuration() - ticks, current.getAmplifier(),
                current.isAmbient(), current.isVisible(), current.showIcon());
        player.removeEffect(effect);
        player.addEffect(shorter);
    }

    // ------------------------------------------------------------------ reroll

    /** Nombre de Race Reroll Tokens dans l'inventaire du joueur. */
    public static int tokens(net.minecraft.world.entity.player.Player player) {
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.RACE_REROLL_TOKEN)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Demande de reroll (bouton du menu Race) : tout est vérifié ici. */
    public static void reroll(ServerPlayer player) {
        long now = player.level().getServer().getTickCount();
        Long last = LAST_REROLL.get(player.getUUID());
        if (last != null && now - last < REROLL_COOLDOWN) {
            return; // double clic / paquet répété : ignoré, aucun token consommé
        }
        LAST_REROLL.put(player.getUUID(), now);
        Inventory inventory = player.getInventory();
        int slot = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.RACE_REROLL_TOKEN)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.race.no_token"), true);
            return;
        }
        // tirage côté serveur (20 % par race, la même race peut retomber), AVANT toute animation
        Race race = Races.roll(player.getRandom(), null);
        if (Races.get(race.id()) == null) {
            return; // erreur de tirage : rien n'est consommé
        }
        inventory.getItem(slot).shrink(1); // un seul token, seulement pour un reroll réussi
        inventory.setChanged();
        player.setAttached(ModAttachments.RACE, race.id());
        apply(player); // anciens bonus de race retirés, nouveaux posés ; les autres bonus ne bougent pas
        // le menu Race joue la roulette jusqu'à CETTE race ; le message du chat arrive à la fin de l'animation (pas de spoiler)
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new nolan.bloxpiece.network.RaceRollResultPayload(race.id()));
        PENDING_MESSAGE.put(player.getUUID(), new Object[] {now + REVEAL_DELAY, race.id()});
    }

    /** Message du chat d'un reroll, envoyé à la fin de la roulette du menu. */
    private static void announce(ServerPlayer player, long now) {
        Object[] pending = PENDING_MESSAGE.get(player.getUUID());
        if (pending == null || now < (Long) pending[0]) {
            return;
        }
        PENDING_MESSAGE.remove(player.getUUID());
        Race race = Races.get((String) pending[1]);
        if (race != null) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.race.rerolled",
                    Component.translatable(race.translationKey()).withColor(race.color())).withStyle(ChatFormatting.GOLD));
        }
    }

    // ------------------------------------------------------------------ événements

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            ensureRace(player);
            apply(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LAST_REROLL.remove(handler.getPlayer().getUUID());
            PENDING_MESSAGE.remove(handler.getPlayer().getUUID());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> apply(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = server.getTickCount();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tick(player, now);
                announce(player, now);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register((MinecraftServer server) -> {
            LAST_REROLL.clear();
            PENDING_MESSAGE.clear();
        });
    }
}
