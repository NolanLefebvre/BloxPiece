package nolan.bloxpiece.client.lunarian;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

import nolan.bloxpiece.client.goro.GoroLightning;
import nolan.bloxpiece.client.human.HumanDraw;

/**
 * Le FEU des Lunarians, en VOLUMES 3D pixel art (LunarianModels.FLAME : cube texturé, mis à l'échelle et orienté pièce par
 * pièce). Une langue de feu est une CHAÎNE de volumes qui s'affinent vers la pointe, en trois couches emboîtées : un COEUR
 * blanc-jaune, un CORPS orange et un BORD rouge troué (contour irrégulier) ; chaque maillon ondule de son côté, à sa propre
 * vitesse, et tourne sur lui-même : la flamme vit et se lit comme du feu sous tous les angles. Formes propres à chaque usage :
 * <ul>
 *   <li>{@link #aura} : grande langue verticale très ondulante (flammes du dos, mode Défense) ;</li>
 *   <li>{@link #wisp} : mèche fine et vive, couchée par la vitesse (plumes incandescentes) ;</li>
 *   <li>{@link #sleeve} : langue courte et épaisse qui épouse le poing et l'avant-bras ;</li>
 *   <li>{@link #fireball} : boule de feu en coquilles qui roulent (explosions) ;</li>
 *   <li>{@link #seg} : maillon seul (rubans de la tornade, traînées) ; {@link #ember} : braise.</li>
 * </ul>
 * {@link #time} : l'instant de l'image (ticks), à donner avant de dessiner. Fumée et éclats : outils partagés (HumanDraw).
 */
public final class LunarianFire {

    // ---- teintes (multipliées par les textures) et fumée
    public static final int WHITE_HOT = 0xFFF6D2, YELLOW = 0xFFD447, ORANGE = 0xFF8C22, RED = 0xE2421A, DARK = 0x8E2410, SMOKE = 0x3C3436;
    public static final int CORE = 0, BODY = 1, EDGE = 2;

    public final HumanDraw draw;
    private final ModelPart cube, feather;
    /** Instant de l'image (ticks). */
    public float time;

    public LunarianFire(EntityRendererProvider.Context context) {
        this.draw = new HumanDraw(context);
        this.cube = context.bakeLayer(LunarianModels.FLAME).getChild("cube");
        this.feather = context.bakeLayer(LunarianModels.FEATHER).getChild("feather");
    }

    public static int argb(float alpha, int rgb) {
        return GoroLightning.argb(Mth.clamp(alpha, 0.0F, 1.0F), rgb);
    }

    private static float[] add(float[] a, float[] d, float k) {
        return new float[] {a[0] + d[0] * k, a[1] + d[1] * k, a[2] + d[2] * k};
    }

    // ------------------------------------------------------------------ maillon

