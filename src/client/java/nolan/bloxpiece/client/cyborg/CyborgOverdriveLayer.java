package nolan.bloxpiece.client.cyborg;

import java.util.Random;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.ability.Cyborg;
import nolan.bloxpiece.client.goro.GoroLightning;
import nolan.bloxpiece.client.human.HumanDraw;

/**
 * SURCHARGE MOTEUR (race Cyborg) : le corps mécanique pousse ses capacités au maximum. Tout est dessiné dans le repère des
 * membres du joueur (couche de rendu) : les effets collent au corps, en marche, en saut et pendant toutes les animations.
 * <ul>
 *   <li>RÉACTEUR dans la poitrine : une cellule de plasma qui bat comme un coeur, cerclée d'un anneau hexagonal qui tourne ;</li>
 *   <li>CONDUITS D'ÉNERGIE : des lignes lumineuses sur le torse, les deux bras (le bras mécanique plus brillant) et les jambes,
 *       parcourues par des impulsions qui partent du réacteur ; au démarrage, elles s'allument du réacteur vers les membres ;</li>
 *   <li>GYROSCOPES : deux anneaux hexagonaux en plaques qui tournent autour du torse sur des axes qui basculent lentement ;</li>
 *   <li>ARCS électriques en zigzag qui sautent entre les membres (épaules, mains, réacteur, genoux), et parfois vers le sol ;</li>
 *   <li>TUYÈRES dans le dos : panache de chaleur qui ondule et vapeur ; cercle de pression au sol ;</li>
 *   <li>BATTEMENT : à chaque pulsation, le réacteur s'embrase et une onde part de la poitrine ;</li>
 *   <li>FIN : la dernière seconde, tout grésille et s'éteint des membres vers le réacteur, puis une décharge (onde et vapeur).</li>
 * </ul>
 * Première personne : conduits et arcs sur les bras ({@link #submitHand}).
 */
