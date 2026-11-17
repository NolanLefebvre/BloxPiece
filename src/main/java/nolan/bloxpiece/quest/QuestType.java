package nolan.bloxpiece.quest;

import java.util.List;

/**
 * Les types de quêtes journalières : objectif (fourchette tirée au hasard, arrondie à {@code step}) et récompenses possibles
 * (une seule est tirée par quête). Récompenses utiles mais modestes : petits cristaux d'XP, nourriture, quelques lingots, un
 * peu d'XP BloxPiece ; jamais de fruit du démon ni d'équipement de fin de jeu. Pour rééquilibrer, modifie uniquement ce fichier.
 */
public enum QuestType {

    KILL_PIRATES("kill_pirates", 8, 12, 1, false, List.of(
            Reward.item("bloxpiece:xp_crystal_cabin_boy", 2), Reward.item("minecraft:gold_ingot", 4),
            Reward.xp(1000), Reward.item("bloxpiece:xp_crystal_pirate", 1))),
    KILL_HOSTILES("kill_hostiles", 12, 18, 1, false, List.of(
            Reward.item("bloxpiece:xp_crystal_cabin_boy", 1), Reward.item("minecraft:iron_ingot", 6),
            Reward.item("minecraft:cooked_beef", 8))),
    USE_ABILITIES("use_abilities", 10, 15, 1, true, List.of(
            Reward.item("bloxpiece:xp_crystal_cabin_boy", 1), Reward.item("minecraft:golden_apple", 1), Reward.xp(600))),
    TRAVEL("travel", 800, 1200, 100, false, List.of(
            Reward.item("minecraft:bread", 12), Reward.item("minecraft:cooked_beef", 6),
            Reward.item("bloxpiece:xp_crystal_cabin_boy", 1))),
    MINE_BLOCKS("mine_blocks", 64, 96, 8, false, List.of(
            Reward.item("minecraft:iron_ingot", 5), Reward.item("minecraft:gold_ingot", 3), Reward.xp(500)));

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

    QuestType(String id, int min, int max, int step, boolean needsFruit, List<Reward> rewards) {
        this.id = id;
        this.min = min;
        this.max = max;
        this.step = step;
        this.needsFruit = needsFruit;
        this.rewards = rewards;
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
