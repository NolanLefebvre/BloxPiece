package nolan.bloxpiece.fruit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nolan.bloxpiece.ability.DaiEnkaiEnteiAbility;
import nolan.bloxpiece.ability.FireAbility;
import nolan.bloxpiece.ability.HikenAbility;
import nolan.bloxpiece.ability.MeteorRainAbility;

/** Liste de tous les fruits du mod. Pour en ajouter un : créer ses attaques puis l'enregistrer ici. */
public class Fruits {
    private static final Map<String, DevilFruit> REGISTRY = new HashMap<>();

    public static final DevilFruit FIRE = register(new DevilFruit(
            "fire",
            new WaterCurse(), // faiblesse : malédiction de l'eau
            true,             // immunisé au feu et à la lave
            List.of(
                    new FireAbility(),          // touche 1 : boule de feu
                    new HikenAbility(),         // touche 2 : Hiken (poing de feu géant)
                    new MeteorRainAbility(),    // touche 3 : pluie de météores
                    new DaiEnkaiEnteiAbility()  // touche 4 : Dai Enkai: Entei (ultime)
            )));

    // Fruit du Bouddha : pour l'instant seulement le fruit (item, consommation, menu). Ni faiblesse, ni immunité, ni attaque :
    // les capacités s'ajouteront en remplaçant List.of() par la liste de ses attaques (touches 1 à 4), comme pour le fruit du feu.
    public static final DevilFruit BUDDHA = register(new DevilFruit(
            "buddha",
            null,  // pas de faiblesse pour le moment
            false, // pas d'immunité au feu
            List.of()));

    private static DevilFruit register(DevilFruit fruit) {
        REGISTRY.put(fruit.id(), fruit);
        return fruit;
    }

    /** Renvoie le fruit correspondant à cet id, ou null s'il n'existe pas. */
    public static DevilFruit get(String id) {
        return id == null ? null : REGISTRY.get(id);
    }
}
