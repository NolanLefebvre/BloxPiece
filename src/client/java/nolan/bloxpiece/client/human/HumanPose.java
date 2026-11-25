package nolan.bloxpiece.client.human;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.ability.Human;
import nolan.bloxpiece.ability.HumanConquerorFistAbility;
import nolan.bloxpiece.ability.HumanDestructiveRushAbility;
import nolan.bloxpiece.ability.HumanEarthBreakerAbility;
import nolan.bloxpiece.ability.HumanLightningImpactAbility;

/**
 * Race Humaine : poses du corps entier pendant les attaques (appelé par PlayerModelMixin, chez tous les joueurs). Chaque
 * attaque est une suite de POSES CLÉS (bras droit, bras gauche, torsion des épaules, buste penché sur les hanches, corps qui
 * s'abaisse, jambes), enchaînées par des courbes différentes selon la phase : montée douce pour la préparation, départ
 * explosif pour les frappes ({@link HumanClient#snap}), retour amorti pour la récupération.
 * <ul>
 *   <li>Impact Fulgurant : garde, bras droit armé en arrière (épaule reculée), puis frappe droit devant dans l'axe du regard,
 *       épaule projetée, bras gauche ramené ; retour rapide.</li>
 *   <li>Ruée Destructrice : accroupi, épaule droite en avant, bras rentrés ; pendant la course, buste très penché, jambes qui
 *       battent ; à l'arrêt, contrecoup puis redressement.</li>
 *   <li>Briseur Terrestre : poing levé haut au-dessus de la tête, buste cambré, bras gauche en balancier (tremblement de
 *       tension) ; puis le poing s'abat, buste plongé, corps abaissé en fente ; tenue au sol, puis redressement lent.</li>
 *   <li>Poing du Conquérant : grande garde de préparation (poing loin derrière, épaule reculée, bras gauche tendu en visée,
 *       appuis larges, tremblement croissant) ; frappe explosive, bras gauche ramené à la hanche ; tenue, puis retour.</li>
 * </ul>
 * {@link #sample} donne la pose pure d'un instant (traînées du bras : HumanFistLayer).
 */
public final class HumanPose {

    /** Une pose clé (radians et pixels du modèle). */
    public static final class Pose {
        /** Bras droit : rotations, et déplacement de l'épaule (px). */
        float rx, ry, rz, rdx, rdy, rdz;
        /** Bras gauche : rotations. */
        float lx, ly, lz;
        /** Torsion des épaules (> 0 : épaule droite en arrière), buste penché en avant (> 0), corps abaissé (px). */
        float twist, lean, drop;
        /** Jambes : rotation avant/arrière de chacune, écart. Tête : inclinaison ajoutée. */
        float legR, legL, spread, head;

        Pose copy() {
            Pose p = new Pose();
            p.rx = rx; p.ry = ry; p.rz = rz; p.rdx = rdx; p.rdy = rdy; p.rdz = rdz;
            p.lx = lx; p.ly = ly; p.lz = lz;
            p.twist = twist; p.lean = lean; p.drop = drop;
            p.legR = legR; p.legL = legL; p.spread = spread; p.head = head;
            return p;
        }

        static Pose lerp(Pose a, Pose b, float k) {
            Pose p = new Pose();
            p.rx = Mth.lerp(k, a.rx, b.rx); p.ry = Mth.lerp(k, a.ry, b.ry); p.rz = Mth.lerp(k, a.rz, b.rz);
            p.rdx = Mth.lerp(k, a.rdx, b.rdx); p.rdy = Mth.lerp(k, a.rdy, b.rdy); p.rdz = Mth.lerp(k, a.rdz, b.rdz);
            p.lx = Mth.lerp(k, a.lx, b.lx); p.ly = Mth.lerp(k, a.ly, b.ly); p.lz = Mth.lerp(k, a.lz, b.lz);
            p.twist = Mth.lerp(k, a.twist, b.twist); p.lean = Mth.lerp(k, a.lean, b.lean); p.drop = Mth.lerp(k, a.drop, b.drop);
            p.legR = Mth.lerp(k, a.legR, b.legR); p.legL = Mth.lerp(k, a.legL, b.legL);
            p.spread = Mth.lerp(k, a.spread, b.spread); p.head = Mth.lerp(k, a.head, b.head);
            return p;
        }

        /**
         * Bras droit complet pour cette pose (mêmes calculs que {@link #apply}, à partir de la pose de repos de Minecraft) :
         * {x, y, z (px), xRot, yRot, zRot}. Sert aux images rémanentes du bras (HumanFistLayer).
         */
        public float[] rightArm() {
            float forward = 12.0F * Mth.sin(lean) * 0.85F;
            float down = 12.0F * (1.0F - Mth.cos(lean)) + drop;
            return new float[] {-5.0F * Mth.cos(twist) + rdx, 2.0F + rdy + down, 5.0F * Mth.sin(twist) + rdz - forward,
                    rx + lean * 0.5F, ry + twist, rz};
        }

