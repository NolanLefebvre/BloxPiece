package nolan.bloxpiece.race;

import java.util.List;

import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;

/**
 * Une race : TOUTES ses données au même endroit (définies dans Races). Le système de races (RaceManager, menus) est commun :
 * ajouter une race = ajouter une entrée dans Races (+ ses textes dans la lang + son icône), rien d'autre.
 * <ul>
 *   <li>{@code id} : identifiant (sauvegardé avec le joueur) ; nom « race.bloxpiece.&lt;id&gt; », description
 *       « race.bloxpiece.&lt;id&gt;.description » dans la lang ; icône textures/gui/race/&lt;id&gt;.png (16 x 16).</li>
 *   <li>{@code color} : couleur ARGB du nom dans les interfaces.</li>
 *   <li>{@code weight} : chance d'obtention, en % (le total de toutes les races doit faire 100 : vérifié avant chaque tirage).</li>
 *   <li>{@code bonuses} : bonus passifs (RaceBonus) : appliqués par RaceManager ET affichés par le menu Race à partir des mêmes
 *       valeurs (ce qui est affiché est exactement ce qui est appliqué).</li>
 *   <li>{@code attacks} : attaques raciales (RaceAttack), dans l'ordre de déblocage.</li>
 * </ul>
 */
public record Race(String id, int color, int weight, List<RaceBonus> bonuses, List<RaceAttack> attacks) {

    public String translationKey() {
        return "race." + BloxPiece.MOD_ID + "." + id;
    }

    public String descriptionKey() {
        return translationKey() + ".description";
    }

    public Identifier icon() {
        return BloxPiece.id("textures/gui/race/" + id + ".png");
    }

    /** Cette race a-t-elle ce bonus spécial (RaceBonus.Kind) ? */
    public boolean has(RaceBonus.Kind kind) {
        for (RaceBonus bonus : bonuses) {
            if (bonus.kind() == kind) {
                return true;
            }
        }
        return false;
    }

    /** Valeur d'un bonus spécial de cette race (0 s'il n'en a pas). */
    public double value(RaceBonus.Kind kind) {
        for (RaceBonus bonus : bonuses) {
            if (bonus.kind() == kind) {
                return bonus.amount();
            }
        }
        return 0.0;
    }
}
