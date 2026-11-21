package nolan.bloxpiece.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.fx.ConquerorShockwave;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Posture de libération du Haki des Rois (attachment CONQUEROR_RELEASE, synchronisé avec tous les joueurs), ajoutée à la pose
 * normale d'un modèle humanoïde : joueur (PlayerModelMixin) ou Gol D. Roger (ArmedPirateModel).
 * <ol>
 *   <li>montée (0 à ConquerorShockwave.BUILD_TICKS) : il se ramasse, buste un peu penché, bras tirés en arrière ;</li>
 *   <li>décharge (en 3 ticks) : buste redressé, tête relevée (posture assurée), bras jetés vers l'extérieur et vers le bas ;</li>
 *   <li>tenue {@code holdTicks} ticks (joueur : 3 ; Roger : toute sa libération), puis retour en douceur en {@code relaxTicks}.</li>
 * </ol>
 */
public final class ConquerorPose {

    private ConquerorPose() {
    }

    public static void apply(HumanoidModel<?> model, Entity entity, float now, float holdTicks, float relaxTicks) {
        Integer at = entity.getAttached(ModAttachments.CONQUEROR_RELEASE);
        if (at == null) {
            return;
        }
        float t = now - at;
        float build = ConquerorShockwave.BUILD_TICKS;
        float holdEnd = build + 3.0F + holdTicks;
        if (t < 0.0F || t >= holdEnd + relaxTicks) {
            return;
        }
        // décalages ajoutés à la pose normale : {bras x, bras z (vers l'extérieur), buste x, tête x}
        float[] gather = {0.25F, -0.15F, 0.15F, 0.1F};
        float[] release = {-0.3F, 0.75F, -0.15F, -0.3F};
        float[] o = new float[4];
        for (int i = 0; i < 4; i++) {
            if (t < build) {
                o[i] = gather[i] * smooth(t / build);
            } else if (t < build + 3.0F) {
                o[i] = gather[i] + (release[i] - gather[i]) * smooth((t - build) / 3.0F);
            } else if (t < holdEnd) {
                o[i] = release[i];
            } else {
                o[i] = release[i] * (1.0F - smooth((t - holdEnd) / relaxTicks));
            }
        }
        model.rightArm.xRot += o[0];
        model.leftArm.xRot += o[0];
        model.rightArm.zRot += o[1];   // bras droit : positif = écarté du corps
        model.leftArm.zRot -= o[1];    // bras gauche : miroir
        model.body.xRot += o[2];
        model.head.xRot += o[3];
    }

    private static float smooth(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }
}
