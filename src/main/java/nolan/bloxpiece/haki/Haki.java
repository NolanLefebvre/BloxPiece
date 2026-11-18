package nolan.bloxpiece.haki;

import net.minecraft.server.level.ServerPlayer;

/**
 * Un Haki (architecture évolutive) : chaque Haki de base est une classe qui implémente cette interface et décide seule de ce
 * que font l'appui et le relâchement de sa touche, et de son effet à chaque tick. HakiManager s'occupe du reste (touches,
 * recharges, déblocage, conditions). Pour une forme avancée plus tard (Ryuo, Vision du futur, Revêtement des Rois) : une
 * nouvelle classe Haki renvoyée par {@link #advanced}, débloquée par son propre bit.
 */
public interface Haki {

    HakiType type();

    /** Touche appuyée (recharge et conditions déjà vérifiées par HakiManager). */
    void press(ServerPlayer player);

    /** Touche relâchée (même pendant une recharge : un maintien peut se terminer). */
    default void release(ServerPlayer player) {
    }

    /** À chaque tick serveur, pour chaque joueur connecté. */
    void tick(ServerPlayer player);

    /** Le Haki est-il actif en ce moment (le HUD l'éclaire) ? */
    boolean isActive(ServerPlayer player);

    /** Coupe le Haki tout de suite (menottes, évanouissement, mort...). */
    void stop(ServerPlayer player);

    /** Forme avancée de ce Haki, ou null tant qu'elle n'existe pas. */
    default Haki advanced() {
        return null;
    }
}
