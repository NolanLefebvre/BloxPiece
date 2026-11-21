package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import nolan.bloxpiece.mob.ai.MorganAxeGoal;

/**
 * Morgan : géométrie MorganModels (générée, 3 blocs de haut), animations. Les poses du bras à la hache viennent de
 * make_morgan.py (calculées pour que la hache ne traverse ni la tête ni le corps) ; les durées et l'angle de la rotation sont
 * ceux de MorganAxeGoal (mêmes moments que les dégâts).
 * <ul>
 *   <li>Repos, marche et poursuite : hache posée sur l'épaule droite, derrière la tête, le fer en l'air (comme sur les images) ;
 *       respiration lente.</li>
 *   <li>Marche : grandes enjambées LENTES (cadence réduite pour ses jambes de 19 px), le bras gauche balance, les pans du manteau
 *       s'écartent avec les jambes (elles ne les traversent pas).</li>
 *   <li>Coup classique : préparation (hache levée haut derrière lui), frappe rapide (impact à HIT_TICK), récupération.</li>
 *   <li>Rotation : il tend la hache à l'horizontale sur le côté, jambes écartées, puis tout le modèle tourne sur lui-même
 *       (spinDegrees : accélère, 4 tours, ralentit) ; la hache revient sur l'épaule.</li>
 *   <li>Coup au sol : hache levée au-dessus de la tête, court temps d'arrêt (elle tremble), chute violente jusqu'au sol devant lui
 *       (impact à SLAM_HIT), fente en avant ; récupération.</li>
 *   <li>Mort : chute vanilla sur le côté.</li>
 * </ul>
 */
public class MorganModel extends HumanoidModel<MorganRenderState> {

    // poses du bras droit (épaule) et de l'avant-bras (coude) : xRot, yRot, zRot
    private static final float[] REST_ARM = {-1.088F, -1.872F, -3.14F};
    private static final float[] REST_FORE = {-0.635F, 2.888F, -2.643F};
    private static final float[] PREP_ARM = {0.648F, -1.032F, -4.044F};
    private static final float[] PREP_FORE = {-2.039F, 2.113F, -1.946F};
    private static final float[] HIT_ARM = {1.557F, -3.245F, -4.71F};
    private static final float[] HIT_FORE = {-1.801F, 1.689F, -1.694F};
    private static final float[] SPIN_ARM = {-1.137F, -1.536F, -3.667F};
    private static final float[] SPIN_FORE = {-1.944F, 1.63F, -1.991F};
    private static final float[] SLAM_ARM = {1.616F, -4.138F, -4.689F};
    private static final float[] SLAM_FORE = {-1.405F, 1.593F, -1.674F};
    /** Cadence de marche : celle d'un humanoïde x 0,6 (jambes plus longues). */
    private static final float STRIDE = 0.6662F * 0.6F;

    private final ModelPart forearm;
    private final ModelPart skirtBack;
    private final ModelPart skirtFrontRight;
    private final ModelPart skirtFrontLeft;

