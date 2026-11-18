package nolan.bloxpiece.client.entity;

/**
 * Ajouté à l'état de rendu de toutes les entités vivantes (LivingEntityRenderStateMixin) : l'entité est-elle menottée ?
 * Rempli à chaque image (LivingEntityRendererMixin), lu par la pose des bras (CuffPose) et par MobHandcuffsLayer.
 */
public interface CuffedRenderState {

    boolean bloxpiece$isCuffed();

    void bloxpiece$setCuffed(boolean cuffed);
}
