package nolan.bloxpiece.progression;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.registry.ModAttachments;

/**
 * Point d'entrée du système de niveaux : TOUT passe par ici.
 * Plus tard, pour donner de l'XP (tuer un mob, finir une quête...), il suffira d'appeler addXp.
 */
public final class Progression {

    private Progression() {
    }

    /** Niveau et XP d'un joueur (niveau 1 / 0 XP tant qu'il n'a rien gagné). Utilisable côté client et serveur. */
    public static PlayerProgress get(Player player) {
        PlayerProgress progress = player.getAttached(ModAttachments.PROGRESS);
        return progress != null ? progress : PlayerProgress.START;
    }

    /**
     * Ajoute de l'XP à un joueur (serveur uniquement) ; un nombre négatif en retire (le niveau peut alors baisser).
     * Renvoie sa nouvelle progression. Le client est prévenu automatiquement.
     */
    public static PlayerProgress addXp(ServerPlayer player, long amount) {
        PlayerProgress updated = get(player).withXp(amount);
        player.setAttached(ModAttachments.PROGRESS, updated);
        return updated;
    }
}
