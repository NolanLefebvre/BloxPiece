package nolan.bloxpiece.client.entity;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** État de rendu d'un pirate armé : en plus du vanilla, s'il est en train de viser (bras droit levé). */
public class ArmedPirateRenderState extends HumanoidRenderState {
    public boolean aiming;
    /** Numéro du skin (variante visuelle) de ce pirate. */
    public int variant;
}
