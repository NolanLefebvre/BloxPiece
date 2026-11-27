package nolan.bloxpiece.client.lunarian;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.ability.Lunarian;
import nolan.bloxpiece.ability.LunarianFeathersAbility;
import nolan.bloxpiece.ability.LunarianFistAbility;
import nolan.bloxpiece.ability.LunarianFlamesAbility;
import nolan.bloxpiece.ability.LunarianJudgmentAbility;

/**
 * Race Lunarian : poses du corps pendant les capacités (PlayerModelMixin, chez tous les joueurs) et inclinaison du CORPS
 * ENTIER ({@link #rotate}, LunarianRotationMixin) pour foncer poing en avant et plonger tête vers le sol.
 * <ul>
 *   <li>Flammes : bras écartés vers l'arrière, poitrine bombée et tête relevée pendant que les flammes naissent, puis retour ;</li>
 *   <li>Plumes incandescentes : bras ouverts en arrière avec les ailes, puis un bras projeté en avant à chaque plume (du côté
 *       de l'aile qui tire) ;</li>
 *   <li>Poing embrasé : bras droit armé, garde de l'autre bras, puis corps lancé à l'horizontale (ou dans l'axe du regard), poing
 *       tendu devant, jambes en arrière ; récupération ;</li>
 *   <li>Tempête : accroupi, poing armé bas ; envol bras le long du corps ; bascule tête en bas, poing en feu devant la tête,
 *       corps tendu en plongée ; réception accroupie, poing au sol, puis relevé.</li>
 * </ul>
 */
public final class LunarianPose {

    private LunarianPose() {
    }

    public static void apply(PlayerModel model, Entity player, float age) {
        if (!LunarianClient.isLunarian(player)) {
            return;
        }
        int kind = LunarianClient.kind(player);
        float e = LunarianClient.elapsed(player, age);
        if (kind == 0 || e < 0.0F) {
            return;
        }
        float hx = model.head.xRot, hy = model.head.yRot;
        switch (kind) {
            case Lunarian.FLAMES -> {
                float w = LunarianClient.ramp(e, 0.0F, 5.0F) * (1.0F - LunarianClient.ramp(e, 13.0F, LunarianFlamesAbility.DURATION));
                arms(model, w, 0.45F, 0.2F, 0.75F, 0.45F, -0.2F, -0.75F);
                model.body.xRot -= 0.12F * w;
                model.head.xRot -= 0.3F * w;
            }
            case Lunarian.FEATHERS -> feathers(model, e, hx, hy);
            case Lunarian.FIST -> {
                float wu = LunarianFistAbility.WINDUP;
                float w = LunarianClient.ramp(e, 0.0F, 3.0F);
                float go = LunarianClient.snap(e, wu - 1.5F, wu);
                float[] cock = {0.9F, 0.3F, 0.35F, -1.2F, 0.4F, -0.1F};
                float[] dash = {-Mth.PI + 0.05F, 0.0F, 0.0F, 0.35F, 0.0F, -0.15F}; // (corps couché : bras « en haut » = devant)
                arms(model, w, Mth.lerp(go, cock[0], dash[0]), Mth.lerp(go, cock[1], dash[1]), Mth.lerp(go, cock[2], dash[2]),
                        Mth.lerp(go, cock[3], dash[3]), Mth.lerp(go, cock[4], dash[4]), Mth.lerp(go, cock[5], dash[5]));
                model.body.yRot += 0.35F * w * (1.0F - go);
                legs(model, go * w, 0.25F, 0.45F);
            }
            case Lunarian.FIST_END -> {
                float w = 1.0F - LunarianClient.ramp(e, 2.0F, LunarianFistAbility.RECOVER);
                float back = LunarianClient.ramp(e, 0.0F, 4.0F);
                arms(model, w, Mth.lerp(back, -Mth.PI + 0.05F, -1.4F + hx), hy, 0.0F, 0.3F, 0.0F, -0.15F);
            }
            case Lunarian.JUDGMENT -> judgment(model, player, e, age);
            case Lunarian.JUDGMENT_END -> {
                float w = 1.0F - LunarianClient.ramp(e, 6.0F, LunarianJudgmentAbility.RECOVER);
                arms(model, w, -0.55F, -0.1F, 0.1F, 0.4F, 0.1F, -0.45F);
                crouch(model, 0.6F * w);
            }
            default -> {
            }
        }
    }

