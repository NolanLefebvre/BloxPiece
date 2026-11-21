package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;

/**
 * Silvers Rayleigh : géométrie RayleighModels (générée), animations d'attente.
 * <ul>
 *   <li>La tête suit le joueur (vanilla) et monte/descend à peine avec la respiration.</li>
 *   <li>Mains dans les poches, sous la cape : bras le long du corps, coudes un peu en avant, serrés contre les hanches.</li>
 *   <li>Les deux longues mèches du visage ondulent doucement (brise).</li>
 * </ul>
 */
public class RayleighModel extends HumanoidModel<HumanoidRenderState> {

    private final ModelPart lockRight;
    private final ModelPart lockLeft;

    public RayleighModel(ModelPart root) {
        super(root);
        ModelPart head = root.getChild("head");
        this.lockRight = head.getChild("hair_lock_front_right");
        this.lockLeft = head.getChild("hair_lock_front_left");
    }

    @Override
    public void setupAnim(HumanoidRenderState state) {
        super.setupAnim(state);
        float t = state.ageInTicks;
        float breath = Mth.sin(t * 0.08F);
        float breeze = Mth.sin(t * 0.045F) + 0.4F * Mth.sin(t * 0.11F + 1.3F);
        // mains dans les poches : pose fixe (pas de balancement), qui suit juste la respiration
        this.rightArm.xRot = -0.12F;
        this.rightArm.yRot = 0.0F;
        this.rightArm.zRot = -0.05F - 0.01F * breath;
        this.leftArm.xRot = -0.12F;
        this.leftArm.yRot = 0.0F;
        this.leftArm.zRot = 0.05F + 0.01F * breath;
        // respiration
        this.head.y = 0.1F * breath; // position de repos de la tête : y = 0
        // brise : les deux mèches du visage (valeurs de repos : celles de RayleighModels ; la cape reste fermée, sans écart aux coins)
        this.lockRight.zRot = 0.06F + 0.03F * breeze;
        this.lockLeft.zRot = -0.06F - 0.03F * breeze;
    }
}
