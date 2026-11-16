package nolan.bloxpiece.client.entity;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Données de rendu du sceau du Bouddha ; le temps d'animation vient de ageInTicks. */
public class BuddhaSealRenderState extends EntityRenderState {

    /** Tracé du sceau : 0 = invisible, 1 = entièrement dessiné. */
    public float reveal;

    /** Disparition après l'impact : 0 = intact, 1 = toutes les plaques détachées. */
    public float dissolve;

    /** Agrandissement de l'onde de choc après l'impact (1 = taille normale). */
    public float expand = 1.0F;

    /** Taille du sceau (1 = BuddhaSeal.RADIUS), synchronisée par le serveur. */
    public float size = 1.0F;
}
