package nolan.bloxpiece.client.fishman;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.ability.Fishman;
import nolan.bloxpiece.ability.FishmanDepthsAbility;
import nolan.bloxpiece.ability.FishmanSpearAbility;
import nolan.bloxpiece.ability.FishmanTideAbility;
import nolan.bloxpiece.ability.FishmanWaveAbility;

/**
 * Race Fishman : poses du corps pendant les attaques (appelé par PlayerModelMixin, chez tous les joueurs : synchronisé).
 * <ul>
 *   <li>Onde aquatique : GARDE DU KARATÉ DES HOMMES-POISSONS (jambes très écartées et fléchies, main avant ouverte dressée devant
 *       le visage, poing arrière serré à la hanche), le buste tourne pour armer, puis le poing part droit devant de tout le
 *       corps pendant que l'autre bras revient à la hanche ;</li>
 *   <li>Lance aquatique : mains jointes devant la poitrine qui tournent avec les gouttes, léger recul, puis les deux bras jetés
 *       devant d'un coup sec, buste penché ;</li>
 *   <li>Vague dévastatrice : il se baisse mains devant, se penche et lève les deux bras, puis frappe le sol des deux mains,
 *       accroupi ; il se relève ;</li>
 *   <li>Perforation des profondeurs : garde large, bras droit armé loin derrière, buste tordu, tout le corps tremble de plus en
 *       plus ; frappe surpuissante de tout le corps, bras tendu, puis récupération.</li>
 * </ul>
 */
public final class FishmanPose {

    private FishmanPose() {
    }

    public static void apply(PlayerModel model, Entity player, float age) {
        if (!FishmanClient.isFishman(player)) {
            return;
        }
        int kind = FishmanClient.kind(player);
        float e = FishmanClient.elapsed(player, age);
        if (kind == 0 || e < 0.0F) {
            return;
        }
        float hx = model.head.xRot, hy = model.head.yRot;
        switch (kind) {
            case Fishman.WAVE -> wave(model, e, hx, hy);
            case Fishman.SPEAR -> spear(model, e, age, hx, hy);
            case Fishman.TIDE -> tide(model, e);
            case Fishman.DEPTHS -> depths(model, e, age, hx, hy);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ outils

    /** Poses clés : {brasD x, y, z, brasG x, y, z, torsion du buste, penché, écart des jambes, abaissé (px)}. */
    private static float[] mix(float[] a, float[] b, float k) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = Mth.lerp(k, a[i], b[i]);
        }
        return out;
    }

