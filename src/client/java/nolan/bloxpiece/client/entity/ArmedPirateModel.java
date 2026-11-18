package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Modèle humanoïde des pirates armés. Au repos ou en marche : animation vanilla (bras baissé, l'arme tenue comme un objet
 * normal). Pendant la visée (ArmedPirate.isAiming) : bras droit levé à l'horizontale, orienté comme la tête vers la cible.
 */
public class ArmedPirateModel extends HumanoidModel<ArmedPirateRenderState> {

    public ArmedPirateModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(ArmedPirateRenderState state) {
        super.setupAnim(state);
        if (state.aiming && !(state instanceof CuffedRenderState cuffed && cuffed.bloxpiece$isCuffed())) { // menotté : bras dans le dos
            this.rightArm.xRot = -Mth.HALF_PI + this.head.xRot;
            this.rightArm.yRot = -0.1F + this.head.yRot;
            this.rightArm.zRot = 0.0F;
        }
    }
}
