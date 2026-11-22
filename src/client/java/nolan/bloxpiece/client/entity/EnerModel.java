package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;

/**
 * Ener : géométrie EnerModels (générée par make_ener.py), animations.
 * <ul>
 *   <li>Repos : respiration lente ; les lobes d'oreilles, les pans du bandeau et le pan de l'écharpe ondulent doucement.</li>
 *   <li>Marche et course (poursuite) : jambes et bras gauche vanilla (amplitude selon la vitesse) ; le bras droit tient le bâton,
 *       un peu en avant, et ne balance qu'à peine ; le bâton reste VERTICAL (la rotation du bras est annulée sur lui).</li>
 *   <li>Le pagne et le pan de l'écharpe suivent les jambes (devant : la jambe qui avance ; dos : celle qui recule), comme les pans
 *       du manteau de Morgan, pour que les jambes ne les traversent pas.</li>
 *   <li>Les lobes d'oreilles compensent l'inclinaison de la tête : ils pendent toujours devant le torse.</li>
 *   <li>Anneau et tambours : accrochés au torse, ils suivent le corps dans toutes les animations. Mort : chute vanilla.</li>
 * </ul>
 */
public class EnerModel extends HumanoidModel<HumanoidRenderState> {

    /** Pose de repos du bras droit (bâton en main) : même valeur que dans make_ener.py. */
    private static final float STAFF_ARM_X = -0.5F;
    /** Rotations de repos des lobes et des pans (celles d'EnerModels). */
    private static final float EAR_X = -0.30F;
    private static final float EAR_Z = 0.15F;
    private static final float SASH_Z = -0.06F;

    private final ModelPart staff;
    private final ModelPart earRight;
    private final ModelPart earLeft;
    private final ModelPart wrapFrontRight;
    private final ModelPart wrapFrontLeft;
    private final ModelPart wrapBack;
    private final ModelPart sashTail;
    private final ModelPart tailRight;
    private final ModelPart tailLeft;

    public EnerModel(ModelPart root) {
        super(root);
        this.staff = this.rightArm.getChild("staff");
        this.earRight = this.head.getChild("ear_right");
        this.earLeft = this.head.getChild("ear_left");
        this.wrapFrontRight = this.body.getChild("wrap_front_right");
        this.wrapFrontLeft = this.body.getChild("wrap_front_left");
        this.wrapBack = this.body.getChild("wrap_back");
        this.sashTail = this.body.getChild("sash_tail");
        ModelPart knot = this.head.getChild("bandana_knot");
        this.tailRight = knot.getChild("bandana_tail_right");
        this.tailLeft = knot.getChild("bandana_tail_left");
    }

    @Override
    public void setupAnim(HumanoidRenderState state) {
        super.setupAnim(state); // tête vers la cible, marche / course, coup vanilla
        float t = state.ageInTicks;
        float breath = Mth.sin(t * 0.08F);
        float breeze = Mth.sin(t * 0.05F) + 0.4F * Mth.sin(t * 0.13F + 1.1F);

        // bras droit : tient le bâton (un peu en avant), garde un quart du mouvement vanilla (marche, coup)
        this.rightArm.xRot = STAFF_ARM_X + this.rightArm.xRot * 0.25F;
        this.rightArm.zRot = this.rightArm.zRot * 0.5F;
        // bâton toujours vertical : il annule la rotation du bras
        this.staff.xRot = -this.rightArm.xRot;
        this.staff.yRot = -this.rightArm.yRot;
        this.staff.zRot = -this.rightArm.zRot;

        // respiration (position de repos de la tête : y = 0)
        this.head.y = 0.1F * breath;

        // lobes d'oreilles : ils pendent quoi que fasse la tête (inclinaison compensée), léger balancement
        this.earRight.xRot = EAR_X - this.head.xRot;
        this.earLeft.xRot = EAR_X - this.head.xRot;
        this.earRight.zRot = -EAR_Z + 0.03F * breeze;
        this.earLeft.zRot = EAR_Z + 0.03F * breeze;

        // pans du bandeau
        this.tailRight.zRot = 0.2F + 0.08F * breeze;
        this.tailLeft.zRot = -0.25F + 0.08F * breeze;

        // pagne et pan de l'écharpe : ils suivent la jambe qui avance (devant) ou qui recule (dos)
        this.wrapFrontRight.xRot = Math.min(0.0F, this.rightLeg.xRot);
        this.wrapFrontLeft.xRot = Math.min(0.0F, this.leftLeg.xRot);
        this.wrapBack.xRot = Math.max(0.0F, Math.max(this.rightLeg.xRot, this.leftLeg.xRot));
        this.sashTail.xRot = Math.min(0.0F, this.leftLeg.xRot) - 0.02F * breeze;
        this.sashTail.zRot = SASH_Z + 0.03F * breeze;
    }
}
