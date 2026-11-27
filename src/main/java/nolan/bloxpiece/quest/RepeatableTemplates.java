package nolan.bloxpiece.quest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.RandomSource;

import nolan.bloxpiece.progression.XpBalance;

/**
 * Les missions possibles du Tavernier (quêtes répétables) et leurs probabilités. TOUT l'équilibrage des missions est ici :
 * <ul>
 *   <li>{@link #DIFFICULTY_WEIGHTS} : chance de tirer chaque difficulté (facile, moyenne, difficile, très difficile) ; les plus
 *       difficiles sont les plus rares ;</li>
 *   <li>{@link #TEMPLATES} : les missions de chaque difficulté (une ou plusieurs cibles, chacune avec un nombre tiré entre min
 *       et max). Dans une difficulté, chaque mission a la même chance ; la mission précédente du joueur n'est pas reproposée
 *       tout de suite ;</li>
 *   <li>la récompense en XP : XpBalance.questReward (difficulté, et XP normale des ennemis demandés).</li>
 * </ul>
 * Ajouter une mission = une ligne ici, son titre dans la lang (« quest.bloxpiece.repeatable.&lt;id&gt;.title »).
 */
public final class RepeatableTemplates {

    public static final int EASY = 0, MEDIUM = 1, HARD = 2, VERY_HARD = 3;
    /** Poids des difficultés (sur 100). */
    public static final int[] DIFFICULTY_WEIGHTS = {50, 30, 15, 5};

    /** Une cible de la mission et la fourchette du nombre d'ennemis demandé. */
    public record Part(QuestTarget target, int min, int max) {
    }

    public record Template(String id, int difficulty, List<Part> parts) {
    }

    private static Template t(String id, int difficulty, Part... parts) {
        return new Template(id, difficulty, List.of(parts));
    }

    private static Part p(QuestTarget target, int min, int max) {
        return new Part(target, min, max);
    }

    public static final List<Template> TEMPLATES = List.of(
            // FACILE : ennemis ordinaires, petits objectifs
            t("pirate_hunt", EASY, p(QuestTarget.PIRATES, 8, 12)),
            t("bandit_hunt", EASY, p(QuestTarget.BANDITS, 8, 12)),
            t("marine_hunt", EASY, p(QuestTarget.MARINES, 8, 12)),
            t("harbor_brawl", EASY, p(QuestTarget.PIRATES, 4, 6), p(QuestTarget.MARINES, 4, 6)),
            t("petty_crime", EASY, p(QuestTarget.BANDITS, 5, 7), p(QuestTarget.PIRATES, 4, 6)),
            // MOYENNE : davantage d'ennemis, ou un adversaire plus résistant
            t("pirate_raid", MEDIUM, p(QuestTarget.PIRATES, 15, 25)),
            t("bandit_raid", MEDIUM, p(QuestTarget.BANDITS, 15, 25)),
            t("marine_raid", MEDIUM, p(QuestTarget.MARINES, 15, 20)),
            t("sea_war", MEDIUM, p(QuestTarget.PIRATES, 8, 12), p(QuestTarget.MARINES, 8, 12)),
            t("outlaws", MEDIUM, p(QuestTarget.BANDITS, 10, 15), p(QuestTarget.PIRATES, 6, 10)),
            t("captain_and_crew", MEDIUM, p(QuestTarget.PIRATE_CAPTAINS, 1, 1), p(QuestTarget.PIRATES, 6, 10)),
            t("leader_and_band", MEDIUM, p(QuestTarget.BANDIT_LEADERS, 1, 1), p(QuestTarget.BANDITS, 6, 10)),
            // DIFFICILE : capitaines, Chefs des bandits, groupes variés
            t("captain_hunt", HARD, p(QuestTarget.PIRATE_CAPTAINS, 3, 5)),
            t("leader_hunt", HARD, p(QuestTarget.BANDIT_LEADERS, 3, 5)),
            t("pirate_fleet", HARD, p(QuestTarget.PIRATE_CAPTAINS, 2, 2), p(QuestTarget.PIRATES, 12, 18)),
            t("bandit_gangs", HARD, p(QuestTarget.BANDIT_LEADERS, 2, 2), p(QuestTarget.BANDITS, 12, 18)),
            t("three_factions", HARD, p(QuestTarget.PIRATES, 8, 12), p(QuestTarget.BANDITS, 8, 12), p(QuestTarget.MARINES, 8, 12)),
            // TRÈS DIFFICILE : Morgan, ou plusieurs ennemis puissants
            t("morgan", VERY_HARD, p(QuestTarget.MORGAN, 1, 1)),
            t("marine_base", VERY_HARD, p(QuestTarget.MORGAN, 1, 1), p(QuestTarget.MARINES, 15, 20)),
            t("captain_purge", VERY_HARD, p(QuestTarget.PIRATE_CAPTAINS, 5, 8)),
            t("leader_purge", VERY_HARD, p(QuestTarget.BANDIT_LEADERS, 6, 8)),
            t("warlords", VERY_HARD, p(QuestTarget.PIRATE_CAPTAINS, 3, 3), p(QuestTarget.BANDIT_LEADERS, 3, 3))
    );

    private RepeatableTemplates() {
    }

    public static Template byId(String id) {
        for (Template template : TEMPLATES) {
            if (template.id().equals(id)) {
                return template;
            }
        }
        return null;
    }

    /** Tire une nouvelle mission (difficulté pondérée, mission au hasard dans cette difficulté, sauf {@code last}). */
    public static RepeatableQuest roll(RandomSource random, String last) {
        int total = 0;
        for (int w : DIFFICULTY_WEIGHTS) {
            total += Math.max(0, w);
        }
        int r = random.nextInt(Math.max(1, total)), difficulty = 0;
        for (int d = 0; d < DIFFICULTY_WEIGHTS.length; d++) {
            r -= Math.max(0, DIFFICULTY_WEIGHTS[d]);
            if (r < 0) {
                difficulty = d;
                break;
            }
        }
        List<Template> pool = new ArrayList<>();
        for (Template template : TEMPLATES) {
            if (template.difficulty() == difficulty && !template.id().equals(last)) {
                pool.add(template);
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(TEMPLATES);
        }
        Template template = pool.get(random.nextInt(pool.size()));
        List<RepeatableQuest.Objective> objectives = new ArrayList<>();
        double killXp = 0.0;
        for (Part part : template.parts()) {
            int count = part.min() + random.nextInt(Math.max(1, part.max() - part.min() + 1));
            objectives.add(new RepeatableQuest.Objective(part.target().id, count, 0));
            killXp += count * part.target().averageXp();
        }
        return new RepeatableQuest(template.id(), template.difficulty(), objectives,
                XpBalance.questReward(template.difficulty(), killXp), false);
    }
}
