package nolan.bloxpiece.client.screen;

/**
 * Les catégories de la page Quêtes (onglets). Une seule pour l'instant ; pour en ajouter une (principales, hebdomadaires...) :
 * une valeur ici, son nom dans la lang, et son contenu dans QuestsScreen.
 */
public enum QuestCategory {
    DAILY("daily");

    public final String id;

    QuestCategory(String id) {
        this.id = id;
    }

    public String titleKey() {
        return "screen.bloxpiece.quests.category." + this.id;
    }
}
