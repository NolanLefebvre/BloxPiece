package nolan.bloxpiece.client.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

import nolan.bloxpiece.client.goro.GoroLightning;

/**
 * SYSTÈME ÉLECTRIQUE COMMUN de BloxPiece. Toute capacité qui a besoin d'éclairs (races, fruits, futures capacités) passe par
 * ici plutôt que de dessiner ses propres zigzags. La forme vient de la foudre du Goro Goro no Mi (GoroLightning, même
 * construction que les éclairs du Haki des Rois) : canal sinueux animé, crépitement, épaisseur organique avec renflements,
 * fourches qui se détachent, prismes « segment » qui se chevauchent.
 * <p>
 * Deux réglages, indépendants du rendu :
 * <ul>
 *   <li>{@link Palette} : la COULEUR (coeur, gaine, halo). Changer la palette ne change ni la forme ni l'animation ;</li>
 *   <li>{@link Style} : la FORME selon l'emploi (grand éclair à fourches, petit arc sur un membre, fil qui suit un tracé),
 *       l'épaisseur des trois couches, leur intensité, l'amplitude et la FRÉQUENCE du crépitement.</li>
 * </ul>
 * La durée et la fréquence d'apparition restent décidées par l'effet appelant (il choisit quand et combien en dessiner) ;
 * l'intensité passe par {@code alpha}. Tout est calculé côté client à partir de l'état synchronisé : identique pour tous les
 * joueurs, en première comme en troisième personne.
 * <p>
 * Exemple : {@code electric.bolt(pose, col, Palette.MINKS, Style.ARC, from, to, 0.4F, age, seed, 0.9F)}.
 */
public final class Electric {

    /** Couleurs d'un éclair : coeur (presque blanc), gaine (couleur d'identité), halo (nuance profonde). */
    public record Palette(int core, int sheath, int haze) {
        /** Goro Goro no Mi : la foudre bleue d'origine. */
        public static final Palette GORO = new Palette(GoroLightning.CORE, GoroLightning.SHEATH, GoroLightning.HAZE);
        /** Minks : bleu électrique clair, plus lumineux que le Goro. */
        public static final Palette MINKS = new Palette(0xF2FBFF, 0x4CC3FF, 0x2A6BFF);
        /** Cyborg : l'énergie jaune-orangée de ses capacités. */
        public static final Palette CYBORG = new Palette(0xFFF8D0, 0xFFE24A, 0xFF8A2A);
    }

