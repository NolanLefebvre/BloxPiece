package nolan.bloxpiece.client.screen;

/**
 * Les catégories de la page Quêtes (onglets) : journalières et uniques. Pour en ajouter une (principales, hebdomadaires...) :
 * une valeur ici, son nom dans la lang, et son contenu dans QuestsScreen.
 */
public enum QuestCategory {
    /** Quêtes journalières : renouvelées chaque jour Minecraft. */
    DAILY("daily"),
    /** Quêtes uniques : réalisables une seule fois dans toute la partie (contenu à venir). */
    UNIQUE("unique");

    public final String id;

    QuestCategory(String id) {
        this.id = id;
    }

    public String titleKey() {
        return "screen.bloxpiece.quests.category." + this.id;
    }
}
