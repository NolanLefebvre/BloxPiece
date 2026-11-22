package nolan.bloxpiece.client.cyborg;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.ability.Cyborg;
import nolan.bloxpiece.ability.CyborgCannonArmAbility;
import nolan.bloxpiece.ability.CyborgEnergyShieldAbility;
import nolan.bloxpiece.ability.CyborgRadicalBeamAbility;

/**
 * Race Cyborg : le bras gauche vanilla est CACHÉ (le bras mécanique de CyborgArmLayer le remplace et suit exactement sa pose),
 * et les poses des attaques (appelé par PlayerModelMixin, chez tous les joueurs) :
 * <ul>
 *   <li>Cannon Arm : le bras gauche se lève dans l'axe du regard, tient la visée, recule au tir, redescend ;</li>
 *   <li>Bouclier énergétique : les deux bras levés au ciel pendant le déploiement ;</li>
 *   <li>Surcharge moteur (démarrage) : bras écartés et pliés, le corps tremble de plus en plus ;</li>
 *   <li>Radical Beam : les deux bras tendus devant lui, mains rapprochées, tremblement pendant le tir.</li>
 * </ul>
 */
public final class CyborgPose {

    /** Modèles dont NOUS avons caché le bras gauche (pour le rendre visible au joueur suivant, non Cyborg). */
    private static final Map<PlayerModel, Boolean> HIDDEN = new WeakHashMap<>();

    private CyborgPose() {
    }

    public static void apply(PlayerModel model, Entity player, float ageInTicks) {
        boolean cyborg = CyborgClient.isCyborg(player);
        if (cyborg) {
            model.leftArm.visible = false; // (la manche vanilla est une partie du bras : cachée avec lui)
            HIDDEN.put(model, Boolean.TRUE);
        } else if (HIDDEN.remove(model) != null) {
            model.leftArm.visible = true;
        }
        if (!cyborg) {
            return;
        }
        int kind = CyborgClient.kind(player);
        float e = CyborgClient.elapsed(player, ageInTicks);
        if (e < 0.0F) {
            return;
        }
        float headX = model.head.xRot, headY = model.head.yRot;
        switch (kind) {
            case Cyborg.CANNON -> {
                float up = CyborgClient.ramp(e, 0.0F, CyborgCannonArmAbility.RAISE)
                        * (1.0F - CyborgClient.ramp(e, CyborgCannonArmAbility.DURATION - 8, CyborgCannonArmAbility.DURATION));
                float recoil = e >= CyborgCannonArmAbility.FIRE && e < CyborgCannonArmAbility.FIRE + 5
                        ? 0.35F * (1.0F - (e - CyborgCannonArmAbility.FIRE) / 5.0F) : 0.0F;
                model.leftArm.xRot = Mth.lerp(up, model.leftArm.xRot, -Mth.HALF_PI + headX - recoil);
                model.leftArm.yRot = Mth.lerp(up, model.leftArm.yRot, headY + 0.08F);
                model.leftArm.zRot = Mth.lerp(up, model.leftArm.zRot, 0.0F);
            }
            case Cyborg.SHIELD -> {
                float up = CyborgClient.ramp(e, 0.0F, 6.0F)
                        * (1.0F - CyborgClient.ramp(e, CyborgEnergyShieldAbility.DEPLOY - 4, CyborgEnergyShieldAbility.DEPLOY));
                float shake = Mth.sin(ageInTicks * 1.4F) * 0.04F;
                model.rightArm.xRot = Mth.lerp(up, model.rightArm.xRot, -3.0F + shake);
                model.rightArm.zRot = Mth.lerp(up, model.rightArm.zRot, 0.35F);
                model.leftArm.xRot = Mth.lerp(up, model.leftArm.xRot, -3.0F - shake);
                model.leftArm.zRot = Mth.lerp(up, model.leftArm.zRot, -0.35F);
            }
            case Cyborg.OVERDRIVE -> {
                float k = CyborgClient.ramp(e, 0.0F, 8.0F);
                float shake = Mth.sin(ageInTicks * 3.1F) * 0.06F * CyborgClient.ramp(e, 0.0F, Cyborg.OVERDRIVE_WINDUP);
                model.rightArm.xRot = Mth.lerp(k, model.rightArm.xRot, -1.1F + shake);
                model.rightArm.zRot = Mth.lerp(k, model.rightArm.zRot, 0.9F);
                model.leftArm.xRot = Mth.lerp(k, model.leftArm.xRot, -1.1F - shake);
                model.leftArm.zRot = Mth.lerp(k, model.leftArm.zRot, -0.9F);
                model.body.yRot += shake * 0.5F;
                model.head.xRot += 0.25F * k;
            }
            case Cyborg.BEAM -> {
                float k = CyborgClient.ramp(e, 0.0F, 6.0F)
                        * (1.0F - CyborgClient.ramp(e, CyborgRadicalBeamAbility.DURATION - 6, CyborgRadicalBeamAbility.DURATION));
                boolean firing = e >= CyborgRadicalBeamAbility.CHARGE && e < CyborgRadicalBeamAbility.CHARGE + CyborgRadicalBeamAbility.BEAM_LIFE;
                float shake = firing ? Mth.sin(ageInTicks * 4.0F) * 0.05F : 0.0F;
                model.rightArm.xRot = Mth.lerp(k, model.rightArm.xRot, -Mth.HALF_PI + headX + shake);
                model.rightArm.yRot = Mth.lerp(k, model.rightArm.yRot, -0.32F + headY);
                model.rightArm.zRot = Mth.lerp(k, model.rightArm.zRot, 0.0F);
                model.leftArm.xRot = Mth.lerp(k, model.leftArm.xRot, -Mth.HALF_PI + headX - shake);
                model.leftArm.yRot = Mth.lerp(k, model.leftArm.yRot, 0.32F + headY);
                model.leftArm.zRot = Mth.lerp(k, model.leftArm.zRot, 0.0F);
            }
            default -> {
            }
        }
    }
}