    /** Applique une pose clé avec le poids {@code w} (0 : pose normale du jeu, 1 : pose de l'attaque). */
    private static void pose(PlayerModel model, float[] p, float w) {
        model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, p[0]);
        model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, p[1]);
        model.rightArm.zRot = Mth.lerp(w, model.rightArm.zRot, p[2]);
        model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, p[3]);
        model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, p[4]);
        model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, p[5]);
        // torsion du buste : les épaules suivent (comme l'attaque de Minecraft)
        float twist = p[6] * w;
        model.body.yRot += twist;
        model.rightArm.z += Mth.sin(twist) * 5.0F;
        model.rightArm.x += -Mth.cos(twist) * 5.0F + 5.0F;
        model.leftArm.z -= Mth.sin(twist) * 5.0F;
        model.leftArm.x += Mth.cos(twist) * 5.0F - 5.0F;
        // buste penché en avant sur les hanches
        float lean = p[7] * w, fwd = 12.0F * Mth.sin(lean), dn = 12.0F * (1.0F - Mth.cos(lean));
        model.body.xRot += lean;
        model.body.z -= fwd;
        model.body.y += dn;
        model.head.z -= fwd;
        model.head.y += dn;
        model.rightArm.z -= fwd * 0.85F;
        model.leftArm.z -= fwd * 0.85F;
        model.rightArm.y += dn;
        model.leftArm.y += dn;
        // jambes écartées (fléchies) : tout le haut du corps s'abaisse pour garder les pieds au sol
        float spread = p[8] * w, feet = 12.0F * (1.0F - Mth.cos(spread)), low = p[9] * w + feet;
        model.rightLeg.zRot += spread;
        model.leftLeg.zRot -= spread;
        model.rightLeg.xRot *= 1.0F - w;
        model.leftLeg.xRot *= 1.0F - w;
        model.rightLeg.y += feet; // jambes : seulement de quoi garder les pieds au sol ; le haut du corps s'abaisse (fléchi)
        model.leftLeg.y += feet;
        model.body.y += low;
        model.head.y += low;
        model.rightArm.y += low;
        model.leftArm.y += low;
    }

    // ------------------------------------------------------------------ attaques

    /** Onde aquatique : garde du karaté des hommes-poissons, armé, coup de poing droit devant de tout le corps. */
    private static void wave(PlayerModel model, float e, float hx, float hy) {
        float st = FishmanWaveAbility.STANCE, s = FishmanWaveAbility.STRIKE, end = FishmanWaveAbility.DURATION;
        float w = FishmanClient.ramp(e, 0.0F, 4.0F) * (1.0F - FishmanClient.ramp(e, end - 6.0F, end));
        float strike = -Mth.HALF_PI + hx;
        //                 brasD x  y      z      brasG x  y      z     torsion penché écart abaissé
        float[] guard = {0.45F, 0.1F, 0.25F, -1.95F, 0.5F, 0.15F, 0.2F, 0.08F, 0.42F, 1.5F};
        float[] cock = {0.85F, 0.25F, 0.3F, -1.8F, 0.6F, 0.1F, 0.45F, 0.12F, 0.45F, 2.0F};
        float[] punch = {strike, hy - 0.05F, 0.0F, 0.55F, 0.1F, -0.2F, -0.35F, 0.22F, 0.48F, 2.2F};
        float[] p = mix(guard, cock, FishmanClient.ramp(e, st, s - 2.0F));
        p = mix(p, punch, FishmanClient.snap(e, s - 1.5F, s + 0.5F));
        pose(model, p, w);
    }

    /** Lance aquatique : mains jointes devant (les gouttes tournent autour), recul, projection sèche. */
    private static void spear(PlayerModel model, float e, float age, float hx, float hy) {
        float g = FishmanSpearAbility.GATHER, th = FishmanSpearAbility.THROW, end = FishmanSpearAbility.DURATION;
        float w = FishmanClient.ramp(e, 0.0F, 4.0F) * (1.0F - FishmanClient.ramp(e, end - 6.0F, end));
        float swirl = Mth.sin(age * 0.45F) * 0.12F * (1.0F - FishmanClient.ramp(e, g, th));
        float[] hold = {-1.25F + swirl, -0.42F, 0.1F + swirl, -1.25F - swirl, 0.42F, -0.1F - swirl, 0.0F, 0.05F, 0.3F, 1.0F};
        float[] back = {-0.75F, -0.15F, 0.35F, -0.75F, 0.15F, -0.35F, 0.0F, -0.08F, 0.32F, 1.2F};
        float[] thrown = {-Mth.HALF_PI + hx - 0.08F, hy - 0.12F, 0.0F, -Mth.HALF_PI + hx - 0.08F, hy + 0.12F, 0.0F, 0.0F, 0.25F, 0.36F, 1.6F};
        float[] p = mix(hold, back, FishmanClient.ramp(e, g - 3.0F, th - 0.5F));
        p = mix(p, thrown, FishmanClient.snap(e, th - 0.5F, th + 1.0F));
        // salves suivantes : le même mouvement, les bras se réarment puis projettent à nouveau
        int[] throws_ = FishmanSpearAbility.THROWS;
        for (int i = 1; i < throws_.length; i++) {
            p = mix(p, back, FishmanClient.ramp(e, throws_[i - 1] + 2.0F, throws_[i] - 0.5F));
            p = mix(p, thrown, FishmanClient.snap(e, throws_[i] - 0.5F, throws_[i] + 1.0F));
        }
        pose(model, p, w);
    }

    /** Vague dévastatrice : baissé mains devant, bras levés, frappe du sol des deux mains, relevé. */
    private static void tide(PlayerModel model, float e) {
        float c = FishmanTideAbility.CROUCH, s = FishmanTideAbility.SLAM, end = FishmanTideAbility.DURATION;
        float w = FishmanClient.ramp(e, 0.0F, 3.0F) * (1.0F - FishmanClient.ramp(e, end - 10.0F, end));
        float[] low = {-1.2F, -0.25F, 0.15F, -1.2F, 0.25F, -0.15F, 0.0F, 0.3F, 0.3F, 2.5F};
        float[] raised = {-2.9F, -0.1F, 0.25F, -2.9F, 0.1F, -0.25F, 0.0F, 0.45F, 0.35F, 3.0F};
        float[] slam = {-0.55F, -0.2F, 0.1F, -0.55F, 0.2F, -0.1F, 0.0F, 0.95F, 0.42F, 5.0F};
        float[] p = mix(low, raised, FishmanClient.ramp(e, c, s - 2.0F));
        p = mix(p, slam, FishmanClient.snap(e, s - 1.5F, s + 0.3F));
        pose(model, p, w);
    }

    /** Perforation des profondeurs : armé long qui tremble, frappe surpuissante, récupération. */
    private static void depths(PlayerModel model, float e, float age, float hx, float hy) {
        float c = FishmanDepthsAbility.CHARGE, s = FishmanDepthsAbility.STRIKE, end = FishmanDepthsAbility.DURATION;
        float w = FishmanClient.ramp(e, 0.0F, 5.0F) * (1.0F - FishmanClient.ramp(e, end - 8.0F, end));
        float shake = Mth.sin(age * 4.3F) * 0.06F * FishmanClient.ramp(e, 8.0F, c) * (e < s ? 1.0F : 0.0F);
        float[] base = {0.6F, 0.2F, 0.45F, -1.45F, 0.35F, -0.05F, 0.35F, 0.1F, 0.45F, 1.8F};
        float[] wound = {1.25F, 0.35F, 0.55F, -1.6F, 0.55F, -0.1F, 0.7F, 0.18F, 0.5F, 2.6F};
        float[] hit = {-Mth.HALF_PI + hx, hy - 0.05F, 0.0F, 0.7F, 0.1F, -0.3F, -0.5F, 0.3F, 0.55F, 2.8F};
        float[] p = mix(base, wound, FishmanClient.ramp(e, 6.0F, s - 2.0F));
        p[0] += shake;
        p[6] += shake * 0.5F;
        p = mix(p, hit, FishmanClient.snap(e, s - 1.0F, s + 0.6F));
        pose(model, p, w);
    }
}
