package nolan.bloxpiece.client.hud;

import java.util.HashMap;
import java.util.Map;

/**
 * Copie d'affichage des recharges, alimentée par les paquets du serveur (CooldownPayload).
 * Ce n'est PAS un second système de recharge : le serveur reste le seul à décider si une attaque
 * est utilisable. Ici on ne fait que compter à rebours pour le HUD, avec le temps de jeu du client
 * (donc le décompte s'arrête aussi quand le jeu est en pause).
 */
public final class ClientCooldowns {

    // "fruit:attaque" (même clé que le serveur) -> tick de jeu où l'attaque redevient utilisable
    private static final Map<String, Long> READY_AT = new HashMap<>();

    private ClientCooldowns() {
    }

    /** ticks <= 0 : plus de recharge. */
    public static void set(String key, int ticks, long gameTime) {
        if (ticks <= 0) {
            READY_AT.remove(key);
        } else {
            READY_AT.put(key, gameTime + ticks);
        }
    }

    /** Ticks restants avant que l'attaque soit de nouveau disponible (0 = disponible). */
    public static int remainingTicks(String key, long gameTime) {
        Long readyAt = READY_AT.get(key);
        if (readyAt == null) {
            return 0;
        }
        long remaining = readyAt - gameTime;
        if (remaining <= 0) {
            READY_AT.remove(key);
            return 0;
        }
        return (int) remaining;
    }

    public static void clear() {
        READY_AT.clear();
    }
}
