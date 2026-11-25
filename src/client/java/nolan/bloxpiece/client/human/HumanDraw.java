package nolan.bloxpiece.client.human;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

import nolan.bloxpiece.client.goro.GoroLightning;

/**
 * Outils de dessin 3D des effets de la race Humaine (ondes de choc, anneaux, cônes d'air comprimé, coques d'air déformé,
 * fissures, éclats de pierre, nuages de poussière, poing d'air). Tout est construit avec des volumes : le prisme « segment »
 * (HakiBoltRenderer, texture blanche teintée, comme les effets du Cyborg) et les modèles pixel art de HumanModels. Partagé par
 * HumanFxRenderer (effets posés dans le monde) et HumanFistLayer (effets qui suivent le joueur).
 */
public final class HumanDraw {

    // ---- palette de la race Humaine : l'air (blanc bleuté), la force (or, ambre), la terre (poussière, fissures)
    public static final int AIR = 0xEAF7FF, AIR_EDGE = 0xB9E2FF, FLASH = 0xFFFFFF;
    public static final int GOLD = 0xFFD36A, AMBER = 0xFF9A3C, EMBER = 0xFF6A2A;
    public static final int DUST = 0xE0D2BC, DUST_DARK = 0xA8957C, CRACK = 0x22170F, CRACK_GLOW = 0xFFB25A;

    private final ModelPart segment, rockA, rockB, dust, fist, ring, chunk, flame;

    /** Construit avec les modèles cuits par Minecraft (renderer d'entité ou couche du joueur). */
    public HumanDraw(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
        this.segment = context.bakeLayer(nolan.bloxpiece.client.haki.HakiBoltRenderer.LAYER).getChild("segment");
        ModelPart rockRoot = context.bakeLayer(HumanModels.ROCK);
        this.rockA = rockRoot.getChild("a");
        this.rockB = rockRoot.getChild("b");
        this.dust = context.bakeLayer(HumanModels.DUST).getChild("puff");
        this.fist = context.bakeLayer(HumanModels.FIST).getChild("fist");
        this.ring = context.bakeLayer(HumanModels.RING).getChild("ring");
        this.chunk = context.bakeLayer(HumanModels.CHUNK).getChild("chunk");
        this.flame = context.bakeLayer(HumanModels.FLAME).getChild("flame");
    }

    public static RenderType glow() {
        return RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
    }

    public static float[] v(float x, float y, float z) {
        return new float[] {x, y, z};
    }

    public static float[] add(float[] a, float[] d, float k) {
        return new float[] {a[0] + d[0] * k, a[1] + d[1] * k, a[2] + d[2] * k};
    }

    /** Point du cercle de centre {@code c}, de rayon {@code r}, dans le plan (u, w), à l'angle {@code a}. */
    public static float[] onCircle(float[] c, float[] u, float[] w, float r, double a) {
        float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
        return new float[] {c[0] + (u[0] * cs + w[0] * sn) * r, c[1] + (u[1] * cs + w[1] * sn) * r, c[2] + (u[2] * cs + w[2] * sn) * r};
    }

    public static float easeOut(float k) {
        k = Math.max(0.0F, Math.min(1.0F, k));
        return 1.0F - (1.0F - k) * (1.0F - k);
    }

    // ------------------------------------------------------------------ volumes lumineux (prismes)

    /** Prisme de section carrée {@code width} px de a vers b. */
    public void seg(PoseStack pose, SubmitNodeCollector collector, int order, float[] a, float[] b, float width, int color) {
        GoroLightning.segment(this.segment, pose, collector, order, glow(), a, b, width, color);
    }

