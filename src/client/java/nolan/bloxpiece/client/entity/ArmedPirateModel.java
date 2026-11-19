package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Modèle humanoïde des combattants armés (pirates, Marines). Au repos ou en marche : animation vanilla (bras baissé, l'arme tenue
 * comme un objet normal). Pendant la visée (ArmedHumanoid.isAiming) :
 * <ul>
 *   <li>pistolet : bras droit levé à l'horizontale, orienté comme la tête vers la cible ;</li>
 *   <li>fusil (à deux mains) : crosse à l'épaule, bras droit sur la détente, bras gauche tendu sous le canon, tête penchée vers
 *       la mire ;</li>
 *   <li>coup de feu : recul vif de l'arme et des bras vers le haut et l'arrière, puis retour.</li>
 * </ul>
 */
public class ArmedPirateModel extends HumanoidModel<ArmedPirateRenderState> {

    /**
     * Arme rangée, affichée seulement hors combat : fusil dans le dos (Marine Fusilier, « back_rifle ») ou poignée du sabre au
     * fourreau (Gol D. Roger, « sheathed_hilt ») ; null pour les modèles qui n'en ont pas.
     */
    private final ModelPart backRifle;

    public ArmedPirateModel(ModelPart root) {
        super(root);
        this.backRifle = this.body.hasChild("back_rifle") ? this.body.getChild("back_rifle")
                : this.body.hasChild("sheathed_hilt") ? this.body.getChild("sheathed_hilt") : null;
    }

    @Override
    public void setupAnim(ArmedPirateRenderState state) {
        super.setupAnim(state);
        if (this.backRifle != null) {
            this.backRifle.visible = state.weaponStowed; // dans le dos hors combat, dans les mains en combat
        }
        if (state.aiming && !(state instanceof CuffedRenderState cuffed && cuffed.bloxpiece$isCuffed())) { // menotté : bras dans le dos
            float kick = state.recoil * state.recoil; // recul : vif au départ, puis s'amortit
            if (state.twoHanded) {
                // fusil épaulé : les deux bras vers la cible, le gauche plus loin sous le canon, la joue contre la crosse
                this.rightArm.xRot = -Mth.HALF_PI + this.head.xRot - 0.1F - kick * 0.45F;
                this.rightArm.yRot = -0.32F + this.head.yRot;
                this.rightArm.zRot = 0.0F;
                this.leftArm.xRot = -Mth.HALF_PI + this.head.xRot - 0.05F - kick * 0.35F;
                this.leftArm.yRot = 0.62F + this.head.yRot;
                this.leftArm.zRot = 0.0F;
                this.rightArm.z += kick * 1.5F; // l'arme recule contre l'épaule
                this.leftArm.z += kick * 1.0F;
                this.head.zRot += 0.12F;        // tête penchée vers la mire
            } else {
                this.rightArm.xRot = -Mth.HALF_PI + this.head.xRot - kick * 0.6F;
                this.rightArm.yRot = -0.1F + this.head.yRot;
                this.rightArm.zRot = 0.0F;
            }
        }
    }
}
