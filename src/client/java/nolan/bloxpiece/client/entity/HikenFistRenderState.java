package nolan.bloxpiece.client.entity;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Données de rendu du poing de Hiken : sa direction (lacet et inclinaison, en degrés) ; le temps d'animation vient de ageInTicks. */
public class HikenFistRenderState extends EntityRenderState {

    /** Direction horizontale du vol (lacet, degrés, convention Minecraft). */
    public float yaw;

    /** Inclinaison du vol (degrés, positif = vers le bas, comme la tête d'un mob). */
    public float pitch;
}
