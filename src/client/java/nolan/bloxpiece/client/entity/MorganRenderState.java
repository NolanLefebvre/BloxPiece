package nolan.bloxpiece.client.entity;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** État de rendu de Morgan : attaque à la hache en cours (ticks depuis le début, -1 : aucune) et son type (MorganAxeGoal). */
public class MorganRenderState extends HumanoidRenderState {
    public float axeSwing = -1.0F;
    public int axeKind;
}