    public MorganModel(ModelPart root) {
        super(root);
        this.forearm = this.rightArm.getChild("forearm_right");
        this.skirtBack = this.body.getChild("skirt_back");
        this.skirtFrontRight = this.body.getChild("skirt_front_right");
        this.skirtFrontLeft = this.body.getChild("skirt_front_left");
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private void armPose(float[] a0, float[] f0, float[] a1, float[] f1, float t) {
        this.rightArm.xRot = Mth.lerp(t, a0[0], a1[0]);
        this.rightArm.yRot = Mth.lerp(t, a0[1], a1[1]);
        this.rightArm.zRot = Mth.lerp(t, a0[2], a1[2]);
        this.forearm.xRot = Mth.lerp(t, f0[0], f1[0]);
        this.forearm.yRot = Mth.lerp(t, f0[1], f1[1]);
        this.forearm.zRot = Mth.lerp(t, f0[2], f1[2]);
    }

    @Override
    public void setupAnim(MorganRenderState state) {
        super.setupAnim(state); // tête vers la cible / le joueur
        float speed = Math.min(state.walkAnimationSpeed, 1.0F);
        float phase = state.walkAnimationPos * STRIDE;
        float breath = Mth.sin(state.ageInTicks * 0.07F);

        // jambes : grandes enjambées lentes
        this.rightLeg.xRot = Mth.cos(phase) * 0.8F * speed;
        this.leftLeg.xRot = Mth.cos(phase + Mth.PI) * 0.8F * speed;
        this.rightLeg.yRot = this.leftLeg.yRot = 0.0F;
        this.rightLeg.zRot = this.leftLeg.zRot = 0.0F;
        // bras gauche : balancement, poing un peu écarté du manteau
        this.leftArm.xRot = Mth.cos(phase) * 0.55F * speed - 0.05F * breath;
        this.leftArm.yRot = 0.0F;
        this.leftArm.zRot = -0.06F;
        // respiration
        this.head.y = -12.0F + 0.15F * breath; // position de repos de la tête : y = -12 (MorganModels)

        // bras droit : hache sur l'épaule, ou l'une des trois attaques
        this.root().yRot = 0.0F;
        float t = state.axeSwing;
        float stance = 0.0F;   // appui de combat (0 : aucun, 1 : complet)
        float wide = 0.0F;     // jambes écartées sur les côtés (rotation)
        boolean power = false; // coup au sol, temps d'arrêt : posture de puissance
        if (t < 0.0F) {
            this.armPose(REST_ARM, REST_FORE, REST_ARM, REST_FORE, 0.0F);
        } else if (state.axeKind == MorganAxeGoal.SPIN) {
            if (t < MorganAxeGoal.SPIN_PREP) {                              // il tend la hache sur le côté
                float k = ease(t / MorganAxeGoal.SPIN_PREP);
                this.armPose(REST_ARM, REST_FORE, SPIN_ARM, SPIN_FORE, k);
                wide = k;
            } else if (t < MorganAxeGoal.SPIN_STOP) {                      // il tourne
                this.armPose(SPIN_ARM, SPIN_FORE, SPIN_ARM, SPIN_FORE, 0.0F);
                wide = 1.0F;
            } else {                                                       // il se redresse, hache sur l'épaule
                float k = ease((t - MorganAxeGoal.SPIN_STOP) / (MorganAxeGoal.SPIN_END - MorganAxeGoal.SPIN_STOP));
                this.armPose(SPIN_ARM, SPIN_FORE, REST_ARM, REST_FORE, k);
                wide = 1.0F - k;
            }
            this.root().yRot = -MorganAxeGoal.spinDegrees(t) * Mth.DEG_TO_RAD; // même angle que les dégâts (le fil mène)
        } else if (state.axeKind == MorganAxeGoal.SLAM) {
            if (t < MorganAxeGoal.SLAM_RAISE) {                             // hache levée au-dessus de la tête
                float k = ease(t / MorganAxeGoal.SLAM_RAISE);
                this.armPose(REST_ARM, REST_FORE, PREP_ARM, PREP_FORE, k);
                stance = k;
            } else if (t < MorganAxeGoal.SLAM_HOLD) {                       // court temps d'arrêt : la hache tremble
                this.armPose(PREP_ARM, PREP_FORE, PREP_ARM, PREP_FORE, 0.0F);
                this.rightArm.xRot += 0.03F * Mth.sin(t * 2.6F);
                stance = 1.0F;
                power = true;
            } else if (t < MorganAxeGoal.SLAM_HIT) {                        // chute violente jusqu'au sol
                float k = (t - MorganAxeGoal.SLAM_HOLD) / (MorganAxeGoal.SLAM_HIT - MorganAxeGoal.SLAM_HOLD);
                this.armPose(PREP_ARM, PREP_FORE, SLAM_ARM, SLAM_FORE, k * k);
                stance = 1.0F;
            } else {                                                       // fer dans le sol un instant, puis retour
                float k = ease((t - MorganAxeGoal.SLAM_HIT - 4.0F) / (MorganAxeGoal.SLAM_END - MorganAxeGoal.SLAM_HIT - 4.0F));
                this.armPose(SLAM_ARM, SLAM_FORE, REST_ARM, REST_FORE, k);
                stance = 1.0F - k;
            }
        } else if (t < MorganAxeGoal.PREP_TICKS) {                       // coup classique : préparation
            float k = ease(t / MorganAxeGoal.PREP_TICKS);
            this.armPose(REST_ARM, REST_FORE, PREP_ARM, PREP_FORE, k);
            stance = k;
        } else if (t < MorganAxeGoal.HIT_TICK) {                         // frappe : rapide, de plus en plus vite
            float k = (t - MorganAxeGoal.PREP_TICKS) / (MorganAxeGoal.HIT_TICK - MorganAxeGoal.PREP_TICKS);
            this.armPose(PREP_ARM, PREP_FORE, HIT_ARM, HIT_FORE, k * k);
            stance = 1.0F;
        } else {                                                         // récupération : retour sur l'épaule
            float k = ease((t - MorganAxeGoal.HIT_TICK) / (MorganAxeGoal.END_TICK - MorganAxeGoal.HIT_TICK));
            this.armPose(HIT_ARM, HIT_FORE, REST_ARM, REST_FORE, k);
            stance = 1.0F - k;
        }
        if (stance > 0.0F) {
            // appui : pied gauche en avant, droit en arrière (petits angles : les pieds restent au sol), poing gauche en garde
            this.leftLeg.xRot = Mth.lerp(stance, this.leftLeg.xRot, -0.22F);
            this.rightLeg.xRot = Mth.lerp(stance, this.rightLeg.xRot, 0.18F);
            this.leftArm.xRot = Mth.lerp(stance, this.leftArm.xRot, -0.55F);
            this.leftArm.zRot = Mth.lerp(stance, this.leftArm.zRot, -0.15F);
        }
        if (power) {
            // posture de puissance : il se cambre en arrière, le poing gauche serré tendu devant lui
            this.head.xRot -= 0.25F;
            this.leftArm.xRot = -1.2F;
            this.leftArm.zRot = -0.25F;
        }
        if (wide > 0.0F) {
            // rotation : jambes écartées sur les côtés (petits angles), bras gauche replié contre le torse pour l'élan
            this.rightLeg.xRot = Mth.lerp(wide, this.rightLeg.xRot, 0.0F);
            this.leftLeg.xRot = Mth.lerp(wide, this.leftLeg.xRot, 0.0F);
            this.rightLeg.zRot = 0.12F * wide;
            this.leftLeg.zRot = -0.12F * wide;
            this.leftArm.xRot = Mth.lerp(wide, this.leftArm.xRot, -0.9F);
            this.leftArm.yRot = -0.5F * wide;
            this.leftArm.zRot = Mth.lerp(wide, this.leftArm.zRot, -0.1F);
        }

        // pans du manteau : ils suivent la jambe qui avance (devant) ou qui recule (dos)
        this.skirtFrontRight.xRot = Math.min(0.0F, this.rightLeg.xRot);
        this.skirtFrontLeft.xRot = Math.min(0.0F, this.leftLeg.xRot);
        this.skirtBack.xRot = Math.max(0.0F, Math.max(this.rightLeg.xRot, this.leftLeg.xRot));
    }
}
