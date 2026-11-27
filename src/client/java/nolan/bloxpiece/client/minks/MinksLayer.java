package nolan.bloxpiece.client.minks;

import java.util.ArrayList;
import java.util.List;
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

import nolan.bloxpiece.ability.Minks;
import nolan.bloxpiece.ability.MinksDanceAbility;
import nolan.bloxpiece.ability.MinksSulongAbility;
import nolan.bloxpiece.client.fx.Electric;
import nolan.bloxpiece.client.goro.GoroLightning;

/**
 * Race Minks : apparence et effets qui suivent le joueur (couche de rendu, chez tous les joueurs ; rien sur les autres races) :
 * <ul>
 *   <li>OREILLES de lapin blanches sur la tête (elles suivent la tête, ballottent quand il court, frémissent de temps en temps) et QUEUE
 *       duveteuse en bas du dos (elle remue) ; le skin n'est jamais modifié ;</li>
 *   <li>GANTS ÉLECTRIQUES aux deux mains quand ils sont sortis : un gant fait d'électricité bleue (coque, doigts, griffes),
 *       arcs le long des doigts, décharges aux griffes ; il se forme à l'activation ;</li>
 *   <li>barrage : lignes de vitesse le long du bras qui frappe, air comprimé devant le poing ;</li>
 *   <li>danse : électricité qui parcourt le corps pendant la préparation, traces de griffes bleues à chaque frappe ;</li>
 *   <li>SULONG : cinématique de transformation (étincelles, éclairs qui parcourent les membres, aura qui se forme), puis aura
 *       permanente (langues électriques en deux couches, éclairs sur bras, jambes et dos, pulsations, éclairs qui se détachent) ;
 *       MODÈLES PROPRES (grandes oreilles hérissées de touffes, gros pompon), morphose avec arcs ; aura électrique (arcs
 *       qui crépitent sur la silhouette, anneaux d'éclairs qui tournent). La transformation : électricité qui s'accumule.</li>
 * </ul>
 * Première personne : griffes sur les mains vues ({@link #submitHand}).
 */
