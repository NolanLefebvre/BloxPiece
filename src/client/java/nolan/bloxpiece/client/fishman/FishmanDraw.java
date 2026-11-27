package nolan.bloxpiece.client.fishman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

import nolan.bloxpiece.client.goro.GoroLightning;

/**
 * Outils de dessin de l'EAU des Fishmen, en volumes pixel art (pas de particules rondes) : cubes d'eau texturés et
 * translucides (FishmanModels.WATER), écume, gouttes en larme orientées dans leur trajectoire, murs de vague en anneau avec crête
 * d'écume recourbée. L'eau est éclairée par le monde mais garde une lumière minimale (lisible la nuit). Reflets fins : prisme
 * « segment » (GoroLightning.segment), comme les autres races. Partagé par FishmanFxRenderer et FishmanLayer.
 */
public final class FishmanDraw {

    // ---- palette de l'eau : profond, eau, reflet, écume
    public static final int DEEP = 0x1E5AB4, WATER = 0x3C8CE6, LIGHT = 0x8CD2FA, SHINE = 0xE6F8FF, FOAM = 0xFFFFFF;

    private final ModelPart cube, segment;

    public FishmanDraw(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
        this.cube = context.bakeLayer(FishmanModels.WATER).getChild("cube");
        this.segment = context.bakeLayer(nolan.bloxpiece.client.haki.HakiBoltRenderer.LAYER).getChild("segment");
    }

    public static int argb(float alpha, int rgb) {
        return GoroLightning.argb(Mth.clamp(alpha, 0.0F, 1.0F), rgb);
    }

    /** Lumière de l'eau : celle du monde, avec un minimum (lumière de bloc 10) pour rester lisible dans le noir. */
    public static int lit(int light) {
        int block = Math.max((light & 0xFFFF) >> 4, 10);
        return (light & 0xFFFF0000) | (block << 4);
    }

