package nolan.bloxpiece.race;

import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;

/**
 * Une attaque raciale (définie dans Races, dans la liste de sa race). Pour en ajouter une : une ligne dans Races
 * ({@code id}, niveau requis) + son nom « race.bloxpiece.attack.&lt;id&gt; » et sa description « ....description » dans la lang
 * + son icône textures/gui/race/attack/&lt;id&gt;.png (facultative : sinon l'icône de la race). Le menu des attaques s'adapte seul.
 * <p>
 * {@code ability} : le code de l'attaque (le même contrat que les attaques des fruits : nolan.bloxpiece.ability.Ability,
 * recharge, charge et relâchement compris) ; null = « À venir » (jamais présentée comme utilisable, et refusée par
 * RaceAbilities). Le jour où elle est codée : remplacer null par son Ability, rien d'autre à changer. Touches : les
 * mêmes que les attaques du fruit, en mode race (RaceAbilities.raceMode, touche « Basculer fruit / race »).
 */
public record RaceAttack(String id, int requiredLevel, nolan.bloxpiece.ability.Ability ability) {

    /** Attaque prévue, pas encore développée. */
    public static RaceAttack planned(String id, int requiredLevel) {
        return new RaceAttack(id, requiredLevel, null);
    }

    /** Attaque développée : son niveau requis est celui de la ligne de Races. */
    public static RaceAttack of(String id, int requiredLevel, nolan.bloxpiece.ability.Ability ability) {
        return new RaceAttack(id, requiredLevel, ability);
    }

    public boolean implemented() {
        return ability != null;
    }

    public String nameKey() {
        return "race.bloxpiece.attack." + id;
    }

    public String descriptionKey() {
        return nameKey() + ".description";
    }

    public Identifier icon() {
        return BloxPiece.id("textures/gui/race/attack/" + id + ".png");
    }
}
