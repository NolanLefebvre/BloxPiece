package nolan.bloxpiece.client.goro;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;

/**
 * Foudre BLEUE du Goro Goro no Mi : même construction que les éclairs du Haki des Rois (HakiBoltRenderer : canal sinueux qui
 * crépite, fourches qui se détachent et se divisent, épaisseur organique avec renflements, prismes qui se chevauchent), mais :
 * <ul>
 *   <li>en bleu : coeur blanc-bleu éblouissant, gaine bleu électrique, halo bleu profond (trois nuances de bleu) ;</li>
 *   <li>plus épaisse, et le crépitement change à CHAQUE tick (décharge plus rapide) ;</li>
 *   <li>tracé tendu entre deux points : il part exactement de A et arrive exactement sur B (frappes sur une cible, colonnes
 *       qui touchent le sol), les écarts du canal sont nuls aux deux bouts.</li>
 * </ul>
 * Outils partagés par les rendus des éclairs (GoroBoltRenderer) et de la sphère du Raigo (GoroOrbRenderer).
 */
public final class GoroLightning {

    public static final Identifier TEXTURE = BloxPiece.id("textures/entity/haki_eye_trail.png"); // blanc uni, teinté
    public static final int FULL_BRIGHT = 0xF000F0;
    public static final int CORE = 0xE4F6FF, SHEATH = 0x2E86FF, HAZE = 0x1A4FE0;

    /** Un tracé : points (blocs) et largeur (px) à chaque point. */
    public record Path(List<float[]> points, float[] widths) {
    }

    private GoroLightning() {
    }

    /** Éclair complet de {@code a} à {@code a + d} : canal principal et fourches (tirées avec {@code shape}, crépitement {@code jag}). */
    public static List<Path> bolt(float[] a, float[] d, float rootWidth, float age, int seed, Random shape, Random jag, boolean forks) {
        float length = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        List<Path> paths = new ArrayList<>();
        if (length < 1.0E-3F) {
            return paths;
        }
        float[] dir = {d[0] / length, d[1] / length, d[2] / length};
        int points = Math.max(8, Math.min(42, (int) (length * 2.2F)));
        Path main = channel(a, dir, length, points, rootWidth, 0.07F, age, seed, shape, jag, true);
        paths.add(main);
        if (!forks) {
            return paths;
        }
        int count = 2 + shape.nextInt(3) + (int) Math.min(4, length / 8);
        for (int f = 0; f < count; f++) {
            if (jag.nextFloat() < 0.3F) {
                continue; // les fourches clignotent d'un crépitement à l'autre
            }
            int at = 2 + shape.nextInt(Math.max(1, points - 5));
            float[] start = main.points().get(at);
            float[] forkDir = deviate(dir, 0.45F + shape.nextFloat() * 0.55F, shape);
            float forkLen = Math.min(6.0F, length * (0.18F + shape.nextFloat() * 0.22F));
            Path fork = channel(start, forkDir, forkLen, 8, main.widths()[at] * 0.55F, 0.12F, age, seed + f * 17, shape, jag, false);
            paths.add(fork);
            if (shape.nextFloat() < 0.5F) {
                float[] s2 = fork.points().get(3);
                paths.add(channel(s2, deviate(forkDir, 0.6F, shape), forkLen * 0.5F, 5, fork.widths()[3] * 0.6F, 0.1F, age,
                        seed + f * 31, shape, jag, false));
            }
        }
        return paths;
    }

    /**
     * Un canal : de {@code start}, sur {@code length} blocs dans {@code dir}. Écart latéral = ondulation animée + zigzags du
     * crépitement ; {@code tied} : nul aux DEUX bouts (il arrive exactement au point visé), sinon nul seulement au départ.
     */
    public static Path channel(float[] start, float[] dir, float length, int count, float rootWidth, float sway, float age, int seed,
                               Random shape, Random jag, boolean tied) {
        return channel(start, dir, length, count, rootWidth, sway, age, seed, shape, jag, tied, 0.06F);
    }

    /** Canal avec amplitude de crépitement {@code jitter} réglable (0,06 : foudre du Goro). Utilisé par le système commun Electric. */
    public static Path channel(float[] start, float[] dir, float length, int count, float rootWidth, float sway, float age, int seed,
                               Random shape, Random jag, boolean tied, float jitter) {
        float[][] basis = perpendiculars(dir);
        float[] u = basis[0], v = basis[1];
        float p1 = shape.nextFloat() * 6.28F, p2 = shape.nextFloat() * 6.28F;
        float f1 = 1.3F + shape.nextFloat(), f2 = 2.6F + shape.nextFloat() * 1.5F;
        List<float[]> points = new ArrayList<>(count);
        float[] widths = new float[count];
        for (int i = 0; i < count; i++) {
            float t = i / (float) (count - 1);
            float envelope = tied ? (float) Math.sin(Math.PI * t) : (float) Math.pow(t, 0.75);
            float waveU = (float) (Math.sin(t * f1 * Math.PI + p1 + age * 1.3F) * 0.6 + Math.sin(t * f2 * Math.PI + p2 - age * 2.2F) * 0.4);
            float waveV = (float) (Math.sin(t * f1 * Math.PI * 0.8 + p2 - age * 1.5F) * 0.6 + Math.sin(t * f2 * Math.PI + p1 + age * 2.6F) * 0.4);
            boolean end = i == 0 || (tied && i == count - 1);
            float jagU = end ? 0.0F : (jag.nextFloat() - 0.5F) * 2.0F;
            float jagV = end ? 0.0F : (jag.nextFloat() - 0.5F) * 2.0F;
            float scale = Math.min(length, 14.0F); // les longues colonnes ne s'écartent pas démesurément
            float offU = (waveU * sway + jagU * jitter) * scale * envelope;
            float offV = (waveV * sway + jagV * jitter) * scale * envelope;
            float along = t * length;
            float[] p = new float[3];
            for (int k = 0; k < 3; k++) {
                p[k] = start[k] + dir[k] * along + u[k] * offU + v[k] * offV;
            }
            points.add(p);
            float taper = tied ? 1.0F - 0.45F * t : 1.0F - 0.8F * t;
            float bulge = 0.75F + 0.5F * (float) Math.abs(Math.sin(t * 9.0F + seed + age * 0.8F));
            widths[i] = rootWidth * taper * bulge;
        }
        return new Path(points, widths);
    }

