package nolan.bloxpiece.progression;

/**
 * LE barème d'XP de BloxPiece (calculs purs, sans Minecraft) : XP d'un mob tué ({@link #mobXp}) et récompense d'une quête
 * répétable ({@link #questReward}). Pour rééquilibrer, modifier UNIQUEMENT les constantes de ce fichier (MobXp et les quêtes
 * répétables s'en servent).
 *
 * <h3>XP d'un mob</h3>
 * {@code brut = HEALTH_WEIGHT x PV + DAMAGE_WEIGHT x dégâts + MIX_WEIGHT x racine(PV x dégâts)} : les PV comptent peu seuls
 * (un sac à PV inoffensif rapporte peu), les dégâts comptent davantage, et le terme mixte récompense ce qui est à la fois
 * résistant ET dangereux. Puis x le rôle (normal, élite, capitaine, boss), x un coefficient propre au mob (1 par défaut), x
 * {@link #MOB_SCALE}, borné entre {@link #MOB_MIN} et {@link #MOB_MAX}.
 * Valeurs obtenues (courbe des niveaux : 100 x niveau^1,5 XP par niveau) : Pirate Tireur 15, Pirate Sabreur 18, Bandit 13,
 * Marine Épéiste 18, Marine Fusilier 16, Humandrill 49, Chef des bandits 63, Capitaine Pirate 88, Morgan 298, Ener 335,
 * Gol D. Roger 647. Au niveau 1, six pirates font un niveau ; au niveau 10, environ 180 : on progresse en combattant, mais
 * lentement.
 *
 * <h3>Récompense d'une quête répétable</h3>
 * {@code (somme des XP des ennemis demandés) x QUEST_MULTIPLIER[difficulté] + QUEST_BONUS[difficulté]}, arrondie à 10, bornée
 * par {@link #QUEST_MAX}. Elle s'AJOUTE à l'XP normale de chaque ennemi tué (jamais comptée deux fois) : une mission rapporte
 * en tout environ 3,5 fois (facile) à 4-4,5 fois (moyenne), 5-6 fois (difficile) et 6,5-9,5 fois (très difficile) l'XP des
 * mêmes combats sans mission. Ex. : 10 pirates = ~165 XP + prime ~430 ; 4 Capitaines Pirates = ~350 + ~1 650 ; Morgan = 298 + 2 540.
 */
public final class XpBalance {

    // ------------------------------------------------------------------ mobs

    public static final double HEALTH_WEIGHT = 0.18;
    public static final double DAMAGE_WEIGHT = 1.2;
    public static final double MIX_WEIGHT = 0.35;
    /** Multiplicateur général de l'XP des mobs. */
    public static final double MOB_SCALE = 1.25;
    public static final int MOB_MIN = 5;
    public static final int MOB_MAX = 800;

    /** Rôle d'un mob : multiplicateur de son XP. */
    public enum Role {
        NORMAL(1.0), ELITE(1.5), CAPTAIN(2.0), BOSS(3.0);

        public final double multiplier;

        Role(double multiplier) {
            this.multiplier = multiplier;
        }
    }

    /**
     * Rôle déduit automatiquement des PV quand un mob n'en a pas de déclaré (futurs mobs) : beaucoup de PV = plus qu'un mob
     * ordinaire. Seuils réglables.
     */
    public static final double AUTO_BOSS_HEALTH = 200.0;
    public static final double AUTO_CAPTAIN_HEALTH = 85.0;
    public static final double AUTO_ELITE_HEALTH = 50.0;

    public static Role autoRole(double health) {
        return health >= AUTO_BOSS_HEALTH ? Role.BOSS : health >= AUTO_CAPTAIN_HEALTH ? Role.CAPTAIN
                : health >= AUTO_ELITE_HEALTH ? Role.ELITE : Role.NORMAL;
    }

    /** XP d'un mob d'après ses PV max, ses dégâts de base, son rôle et son coefficient propre. */
    public static int mobXp(double health, double damage, Role role, double coefficient) {
        double hp = Math.max(0.0, health), dmg = Math.max(0.0, damage);
        double raw = HEALTH_WEIGHT * hp + DAMAGE_WEIGHT * dmg + MIX_WEIGHT * Math.sqrt(hp * dmg);
        double xp = raw * role.multiplier * coefficient * MOB_SCALE;
        return (int) Math.max(MOB_MIN, Math.min(MOB_MAX, Math.round(xp)));
    }

    // ------------------------------------------------------------------ quêtes répétables

    /** Difficultés : 0 facile, 1 moyenne, 2 difficile, 3 très difficile. */
    public static final double[] QUEST_MULTIPLIER = {2.0, 2.5, 3.0, 3.5};
    public static final int[] QUEST_BONUS = {100, 250, 600, 1500};
    public static final int QUEST_MAX = 12000;

    /** Récompense d'une quête : {@code killXp} = somme des XP normales des ennemis demandés. */
    public static int questReward(int difficulty, double killXp) {
        int d = Math.max(0, Math.min(QUEST_MULTIPLIER.length - 1, difficulty));
        double xp = killXp * QUEST_MULTIPLIER[d] + QUEST_BONUS[d];
        return (int) Math.min(QUEST_MAX, Math.round(xp / 10.0) * 10L);
    }

    private XpBalance() {
    }
}
