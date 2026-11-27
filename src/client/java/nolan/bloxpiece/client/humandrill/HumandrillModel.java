package nolan.bloxpiece.client.humandrill;

import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Matrix3f;
import org.joml.Quaternionf;

import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;

import nolan.bloxpiece.mob.ai.HumandrillAttackGoal;

/**
 * Modèle du Humandrill : géométrie HumandrillModels (générée), rotations calculées par HumandrillAnimator à chaque image
 * (repos, garde, marche, course, virage, saut, réception, sept attaques).
 * <p>
 * <b>Épées en main</b> : vrais objets dessinés par la couche vanilla ItemInHandLayer. {@link #translateToHand} suit toute la
 * chaîne (bassin, torse, épaule, coude, poignet) puis recale le point où ItemInHandLayer pose la poignée exactement au centre du
 * poing : l'épée suit chaque animation. <b>Épée de la bouche</b> : {@link #translateToMouthGrip} (repère de la tête, poignée
 * serrée entre les crocs), utilisé par HumandrillMouthSwordLayer.
 */
public class HumandrillModel extends EntityModel<HumandrillRenderState> implements ArmedModel<HumandrillRenderState>, HeadedModel {

    /**
     * Point (px) où ItemInHandLayer place le centre de la poignée du sabre, dans le repère qu'on lui donne (main droite ; x
     * opposé pour la gauche) : décalage fixe de la couche (±1, 2, -10 après ses rotations) et cadrage « thirdperson » du sabre
     * (le même que celui d'Ace).
     */
    static final float[] VANILLA_GRIP = {-1.0F, 8.66F, -0.23F};

    private final ModelPart waist, torso, head, jaw, cloth, tail, tailTip;
    private final ModelPart rArm, rFore, rHand, lArm, lFore, lHand;
    private final ModelPart rThigh, rShin, rFoot, lThigh, lShin, lFoot;
    private final HumandrillAnimator.Input input = new HumandrillAnimator.Input();
    private final HumandrillAnimator.Rig rig = new HumandrillAnimator.Rig();
    private final Quaternionf mouthRotation;