    /**
     * Dessine des tracés en trois nuances de bleu : coeur blanc-bleu opaque, gaine bleu électrique translucide, halo bleu
     * profond sur le canal principal (le premier tracé).
     */
    public static void draw(ModelPart segment, PoseStack pose, SubmitNodeCollector collector, List<Path> paths, float scale,
                            float intensity, float fade) {
        if (paths.isEmpty()) {
            return;
        }
        RenderType glow = RenderTypes.entityTranslucent(TEXTURE);
        RenderType core = RenderTypes.entityCutout(TEXTURE);
        for (Path p : paths) {
            drawPath(segment, pose, collector, 0, core, p, 1.5F * scale * (0.35F + 0.65F * fade), argb(1.0F, CORE));
        }
        for (Path p : paths) {
            drawPath(segment, pose, collector, 1, glow, p, 3.0F * scale, argb(Math.min(1.0F, 0.5F * intensity), SHEATH));
        }
        drawPath(segment, pose, collector, 2, glow, paths.get(0), 6.5F * scale, argb(0.16F * intensity, HAZE));
    }

    public static void drawPath(ModelPart segment, PoseStack pose, SubmitNodeCollector collector, int order, RenderType type, Path path,
                                float scale, int color) {
        List<float[]> pts = path.points();
        for (int i = 0; i < pts.size() - 1; i++) {
            float width = (path.widths()[i] + path.widths()[i + 1]) * 0.5F * scale;
            segment(segment, pose, collector, order, type, pts.get(i), pts.get(i + 1), width, color);
        }
    }

    /** Prisme de section {@code width} (px) de a vers b (blocs), prolongé d'une demi-largeur aux deux bouts (jointures). */
    public static void segment(ModelPart segment, PoseStack pose, SubmitNodeCollector collector, int order, RenderType type, float[] a,
                               float[] b, float width, int color) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5F || width <= 0.0F) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float overlap = width * 0.5F / 16.0F;
        pose.pushPose();
        pose.translate(a[0], a[1], a[2]);
        pose.rotateDegrees(Axis.YP, yaw);
        pose.rotateDegrees(Axis.XP, pitch);
        pose.translate(0.0F, 0.0F, -overlap);
        pose.scale(width, width, (len + 2.0F * overlap) * 16.0F);
        collector.order(order).submitModelPart(segment, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    /** Point du tracé à l'indice (fractionnaire) {@code index}. */
    public static float[] pointAt(Path path, float index) {
        List<float[]> pts = path.points();
        int i = Math.max(0, Math.min(pts.size() - 2, (int) Math.floor(index)));
        float f = Math.max(0.0F, Math.min(1.0F, index - i));
        float[] a = pts.get(i), b = pts.get(i + 1);
        return new float[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }

    /** Le début du tracé, jusqu'à l'indice (fractionnaire) {@code index} (pointe interpolée). */
    public static Path truncate(Path path, float index) {
        List<float[]> pts = path.points();
        int whole = Math.max(0, Math.min(pts.size() - 1, (int) Math.floor(index)));
        List<float[]> out = new ArrayList<>(whole + 2);
        float[] widths = new float[whole + 2];
        for (int i = 0; i <= whole; i++) {
            out.add(pts.get(i));
            widths[i] = path.widths()[i];
        }
        out.add(pointAt(path, index));
        widths[whole + 1] = path.widths()[Math.min(pts.size() - 1, whole + 1)];
        return new Path(out, widths);
    }

    /** Indice du point du tracé le plus proche de {@code p}. */
    public static int nearestIndex(Path path, float[] p) {
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < path.points().size(); i++) {
            float[] q = path.points().get(i);
            float d = (q[0] - p[0]) * (q[0] - p[0]) + (q[1] - p[1]) * (q[1] - p[1]) + (q[2] - p[2]) * (q[2] - p[2]);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    public static float[] deviate(float[] base, float angle, Random random) {
        float[][] basis = perpendiculars(base);
        double t = random.nextDouble() * Math.PI * 2;
        double c = Math.cos(angle), s = Math.sin(angle);
        float[] d = new float[3];
        for (int i = 0; i < 3; i++) {
            d[i] = (float) (base[i] * c + (basis[0][i] * Math.cos(t) + basis[1][i] * Math.sin(t)) * s);
        }
        return d;
    }

    public static float[][] perpendiculars(float[] d) {
        float[] ref = Math.abs(d[1]) > 0.9F ? new float[] {1.0F, 0.0F, 0.0F} : new float[] {0.0F, 1.0F, 0.0F};
        float[] u = normalize(cross(d, ref));
        float[] v = normalize(cross(d, u));
        return new float[][] {u, v};
    }

    public static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    public static float[] normalize(float[] a) {
        float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return l < 1.0E-6F ? new float[] {1.0F, 0.0F, 0.0F} : new float[] {a[0] / l, a[1] / l, a[2] / l};
    }

    public static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
