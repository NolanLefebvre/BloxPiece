package nolan.bloxpiece.race;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.registry.ModAttachments;

/**
 * Liste de toutes les races du mod (même principe que Fruits).
 * Pour ajouter une race : l'enregistrer ici + ajouter son nom dans la lang ("race.bloxpiece.<id>").
 * Les interfaces (menu personnage...) l'afficheront toutes seules.
 */
public class Races {
    private static final Map<String, Race> REGISTRY = new HashMap<>();

    public static final Race HUMAN = register(new Race("human", 0xFFE8C9A0));

    /** Race des joueurs qui n'en ont pas choisi : tout le monde est humain pour l'instant. */
    public static final Race DEFAULT = HUMAN;

    private static Race register(Race race) {
        REGISTRY.put(race.id(), race);
        return race;
    }

    /** Renvoie la race correspondant à cet id, ou null si elle n'existe pas. */
    public static Race get(String id) {
        return id == null ? null : REGISTRY.get(id);
    }

    /** Race actuelle d'un joueur (la race par défaut si aucune n'est enregistrée). Utilisable côté client et serveur. */
    public static Race of(Player player) {
        Race race = get(player.getAttached(ModAttachments.RACE));
        return race != null ? race : DEFAULT;
    }
}