public class MinksLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    /** Électricité BLEUE des Minks : coeur blanc bleuté, gaine bleu électrique clair, halo bleu intense (plus claire que le Goro). */
    public static final int CORE = 0xF2FBFF, SHEATH = 0x4CC3FF, HAZE = 0x2A6BFF;

    private static MinksLayer shared;
    private final ModelPart earNormal, earSulong, tailNormal, tailSulong, gloveRight, gloveLeft, segment;
    /** Éclairs : système électrique commun (forme du Goro), palette des Minks. */
    private final Electric electric;

    public MinksLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
        super(parent);
        this.earNormal = context.bakeLayer(MinksModels.EAR).getChild("ear");
        this.earSulong = context.bakeLayer(MinksModels.EAR_SULONG).getChild("ear");
        this.tailNormal = context.bakeLayer(MinksModels.TAIL).getChild("tail");
        this.tailSulong = context.bakeLayer(MinksModels.TAIL_SULONG).getChild("tail");
        this.gloveRight = context.bakeLayer(MinksModels.GLOVE_RIGHT).getChild("glove");
        this.gloveLeft = context.bakeLayer(MinksModels.GLOVE_LEFT).getChild("glove");
        this.segment = context.bakeLayer(nolan.bloxpiece.client.haki.HakiBoltRenderer.LAYER).getChild("segment");
        this.electric = new Electric(this.segment);
        shared = this;
    }

    static int a(float alpha, int rgb) {
        return GoroLightning.argb(Mth.clamp(alpha, 0.0F, 1.0F), rgb);
    }

    static RenderType glow() {
        return RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector col, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(state.id);
        if (entity == null || !MinksClient.isMinks(entity)) {
            return;
        }
        PlayerModel model = this.getParentModel();
        float age = state.ageInTicks;
        float su = MinksClient.sulong(entity, age);
        int kind = MinksClient.kind(entity);
        float e = MinksClient.elapsed(entity, age);
        // 1. oreilles et queue (forme normale, ou Sulong : plus grandes, blanches)
        earsAndTail(pose, col, light, model, state, su, age);
        // 2. griffes électriques
        float cl = MinksClient.claws(entity, age);
        if (cl > 0.0F) {
            hand(pose, col, model.rightArm, false, cl, su, age, state.id, light);
            hand(pose, col, model.leftArm, true, cl, su, age, state.id + 7, light);
        }
        // 3. barrage : vitesse le long du bras qui frappe
        if (MinksClient.barrage(kind) && e >= 0.0F) {
            float[] pu = MinksClient.punch(kind, e);
            if (pu[0] != 0.0F && pu[1] > 0.2F) {
                pose.pushPose();
                (pu[0] > 0 ? model.rightArm : model.leftArm).translateAndRotate(pose);
                pose.translate((pu[0] > 0 ? -1.0F : 1.0F) / 16.0F, 10.5F / 16.0F, 0.0F);
                punchStreaks(pose, col, pu[1] * (1.0F + 0.4F * su), age, state.id);
                pose.popPose();
            }
        }
        // danse : traces de griffes à chaque frappe
        if (MinksClient.dance(kind) && e >= 0.0F) {
            danceSlash(pose, col, entity, age, su);
            // avant la plongée finale : l'électricité se concentre autour des mains et du corps
            boolean sul = kind == Minks.DANCE_S;
            float ret = nolan.bloxpiece.ability.MinksDanceAbility.returnTick(sul);
            float focus = MinksClient.ramp(e, ret, ret + 3.0F) * (1.0F - MinksClient.ramp(e, ret + nolan.bloxpiece.ability.MinksDanceAbility.FINAL_DELAY,
                    ret + nolan.bloxpiece.ability.MinksDanceAbility.FINAL_DELAY + 2.0F));
            if (focus > 0.0F) {
                bodyArcs(pose, col, model, 0.8F * focus, age, state.id, true);
                limbSurge(pose, col, model.rightArm, -1.0F, 0.0F, -1.5F, 9.5F, 2.35F, focus, age, state.id);
                limbSurge(pose, col, model.leftArm, 1.0F, 0.0F, -1.5F, 9.5F, 2.35F, focus, age, state.id + 3);
            }
        }
        // 4. danse : électricité sur le corps pendant la préparation
        if (MinksClient.dance(kind) && e >= 0.0F && e < MinksDanceAbility.PREP) {
            bodyArcs(pose, col, model, MinksClient.ramp(e, 0.0F, MinksDanceAbility.PREP) * 0.7F, age, state.id, false);
        }
        // 5. Sulong : cinématique de transformation, puis aura permanente pendant les 30 s
        if (kind == Minks.SULONG && e >= 0.0F) {
            sulongCharge(pose, col, model, e, age, state.id);
        }
        if (su > 0.0F) {
            sulongAura(pose, col, model, su, age, state.id);
        }
    }

    // ------------------------------------------------------------------ oreilles et queue

    private void earsAndTail(PoseStack pose, SubmitNodeCollector col, int light, PlayerModel model, AvatarRenderState state, float su,
                             float age) {
        // ballottement à la course, frémissement de temps en temps
        float run = Mth.sin(state.walkAnimationPos * 0.66F) * state.walkAnimationSpeed;
        float twitch = (float) Math.pow(Math.max(0.0F, Mth.sin(age * 0.11F)), 24.0) * 14.0F;
        // morphose : les modèles normaux rétrécissent pendant la première moitié, ceux de Sulong grandissent pendant la seconde
        float normal = 1.0F - MinksClient.ramp(su, 0.0F, 0.5F), sulong = MinksClient.snap(su, 0.5F, 1.0F);
        pose.pushPose();
        model.head.translateAndRotate(pose);
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * 2.3F / 16.0F, -7.8F / 16.0F, 0.4F / 16.0F);
            pose.rotateDegrees(Axis.ZP, side * (11.0F + 7.0F * sulong) + side * run * 8.0F);
            pose.rotateDegrees(Axis.XP, -10.0F - Math.abs(run) * 14.0F - (side > 0 ? twitch : 0.0F) + 6.0F * sulong);
            if (normal > 0.01F) {
                pose.pushPose();
                pose.scale(normal, normal, normal);
                col.submitModelPart(this.earNormal, pose, RenderTypes.entityCutout(MinksModels.EARS_TEXTURE), light, OverlayTexture.NO_OVERLAY,
                        null, 0xFFFFFFFF);
                pose.popPose();
            }
            if (sulong > 0.01F) {
                pose.pushPose();
                pose.scale(sulong, sulong, sulong);
                col.submitModelPart(this.earSulong, pose, RenderTypes.entityCutout(MinksModels.EARS_SULONG_TEXTURE), light,
                        OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
                pose.popPose();
            }
            pose.popPose();
        }
        pose.popPose();
        pose.pushPose();
        model.body.translateAndRotate(pose);
        pose.translate(0.0F, 10.8F / 16.0F, (2.6F + 1.4F * sulong) / 16.0F);
        pose.rotateDegrees(Axis.YP, Mth.sin(age * 0.35F) * 9.0F + run * 10.0F);
        if (normal > 0.01F) {
            pose.pushPose();
            pose.scale(normal, normal, normal);
            col.submitModelPart(this.tailNormal, pose, RenderTypes.entityCutout(MinksModels.TAIL_TEXTURE), light, OverlayTexture.NO_OVERLAY,
                    null, 0xFFFFFFFF);
            pose.popPose();
        }
        if (sulong > 0.01F) {
            pose.pushPose();
            float puff = sulong * (1.0F + 0.04F * Mth.sin(age * 0.5F)); // le pompon « respire »
            pose.scale(puff, puff, puff);
            col.submitModelPart(this.tailSulong, pose, RenderTypes.entityCutout(MinksModels.TAIL_SULONG_TEXTURE), light,
                    OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
            pose.popPose();
        }
        pose.popPose();
        // pendant la morphose : arcs bleus autour des oreilles et de la queue
        if (su > 0.0F && su < 1.0F) {
            float k = 1.0F - Math.abs(su - 0.5F) * 2.0F;
            Random r = new Random(state.id * 3L + (long) age);
            float[][] spots = {onPart(model.head, -2.3F, -12.0F, 0), onPart(model.head, 2.3F, -12.0F, 0), onPart(model.body, 0, 10.8F, 3.5F)};
            for (float[] p : spots) {
                for (int i = 0; i < 3; i++) {
                    arc(pose, col, p, new float[] {p[0] + (r.nextFloat() - 0.5F) * 0.6F, p[1] + (r.nextFloat() - 0.5F) * 0.6F,
                            p[2] + (r.nextFloat() - 0.5F) * 0.6F}, r, k, 0.9F);
                }
            }
        }
    }

    // ------------------------------------------------------------------ gants électriques

    private void hand(PoseStack pose, SubmitNodeCollector col, ModelPart arm, boolean left, float cl, float su, float age, int seed, int light) {
        pose.pushPose();
        arm.translateAndRotate(pose);
        pose.translate((left ? 1.0F : -1.0F) / 16.0F, 10.0F / 16.0F, 0.0F);
        drawGlove(pose, col, left, cl, su, age, seed);
        pose.popPose();
    }

    /**
     * Un GANT ÉLECTRIQUE (repère : bout de la main, doigts vers +Y). Toute sa structure est de l'électricité : le modèle (coque,
     * doigts, griffes) est dessiné en trois couches lumineuses translucides (coeur blanc-bleu, gaine bleu électrique, halo bleu
     * intense) avec une texture de veines qui crépite ; des arcs parcourent les doigts, des décharges jaillissent des griffes.
     * À l'activation, il se forme : des arcs convergent vers la main pendant que la structure s'allume.
     */
    void drawGlove(PoseStack pose, SubmitNodeCollector col, boolean left, float cl, float su, float age, int seed) {
        ModelPart glove = left ? this.gloveLeft : this.gloveRight;
        net.minecraft.resources.Identifier tex = MinksModels.GLOVE_TEXTURES[Math.floorMod((int) (age * 0.5F) + seed, 2)];
        float form = MinksClient.snap(cl, 0.0F, 1.0F);
        float s = (0.75F + 0.25F * form) * (1.0F + 0.1F * su);
        float flicker = 0.88F + 0.12F * Mth.sin(age * 6.1F + seed);
        pose.pushPose();
        pose.scale(s, s, s);
        col.order(1).submitModelPart(glove, pose, RenderTypes.entityTranslucentEmissive(tex), GoroLightning.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, null, a(0.95F * cl * flicker, CORE));
        pose.scale(1.07F, 1.04F, 1.07F);
        col.order(2).submitModelPart(glove, pose, RenderTypes.entityTranslucentEmissive(tex), GoroLightning.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, null, a((0.5F + 0.2F * su) * cl, SHEATH));
        pose.scale(1.1F, 1.06F, 1.1F);
        col.order(3).submitModelPart(glove, pose, RenderTypes.entityTranslucentEmissive(tex), GoroLightning.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, null, a((0.2F + 0.15F * su) * cl, HAZE));
        pose.popPose();
        float p = left ? -1.0F : 1.0F;
        Random r = new Random(seed * 31L + (long) (age * 1.0F));
        // arcs qui parcourent les doigts (de la jointure au bout de la griffe)
        for (int i = 0; i < 2 + (int) (2 * su); i++) {
            int f = r.nextInt(4);
            float z = MinksModels.FINGER_Z[f] / 16.0F * s;
            float[] knuckle = {0.35F * p / 16.0F * s, 0.0F, z};
            float[] tip = {(0.35F + 2.4F) * p / 16.0F * s, (MinksModels.FINGER_LEN[f] + 3.6F) / 16.0F * s, z};
            arc(pose, col, knuckle, tip, r, cl * 0.9F, 0.5F);
        }
        // décharges aux pointes des griffes
        for (int f = 0; f < 4; f++) {
            if (r.nextFloat() > 0.35F + 0.3F * su) {
                continue;
            }
            float z = MinksModels.FINGER_Z[f] / 16.0F * s;
            float[] tip = {(0.35F + 2.6F) * p / 16.0F * s, (MinksModels.FINGER_LEN[f] + 3.9F) / 16.0F * s, z};
            float[] out = {tip[0] + (r.nextFloat() - 0.2F) * 0.12F * p, tip[1] + r.nextFloat() * 0.12F, tip[2] + (r.nextFloat() - 0.5F) * 0.12F};
            arc(pose, col, tip, out, r, cl, 0.45F);
        }
        // formation : l'électricité converge vers la main
        if (cl < 1.0F) {
            for (int i = 0; i < 5; i++) {
                float[] d = GoroLightning.normalize(new float[] {r.nextFloat() - 0.5F, r.nextFloat() - 0.5F, r.nextFloat() - 0.5F});
                float rad = 0.15F + 0.5F * (1.0F - cl);
                arc(pose, col, new float[] {d[0] * rad, d[1] * rad + 0.1F, d[2] * rad}, new float[] {0.0F, 0.08F, 0.0F}, r, 1.0F - cl * 0.5F, 0.6F);
            }
        }
    }

    /**
     * Danse des griffes éclair : à chaque frappe, trois traces de griffes bleues (rubans courbes et fins) balaient l'air devant
     * le joueur DANS LE SENS DU COUP du mouvement en cours : horizontales (devant, rafale : deux), diagonales (côté), en revers
     * (derrière), droites devant (dash), plongeantes (au-dessus), croisées en X (aérien en diagonale). Brèves : elles s'éteignent
     * avant le mouvement suivant.
     */
    private void danceSlash(PoseStack pose, SubmitNodeCollector col, Entity entity, float age, float su) {
        float[] mv = MinksClient.move(entity, age);
        if (mv == null) {
            return;
        }
        int type = (int) mv[0];
        float s = mv[1];
        int[] at = nolan.bloxpiece.ability.MinksDanceAbility.strikeTicks(type);
        boolean cross = type == nolan.bloxpiece.ability.MinksDanceAbility.AIR_DIAG || type == nolan.bloxpiece.ability.MinksDanceAbility.RISE;
        for (int n = 0; n < (cross ? 2 : at.length); n++) {
            float when = at[Math.min(n, at.length - 1)] + (cross ? n * 0.4F : 0.0F);
            float k = MinksClient.snap(s, when - 1.0F, when + 0.4F);
            float fade = 1.0F - MinksClient.ramp(s, when + 0.4F, when + 1.6F); // brève : avant l'apparition suivante
            if (k <= 0.0F || fade <= 0.0F) {
                continue;
            }
            // orientation : 0 = horizontal, 1 = plongeant (vertical), 2 = droit devant ; inclinaison et sens
            int mode = 0;
            float roll = 0.0F, dir = 1.0F;
            switch (type) {
                case nolan.bloxpiece.ability.MinksDanceAbility.SIDE, nolan.bloxpiece.ability.MinksDanceAbility.FAR -> roll = 0.75F;
                case nolan.bloxpiece.ability.MinksDanceAbility.BEHIND -> dir = -1.0F;
                case nolan.bloxpiece.ability.MinksDanceAbility.DASH -> mode = 2;
                case nolan.bloxpiece.ability.MinksDanceAbility.ABOVE -> mode = 1;
                case nolan.bloxpiece.ability.MinksDanceAbility.AIR_DIAG, nolan.bloxpiece.ability.MinksDanceAbility.RISE -> roll = n == 0 ? 0.8F : -0.8F;
                case nolan.bloxpiece.ability.MinksDanceAbility.MULTI -> dir = n == 0 ? 1.0F : -1.0F;
                default -> {
                }
            }
            for (int line = 0; line < 3; line++) {
                float off = (line - 1) * 0.11F;
                float[] prev = null;
                for (int i = 0; i <= 10; i++) {
                    float t = i / 10.0F * k;
                    double ang = (-1.1 + 2.2 * t) * dir;
                    float r = 0.9F + off;
                    float[] p;
                    if (mode == 1) {        // plongeant : de haut en bas devant le joueur
                        p = new float[] {off, 0.2F + (float) Math.sin(ang) * r, -0.5F - (float) Math.cos(ang) * r * 0.6F};
                    } else if (mode == 2) { // droit devant : traces qui filent vers l'avant
                        p = new float[] {off * 1.5F, 0.55F + off * 0.5F, -0.3F - 1.6F * t};
                    } else {                // horizontal, incliné de « roll »
                        float x = (float) Math.sin(ang) * r;
                        p = new float[] {x, 0.55F + off * 0.3F + x * (float) Math.tan(roll), -(float) Math.cos(ang) * r};
                    }
                    if (prev != null) {
                        float wd = 1.0F - Math.abs(t - k * 0.5F) * 1.2F;
                        GoroLightning.segment(this.segment, pose, col, 1, glow(), prev, p, 0.6F * wd + 0.15F, a(0.9F * fade, CORE));
                        GoroLightning.segment(this.segment, pose, col, 2, glow(), prev, p, 1.6F * wd + 0.3F, a((0.35F + 0.15F * su) * fade, SHEATH));
                    }
                    prev = p;
                }
            }
        }
    }

    /** Barrage : lignes de vitesse qui filent du poing vers l'épaule, air comprimé devant les jointures (repère de la main). */
    private void punchStreaks(PoseStack pose, SubmitNodeCollector col, float k, float age, int seed) {
        Random r = new Random(seed * 13L + (long) (age * 2.0F));
        for (int i = 0; i < 7; i++) {
            double ang = i * Math.PI * 2 / 7 + r.nextDouble() * 0.4;
            float rad = 0.16F + r.nextFloat() * 0.12F, len = (0.35F + r.nextFloat() * 0.45F) * k;
            float[] p = {(float) Math.cos(ang) * rad, 0.05F, (float) Math.sin(ang) * rad};
            GoroLightning.segment(this.segment, pose, col, 2, glow(), p, new float[] {p[0], p[1] - len, p[2]}, 0.5F, a(0.6F * k, i % 2 == 0 ? CORE : SHEATH));
        }
        for (int i = 0; i < 3; i++) {
            float y = (0.2F + 0.05F * i), rr = (0.22F - 0.05F * i) * (0.8F + 0.4F * k);
            ring(pose, col, new float[] {0, y, 0}, new float[] {1, 0, 0}, new float[] {0, 0, 1}, rr, 1.0F - 0.2F * i, a(0.5F * k, i == 0 ? HAZE : CORE), 14);
        }
    }

    // ------------------------------------------------------------------ électricité du corps, Sulong

    /** Point d'un membre (px dans son repère) exprimé dans le repère du modèle (blocs). */
    private static float[] onPart(ModelPart part, float x, float y, float z) {
        Vector3f v = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(new Vector3f(x, y, z))
                .add(part.x, part.y, part.z).mul(1.0F / 16.0F);
        return new float[] {v.x, v.y, v.z};
    }

    /** Arcs qui crépitent sur la silhouette (épaules, mains, tête, hanches, pieds), plus nombreux avec l'intensité. */
    private void bodyArcs(PoseStack pose, SubmitNodeCollector col, PlayerModel model, float k, float age, int seed, boolean charging) {
        float[][] pts = {onPart(model.head, 0, -8.5F, 0), onPart(model.body, 4.2F, 0.5F, 0), onPart(model.body, -4.2F, 0.5F, 0),
                onPart(model.rightArm, -1, 10.5F, 0), onPart(model.leftArm, 1, 10.5F, 0), onPart(model.body, 0, 11.5F, 2.3F),
                onPart(model.rightLeg, 0, 12, 0), onPart(model.leftLeg, 0, 12, 0), onPart(model.body, 0, 5, -2.3F)};
        Random r = new Random(seed * 17L + (long) (age * 1.0F));
        int n = 2 + (int) (6 * k);
        for (int i = 0; i < n; i++) {
            float[] p = pts[r.nextInt(pts.length)];
            float[] q = {p[0] + (r.nextFloat() - 0.5F) * 0.7F, p[1] - r.nextFloat() * 0.5F, p[2] + (r.nextFloat() - 0.5F) * 0.7F};
            arc(pose, col, p, q, r, k, 1.0F);
        }
        // pendant la transformation : l'électricité converge vers le joueur depuis l'extérieur
        if (charging) {
            for (int i = 0; i < 4 + (int) (6 * k); i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                float rad = 2.4F - 1.6F * k * r.nextFloat();
                float[] far = {(float) Math.cos(ang) * rad, 1.5F - r.nextFloat() * 2.5F, (float) Math.sin(ang) * rad};
                arc(pose, col, far, pts[r.nextInt(pts.length)], r, k * 0.8F, 0.8F);
            }
        }
    }

    /**
     * Sulong : petits ÉCLAIRS RAMIFIÉS autour du corps (la foudre du Goro en miniature, système commun) : ils partent des
     * épaules, des mains, de la tête, des hanches et des pieds vers l'extérieur, avec de courtes fourches. Chaque éclair tient
     * deux ticks (forme lisible) en crépitant, puis un autre apparaît ailleurs ; assez fins pour garder le personnage visible.
     */
    private void sulongSparks(PoseStack pose, SubmitNodeCollector col, PlayerModel model, float k, float age, int seed) {
        float[][] pts = {onPart(model.head, 0, -8.5F, 0), onPart(model.body, 4.2F, 0.5F, 0), onPart(model.body, -4.2F, 0.5F, 0),
                onPart(model.rightArm, -1, 10.5F, 0), onPart(model.leftArm, 1, 10.5F, 0), onPart(model.body, 0, 11.5F, 2.3F),
                onPart(model.rightLeg, 0, 12, 0), onPart(model.leftLeg, 0, 12, 0), onPart(model.body, 0, 5, 2.4F)};
        long slot = (long) (age * 0.5F);
        Random r = new Random(seed * 17L + slot);
        int n = 2 + (int) (3 * k);
        for (int i = 0; i < n; i++) {
            float[] p = pts[r.nextInt(pts.length)];
            // vers l'extérieur de la silhouette (repère du modèle : y vers le bas), un peu au hasard
            float hx = p[0] + (r.nextFloat() - 0.5F) * 0.3F, hz = p[2] + (r.nextFloat() - 0.5F) * 0.3F;
            float hl = Math.max(0.05F, (float) Math.sqrt(hx * hx + hz * hz));
            float len = 0.45F + r.nextFloat() * 0.45F;
            float[] q = {p[0] + hx / hl * len * 0.8F, p[1] + (r.nextFloat() - 0.6F) * len, p[2] + hz / hl * len * 0.8F};
            this.electric.bolt(pose, col, Electric.Palette.MINKS, Electric.Style.SPARK, p, q, 0.42F, age, (int) (seed * 31 + slot * 7 + i), k);
        }
    }

    /** Deux anneaux d'éclairs brisés qui tournent autour du corps (Sulong). */
    private void lightningRings(PoseStack pose, SubmitNodeCollector col, float su, float age, int seed) {
        for (int ring = 0; ring < 2; ring++) {
            float y = ring == 0 ? 1.1F : 0.2F;
            float rad = 0.75F + 0.1F * ring;
            Random r = new Random(seed * 7L + ring * 101L + (long) (age * 0.5F));
            float spin = age * (ring == 0 ? 0.2F : -0.25F);
            List<float[]> pts = new ArrayList<>();
            int n = 14;
            for (int i = 0; i <= n; i++) {
                if (r.nextFloat() < 0.18F) { // l'anneau se brise par endroits
                    pts.add(null);
                    continue;
                }
                double ang = spin + 2 * Math.PI * i / n;
                float jit = (r.nextFloat() - 0.5F) * 0.12F;
                pts.add(new float[] {(float) Math.cos(ang) * (rad + jit), y + (r.nextFloat() - 0.5F) * 0.12F, (float) Math.sin(ang) * (rad + jit)});
            }
            this.electric.broken(pose, col, Electric.Palette.MINKS, Electric.Style.TRACE, pts, 0.6F, age, seed + ring * 7, 0.85F * su);
        }
    }

    /** Petit arc électrique (système commun : canal crépitant du Goro, palette des Minks), re-tiré selon {@code r}. */
    void arc(PoseStack pose, SubmitNodeCollector col, float[] from, float[] to, Random r, float k, float width) {
        this.electric.bolt(pose, col, Electric.Palette.MINKS, Electric.Style.ARC, from, to, 0.45F * width, 0.0F, r.nextInt(), k);
    }

    private void ring(PoseStack pose, SubmitNodeCollector col, float[] c, float[] u, float[] w, float r, float width, int color, int n) {
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            GoroLightning.segment(this.segment, pose, col, 2, glow(),
                    new float[] {c[0] + (u[0] * (float) Math.cos(a0) + w[0] * (float) Math.sin(a0)) * r, c[1] + (u[1] * (float) Math.cos(a0) + w[1] * (float) Math.sin(a0)) * r,
                            c[2] + (u[2] * (float) Math.cos(a0) + w[2] * (float) Math.sin(a0)) * r},
                    new float[] {c[0] + (u[0] * (float) Math.cos(a1) + w[0] * (float) Math.sin(a1)) * r, c[1] + (u[1] * (float) Math.cos(a1) + w[1] * (float) Math.sin(a1)) * r,
                            c[2] + (u[2] * (float) Math.cos(a1) + w[2] * (float) Math.sin(a1)) * r}, width, color);
        }
    }

    // ------------------------------------------------------------------ Sulong : transformation et aura

    /**
     * Cinématique du Sulong, côté effets : 1. de petits éclairs apparaissent autour du joueur, de plus en plus nombreux, le sol
     * grésille ; 2. les éclairs PARCOURENT ses membres (bras, jambes, dos), l'électricité converge vers lui et une aura se forme
     * peu à peu ; à l'explosion (MinksFx.SULONG), tout cela s'efface pour laisser place à l'aura permanente.
     */
    private void sulongCharge(PoseStack pose, SubmitNodeCollector col, PlayerModel model, float e, float age, int seed) {
        float c = MinksSulongAbility.CHARGE;
        float fade = 1.0F - MinksClient.ramp(e, c, c + 8.0F);
        if (fade <= 0.0F) {
            return;
        }
        float p1 = MinksClient.ramp(e, 0.0F, 10.0F), p2 = MinksClient.ramp(e, 10.0F, c);
        // phase 1 : petits éclairs autour, sol qui grésille
        bodyArcs(pose, col, model, (0.25F + 0.5F * p2) * p1 * fade, age, seed, p2 > 0.0F);
        float flick = 0.6F + 0.4F * Mth.sin(age * 3.1F);
        ring(pose, col, new float[] {0, 1.48F, 0}, new float[] {1, 0, 0}, new float[] {0, 0, 1}, 0.9F - 0.3F * p2, 1.2F, a(0.5F * p1 * flick * fade, SHEATH), 28);
        // phase 2 : éclairs qui parcourent les membres, aura qui se forme
        if (p2 > 0.0F) {
            limbSurges(pose, col, model, p2 * fade, age, seed);
            auraTongues(pose, col, p2 * 0.8F * fade, age, seed, 0.9F + 0.3F * p2);
        }
    }

    /**
     * Aura permanente du Sulong : langues électriques en deux couches autour de la silhouette, éclairs qui parcourent bras,
     * jambes et dos, pulsation régulière (onde et anneau qui s'embrase), éclairs qui se détachent de l'aura et s'éteignent,
     * anneaux d'éclairs qui tournent. L'intensité varie sans cesse ; tout reste assez translucide pour garder le
     * personnage lisible.
     */
    private void sulongAura(PoseStack pose, SubmitNodeCollector col, PlayerModel model, float su, float age, int seed) {
        float vary = 0.8F + 0.2F * Mth.sin(age * 0.31F) * Mth.sin(age * 0.11F + 2.0F);
        float k = su * vary;
        float pulse = (age % 20.0F) / 8.0F; // pulsation toutes les secondes
        float beat = pulse < 1.0F ? 1.0F - pulse : 0.0F;
        sulongSparks(pose, col, model, (0.6F + 0.3F * beat) * k, age, seed);
        limbSurges(pose, col, model, (0.6F + 0.3F * beat) * k, age, seed);
        auraTongues(pose, col, (0.7F + 0.3F * beat) * k, age, seed, 1.0F + 0.15F * beat);
        detachBolts(pose, col, k, age, seed);
        lightningRings(pose, col, su, age, seed);
        if (pulse < 1.0F) { // l'onde de la pulsation part de la poitrine
            float[] chest = onPart(model.body, 0.0F, 5.0F, 0.0F);
            ring(pose, col, chest, new float[] {1, 0, 0}, new float[] {0, 0, 1}, 0.35F + 1.1F * MinksClient.snap(pulse, 0.0F, 1.0F),
                    1.5F * (1.0F - pulse) + 0.2F, a(0.6F * (1.0F - pulse) * su, CORE), 28);
            ring(pose, col, new float[] {0, 1.47F, 0}, new float[] {1, 0, 0}, new float[] {0, 0, 1}, 0.5F + 1.4F * MinksClient.snap(pulse, 0.0F, 1.0F),
                    1.2F * (1.0F - pulse) + 0.2F, a(0.45F * (1.0F - pulse) * su, HAZE), 28);
        }
    }

    /** Éclairs qui parcourent les deux bras, les deux jambes et le dos (impulsions en spirale à la surface des membres). */
    private void limbSurges(PoseStack pose, SubmitNodeCollector col, PlayerModel model, float k, float age, int seed) {
        limbSurge(pose, col, model.rightArm, -1.0F, 0.0F, -1.5F, 9.5F, 2.35F, k, age, seed);
        limbSurge(pose, col, model.leftArm, 1.0F, 0.0F, -1.5F, 9.5F, 2.35F, k, age, seed + 3);
        limbSurge(pose, col, model.rightLeg, 0.0F, 0.0F, 0.5F, 11.5F, 2.3F, k, age, seed + 5);
        limbSurge(pose, col, model.leftLeg, 0.0F, 0.0F, 0.5F, 11.5F, 2.3F, k, age, seed + 7);
        limbSurge(pose, col, model.body, 0.0F, 2.4F, 0.5F, 11.5F, 1.6F, k, age, seed + 9); // le long du dos
    }

    /**
     * Impulsions électriques le long d'un membre (repère du membre, px) : de {@code y0} à {@code y1}, en spirale de rayon
     * {@code r} autour de l'axe ({@code cx}, {@code cz}) ; elles descendent vers le bout du membre.
     */
    void limbSurge(PoseStack pose, SubmitNodeCollector col, ModelPart part, float cx, float cz, float y0, float y1, float r, float k, float age,
                   int seed) {
        if (k <= 0.01F) {
            return;
        }
        pose.pushPose();
        part.translateAndRotate(pose);
        Random jr = new Random(seed * 41L + (long) (age * 1.5F));
        for (int i = 0; i < 2; i++) {
            float head = (age * 0.11F + i * 0.5F + seed * 0.13F) % 1.0F;
            // l'impulsion : une queue pâle puis une tête brillante (deux fils électriques qui se suivent)
            List<float[]> tail = new ArrayList<>(), front = new ArrayList<>();
            for (int s = 0; s <= 6; s++) {
                float t = head - 0.4F + 0.4F * s / 6.0F;
                if (t < 0.0F) {
                    continue;
                }
                double ang = t * 7.0 + i * Math.PI + seed;
                float y = y0 + (y1 - y0) * t;
                float[] p = {(cx + (float) Math.cos(ang) * r) / 16.0F + (jr.nextFloat() - 0.5F) * 0.02F, y / 16.0F,
                        (cz + (float) Math.sin(ang) * r) / 16.0F + (jr.nextFloat() - 0.5F) * 0.02F};
                if (s <= 3) {
                    tail.add(p);
                }
                if (s >= 3) {
                    front.add(p);
                }
            }
            this.electric.trace(pose, col, Electric.Palette.MINKS, Electric.Style.TRACE, tail, 0.5F, age, seed + i * 5, 0.4F * k);
            this.electric.trace(pose, col, Electric.Palette.MINKS, Electric.Style.TRACE, front, 0.7F, age, seed + i * 5 + 1, 0.9F * k);
        }
        pose.popPose();
    }

    /** Aura en deux couches : petits éclairs ramifiés qui montent autour de la silhouette (re-tirés tous les 2 ticks). */
    private void auraTongues(PoseStack pose, SubmitNodeCollector col, float k, float age, int seed, float size) {
        if (k <= 0.01F) {
            return;
        }
        // aura : petits ÉCLAIRS RAMIFIÉS (foudre du Goro en miniature, système commun) répartis autour de la silhouette ; ils
        // montent en s'écartant un peu du corps, chacun tient deux ticks en crépitant puis réapparaît ailleurs
        long slot = (long) (age * 0.5F);
        Random r = new Random(seed * 59L + slot);
        for (int layer = 0; layer < 2; layer++) {
            int n = layer == 0 ? 5 : 3;
            float rad0 = (layer == 0 ? 0.4F : 0.55F) * size;
            for (int i = 0; i < n; i++) {
                double ang = i * Math.PI * 2 / n + r.nextDouble() * 0.9 + layer * 0.6;
                float rad = rad0 + r.nextFloat() * 0.08F, y = 0.1F + r.nextFloat() * 1.3F, h = (0.4F + r.nextFloat() * 0.4F) * size;
                float[] p = {(float) Math.cos(ang) * rad, y, (float) Math.sin(ang) * rad};
                double lean = ang + (r.nextDouble() - 0.5) * 1.2; // un peu de travers : jamais un trait vertical
                float out = h * (0.35F + r.nextFloat() * 0.35F);
                float[] q = {p[0] + (float) Math.cos(lean) * out, y - h, p[2] + (float) Math.sin(lean) * out};
                this.electric.bolt(pose, col, Electric.Palette.MINKS, Electric.Style.SPARK, p, q, layer == 0 ? 0.4F : 0.34F, age,
                        (int) (seed * 59 + slot * 11 + layer * 50 + i), (layer == 0 ? 0.85F : 0.55F) * k);
            }
        }
    }

    /** Petits éclairs qui se détachent de l'aura, s'éloignent et s'éteignent (cycles décalés). */
    private void detachBolts(PoseStack pose, SubmitNodeCollector col, float k, float age, int seed) {
        for (int i = 0; i < 4; i++) {
            float slot = (age + i * 2.9F) / 10.0F;
            long n = (long) Math.floor(slot);
            float t = slot - n;
            Random sr = new Random(seed * 97L + n * 13L + i);
            double ang = sr.nextDouble() * Math.PI * 2;
            float y = 0.05F + sr.nextFloat() * 1.4F;
            float[] base = {(float) Math.cos(ang) * 0.45F, y, (float) Math.sin(ang) * 0.45F};
            float[] out = {(float) Math.cos(ang) * (0.45F + 1.0F * t), y - 0.35F * t, (float) Math.sin(ang) * (0.45F + 1.0F * t)};
            float[] from = {base[0] + (out[0] - base[0]) * Math.max(0.0F, t - 0.3F), base[1] + (out[1] - base[1]) * Math.max(0.0F, t - 0.3F),
                    base[2] + (out[2] - base[2]) * Math.max(0.0F, t - 0.3F)};
            float al = 0.9F * (1.0F - t) * k;
            this.electric.bolt(pose, col, Electric.Palette.MINKS, Electric.Style.SPARK, from, out, 0.45F, age, seed * 97 + (int) n * 13 + i, al);
        }
    }

    // ------------------------------------------------------------------ première personne

    /** Première personne : griffes sur la main vue (AvatarRendererHandMixin). */
    public static void submitHand(PoseStack pose, SubmitNodeCollector col, ModelPart arm, PlayerModel model) {
        Minecraft mc = Minecraft.getInstance();
        if (shared == null || mc.player == null || !MinksClient.isMinks(mc.player)) {
            return;
        }
        float age = mc.player.tickCount + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float cl = MinksClient.claws(mc.player, age);
        boolean left = arm == model.leftArm;
        // Sulong (transformation puis aura) : éclairs le long du bras vu
        float e = MinksClient.elapsed(mc.player, age);
        float charge = MinksClient.kind(mc.player) == Minks.SULONG && e >= 0.0F
                ? MinksClient.ramp(e, 10.0F, MinksSulongAbility.CHARGE) * (1.0F - MinksClient.ramp(e, MinksSulongAbility.CHARGE, MinksSulongAbility.CHARGE + 8.0F)) : 0.0F;
        float surge = Math.max(0.6F * MinksClient.sulong(mc.player, age), charge);
        if (surge > 0.0F) {
            shared.limbSurge(pose, col, arm, left ? 1.0F : -1.0F, 0.0F, -1.5F, 9.5F, 2.35F, surge, age, mc.player.getId() + (left ? 3 : 0));
        }
        if (cl <= 0.0F) {
            return;
        }
        pose.pushPose();
        arm.translateAndRotate(pose);
        pose.translate((left ? 1.0F : -1.0F) / 16.0F, 10.0F / 16.0F, 0.0F);
        shared.drawGlove(pose, col, left, cl, MinksClient.sulong(mc.player, age), age, mc.player.getId() + (left ? 7 : 0));
        pose.popPose();
    }
}
