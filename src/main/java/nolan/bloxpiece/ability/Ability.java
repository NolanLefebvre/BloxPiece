package nolan.bloxpiece.ability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.progression.LevelCurve;
import nolan.bloxpiece.progression.Progression;

/**
 * Une attaque ou un pouvoir lié à un fruit du démon.
 * Chaque attaque sera une classe qui implémente cette interface.
 */
public interface Ability {

    /** Identifiant interne de l'attaque (ex : "fire_fist"). */
    String id();

    /** Temps de recharge en ticks (20 ticks = 1 seconde). */
    int cooldownTicks();

    /** Niveau minimum du joueur pour utiliser cette attaque (1 = disponible dès le départ). */
    default int requiredLevel() {
        return LevelCurve.START_LEVEL;
    }

    /**
     * Cette attaque est-elle débloquée pour ce joueur ? Se déduit uniquement de son niveau : le déblocage est
     * automatique, rien à enregistrer. Utilisable côté serveur (vérification) ET côté client (affichage) :
     * le niveau du joueur est synchronisé par le serveur.
     */
    default boolean isUnlockedFor(Player player) {
        return Progression.get(player).level() >= requiredLevel();
    }

    /** Clé de traduction du nom de l'attaque (à écrire dans la lang). */
    default String nameKey() {
        return "ability." + BloxPiece.MOD_ID + "." + id() + ".name";
    }

    /** Clé de traduction de la description de l'attaque (à écrire dans la lang). */
    default String descriptionKey() {
        return "ability." + BloxPiece.MOD_ID + "." + id() + ".description";
    }

    /**
     * Vérifié côté serveur AVANT de lancer l'attaque et de démarrer sa recharge.
     * Une attaque qui a besoin d'une cible (ex : viser un endroit) renvoie false s'il n'y en a pas :
     * dans ce cas rien ne se lance et la recharge n'est pas consommée. L'attaque prévient elle-même le joueur.
     */
    default boolean canUse(ServerPlayer player) {
        return true;
    }

    /** Exécuté côté serveur quand le joueur déclenche l'attaque. */
    void use(ServerPlayer player);
}
