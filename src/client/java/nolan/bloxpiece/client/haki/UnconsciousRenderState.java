package nolan.bloxpiece.client.haki;

/**
 * Ajouté à l'état de rendu de toutes les entités vivantes (LivingEntityRenderStateMixin) : l'entité est-elle évanouie par un
 * Haki des Rois ? Rempli à chaque image (LivingEntityRendererMixin), qui la couche alors sur le côté au ras du sol.
 */
public interface UnconsciousRenderState {

    boolean bloxpiece$isUnconscious();

    void bloxpiece$setUnconscious(boolean unconscious);
}