    /**
     * Forme et animation d'un éclair.
     *
     * @param core       épaisseur du coeur (multiplie la largeur donnée)
     * @param sheath     épaisseur de la gaine
     * @param haze       épaisseur du halo
     * @param sheathA    intensité de la gaine (multiplie alpha)
     * @param hazeA      intensité du halo
     * @param sway       ondulation lente du canal (part de la longueur)
     * @param jitter     amplitude du crépitement (part de la longueur)
     * @param rate       fréquence du crépitement (nouveaux zigzags par tick)
     * @param density    points du canal par bloc
     * @param minPoints  points au minimum (forme lisible même très court)
     * @param forks      fourches qui se détachent (grands éclairs)
     * @param hazeAll    halo sur tous les tracés (sinon seulement sur le canal principal)
     */
    public record Style(float core, float sheath, float haze, float sheathA, float hazeA, float sway, float jitter, float rate,
                        float density, int minPoints, boolean forks, boolean hazeAll) {
        /** Grand éclair à fourches : la foudre du Goro telle quelle (frappes, éclairs du ciel). */
        public static final Style STRIKE = new Style(1.4F, 2.8F, 6.0F, 0.5F, 0.16F, 0.07F, 0.06F, 2.0F, 2.2F, 8, true, false);
        /** Éclair sans fourche, même canal (trajets, éclairs au sol). */
        public static final Style BOLT = new Style(1.4F, 2.8F, 6.0F, 0.5F, 0.16F, 0.07F, 0.06F, 2.0F, 2.2F, 8, false, false);
        /** Petit arc qui saute entre deux points proches (membres, gants, griffes) : crépitement plus marqué. */
        public static final Style ARC = new Style(1.0F, 2.6F, 5.2F, 0.48F, 0.14F, 0.1F, 0.16F, 1.0F, 9.0F, 5, false, true);
        /** Fil électrique qui suit un tracé donné (anneaux, fronts d'onde, impulsions le long d'un membre). */
        public static final Style TRACE = new Style(1.0F, 2.7F, 5.0F, 0.42F, 0.1F, 0.04F, 0.14F, 1.0F, 6.0F, 2, false, true);
        /**
         * Petit éclair ramifié, la foudre du Goro en miniature (aura du Sulong, éclairs autour du corps) : canal plus sinueux,
         * crépitement vif, fourches courtes ; lisible même à moins d'un bloc.
         */
        public static final Style SPARK = new Style(1.2F, 2.8F, 5.5F, 0.5F, 0.14F, 0.12F, 0.11F, 1.5F, 11.0F, 7, true, false);

        public Style withForks(boolean f) {
            return new Style(core, sheath, haze, sheathA, hazeA, sway, jitter, rate, density, minPoints, f, hazeAll);
        }

        public Style withRate(float r) {
            return new Style(core, sheath, haze, sheathA, hazeA, sway, jitter, r, density, minPoints, forks, hazeAll);
        }

        public Style withLayers(float c, float s, float h) {
            return new Style(c, s, h, sheathA, hazeA, sway, jitter, rate, density, minPoints, forks, hazeAll);
        }

        public Style withIntensity(float s, float h) {
            return new Style(core, sheath, haze, s, h, sway, jitter, rate, density, minPoints, forks, hazeAll);
        }
    }

    private final ModelPart segment;

    /** @param segment le prisme « segment » (HakiBoltRenderer.LAYER), déjà cuit par le rendu appelant */
    public Electric(ModelPart segment) {
        this.segment = segment;
    }

    private static RenderType glow() {
        return RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
    }

    private static int argb(float alpha, int rgb) {
        return GoroLightning.argb(Mth.clamp(alpha, 0.0F, 1.0F), rgb);
    }

    /** Éclair tendu de {@code from} à {@code to} (il part et arrive exactement sur ces points). */
    public void bolt(PoseStack pose, SubmitNodeCollector col, Palette pal, Style st, float[] from, float[] to, float width, float age,
                     int seed, float alpha) {
        if (alpha <= 0.01F || width <= 0.0F) {
            return;
        }
        float[] d = {to[0] - from[0], to[1] - from[1], to[2] - from[2]};
        float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        if (len < 1.0E-3F) {
            return;
        }
        Random shape = new Random(seed), jag = new Random(seed * 31L + (long) Math.floor(age * st.rate()));
        float[] dir = {d[0] / len, d[1] / len, d[2] / len};
        int n = Mth.clamp((int) (len * st.density()), st.minPoints(), 42);
        List<GoroLightning.Path> paths = new ArrayList<>();
        GoroLightning.Path main = GoroLightning.channel(from, dir, len, n, width, st.sway(), age, seed, shape, jag, true, st.jitter());
        paths.add(main);
        if (st.forks() && n >= 6) { // fourches : même logique que la foudre du Goro (elles clignotent, certaines se divisent)
            int count = 2 + shape.nextInt(3) + (int) Math.min(4, len / 8);
            for (int f = 0; f < count; f++) {
                if (jag.nextFloat() < 0.3F) {
                    continue;
                }
                int at = 2 + shape.nextInt(Math.max(1, n - 5));
                float[] start = main.points().get(at);
                float[] forkDir = GoroLightning.deviate(dir, 0.45F + shape.nextFloat() * 0.55F, shape);
                float forkLen = Math.min(6.0F, len * (0.18F + shape.nextFloat() * 0.22F));
                GoroLightning.Path fork = GoroLightning.channel(start, forkDir, forkLen, 8, main.widths()[at] * 0.55F, st.sway() * 1.7F, age,
                        seed + f * 17, shape, jag, false, st.jitter());
                paths.add(fork);
                if (shape.nextFloat() < 0.5F) {
                    paths.add(GoroLightning.channel(fork.points().get(3), GoroLightning.deviate(forkDir, 0.6F, shape), forkLen * 0.5F, 5,
                            fork.widths()[3] * 0.6F, st.sway() * 1.4F, age, seed + f * 31, shape, jag, false, st.jitter()));
                }
            }
        }
        draw(pose, col, pal, st, paths, alpha);
    }

