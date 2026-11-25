package nolan.bloxpiece.client.minks;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.ability.Minks;
import nolan.bloxpiece.ability.MinksBarrageAbility;
import nolan.bloxpiece.ability.MinksClawsAbility;
import nolan.bloxpiece.ability.MinksDanceAbility;
import nolan.bloxpiece.ability.MinksSulongAbility;

/**
 * Race Minks : poses du corps pendant les attaques (appelé par PlayerModelMixin, chez tous les joueurs).
 * <ul>
 *   <li>Griffes électriques : les bras se croisent devant la poitrine, puis s'écartent d'un coup (les griffes jaillissent).</li>
 *   <li>Barrage : vrais coups de griffes alternés (bras armé en arrière, projeté devant, ramené ; l'autre bras en décalé),
 *       torse qui tourne à chaque coup ; dernier coup armé très loin puis lancé de tout le corps.</li>
 *   <li>Danse des griffes éclair : accroupi prêt à bondir, puis à chaque bond une frappe différente (revers horizontal,
 *       uppercut, double griffe en X vers le bas), jambes écartées en l'air ; coup final : bras levés puis abattus.</li>
 *   <li>Sulong (transformation) : garde basse, se ramasse en tremblant, bras jetés vers le ciel à l'explosion, posture de
 *       puissance.</li>
 * </ul>
 */
public final class MinksPose {

    private MinksPose() {
    }

    public static void apply(PlayerModel model, Entity player, float age) {
        if (!MinksClient.isMinks(player)) {
            return;
        }
        int kind = MinksClient.kind(player);
        float e = MinksClient.elapsed(player, age);
        if (kind == 0 || e < 0.0F) {
            return;
        }
        float hx = model.head.xRot, hy = model.head.yRot;
        if (kind == Minks.CLAWS) {
            float d = MinksClawsAbility.ACTIVATE, half = d / 2.0F;
            float w = MinksClient.ramp(e, 0.0F, 2.0F) * (1.0F - MinksClient.ramp(e, d - 3.0F, d));
            float cross = MinksClient.ramp(e, 0.0F, half - 1.0F), open = MinksClient.snap(e, half - 1.0F, half + 1.0F);
            arms(model, w, -1.35F + 0.35F * open, (0.65F - 0.95F * open) * cross, -0.15F + 0.75F * open,
                    -1.35F + 0.35F * open, (-0.65F + 0.95F * open) * cross, 0.15F - 0.75F * open);
            return;
        }
        if (MinksClient.barrage(kind)) {
            barrage(model, kind, e, hx, hy);
            return;
        }
        if (MinksClient.dance(kind)) {
            dance(model, player, kind, e, age, hx, hy);
            return;
        }
        if (kind == Minks.SULONG) {
            sulong(model, e, age);
        }
    }

    /**
     * Cinématique du Sulong : 1. il se met en garde basse, poings serrés le long du corps ; 2. il se ramasse de plus en plus,
     * bras rentrés, tête baissée, tout le corps tremble sous l'électricité ; 3. à l'explosion, les bras sont jetés vers le
     * ciel et l'extérieur, buste cambré, tête relevée ; 4. posture de puissance : bras ouverts vers le bas, poitrine en avant,
     * menton haut, qui respire, puis retour en douceur.
     */
    private static void sulong(PlayerModel model, float e, float age) {
        float c = MinksSulongAbility.CHARGE, end = c + MinksSulongAbility.REVEAL;
        float w = MinksClient.ramp(e, 0.0F, 4.0F) * (1.0F - MinksClient.ramp(e, end - 4.0F, end));
        float shake = Mth.sin(age * 4.6F) * 0.07F * MinksClient.ramp(e, 6.0F, c) * (e < c ? 1.0F : 0.0F);
        // poses clés : {bras droit x, y, z, bras gauche x, y, z, buste penché, tête}
        float[] guard = {0.25F, 0.0F, 0.35F, 0.25F, 0.0F, -0.35F, 0.12F, 0.15F};
        float[] gather = {0.45F, 0.3F, 0.15F, 0.45F, -0.3F, -0.15F, 0.45F, 0.45F};
        float[] burst = {-2.5F, -0.2F, 1.0F, -2.5F, 0.2F, -1.0F, -0.18F, -0.45F};
        float[] power = {0.15F, 0.0F, 0.6F, 0.15F, 0.0F, -0.6F, -0.08F, -0.2F};
        float[] p = guard.clone();
        p = mix(p, gather, MinksClient.ramp(e, 8.0F, c - 2.0F));
        p = mix(p, burst, MinksClient.snap(e, c - 1.0F, c + 1.0F));
        p = mix(p, power, MinksClient.ramp(e, c + 3.0F, c + 8.0F));
        float breathe = e > c + 8.0F ? Mth.sin(age * 0.5F) * 0.04F : 0.0F;
        arms(model, w, p[0] + shake, p[1], p[2] + breathe, p[3] - shake, p[4], p[5] - breathe);
        float hunch = p[6] * w;
        model.body.xRot += hunch;
        model.body.z -= 12.0F * Mth.sin(hunch);
        model.head.z -= 12.0F * Mth.sin(hunch);
        model.rightArm.z -= 10.0F * Mth.sin(hunch);
        model.leftArm.z -= 10.0F * Mth.sin(hunch);
        model.body.yRot += shake * 0.5F;
        model.head.xRot += p[7] * w;
        legs(model, -0.2F * w, 0.2F * w);
        model.rightLeg.zRot += 0.12F * w;
        model.leftLeg.zRot -= 0.12F * w;
    }

