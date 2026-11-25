package nolan.bloxpiece.client.human;

import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.ability.Human;
import nolan.bloxpiece.ability.HumanConquerorFistAbility;
import nolan.bloxpiece.ability.HumanDestructiveRushAbility;
import nolan.bloxpiece.ability.HumanEarthBreakerAbility;
import nolan.bloxpiece.ability.HumanLightningImpactAbility;
import nolan.bloxpiece.client.goro.GoroLightning;

import static nolan.bloxpiece.client.human.HumanDraw.AIR;
import static nolan.bloxpiece.client.human.HumanDraw.AIR_EDGE;
import static nolan.bloxpiece.client.human.HumanDraw.AMBER;
import static nolan.bloxpiece.client.human.HumanDraw.DUST;
import static nolan.bloxpiece.client.human.HumanDraw.EMBER;
import static nolan.bloxpiece.client.human.HumanDraw.FLASH;
import static nolan.bloxpiece.client.human.HumanDraw.GOLD;
import static nolan.bloxpiece.client.human.HumanDraw.v;

/**
 * Race Humaine : effets qui SUIVENT le joueur pendant ses attaques (chez tous les joueurs ; à la première personne, l'aura de
 * la main : {@link #submitHand}). Tout est dessiné dans le repère du modèle (ou du bras), donc colle exactement aux poses :
 * <ul>
 *   <li>Aura du poing : concentration de l'Impact Fulgurant (coeur blanc, anneaux qui tournent, traits aspirés), veines dorées
 *       et chaleur du Briseur Terrestre, et la grande aura du Poing du Conquérant (trois couches qui grossissent, anneaux
 *       inclinés, flammes qui montent, aura du corps entier).</li>
 *   <li>Traînée du bras : un ruban 3D qui suit EXACTEMENT le trajet du poing pendant les frappes (positions passées du poing,
 *       calculées par HumanPose), de plus en plus large d'une attaque à l'autre.</li>
 *   <li>Ruée Destructrice : onde d'étrave devant l'épaule (air comprimé), stries de vitesse et anneaux d'air qui filent
 *       derrière le corps, poussière aux pieds ; à la prise d'appui, l'air se resserre autour du joueur.</li>
 * </ul>
 */
