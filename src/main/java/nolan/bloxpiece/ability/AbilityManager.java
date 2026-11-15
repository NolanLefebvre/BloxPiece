package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import nolan.bloxpiece.fruit.DevilFruit;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Toute la logique côté serveur : quel fruit a le joueur, quelle attaque correspond à la touche,
 * est-ce qu'elle est en recharge. Le client ne fait qu'envoyer le numéro de la touche.
 */
public class AbilityManager {

    // joueur -> ("fruit:attaque" -> tick de jeu où l'attaque redevient utilisable)
    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();

    public static void tryUse(ServerPlayer player, int slot) {
        // On ne fait jamais confiance au client : on vérifie tout côté serveur.
        if (slot < 0 || slot >= DevilFruit.MAX_ABILITIES) {
            return;
        }

        DevilFruit fruit = Fruits.get(player.getAttached(ModAttachments.FRUIT));
        if (fruit == null) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.no_fruit"));
            return;
        }

        Ability ability = fruit.getAbility(slot);
        if (ability == null) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.no_ability"));
            return;
        }

        long now = player.level().getGameTime();
        String key = fruit.id() + ":" + ability.id();
        Map<String, Long> playerCooldowns = COOLDOWNS.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>());

        long readyAt = playerCooldowns.getOrDefault(key, 0L);
        if (now < readyAt) {
            long secondsLeft = (readyAt - now + 19) / 20; // arrondi au supérieur
            player.sendSystemMessage(Component.translatable("message.bloxpiece.cooldown", secondsLeft));
            return;
        }

        playerCooldowns.put(key, now + ability.cooldownTicks());
        ability.use(player);
    }

    /** Remet à zéro toutes les recharges d'un joueur (utilisé quand on lui retire son fruit). */
    public static void clearCooldowns(UUID playerId) {
        COOLDOWNS.remove(playerId);
    }
}
