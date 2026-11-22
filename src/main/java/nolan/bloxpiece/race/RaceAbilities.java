package nolan.bloxpiece.race;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import nolan.bloxpiece.ability.Ability;
import nolan.bloxpiece.network.CooldownPayload;
import nolan.bloxpiece.progression.Progression;

/**
 * Attaques RACIALES côté serveur (le même rôle qu'AbilityManager pour les fruits, avec le même contrat Ability). Elles utilisent
 * les MÊMES touches que les attaques du fruit (UseAbilityPayload) : en mode race ({@link #raceMode}), la touche n° {@code slot}
 * lance la {@code slot}-ième attaque de la race du joueur. Tout est vérifié ici, jamais côté client :
 * <ul>
 *   <li>l'attaque appartient à SA race (une autre race ne peut jamais la lancer) ;</li>
 *   <li>elle est développée (« À venir » : refusée) ;</li>
 *   <li>son niveau BloxPiece (Progression) atteint le niveau requis ;</li>
 *   <li>pas évanoui (Haki des Rois), pas menotté ;</li>
 *   <li>sa recharge est terminée (recharges propres aux races : clé « race:&lt;id&gt; », envoyées au HUD comme celles des
 *       fruits).</li>
 * </ul>
 */
public final class RaceAbilities {

    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();

    private RaceAbilities() {
    }

    /**
     * Les touches d'attaque lancent-elles les attaques de la RACE ? Oui sans fruit du démon (rien à basculer), sinon selon le
     * mode choisi avec la touche « Basculer fruit / race ». Même règle côté client (HUD) : RACE_ATTACK_MODE est synchronisé.
     */
    public static boolean raceMode(net.minecraft.world.entity.player.Player player) {
        return nolan.bloxpiece.fruit.Fruits.get(player.getAttached(nolan.bloxpiece.registry.ModAttachments.FRUIT)) == null
                || Boolean.TRUE.equals(player.getAttached(nolan.bloxpiece.registry.ModAttachments.RACE_ATTACK_MODE));
    }

    /** Touche « Basculer fruit / race » : change d'ensemble (seulement avec un fruit ; sans fruit, c'est toujours la race). */
    public static void toggle(ServerPlayer player) {
        nolan.bloxpiece.fruit.DevilFruit fruit = nolan.bloxpiece.fruit.Fruits.get(
                player.getAttached(nolan.bloxpiece.registry.ModAttachments.FRUIT));
        Race race = Races.of(player);
        if (fruit == null) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.attack_set.no_fruit",
                    Component.translatable(race.translationKey())), true);
            return;
        }
        boolean toRace = !raceMode(player);
        if (toRace) {
            player.setAttached(nolan.bloxpiece.registry.ModAttachments.RACE_ATTACK_MODE, true);
        } else {
            player.removeAttached(nolan.bloxpiece.registry.ModAttachments.RACE_ATTACK_MODE);
        }
        player.sendSystemMessage(Component.translatable("message.bloxpiece.attack_set.now",
                toRace ? Component.translatable(race.translationKey()) : Component.translatable(fruit.translationKey())), true);
    }

    public static String cooldownKey(RaceAttack attack) {
        return "race:" + attack.id();
    }

    private static RaceAttack attackAt(ServerPlayer player, int slot) {
        java.util.List<RaceAttack> attacks = Races.of(player).attacks();
        return slot < 0 || slot >= attacks.size() ? null : attacks.get(slot);
    }

    public static void tryUse(ServerPlayer player, int slot) {
        RaceAttack attack = attackAt(player, slot);
        if (attack == null || !player.isAlive()) {
            return;
        }
        if (!attack.implemented()) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.race.attack_soon",
                    Component.translatable(attack.nameKey())), true);
            return;
        }
        if (Progression.get(player).level() < attack.requiredLevel()) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.ability_locked",
                    Component.translatable(attack.nameKey()), attack.requiredLevel()));
            return;
        }
        if (!Races.canUse(player, attack)) {
            return; // (sécurité : même vérification, centralisée dans Races)
        }
        if (nolan.bloxpiece.haki.Unconscious.is(player) || nolan.bloxpiece.handcuffs.Handcuffs.isCuffed(player)) {
            return;
        }
        Ability ability = attack.ability();
        long now = player.level().getGameTime();
        Map<String, Long> cooldowns = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        long readyAt = cooldowns.getOrDefault(cooldownKey(attack), 0L);
        if (now < readyAt) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.race.attack_cooldown",
                    Component.translatable(attack.nameKey()), (readyAt - now + 19) / 20), true);
            return;
        }
        if (!ability.canUse(player)) {
            return; // l'attaque refuse de partir (déjà une autre en cours...) : recharge non consommée
        }
        if (!ability.cooldownOnRelease()) {
            startCooldown(player, attack);
        }
        ability.use(player);
        nolan.bloxpiece.quest.Quests.progress(player, nolan.bloxpiece.quest.QuestType.USE_ABILITIES, 1);
    }

    public static void release(ServerPlayer player, int slot) {
        RaceAttack attack = attackAt(player, slot);
        if (attack != null && attack.implemented()) {
            attack.ability().release(player);
        }
    }

    public static void startCooldown(ServerPlayer player, RaceAttack attack) {
        int ticks = attack.ability().cooldownTicks();
        COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(cooldownKey(attack), player.level().getGameTime() + ticks);
        ServerPlayNetworking.send(player, new CooldownPayload(cooldownKey(attack), ticks));
    }

    /**
     * Efface TOUTES les recharges d'attaques de race du joueur (commande /bloxfruit resetcooldowns) et prévient son HUD.
     * Toutes les attaques de race passent par {@link #startCooldown} : la table contient donc celles de toutes les races (Cyborg
     * comme les futures), indépendamment du mode fruit / race choisi. Renvoie le nombre de recharges qui étaient en cours.
     */
    public static int clearCooldowns(ServerPlayer player) {
        Map<String, Long> removed = COOLDOWNS.remove(player.getUUID());
        if (removed == null) {
            return 0;
        }
        long now = player.level().getGameTime();
        int active = 0;
        for (Map.Entry<String, Long> entry : removed.entrySet()) {
            if (entry.getValue() > now) {
                active++;
            }
            ServerPlayNetworking.send(player, new CooldownPayload(entry.getKey(), 0)); // le HUD efface son compte à rebours
        }
        return active;
    }

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // recharges encore en cours : renvoyées au client (affichage)
            Map<String, Long> cooldowns = COOLDOWNS.get(handler.getPlayer().getUUID());
            if (cooldowns != null) {
                long now = handler.getPlayer().level().getGameTime();
                cooldowns.forEach((key, at) -> {
                    if (at > now) {
                        ServerPlayNetworking.send(handler.getPlayer(), new CooldownPayload(key, (int) (at - now)));
                    }
                });
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> COOLDOWNS.clear());
    }
}
