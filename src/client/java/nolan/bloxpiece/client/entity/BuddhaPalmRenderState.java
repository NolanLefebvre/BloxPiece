package nolan.bloxpiece.client.entity;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Données de rendu de la Paume du Bouddha ; le temps d'animation vient de ageInTicks. */
public class BuddhaPalmRenderState extends EntityRenderState {

    /** Direction horizontale du vol (lacet, degrés, convention Minecraft). */
    public float yaw;

    /** Inclinaison du vol (degrés, positif = vers le bas, comme la tête d'un mob). */
    public float pitch;

    /** Échelle d'affichage : la main jaillit du ciel (0 -> 1, léger dépassement), puis se dissout après l'impact (1 -> 0). */
    public float scale = 1.0F;

    /** Ouverture des doigts : 0 = à demi repliés (apparition), 1 = grands ouverts (impact). */
    public float spread = 1.0F;
}
