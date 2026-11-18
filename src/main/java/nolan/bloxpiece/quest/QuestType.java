package nolan.bloxpiece.quest;

import java.util.List;

/**
 * Les types de quêtes journalières : objectif (fourchette tirée au hasard, arrondie à {@code step}), difficulté, et récompenses.
 * Chaque quête donne TOUJOURS des cristaux d'XP selon sa difficulté (voir {@link #crystal}) + un petit bonus tiré parmi
 * {@code rewards} (nourriture, quelques lingots). Jamais de fruit du démon ni d'équipement de fin de jeu. Pour rééquilibrer,
 * modifie uniquement ce fichier.
 */
public enum QuestType {

    // difficulté : 1 facile, 2 moyenne, 3 difficile (+1 si l'objectif tiré est dans le tiers haut de la fourchette)
    KILL_PIRATES("kill_pirates", 8, 12, 1, false, 3, List.of(
            Reward.item("minecraft:gold_ingot", 4), Reward.item("minecraft:iron_ingot", 6), Reward.item("minecraft:golden_carrot", 6))),
    KILL_HOSTILES("kill_hostiles", 12, 18, 1, false, 2, List.of(
            Reward.item("minecraft:iron_ingot", 6), Reward.item("minecraft:cooked_beef", 8), Reward.item("minecraft:arrow", 16))),
    USE_ABILITIES("use_abilities", 10, 15, 1, true, 2, List.of(
            Reward.item("minecraft:golden_apple", 1), Reward.item("minecraft:cooked_beef", 8))),
    TRAVEL("travel", 800, 1200, 100, false, 1, List.of(
            Reward.item("minecraft:bread", 12), Reward.item("minecraft:cooked_beef", 6))),
    MINE_BLOCKS("mine_blocks", 64, 96, 8, false, 1, List.of(
            Reward.item("minecraft:iron_ingot", 5), Reward.item("minecraft:gold_ingot", 3), Reward.item("minecraft:coal", 12)));

    /** Une récompense : un objet (id + nombre) ou de l'XP BloxPiece (id {@link #XP}). */
    public record Reward(String id, int count) {
        public static final String XP = "xp";

        static Reward item(String id, int count) {
            return new Reward(id, count);
        }

        static Reward xp(int amount) {
            return new Reward(XP, amount);
        }

        public boolean isXp() {
            return XP.equals(this.id);
        }
    }

    public final String id;
    final int min;
    final int max;
    final int step;
    /** Demande un fruit du démon (pas tirée pour un joueur sans fruit). */
    final boolean needsFruit;
    final List<Reward> rewards;
    /** Difficulté de base (1 à 3). */
    final int difficulty;

    QuestType(String id, int min, int max, int step, boolean needsFruit, int difficulty, List<Reward> rewards) {
        this.difficulty = difficulty;
        this.id = id;
        this.min = min;
        this.max = max;
        this.step = step;
        this.needsFruit = needsFruit;
        this.rewards = rewards;
    }

    /** Difficulté réelle d'une quête de ce type pour cet objectif : 1 à 4. */
    public int difficulty(int target) {
        boolean hard = this.max > this.min && target >= this.min + (this.max - this.min) * 2 / 3;
        int tier = this.difficulty + (hard ? 1 : 0);
        return tier >= 4 && target < this.max ? 3 : Math.min(4, tier); // palier 4 (rare) : seulement l'objectif maximal
    }

    /**
     * Cristaux d'XP TOUJOURS donnés en plus de la récompense tirée, selon la difficulté (quêtes simples : récompense modérée) :
     * 1 = 1 Cristal du Moussaillon (500 XP), 2 = 2 Moussaillon, 3 = 3 Moussaillon, 4 = 1 Cristal du Pirate (5 000 XP, rare : chasse aux
     * pirates à l'objectif maximal seulement). Calculé à partir du type et de l'objectif : rien à enregistrer en plus.
     */
    public Reward crystal(int target) {
        return switch (difficulty(target)) {
            case 1 -> Reward.item("bloxpiece:xp_crystal_cabin_boy", 1);
            case 2 -> Reward.item("bloxpiece:xp_crystal_cabin_boy", 2);
            case 3 -> Reward.item("bloxpiece:xp_crystal_cabin_boy", 3);
            default -> Reward.item("bloxpiece:xp_crystal_pirate", 1);
        };
    }

    public String titleKey() {
        return "quest.bloxpiece." + this.id + ".title";
    }

    public String objectiveKey() {
        return "quest.bloxpiece." + this.id + ".objective";
    }

    public static QuestType byId(String id) {
        for (QuestType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