        /** Position du poing droit (px, repère du modèle) pour cette pose : mêmes calculs que {@link #apply} (épaule, bras, main). */
        public Vector3f fist() {
            float forward = 12.0F * Mth.sin(lean) * 0.85F;
            float down = 12.0F * (1.0F - Mth.cos(lean)) + drop;
            Vector3f shoulder = new Vector3f(-5.0F * Mth.cos(twist) + rdx, 2.0F + rdy + down, 5.0F * Mth.sin(twist) + rdz - forward);
            Vector3f hand = new Quaternionf().rotationZYX(rz, ry + twist, rx + lean * 0.5F).transform(new Vector3f(-1.0F, 10.0F, 0.0F));
            return shoulder.add(hand);
        }
    }

    private static final Pose NEUTRAL = new Pose();

    private HumanPose() {
    }

    // ------------------------------------------------------------------ poses clés

    /** Impact Fulgurant, garde de combat : poings levés devant le visage, pieds décalés, centre de gravité un peu abaissé. */
    private static Pose impactGuard() {
        Pose p = new Pose();
        p.rx = -1.05F; p.ry = 0.35F; p.rz = -0.1F;
        p.lx = -1.25F; p.ly = 0.45F; p.lz = 0.1F;
        p.twist = 0.15F; p.lean = 0.12F; p.drop = 1.2F;
        p.legR = 0.35F; p.legL = -0.3F; p.spread = 0.06F;
        return p;
    }

    /** Impact Fulgurant, armé : le coude recule, le poing passe à hauteur de la poitrine, l'épaule droite recule avec le torse. */
    private static Pose impactArmed() {
        Pose p = new Pose();
        p.rx = 0.25F; p.ry = 0.15F; p.rz = 0.3F; p.rdz = 2.5F;
        p.lx = -1.35F; p.ly = 0.35F; p.lz = 0.1F;
        p.twist = 0.5F; p.lean = 0.1F; p.drop = 1.4F;
        p.legR = 0.4F; p.legL = -0.35F; p.spread = 0.06F;
        return p;
    }

    /** Impact Fulgurant, frappe : bras tendu dans l'axe du regard, épaule projetée, bras gauche ramené à la poitrine. */
    private static Pose impactStruck(float headX, float headY) {
        Pose p = new Pose();
        p.rx = -Mth.HALF_PI + headX; p.ry = headY - 0.06F; p.rdz = -3.5F;
        p.lx = -0.35F; p.ly = 0.5F; p.lz = 0.15F;
        p.twist = -0.45F; p.lean = 0.24F; p.drop = 1.0F;
        p.legR = 0.45F; p.legL = -0.35F; p.spread = 0.06F;
        return p;
    }

    /** Ruée, prise d'appui : centre de gravité très bas, appuis en fente, buste penché, épaule droite devant, bras rentrés. */
    private static Pose rushCrouch() {
        Pose p = new Pose();
        p.rx = -0.55F; p.ry = 0.65F; p.rz = -0.35F;
        p.lx = 0.45F; p.lz = 0.2F;
        p.twist = -0.65F; p.lean = 0.55F; p.drop = 3.5F;
        p.legR = -0.85F; p.legL = 0.75F; p.head = -0.3F;
        return p;
    }

    /**
     * Ruée, course : buste couché vers l'avant, épaule droite en bélier (bras droit rentré contre la poitrine), le bras gauche et
     * les jambes battent à grande vitesse, le corps rebondit à chaque foulée, la tête reste levée vers la cible.
     */
    private static Pose rushDash(float age) {
        Pose p = new Pose();
        float phase = age * 2.3F;
        float run = Mth.sin(phase);
        p.rx = -0.5F + run * 0.06F; p.ry = 0.7F; p.rz = -0.4F;
        p.lx = 0.2F - run * 0.95F; p.lz = 0.15F;
        p.twist = -0.75F + run * 0.05F; p.lean = 0.72F; p.drop = 2.2F + Math.abs(run) * 0.9F;
        p.legR = run * 1.15F; p.legL = -run * 1.15F; p.head = -0.45F;
        return p;
    }

    /** Ruée, contrecoup : le choc redresse le buste d'un coup, appuis freinés. */
    private static Pose rushRecoil() {
        Pose p = new Pose();
        p.rx = -0.3F; p.ry = 0.4F; p.rz = -0.2F;
        p.lx = -0.2F; p.lz = 0.35F;
        p.twist = -0.3F; p.lean = -0.12F; p.drop = 1.6F;
        p.legR = -0.5F; p.legL = 0.45F;
        return p;
    }