    /**
     * Dalle de a vers b : largeur {@code wx} px (horizontale, perpendiculaire au trajet) et hauteur {@code wy} px. Sert aux murs
     * d'onde de choc (une dalle par portion d'anneau).
     */
    public void slab(PoseStack pose, SubmitNodeCollector collector, int order, RenderType type, float[] a, float[] b, float wx, float wy,
                     int color, int light) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5F || wx <= 0.0F || wy <= 0.0F) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float overlap = 0.02F;
        pose.pushPose();
        pose.translate(a[0], a[1], a[2]);
        pose.rotateDegrees(Axis.YP, yaw);
        pose.rotateDegrees(Axis.XP, pitch);
        pose.translate(0.0F, 0.0F, -overlap);
        pose.scale(wx, wy, (len + 2.0F * overlap) * 16.0F);
        collector.order(order).submitModelPart(this.segment, pose, type, light, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    /** Anneau de {@code n} prismes, de centre {@code c}, rayon {@code r}, dans le plan (u, w). */
    public void ring(PoseStack pose, SubmitNodeCollector collector, int order, float[] c, float[] u, float[] w, float r, float width,
                     int color, int n) {
        if (r <= 0.01F || width <= 0.0F) {
            return;
        }
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n, b = 2 * Math.PI * (i + 1) / n;
            seg(pose, collector, order, onCircle(c, u, w, r, a), onCircle(c, u, w, r, b), width, color);
        }
    }

    /** Anneau horizontal (plan du sol). */
    public void flatRing(PoseStack pose, SubmitNodeCollector collector, int order, float[] c, float r, float width, int color, int n) {
        ring(pose, collector, order, c, v(1, 0, 0), v(0, 0, 1), r, width, color, n);
    }

    /**
     * Mur d'onde de choc : bande verticale circulaire de rayon {@code r}, de {@code y0} à {@code y0 + height}, faite de dalles
     * (une par portion), plus une crête lumineuse au sommet.
     */
    public void wall(PoseStack pose, SubmitNodeCollector collector, int order, float[] c, float r, float y0, float height, int color,
                     int crest, int n) {
        if (r <= 0.05F || height <= 0.01F) {
            return;
        }
        float mid = y0 + height * 0.5F;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n, b = 2 * Math.PI * (i + 1) / n;
            float[] p = onCircle(v(c[0], c[1] + mid, c[2]), v(1, 0, 0), v(0, 0, 1), r, a);
            float[] q = onCircle(v(c[0], c[1] + mid, c[2]), v(1, 0, 0), v(0, 0, 1), r, b);
            slab(pose, collector, order, glow(), p, q, 0.7F, height * 16.0F, color, GoroLightning.FULL_BRIGHT);
        }
        flatRing(pose, collector, order + 1, v(c[0], c[1] + y0 + height, c[2]), r, 1.4F, crest, n);
    }

    /**
     * Cône d'air comprimé : {@code rings} anneaux empilés de {@code apex} sur {@code len} blocs dans la direction {@code dir},
     * rayon de {@code r0} à {@code r1}, opacité qui décroît vers l'ouverture.
     */
    public void cone(PoseStack pose, SubmitNodeCollector collector, int order, float[] apex, float[] dir, float len, float r0, float r1,
                     float alpha, int rgb, int rings, float width) {
        float[][] perp = GoroLightning.perpendiculars(dir);
        for (int i = 0; i < rings; i++) {
            float k = (i + 0.5F) / rings;
            float[] c = add(apex, dir, len * k);
            ring(pose, collector, order, c, perp[0], perp[1], r0 + (r1 - r0) * k, width * (1.0F - 0.5F * k),
                    GoroLightning.argb(alpha * (1.0F - 0.6F * k), rgb), 20);
        }
    }

    /** Boule de lumière (six prismes croisés : une étoile pleine qui paraît ronde). */
    public void ball(PoseStack pose, SubmitNodeCollector collector, int order, float[] c, float r, int color) {
        if (r <= 0.01F) {
            return;
        }
        float[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0.7F, 0.7F, 0}, {0.7F, 0, 0.7F}, {0, 0.7F, 0.7F}};
        for (float[] ax : axes) {
            seg(pose, collector, order, v(c[0] - ax[0] * r, c[1] - ax[1] * r, c[2] - ax[2] * r),
                    v(c[0] + ax[0] * r, c[1] + ax[1] * r, c[2] + ax[2] * r), r * 16.0F * 1.15F, color);
        }
    }

    /**
     * Coque d'air déformé : une sphère creuse dessinée par des méridiens et des parallèles très translucides (on voit à travers,
     * le bord se lit comme une ondulation de l'air).
     */
    public void shell(PoseStack pose, SubmitNodeCollector collector, int order, float[] c, float r, float width, int color, float spin) {
        if (r <= 0.02F) {
            return;
        }
        for (int m = 0; m < 4; m++) { // méridiens
            double a = spin + m * Math.PI / 4;
            float[] u = v((float) Math.cos(a), 0, (float) Math.sin(a));
            ring(pose, collector, order, c, u, v(0, 1, 0), r, width, color, 20);
        }
        for (int p = -1; p <= 1; p++) { // parallèles
            float h = p * 0.5F;
            float pr = r * (float) Math.sqrt(1.0 - h * h);
            flatRing(pose, collector, order, v(c[0], c[1] + h * r, c[2]), pr, width, color, 20);
        }
    }

    /** Rayons d'éclat : {@code n} pointes effilées dans le plan (u, w), de {@code r0} à {@code r1}. */
    public void burst(PoseStack pose, SubmitNodeCollector collector, int order, float[] c, float[] u, float[] w, float r0, float r1,
                      float width, int color, int n, double phase) {
        for (int i = 0; i < n; i++) {
            double a = phase + 2 * Math.PI * i / n;
            float[] p = onCircle(c, u, w, r0, a), q = onCircle(c, u, w, r1 * (i % 2 == 0 ? 1.0F : 0.65F), a);
            float[] mid = v((p[0] + q[0]) * 0.5F, (p[1] + q[1]) * 0.5F, (p[2] + q[2]) * 0.5F);
            seg(pose, collector, order, p, mid, width, color);
            seg(pose, collector, order, mid, q, width * 0.45F, color);
        }
    }

    // ------------------------------------------------------------------ modèles pixel art

    /** Éclat de pierre en {@code c} (variante 0 ou 1), tourné, à l'échelle {@code scale} (1 = 4 px), éclairé comme le monde. */
    public void rock(PoseStack pose, SubmitNodeCollector collector, float[] c, float rx, float ry, float rz, float scale, int variant, int light) {
        if (scale <= 0.01F) {
            return;
        }
        pose.pushPose();
        pose.translate(c[0], c[1], c[2]);
        pose.rotateDegrees(Axis.YP, ry);
        pose.rotateDegrees(Axis.XP, rx);
        pose.rotateDegrees(Axis.ZP, rz);
        pose.scale(scale, scale, scale);
        collector.submitModelPart(variant == 0 ? this.rockA : this.rockB, pose, RenderTypes.entityCutout(HumanModels.ROCK_TEXTURE), light,
                OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
        pose.popPose();
    }

    /** Nuage de poussière en {@code c} (1 = 8 px de large), teinté {@code rgb}, d'opacité {@code alpha}. */
    public void dust(PoseStack pose, SubmitNodeCollector collector, float[] c, float rot, float scale, float alpha, int rgb, int light) {
        if (scale <= 0.01F || alpha <= 0.01F) {
            return;
        }
        pose.pushPose();
        pose.translate(c[0], c[1], c[2]);
        pose.rotateDegrees(Axis.YP, rot);
        pose.rotateDegrees(Axis.XP, rot * 0.37F);
        pose.scale(scale, scale, scale);
        collector.order(3).submitModelPart(this.dust, pose, RenderTypes.entityTranslucent(HumanModels.DUST_TEXTURE), light,
                OverlayTexture.NO_OVERLAY, null, GoroLightning.argb(alpha, rgb));
        pose.popPose();
    }

    /**
     * Poing d'air géant en {@code c}, pointé dans la direction {@code dir}, à l'échelle {@code scale} (1 = environ 0,5 bloc de
     * large), tourné de {@code roll} degrés sur son axe ; {@code emissive} : lumineux (pleine luminosité).
     */
    public void fist(PoseStack pose, SubmitNodeCollector collector, int order, float[] c, float[] dir, float scale, float roll, int color) {
        if (scale <= 0.01F) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dir[0], dir[2]));
        float pitch = (float) Math.toDegrees(-Math.atan2(dir[1], Math.sqrt(dir[0] * dir[0] + dir[2] * dir[2])));
        pose.pushPose();
        pose.translate(c[0], c[1], c[2]);
        pose.rotateDegrees(Axis.YP, yaw);
        pose.rotateDegrees(Axis.XP, pitch);
        pose.rotateDegrees(Axis.YP, 180.0F); // le modèle pointe vers -Z : retourné vers la direction de la frappe
        pose.rotateDegrees(Axis.ZP, roll);
        pose.scale(scale, scale, scale);
        collector.order(order).submitModelPart(this.fist, pose, RenderTypes.entityTranslucentEmissive(HumanModels.FIST_TEXTURE),
                GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    // ------------------------------------------------------------------ Poing du Conquérant : modèles en blocs

    /** Tourne le repère pour que son axe +Y pointe vers {@code dir}. */
    public static void orientY(PoseStack pose, float[] dir) {
        org.joml.Vector3f to = new org.joml.Vector3f(dir[0], dir[1], dir[2]);
        if (to.lengthSquared() < 1.0E-8F) {
            return;
        }
        to.normalize();
        pose.mulPose(new org.joml.Matrix4f().rotation(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0.0F, 1.0F, 0.0F), to)));
    }

    /**
     * Anneau de force en blocs (HumanModels.RING) de centre {@code c}, autour de l'axe {@code axis}, de rayon {@code radius}
     * blocs, tourné de {@code spin} degrés sur son axe ; {@code thick} : épaisseur de la bande (1 = 2 px) ; lumineux.
     */
    public void ringModel(PoseStack pose, SubmitNodeCollector collector, int order, float[] c, float[] axis, float radius, float spin,
                          float thick, int color) {
        if (radius <= 0.01F || (color >>> 24) == 0) {
            return;
        }
        float k = radius / 0.375F; // le modèle a un rayon de 6 px
        pose.pushPose();
        pose.translate(c[0], c[1], c[2]);
        orientY(pose, axis);
        pose.rotateDegrees(Axis.YP, spin);
        pose.scale(k, thick * Math.max(1.0F, k * 0.35F), k);
        collector.order(order).submitModelPart(this.ring, pose, RenderTypes.entityTranslucentEmissive(HumanModels.RING_TEXTURE),
                GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    /**
     * Voxel d'explosion (HumanModels.CHUNK, 1 = 8 px = 0,5 bloc) : lumineux pour le feu ({@code emissive}), éclairé par le monde
     * pour la fumée.
     */
    public void chunk(PoseStack pose, SubmitNodeCollector collector, int order, float[] c, float rx, float ry, float rz, float scale,
                      int color, boolean emissive, int light) {
        if (scale <= 0.01F || (color >>> 24) == 0) {
            return;
        }
        pose.pushPose();
        pose.translate(c[0], c[1], c[2]);
        pose.rotateDegrees(Axis.YP, ry);
        pose.rotateDegrees(Axis.XP, rx);
        pose.rotateDegrees(Axis.ZP, rz);
        pose.scale(scale, scale, scale);
        collector.order(order).submitModelPart(this.chunk, pose, emissive ? RenderTypes.entityTranslucentEmissive(HumanModels.CHUNK_TEXTURE)
                : RenderTypes.entityTranslucent(HumanModels.CHUNK_TEXTURE), emissive ? GoroLightning.FULL_BRIGHT : light,
                OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    /**
     * Langue de flamme pixel (HumanModels.FLAME) posée en {@code base}, qui monte vers -Y du repère (repère d'un modèle :
     * vers le haut du monde), haute de {@code scale} x 13 px, inclinée de {@code swayX} / {@code swayZ} degrés.
     */
    public void flame(PoseStack pose, SubmitNodeCollector collector, int order, float[] base, float scale, float width, float swayX,
                      float swayZ, int color) {
        if (scale <= 0.01F || (color >>> 24) == 0) {
            return;
        }
        pose.pushPose();
        pose.translate(base[0], base[1], base[2]);
        pose.rotateDegrees(Axis.XP, swayX);
        pose.rotateDegrees(Axis.ZP, swayZ);
        pose.scale(scale * width, scale, scale * width);
        collector.order(order).submitModelPart(this.flame, pose, RenderTypes.entityTranslucentEmissive(HumanModels.FLAME_TEXTURE),
                GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    /** Poing d'air (HumanModels.FIST) dans le repère courant, pointé vers -Z, à l'échelle {@code scale}, lumineux. */
    public void fistHere(PoseStack pose, SubmitNodeCollector collector, int order, float scale, int color) {
        if (scale <= 0.01F || (color >>> 24) == 0) {
            return;
        }
        pose.pushPose();
        pose.scale(scale, scale, scale);
        collector.order(order).submitModelPart(this.fist, pose, RenderTypes.entityTranslucentEmissive(HumanModels.FIST_TEXTURE),
                GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    /** Fissure en zigzag (déjà tracée) : {@code pts} jusqu'à l'indice fractionnaire {@code upTo}, large de {@code width} px. */
    public void crack(PoseStack pose, SubmitNodeCollector collector, int order, float[][] pts, float upTo, float width, int color,
                      int light, boolean emissive) {
        int whole = (int) Math.floor(upTo);
        for (int i = 0; i < pts.length - 1 && i <= whole; i++) {
            float[] a = pts[i];
            float[] b = pts[i + 1];
            if (i == whole) {
                float f = upTo - whole;
                if (f <= 0.01F) {
                    break;
                }
                b = v(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f);
            }
            float taper = 1.0F - 0.7F * i / (pts.length - 1);
            slab(pose, collector, order, emissive ? RenderTypes.entityTranslucentEmissive(GoroLightning.TEXTURE) : glow(), a, b,
                    width * taper, 0.25F, color, emissive ? GoroLightning.FULL_BRIGHT : light);
        }
    }
}
