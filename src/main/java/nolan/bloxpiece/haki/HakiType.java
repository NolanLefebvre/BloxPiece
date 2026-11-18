package nolan.bloxpiece.haki;

/**
 * Les trois Hakis de base. Chacun a sa touche (0, 1, 2 = ordre des cases du HUD), son bit de déblocage, et l'identifiant de
 * sa future forme avancée (pas encore implémentée : voir {@link Haki#advanced}).
 */
public enum HakiType {
    ARMAMENT("armament", "ryuo"),
    OBSERVATION("observation", "future_sight"),
    CONQUEROR("conqueror", "conqueror_coating");

    public final String id;
    /** Forme avancée prévue (Ryuo, Vision du futur, Revêtement des Rois). */
    public final String advancedId;

    HakiType(String id, String advancedId) {
        this.id = id;
        this.advancedId = advancedId;
    }

    public int slot() {
        return this.ordinal();
    }

    public int bit() {
        return 1 << this.ordinal();
    }

    public String nameKey() {
        return "haki.bloxpiece." + this.id + ".name";
    }

    public String descriptionKey() {
        return "haki.bloxpiece." + this.id + ".description";
    }

    /** Clé de recharge (partagée avec le HUD client, comme pour les attaques de fruit). */
    public String cooldownKey() {
        return "haki:" + this.id;
    }

    public static HakiType bySlot(int slot) {
        return slot >= 0 && slot < values().length ? values()[slot] : null;
    }
}
