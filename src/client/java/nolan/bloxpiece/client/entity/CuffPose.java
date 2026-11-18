package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Pose "mains dans le dos" d'un humanoïde menotté (joueurs : PlayerModelMixin ; mobs : HumanoidModelMixin et CuffedMobModelMixin). */
public final class CuffPose {

    private CuffPose() {
    }

    /** Applique la pose si cet état de rendu est celui d'une entité menottée. */
    public static void applyIfCuffed(HumanoidModel<?> model, EntityRenderState state) {
        if (state instanceof CuffedRenderState cuffed && cuffed.bloxpiece$isCuffed()) {
            apply(model);
        }
    }

    /** Les deux bras ramenés dans le dos, poignets rapprochés. Bras droit vers l'intérieur = zRot négatif. */
    public static void apply(HumanoidModel<?> model) {
        model.rightArm.xRot = 0.62F;
        model.rightArm.yRot = 0.0F;
        model.rightArm.zRot = -0.36F;
        model.leftArm.xRot = 0.62F;
        model.leftArm.yRot = 0.0F;
        model.leftArm.zRot = 0.36F;
    }
}
