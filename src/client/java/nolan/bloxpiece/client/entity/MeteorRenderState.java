package nolan.bloxpiece.client.entity;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Données de rendu d'un météore : direction de la chute, et variations propres à chaque météore (taille, vitesse et sens de rotation). */
public class MeteorRenderState extends EntityRenderState {

    /** Direction du vol : lacet autour de Y et inclinaison (degrés), pour placer la traînée derrière le météore. */
    public float yaw;
    public float pitch;

    /** Taille du modèle (identique pour un même météore chez tous les joueurs : tirée de son UUID). */
    public float scale = 1.0F;

    /** Multiplicateur de vitesse de rotation, sens de rotation (+1 / -1) et décalage de départ de l'animation. */
    public float spin = 1.0F;
    public float spinSign = 1.0F;
    public float phase;
}