    /** Cube d'eau (ou d'écume) centré en {@code c}, tourné (degrés : lacet, tangage, roulis), de taille {@code sx, sy, sz} blocs. */
    public void cube(PoseStack pose, SubmitNodeCollector col, boolean foam, float[] c, float yaw, float pitch, float roll, float sx, float sy,
                     float sz, int color, int light) {
        if (sx <= 0.004F || sy <= 0.004F || sz <= 0.004F || (color >>> 24) < 3) {
            return;
        }
        pose.pushPose();
        pose.translate(c[0], c[1], c[2]);
        pose.rotateDegrees(Axis.YP, yaw);
        pose.rotateDegrees(Axis.XP, pitch);
        pose.rotateDegrees(Axis.ZP, roll);
        pose.scale(sx, sy, sz);
        col.order(foam ? 2 : 1).submitModelPart(this.cube, pose, RenderTypes.entityTranslucent(foam ? FishmanModels.FOAM_TEXTURE
                : FishmanModels.WATER_TEXTURE), lit(light), OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    /** Lacet et tangage (degrés) qui orientent l'axe local +z sur {@code d}. */
    public static float[] angles(float[] d) {
        float yaw = (float) Math.toDegrees(Math.atan2(d[0], d[2]));
        float pitch = (float) Math.toDegrees(-Math.atan2(d[1], Math.sqrt(d[0] * d[0] + d[2] * d[2])));
        return new float[] {yaw, pitch};
    }

    /** Cube allongé de {@code a} à {@code b} (section {@code w} x {@code h} blocs), roulis {@code roll}. */
    public void bar(PoseStack pose, SubmitNodeCollector col, boolean foam, float[] a, float[] b, float w, float h, float roll, int color, int light) {
        float[] d = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        if (len < 1.0E-4F) {
            return;
        }
        float[] an = angles(d);
        cube(pose, col, foam, new float[] {(a[0] + b[0]) * 0.5F, (a[1] + b[1]) * 0.5F, (a[2] + b[2]) * 0.5F}, an[0], an[1], roll, w, h, len, color, light);
    }

    /** Masse d'eau « ronde » : deux cubes imbriqués tournés l'un par rapport à l'autre, plus un reflet clair au-dessus. */
    public void blob(PoseStack pose, SubmitNodeCollector col, float[] c, float r, float spin, float alpha, int light) {
        if (r <= 0.01F) {
            return;
        }
        cube(pose, col, false, c, spin, 18.0F, 30.0F, r * 1.6F, r * 1.6F, r * 1.6F, argb(0.8F * alpha, WATER), light);
        cube(pose, col, false, c, spin + 45.0F, -26.0F, 12.0F, r * 1.35F, r * 1.35F, r * 1.35F, argb(0.6F * alpha, LIGHT), light);
        cube(pose, col, true, new float[] {c[0], c[1] + r * 0.55F, c[2]}, spin * 1.3F, 0.0F, 0.0F, r * 0.55F, r * 0.25F, r * 0.55F,
                argb(0.7F * alpha, SHINE), light);
    }

    /**
     * Goutte en LARME dans le sens {@code dir} : une tête en losange (cube tourné de 45 degrés), un corps qui s'affine derrière,
     * un fil clair de vitesse ; {@code s} : taille de la tête (blocs).
     */
    public void drop(PoseStack pose, SubmitNodeCollector col, float[] c, float[] dir, float s, float stretch, float alpha, int light) {
        float l = (float) Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        float[] d = l < 1.0E-4F ? new float[] {0, -1, 0} : new float[] {dir[0] / l, dir[1] / l, dir[2] / l};
        float[] an = angles(d);
        cube(pose, col, false, c, an[0], an[1], 45.0F, s, s, s * 1.2F, argb(0.9F * alpha, WATER), light);
        float[] body = {c[0] - d[0] * s * (0.6F + 0.5F * stretch), c[1] - d[1] * s * (0.6F + 0.5F * stretch), c[2] - d[2] * s * (0.6F + 0.5F * stretch)};
        cube(pose, col, false, body, an[0], an[1], 45.0F, s * 0.62F, s * 0.62F, s * (1.0F + 1.4F * stretch), argb(0.75F * alpha, LIGHT), light);
        cube(pose, col, true, new float[] {c[0] + d[0] * s * 0.15F, c[1] + d[1] * s * 0.15F + s * 0.2F, c[2] + d[2] * s * 0.15F}, an[0], an[1], 45.0F,
                s * 0.3F, s * 0.3F, s * 0.4F, argb(0.85F * alpha, SHINE), light);
    }

    /** Fil de reflet (prisme lumineux fin) de a vers b. */
    public void glint(PoseStack pose, SubmitNodeCollector col, float[] a, float[] b, float width, int color) {
        GoroLightning.segment(this.segment, pose, col, 3, RenderTypes.entityTranslucent(GoroLightning.TEXTURE), a, b, width, color);
    }

    /**
     * Mur de VAGUE en anneau autour de {@code c} (plan horizontal) : {@code n} panneaux d'eau tangents, de hauteur {@code h}
     * (variée par la houle), d'épaisseur {@code thick} ; au sommet, une crête d'écume penchée vers l'extérieur ({@code curl}) ;
     * au pied, une base plus large et plus sombre. {@code from}..{@code to} : portion d'anneau (radians) ; {@code seed} : houle.
     */
    public void wall(PoseStack pose, SubmitNodeCollector col, float[] c, float r, float h, float thick, float curl, float alpha, double from,
                     double to, int n, float phase, int light) {
        if (r <= 0.05F || h <= 0.02F || alpha <= 0.01F) {
            return;
        }
        double step = (to - from) / n;
        float seg = (float) (r * step) * 1.18F;
        for (int i = 0; i < n; i++) {
            double a = from + step * (i + 0.5);
            float swell = 0.78F + 0.22F * Mth.sin((float) a * 5.0F + phase) + 0.12F * Mth.sin((float) a * 11.0F - phase * 1.7F);
            float hh = h * swell;
            float cx = (float) Math.cos(a), cz = (float) Math.sin(a);
            float yaw = (float) Math.toDegrees(Math.atan2(cx, cz)) + 90.0F; // panneau tangent : son axe z suit l'anneau
            // base large et sombre
            cube(pose, col, false, new float[] {c[0] + cx * (r - thick * 0.4F), c[1] + hh * 0.18F, c[2] + cz * (r - thick * 0.4F)}, yaw, 0.0F, 0.0F,
                    thick * 1.8F, hh * 0.36F, seg, argb(0.55F * alpha, DEEP), light);
            // corps de la vague, légèrement penché vers l'extérieur
            cube(pose, col, false, new float[] {c[0] + cx * (r + curl * 0.3F), c[1] + hh * 0.55F, c[2] + cz * (r + curl * 0.3F)}, yaw, 0.0F,
                    -12.0F * Math.signum(curl + 1.0E-3F), thick, hh * 0.85F, seg, argb(0.62F * alpha, WATER), light);
            // crête d'écume recourbée vers l'extérieur
            cube(pose, col, true, new float[] {c[0] + cx * (r + curl), c[1] + hh * 0.98F, c[2] + cz * (r + curl)}, yaw, 0.0F, -28.0F,
                    thick * 1.25F, Math.max(0.08F, hh * 0.16F), seg * 0.95F, argb(0.8F * alpha, FOAM), light);
        }
    }
}
