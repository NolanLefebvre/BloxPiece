package nolan.bloxpiece.client.minks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.client.fx.Electric;
import nolan.bloxpiece.client.goro.GoroLightning;
import nolan.bloxpiece.client.haki.HakiBoltRenderer;
import nolan.bloxpiece.client.human.HumanDraw;
import nolan.bloxpiece.fx.MinksFx;

import static nolan.bloxpiece.client.minks.MinksLayer.CORE;
import static nolan.bloxpiece.client.minks.MinksLayer.HAZE;
import static nolan.bloxpiece.client.minks.MinksLayer.SHEATH;

/**
 * Effets 3D de la race Minks (MinksFx). L'électricité des Minks (coeur blanc, gaine jaune, halo bleu pâle) reprend la
 * construction des éclairs du Goro (GoroLightning : canal sinueux, fourches, épaisseur organique), mais suit les GRIFFES et les
 * BONDS : rien n'est lancé comme un projectile.
 * <ul>
 *   <li>IMPACT : TROIS GRIFFURES fines, courbes et effilées qui se tracent puis s'effacent, quelques arcs fins, onde légère ;
 *       deux frappes successives se posent au même endroit en diagonales opposées : marque en X (Minks.slash) ;</li>
 *   <li>BARRAGE_HIT : les trois griffures, plus courtes et plus brèves, en diagonale alternée ;</li>
 *   <li>BLINK : l'éclair qui relie l'ancienne position à la nouvelle (il s'amincit et s'éteint), la silhouette électrique qui
 *       reste un instant à l'ancienne place, l'air chassé au départ, la poussière d'appui, des stries de vitesse ;</li>
 *   <li>FINAL : la marque en X complète (deux séries de trois griffures croisées, la seconde un instant après), éclairs qui courent au sol, ondes de choc ;</li>
 *   <li>SULONG : éclairs qui tombent du ciel autour du joueur, dôme d'arcs, ondes au sol, rayons.</li>
 * </ul>
 */
public class MinksFxRenderer extends EntityRenderer<MinksFx, MinksFxRenderer.State> {

    public static class State extends EntityRenderState {
        public float age, size = 1.0F;
        public int kind, life = 12, seed;
        public float[] reach = {0, 0, 1};
        /** Diagonale des griffures (alternée à chaque frappe par le serveur). */
        public boolean flip;
    }

    private final ModelPart segment;
    private final HumanDraw draw;
    /** Éclairs : système électrique commun (forme du Goro), palette des Minks. */
    private final Electric electric;