    private static float[] mix(float[] a, float[] b, float k) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = Mth.lerp(k, a[i], b[i]);
        }
        return out;
    }

    /**
     * Barrage : chaque bras fait de VRAIS coups de griffes : armé en arrière (coude reculé, épaule en arrière), projeté droit
     * devant dans l'axe du regard (bras tendu, épaule projetée), puis ramené ; les deux bras alternent en décalé (l'un frappe
     * pendant que l'autre recule) et le torse tourne avec chaque coup. Coup final : le bras droit s'arme très loin derrière,
     * le buste se tord, puis tout le corps accompagne la frappe.
     */
    private static void barrage(PlayerModel model, int kind, float e, float hx, float hy) {
        float g = MinksBarrageAbility.GUARD;
        float end = MinksBarrageAbility.duration(MinksClient.punches(kind));
        float w = MinksClient.ramp(e, 0.0F, 2.0F) * (1.0F - MinksClient.ramp(e, end - 5.0F, end));
        float set = MinksClient.ramp(e, 0.0F, g); // passage en position de combat
        float r = MinksClient.armExt(kind, e, true) * set, l = MinksClient.armExt(kind, e, false) * set;
        float strike = -Mth.HALF_PI + hx;
        // bras droit (extension négative : armé très loin pour le coup final)
        float rx = r >= 0.0F ? Mth.lerp(r, 0.35F, strike) : Mth.lerp(-r, 0.35F, 0.8F);
        float ry = r >= 0.0F ? Mth.lerp(r, 0.15F, hy - 0.18F) : 0.15F;
        float lx = Mth.lerp(Math.max(0.0F, l), 0.35F, strike);
        float ly = Mth.lerp(Math.max(0.0F, l), -0.15F, hy + 0.18F);
        arms(model, w, rx, ry, 0.12F * (1.0F - Math.abs(r)), lx, ly, -0.12F * (1.0F - Math.max(0.0F, l)));
        // l'épaule qui frappe part en avant, l'autre recule : le torse tourne avec chaque coup
        model.rightArm.z += (2.5F - 6.0F * r) * w * set;
        model.leftArm.z += (2.5F - 6.0F * Math.max(0.0F, l)) * w * set;
        float twist = (-0.42F * r + 0.42F * l) * w;
        model.body.yRot += twist;
        model.rightArm.z += 5.0F * Mth.sin(twist);
        model.leftArm.z -= 5.0F * Mth.sin(twist);
        model.rightArm.x += -5.0F * Mth.cos(twist) + 5.0F;
        model.leftArm.x += 5.0F * Mth.cos(twist) - 5.0F;
        // buste légèrement penché, appuis décalés ; le coup final engage tout le corps
        float lean = (0.12F + 0.15F * Math.max(0.0F, r) * MinksClient.ramp(e, MinksBarrageAbility.finalTick(MinksClient.punches(kind)) - 1.0F,
                MinksBarrageAbility.finalTick(MinksClient.punches(kind)))) * w;
        crouch(model, lean);
        legs(model, w * 0.3F, -0.3F * w);
    }

    /** Danse : prêt à bondir, frappes variées à chaque bond, coup final. */
    private static void dance(PlayerModel model, Entity player, int kind, float e, float age, float hx, float hy) {
        float prep = MinksDanceAbility.PREP;
        float step = MinksClient.step(kind);
        int steps = MinksClient.steps(kind);
        boolean sulong = kind == Minks.DANCE_S;
        float ret = MinksDanceAbility.returnTick(sulong), fin = ret + MinksDanceAbility.FINAL_DELAY;
        float end = MinksDanceAbility.duration(sulong);
        float w = MinksClient.ramp(e, 0.0F, 3.0F) * (1.0F - MinksClient.ramp(e, end - 6.0F, end));
        if (e < prep) { // accroupi, bras en arrière, prêt à bondir
            float k = MinksClient.ramp(e, 0.0F, prep);
            arms(model, w, 0.55F * k, 0.0F, 0.35F * k, 0.55F * k, 0.0F, -0.35F * k);
            crouch(model, 0.45F * k);
            return;
        }
        if (e < ret) {
            float[] mv = MinksClient.move(player, age);
            int type = mv == null ? MinksDanceAbility.FRONT : (int) mv[0];
            float s = mv == null ? 0.0F : mv[1]; // ticks dans le mouvement
            int at = MinksDanceAbility.strikeTicks(type)[0];
            float hit = MinksClient.snap(s, at - 1.0F, at + 0.4F);
            switch (type) {
                case MinksDanceAbility.SIDE, MinksDanceAbility.FAR -> // griffe diagonale du bras gauche, de haut en bas
                        arms(model, w, -0.9F, hy + 0.35F, -0.15F, Mth.lerp(hit, -2.6F, -0.4F), hy + Mth.lerp(hit, -0.6F, 0.5F), 0.0F);
                case MinksDanceAbility.BEHIND -> { // revers en pivotant : le buste tourne avec le bras droit
                    arms(model, w, -1.55F + hx * 0.5F, hy + Mth.lerp(hit, -0.8F, 1.0F), 0.0F, -0.6F, hy - 0.3F, 0.2F);
                    model.body.yRot += Mth.lerp(hit, -0.6F, 0.5F) * w;
                }
                case MinksDanceAbility.DASH -> { // fente : les deux griffes projetées devant
                    float lunge = MinksClient.snap(s, 0.0F, at + 0.3F);
                    arms(model, w, Mth.lerp(lunge, 0.4F, -Mth.HALF_PI + hx), hy - 0.25F * lunge, 0.0F, Mth.lerp(lunge, 0.4F, -Mth.HALF_PI + hx),
                            hy + 0.25F * lunge, 0.0F);
                    crouch(model, 0.5F * w);
                    legs(model, -0.7F * w, 0.6F * w);
                    return;
                }
                case MinksDanceAbility.ABOVE -> { // au-dessus de la cible : double griffe plongeante, jambes repliées
                    arms(model, w, Mth.lerp(hit, -2.9F, -0.3F), hy + Mth.lerp(hit, -0.2F, 0.2F), 0.1F, Mth.lerp(hit, -2.9F, -0.3F),
                            hy + Mth.lerp(hit, 0.2F, -0.2F), -0.1F);
                    legs(model, -0.9F * w, -0.6F * w);
                    model.head.xRot += 0.4F * w;
                    return;
                }
                case MinksDanceAbility.AIR_DIAG, MinksDanceAbility.RISE -> { // en l'air : griffes croisées en X
                    float hit2 = MinksClient.snap(s, at - 0.6F, at + 0.8F);
                    arms(model, w, Mth.lerp(hit, -2.6F, -0.6F), hy + Mth.lerp(hit, -0.5F, 0.5F), 0.0F, Mth.lerp(hit2, -2.6F, -0.6F),
                            hy + Mth.lerp(hit2, 0.5F, -0.5F), 0.0F);
                    legs(model, 0.6F * w, -0.6F * w);
                    return;
                }
                case MinksDanceAbility.MULTI -> { // rafale : deux griffes horizontales, droite puis gauche
                    float h2 = MinksClient.snap(s, 0.6F, 1.6F);
                    arms(model, w, -1.5F + hx * 0.5F, hy + Mth.lerp(hit, 0.95F, -0.8F), 0.0F, -1.5F + hx * 0.5F, hy + Mth.lerp(h2, -0.95F, 0.8F), 0.0F);
                    model.body.yRot += (Mth.lerp(hit, 0.3F, -0.3F) + Mth.lerp(h2, 0.0F, 0.6F)) * w;
                }
                default -> // devant : revers horizontal du bras droit
                        arms(model, w, -1.5F + hx * 0.5F, hy + Mth.lerp(hit, 0.95F, -0.9F), 0.0F, -0.9F, hy + 0.3F, -0.2F);
            }
            legs(model, w * 0.3F, -w * 0.3F);
            crouch(model, 0.22F * w);
            return;
        }
        // final : au-dessus du centre, posture offensive tournée vers l'avant (buste penché, griffes ramenées, l'électricité se
        // concentre) ; plongée le corps penché en avant, bras balayés vers l'arrière ; puis ÉCRASEMENT : réception accroupie, une
        // main de griffes plantée au sol, l'autre bras ouvert derrière (pas de coup de griffe devant lui)
        float hoverK = MinksClient.ramp(e, ret, ret + 3.0F);
        float diveK = MinksClient.ramp(e, ret + MinksDanceAbility.DIVE, ret + MinksDanceAbility.DIVE + 2.0F);
        float landK = MinksClient.snap(e, fin - 0.5F, fin + 0.5F);
        float shake = Mth.sin(age * 4.4F) * 0.06F * hoverK * (1.0F - diveK);
        // {bras droit x, y, z, bras gauche x, y, z, buste, jambe droite, jambe gauche}
        float[] hoverP = {-1.2F + shake, 0.4F, 0.2F, -1.2F - shake, -0.4F, -0.2F, 0.35F, -0.6F, -0.3F};
        float[] diveP = {0.75F, 0.1F, 0.35F, 0.75F, -0.1F, -0.35F, 0.7F, -0.5F, 0.4F};
        float[] landP = {-0.25F, 0.1F, 0.25F, 0.6F, -0.2F, -0.75F, 0.75F, -1.1F, 0.9F};
        float[] p = mix(mix(mix(new float[9], hoverP, hoverK), diveP, diveK), landP, landK);
        arms(model, w, p[0], hy + p[1], p[2], p[3], hy + p[4], p[5]);
        crouch(model, p[6] * w);
        model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, p[7]);
        model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, p[8]);
        model.head.xRot -= 0.35F * diveK * (1.0F - landK) * w; // tête relevée pendant la chute, regard vers le point d'impact
    }

    /** Pose les deux bras (mélangés avec la pose de Minecraft selon {@code w}). */
    private static void arms(PlayerModel model, float w, float rx, float ry, float rz, float lx, float ly, float lz) {
        model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, rx);
        model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, ry);
        model.rightArm.zRot = Mth.lerp(w, model.rightArm.zRot, rz);
        model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, lx);
        model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, ly);
        model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, lz);
    }

    private static void legs(PlayerModel model, float right, float left) {
        model.rightLeg.xRot += right;
        model.leftLeg.xRot += left;
    }

    /** Buste penché en avant sur les hanches, haut du corps abaissé (accroupi, prêt à bondir). */
    private static void crouch(PlayerModel model, float angle) {
        float fwd = 12.0F * Mth.sin(angle), down = 12.0F * (1.0F - Mth.cos(angle)) + 2.0F * angle;
        model.body.xRot += angle;
        model.body.z -= fwd;
        model.body.y += down;
        model.head.z -= fwd;
        model.head.y += down;
        model.rightArm.z -= fwd * 0.85F;
        model.leftArm.z -= fwd * 0.85F;
        model.rightArm.y += down;
        model.leftArm.y += down;
        model.rightLeg.xRot -= angle * 0.6F;
        model.leftLeg.xRot += angle * 0.3F;
    }
}