public class CyborgOverdriveLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final int CORE = 0xFFF8D0, ENERGY = 0xFFE24A, HOT = 0xFF8A2A, DEEP = 0xD8401E;
    /** Durée (ticks) de l'extinction et de la décharge finale, et période du battement du réacteur. */
    private static final float END = 24.0F, BEAT = 16.0F;

    private static CyborgOverdriveLayer shared;
    private final ModelPart segment, hex, plasma;
    private final HumanDraw draw;

    public CyborgOverdriveLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
        super(parent);
        this.segment = context.bakeLayer(nolan.bloxpiece.client.haki.HakiBoltRenderer.LAYER).getChild("segment");
        this.hex = context.bakeLayer(CyborgFxModels.HEX).getChild("hex");
        this.plasma = context.bakeLayer(CyborgFxModels.PLASMA).getChild("cell");
        this.draw = new HumanDraw(context); // (vapeur : nuages en volume, outils partagés)
        shared = this;
    }

    private static int a(float alpha, int rgb) {
        return GoroLightning.argb(Mth.clamp(alpha, 0.0F, 1.0F), rgb);
    }

    private static RenderType glow() {
        return RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
    }

    // ------------------------------------------------------------------ état

    /** Puissance de la surcharge (0 à 1) : montée au démarrage, pleine pendant, extinction à la fin ; -1 hors surcharge. */
    static float power(Entity entity, float age) {
        float since = CyborgClient.overdrive(entity, age);
        if (since >= 0.0F) {
            float left = Cyborg.OVERDRIVE_TICKS - since;
            return Mth.clamp(left / END, 0.0F, 1.0F);
        }
        float engine = CyborgClient.engine(entity, age); // démarrage
        return engine > 0.0F ? engine : -1.0F;
    }

    /** Fin de la surcharge (0 pendant, monte à 1 à la toute fin) : grésillement et décharge. */
    static float ending(Entity entity, float age) {
        float since = CyborgClient.overdrive(entity, age);
        return since < 0.0F ? 0.0F : 1.0F - Mth.clamp((Cyborg.OVERDRIVE_TICKS - since) / END, 0.0F, 1.0F);
    }

    /** Battement du réacteur (0 à 1, pic bref à chaque période). */
    static float beat(float age) {
        float t = (age % BEAT) / BEAT;
        return t < 0.12F ? t / 0.12F : Math.max(0.0F, 1.0F - (t - 0.12F) / 0.35F);
    }

    /** Point d'un membre (px dans son repère) exprimé dans le repère du modèle (blocs). */
    private static float[] onPart(ModelPart part, float x, float y, float z) {
        Vector3f v = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(new Vector3f(x, y, z))
                .add(part.x, part.y, part.z).mul(1.0F / 16.0F);
        return new float[] {v.x, v.y, v.z};
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector col, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(state.id);
        if (entity == null || !CyborgClient.isCyborg(entity)) {
            return;
        }
        float age = state.ageInTicks;
        float p = power(entity, age);
        if (p <= 0.0F) {
            return;
        }
        float end = ending(entity, age);
        // la dernière seconde : l'énergie grésille (coupures brèves)
        float flick = end > 0.0F && Mth.sin(age * 9.0F + Mth.sin(age * 3.1F) * 2.0F) < -0.2F + end * 0.6F ? 0.35F : 1.0F;
        float k = p * flick;
        float b = beat(age);
        PlayerModel model = this.getParentModel();
        int seed = state.id;

        // 1. conduits d'énergie et réacteur, dans le repère de chaque membre
        pose.pushPose();
        model.body.translateAndRotate(pose);
        bodyConduits(pose, col, k, b, age, p);
        reactor(pose, col, k, b, age, light);
        vents(pose, col, k, age, light, end);
        pose.popPose();
        armConduits(pose, col, model.leftArm, true, k, b, age, p);
        armConduits(pose, col, model.rightArm, false, k, b, age, p);
        legConduit(pose, col, model.leftLeg, k, b, age, p);
        legConduit(pose, col, model.rightLeg, k, b, age, p);

        // 2. gyroscopes : deux anneaux hexagonaux qui tournent autour du torse
        float[] chest = onPart(model.body, 0.0F, 6.0F, 0.0F);
        for (int i = 0; i < 2; i++) {
            float tilt = (i == 0 ? 1.0F : -1.0F);
            float[] axis = GoroLightning.normalize(new float[] {Mth.sin(age * 0.05F + i) * 0.45F * tilt, 1.0F, Mth.cos(age * 0.07F + i * 2.0F) * 0.45F});
            hexRing(pose, col, 2, chest, axis, (0.62F + 0.06F * i + 0.04F * b) * (0.7F + 0.3F * p), age * (14.0F + 6.0F * i) * tilt, 0.55F,
                    a((0.35F + 0.2F * b) * k, i == 0 ? ENERGY : HOT));
        }

        // 3. arcs électriques entre les membres (re-tirés tous les 2 ticks)
        float[][] anchors = {onPart(model.body, 0.0F, 4.0F, -2.4F), onPart(model.leftArm, 1.0F, 10.0F, 0.0F),
                onPart(model.rightArm, -1.0F, 10.0F, 0.0F), onPart(model.body, 3.8F, 0.8F, 0.0F), onPart(model.body, -3.8F, 0.8F, 0.0F),
                onPart(model.leftLeg, 0.0F, 7.0F, -2.2F), onPart(model.rightLeg, 0.0F, 7.0F, -2.2F), onPart(model.head, 0.0F, -8.5F, 0.0F)};
        Random r = new Random(seed * 31L + (long) (age * 0.5F));
        int arcs = 1 + (int) (4 * k) + (end > 0.0F ? 3 : 0);
        for (int i = 0; i < arcs; i++) {
            float[] from = anchors[r.nextInt(anchors.length)];
            float[] to = anchors[r.nextInt(anchors.length)];
            if (from == to) {
                to = HumanDraw.add(from, new float[] {r.nextFloat() - 0.5F, r.nextFloat() - 0.5F, r.nextFloat() - 0.5F}, 0.9F);
            }
            arc(pose, col, from, to, r, k, 0.9F + 0.6F * b);
        }
        // à pleine puissance : une décharge vers le sol de temps en temps
        if (k > 0.8F && r.nextFloat() < 0.35F) {
            float[] foot = {(r.nextFloat() - 0.5F) * 1.6F, 1.5F, (r.nextFloat() - 0.5F) * 1.6F};
            arc(pose, col, anchors[r.nextBoolean() ? 1 : 2], foot, r, k, 1.0F);
        }

        // 4. cercle de pression au sol (anneau hexagonal qui tourne), et onde du battement depuis la poitrine
        hexRing(pose, col, 2, new float[] {0.0F, 1.49F, 0.0F}, new float[] {0, 1, 0}, 0.75F + 0.12F * b, age * 3.0F, 0.5F, a(0.45F * k, HOT));
        float t = (age % BEAT) / BEAT;
        if (t < 0.4F) {
            float wave = t / 0.4F;
            roundRing(pose, col, onPart(model.body, 0.0F, 4.5F, 0.0F), new float[] {0, 1, 0}, 0.35F + 1.0F * wave, 1.4F * (1.0F - wave) + 0.3F,
                    a(0.55F * (1.0F - wave) * k, ENERGY));
        }

        // 5. décharge finale : l'énergie s'échappe en onde et en vapeur
        if (end > 0.6F) {
            float d = (end - 0.6F) / 0.4F;
            roundRing(pose, col, chest, new float[] {0, 1, 0}, 0.5F + 2.2F * d, 2.0F * (1.0F - d) + 0.3F, a(0.7F * (1.0F - d), CORE));
            roundRing(pose, col, chest, new float[] {0, 0, 1}, 0.4F + 1.8F * d, 1.6F * (1.0F - d) + 0.3F, a(0.5F * (1.0F - d), HOT));
        }
    }

    // ------------------------------------------------------------------ éléments

    /** Une ligne de conduit (repère du membre, px), base faible et impulsions qui la parcourent ; allumée jusqu'à {@code lit}. */
    private void conduit(PoseStack pose, SubmitNodeCollector col, float[][] pts, float lit, float k, float b, float age, float phase,
                         float width, int color) {
        if (lit <= 0.0F) {
            return;
        }
        int n = pts.length - 1;
        float pulse = (age * 0.09F + phase) % 1.0F;
        for (int i = 0; i < n; i++) {
            float t0 = (float) i / n, t1 = (float) (i + 1) / n;
            if (t0 >= lit) {
                break;
            }
            float f = Math.min(1.0F, (lit - t0) / (t1 - t0));
            float[] p0 = scale16(pts[i]);
            float[] p1 = scale16(pts[i + 1]);
            p1 = new float[] {p0[0] + (p1[0] - p0[0]) * f, p0[1] + (p1[1] - p0[1]) * f, p0[2] + (p1[2] - p0[2]) * f};
            GoroLightning.segment(this.segment, pose, col, 1, glow(), p0, p1, width, a((0.45F + 0.3F * b) * k, color));
            GoroLightning.segment(this.segment, pose, col, 2, glow(), p0, p1, width * 2.4F, a(0.14F * k, HOT));
            // impulsion qui file du réacteur vers le bout du membre
            float mid = (t0 + t1) * 0.5F;
            float near = 1.0F - Math.min(1.0F, Math.abs(mid - pulse) * 4.0F);
            if (near > 0.0F) {
                GoroLightning.segment(this.segment, pose, col, 0, glow(), p0, p1, width * 1.3F, a(0.95F * near * k, CORE));
            }
        }
    }

    private static float[] scale16(float[] px) {
        return new float[] {px[0] / 16.0F, px[1] / 16.0F, px[2] / 16.0F};
    }

    /** Conduits du torse (face avant) : épine, épaules, côtes. */
    private void bodyConduits(PoseStack pose, SubmitNodeCollector col, float k, float b, float age, float p) {
        float z = -2.25F;
        float lit = Mth.clamp(p * 1.6F, 0.0F, 1.0F);
        conduit(pose, col, new float[][] {{0, 4, z}, {0, 7, z}, {0, 11.6F, z}}, lit, k, b, age, 0.0F, 0.8F, ENERGY);
        conduit(pose, col, new float[][] {{0, 4, z}, {-1.8F, 2.6F, z}, {-3.7F, 1.0F, z}}, lit, k, b, age, 0.2F, 0.8F, ENERGY);
        conduit(pose, col, new float[][] {{0, 4, z}, {1.8F, 2.6F, z}, {3.7F, 1.0F, z}}, lit, k, b, age, 0.2F, 0.8F, ENERGY);
        conduit(pose, col, new float[][] {{0, 7, z}, {-2.6F, 8.2F, z}, {-3.6F, 10.8F, z}}, lit, k, b, age, 0.5F, 0.6F, HOT);
        conduit(pose, col, new float[][] {{0, 7, z}, {2.6F, 8.2F, z}, {3.6F, 10.8F, z}}, lit, k, b, age, 0.5F, 0.6F, HOT);
        // dos : deux lignes vers les tuyères
        float zb = 2.25F;
        conduit(pose, col, new float[][] {{0, 10, zb}, {-2.0F, 6.0F, zb}, {-2.0F, 3.0F, zb}}, lit, k, b, age, 0.3F, 0.6F, HOT);
        conduit(pose, col, new float[][] {{0, 10, zb}, {2.0F, 6.0F, zb}, {2.0F, 3.0F, zb}}, lit, k, b, age, 0.3F, 0.6F, HOT);
    }

    /** Réacteur dans la poitrine : cellule de plasma qui bat, anneau hexagonal qui tourne, rayons de lumière brefs au battement. */
    private void reactor(PoseStack pose, SubmitNodeCollector col, float k, float b, float age, int light) {
        float[] c = {0.0F, 4.0F / 16.0F, -2.6F / 16.0F};
        float s = (0.13F + 0.05F * b) * (0.6F + 0.4F * k);
        cell(pose, col, 0, c, age * 30.0F, age * 17.0F, 0.0F, s, a(0.95F * k, CORE));
        cell(pose, col, 1, c, -age * 20.0F, 45.0F, age * 11.0F, s * 1.45F, a(0.4F * k, ENERGY));
        hexRing(pose, col, 1, c, new float[] {0, 0, -1}, 0.2F + 0.03F * b, age * 40.0F, 0.6F, a(0.85F * k, ENERGY));
        if (b > 0.5F) {
            for (int i = 0; i < 6; i++) {
                double ang = i * Math.PI / 3 + age * 0.2;
                float r = 0.3F + 0.25F * b;
                GoroLightning.segment(this.segment, pose, col, 2, glow(), new float[] {c[0], c[1], c[2] - 0.02F},
                        new float[] {c[0] + (float) Math.cos(ang) * r, c[1] + (float) Math.sin(ang) * r, c[2] - 0.05F}, 0.7F,
                        a(0.6F * (b - 0.5F) * 2.0F * k, CORE));
            }
        }
    }

    /** Tuyères dans le dos : anneau hexagonal, panache de chaleur qui ondule vers l'arrière et le haut, vapeur. */
    private void vents(PoseStack pose, SubmitNodeCollector col, float k, float age, int light, float end) {
        for (int side = -1; side <= 1; side += 2) {
            float[] c = {side * 2.0F / 16.0F, 3.0F / 16.0F, 2.4F / 16.0F};
            hexRing(pose, col, 1, c, new float[] {0, 0, 1}, 0.09F, age * 25.0F * side, 0.6F, a(0.8F * k, HOT));
            for (int i = 0; i < 5; i++) {
                float t = ((age * 0.12F + i / 5.0F) % 1.0F);
                float[] p = {c[0] + Mth.sin(age * 0.7F + i + side) * 0.03F, c[1] - 0.25F * t, c[2] + 0.55F * t};
                roundRing(pose, col, p, new float[] {0, 0.4F, 1}, 0.07F + 0.16F * t, 1.0F * (1.0F - t) + 0.2F,
                        a(0.5F * (1.0F - t) * k, i % 2 == 0 ? HOT : ENERGY));
            }
            // vapeur (plus forte pendant la décharge finale)
            float steam = 0.25F + 0.6F * end;
            float t = (age * 0.05F + (side > 0 ? 0.5F : 0.0F)) % 1.0F;
            this.draw.dust(pose, col, new float[] {c[0], c[1] - 0.3F * t - 0.05F, c[2] + 0.35F + 0.4F * t}, age * 3.0F + side * 40.0F,
                    0.12F + 0.25F * t, steam * (1.0F - t) * k, 0xD8D4DA, light);
        }
    }

    /** Conduits d'un bras : face avant et face extérieure ; le bras mécanique (gauche) est plus large et plus brillant. */
    private void armConduits(PoseStack pose, SubmitNodeCollector col, ModelPart arm, boolean mech, float k, float b, float age, float p) {
        pose.pushPose();
        arm.translateAndRotate(pose);
        drawArm(pose, col, mech, k, b, age, Mth.clamp(p * 1.6F - 0.4F, 0.0F, 1.0F));
        pose.popPose();
    }

    private void drawArm(PoseStack pose, SubmitNodeCollector col, boolean mech, float k, float b, float age, float lit) {
        float side = mech ? 1.0F : -1.0F;     // côté extérieur du bras
        float cx = mech ? 1.0F : -1.0F;       // milieu du bras (px)
        float front = mech ? -2.6F : -2.25F, out = cx + side * (mech ? 2.6F : 2.25F);
        conduit(pose, col, new float[][] {{cx, -1.5F, front}, {cx, 4.0F, front}, {cx, 9.6F, front}}, lit, k, b, age, 0.4F,
                mech ? 1.0F : 0.7F, mech ? ENERGY : HOT);
        conduit(pose, col, new float[][] {{out, -1.0F, 0.0F}, {out, 4.5F, 0.4F}, {out, 9.0F, 0.0F}}, lit, k, b, age, 0.65F,
                mech ? 0.9F : 0.6F, mech ? ENERGY : HOT);
        // poignet : petit anneau hexagonal qui tourne au bout du bras mécanique
        if (mech) {
            hexRing(pose, col, 1, new float[] {cx / 16.0F, 9.0F / 16.0F, 0.0F}, new float[] {0, 1, 0}, 0.2F + 0.02F * b, age * 30.0F, 0.5F,
                    a(0.75F * k * lit, ENERGY));
        }
    }

    private void legConduit(PoseStack pose, SubmitNodeCollector col, ModelPart leg, float k, float b, float age, float p) {
        pose.pushPose();
        leg.translateAndRotate(pose);
        conduit(pose, col, new float[][] {{0, 0.5F, -2.25F}, {0, 6.0F, -2.25F}, {0, 11.5F, -2.25F}},
                Mth.clamp(p * 1.6F - 0.5F, 0.0F, 1.0F), k, b, age, 0.8F, 0.6F, HOT);
        pose.popPose();
    }

    /** Arc électrique en zigzag (trois épaisseurs : coeur blanc, gaine jaune, halo orange). */
    private void arc(PoseStack pose, SubmitNodeCollector col, float[] from, float[] to, Random r, float k, float width) {
        int n = 5;
        float[] prev = from;
        for (int i = 1; i <= n; i++) {
            float t = (float) i / n;
            float[] q = {from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t, from[2] + (to[2] - from[2]) * t};
            if (i < n) {
                q[0] += (r.nextFloat() - 0.5F) * 0.22F;
                q[1] += (r.nextFloat() - 0.5F) * 0.22F;
                q[2] += (r.nextFloat() - 0.5F) * 0.22F;
            }
            GoroLightning.segment(this.segment, pose, col, 0, glow(), prev, q, 0.5F * width, a(0.95F * k, CORE));
            GoroLightning.segment(this.segment, pose, col, 1, glow(), prev, q, 1.3F * width, a(0.5F * k, ENERGY));
            GoroLightning.segment(this.segment, pose, col, 2, glow(), prev, q, 2.6F * width, a(0.16F * k, HOT));
            prev = q;
        }
    }

    /** Anneau hexagonal en plaques (CyborgFxModels.HEX, apothème 6 px) de rayon {@code radius} blocs autour de {@code axis}. */
    private void hexRing(PoseStack pose, SubmitNodeCollector col, int order, float[] c, float[] axis, float radius, float spin, float thick,
                         int color) {
        if (radius <= 0.01F || (color >>> 24) == 0) {
            return;
        }
        float s = radius / 0.375F;
        pose.pushPose();
        pose.translate(c[0], c[1], c[2]);
        HumanDraw.orientY(pose, axis);
        pose.rotateDegrees(Axis.YP, spin);
        pose.scale(s, thick, s);
        col.order(order).submitModelPart(this.hex, pose, RenderTypes.entityTranslucentEmissive(CyborgFxModels.HEX_TEXTURE),
                GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    /** Cellule de plasma (CyborgFxModels.PLASMA), lumineuse. */
    private void cell(PoseStack pose, SubmitNodeCollector col, int order, float[] c, float rx, float ry, float rz, float scale, int color) {
        if (scale <= 0.01F || (color >>> 24) == 0) {
            return;
        }
        pose.pushPose();
        pose.translate(c[0], c[1], c[2]);
        pose.rotateDegrees(Axis.YP, ry);
        pose.rotateDegrees(Axis.XP, rx);
        pose.rotateDegrees(Axis.ZP, rz);
        pose.scale(scale, scale, scale);
        col.order(order).submitModelPart(this.plasma, pose, RenderTypes.entityTranslucentEmissive(CyborgFxModels.PLASMA_TEXTURE),
                GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    /** Anneau rond lumineux (prismes). */
    private void roundRing(PoseStack pose, SubmitNodeCollector col, float[] c, float[] axis, float radius, float width, int color) {
        if (radius <= 0.01F || (color >>> 24) == 0) {
            return;
        }
        float[][] perp = GoroLightning.perpendiculars(GoroLightning.normalize(axis));
        int n = 20;
        for (int i = 0; i < n; i++) {
            GoroLightning.segment(this.segment, pose, col, 2, glow(), HumanDraw.onCircle(c, perp[0], perp[1], radius, 2 * Math.PI * i / n),
                    HumanDraw.onCircle(c, perp[0], perp[1], radius, 2 * Math.PI * (i + 1) / n), width, color);
        }
    }

    // ------------------------------------------------------------------ première personne

    /**
     * Première personne : les conduits et un arc sur le bras vu (main gauche mécanique : CyborgArmLayer.renderFirstPerson ; main
     * droite : AvatarRendererHandMixin). {@code arm} : la pièce du bras, déjà posée par Minecraft.
     */
    public static void submitHand(PoseStack pose, SubmitNodeCollector col, ModelPart arm, boolean mech) {
        Minecraft mc = Minecraft.getInstance();
        if (shared == null || mc.player == null || !CyborgClient.isCyborg(mc.player)) {
            return;
        }
        float age = mc.player.tickCount + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float p = power(mc.player, age);
        if (p <= 0.0F) {
            return;
        }
        float b = beat(age);
        pose.pushPose();
        arm.translateAndRotate(pose);
        shared.drawArm(pose, col, mech, p, b, age, Mth.clamp(p * 1.6F - 0.4F, 0.0F, 1.0F));
        Random r = new Random(mc.player.getId() * 31L + (long) (age * 0.5F));
        if (r.nextFloat() < 0.5F * p) {
            float cx = mech ? 1.0F / 16.0F : -1.0F / 16.0F;
            shared.arc(pose, col, new float[] {cx, 1.0F / 16.0F, -0.15F}, new float[] {cx + (r.nextFloat() - 0.5F) * 0.3F, 0.6F, -0.2F}, r, p, 0.8F);
        }
        pose.popPose();
    }
}
