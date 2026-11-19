package nolan.bloxpiece.job;

import org.jspecify.annotations.Nullable;

/**
 * Les jobs (classes) que le joueur peut choisir à partir du niveau {@link Jobs#REQUIRED_LEVEL}, dans le menu du personnage.
 * Pour en ajouter un : une valeur de plus ici (+ son nom, sa description et son icône dans les ressources), puis son effet dans
 * Jobs / le système concerné.
 */
public enum Job {
    /** Épéiste : le Haki de l'Armement se propage sur l'arme tenue (revêtement + bonus de dégâts de l'arme). */
    SWORDMAN("swordman", 0xFF8FC8FF),
    /** Bagarreur : poings nus renforcés (gros bonus de dégâts à mains nues, encore plus avec le Haki de l'Armement). */
    BRAWLER("brawler", 0xFFFF8A5A),
    /** Sniper : la jauge de Haki se vide beaucoup moins vite (surtout avec le Haki de l'Observation). */
    SNIPER("sniper", 0xFF7BE07B);

    public final String id;
    public final int color;

    Job(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String nameKey() {
        return "job.bloxpiece." + id + ".name";
    }

    public String descriptionKey() {
        return "job.bloxpiece." + id + ".description";
    }

    public static @Nullable Job byId(@Nullable String id) {
        for (Job job : values()) {
            if (job.id.equals(id)) {
                return job;
            }
        }
        return null;
    }
}
