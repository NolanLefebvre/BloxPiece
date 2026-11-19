package nolan.bloxpiece.haki;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.registry.ModAttachments;

/**
 * Maîtrise du Haki : un seul palier au-dessus du niveau de base. Chaque type de Haki est soit au niveau de base, soit
 * MAÎTRISÉ (plus tard : débloqué par une quête spéciale propre à ce Haki). La maîtrise débloque l'attaque avancée de ce Haki
 * ({@link #ADVANCED_ATTACKS}, à venir).
 *
 * Stockage : attachment HAKI_MASTERY = une case par type de Haki (0 = base, 1 = maîtrisé), dans l'ordre de HakiType.
 */
public final class HakiMastery {

    /** Attaque avancée d'un Haki, débloquée par sa maîtrise. */
    public record AdvancedAttack(HakiType type, String id) {
        /** Clé de traduction du nom : haki.bloxpiece.&lt;type&gt;.advanced.name (et .description). */
        public String nameKey() {
            return "haki.bloxpiece." + type.id + ".advanced.name";
        }

        public String descriptionKey() {
            return "haki.bloxpiece." + type.id + ".advanced.description";
        }
    }

    /** Une attaque avancée prévue par type de Haki (pas encore jouable : affichée dans la page Haki). */
    public static final List<AdvancedAttack> ADVANCED_ATTACKS = List.of(
            new AdvancedAttack(HakiType.ARMAMENT, "advanced"),
            new AdvancedAttack(HakiType.OBSERVATION, "advanced"),
            new AdvancedAttack(HakiType.CONQUEROR, "advanced"));

    private HakiMastery() {
    }

    public static AdvancedAttack advancedAttack(HakiType type) {
        return ADVANCED_ATTACKS.get(type.ordinal());
    }

    public static boolean isMastered(Entity entity, HakiType type) {
        List<Integer> data = entity.getAttached(ModAttachments.HAKI_MASTERY);
        return data != null && type.ordinal() < data.size() && data.get(type.ordinal()) > 0;
    }

    /** Côté serveur (plus tard : à la fin de la quête spéciale de ce Haki) ; l'attachment est renvoyé au joueur. */
    public static void setMastered(Entity entity, HakiType type, boolean mastered) {
        List<Integer> data = entity.getAttached(ModAttachments.HAKI_MASTERY);
        List<Integer> copy = new ArrayList<>(data == null ? List.of() : data);
        while (copy.size() < HakiType.values().length) {
            copy.add(0);
        }
        copy.set(type.ordinal(), mastered ? 1 : 0);
        entity.setAttached(ModAttachments.HAKI_MASTERY, List.copyOf(copy));
    }
}
