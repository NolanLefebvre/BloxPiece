package nolan.bloxpiece.client.haki;

/**
 * Ajouté à l'état de rendu de toutes les entités vivantes (LivingEntityRenderStateMixin) : l'entité est-elle évanouie par un
 * Haki des Rois, et comment tombe-t-elle ? Rempli à chaque image (LivingEntityRendererMixin) ; UnconsciousPoseMixin la couche.
 */
public interface UnconsciousRenderState {

    /** Debout (pas évanoui). */
    int STANDING = 0;
    /** Bipède (joueur, zombie, villageois...) : tombé sur le dos. */
    int ON_BACK = 1;
    /** Autre (quadrupède, araignée...) : tombé sur le côté. */
    int ON_SIDE = 2;

    /** Bipèdes dont le modèle n'est pas humanoïde (identifiants Minecraft) : ils tombent aussi sur le dos. */
    java.util.Set<String> BIPEDS = java.util.Set.of("villager", "wandering_trader", "witch", "pillager", "vindicator", "evoker",
            "illusioner", "iron_golem", "snow_golem", "warden", "chicken", "allay", "creaking", "breeze", "tavern_keeper");

    boolean bloxpiece$isUnconscious();

    void bloxpiece$setUnconscious(boolean unconscious);

    /** STANDING, ON_BACK ou ON_SIDE. */
    int bloxpiece$lieMode();

    /** Largeur de l'entité (blocs) : hauteur dont il faut la remonter une fois couchée pour qu'elle repose sur le sol. */
    float bloxpiece$lieWidth();

    void bloxpiece$setLie(int mode, float width);
}