    private static void arms(PlayerModel model, float w, float rx, float ry, float rz, float lx, float ly, float lz) {
        model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, rx);
        model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, ry);
        model.rightArm.zRot = Mth.lerp(w, model.rightArm.zRot, rz);
        model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, lx);
        model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, ly);
        model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, lz);
    }

    /** Jambes tendues en arrière (corps couché en dash / plongée), légèrement décalées. */
    private static void legs(PlayerModel model, float w, float right, float left) {
        model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, right);
        model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, left);
    }

    /** Buste penché, haut du corps abaissé (accroupi). */
    private static void crouch(PlayerModel model, float angle) {
        float fwd = 12.0F * Mth.sin(angle), down = 12.0F * (1.0F - Mth.cos(angle)) + 3.0F * angle;
        model.body.xRot += angle;
        model.body.z -= fwd;
        model.body.y += down;
        model.head.z -= fwd;
        model.head.y += down;
        model.rightArm.z -= fwd * 0.85F;
        model.leftArm.z -= fwd * 0.85F;
        model.rightArm.y += down;
        model.leftArm.y += down;
    }

    /** Plumes : bras ouverts en arrière (préparation), puis à chaque plume le bras du côté de l'aile qui tire jaillit devant. */
    private static void feathers(PlayerModel model, float e, float hx, float hy) {
        float d = LunarianFeathersAbility.DEPLOY, first = LunarianFeathersAbility.FIRST;
        float last = first + LunarianFeathersAbility.EVERY * LunarianFeathersAbility.COUNT;
        float w = LunarianClient.ramp(e, 0.0F, 4.0F) * (1.0F - LunarianClient.ramp(e, last + 4.0F, LunarianFeathersAbility.DURATION));
        float open = LunarianClient.ramp(e, 2.0F, d);
        float rx = 0.5F * open, lx = 0.5F * open, rz = 0.9F * open, lz = -0.9F * open;
        if (e >= first - 1.0F) {
            int i = Math.max(0, (int) ((e - first + 1.0F) / LunarianFeathersAbility.EVERY));
            float local = e - (first + i * LunarianFeathersAbility.EVERY);
            float thrust = LunarianClient.snap(local, -1.0F, 0.3F) * (1.0F - LunarianClient.ramp(local, 0.6F, 1.8F));
            boolean right = i % 2 == 0;
            float strike = -Mth.HALF_PI + hx;
            if (i < LunarianFeathersAbility.COUNT) {
                if (right) {
                    rx = Mth.lerp(thrust, 0.2F, strike);
                    rz = Mth.lerp(thrust, 0.6F, 0.0F);
                } else {
                    lx = Mth.lerp(thrust, 0.2F, strike);
                    lz = Mth.lerp(thrust, -0.6F, 0.0F);
                }
            }
        }
        arms(model, w, rx, hy * 0.5F, rz, lx, hy * 0.5F, lz);
        model.body.xRot -= 0.08F * w * open;
    }

    /** Tempête : accroupi et armé, envol, bascule, plongée, (réception : JUDGMENT_END). */
    private static void judgment(PlayerModel model, Entity player, float e, float age) {
        int ph = LunarianClient.judgmentPhase(player, e);
        float p = LunarianJudgmentAbility.PREP;
        if (ph == 0) {
            float w = LunarianClient.ramp(e, 0.0F, 5.0F);
            float shake = Mth.sin(age * 4.1F) * 0.05F * LunarianClient.ramp(e, 6.0F, p);
            float jump = LunarianClient.snap(e, p - 2.0F, p);
            arms(model, w, Mth.lerp(jump, 0.75F + shake, 0.15F), 0.25F, Mth.lerp(jump, 0.35F, 0.2F), Mth.lerp(jump, -0.9F, 0.15F), 0.3F,
                    Mth.lerp(jump, -0.1F, -0.2F));
            crouch(model, (0.45F - 0.45F * jump) * w);
        } else if (ph == 1) { // envol : bras le long du corps, jambes jointes
            arms(model, 1.0F, 0.15F, 0.0F, 0.2F, 0.15F, 0.0F, -0.2F);
            legs(model, 1.0F, 0.1F, 0.15F);
        } else { // bascule et plongée : poing en feu devant la tête (le corps est retourné : LunarianRotation)
            arms(model, 1.0F, -Mth.PI + 0.1F, 0.0F, 0.0F, 0.25F, 0.0F, -0.2F);
            legs(model, 1.0F, 0.12F, 0.2F);
            model.head.xRot = -0.6F; // regard vers le sol (devant, dans le sens de la plongée)
        }
    }

    // ------------------------------------------------------------------ corps entier

    /**
     * Inclinaison du corps entier (après les rotations de Minecraft, avant la mise à l'échelle du modèle) : il pivote autour de
     * son centre. Dash : couché vers l'avant (plus ou moins selon le regard) ; Tempête : léger penché à l'envol, puis tête en
     * bas pour la plongée. Lissé (LunarianClient.anim).
     */
    public static void rotate(AvatarRenderState state, PoseStack pose) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(state.id);
        if (entity == null || !LunarianClient.isLunarian(entity)) {
            return;
        }
        LunarianClient.Anim a = LunarianClient.anim(entity, state.ageInTicks, Math.min(1.0F, state.walkAnimationSpeed));
        int kind = LunarianClient.kind(entity);
        float full = kind == Lunarian.JUDGMENT || kind == Lunarian.JUDGMENT_END ? 168.0F : Mth.clamp(72.0F + state.xRot, 10.0F, 165.0F);
        float angle = Mth.clamp(a.pitch, 0.0F, 1.0F) * full;
        if (angle < 0.5F) {
            return;
        }
        pose.translate(0.0F, 0.9F, 0.0F);
        pose.rotateDegrees(Axis.XP, -angle);
        pose.translate(0.0F, -0.9F, 0.0F);
    }
}