    private static Pose breakerRaised() {
        Pose p = new Pose();
        p.rx = -2.95F; p.rz = 0.3F; p.rdy = -1.0F;
        p.lx = -0.8F; p.lz = -0.75F;
        p.twist = 0.3F; p.lean = -0.14F;
        p.legR = -0.15F; p.legL = 0.2F; p.spread = 0.12F; p.head = -0.25F;
        return p;
    }

    private static Pose breakerSlammed() {
        Pose p = new Pose();
        p.rx = -0.5F; p.rz = 0.05F; p.rdz = -1.5F;
        p.lx = 0.35F; p.lz = -0.55F;
        p.twist = -0.22F; p.lean = 0.8F; p.drop = 3.5F;
        p.legR = -0.75F; p.legL = 0.65F; p.spread = 0.1F; p.head = 0.4F;
        return p;
    }

    private static Pose conquerorDrawn(float headX, float headY) {
        Pose p = new Pose();
        p.rx = 0.8F; p.rz = 0.3F; p.rdz = 3.5F;
        p.lx = -Mth.HALF_PI + headX; p.ly = headY + 0.2F;
        p.twist = 0.65F; p.lean = 0.15F; p.drop = 1.5F;
        p.legR = 0.4F; p.legL = -0.35F; p.spread = 0.2F;
        return p;
    }

    private static Pose conquerorStruck(float headX, float headY) {
        Pose p = new Pose();
        p.rx = -Mth.HALF_PI + headX; p.ry = headY - 0.05F; p.rdz = -4.0F;
        p.lx = 0.7F; p.lz = 0.1F;
        p.twist = -0.6F; p.lean = 0.32F; p.drop = 2.0F;
        p.legR = 0.45F; p.legL = -0.4F; p.spread = 0.2F;
        return p;
    }

    // ------------------------------------------------------------------ déroulement

    /**
     * La pose de l'attaque {@code kind} à l'instant {@code e} (ticks), et son poids (0 = pose normale de Minecraft, 1 = pose de
     * l'attaque) en {@code weight[0]}. Null si rien à poser.
     */
    public static Pose sample(int kind, float e, float headX, float headY, float age, float[] weight) {
        switch (kind) {
            case Human.IMPACT -> {
                float w = HumanLightningImpactAbility.WINDUP, s = HumanLightningImpactAbility.STRIKE;
                float d = HumanLightningImpactAbility.DURATION;
                // garde (0-3), bras armé et torse qui recule (3-WINDUP), frappe explosive, contrecoup, retour en garde, puis repos
                Pose p = Pose.lerp(NEUTRAL, impactGuard(), HumanClient.ramp(e, 0.0F, 3.0F));
                p = Pose.lerp(p, impactArmed(), HumanClient.ramp(e, 3.0F, w));
                p = Pose.lerp(p, impactStruck(headX, headY), HumanClient.snap(e, w, s));
                if (e >= s && e < s + 3.0F) { // le choc remonte dans le bras : petit recul du poing et de l'épaule
                    float k = 1.0F - (e - s) / 3.0F;
                    p.rx += 0.14F * k;
                    p.rdz += 1.2F * k;
                    p.twist += 0.08F * k;
                }
                p = Pose.lerp(p, impactGuard(), HumanClient.ramp(e, s + 3.0F, s + 8.0F));
                weight[0] = HumanClient.ramp(e, 0.0F, 2.0F) * (1.0F - HumanClient.ramp(e, s + 8.0F, d));
                return p;
            }
            case Human.RUSH -> {
                float c = HumanDestructiveRushAbility.CROUCH;
                Pose p = Pose.lerp(NEUTRAL, rushCrouch(), HumanClient.ramp(e, 0.0F, c));
                p = Pose.lerp(p, rushDash(age), HumanClient.snap(e, c, c + 2.0F));
                weight[0] = HumanClient.ramp(e, 0.0F, 3.0F);
                return p;
            }
            case Human.RUSH_END -> {
                float r = HumanDestructiveRushAbility.RECOVER;
                Pose p = Pose.lerp(rushDash(age), rushRecoil(), HumanClient.snap(e, 0.0F, 3.0F));
                weight[0] = 1.0F - HumanClient.ramp(e, 3.0F, r);
                return p;
            }
            case Human.BREAKER -> {
                float r = HumanEarthBreakerAbility.RAISE, h = HumanEarthBreakerAbility.HOLD, i = HumanEarthBreakerAbility.IMPACT;
                float d = HumanEarthBreakerAbility.DURATION;
                Pose p = Pose.lerp(NEUTRAL, breakerRaised(), HumanClient.ramp(e, 0.0F, r));
                float tension = HumanClient.ramp(e, r - 2.0F, h) * (1.0F - HumanClient.ramp(e, h, h + 1.0F));
                p.rx += Mth.sin(age * 2.3F) * 0.05F * tension;
                p.drop += 0.6F * tension;
                // la descente : lente au départ (le poids), puis s'abat d'un coup
                float slam = Mth.clamp((e - h) / (i - h), 0.0F, 1.0F);
                p = Pose.lerp(p, breakerSlammed(), slam * slam);
                if (e >= i && e < i + 5.0F) { // le sol renvoie le choc dans le bras
                    float k = 1.0F - (e - i) / 5.0F;
                    p.rx -= 0.18F * k;
                    p.drop += 0.8F * k;
                }
                weight[0] = HumanClient.ramp(e, 0.0F, 3.0F) * (1.0F - HumanClient.ramp(e, i + 8.0F, d));
                return p;
            }
            case Human.CONQUEROR -> {
                float c = HumanConquerorFistAbility.CHARGE, rel = HumanConquerorFistAbility.RELEASE;
                float d = HumanConquerorFistAbility.DURATION;
                Pose p = Pose.lerp(NEUTRAL, conquerorDrawn(headX, headY), HumanClient.ramp(e, 0.0F, 14.0F));
                float build = HumanClient.ramp(e, 8.0F, c);
                p.rx += Mth.sin(age * 3.7F) * 0.06F * build;           // la puissance accumulée fait trembler le bras
                p.twist += Mth.sin(age * 2.9F + 1.0F) * 0.025F * build;
                p.drop += 1.0F * build;
                p = Pose.lerp(p, conquerorStruck(headX, headY), HumanClient.snap(e, c, rel));
                if (e >= rel && e < rel + 10.0F) { // l'onde part : le corps encaisse
                    float k = 1.0F - (e - rel) / 10.0F;
                    p.rx += 0.15F * k * Mth.sin(age * 4.0F);
                    p.lean -= 0.12F * k;
                }
                weight[0] = HumanClient.ramp(e, 0.0F, 3.0F) * (1.0F - HumanClient.ramp(e, rel + 12.0F, d));
                return p;
            }
            default -> {
                return null;
            }
        }
    }

