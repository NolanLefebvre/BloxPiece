package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import nolan.bloxpiece.fruit.DevilFruit;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.network.CooldownPayload;
import nolan.bloxpiece.quest.QuestType;
import nolan.bloxpiece.quest.Quests;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.fruit.WaterCurse;
import nolan.bloxpiece.handcuffs.Handcuffs;
import nolan.bloxpiece.handcuffs.Seastone;

/**
 * Toute la logique côté serveur : quel fruit a le joueur, quelle attaque correspond à la touche,
 * est-ce qu'elle est en recharge. Le client ne fait qu'envoyer le numéro de la touche.
 * Le serveur prévient aussi le client des recharges en cours, uniquement pour l'affichage du HUD.
 */
public class AbilityManager {

    // joueur -> ("fruit:attaque" -> tick de jeu où l'attaque redevient utilisable)
    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();

    /** Clé de recharge d'une attaque ("fruit:attaque"). Utilisée aussi par le HUD client pour lire la même recharge. */
    public static String cooldownKey(DevilFruit fruit, Ability ability) {
        return fruit.id() + ":" + ability.id();
    }

    public static void tryUse(ServerPlayer player, int slot) {
        // On ne fait jamais confiance au client : on vérifie tout côté serveur.
        if (slot < 0 || slot >= DevilFruit.MAX_ABILITIES) {
            return;
        }

        // Pas de fruit, ou pas d'attaque sur cette touche : on ne fait rien (le HUD montre déjà les cases vides)
        DevilFruit fruit = Fruits.get(player.getAttached(ModAttachments.FRUIT));
        if (fruit == null) {
            return;
        }

        Ability ability = fruit.getAbility(slot);
        if (ability == null) {
            return;
        }

        // Visiteur dans la dimension de poche d'un autre joueur : aucun pouvoir (rien ne doit abîmer ses constructions)
        if (KameVisits.isGuestIn(player, player.level())) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.kame.visit_no_powers"), true);
            return;
        }

        // Évanoui (Haki des Rois) : rien ne part
        if (nolan.bloxpiece.haki.Unconscious.is(player)) {
            return;
        }

        // Granite Marin : aucun pouvoir (transformation comprise) si le joueur est menotté ou a un objet en Granite Marin sur lui
        if (Handcuffs.isCuffed(player)) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.handcuffs.no_powers"), true);
            return;
        }
        if (Seastone.carries(player)) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.seastone.no_powers"), true);
            return;
        }

        // Faiblesse à l'eau : les pieds dans l'eau suffisent à bloquer tous les pouvoirs (transformation comprise)
        if (WaterCurse.touchesWater(player)) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.water_weakness"), true);
            return;
        }

        // Attaque verrouillée : le niveau du joueur (côté serveur) est trop bas. Quel que soit le client, rien ne part.
        if (!ability.isUnlockedFor(player)) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.ability_locked",
                    Component.translatable(ability.nameKey()), ability.requiredLevel()));
            return;
        }

        long now = player.level().getGameTime();
        String key = cooldownKey(fruit, ability);
        Map<String, Long> playerCooldowns = COOLDOWNS.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>());

        long readyAt = playerCooldowns.getOrDefault(key, 0L);
        if (now < readyAt) {
            return; // en recharge : le temps restant est affiché dans le HUD
        }

        // L'attaque peut refuser de partir (ex : aucune cible visée) : dans ce cas la recharge n'est pas consommée
        if (!ability.canUse(player)) {
            return;
        }

        if (!ability.cooldownOnRelease()) {
            playerCooldowns.put(key, now + ability.cooldownTicks());
            // Affichage uniquement : le HUD du joueur démarre son compte à rebours
            ServerPlayNetworking.send(player, new CooldownPayload(key, ability.cooldownTicks()));
        }
        ability.use(player);
        Quests.progress(player, QuestType.USE_ABILITIES, 1); // quête journalière "utiliser des capacités"
    }

    /** Démarre la recharge d'une attaque à charge au moment où elle part (voir Ability.cooldownOnRelease). */
    public static void startCooldown(ServerPlayer player, Ability ability) {
        DevilFruit fruit = Fruits.get(player.getAttached(ModAttachments.FRUIT));
        if (fruit == null) {
            return;
        }
        String key = cooldownKey(fruit, ability);
        COOLDOWNS.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>())
                .put(key, player.level().getGameTime() + ability.cooldownTicks());
        ServerPlayNetworking.send(player, new CooldownPayload(key, ability.cooldownTicks()));
    }

    /** Le joueur a relâché la touche de ce slot : prévient l'attaque (seules les attaques à charge s'en servent). */
    public static void release(ServerPlayer player, int slot) {
        DevilFruit fruit = Fruits.get(player.getAttached(ModAttachments.FRUIT));
        Ability ability = fruit == null || slot < 0 || slot >= DevilFruit.MAX_ABILITIES ? null : fruit.getAbility(slot);
        if (ability != null) {
            ability.release(player);
        }
    }

    /** Renvoie au client ses recharges encore en cours (à appeler quand le joueur rejoint le serveur). */
    public static void syncCooldowns(ServerPlayer player) {
        Map<String, Long> playerCooldowns = COOLDOWNS.get(player.getUUID());
        if (playerCooldowns == null) {
            return;
        }
        long now = player.level().getGameTime();
        playerCooldowns.forEach((key, readyAt) -> {
            long remaining = readyAt - now;
            if (remaining > 0) {
                ServerPlayNetworking.send(player, new CooldownPayload(key, (int) remaining));
            }
        });
    }

    /** Remet à zéro toutes les recharges d'un joueur (utilisé quand on lui retire son fruit). */
    public static void clearCooldowns(UUID playerId) {
        COOLDOWNS.remove(playerId);
    }

    /**
     * Pareil, mais prévient aussi le HUD du joueur pour qu'il efface les recharges affichées.
     * Renvoie le nombre d'attaques qui étaient réellement en recharge (0 = il n'y avait rien à réinitialiser).
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
            ServerPlayNetworking.send(player, new CooldownPayload(entry.getKey(), 0));
        }
        return active;
    }
}
