package nolan.bloxpiece.fruit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nolan.bloxpiece.ability.BuddhaPalmAbility;
import nolan.bloxpiece.ability.BuddhaShockwaveAbility;
import nolan.bloxpiece.ability.BuddhaTransformAbility;
import nolan.bloxpiece.ability.BuddhaUltimateAbility;
import nolan.bloxpiece.ability.DaiEnkaiEnteiAbility;
import nolan.bloxpiece.ability.FireAbility;
import nolan.bloxpiece.ability.GuraGekishinAbility;
import nolan.bloxpiece.ability.GuraKabutowariAbility;
import nolan.bloxpiece.ability.GuraKaishinAbility;
import nolan.bloxpiece.ability.GuraShimaYurashiAbility;
import nolan.bloxpiece.ability.HikenAbility;
import nolan.bloxpiece.ability.IceAgeAbility;
import nolan.bloxpiece.ability.IceEagleAbility;
import nolan.bloxpiece.ability.IceSaberAbility;
import nolan.bloxpiece.ability.IceTridentThrowAbility;
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

    // Fruit du Bouddha : 4 attaques, comme le fruit du feu. Malédiction de l'eau, pas d'immunité.
    public static final DevilFruit BUDDHA = register(new DevilFruit(
            "buddha",
            new WaterCurse(), // faiblesse : malédiction de l'eau (comme tous les fruits du démon)
            false, // pas d'immunité au feu
            List.of(
                    new BuddhaTransformAbility(), // touche 1 : transformation en Bouddha
                    new BuddhaPalmAbility(),      // touche 2 : Paume du Bouddha (la main géante qui tombe du ciel)
                    new BuddhaShockwaveAbility(), // touche 3 : Onde de choc (cône devant le joueur)
                    new BuddhaUltimateAbility()   // touche 4 : Déflagration du Bouddha (ultime)
            )));

    // Fruit de la Glace (Hie Hie no Mi) : 4 attaques. Malédiction de l'eau comme tous les fruits du démon, pas d'immunité.
    public static final DevilFruit ICE = register(new DevilFruit(
            "ice",
            new WaterCurse(),
            false,
            List.of(
                    new IceSaberAbility(),        // touche 1 : Ice Saber (trident de glace en main)
                    new IceTridentThrowAbility(), // touche 2 : Partisan (lancer du trident)
                    new IceEagleAbility(),        // touche 3 : Pheasant Peck (aigle de glace)
                    new IceAgeAbility()           // touche 4 : Ice Age (ultime)
            )));

    // Fruit du Tremblement (Gura Gura no Mi) : 4 attaques sismiques. Malédiction de l'eau, pas d'immunité.
    public static final DevilFruit GURA = register(new DevilFruit(
            "gura",
            new WaterCurse(),
            false,
            List.of(
                    new GuraKaishinAbility(),       // touche 1 : Kaishin (fissure sismique en cône)
                    new GuraGekishinAbility(),      // touche 2 : Gekishin (dash et coup sismique)
                    new GuraKabutowariAbility(),    // touche 3 : Kabutowari (séisme localisé)
                    new GuraShimaYurashiAbility()   // touche 4 : Shima Yurashi (ultime, à charger)
            )));

    // Fruit de la Tortue (Kame Kame no Mi) : 3 pouvoirs autour de sa carapace. Malédiction de l'eau, pas d'immunité.
    public static final DevilFruit KAME = register(new DevilFruit(
            "kame",
            new WaterCurse(),
            false,
            List.of(
                    new nolan.bloxpiece.ability.KameShellAbility(),  // touche 1 : la carapace pousse (bonus d'armure)
                    new nolan.bloxpiece.ability.KamePocketAbility(), // touche 2 : dimension de poche (et retour)
                    new nolan.bloxpiece.ability.KameSpinAbility()    // touche 3 : carapace tournoyante
            )));

    // Fruit de la Foudre (Goro Goro no Mi, le fruit d'Ener) : 4 attaques électriques bleues. Malédiction de l'eau, pas d'immunité.
    public static final DevilFruit GORO = register(new DevilFruit(
            "goro",
            new WaterCurse(),
            false,
            List.of(
                    new nolan.bloxpiece.ability.GoroMamaraganAbility(), // touche 1 : Mamaragan (nuage, foudre sur toute la zone)
                    new nolan.bloxpiece.ability.GoroKariAbility(),      // touche 2 : Kari (pluie de foudre sur la zone visée)
                    new nolan.bloxpiece.ability.GoroElThorAbility(),    // touche 3 : El Thor (colonne de foudre géante, cratère)
                    new nolan.bloxpiece.ability.GoroRaigoAbility()      // touche 4 : Raigo (ultime, à charger)
            )));

    private static DevilFruit register(DevilFruit fruit) {
        REGISTRY.put(fruit.id(), fruit);
        return fruit;
    }

    /** Renvoie le fruit correspondant à cet id, ou null s'il n'existe pas. */
    public static DevilFruit get(String id) {
        return id == null ? null : REGISTRY.get(id);
    }
}
