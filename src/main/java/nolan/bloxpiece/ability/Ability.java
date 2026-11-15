package nolan.bloxpiece.ability;

import net.minecraft.server.level.ServerPlayer;

/**
 * Une attaque ou un pouvoir lié à un fruit du démon.
 * Chaque attaque sera une classe qui implémente cette interface.
 */
public interface Ability {

    /** Identifiant interne de l'attaque (ex : "fire_fist"). */
    String id();

    /** Temps de recharge en ticks (20 ticks = 1 seconde). */
    int cooldownTicks();

    /** Exécuté côté serveur quand le joueur déclenche l'attaque. */
    void use(ServerPlayer player);
}
