package nolan.bloxpiece.client.entity;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** État de rendu d'un pirate armé : en plus du vanilla, s'il est en train de viser (bras droit levé). */
public class ArmedPirateRenderState extends HumanoidRenderState {
    public boolean aiming;
    /** Numéro du skin (variante visuelle) de ce pirate. */
    public int variant;
    /** Arme tenue à deux mains (fusil) : pose de visée à deux bras. */
    public boolean twoHanded;
    /** Recul du coup de feu : 1 juste après le tir, puis 0. */
    public float recoil;
    /** Arme rangée (fusil dans le dos) : la pièce « back_rifle » du modèle est affichée. */
    public boolean weaponStowed;
    /** Identifiant de l'entité (geste et éclairs de l'épée Ace : AceSwing, AceChargeLightning). */
    public int entityId;
}