    /**
     * Fil électrique qui passe par {@code pts} (tracé ouvert) : chaque tronçon est un canal crépitant tendu entre deux points,
     * l'épaisseur varie (renflements) et s'effile aux deux bouts. Pour un anneau, répéter le premier point à la fin.
     */
    public void trace(PoseStack pose, SubmitNodeCollector col, Palette pal, Style st, List<float[]> pts, float width, float age, int seed,
                      float alpha) {
        if (alpha <= 0.01F || width <= 0.0F || pts.size() < 2) {
            return;
        }
        Random shape = new Random(seed), jag = new Random(seed * 31L + (long) Math.floor(age * st.rate()));
        List<float[]> out = new ArrayList<>();
        out.add(pts.get(0));
        for (int i = 0; i < pts.size() - 1; i++) {
            float[] a = pts.get(i), b = pts.get(i + 1);
            float[] d = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
            float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            if (len < 1.0E-4F) {
                continue;
            }
            int n = Mth.clamp((int) (len * st.density()) + 1, Math.max(2, st.minPoints()), 12);
            GoroLightning.Path c = GoroLightning.channel(a, new float[] {d[0] / len, d[1] / len, d[2] / len}, len, n, width, st.sway(), age,
                    seed + i, shape, jag, true, st.jitter());
            for (int k = 1; k < c.points().size(); k++) {
                out.add(c.points().get(k));
            }
        }
        float[] widths = new float[out.size()];
        for (int i = 0; i < widths.length; i++) {
            float t = widths.length > 1 ? i / (float) (widths.length - 1) : 0.5F;
            float ends = 0.55F + 0.45F * (float) Math.sin(Math.PI * t);
            float bulge = 0.75F + 0.5F * (float) Math.abs(Math.sin(i * 1.7F + seed + age * 0.8F));
            widths[i] = width * ends * bulge;
        }
        draw(pose, col, pal, st, List.of(new GoroLightning.Path(out, widths)), alpha);
    }

    /** Fil électrique sur une suite de points, coupé là où {@code pts} contient null (anneaux brisés, fronts d'onde). */
    public void broken(PoseStack pose, SubmitNodeCollector col, Palette pal, Style st, List<float[]> pts, float width, float age, int seed,
                       float alpha) {
        List<float[]> run = new ArrayList<>();
        int part = 0;
        for (float[] p : pts) {
            if (p == null) {
                trace(pose, col, pal, st, run, width, age, seed + 13 * part++, alpha);
                run = new ArrayList<>();
            } else {
                run.add(p);
            }
        }
        trace(pose, col, pal, st, run, width, age, seed + 13 * part, alpha);
    }

    /** Les trois couches d'un ensemble de tracés : coeur clair, gaine colorée translucide, halo profond très léger. */
    public void draw(PoseStack pose, SubmitNodeCollector col, Palette pal, Style st, List<GoroLightning.Path> paths, float alpha) {
        if (paths.isEmpty()) {
            return;
        }
        RenderType glow = glow();
        for (GoroLightning.Path p : paths) {
            GoroLightning.drawPath(this.segment, pose, col, 0, glow, p, st.core(), argb(0.95F * alpha, pal.core()));
        }
        for (GoroLightning.Path p : paths) {
            GoroLightning.drawPath(this.segment, pose, col, 1, glow, p, st.sheath(), argb(st.sheathA() * alpha, pal.sheath()));
        }
        for (int i = 0; i < (st.hazeAll() ? paths.size() : 1); i++) {
            GoroLightning.drawPath(this.segment, pose, col, 2, glow, paths.get(i), st.haze(), argb(st.hazeA() * alpha, pal.haze()));
        }
    }
}
