package nolan.bloxpiece.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;

import nolan.bloxpiece.mob.ai.MorganAxeGoal;

/**
 * Rotation de Morgan : traînée de la hache. Dans le repère du modèle, sur l'anneau que balaie le fer (hache tendue à
 * l'horizontale : rayon {@link #R_IN} à {@link #R_OUT} blocs, à hauteur d'épaule), une bande claire qui suit exactement la lame
 * (même angle que l'animation et que les dégâts : MorganAxeGoal.spinDegrees) et s'étire derrière elle selon la vitesse ; une
 * bande d'air plus pâle et plus large autour (le manche, l'air déplacé). Rien hors de la rotation.
 */
public class MorganSpinTrailLayer extends RenderLayer<MorganRenderState, MorganModel> {

    private static final float R_IN = 2.05F, R_OUT = 2.6F;
    /** Hauteur du fer pendant la rotation (repère du modèle, blocs ; y vers le bas). */
    private static final float BLADE_Y = -8.6F / 16.0F;
    private static final int STEPS = 28;

    private final ModelPart cube;

    public MorganSpinTrailLayer(RenderLayerParent<MorganRenderState, MorganModel> parent, ModelPart cube) {
        super(parent);
        this.cube = cube;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, MorganRenderState state, float yRot, float xRot) {
        float t = state.axeSwing;
        if (state.isInvisible || state.axeKind != MorganAxeGoal.SPIN || t < MorganAxeGoal.SPIN_START || t > MorganAxeGoal.SPIN_STOP) {
            return;
        }
        float speed = MorganAxeGoal.spinDegrees(t + 0.5F) - MorganAxeGoal.spinDegrees(t - 0.5F); // degrés par tick
        float power = Mth.clamp(speed / 40.0F, 0.0F, 1.0F);
        if (power <= 0.05F) {
            return;
        }
        float trail = Math.min(200.0F, speed * 3.4F);                 // longueur de la traînée (degrés derrière la lame)
        float blade = -MorganAxeGoal.spinDegrees(t);                  // angle du modèle (root.yRot, en degrés)
        float step = trail / STEPS;
        for (int k = 0; k < STEPS; k++) {
            float back = 1.0F - (float) k / STEPS;                    // 1 contre la lame, 0 au bout de la traînée
            float a = (blade + k * step) * Mth.DEG_TO_RAD;            // positions passées de la lame
            float rm = (R_IN + R_OUT) * 0.5F;
            float arc = step * Mth.DEG_TO_RAD * rm + 0.02F;
            int core = MorganFx.argb(0.6F * power * back * back, 0xF4F6F8);
            this.band(pose, collector, a, rm, arc, (R_OUT - R_IN) * 16.0F, 0.8F, core);
            int air = MorganFx.argb(0.18F * power * back, 0xDDE6EE);   // air déplacé : plus large, plus pâle
            this.band(pose, collector, a, rm - 0.25F, arc, (R_OUT - R_IN + 1.2F) * 16.0F, 2.5F, air);
        }
    }

    /** Un tronçon de la traînée à l'angle a (rad) : direction de la hache tendue = (-cos a, 0, sin a), tangent à l'anneau. */
    private void band(PoseStack pose, SubmitNodeCollector collector, float a, float radius, float length, float width, float height,
                      int color) {
        pose.pushPose();
        pose.translate(-Mth.cos(a) * radius, BLADE_Y, Mth.sin(a) * radius);
        pose.rotateDegrees(Axis.YP, a * Mth.RAD_TO_DEG);
        pose.scale(width, height, length * 16.0F);
        collector.order(1).submitModelPart(this.cube, pose, MorganFx.type(), MorganFx.FULL_BRIGHT,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }
}
