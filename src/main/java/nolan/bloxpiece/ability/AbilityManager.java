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
import nolan.bloxpiece.registry.ModAttachments;

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

        playerCooldowns.put(key, now + ability.cooldownTicks());
        // Affichage uniquement : le HUD du joueur démarre son compte à rebours
        ServerPlayNetworking.send(player, new CooldownPayload(key, ability.cooldownTicks()));
        ability.use(player);
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

    /** Pareil, mais prévient aussi le HUD du joueur pour qu'il efface les recharges affichées. */
    public static void clearCooldowns(ServerPlayer player) {
        Map<String, Long> removed = COOLDOWNS.remove(player.getUUID());
        if (removed == null) {
            return;
        }
        for (String key : removed.keySet()) {
            ServerPlayNetworking.send(player, new CooldownPayload(key, 0));
        }
    }
}
