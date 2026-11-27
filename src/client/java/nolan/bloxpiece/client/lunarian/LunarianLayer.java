package nolan.bloxpiece.client.lunarian;

import java.util.Random;

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

import nolan.bloxpiece.ability.Lunarian;
import nolan.bloxpiece.ability.LunarianFeathersAbility;
import nolan.bloxpiece.client.goro.GoroLightning;

import static nolan.bloxpiece.client.lunarian.LunarianFire.ORANGE;
import static nolan.bloxpiece.client.lunarian.LunarianFire.RED;
import static nolan.bloxpiece.client.lunarian.LunarianFire.WHITE_HOT;
import static nolan.bloxpiece.client.lunarian.LunarianFire.YELLOW;
import static nolan.bloxpiece.client.lunarian.LunarianFire.argb;

/**
 * Race Lunarian, sur le joueur (troisième personne ; la main vue à la première personne via {@link #submitHand}) :
 * <ul>
 *   <li>LES AILES (références : ailes de King) : deux GRANDES ailes noires assemblées à chaque image, pièce par pièce
 *       (LunarianModels.WING) : l'os du bras de l'aile part de l'omoplate, l'os de la main au bout ; une trentaine de plumes
 *       individuelles par aile, en rangées qui se chevauchent (petites couvertures contre l'os, couvertures, rémiges
 *       secondaires, grandes rémiges primaires en éventail). Au repos, ailes dressées derrière le dos, plumes qui pendent
 *       jusqu'aux genoux ; ouvertes, elles s'écartent largement et les primaires s'ouvrent en éventail. Tout est construit
 *       DERRIÈRE le dos (rotations limitées vers l'arrière, profondeur positive) : aucune pièce ne peut passer sur le torse.
 *       Animation (LunarianClient.anim) : respiration, balancement à la marche, battements (propagés de l'épaule au bout de
 *       l'aile, les plumes suivent avec retard), déploiement et repli fluides ;</li>
 *   <li>LES FLAMMES DU DOS (mode Défense) : de vraies flammes pixel animées (plans croisés, contours de feu), en couches,
 *       nées derrière le dos entre les ailes et montant au-dessus de la tête (devant elles rien ne les cache) ; elles
 *       grandissent ou s'éteignent avec la transition (gerbe de braises, fumée) ;</li>
 *   <li>le FEU DU POING droit (Poing embrasé, Tempête), les PLUMES INCANDESCENTES en formation.</li>
 * </ul>
 */