    /**
     * Un maillon de feu : volume de la couche {@code layer} de {@code a} à {@code b} (prolongé d'une demi-largeur aux deux
     * bouts : les maillons se chevauchent), section {@code w} x {@code d} blocs, tourné de {@code roll} degrés sur son axe.
     */
    public void seg(PoseStack pose, SubmitNodeCollector col, float[] a, float[] b, float w, float d, float roll, int layer, int color) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4F || w <= 0.003F || (color >>> 24) < 3) {
            return;
        }
        int variant = Math.floorMod((int) (this.time * 0.35F + a[0] * 7.0F + a[2] * 5.0F + layer), 2);
        pose.pushPose();
        pose.translate((a[0] + b[0]) * 0.5F, (a[1] + b[1]) * 0.5F, (a[2] + b[2]) * 0.5F);
        pose.rotateDegrees(Axis.YP, (float) Math.toDegrees(Math.atan2(dx, dz)));
        pose.rotateDegrees(Axis.XP, (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        pose.rotateDegrees(Axis.ZP, roll);
        pose.scale(w, d, len + w * 0.5F);
        col.order(3 + layer).submitModelPart(this.cube, pose, RenderTypes.entityTranslucentEmissive(LunarianModels.FIRE_TEXTURES[layer][variant]),
                GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    /** Petit volume posé en {@code c} (taille {@code s} blocs), tourné. */
    public void blob(PoseStack pose, SubmitNodeCollector col, float[] c, float s, float spin, int layer, int color) {
        if (s <= 0.003F || (color >>> 24) < 3) {
            return;
        }
        pose.pushPose();
        pose.translate(c[0], c[1], c[2]);
        pose.rotateDegrees(Axis.YP, spin);
        pose.rotateDegrees(Axis.XP, spin * 0.7F);
        pose.rotateDegrees(Axis.ZP, spin * 1.3F);
        pose.scale(s, s, s);
        col.order(3 + layer).submitModelPart(this.cube, pose, RenderTypes.entityTranslucentEmissive(LunarianModels.FIRE_TEXTURES[layer][
                Math.floorMod((int) (this.time * 0.4F + spin), 2)]), GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    // ------------------------------------------------------------------ langue de feu générique

    /**
     * Langue de feu : de {@code base}, le long de {@code up} (unitaire), longue de {@code len}, large de {@code width} à la base,
     * courbée par {@code drift} (blocs par bloc, au carré : la pointe part plus), {@code n} maillons ; {@code wave} : ondulation
     * latérale ; {@code speed} : vitesse de l'ondulation ; {@code layers} : 1 corps, 2 + coeur, 3 + bord troué.
     */
    public void tongue(PoseStack pose, SubmitNodeCollector col, float[] base, float[] up, float len, float width, float[] drift, int n,
                       float wave, float speed, int layers, float alpha, int seed) {
        tongue(pose, col, base, up, len, width, drift, n, wave, speed, layers, alpha, seed, 9.0F, true);
    }

    /**
     * Langue de feu, réglages complets : {@code spin} : rotation des maillons sur eux-mêmes (degrés par tick ; 0 = aucune, le
     * feu ondule sans tourner) ; {@code tip} : petit volume qui se détache à la pointe.
     */
    public void tongue(PoseStack pose, SubmitNodeCollector col, float[] base, float[] up, float len, float width, float[] drift, int n,
                       float wave, float speed, int layers, float alpha, int seed, float spin, boolean tip) {
        if (len <= 0.01F || alpha <= 0.01F) {
            return;
        }
        float[][] perp = GoroLightning.perpendiculars(GoroLightning.normalize(up));
        float[] prev = base;
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n;
            float s1 = Mth.sin(this.time * speed * (1.0F + 0.13F * (seed % 5)) + i * 1.25F + seed * 0.7F);
            float s2 = Mth.cos(this.time * speed * 0.83F + i * 0.9F + seed * 1.3F);
            float[] p = add(add(base, up, len * t), drift, len * t * t);
            p = add(add(p, perp[0], s1 * wave * width * t), perp[1], s2 * wave * width * 0.7F * t);
            float w = width * (1.0F - 0.78F * t) * (0.85F + 0.15F * Mth.sin(this.time * 0.6F + i + seed));
            float roll = this.time * spin + i * 37.0F + seed * 11.0F;
            seg(pose, col, prev, p, w, w * 0.85F, roll, BODY, argb(alpha * 0.82F, 0xFFFFFF)); // (translucide : on devine le coeur)
            if (layers >= 2 && t <= 0.67F) {
                seg(pose, col, prev, p, w * 0.5F, w * 0.45F, -roll, CORE, argb(alpha, 0xFFFFFF));
            }
            if (layers >= 3) {
                seg(pose, col, prev, p, w * 1.4F, w * 1.25F, roll * 0.5F + 20.0F, EDGE, argb(alpha * 0.75F, 0xFFFFFF));
            }
            prev = p;
        }
        if (!tip) {
            return;
        }
        // la pointe : petit volume qui se détache et vacille
        float flick = (this.time * speed * 0.5F + seed * 0.37F) % 1.0F;
        this.blob(pose, col, add(add(prev, up, width * (0.4F + 1.2F * flick)), drift, width * flick), width * 0.22F * (1.0F - flick),
                this.time * 30.0F + seed, BODY, argb(alpha * (1.0F - flick), 0xFFFFFF));
    }

    // ------------------------------------------------------------------ formes propres à chaque attaque

    /**
     * Feu du dos (mode Défense) : langue douce, cinq maillons qui ondulent LENTEMENT et faiblement, sans tourner sur eux-mêmes
     * ni pointe qui saute : le feu brûle calmement ; la hauteur varie lentement (sommes de sinus à périodes différentes).
     */
    public void aura(PoseStack pose, SubmitNodeCollector col, float[] base, float[] up, float len, float width, float[] drift, float alpha, int seed) {
        float breathe = 0.85F + 0.09F * Mth.sin(this.time * 0.11F + seed) + 0.06F * Mth.sin(this.time * 0.27F + seed * 2.3F);
        tongue(pose, col, base, up, len * breathe, width, drift, 5, 0.32F, 0.13F, 3, alpha, seed, 0.0F, false);
    }

    /**
     * MASSE DE FEU de la tornade : langue large et courte (trois maillons épais), corps translucide enveloppé d'un bord troué
     * (silhouette irrégulière), coeur si {@code hot} ; elle ondule doucement sans tourner sur elle-même : ce sont les masses qui
     * tournent autour de l'axe, pas les morceaux.
     */
    public void mass(PoseStack pose, SubmitNodeCollector col, float[] base, float[] up, float len, float width, float[] drift, float alpha, int seed,
                     boolean hot) {
        tongue(pose, col, base, up, len, width, drift, 3, 0.3F, 0.25F, hot ? 3 : 1, alpha, seed, 0.0F, false);
        if (!hot) { // bord troué autour du corps : contour irrégulier
            float[] a = base, b = add(add(base, up, len * 0.55F), drift, len * 0.3F);
            seg(pose, col, a, b, width * 1.35F, width * 1.15F, seed * 23.0F, EDGE, argb(alpha * 0.6F, 0xFFFFFF));
        }
    }

    /** Mèche des plumes incandescentes : fine, vive, couchée par la vitesse (vers {@code back}), qui monte de {@code rise} (repère : +y ou -y = haut), deux couches. */
    public void wisp(PoseStack pose, SubmitNodeCollector col, float[] base, float[] back, float len, float width, float rise, float alpha, int seed) {
        tongue(pose, col, base, back, len, width, new float[] {0.0F, rise, 0.0F}, 3, 0.6F, 0.9F, 2, alpha, seed);
    }

    /** Manchon du poing : langue courte et épaisse qui épouse le poing et file vers l'arrière ({@code back}). */
    public void sleeve(PoseStack pose, SubmitNodeCollector col, float[] base, float[] back, float len, float width, float alpha, int seed) {
        tongue(pose, col, base, back, len, width, new float[] {0, 0, 0}, 4, 0.5F, 0.55F, 3, alpha, seed);
    }

    /**
     * Boule de feu : trois coquilles de volumes qui roulent (coeur dense, corps, bord troué), rayon {@code r} ; elle gonfle puis
     * s'ouvre (les volumes du bord s'écartent et s'éteignent quand {@code open} va vers 1).
     */
    public void fireball(PoseStack pose, SubmitNodeCollector col, float[] c, float r, float open, float alpha, int seed) {
        if (r <= 0.01F || alpha <= 0.01F) {
            return;
        }
        java.util.Random rnd = new java.util.Random(seed * 41L);
        for (int shell = 0; shell < 3; shell++) {
            int n = shell == 0 ? 6 : shell == 1 ? 12 : 16;
            float rr = r * (shell == 0 ? 0.25F : shell == 1 ? 0.6F : 0.95F) * (1.0F + open * (0.2F + 0.4F * shell));
            for (int i = 0; i < n; i++) {
                float[] dir = GoroLightning.normalize(new float[] {rnd.nextFloat() - 0.5F, rnd.nextFloat() - 0.4F, rnd.nextFloat() - 0.5F});
                float s = r * (shell == 0 ? 0.55F : shell == 1 ? 0.42F : 0.32F) * (1.0F - open * (shell == 2 ? 0.8F : 0.5F));
                float a = alpha * (shell == 2 ? 0.8F * (1.0F - open) : 1.0F);
                this.blob(pose, col, add(c, dir, rr), s, this.time * (20.0F + shell * 10.0F) + i * 47.0F, shell == 0 ? CORE : shell == 1 ? BODY : EDGE,
                        argb(a, 0xFFFFFF));
            }
        }
    }

    /** Braise : petit volume vif qui tourne. */
    public void ember(PoseStack pose, SubmitNodeCollector col, float[] c, float spin, float size, int color) {
        this.blob(pose, col, c, size, spin + this.time * 25.0F, CORE, color);
    }

    /** Fumée (nuage pixel éclairé par le monde). */
    public void smoke(PoseStack pose, SubmitNodeCollector col, float[] c, float rot, float scale, float alpha, int light) {
        this.draw.dust(pose, col, c, rot, scale, alpha, SMOKE, light);
    }

    /** Plume incandescente (pointe vers +z du repère courant), à l'échelle {@code scale}. */
    public void feather(PoseStack pose, SubmitNodeCollector col, float scale, int color) {
        if (scale <= 0.01F) {
            return;
        }
        pose.pushPose();
        pose.scale(scale, scale, scale);
        col.order(2).submitModelPart(this.feather, pose, RenderTypes.entityTranslucentEmissive(LunarianModels.FEATHER_TEXTURE),
                GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    /**
     * PLUME DE PHÉNIX EN FEU, dans le repère courant (pointe vers +z), à l'échelle {@code scale} :
     * <ol>
     *   <li>la plume (LunarianModels.FEATHER : rachis incandescent, barbes jaunes en relief sur barbes orange-rouge, pointe en
     *       braise), lumineuse ;</li>
     *   <li>une ENVELOPPE DE CHALEUR : la même plume, un peu plus grande, orange et translucide, qui pulse ;</li>
     *   <li>des BARBES DE FEU : sept langues de flamme 3D de chaque côté, nées sur le bord de la plume et couchées vers l'arrière
     *       et l'extérieur ; elles vacillent chacune à leur rythme (la plume semble faite de flammes) et s'allongent en vol
     *       ({@code moving}) ;</li>
     *   <li>une longue flamme qui part de la base (la queue de la plume) et une petite flamme vive à la pointe.</li>
     * </ol>
     */
    public void phoenixFeather(PoseStack pose, SubmitNodeCollector col, float scale, float alpha, int seed, boolean moving) {
        if (scale <= 0.01F || alpha <= 0.01F) {
            return;
        }
        // 1. la plume et 2. son enveloppe de chaleur
        feather(pose, col, scale, argb(alpha, 0xFFFFFF));
        float pulse = 1.0F + 0.06F * Mth.sin(this.time * 0.9F + seed);
        pose.pushPose();
        pose.scale(1.22F * pulse, 1.8F, 1.08F * pulse);
        feather(pose, col, scale, argb(0.3F * alpha, 0xFF7A26));
        pose.popPose();
        // 3. barbes de feu le long des deux bords
        float u = scale / 16.0F; // 1 px du modèle, en blocs
        float stretch = moving ? 1.35F : 1.0F;
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 7; k++) {
                int slice = Math.min(LunarianModels.FEATHER_PROFILE.length - 1, 1 + k * 3 / 2);
                float z = (LunarianModels.FEATHER_START + slice * LunarianModels.FEATHER_STEP + 0.8F) * u;
                float hw = LunarianModels.FEATHER_PROFILE[slice] * u;
                float flick = 0.75F + 0.25F * Mth.sin(this.time * (0.8F + 0.07F * k) + k * 1.9F + seed + side);
                float[] base = {side * hw * 0.9F, 0.0F, z};
                float[] dir = GoroLightning.normalize(new float[] {side * 0.6F, 0.12F * Mth.sin(this.time * 0.5F + k), -1.0F});
                float len = (0.2F + 0.05F * (3 - Math.abs(k - 3))) * scale * flick * stretch;
                tongue(pose, col, base, dir, len, 0.055F * scale, new float[] {0, 0, 0}, 3, 0.6F, 0.9F, k % 2 == 0 ? 2 : 3,
                        0.95F * alpha, seed * 7 + k * 2 + side, 0.0F, false);
            }
        }
        // 4. flamme de queue et flamme de pointe
        tongue(pose, col, new float[] {0.0F, 0.0F, -9.0F * u}, new float[] {0.0F, 0.0F, -1.0F}, 0.55F * scale * stretch, 0.1F * scale,
                new float[] {0, 0, 0}, 4, 0.7F, 0.7F, 3, alpha, seed + 31, 0.0F, false);
        tongue(pose, col, new float[] {0.0F, 0.0F, 11.0F * u}, new float[] {0.0F, 0.15F, 1.0F}, 0.14F * scale, 0.04F * scale,
                new float[] {0, 0, 0}, 2, 0.5F, 1.2F, 2, alpha, seed + 37, 0.0F, false);
    }

    /** Oriente l'axe +z du repère courant sur {@code d} (lacet puis tangage). */
    public static void face(PoseStack pose, float[] d) {
        float yaw = (float) Math.toDegrees(Math.atan2(d[0], d[2]));
        float pitch = (float) Math.toDegrees(-Math.atan2(d[1], Math.sqrt(d[0] * d[0] + d[2] * d[2])));
        pose.rotateDegrees(Axis.YP, yaw);
        pose.rotateDegrees(Axis.XP, pitch);
    }
}
