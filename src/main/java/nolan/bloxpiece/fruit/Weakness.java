package nolan.bloxpiece.fruit;

import net.minecraft.server.level.ServerPlayer;

/**
 * Une faiblesse liée à un fruit. Ce n'est PAS un effet de potion : elle est exécutée à chaque tick
 * serveur pour les joueurs qui ont le fruit, sans rien afficher dans le HUD.
 */
@FunctionalInterface
public interface Weakness {
    void tick(ServerPlayer player);
}