public class LunarianLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static LunarianLayer shared;
    /** Profondeur de l'attache des ailes derrière le centre du buste (px ; la surface du dos est à 2 px). */
    private static final float ROOT_Z = 2.4F;
    /** Attache des ailes : de part et d'autre de la colonne, sur les omoplates (px) ; le centre du dos reste dégagé pour le feu. */
    private static final float ROOT_X = 4.4F;
    /** Échelle des ailes en 3e personne : imposantes mais proportionnées au joueur (environ 2 blocs d'envergure par aile ouverte). */
    static final float WING_SCALE = 0.72F;
    private final ModelPart[] bones = new ModelPart[3];
    private final ModelPart[] tiers = new ModelPart[LunarianModels.TIERS.length];
    final LunarianFire fire;

    public LunarianLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
        super(parent);
        ModelPart wing = context.bakeLayer(LunarianModels.WING);
        for (int i = 0; i < this.bones.length; i++) {
            this.bones[i] = wing.getChild("bone" + i);
        }
        for (int i = 0; i < this.tiers.length; i++) {
            this.tiers[i] = wing.getChild(LunarianModels.TIERS[i]);
        }
        this.fire = new LunarianFire(context);
        shared = this;
    }

    static LunarianLayer shared() {
        return shared;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector col, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(state.id);
        if (entity == null || !LunarianClient.isLunarian(entity)) {
            return;
        }
        PlayerModel model = this.getParentModel();
        float age = state.ageInTicks;
        this.fire.time = age;
        float walk = Math.min(1.0F, state.walkAnimationSpeed);
        LunarianClient.Anim anim = LunarianClient.anim(entity, age, walk);
        float[] p = params(anim, age, state.walkAnimationPos, walk);
        pose.pushPose();
        model.body.translateAndRotate(pose);
        // 1. les ailes
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * ROOT_X / 16.0F, 2.5F / 16.0F, ROOT_Z / 16.0F); // attache aux omoplates, juste derrière le dos
            if (side < 0) {
                pose.scale(-1.0F, 1.0F, 1.0F); // aile droite : miroir exact de la gauche
            }
            pose.scale(WING_SCALE, WING_SCALE, WING_SCALE); // proportions équilibrées avec le joueur (taille réelle en 3e personne)
            wing(pose, col, p, light, false);
            pose.popPose();
        }
        // 2. les flammes du dos, derrière les ailes et au-dessus de la tête
        backFlames(pose, col, LunarianClient.flames(entity, age), LunarianClient.flameChange(entity, age), age, state.id, light);
        // 3. plumes en formation
        int kind = LunarianClient.kind(entity);
        float e = LunarianClient.elapsed(entity, age);
        if (kind == Lunarian.FEATHERS && e >= 0.0F) {
            forming(pose, col, e, age);
        }
        pose.popPose();
        // 4. feu du poing droit
        float fist = LunarianClient.fist(entity, age);
        if (fist > 0.01F) {
            pose.pushPose();
            model.rightArm.translateAndRotate(pose);
            fistFire(pose, col, fist, kind == Lunarian.FIST && e >= nolan.bloxpiece.ability.LunarianFistAbility.WINDUP
                    || kind == Lunarian.JUDGMENT && LunarianClient.judgmentPhase(entity, e) == 3, age, state.id);
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ ailes

    /**
     * Paramètres de l'aile à cette image : {lacet (degrés, toujours vers l'arrière), angle du bord d'attaque à l'attache, rotation
     * totale du bord jusqu'au bout, plumes pendantes (0) ou perpendiculaires au bord (1), éventail des plumes extérieures,
     * début et largeur de l'arrondi de l'arche (fractions du bord), facteur de longueur des plumes, retard des plumes au battement}.
     * Poses clés reprises des références : REPLIÉES (deux faisceaux dressés, sommet arrondi), DÉPLOYÉES (arche côté intérieur,
     * aile large, étages de plumes vers le bas et l'extérieur), BALAYÉES (dash, plongée).
     */
    static float[] params(LunarianClient.Anim a, float age, float walkPos, float walk) {
        //                  lacet    bord    courbe  pendant éventail arrondi largeur longueur
        float[] folded = {-15.0F, -79.0F, 150.0F, 0.0F, 0.0F, 0.38F, 0.3F, 0.8F};
        float[] open = {-8.0F, -60.0F, 88.0F, 0.45F, 26.0F, 0.12F, 0.42F, 0.78F};
        float[] swept = {-45.0F, -30.0F, 40.0F, 0.45F, 10.0F, 0.2F, 0.4F, 0.8F}; // (lacet limité : les étages ne reviennent jamais vers la flamme)
        float o = Mth.clamp(a.open, 0.0F, 1.0F), s = Mth.clamp(a.sweep, 0.0F, 1.0F);
        float[] r = new float[9];
        for (int i = 0; i < 8; i++) {
            r[i] = Mth.lerp(s, Mth.lerp(o, folded[i], open[i]), swept[i]);
        }
        r[1] += a.amp * Mth.sin(a.phase) + 2.5F * Mth.sin(age * 0.09F);                      // battement + respiration
        r[2] += a.amp * 0.6F * Mth.sin(a.phase - 0.8F) + 2.0F * Mth.sin(age * 0.09F - 0.8F);
        r[8] = a.amp * 0.35F * Mth.sin(a.phase - 1.4F);                                       // les plumes suivent avec retard
        r[0] = Math.min(-5.0F, r[0] + 5.0F * Mth.sin(walkPos * 0.6662F) * walk * (1.0F - o)); // balancement à la marche
        return r;
    }

    /** Longueur du bord d'attaque (px) et nombre de maillons de sa courbe. */
    private static final float EDGE = 40.0F;
    private static final int CURVE = 16;

    /**
     * Étages de plumes (du fond vers l'avant) : {pièce (0 A ... 4 E), début le long du bord, nombre, distance sous le bord (px),
     * profondeur (px), cambrure (degrés vers l'arrière), part de l'éventail}. Les étages se superposent comme sur les références :
     * grandes rémiges au fond et en bas, couvertures de plus en plus courtes vers le bord.
     */
    private static final float[][] TIERS = {
            // (profondeur RELATIVE à l'os : les petites couvertures se posent sur lui, chaque étage touche le suivant)
            {4, 0.45F, 8, 8.5F, -2.8F, 3.0F, 1.0F},
            {3, 0.28F, 10, 7.0F, -2.0F, 7.0F, 0.8F},
            {2, 0.24F, 12, 5.0F, -1.0F, 12.0F, 0.6F},
            {1, 0.08F, 13, 2.5F, -0.4F, 17.0F, 0.4F},
            {0, 0.04F, 15, 0.6F, 0.4F, 22.0F, 0.2F},
    };
    /**
     * Une aile (gauche ; la droite est dessinée en miroir) dans le repère de l'attache (omoplate) : x vers l'extérieur, y vers le
     * bas, z vers l'arrière. La BASE est au ras du dos et l'aile est DROITE : l'os ne recule que sur ses premiers pixels (au-dessus, profondeur
     * constante : voir {@link #DEPTH_ROOT}) ; trois petites couvertures recouvrent la naissance
     * de l'aile sur l'omoplate. Le bord d'attaque est une chaîne de 16 maillons d'os orientés en 3D (épais, moyens puis fins),
     * cinq étages de PLUMES VOXEL pendent sous lui, décalés en profondeur et cambrés. Aucune pièce n'est étirée : chaque pièce
     * est dessinée à sa taille réelle, les textures restent nettes et alignées (l'échelle commune {@link #WING_SCALE} ne
     * déforme rien). Le centre du dos reste libre pour la flamme. {@code handOnly} : inutilisé.
     */
    void wing(PoseStack pose, SubmitNodeCollector col, float[] p, int light, boolean handOnly) {
        RenderType type = RenderTypes.entityCutout(LunarianModels.WING_TEXTURE);
        pose.pushPose();
        pose.rotateDegrees(Axis.YP, p[0]);
        float[][] c = curve(p);
        // 1. naissance de l'aile : petites couvertures posées sur l'omoplate, contre le dos
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.translate((-0.8F + 1.6F * i) / 16.0F, (-0.5F + 0.3F * i) / 16.0F, (0.4F + 0.2F * i) / 16.0F);
            pose.rotateDegrees(Axis.ZP, -10.0F + 10.0F * i);
            pose.rotateDegrees(Axis.XP, 12.0F);
            col.submitModelPart(this.tiers[i == 1 ? 1 : 0], pose, type, light, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
            pose.popPose();
        }
        // 2. l'arche du bord d'attaque : maillons orientés en 3D de point en point (elle monte et penche vers l'arrière)
        for (int k = 1; k <= CURVE; k++) {
            float s = (k - 0.5F) / CURVE;
            float dx = c[k][0] - c[k - 1][0], dy = c[k][1] - c[k - 1][1], dz = c[k][3] - c[k - 1][3];
            float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            pose.pushPose();
            pose.translate(c[k - 1][0] / 16.0F, c[k - 1][1] / 16.0F, c[k - 1][3] / 16.0F);
            pose.rotateDegrees(Axis.ZP, (float) Math.toDegrees(Math.atan2(dy, dx)));
            pose.rotateDegrees(Axis.YP, (float) Math.toDegrees(-Math.asin(Mth.clamp(dz / Math.max(1.0E-4F, l), -1.0F, 1.0F))));
            col.submitModelPart(this.bones[s < 0.34F ? 0 : s < 0.67F ? 1 : 2], pose, type, light, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
            pose.popPose();
        }
        // 3. les étages de plumes
        for (float[] t : TIERS) {
            int tier = (int) t[0];
            int n = (int) t[2];
            for (int i = 0; i < n; i++) {
                float s = t[1] + (1.0F - t[1]) * (i + 0.5F) / n;
                float rel = (s - t[1]) / (1.0F - t[1]);
                float x = s * CURVE;
                int k = Math.min(CURVE - 1, (int) x);
                float f = x - k;
                float bx = Mth.lerp(f, c[k][0], c[k + 1][0]), by = Mth.lerp(f, c[k][1], c[k + 1][1]), bz = Mth.lerp(f, c[k][3], c[k + 1][3]);
                float edge = (float) Math.toDegrees(c[k + 1][2]);
                // direction : entre « pendante » et « perpendiculaire au bord », éventail vers l'extérieur au bout de l'aile
                float phi = Mth.lerp(p[3], 90.0F, edge + 90.0F) - p[4] * t[6] * (float) Math.pow(rel, 1.3) + p[8] * (0.5F + 0.5F * s);
                double pr = Math.toRadians(phi);
                float drop = t[3] * 0.8F;
                // pièce à sa longueur réelle : grandes plumes plus courtes côté dos, plus longues vers le bout (aucun étirement)
                int piece = tier < 3 ? tier : tier == 3 ? (rel < 0.5F ? 3 : 4) : (rel < 0.5F ? 5 : 6);
                pose.pushPose();
                pose.translate((bx + (float) Math.cos(pr) * drop) / 16.0F, (by + (float) Math.sin(pr) * drop) / 16.0F, (bz + t[4] + 0.03F * i) / 16.0F);
                pose.rotateDegrees(Axis.ZP, phi - 90.0F);
                pose.rotateDegrees(Axis.XP, t[5]);
                col.submitModelPart(this.tiers[piece], pose, type, light, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /** Profondeur de l'os : 0,6 px à l'omoplate (au ras du dos), puis elle recule sur les 6 premiers pixels de hauteur et reste
     * CONSTANTE au-dessus (3,6 px) : l'aile est droite, sa base collée au dos, et la tête est dégagée. */
    private static final float DEPTH_ROOT = 0.6F, DEPTH_TOP = 3.6F, DEPTH_RISE = 0.5F;

    /**
     * Points de l'arche {x, y, angle (rad) du maillon qui y arrive dans le plan de l'aile, profondeur z} : l'angle passe en
     * douceur de l'attache au bout ; la profondeur de l'os (z) : voir DEPTH_ROOT.
     */
    private static float[][] curve(float[] p) {
        float[][] c = new float[CURVE + 1][4];
        double a1 = Math.toRadians(p[1]), a2 = Math.toRadians(p[1] + p[2]);
        for (int k = 1; k <= CURVE; k++) {
            float s = (k - 0.5F) / CURVE;
            float b = Mth.clamp((s - p[5]) / Math.max(0.05F, p[6]), 0.0F, 1.0F);
            double a = a1 + (a2 - a1) * (b * b * (3.0F - 2.0F * b));
            c[k][0] = c[k - 1][0] + (float) Math.cos(a) * EDGE / CURVE;
            c[k][1] = c[k - 1][1] + (float) Math.sin(a) * EDGE / CURVE;
            c[k][2] = (float) a;
            c[k][3] = Math.min(DEPTH_TOP, DEPTH_ROOT + DEPTH_RISE * Math.max(0.0F, -c[k][1]));
        }
        c[0][2] = c[1][2];
        c[0][3] = DEPTH_ROOT;
        return c;
    }

    // ------------------------------------------------------------------ flammes du dos

    /**
     * FEU DU DOS du mode Défense (repère du buste : +z = dos, -y = haut) : petites langues de feu en volume, nées entre les
     * deux attaches des ailes, qui ne touchent jamais le buste ni la tête et suivent la direction des ailes ; elles brûlent
     * calmement (ondulation lente, hauteur qui varie doucement), grandissent depuis la base à l'allumage (gerbe de braises) et
     * rapetissent à l'extinction (fumée). Aucun prisme de lueur (cause de l'ancien rectangle).
     */
    private void backFlames(PoseStack pose, SubmitNodeCollector col, float fl, float[] change, float age, int seed, int light) {
        boolean starting = change != null && change[1] > 0.5F && change[0] < Lunarian.TRANSITION + 6;
        boolean ending = change != null && change[1] < 0.5F && change[0] < Lunarian.TRANSITION + 14;
        if (fl > 0.01F) {
            float[] up = {0.0F, -1.0F, 0.0F};
            float grow = LunarianClient.ramp(fl, 0.0F, 1.0F); // les flammes naissent à la base et grandissent
            // FEU DU DOS, discret et intégré : de petites langues nées dans l'espace entre les ailes (entre les deux attaches),
            // à plus de 2 px de la surface du dos (aucune traversée du buste) ; en montant elles s'écartent du corps (vers
            // l'arrière : jamais dans la tête) et les langues latérales suivent la direction des ailes (vers le haut et
            // l'extérieur). Elles brûlent calmement (LunarianFire.aura : ondulation lente, sans rotation).
            float zb = 4.4F / 16.0F;
            // (repère du BUSTE : le feu a sa propre attache, au centre du dos, indépendante des ailes ; il remplit le couloir laissé
            // libre entre les deux ailes et dépasse au-dessus de la tête, entre les sommets des ailes, comme sur les références)
            this.fire.aura(pose, col, new float[] {0.0F, 7.0F / 16.0F, zb}, up, 0.92F * grow, 0.15F, new float[] {0, 0, 0.32F}, fl, seed);
            this.fire.aura(pose, col, new float[] {0.0F, 9.0F / 16.0F, zb + 0.3F / 16.0F}, up, 0.62F * grow, 0.13F, new float[] {0, 0, 0.4F}, 0.9F * fl, seed + 1);
            for (int side = -1; side <= 1; side += 2) {
                // langues intermédiaires
                this.fire.aura(pose, col, new float[] {side * 1.0F / 16.0F, 8.0F / 16.0F, zb - 0.1F / 16.0F}, up, 0.6F * grow, 0.12F,
                        new float[] {side * 0.2F, 0, 0.3F}, 0.95F * fl, seed + 2 + side);
                // en haut du couloir : elles s'ouvrent vers les sommets des ailes (haut et extérieur)
                this.fire.aura(pose, col, new float[] {side * 1.5F / 16.0F, 4.0F / 16.0F, 4.8F / 16.0F}, up, 0.48F * grow, 0.1F,
                        new float[] {side * 0.45F, 0, 0.22F}, 0.9F * fl, seed + 6 + side);
                // petites langues basses
                this.fire.aura(pose, col, new float[] {side * 0.6F / 16.0F, 10.5F / 16.0F, zb - 0.1F / 16.0F}, up, 0.32F * grow, 0.1F,
                        new float[] {0, 0, 0.35F}, 0.85F * fl, seed + 10 + side);
            }
            // quelques braises qui montent doucement
            Random r = new Random(seed * 7L);
            for (int i = 0; i < 5; i++) {
                float ph = (age * (0.018F + r.nextFloat() * 0.012F) + r.nextFloat()) % 1.0F;
                float[] e = {(r.nextFloat() - 0.5F) * 0.3F + Mth.sin(age * 0.1F + i) * 0.04F, 6.0F / 16.0F - 1.1F * ph, zb + 0.08F + 0.3F * ph};
                this.fire.ember(pose, col, e, i * 40.0F, 0.025F * (1.0F - ph) * fl, argb(1.0F - ph, i % 2 == 0 ? YELLOW : ORANGE));
            }
        }
        if (starting) { // allumage : gerbe de braises au centre du dos
            float k = change[0] / (Lunarian.TRANSITION + 6.0F);
            Random r = new Random(seed * 3L);
            for (int i = 0; i < 12; i++) {
                double an = r.nextDouble() * Math.PI * 2;
                float sp = 0.4F + r.nextFloat() * 0.6F;
                float[] e = {(float) Math.cos(an) * sp * k * 0.7F, 5.0F / 16.0F - 1.2F * k * sp, 5.0F / 16.0F + (float) Math.sin(an) * sp * k * 0.3F + 0.2F * k};
                this.fire.ember(pose, col, e, i * 33.0F, 0.05F * (1.0F - k), argb(1.0F - k, i % 3 == 0 ? WHITE_HOT : YELLOW));
            }
        }
        if (ending) { // extinction : la fumée monte
            float k = change[0] / (Lunarian.TRANSITION + 14.0F);
            for (int i = 0; i < 5; i++) {
                float ph = (k + i * 0.2F) % 1.0F;
                this.fire.smoke(pose, col, new float[] {(i - 2) * 0.06F, 5.0F / 16.0F - 1.4F * ph, 0.5F + 0.2F * ph}, i * 50.0F + age * 3.0F,
                        0.3F + 0.5F * ph, 0.45F * (1.0F - ph) * (1.0F - k * 0.5F), light);
            }
        }
    }

    // ------------------------------------------------------------------ plumes en formation

    private void forming(PoseStack pose, SubmitNodeCollector col, float e, float age) {
        float d = LunarianFeathersAbility.DEPLOY, f = LunarianFeathersAbility.FORMED;
        for (int i = 0; i < LunarianFeathersAbility.COUNT; i++) {
            float born = d + (f - d) * i / LunarianFeathersAbility.COUNT;
            float shot = LunarianFeathersAbility.FIRST + LunarianFeathersAbility.EVERY * i;
            float k = LunarianClient.snap(e, born, born + 3.0F) * (e < shot ? 1.0F : 0.0F);
            if (k <= 0.01F) {
                continue;
            }
            double[] s = LunarianFeathersAbility.slot(i);
            // repère du buste : droite = -x, hauteur h au-dessus des pieds -> y = 1,5 - h (blocs), recul -> +z
            float[] p = {(float) (-s[0]), (float) (1.5 - s[1]) + 0.03F * Mth.sin(age * 0.5F + i), (float) s[2] + 0.25F};
            pose.pushPose();
            pose.translate(p[0], p[1], p[2]);
            pose.rotateDegrees(Axis.YP, 180.0F); // pointe vers l'avant du joueur
            pose.rotateDegrees(Axis.XP, -25.0F + 6.0F * Mth.sin(age * 0.4F + i));
            pose.rotateDegrees(Axis.ZP, Mth.sin(age * 0.3F + i) * 20.0F); // elle flotte en se balançant doucement
            // la plume de phénix s'embrase en se formant (même plume que celle qui sera projetée)
            this.fire.phoenixFeather(pose, col, 0.85F * k, k, i * 13 + 5, false);
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ feu du poing

    /**
     * Feu qui enveloppe le poing (repère du bras : la main au bout, vers +y) : couronne de flammes pixel qui remontent le long
     * du bras (vers l'arrière quand le bras est tendu ; étirées en dash), coeur clair, braises.
     */
    void fistFire(PoseStack pose, SubmitNodeCollector col, float k, boolean dashing, float age, int seed) {
        this.fire.time = age;
        float[] f = {-1.0F / 16.0F, 10.5F / 16.0F, 0.0F};
        float stretch = dashing ? 2.0F : 1.0F;
        // manchon : langues épaisses qui partent du poing et remontent l'avant-bras (vers l'arrière en dash)
        for (int i = 0; i < 7; i++) {
            double an = i * Math.PI * 2 / 7 + age * 0.12;
            float c = (float) Math.cos(an), s = (float) Math.sin(an);
            float[] base = {f[0] + c * 2.3F / 16.0F, f[1] + 0.12F, f[2] + s * 2.3F / 16.0F};
            float[] back = GoroLightning.normalize(new float[] {c * 0.35F, -1.0F, s * 0.35F});
            this.fire.sleeve(pose, col, base, back, (0.36F + 0.06F * Mth.sin(age * 1.1F + i * 1.9F)) * k * stretch, 0.12F, 0.95F * k, seed + i);
        }
        // le long de l'avant-bras : flammes plus courtes qui l'enveloppent
        for (int i = 0; i < 4; i++) {
            double an = i * Math.PI / 2 + 0.6;
            float[] base = {f[0] + (float) Math.cos(an) * 2.6F / 16.0F, 6.5F / 16.0F, (float) Math.sin(an) * 2.6F / 16.0F};
            this.fire.sleeve(pose, col, base, new float[] {0.0F, -1.0F, 0.0F}, 0.24F * k * stretch, 0.09F, 0.8F * k, seed + 20 + i);
        }
        // coeur incandescent autour du poing
        this.fire.blob(pose, col, new float[] {f[0], f[1] + 0.05F, f[2]}, 0.3F * k, age * 12.0F, LunarianFire.CORE, argb(0.55F * k, 0xFFFFFF));
        this.fire.blob(pose, col, new float[] {f[0], f[1] + 0.03F, f[2]}, 0.38F * k, -age * 9.0F, LunarianFire.EDGE, argb(0.4F * k, 0xFFFFFF));
        Random r = new Random(seed * 11L);
        for (int i = 0; i < 6; i++) {
            float ph = (age * 0.09F + r.nextFloat()) % 1.0F;
            double an = r.nextDouble() * Math.PI * 2;
            float[] p = {f[0] + (float) Math.cos(an) * 0.15F * (1.0F + ph), f[1] - 0.6F * ph * stretch, f[2] + (float) Math.sin(an) * 0.15F * (1.0F + ph)};
            this.fire.ember(pose, col, p, i * 60.0F, 0.035F * (1.0F - ph) * k, argb(1.0F - ph, i % 2 == 0 ? RED : ORANGE));
        }
    }

    // ------------------------------------------------------------------ première personne

    /** Première personne : feu du poing sur la main droite vue (AvatarRendererHandMixin). */
    public static void submitHand(PoseStack pose, SubmitNodeCollector col, ModelPart arm, PlayerModel model) {
        Minecraft mc = Minecraft.getInstance();
        if (shared == null || mc.player == null || !LunarianClient.isLunarian(mc.player) || arm != model.rightArm) {
            return;
        }
        float age = mc.player.tickCount + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float fist = LunarianClient.fist(mc.player, age);
        if (fist <= 0.01F) {
            return;
        }
        int kind = LunarianClient.kind(mc.player);
        float e = LunarianClient.elapsed(mc.player, age);
        pose.pushPose();
        arm.translateAndRotate(pose);
        shared.fistFire(pose, col, fist * 0.8F, kind == Lunarian.FIST && e >= nolan.bloxpiece.ability.LunarianFistAbility.WINDUP, age, mc.player.getId());
        pose.popPose();
    }
}