    public HumandrillModel(ModelPart root) {
        super(root);
        this.waist = root.getChild("waist");
        this.torso = this.waist.getChild("torso");
        this.head = this.torso.getChild("head");
        this.jaw = this.head.getChild("jaw");
        this.cloth = this.waist.getChild("cloth");
        this.tail = this.waist.getChild("tail");
        this.tailTip = this.tail.getChild("tail_tip");
        this.rArm = this.torso.getChild("right_arm");
        this.rFore = this.rArm.getChild("right_forearm");
        this.rHand = this.rFore.getChild("right_hand");
        this.lArm = this.torso.getChild("left_arm");
        this.lFore = this.lArm.getChild("left_forearm");
        this.lHand = this.lFore.getChild("left_hand");
        this.rThigh = this.waist.getChild("right_thigh");
        this.rShin = this.rThigh.getChild("right_shin");
        this.rFoot = this.rShin.getChild("right_foot");
        this.lThigh = this.waist.getChild("left_thigh");
        this.lShin = this.lThigh.getChild("left_shin");
        this.lFoot = this.lShin.getChild("left_foot");
        this.input.durations = HumandrillAttackGoal.DURATION;
        this.input.cadence = 1.0F / HumandrillRenderer.SCALE;
        // épée de la bouche : rotation qui amène la lame (B0) et le tranchant (E0) du repère d'ItemInHandLayer sur la direction
        // voulue dans la gueule (HumandrillAnimator.MOUTH_*)
        float[] m = HumandrillAnimator.mul(HumandrillAnimator.basis(HumandrillAnimator.MOUTH_BLADE, HumandrillAnimator.MOUTH_EDGE),
                HumandrillAnimator.transpose(HumandrillAnimator.basis(HumandrillAnimator.B0, HumandrillAnimator.E0)));
        // Matrix3f de JOML : arguments colonne par colonne
        this.mouthRotation = new Quaternionf().setFromNormalized(new Matrix3f(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]));
    }

    @Override
    public void setupAnim(HumandrillRenderState state) {
        super.setupAnim(state);
        HumandrillAnimator.Input in = this.input;
        in.age = state.ageInTicks;
        in.walkPos = state.walkAnimationPos;
        in.walkSpeed = state.walkAnimationSpeed;
        in.headYaw = state.yRot;
        in.headPitch = state.xRot;
        in.guard = state.guard;
        in.attackT = state.attackT;
        in.attackKind = state.attackKind;
        in.left = state.attackLeft;
        in.swords = state.swords;
        in.landT = state.landT;
        in.landPower = state.landPower;
        in.air = state.air;
        in.turn = state.turn;
        in.tremble = state.attackKind == HumandrillAttackGoal.POWER && state.attackT >= 0.0F
                ? HumandrillAttackGoal.powerTremble(state.attackT) : 0.0F;
        HumandrillAnimator.Rig r = this.rig;
        HumandrillAnimator.solve(in, r);
        this.waist.y += r.waistDy;
        this.waist.z += r.waistDz;
        set(this.waist, r.waist);
        set(this.torso, r.torso);
        set(this.head, r.head);
        set(this.jaw, r.jaw);
        set(this.cloth, r.cloth);
        set(this.tail, r.tail);
        set(this.tailTip, r.tailTip);
        set(this.rArm, r.rArm);
        set(this.rFore, r.rFore);
        set(this.rHand, r.rHand);
        set(this.lArm, r.lArm);
        set(this.lFore, r.lFore);
        set(this.lHand, r.lHand);
        set(this.rThigh, r.rThigh);
        set(this.rShin, r.rShin);
        set(this.rFoot, r.rFoot);
        set(this.lThigh, r.lThigh);
        set(this.lShin, r.lShin);
        set(this.lFoot, r.lFoot);
    }

    private static void set(ModelPart part, float[] a) {
        part.xRot = a[0];
        part.yRot = a[1];
        part.zRot = a[2];
    }

    @Override
    public void translateToHand(HumandrillRenderState state, HumanoidArm arm, PoseStack pose) {
        boolean right = arm == HumanoidArm.RIGHT;
        this.root.translateAndRotate(pose);
        this.waist.translateAndRotate(pose);
        this.torso.translateAndRotate(pose);
        (right ? this.rArm : this.lArm).translateAndRotate(pose);
        (right ? this.rFore : this.lFore).translateAndRotate(pose);
        (right ? this.rHand : this.lHand).translateAndRotate(pose);
        float[] g = HumandrillAnimator.GRIP;
        float vx = right ? VANILLA_GRIP[0] : -VANILLA_GRIP[0];
        pose.translate((g[0] - vx) / 16.0F, (g[1] - VANILLA_GRIP[1]) / 16.0F, (g[2] - VANILLA_GRIP[2]) / 16.0F);
    }

    /**
     * Repère de l'épée de la bouche, à donner ensuite aux mêmes transformations qu'ItemInHandLayer (main droite) : la poignée
     * arrive entre les crocs (HumandrillAnimator.MOUTH), la lame et le tranchant dans les directions voulues ; suit la tête.
     */
    public void translateToMouthGrip(PoseStack pose) {
        this.root.translateAndRotate(pose);
        this.waist.translateAndRotate(pose);
        this.torso.translateAndRotate(pose);
        this.head.translateAndRotate(pose);
        float[] m = HumandrillAnimator.MOUTH;
        pose.translate(m[0] / 16.0F, m[1] / 16.0F, m[2] / 16.0F);
        pose.rotate(this.mouthRotation);
        pose.translate(-VANILLA_GRIP[0] / 16.0F, -VANILLA_GRIP[1] / 16.0F, -VANILLA_GRIP[2] / 16.0F);
    }

    @Override
    public ModelPart getHead() {
        return this.head;
    }
}
