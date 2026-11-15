package nolan.bloxpiece.race;

import nolan.bloxpiece.BloxPiece;

/**
 * Une race = un id + une couleur d'affichage.
 * Le nom s'écrit dans la lang : "race.bloxpiece.<id>".
 * Pour l'instant une race ne change aucune mécanique de jeu ; c'est une donnée du personnage.
 *
 * @param color couleur ARGB utilisée par les interfaces pour afficher cette race
 */
public record Race(String id, int color) {

    public String translationKey() {
        return "race." + BloxPiece.MOD_ID + "." + id;
    }
}