    // ------------------------------------------------------------------ application au modèle

    public static void apply(PlayerModel model, Entity player, float ageInTicks) {
        if (!HumanClient.isHuman(player)) {
            return;
        }
        int kind = HumanClient.kind(player);
        float e = HumanClient.elapsed(player, ageInTicks);
        if (kind == 0 || e < 0.0F) {
            return;
        }
        float[] weight = new float[1];
        Pose p = sample(kind, e, model.head.xRot, model.head.yRot, ageInTicks, weight);
        float w = weight[0];
        if (p == null || w <= 0.0F) {
            return;
        }
        // bras (mélangés avec la pose de Minecraft selon le poids)
        model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, p.rx);
        model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, p.ry);
        model.rightArm.zRot = Mth.lerp(w, model.rightArm.zRot, p.rz);
        model.rightArm.x += p.rdx * w;
        model.rightArm.y += p.rdy * w;
        model.rightArm.z += p.rdz * w;
        model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, p.lx);
        model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, p.ly);
        model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, p.lz);
        // jambes
        model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, p.legR);
        model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, p.legL);
        model.rightLeg.zRot += p.spread * w;
        model.leftLeg.zRot -= p.spread * w;
        // torsion des épaules (les bras suivent leur épaule) puis buste penché sur les hanches, corps abaissé
        twist(model, p.twist * w);
        lean(model, p.lean * w, p.drop * w);
        model.head.xRot += p.head * w;
    }

    /** Torsion des épaules (radians, autour de la verticale ; > 0 : épaule droite en arrière). */
    private static void twist(PlayerModel model, float yaw) {
        float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
        model.body.yRot += yaw;
        model.rightArm.x += -5.0F * cos + 5.0F;
        model.rightArm.z += 5.0F * sin;
        model.leftArm.x += 5.0F * cos - 5.0F;
        model.leftArm.z += -5.0F * sin;
        model.rightArm.yRot += yaw;
        model.leftArm.yRot += yaw;
    }

    /** Buste penché en avant (angle > 0) sur les hanches, haut du corps abaissé de {@code drop} px (tête et bras suivent). */
    private static void lean(PlayerModel model, float angle, float drop) {
        float forward = 12.0F * Mth.sin(angle);
        float down = 12.0F * (1.0F - Mth.cos(angle)) + drop;
        model.body.xRot += angle;
        model.body.z -= forward;
        model.body.y += down;
        model.head.z -= forward;
        model.head.y += down;
        model.rightArm.z -= forward * 0.85F;
        model.leftArm.z -= forward * 0.85F;
        model.rightArm.y += down;
        model.leftArm.y += down;
        model.rightArm.xRot += angle * 0.5F;
        model.leftArm.xRot += angle * 0.5F;
    }
}