public class HumanFistLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    /** Main droite dans le repère du bras (px) : bout du bras vanilla. */
    private static final float HAND_X = -1.0F, HAND_Y = 10.5F;
    private static HumanDraw shared;
    private final HumanDraw draw;

    public HumanFistLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent,
                          net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
        super(parent);
        this.draw = new HumanDraw(context);
        shared = this.draw;
    }

    private static int a(float alpha, int rgb) {
        return GoroLightning.argb(Mth.clamp(alpha, 0.0F, 1.0F), rgb);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(state.id);
        if (entity == null || !HumanClient.isHuman(entity)) {
            return;
        }
        int kind = HumanClient.kind(entity);
        float e = HumanClient.elapsed(entity, state.ageInTicks);
        if (kind == 0 || e < 0.0F) {
            return;
        }
        PlayerModel model = this.getParentModel();
        float age = state.ageInTicks;
        // aura du poing, dans le repère exact du bras droit
        pose.pushPose();
        model.rightArm.translateAndRotate(pose);
        pose.translate(HAND_X / 16.0F, HAND_Y / 16.0F, 0.0F);
        fistAura(this.draw, pose, collector, kind, e, age, state.id, 1.0F);
        pose.popPose();
        // traînée du bras, flammes, effets du corps : dans le repère du modèle
        trail(pose, collector, model, kind, e, age);
        if (kind == Human.IMPACT) {
            armGhosts(pose, collector, model, state, e, age, light);
        }
        if (kind == Human.CONQUEROR) {
            conquerorBody(pose, collector, model, e, age, state.id);
        }
        if (kind == Human.RUSH || kind == Human.RUSH_END) {
            rushBody(pose, collector, entity, state, kind, e, age, light);
        }
    }

    // ------------------------------------------------------------------ aura du poing (repère de la main)

    /**
     * Aura autour du poing : {@code k} réduit l'échelle (vue à la première personne : la main est près de la caméra). Repère :
     * origine au poing, +Y le long du bras vers le bout des doigts.
     */
    static void fistAura(HumanDraw draw, PoseStack pose, SubmitNodeCollector col, int kind, float e, float age, int seed, float k) {
        switch (kind) {
            case Human.IMPACT -> {
                // AUCUNE lueur pendant la préparation : c'est la posture qui prépare le coup. Pendant la frappe seulement :
                // lignes de vitesse qui filent le long du bras (vers l'épaule) et air écrasé devant les jointures.
                float w = HumanLightningImpactAbility.WINDUP, s = HumanLightningImpactAbility.STRIKE;
                float p = HumanClient.ramp(e, w - 0.5F, w + 1.0F) * (1.0F - HumanClient.ramp(e, s + 1.0F, s + 4.0F));
                if (p <= 0.0F) {
                    return;
                }
                Random r = new Random(seed * 3L + (long) (age * 2.0F));
                for (int i = 0; i < 9; i++) {
                    double ang = i * Math.PI * 2 / 9 + r.nextDouble() * 0.4;
                    float rad = (0.17F + r.nextFloat() * 0.14F) * k;
                    float y0 = (0.05F - r.nextFloat() * 0.15F) * k, len = (0.45F + r.nextFloat() * 0.55F) * k * p;
                    float[] a0 = v((float) Math.cos(ang) * rad, y0, (float) Math.sin(ang) * rad);
                    draw.seg(pose, col, 2, a0, v(a0[0], y0 - len, a0[2]), 0.45F + r.nextFloat() * 0.35F, a(0.6F * p, i % 3 == 0 ? AIR_EDGE : AIR));
                }
                // air écrasé devant le poing : calotte d'anneaux serrés (l'air s'empile contre les jointures), qui s'aplatit au contact
                float c = HumanClient.ramp(e, w, s) * (1.0F - HumanClient.ramp(e, s, s + 2.0F));
                if (c > 0.0F) {
                    float[] radii = {0.24F, 0.2F, 0.14F, 0.07F};
                    for (int i = 0; i < radii.length; i++) {
                        draw.flatRing(pose, col, 2, v(0, (0.12F + 0.045F * i * (1.0F - 0.5F * c)) * k, 0), radii[i] * k * (1.0F + 0.25F * c),
                                1.3F - 0.2F * i, a(0.55F * c, i == 0 ? AIR_EDGE : AIR), 18);
                    }
                }
            }
            case Human.BREAKER -> {
                float r0 = HumanEarthBreakerAbility.RAISE, h = HumanEarthBreakerAbility.HOLD, i0 = HumanEarthBreakerAbility.IMPACT;
                float p = HumanClient.ramp(e, 2.0F, r0) * (1.0F - HumanClient.ramp(e, i0, i0 + 3.0F));
                if (p <= 0.0F) {
                    return;
                }
                float tension = HumanClient.ramp(e, r0 - 2.0F, h);
                float pulse = 0.8F + 0.2F * Mth.sin(age * (2.0F + 2.0F * tension));
                draw.ball(pose, col, 0, v(0, 0, 0), (0.08F + 0.09F * p) * pulse * k, a(0.8F * p, 0xFFF0C0));
                draw.ball(pose, col, 1, v(0, 0, 0), (0.15F + 0.15F * p) * pulse * k, a(0.4F * p, AMBER));
                // veines de force qui remontent le long du bras (de l'épaule au poing)
                Random r = new Random(seed * 5L + (long) (age * 1.2F));
                for (int vein = 0; vein < 3; vein++) {
                    float[] prev = v(0.0F, 0.0F, 0.0F);
                    double ang = vein * 2.1 + 0.4;
                    for (int s = 1; s <= 5; s++) {
                        float y = -s * 0.12F * k; // vers l'épaule
                        float rr = 0.15F * k + (r.nextFloat() - 0.5F) * 0.03F;
                        float[] q = v((float) Math.cos(ang + s * 0.35) * rr, y, (float) Math.sin(ang + s * 0.35) * rr);
                        float grow = Mth.clamp(p * 6.0F - s, 0.0F, 1.0F); // les veines s'allument du poing vers l'épaule
                        draw.seg(pose, col, 1, prev, q, 0.7F, a(0.8F * grow, vein == 1 ? GOLD : AMBER));
                        prev = q;
                    }
                }
                // anneau de chaleur pendant la tension
                if (tension > 0.0F) {
                    draw.flatRing(pose, col, 2, v(0, 0, 0), (0.25F + 0.05F * Mth.sin(age * 1.7F)) * k, 1.1F, a(0.6F * tension * p, GOLD), 18);
                }
                // la descente : l'air se comprime sous le poing (cône orienté vers l'avant des doigts)
                float slam = HumanClient.ramp(e, h, i0) * (1.0F - HumanClient.ramp(e, i0, i0 + 2.0F));
                if (slam > 0.0F) {
                    draw.cone(pose, col, 2, v(0, 0.12F * k, 0), v(0, 1, 0), 0.5F * k, 0.12F * k, 0.4F * k, 0.75F * slam, AIR, 4, 1.4F);
                }
            }
            case Human.CONQUEROR -> {
                // GANTELET DE FORCE : le poing d'air (même modèle que l'onde) se forme SUR la main, en trois couches, puis des
                // anneaux de force en blocs se verrouillent autour du poignet. Au lancement, tout file vers l'avant.
                float c = HumanConquerorFistAbility.CHARGE, rel = HumanConquerorFistAbility.RELEASE;
                float p = HumanClient.ramp(e, 0.0F, c);
                float form = HumanClient.ramp(e, 0.0F, 10.0F);
                float shot = HumanClient.snap(e, c, rel);
                float gone = 1.0F - HumanClient.ramp(e, rel, rel + 3.0F);
                if (form <= 0.0F || gone <= 0.0F) {
                    return;
                }
                float full = HumanClient.ramp(e, c - 8.0F, c);                  // dernières secondes : compression maximale
                float pulse = 1.0F + 0.06F * Mth.sin(age * (1.2F + 2.0F * p)) - 0.08F * full;
                float jitter = 0.012F * full * k;
                // 1. le gantelet : il se resserre sur la main en apparaissant, grossit avec la puissance, s'étire au lancement
                float s = (0.55F + 0.35F * p) * k * pulse * (1.0F + 0.6F * (1.0F - form));
                pose.pushPose();
                pose.translate(Mth.sin(age * 5.1F) * jitter, 0.04F * k + 0.25F * shot * k, Mth.cos(age * 4.3F) * jitter);
                pose.rotateDegrees(com.mojang.math.Axis.XP, 90.0F); // le modèle pointe vers -Z : le long du bras, vers les doigts
                pose.rotateDegrees(com.mojang.math.Axis.ZP, Mth.sin(age * 0.6F) * 6.0F);
                pose.scale(1.0F, 1.0F, 1.0F + 0.8F * shot);
                draw.fistHere(pose, col, 0, s * 0.82F, a((0.45F + 0.25F * full) * form * gone, 0xFFF6D8));
                draw.fistHere(pose, col, 1, s, a(0.42F * form * gone, GOLD));
                draw.fistHere(pose, col, 2, s * 1.22F, a((0.12F + 0.12F * p) * gone, AMBER));
                draw.fistHere(pose, col, 3, s * 1.42F, a(0.07F * p * gone, FLASH));
                pose.popPose();
                // 2. anneaux de force qui se verrouillent un à un autour du poignet, tournent de plus en plus vite ;
                //    au lancement, ils glissent hors du poing et s'élargissent en ondes
                for (int i = 0; i < 3; i++) {
                    float lock = HumanClient.snap(e, 6.0F + i * 6.0F, 10.0F + i * 6.0F);
                    if (lock <= 0.0F) {
                        continue;
                    }
                    float spin = age * (10.0F + 7.0F * i + 20.0F * p) * (i % 2 == 0 ? 1.0F : -1.0F);
                    float y = (-0.16F - 0.13F * i) * k + (0.55F + 0.2F * i) * shot * k;
                    float r = (0.21F + 0.035F * i) * k * (0.85F + 0.25F * p) * (1.0F + 0.3F * (1.0F - lock)) * (1.0F + 1.8F * shot);
                    draw.ringModel(pose, col, 1, v(0, y, 0), v(0, 1, 0), r, spin, 0.7F,
                            a(0.8F * lock * (1.0F - shot * 0.7F) * gone, i == 1 ? FLASH : GOLD));
                }
                // 3. l'air est aspiré vers le poing (traits qui convergent), jusqu'à la compression finale
                Random r = new Random(seed * 7L + (long) (age * 2.0F));
                for (int i = 0; i < 7; i++) {
                    float[] d = GoroLightning.normalize(v(r.nextFloat() - 0.5F, r.nextFloat() - 0.5F, r.nextFloat() - 0.5F));
                    float phase = (age * 0.18F + r.nextFloat()) % 1.0F;
                    float r1 = (1.1F - 0.8F * phase) * k, r0 = r1 * 0.6F;
                    draw.seg(pose, col, 3, v(d[0] * r1, d[1] * r1, d[2] * r1), v(d[0] * r0, d[1] * r0, d[2] * r0), 0.8F,
                            a(0.5F * p * (1.0F - phase) * (1.0F - shot), AIR));
                }
                // 4. arcs de puissance qui crépitent sur le gantelet (de plus en plus nombreux)
                for (int i = 0; i < (int) (5 * p); i++) {
                    float[] d = GoroLightning.normalize(v(r.nextFloat() - 0.5F, r.nextFloat() * 0.8F, r.nextFloat() - 0.5F));
                    float[] q = GoroLightning.normalize(v(d[0] + (r.nextFloat() - 0.5F), d[1] + (r.nextFloat() - 0.5F), d[2] + (r.nextFloat() - 0.5F)));
                    float r1 = 0.2F * s, r2 = 0.34F * s;
                    draw.seg(pose, col, 2, v(d[0] * r1, d[1] * r1, d[2] * r1), v(q[0] * r2, q[1] * r2, q[2] * r2), 0.6F,
                            a(0.8F * p * gone, i % 2 == 0 ? GOLD : FLASH));
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ traînée du bras (repère du modèle)

    private void trail(PoseStack pose, SubmitNodeCollector col, PlayerModel model, int kind, float e, float age) {
        float from, to, width;
        int color;
        switch (kind) {
            case Human.IMPACT -> {
                from = HumanLightningImpactAbility.WINDUP - 0.5F;
                to = HumanLightningImpactAbility.STRIKE + 3.0F;
                width = 3.0F;
                color = AIR;
            }
            case Human.BREAKER -> {
                from = HumanEarthBreakerAbility.HOLD;
                to = HumanEarthBreakerAbility.IMPACT + 3.0F;
                width = 4.5F;
                color = AMBER;
            }
            case Human.CONQUEROR -> {
                from = HumanConquerorFistAbility.CHARGE;
                to = HumanConquerorFistAbility.RELEASE + 5.0F;
                width = 7.0F;
                color = GOLD;
            }
            default -> {
                return;
            }
        }
        if (e < from || e > to + 4.0F) {
            return;
        }
        float fadeOut = 1.0F - Mth.clamp((e - to) / 4.0F, 0.0F, 1.0F);
        // position EXACTE du poing maintenant (bras tel qu'il est dessiné), puis positions passées (poses pures) recalées dessus
        Vector3f now = new Quaternionf().rotationZYX(model.rightArm.zRot, model.rightArm.yRot, model.rightArm.xRot)
                .transform(new Vector3f(HAND_X, HAND_Y, 0.0F)).add(model.rightArm.x, model.rightArm.y, model.rightArm.z);
        float[] weight = new float[1];
        HumanPose.Pose cur = HumanPose.sample(kind, e, model.head.xRot, model.head.yRot, age, weight);
        if (cur == null) {
            return;
        }
        Vector3f offset = new Vector3f(now).sub(cur.fist());
        int samples = 10;
        float span = 4.0F;
        float[] prev = null;
        for (int i = 0; i <= samples; i++) {
            float t = e - span * i / samples;
            if (t < from) {
                break;
            }
            HumanPose.Pose p = HumanPose.sample(kind, t, model.head.xRot, model.head.yRot, age - (e - t), weight);
            if (p == null) {
                break;
            }
            Vector3f f = p.fist().add(offset);
            float[] q = v(f.x / 16.0F, f.y / 16.0F, f.z / 16.0F);
            if (prev != null) {
                float k = (float) i / samples;
                float w = width * (1.0F - 0.85F * k);
                float al = (1.0F - k) * fadeOut;
                this.draw.seg(pose, col, 1, prev, q, w * 0.45F, a(0.8F * al, FLASH));
                this.draw.seg(pose, col, 2, prev, q, w, a(0.45F * al, color));
                if (kind == Human.CONQUEROR) { // traînée volumineuse : troisième couche, large halo ambré
                    this.draw.seg(pose, col, 3, prev, q, w * 1.8F, a(0.18F * al, AMBER));
                }
            }
            prev = q;
        }
    }

    // ------------------------------------------------------------------ Poing du Conquérant : aura du corps

    private void conquerorBody(PoseStack pose, SubmitNodeCollector col, PlayerModel model, float e, float age, int seed) {
        float c = HumanConquerorFistAbility.CHARGE, rel = HumanConquerorFistAbility.RELEASE;
        float p = HumanClient.ramp(e, 4.0F, c) * (1.0F - HumanClient.ramp(e, rel, rel + 8.0F));
        if (p <= 0.0F) {
            return;
        }
        Random r = new Random(seed * 13L);
        // 1. flammes de combat (modèles pixel) qui montent autour du corps, et vacillent (repère du modèle : pieds vers y = 1,5)
        int tongues = 12;
        for (int i = 0; i < tongues; i++) {
            double ang = i * Math.PI * 2 / tongues + r.nextDouble() * 0.3;
            float rad = 0.42F + r.nextFloat() * 0.12F, tall = 0.9F + r.nextFloat() * 0.9F, ph = r.nextFloat() * 6.28F;
            float show = Mth.clamp(p * tongues - i * 0.6F, 0.0F, 1.0F); // elles s'allument une à une
            if (show <= 0.0F) {
                continue;
            }
            float flick = 0.8F + 0.2F * Mth.sin(age * 1.7F + ph) + 0.1F * Mth.sin(age * 4.3F + ph * 2.0F);
            float[] base = v((float) Math.cos(ang) * rad, 1.48F, (float) Math.sin(ang) * rad);
            float lean = (float) Math.sin(ang) * 10.0F; // penchées vers l'extérieur
            this.draw.flame(pose, col, 3, base, tall * flick * (0.5F + 0.6F * p) * show, 1.6F, -lean + Mth.sin(age * 0.9F + ph) * 6.0F,
                    (float) Math.cos(ang) * 12.0F, a((0.3F + 0.08F * (i % 2)) * p * show, i % 3 == 0 ? AMBER : GOLD));
        }
        // 2. flammes qui jaillissent du poing (position EXACTE du poing dessiné)
        Vector3f hand = new Quaternionf().rotationZYX(model.rightArm.zRot, model.rightArm.yRot, model.rightArm.xRot)
                .transform(new Vector3f(HAND_X, HAND_Y, 0.0F)).add(model.rightArm.x, model.rightArm.y, model.rightArm.z).mul(1.0F / 16.0F);
        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI / 3 + age * 0.15;
            float ph = i * 1.7F;
            float flick = 0.75F + 0.25F * Mth.sin(age * 2.1F + ph);
            float[] base = v(hand.x + (float) Math.cos(ang) * 0.1F, hand.y, hand.z + (float) Math.sin(ang) * 0.1F);
            this.draw.flame(pose, col, 3, base, (0.28F + 0.2F * p) * flick * p, 1.3F, Mth.sin(age + ph) * 10.0F,
                    (float) Math.cos(ang) * 18.0F, a(0.45F * p, i % 2 == 0 ? GOLD : AMBER));
        }
        // 3. cercle d'appui au sol : anneau de force en blocs qui tourne et pulse
        float pulse = 0.5F + 0.5F * Mth.sin(age * 0.9F);
        this.draw.ringModel(pose, col, 2, v(0, 1.48F, 0), v(0, -1, 0), 0.65F + 0.45F * p + 0.06F * pulse, age * 4.0F, 0.6F, a(0.5F * p, GOLD));
        this.draw.ringModel(pose, col, 2, v(0, 1.49F, 0), v(0, -1, 0), 0.95F + 0.6F * p, -age * 2.5F, 0.4F, a(0.25F * p, AMBER));
        // 4. braises qui s'élèvent
        Random er = new Random(seed * 17L + (long) (age * 1.3F));
        for (int i = 0; i < (int) (10 * p); i++) {
            float phase = (age * 0.05F + er.nextFloat()) % 1.0F;
            double ang = er.nextDouble() * Math.PI * 2;
            float rad = 0.3F + er.nextFloat() * 0.5F;
            float[] q = v((float) Math.cos(ang) * rad, 1.4F - phase * 2.4F, (float) Math.sin(ang) * rad);
            this.draw.seg(pose, col, 2, q, v(q[0], q[1] - 0.08F, q[2]), 0.9F, a(0.9F * p * (1.0F - phase), i % 2 == 0 ? GOLD : EMBER));
        }
    }

    // ------------------------------------------------------------------ Ruée Destructrice (repère du modèle)

    /** Mise à l'échelle du modèle du joueur par Minecraft (15/16) (comme HakiAfterimage). */
    private static final float PLAYER_SCALE = 0.9375F;

    /**
     * Impact Fulgurant : IMAGES RÉMANENTES DU BRAS pendant la frappe. Le vrai bras du joueur (avec son skin), dessiné translucide
     * aux positions qu'il occupait un instant plus tôt (HumanPose) : le poing laisse un sillage de bras fantômes, comme un flou
     * de mouvement. Le bras est placé par la matrice seulement (Minecraft remet les pièces à leur pose de repos au dessin).
     */
    private void armGhosts(PoseStack pose, SubmitNodeCollector col, PlayerModel model, AvatarRenderState state, float e, float age,
                           int light) {
        float w = HumanLightningImpactAbility.WINDUP, s = HumanLightningImpactAbility.STRIKE;
        float p = HumanClient.ramp(e, w - 0.5F, w + 0.5F) * (1.0F - HumanClient.ramp(e, s + 0.5F, s + 3.0F));
        if (p <= 0.0F) {
            return;
        }
        float[] weight = new float[1];
        HumanPose.Pose now = HumanPose.sample(Human.IMPACT, e, model.head.xRot, model.head.yRot, age, weight);
        if (now == null) {
            return;
        }
        float[] cur = now.rightArm();
        Identifier skin = state.skin.body().texturePath();
        PartPose rest = model.rightArm.getInitialPose();
        for (int i = 1; i <= 4; i++) {
            float t = e - i * 0.55F;
            HumanPose.Pose past = HumanPose.sample(Human.IMPACT, t, model.head.xRot, model.head.yRot, age - i * 0.55F, weight);
            if (past == null || t < w - 1.0F) {
                break;
            }
            float[] g = past.rightArm();
            pose.pushPose();
            pose.translate((model.rightArm.x + g[0] - cur[0]) / 16.0F, (model.rightArm.y + g[1] - cur[1]) / 16.0F,
                    (model.rightArm.z + g[2] - cur[2]) / 16.0F);
            pose.mulPose(new Matrix4f().rotation(new Quaternionf().rotationZYX(model.rightArm.zRot + g[5] - cur[5],
                    model.rightArm.yRot + g[4] - cur[4], model.rightArm.xRot + g[3] - cur[3])));
            pose.translate(-rest.x() / 16.0F, -rest.y() / 16.0F, -rest.z() / 16.0F);
            float alpha = 0.42F * p * (1.0F - (i - 1) / 4.0F);
            col.order(4).submitModelPart(model.rightArm, pose, RenderTypes.entityTranslucent(skin), light, OverlayTexture.NO_OVERLAY,
                    null, a(alpha, 0xE6F0FF));
            pose.popPose();
        }
    }

    /**
     * Direction RÉELLE du déplacement du joueur dans le repère de son modèle {x, z, vitesse} (-z = devant le corps). Le corps
     * peut regarder ailleurs que là où il fonce : les effets de vitesse suivent le vrai mouvement.
     */
    private static float[] motionInModel(Entity entity, float bodyYaw) {
        double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
        float speed = (float) Math.sqrt(dx * dx + dz * dz);
        if (speed < 1.0E-3F) { // immobile : droit devant le corps
            return new float[] {0.0F, -1.0F, 0.0F};
        }
        float wx = (float) (dx / speed), wz = (float) (dz / speed);
        double phi = Math.toRadians(180.0F - bodyYaw);
        // inverse du rendu des entités : rotation Y de -(180 - lacet du corps), puis miroir (x, y)
        float rx = (float) (wx * Math.cos(phi) - wz * Math.sin(phi));
        float rz = (float) (wx * Math.sin(phi) + wz * Math.cos(phi));
        return new float[] {-rx, rz, speed};
    }

    /**
     * Ruée Destructrice, effets qui collent au corps : images rémanentes du corps entier (le vrai modèle, dans sa pose de course,
     * laissé derrière lui le long du VRAI déplacement), courants d'air qui épousent le corps depuis l'épaule et filent vers
     * l'arrière, front d'air comprimé devant l'épaule, poussière arrachée aux pieds. À la prise d'appui : le sol se tasse.
     * La traînée 3D qui suit le trajet dans le monde est l'entité HumanFx.TRAIL (HumanFxRenderer).
     */
    private void rushBody(PoseStack pose, SubmitNodeCollector col, Entity entity, AvatarRenderState state, int kind, float e,
                          float age, int light) {
        float crouch = HumanDestructiveRushAbility.CROUCH;
        if (kind == Human.RUSH && e < crouch) {
            // prise d'appui : poussière tassée qui s'écarte des pieds sous la pression
            float k = e / crouch;
            for (int i = 0; i < 6; i++) {
                double ang = i * Math.PI / 3 + 0.3;
                float r = 0.35F + 0.45F * k;
                this.draw.dust(pose, col, v((float) Math.cos(ang) * r, 1.38F, (float) Math.sin(ang) * r), i * 40.0F + age * 2.0F,
                        0.2F + 0.18F * k, 0.5F * k, i % 2 == 0 ? DUST : HumanDraw.DUST_DARK, light);
            }
            return;
        }
        float[] m = motionInModel(entity, state.bodyRot);
        float intensity = Mth.clamp(m[2] / (float) HumanDestructiveRushAbility.SPEED, 0.0F, 1.0F);
        float p = kind == Human.RUSH ? HumanClient.snap(e, crouch, crouch + 2.0F) * Math.max(0.35F, intensity)
                : (1.0F - HumanClient.ramp(e, 0.0F, 6.0F)) * intensity;
        if (p <= 0.01F) {
            return;
        }
        float[] fwd = v(m[0], 0.0F, m[1]);     // vers où il fonce (repère du modèle)
        float[] back = v(-m[0], 0.0F, -m[1]);
        float[] side = v(-fwd[2], 0.0F, fwd[0]);
        // 1. images rémanentes du corps entier, plus espacées avec la vitesse, de plus en plus pâles
        Identifier skin = state.skin.body().texturePath();
        for (int i = 1; i <= 3; i++) {
            float dist = i * (0.45F + 0.35F * intensity);
            float alpha = (0.34F - 0.09F * i) * p;
            if (alpha <= 0.02F) {
                continue;
            }
            pose.pushPose();
            pose.translate(back[0] * dist / PLAYER_SCALE, 0.0F, back[2] * dist / PLAYER_SCALE);
            col.order(4).submitModel(this.getParentModel(), state, pose, RenderTypes.entityTranslucent(skin), light,
                    OverlayTexture.NO_OVERLAY, a(alpha, 0xDCE8FF), null, state.outlineColor);
            pose.popPose();
        }
        // 2. courants d'air : des lignes partent de l'avant de l'épaule, s'écartent autour du corps et filent vers l'arrière
        //    (en tirets qui défilent : on voit l'air glisser le long du corps)
        float[] shoulder = HumanDraw.add(v(-0.28F, 0.32F, 0.0F), fwd, 0.55F);
        for (int line = 0; line < 9; line++) {
            double ang = line * Math.PI * 2 / 9;
            float cy = (float) Math.sin(ang), cx = (float) Math.cos(ang);
            float[] prev = null;
            for (int sg = 0; sg <= 8; sg++) {
                float t = sg / 8.0F;
                float along = -0.15F + 2.6F * t * (0.6F + 0.4F * intensity);
                float open = 1.0F - (1.0F - Math.min(1.0F, t * 2.5F)) * (1.0F - Math.min(1.0F, t * 2.5F));
                float rad = 0.12F + 0.55F * open;
                float[] q = HumanDraw.add(HumanDraw.add(HumanDraw.add(shoulder, back, along), side, cx * rad), v(0, 1, 0), cy * rad * 1.3F + 0.25F * t);
                if (prev != null) {
                    float dash = (t * 3.0F - age * 0.9F + line * 0.37F) % 1.0F;
                    if ((dash < 0.0F ? dash + 1.0F : dash) < 0.6F) {
                        this.draw.seg(pose, col, 2, prev, q, 0.7F * (1.0F - 0.6F * t), a(0.5F * p * (1.0F - t), line % 3 == 0 ? AIR_EDGE : AIR));
                    }
                }
                prev = q;
            }
        }
        // 3. front d'air comprimé devant l'épaule : arcs serrés, perpendiculaires au mouvement, qui vibrent
        for (int i = 0; i < 4; i++) {
            float off = 0.08F * i + 0.02F * Mth.sin(age * 3.0F + i);
            float[] c = HumanDraw.add(shoulder, fwd, 0.12F - off);
            arc(pose, col, c, side, v(0, 1, 0), fwd, 0.16F + 0.1F * i, 1.2F - 0.2F * i, a((0.55F - 0.1F * i) * p, i == 0 ? AIR_EDGE : AIR));
        }
        // 4. poussière arrachée derrière les pieds (suit le joueur ; celle qui reste sur le trajet : HumanFx.TRAIL)
        for (int i = 0; i < 4; i++) {
            float phase = (age * 0.3F + i / 4.0F) % 1.0F;
            float[] c = HumanDraw.add(v(i % 2 == 0 ? -0.15F : 0.15F, 1.4F - 0.35F * phase, 0.0F), back, 0.25F + 1.3F * phase);
            this.draw.dust(pose, col, c, i * 50.0F + age * 4.0F, 0.25F + 0.4F * phase, 0.55F * p * (1.0F - phase),
                    i % 2 == 0 ? DUST : HumanDraw.DUST_DARK, light);
        }
    }

    /**
     * Front d'air : arc qui fait le tour de l'avant de l'épaule dans le plan (u, w) perpendiculaire au mouvement, ses bords
     * rabattus vers l'arrière ({@code fwd} : sens du mouvement), comme une étrave.
     */
    private void arc(PoseStack pose, SubmitNodeCollector col, float[] c, float[] u, float[] w, float[] fwd, float r, float width, int color) {
        int n = 12;
        float[] prev = null;
        for (int i = 0; i <= n; i++) {
            double ang = 2 * Math.PI * i / n;
            float[] p = HumanDraw.onCircle(c, u, w, r, ang);
            float bend = 0.5F * r; // les bords reculent : forme d'étrave plutôt qu'un cercle plat
            p = HumanDraw.add(p, fwd, -bend * (0.5F + 0.5F * (float) Math.abs(Math.cos(ang))));
            if (prev != null) {
                this.draw.seg(pose, col, 2, prev, p, width, color);
            }
            prev = p;
        }
    }

    // ------------------------------------------------------------------ première personne

    /**
     * Première personne : la main droite du joueur local (AvatarRenderer.renderHand, AvatarRendererHandMixin) reçoit la même
     * aura que dans le monde, un peu réduite (elle est tout près de la caméra).
     */
    public static void submitHand(PoseStack pose, SubmitNodeCollector collector, ModelPart arm, PlayerModel model) {
        Minecraft mc = Minecraft.getInstance();
        if (shared == null || mc.player == null || arm != model.rightArm || !HumanClient.isHuman(mc.player)) {
            return;
        }
        float age = mc.player.tickCount + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int kind = HumanClient.kind(mc.player);
        float e = HumanClient.elapsed(mc.player, age);
        if (kind == 0 || e < 0.0F) {
            return;
        }
        pose.pushPose();
        arm.translateAndRotate(pose);
        pose.translate(HAND_X / 16.0F, HAND_Y / 16.0F, 0.0F);
        fistAura(shared, pose, collector, kind, e, age, mc.player.getId(), 0.6F);
        pose.popPose();
    }
}