    public MinksFxRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.segment = context.bakeLayer(HakiBoltRenderer.LAYER).getChild("segment");
        this.electric = new Electric(this.segment);
        this.draw = new HumanDraw(context); // (poussière, anneaux : outils d'effets partagés du mod)
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MinksFx entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.kind = entity.kind();
        state.size = entity.size();
        state.life = Math.max(4, entity.life());
        state.reach = entity.reach();
        state.seed = entity.getId();
        state.flip = entity.flipped();
    }

    private static int a(float alpha, int rgb) {
        return GoroLightning.argb(Mth.clamp(alpha, 0.0F, 1.0F), rgb);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        switch (state.kind) {
            case MinksFx.IMPACT -> impact(state, pose, collector, false);
            case MinksFx.BARRAGE_HIT -> impact(state, pose, collector, true);
            case MinksFx.BLINK -> blink(state, pose, collector);
            case MinksFx.FINAL -> fin(state, pose, collector);
            case MinksFx.SULONG -> sulong(state, pose, collector);
            case MinksFx.CRASH -> crash(state, pose, collector);
            default -> {
            }
        }
        super.submit(state, pose, collector, camera);
    }

    private static float[] dir(State s) {
        float[] d = s.reach;
        float l = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        return l < 1.0E-4F ? new float[] {0, 0, 1} : new float[] {d[0] / l, d[1] / l, d[2] / l};
    }

    // ------------------------------------------------------------------ outils électriques

    /** Éclair (canal sinueux et fourches du Goro) de {@code a} vers {@code a + d}, aux couleurs des Minks. */
    private void bolt(PoseStack pose, SubmitNodeCollector col, float[] a, float[] d, float width, float age, int seed, float alpha, boolean forks) {
        this.electric.bolt(pose, col, Electric.Palette.MINKS, forks ? Electric.Style.STRIKE : Electric.Style.BOLT, a,
                new float[] {a[0] + d[0], a[1] + d[1], a[2] + d[2]}, width, age, seed, alpha);
    }

    /** Petit arc électrique (système commun) de {@code from} à {@code to}. */
    private void arc(PoseStack pose, SubmitNodeCollector col, float[] from, float[] to, float width, float age, int seed, float alpha) {
        this.electric.bolt(pose, col, Electric.Palette.MINKS, Electric.Style.ARC, from, to, width, age, seed, alpha);
    }

    /** Fil électrique (système commun) le long de points, coupé aux null. */
    private void wire(PoseStack pose, SubmitNodeCollector col, List<float[]> pts, float width, float age, int seed, float alpha) {
        this.electric.broken(pose, col, Electric.Palette.MINKS, Electric.Style.TRACE, pts, width, age, seed, alpha);
    }

    /**
     * TROIS GRIFFURES (les trois griffes du gant) : traces fines, légèrement courbes, effilées aux deux bouts (épaisseur maximale
     * au premier tiers, comme une vraie marque de griffe), dans le plan (u, w) et orientées selon {@code angle}. La griffure du
     * milieu est la plus longue et part un peu avant les deux autres. Animation : la trace se TRACE en {@code DRAW} ticks dans
     * le sens du coup, tient un instant, puis s'efface depuis sa queue en s'amincissant. Trois nuances de bleu des gants : un fil
     * blanc bleuté, une gaine bleu électrique, un halo bleu intense très léger ; de petits éclats électriques fins scintillent le
     * long des traces et aux pointes.
     *
     * @param len       longueur de la griffure du milieu (blocs)
     * @param thick     épaisseur du fil central (px, au plus épais)
     * @param intensity luminosité (coup final : un peu plus)
     */
    private void rake(PoseStack pose, SubmitNodeCollector col, float[] c, float[] u, float[] w, float len, float gap, float thick, float age,
                      float life, double angle, float intensity, long seed) {
        float draw = 1.6F;
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        float[] along = {u[0] * ca + w[0] * sa, u[1] * ca + w[1] * sa, u[2] * ca + w[2] * sa};
        float[] across = {-u[0] * sa + w[0] * ca, -u[1] * sa + w[1] * ca, -u[2] * sa + w[2] * ca};
        float fadeStart = life * 0.45F;
        float erode = Mth.clamp((age - fadeStart) / (life - fadeStart), 0.0F, 1.0F); // effacement depuis la queue
        float alpha = intensity * (1.0F - erode * erode);
        if (alpha <= 0.01F) {
            return;
        }
        Random r = new Random(seed * 13L + (long) (age * 2.0F));
        int n = 10;
        for (int i = 0; i < 3; i++) {
            float off = (i - 1) * gap;
            float l = len * (i == 1 ? 1.0F : 0.84F);
            float delay = i == 1 ? 0.0F : 0.35F;
            float head = HumanDraw.easeOut(Mth.clamp((age - delay) / draw, 0.0F, 1.0F)); // la pointe avance
            float tail = erode * 0.9F;                                                    // la queue s'efface
            if (head <= tail + 0.02F) {
                continue;
            }
            float bow = 0.09F * l; // courbure : un léger arc, comme un coup de patte
            float[] prev = null;
            float prevW = 0.0F;
            for (int k = 0; k <= n; k++) {
                float t = tail + (head - tail) * k / n;
                float x = (t - 0.5F) * l;
                float y = off + bow * (float) Math.sin(Math.PI * t) - bow * 0.5F;
                float[] p = {c[0] + along[0] * x + across[0] * y, c[1] + along[1] * x + across[1] * y, c[2] + along[2] * x + across[2] * y};
                // épaisseur : effilée aux deux bouts, plus épaisse au premier tiers
                float profile = (float) Math.pow(Math.sin(Math.PI * Math.pow(t, 0.8)), 0.7);
                float width = thick * profile * (1.0F - 0.5F * erode);
                if (prev != null) {
                    float wd = (width + prevW) * 0.5F;
                    this.draw.seg(pose, col, 0, prev, p, wd, a(alpha, CORE));
                    this.draw.seg(pose, col, 1, prev, p, wd * 2.1F + 0.2F, a(0.5F * alpha, SHEATH));
                    this.draw.seg(pose, col, 2, prev, p, wd * 3.6F + 0.3F, a(0.12F * alpha, HAZE));
                }
                prev = p;
                prevW = width;
            }
            // petits éclats électriques fins qui scintillent le long de la trace et à la pointe
            for (int sp = 0; sp < 2; sp++) {
                if (r.nextFloat() > 0.55F) {
                    continue;
                }
                float t = tail + (head - tail) * (sp == 0 ? r.nextFloat() : 1.0F);
                float x = (t - 0.5F) * l, y = off + bow * (float) Math.sin(Math.PI * t) - bow * 0.5F;
                float[] p = {c[0] + along[0] * x + across[0] * y, c[1] + along[1] * x + across[1] * y, c[2] + along[2] * x + across[2] * y};
                float s1 = (r.nextFloat() - 0.5F) * 0.12F, s2 = (r.nextFloat() - 0.5F) * 0.12F;
                float[] m = {p[0] + across[0] * s1 + along[0] * 0.05F, p[1] + across[1] * s1 + along[1] * 0.05F, p[2] + across[2] * s1 + along[2] * 0.05F};
                float[] q = {m[0] + across[0] * s2 + along[0] * 0.06F, m[1] + across[1] * s2 + along[1] * 0.06F, m[2] + across[2] * s2 + along[2] * 0.06F};
                arc(pose, col, p, q, 0.3F, age, r.nextInt(), 0.85F * alpha);
            }
        }
    }

    // ------------------------------------------------------------------ coups

    /** Inclinaison des griffures : diagonale droite (environ 45 degrés) ou son miroir, la gauche. */
    private static double diagonal(State s) {
        double diag = Math.toRadians(48.0);
        return s.flip ? Math.PI - diag : diag;
    }

    /**
     * Coup de griffes (Griffes électriques) ou coup du barrage : les trois griffures au point touché, quelques arcs fins et une
     * légère onde de pression. Barrage : griffures plus courtes et plus brèves, en diagonale alternée (main gauche / droite).
     */
    private void impact(State s, PoseStack pose, SubmitNodeCollector col, boolean small) {
        float a = s.age, size = s.size;
        float[] d = dir(s);
        float[][] perp = GoroLightning.perpendiculars(d);
        float[] o = {0, 0, 0};
        float out = Mth.clamp((s.life - a) / 4.0F, 0.0F, 1.0F);
        Random r = new Random(s.seed * 7L);
        // orientation : diagonale droite ou gauche, ALTERNÉE à chaque frappe (choisie par le serveur, la même pour tous)
        double angle = diagonal(s);
        rake(pose, col, o, perp[0], perp[1], (small ? 0.9F : 0.95F) * size, 0.12F * size, small ? 0.85F : 0.65F, a, s.life,
                angle, 0.95F, s.seed);
        // quelques arcs fins qui jaillissent du point touché (brefs)
        if (a < 4.0F) {
            Random ar = new Random(s.seed * 11L + (long) a);
            for (int i = 0; i < (small ? 1 : 3); i++) {
                float[] dd = GoroLightning.normalize(new float[] {ar.nextFloat() - 0.5F, ar.nextFloat() - 0.5F, ar.nextFloat() - 0.5F});
                float l = (0.2F + ar.nextFloat() * 0.25F) * size;
                arc(pose, col, o, HumanDraw.add(o, dd, l), 0.35F, a, ar.nextInt(), 0.9F * out);
            }
        }
        // légère onde de pression (fine)
        float k = Mth.clamp(a / (small ? 4.0F : 6.0F), 0.0F, 1.0F);
        if (k < 1.0F) {
            draw.ring(pose, col, 2, HumanDraw.add(o, d, 0.05F * k), perp[0], perp[1], (0.12F + (small ? 0.3F : 0.5F) * HumanDraw.easeOut(k)) * size,
                    0.7F * (1.0F - k) + 0.2F, a(0.4F * (1.0F - k), HAZE), 22);
        }
    }

    /** Bond éclair : éclair entre les deux positions, silhouette électrique laissée derrière, air chassé, poussière. */
    private void blink(State s, PoseStack pose, SubmitNodeCollector col) {
        float a = s.age, size = s.size;
        float[] d = s.reach;
        float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        float out = Mth.clamp((s.life - a) / 8.0F, 0.0F, 1.0F);
        // 1. l'éclair qui relie l'ancienne position à la nouvelle (à hauteur de buste), qui s'amincit
        if (len > 0.3F && a < s.life - 2) {
            bolt(pose, col, new float[] {0, 1.0F, 0}, d, (0.8F + 0.3F * size) * out, a, s.seed, 0.9F * out, false); // fin : il indique le trajet sans le masquer
            // stries de vitesse parallèles au bond
            Random r = new Random(s.seed * 3L);
            float[] dn = {d[0] / len, d[1] / len, d[2] / len};
            float[][] perp = GoroLightning.perpendiculars(dn);
            for (int i = 0; i < 6; i++) {
                float[] off = HumanDraw.onCircle(new float[] {0, 1.0F, 0}, perp[0], perp[1], 0.25F + r.nextFloat() * 0.45F, r.nextDouble() * Math.PI * 2);
                float t0 = r.nextFloat() * 0.5F;
                draw.seg(pose, col, 2, HumanDraw.add(off, d, t0), HumanDraw.add(off, d, t0 + 0.45F), 0.4F, a(0.45F * out, i % 2 == 0 ? CORE : HAZE));
            }
        }
        // 2. silhouette électrique qui reste un instant à l'ancienne place (arcs sur la forme d'un corps)
        float ghost = Mth.clamp(1.0F - a / 6.0F, 0.0F, 1.0F); // trace brève : pas d'accumulation
        if (ghost > 0.0F) {
            float[][] body = {{0, 1.85F, 0}, {0, 1.45F, 0}, {-0.35F, 1.4F, 0}, {0.35F, 1.4F, 0}, {-0.4F, 0.75F, 0}, {0.4F, 0.75F, 0},
                    {0, 0.75F, 0}, {-0.15F, 0.05F, 0}, {0.15F, 0.05F, 0}};
            int[][] links = {{0, 1}, {1, 2}, {1, 3}, {2, 4}, {3, 5}, {1, 6}, {6, 7}, {6, 8}};
            Random jr = new Random(s.seed * 5L + (long) (a * 1.5F));
            for (int[] l : links) {
                float[] p = body[l[0]], q = body[l[1]];
                arc(pose, col, p, q, 0.5F, a, jr.nextInt(), 0.85F * ghost);
            }
        }
        // 3. air chassé au départ (anneau au ras du sol) et poussière d'appui
        float k = Mth.clamp(a / 8.0F, 0.0F, 1.0F);
        if (k < 1.0F) {
            draw.flatRing(pose, col, 2, new float[] {0, 0.08F, 0}, 0.3F + 1.2F * HumanDraw.easeOut(k), 1.4F * (1.0F - k) + 0.3F, a(0.5F * (1.0F - k), HAZE), 20);
        }
        float kd = Mth.clamp(a / 14.0F, 0.0F, 1.0F);
        for (int i = 0; i < 3; i++) {
            double ang = i * 2.1 + s.seed;
            draw.dust(pose, col, new float[] {(float) Math.cos(ang) * 0.35F * (1.0F + kd), 0.2F + 0.3F * kd, (float) Math.sin(ang) * 0.35F * (1.0F + kd)},
                    i * 60.0F + a * 3.0F, 0.25F + 0.35F * kd, 0.5F * (1.0F - kd), HumanDraw.DUST, s.lightCoords);
        }
    }

    /** Dernier coup : déchirure en X, éclairs qui courent au sol, ondes de choc, poussière. */
    private void fin(State s, PoseStack pose, SubmitNodeCollector col) {
        float a = s.age, size = s.size;
        float[] d = dir(s);
        float[][] perp = GoroLightning.perpendiculars(d);
        float[] o = {0, 0, 0};
        float out = Mth.clamp((s.life - a) / 8.0F, 0.0F, 1.0F);
        // coup final : la marque en X complète : une série de trois griffures, puis la série croisée un instant après
        rake(pose, col, o, perp[0], perp[1], 0.9F * size, 0.12F * size, 0.85F, a, s.life, diagonal(s), 1.0F, s.seed);
        if (a > 2.5F) {
            rake(pose, col, HumanDraw.add(o, d, -0.01F), perp[0], perp[1], 0.9F * size, 0.12F * size, 0.85F, a - 2.5F, s.life - 2.5F,
                    Math.PI - diagonal(s), 1.0F, s.seed + 1);
        }
        // éclairs qui courent au sol depuis le point de frappe
        if (a < s.life - 4) {
            for (int i = 0; i < 6; i++) {
                double ang = i * Math.PI / 3 + s.seed;
                float l = (1.5F + 1.2F * size) * HumanDraw.easeOut(Math.min(1.0F, a / 4.0F));
                bolt(pose, col, new float[] {0, -0.9F, 0}, new float[] {(float) Math.cos(ang) * l, -0.1F, (float) Math.sin(ang) * l}, 1.6F * out, a,
                        s.seed + i * 13, 0.9F * out, false);
            }
        }
        // ondes de choc
        for (int i = 0; i < 2; i++) {
            float ar = a - i * 3.0F;
            float k = Mth.clamp(ar / 14.0F, 0.0F, 1.0F);
            if (ar > 0.0F && k < 1.0F) {
                draw.ring(pose, col, 2, o, perp[0], perp[1], (0.4F + 2.2F * HumanDraw.easeOut(k)) * size, 2.4F * (1.0F - k) + 0.4F,
                        a(0.7F * (1.0F - k), i == 0 ? CORE : HAZE), 30);
                draw.flatRing(pose, col, 2, new float[] {0, -0.85F, 0}, (0.5F + 3.0F * HumanDraw.easeOut(k)) * size, 2.0F * (1.0F - k) + 0.3F,
                        a(0.6F * (1.0F - k), SHEATH), 32);
            }
        }
        float kd = Mth.clamp(a / 20.0F, 0.0F, 1.0F);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            float r = (0.5F + 2.0F * HumanDraw.easeOut(kd)) * size;
            draw.dust(pose, col, new float[] {(float) Math.cos(ang) * r, -0.7F + 0.5F * kd, (float) Math.sin(ang) * r}, i * 45.0F + a * 3.0F,
                    0.4F + 0.5F * kd, 0.6F * (1.0F - kd), i % 2 == 0 ? HumanDraw.DUST : HumanDraw.DUST_DARK, s.lightCoords);
        }
    }

    /** Transformation Sulong : éclairs qui tombent du ciel autour du joueur, dôme d'arcs, ondes au sol, rayons. */
    private void sulong(State s, PoseStack pose, SubmitNodeCollector col) {
        float a = s.age;
        float out = Mth.clamp((s.life - a) / 10.0F, 0.0F, 1.0F);
        // 1. éclairs du ciel, en décalé
        for (int i = 0; i < 6; i++) {
            float t = a - i * 1.5F;
            if (t < 0.0F || t > 10.0F) {
                continue;
            }
            double ang = i * Math.PI / 3 + s.seed;
            float r = 1.6F + (i % 2) * 1.2F;
            float[] ground = {(float) Math.cos(ang) * r, 0.0F, (float) Math.sin(ang) * r};
            float fade = 1.0F - t / 10.0F;
            bolt(pose, col, new float[] {ground[0] * 0.4F, 14.0F, ground[2] * 0.4F}, new float[] {ground[0] * 0.6F, -14.0F, ground[2] * 0.6F},
                    4.0F * fade, a, s.seed + i * 17, fade, true);
        }
        // 2. dôme d'arcs autour du joueur (anneaux brisés empilés), qui grandit
        float k = HumanDraw.easeOut(Math.min(1.0F, a / 8.0F));
        Random r = new Random(s.seed * 3L + (long) (a * 0.7F));
        for (int lat = 0; lat < 5; lat++) {
            double th = Math.toRadians(lat * 18.0);
            float rr = (1.2F + 1.8F * k) * (float) Math.cos(th), y = (1.2F + 1.8F * k) * (float) Math.sin(th) + 0.1F;
            List<float[]> pts = new ArrayList<>();
            for (int i = 0; i <= 18; i++) {
                if (r.nextFloat() < 0.2F) {
                    pts.add(null);
                    continue;
                }
                double ang = 2 * Math.PI * i / 18;
                pts.add(new float[] {(float) Math.cos(ang) * (rr + (r.nextFloat() - 0.5F) * 0.2F), y + (r.nextFloat() - 0.5F) * 0.2F,
                        (float) Math.sin(ang) * (rr + (r.nextFloat() - 0.5F) * 0.2F)});
            }
            wire(pose, col, pts, 0.8F, a, s.seed + lat * 19, 0.8F * out);
        }
        // 2b. onde d'énergie : un mur électrique bleu qui part du joueur et s'abaisse en s'éloignant
        float wk = Mth.clamp(a / 18.0F, 0.0F, 1.0F);
        if (wk < 1.0F) {
            draw.wall(pose, col, 2, new float[] {0, 0, 0}, 0.6F + 6.5F * HumanDraw.easeOut(wk), 0.0F, 1.6F * (1.0F - wk) + 0.15F,
                    a(0.35F * (1.0F - wk), SHEATH), a(0.8F * (1.0F - wk), CORE), 44);
        }
        // 2c. éclairs qui courent au sol depuis le joueur, et éclairs qui jaillissent vers le ciel
        if (a < 12.0F) {
            float f = 1.0F - a / 12.0F;
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4 + s.seed * 0.7;
                float l = 1.0F + 4.0F * HumanDraw.easeOut(Math.min(1.0F, a / 5.0F));
                bolt(pose, col, new float[] {0, 0.05F, 0}, new float[] {(float) Math.cos(ang) * l, 0.0F, (float) Math.sin(ang) * l}, 2.0F * f, a,
                        s.seed + i * 11, 0.9F * f, false);
            }
            for (int i = 0; i < 3; i++) {
                double ang = i * 2.1 + s.seed;
                bolt(pose, col, new float[] {0, 1.2F, 0}, new float[] {(float) Math.cos(ang) * 0.8F, 5.0F + 2.0F * i, (float) Math.sin(ang) * 0.8F},
                        3.0F * f, a, s.seed + 50 + i, 0.85F * f, true);
            }
        }
        // 3. ondes au sol et rayons
        for (int i = 0; i < 2; i++) {
            float ar = a - i * 4.0F, kk = Mth.clamp(ar / 16.0F, 0.0F, 1.0F);
            if (ar > 0.0F && kk < 1.0F) {
                draw.flatRing(pose, col, 2, new float[] {0, 0.05F, 0}, 0.5F + 7.0F * HumanDraw.easeOut(kk), 3.0F * (1.0F - kk) + 0.4F,
                        a(0.75F * (1.0F - kk), i == 0 ? CORE : HAZE), 40);
            }
        }
        if (a < 12.0F) {
            float kk = a / 12.0F;
            draw.burst(pose, col, 1, new float[] {0, 1.0F, 0}, new float[] {1, 0, 0}, new float[] {0, 1, 0}, 0.5F, 1.0F + 3.5F * HumanDraw.easeOut(kk),
                    2.2F * (1.0F - kk) + 0.3F, a(0.85F * (1.0F - kk), CORE), 12, s.seed);
            draw.burst(pose, col, 1, new float[] {0, 1.0F, 0}, new float[] {0, 0, 1}, new float[] {0, 1, 0}, 0.5F, 1.0F + 3.0F * HumanDraw.easeOut(kk),
                    2.0F * (1.0F - kk) + 0.3F, a(0.7F * (1.0F - kk), SHEATH), 12, s.seed + 1.0);
        }
    }

    // ------------------------------------------------------------------ Danse des griffes éclair : l'écrasement final

    /**
     * L'ÉCRASEMENT final au centre : une ONDE DE CHOC ÉLECTRIQUE qui se propage à la surface du sol, en trois vagues
     * successives. Chaque vague : un front d'éclairs brisés qui épouse le sol, un mur d'énergie fin et bas (volume), des arcs qui
     * courent sur le terrain juste derrière le front. Au contact : compression (anneaux serrés qui s'écrasent sur le sol, air qui
     * ondule), étoile d'impact, éclairs qui jaillissent du point d'impact ; puis poussière et débris. Les vagues vont un peu
     * au-delà de la zone, s'amincissent et s'éteignent. Traits fins : le joueur et le point d'impact restent visibles.
     */
    private void crash(State s, PoseStack pose, SubmitNodeCollector col) {
        float a = s.age, R = s.size;
        float[] o = {0, 0.05F, 0};
        float reach = R + 0.5F; // l'onde couvre TOUTE la zone (rayon calculé avant l'attaque), et pas plus
        // 1. compression au contact : anneaux serrés écrasés au sol, air qui ondule, étoile d'impact
        if (a < 5.0F) {
            float k = a / 5.0F;
            for (int i = 0; i < 4; i++) {
                draw.flatRing(pose, col, 1, new float[] {0, 0.06F + 0.05F * i * (1.0F - k), 0}, (0.25F + 0.2F * i) * (1.0F + 1.5F * k), 1.4F - 0.25F * i,
                        a(0.85F * (1.0F - k), i % 2 == 0 ? CORE : SHEATH), 24);
            }
            // air qui ondule au-dessus du point d'impact (deux anneaux qui oscillent)
            float wob = 0.12F * Mth.sin(a * 3.0F);
            draw.flatRing(pose, col, 3, new float[] {0, 0.4F, 0}, 0.6F + 1.6F * k + wob, 0.8F, a(0.4F * (1.0F - k), FLASH_BLUE), 28);
            draw.flatRing(pose, col, 3, new float[] {0, 0.7F, 0}, 0.5F + 1.2F * k - wob, 0.6F, a(0.3F * (1.0F - k), FLASH_BLUE), 28);
        }
        if (a < 8.0F) {
            float k = a / 8.0F;
            draw.burst(pose, col, 1, o, new float[] {1, 0, 0}, new float[] {0, 0, 1}, 0.3F, 0.8F + R * 0.4F * HumanDraw.easeOut(k),
                    1.0F * (1.0F - k) + 0.3F, a(0.9F * (1.0F - k), CORE), 16, s.seed);
            for (int i = 0; i < 4; i++) { // quelques éclairs qui jaillissent du point d'impact
                double ang = i * 1.6 + s.seed;
                bolt(pose, col, new float[] {0, 0.2F, 0}, new float[] {(float) Math.cos(ang) * 0.9F, 2.2F + i * 0.5F, (float) Math.sin(ang) * 0.9F},
                        0.9F * (1.0F - k), a, s.seed + 70 + i, 0.8F * (1.0F - k), false);
            }
        }
        // 2. quatre vagues successives qui se propagent au ras du sol jusqu'au bord de la zone (la première, la plus forte)
        for (int wv = 0; wv < 4; wv++) {
            float t = a - wv * 3.5F;
            float life = 16.0F + 0.8F * R;
            if (t <= 0.0F || t > life) {
                continue;
            }
            float k = t / life, e = HumanDraw.easeOut(k);
            float front = 0.4F + reach * e;
            float fade = (1.0F - k * k) * (1.0F - 0.18F * wv);
            // front d'éclairs brisés qui épouse le sol (re-tiré à chaque tick)
            Random r = new Random(s.seed * 31L + wv * 101L + (long) a);
            int n = Math.max(20, (int) (front * 6.0F));
            List<float[]> pts = new ArrayList<>();
            for (int i = 0; i <= n; i++) {
                if (r.nextFloat() < 0.12F) {
                    pts.add(null);
                    continue;
                }
                double ang = 2 * Math.PI * i / n;
                float rr = front + (r.nextFloat() - 0.5F) * 0.25F;
                pts.add(new float[] {(float) Math.cos(ang) * rr, 0.06F + r.nextFloat() * 0.12F, (float) Math.sin(ang) * rr});
            }
            wire(pose, col, pts, (wv == 0 ? 1.4F : 1.0F) * 0.55F, a, s.seed + wv * 101, 0.9F * fade);
            // mur d'énergie fin et bas : le volume de la vague (il s'abaisse en s'éloignant)
            draw.wall(pose, col, 3, new float[] {0, 0, 0}, front * 0.97F, 0.0F, (0.9F - 0.2F * wv) * (1.0F - k) + 0.08F, a(0.22F * fade, SHEATH),
                    a(0.55F * fade, CORE), 48);
            // arcs qui courent sur le terrain juste derrière le front
            for (int i = 0; i < 8; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                float r0 = front * (0.55F + r.nextFloat() * 0.35F), r1 = Math.min(front, r0 + 0.6F + r.nextFloat() * 0.6F);
                float[] p0 = {(float) Math.cos(ang) * r0, 0.05F, (float) Math.sin(ang) * r0};
                float[] p1 = {(float) Math.cos(ang + (r.nextDouble() - 0.5) * 0.3) * r1, 0.05F, (float) Math.sin(ang + (r.nextDouble() - 0.5) * 0.3) * r1};
                arc(pose, col, p0, p1, 0.45F, a, r.nextInt(), 0.8F * fade);
            }
        }
        // 2b. la première vague atteint le bord de la zone : le cercle de la zone s'embrase un instant
        float edgeT = a - (16.0F + 0.8F * R) * 0.8F; // (la vague principale atteint presque le bord à 80 % de sa vie)
        if (edgeT > 0.0F && edgeT < 10.0F) {
            float k = edgeT / 10.0F;
            draw.flatRing(pose, col, 1, new float[] {0, 0.08F, 0}, reach, 1.8F * (1.0F - k) + 0.3F, a(0.85F * (1.0F - k), CORE), 60);
            draw.wall(pose, col, 2, new float[] {0, 0, 0}, reach, 0.0F, 1.2F * (1.0F - k) + 0.1F, a(0.22F * (1.0F - k), SHEATH),
                    a(0.7F * (1.0F - k), CORE), 60);
        }
        // 2c. énergie résiduelle : des arcs courent sur tout le sol de la zone, puis s'éteignent
        float res = Mth.clamp((a - 4.0F) / 4.0F, 0.0F, 1.0F) * (1.0F - Mth.clamp((a - 18.0F) / 14.0F, 0.0F, 1.0F));
        if (res > 0.0F) {
            Random rr = new Random(s.seed * 47L + (long) a);
            int n = 6 + (int) (R * 1.5F);
            for (int i = 0; i < n; i++) {
                double ang = rr.nextDouble() * Math.PI * 2;
                float d0 = (float) Math.sqrt(rr.nextFloat()) * R;
                float[] p0 = {(float) Math.cos(ang) * d0, 0.05F, (float) Math.sin(ang) * d0};
                float[] m = {p0[0] + (rr.nextFloat() - 0.5F) * 0.7F, 0.1F, p0[2] + (rr.nextFloat() - 0.5F) * 0.7F};
                float[] p1 = {m[0] + (rr.nextFloat() - 0.5F) * 0.7F, 0.05F, m[2] + (rr.nextFloat() - 0.5F) * 0.7F};
                wire(pose, col, List.of(p0, m, p1), 0.4F, a, rr.nextInt(), 0.75F * res);
            }
        }
        // 3. sol marqué au centre (cercle d'éclairs qui s'éteint lentement)
        float mark = 1.0F - Mth.clamp((a - 6.0F) / 24.0F, 0.0F, 1.0F);
        if (mark > 0.0F) {
            draw.flatRing(pose, col, 2, o, 1.2F, 1.0F, a(0.5F * mark, SHEATH), 28);
            draw.flatRing(pose, col, 3, o, 0.8F, 2.6F, a(0.15F * mark, HAZE), 24);
        }
        // 4. poussière et débris soulevés par l'onde
        float kd = Mth.clamp(a / 26.0F, 0.0F, 1.0F);
        for (int i = 0; i < 14; i++) {
            double ang = i * Math.PI / 7 + 0.2;
            float rr = 0.6F + reach * 0.8F * HumanDraw.easeOut(kd);
            draw.dust(pose, col, new float[] {(float) Math.cos(ang) * rr, 0.25F + 0.5F * kd, (float) Math.sin(ang) * rr}, i * 37.0F + a * 2.0F,
                    0.45F + 0.6F * kd, 0.55F * (1.0F - kd), i % 3 == 0 ? HumanDraw.DUST_DARK : HumanDraw.DUST, s.lightCoords);
        }
        Random dr = new Random(s.seed * 9L);
        for (int i = 0; i < 16; i++) {
            float vx = (dr.nextFloat() - 0.5F) * 0.55F, vy = 0.25F + dr.nextFloat() * 0.3F, vz = (dr.nextFloat() - 0.5F) * 0.55F;
            float yaw = dr.nextFloat() * 360.0F, sc = 0.25F + dr.nextFloat() * 0.25F;
            float y = 0.1F + vy * a - 0.5F * 0.045F * a * a;
            float tt = y < 0.05F ? Math.max(0.0F, (vy + (float) Math.sqrt(Math.max(0.0F, vy * vy + 2 * 0.045F * 0.05F))) / 0.045F) : a;
            float shrink = 1.0F - Mth.clamp((a - 22.0F) / 8.0F, 0.0F, 1.0F);
            draw.rock(pose, col, new float[] {vx * tt, Math.max(0.05F, y), vz * tt}, a * 20.0F, yaw, a * 14.0F, sc * shrink, i % 2, s.lightCoords);
        }
    }

    /** Blanc bleuté de l'air comprimé. */
    private static final int FLASH_BLUE = 0xDDF4FF;

    @Override
    protected AABB getBoundingBoxForCulling(MinksFx entity, float partialTick) {
        float[] d = entity.reach();
        AABB box = entity.getBoundingBox();
        if (entity.kind() == MinksFx.BLINK) {
            box = box.expandTowards(d[0], d[1], d[2]);
        }
        return box.inflate(entity.kind() == MinksFx.SULONG ? 16.0 : entity.kind() == MinksFx.CRASH
                ? entity.size() + 4.0 : 5.0);
    }
}
