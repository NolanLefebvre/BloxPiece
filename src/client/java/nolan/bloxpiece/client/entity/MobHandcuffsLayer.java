package nolan.bloxpiece.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;

/**
 * Menottes portées par un MOB humanoïde (zombies, squelettes, piglins, pirates...) : mêmes bracelets et même chaîne que sur un
 * joueur (même modèle que HandcuffsLayer), dessinés dans le repère des bras du mob (bras dans le dos : CuffPose).
 */
public class MobHandcuffsLayer extends RenderLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/handcuffs.png");

    private final ModelPart rightCuff;
    private final ModelPart leftCuff;
    private final ModelPart chain;

    public MobHandcuffsLayer(RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> parent, ModelPart root) {
        super(parent);
        this.rightCuff = root.getChild("right_cuff");
        this.leftCuff = root.getChild("left_cuff");
        this.chain = root.getChild("chain");
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot) {
        if (!(state instanceof CuffedRenderState cuffed) || !cuffed.bloxpiece$isCuffed() || state.isInvisible) {
            return;
        }
        HumanoidModel<HumanoidRenderState> model = this.getParentModel();
        RenderType type = RenderTypes.entityCutout(TEXTURE);
        int overlay = OverlayTexture.NO_OVERLAY;

        pose.pushPose();
        model.rightArm.translateAndRotate(pose);
        collector.submitModelPart(this.rightCuff, pose, type, light, overlay, null, 0xFFFFFFFF);
        collector.submitModelPart(this.chain, pose, type, light, overlay, null, 0xFFFFFFFF);
        pose.popPose();

        pose.pushPose();
        model.leftArm.translateAndRotate(pose);
        collector.submitModelPart(this.leftCuff, pose, type, light, overlay, null, 0xFFFFFFFF);
        pose.popPose();
    }
}
