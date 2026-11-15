package nolan.bloxpiece.progression;

/**
 * La courbe de progression : combien d'XP faut-il pour passer au niveau suivant.
 * Pour changer la difficulté, modifie uniquement ce fichier.
 */
public final class LevelCurve {

    public static final int START_LEVEL = 1;
    public static final int MAX_LEVEL = 100;

    private LevelCurve() {
    }

    /**
     * XP à gagner pour passer de {@code level} à {@code level + 1}. Augmente avec le niveau :
     * niveau 1 -> 100, niveau 2 -> 283, niveau 10 -> 3 162, niveau 50 -> 35 355...
     */
    public static long xpForNextLevel(int level) {
        return Math.round(100.0 * Math.pow(level, 1.5));
    }

    public static boolean isMaxLevel(int level) {
        return level >= MAX_LEVEL;
    }
}
