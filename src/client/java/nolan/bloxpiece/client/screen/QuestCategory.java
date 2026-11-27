package nolan.bloxpiece.client.screen;

/**
 * Les catégories de la page Quêtes (onglets) : journalières et uniques. Pour en ajouter une (principales, hebdomadaires...) :
 * une valeur ici, son nom dans la lang, et son contenu dans QuestsScreen.
 */
public enum QuestCategory {
    /** Quêtes journalières : renouvelées chaque jour Minecraft. */
    DAILY("daily"),
    /** Quêtes répétables : missions du Tavernier (une à la fois, une nouvelle après chaque récompense). */
    REPEATABLE("repeatable"),
    /** Quêtes uniques : réalisables une seule fois dans toute la partie (quêtes de Haki de Rayleigh : UniqueQuest). */
    UNIQUE("unique");

    public final String id;

    QuestCategory(String id) {
        this.id = id;
    }

    public String titleKey() {
        return "screen.bloxpiece.quests.category." + this.id;
    }
}
