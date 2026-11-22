package nolan.bloxpiece.client.screen;

import java.util.List;

import nolan.bloxpiece.network.PartySyncPayload;

/**
 * Copie d'affichage de la Party du joueur, remplacée à chaque PartySyncPayload du serveur. La page Party la relit à chaque
 * image : elle se met à jour toute seule (arrivée, départ, nouveau chef, connexion...). Aucune décision n'est prise ici.
 */
public final class ClientParty {

    private static PartySyncPayload state = new PartySyncPayload(false, "", "", "", 5, List.of(), List.of(), List.of());

    private ClientParty() {
    }

    public static PartySyncPayload get() {
        return state;
    }

    public static void set(PartySyncPayload payload) {
        state = payload;
    }

    /** Déconnexion du serveur : plus de Party affichée. */
    public static void clear() {
        state = new PartySyncPayload(false, "", "", "", 5, List.of(), List.of(), List.of());
    }
}
