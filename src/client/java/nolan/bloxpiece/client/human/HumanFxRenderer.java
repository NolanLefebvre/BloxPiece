package nolan.bloxpiece.client.human;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.ability.HumanConquerorFistAbility;
import nolan.bloxpiece.ability.HumanDestructiveRushAbility;
import nolan.bloxpiece.ability.HumanEarthBreakerAbility;
import nolan.bloxpiece.client.goro.GoroLightning;
import nolan.bloxpiece.client.haki.HakiBoltRenderer;
import nolan.bloxpiece.fx.HumanFx;

import static nolan.bloxpiece.client.human.HumanDraw.AIR;
import static nolan.bloxpiece.client.human.HumanDraw.AIR_EDGE;
import static nolan.bloxpiece.client.human.HumanDraw.AMBER;
import static nolan.bloxpiece.client.human.HumanDraw.CRACK;
import static nolan.bloxpiece.client.human.HumanDraw.CRACK_GLOW;
import static nolan.bloxpiece.client.human.HumanDraw.DUST;
import static nolan.bloxpiece.client.human.HumanDraw.DUST_DARK;
import static nolan.bloxpiece.client.human.HumanDraw.EMBER;
import static nolan.bloxpiece.client.human.HumanDraw.FLASH;
import static nolan.bloxpiece.client.human.HumanDraw.GOLD;
import static nolan.bloxpiece.client.human.HumanDraw.add;
import static nolan.bloxpiece.client.human.HumanDraw.easeOut;
import static nolan.bloxpiece.client.human.HumanDraw.v;

/**
 * Effets 3D des attaques de la race Humaine (HumanFx), chacun avec son identité et plusieurs couches animées :
 * <ul>
 *   <li>IMPACT FULGURANT (PUNCH) : un coup de poing, pas un projectile. Tout part du point de contact : air écrasé contre la
 *       cible, lignes d'impact qui jaillissent, deux ondes de choc en tore, la force qui traverse la cible (courants d'air
 *       droits), air qui ondule, poussière et gravillons chassés. Aucune sphère.</li>
 *   <li>RUÉE DESTRUCTRICE (RUSH) : une collision d'épaule : air écrasé entre l'épaule et la cible, grande étoile de lignes
 *       d'impact, ondes de choc en tore qui partent avec la cible, air qui s'enroule autour d'elle, onde au sol, couronne de
 *       poussière, débris.</li>
 *   <li>RUÉE DESTRUCTRICE (TRAIL) : la traînée de vitesse le long du trajet RÉEL du joueur (voiles d'air en volume, lignes de
 *       vitesse étirées, anneaux d'air déplacé, poussière des pas), plus forte à pleine vitesse, qui s'efface derrière lui.</li>
 *   <li>BRISEUR TERRESTRE (QUAKE) : dôme d'éclat, marque d'impact, fissures qui s'ouvrent en zigzag (lèvres sombres et coeur
 *       incandescent), MUR d'onde de choc circulaire qui se propage (même courbe que les dégâts), ondulations, bourrelet de
 *       poussière qui roule, fragments projetés sur son passage, colonnes de poussière au bord quand l'onde atteint sa
 *       portée.</li>
 *   <li>POING DU CONQUÉRANT (WAVE) : le POING D'AIR détaillé en quatre couches, poings rémanents, anneaux de force en blocs
 *       autour de l'avant-bras, étrave d'anneaux devant lui, anneaux de compression, tunnel d'air, rubans de vent.</li>
 *   <li>POING DU CONQUÉRANT (BLAST) : coeur de voxels aveuglant, boule de feu en voxels qui refroidit en fumée, dôme de pression
 *       en anneaux de blocs, ondes de choc en blocs, braises, débris, poussière, puis ONDE SECONDAIRE.</li>
 * </ul>
 * Tous les effets s'effacent progressivement avant la fin de vie de l'entité (rien ne reste affiché).
 */
public class HumanFxRenderer extends EntityRenderer<HumanFx, HumanFxRenderer.State> {

    public static class State extends EntityRenderState {
        public float age, size = 1.0F;
        public int kind, life = 20, seed;
        public float[] reach = {0, 0, 1};
        /** Ruée Destructrice : trajet relevé du lanceur, et sa position à cette image (null s'il ne fonce plus). */
        public float[][] trail;
        public float[] head;
        /** Le joueur local regarde SA propre onde à la première personne (premiers mètres allégés). */
        public boolean ownView;
    }

    /** Vue à la première personne du lanceur : l'onde du Conquérant n'est dessinée qu'au-delà de cette distance (blocs). */
    private static final float OWN_CLEAR = 2.6F;

    private final HumanDraw draw;

    public HumanFxRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.draw = new HumanDraw(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HumanFx entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.kind = entity.kind();
        state.size = entity.size();
        state.life = Math.max(4, entity.life());
        state.reach = entity.reach();
        state.seed = entity.getId();
        Minecraft mc = Minecraft.getInstance();
        state.ownView = mc.player != null && entity.shooter() == mc.player.getId() && mc.getCameraEntity() == mc.player
                && mc.options.getCameraType().isFirstPerson();
        if (state.kind == HumanFx.TRAIL) {
            recordTrail(entity, state, partialTick);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        switch (state.kind) {
            case HumanFx.PUNCH -> punch(state, pose, collector);
            case HumanFx.RUSH -> rush(state, pose, collector);
            case HumanFx.QUAKE -> quake(state, pose, collector);
            case HumanFx.WAVE -> wave(state, pose, collector);
            case HumanFx.BLAST -> blast(state, pose, collector);
            case HumanFx.TRAIL -> trail(state, pose, collector);
            default -> {
            }
        }
        super.submit(state, pose, collector, camera);
    }

    /** Couleur avec opacité (0 à 1). */
    private static int a(float alpha, int rgb) {
        return GoroLightning.argb(Math.max(0.0F, alpha), rgb);
    }

    private static float[] dir(State state) {
        float[] d = state.reach;
        float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        return len < 1.0E-4F ? v(0, 0, 1) : v(d[0] / len, d[1] / len, d[2] / len);
    }

    /** Opacité de fin de vie : 1, puis s'efface sur les {@code fade} derniers ticks. */
    private static float out(State state, float fade) {
        return Mth.clamp((state.life - state.age) / fade, 0.0F, 1.0F);
    }

    /** Position balistique (blocs) d'un fragment lancé à {@code vel} depuis {@code p0}, {@code t} ticks plus tard, posé au sol {@code floor}. */
    private static float[] ballistic(float[] p0, float[] vel, float t, float gravity, float floor) {
        float y = p0[1] + vel[1] * t - 0.5F * gravity * t * t;
        float k = t;
        if (y < floor) { // retombé : posé au sol, ne glisse plus
            float a = 0.5F * gravity, b = -vel[1], c = floor - p0[1];
            float disc = b * b - 4 * a * c;
            k = disc > 0 ? (float) ((-b + Math.sqrt(disc)) / (2 * a)) : t;
            k = Math.max(0.0F, Math.min(t, k));
            y = floor;
        }
        return v(p0[0] + vel[0] * k, y, p0[2] + vel[2] * k);
    }

    // ================================================================== outils propres aux coups (aucune boule d'énergie)

    /** Lignes d'impact (style coup de poing) : traits brisés qui jaillissent du point de contact dans le plan (u, w). */
    private void impactLines(PoseStack pose, SubmitNodeCollector col, float[] c, float[] u, float[] w, float r0, float r1, float width,
                             float alpha, int n, long seed) {
        Random r = new Random(seed);
        for (int i = 0; i < n; i++) {
            double ang = 2 * Math.PI * i / n + r.nextDouble() * 0.25;
            float len = 0.55F + r.nextFloat() * 0.45F;
            float kink = (r.nextFloat() - 0.5F) * 0.18F;
            float[] p = HumanDraw.onCircle(c, u, w, r0, ang);
            float[] m = HumanDraw.onCircle(c, u, w, r0 + (r1 - r0) * len * 0.5F, ang + kink);
            float[] q = HumanDraw.onCircle(c, u, w, r0 + (r1 - r0) * len, ang);
            draw.seg(pose, col, 1, p, m, width * 1.8F, a(alpha * 0.35F, AIR_EDGE));
            draw.seg(pose, col, 1, m, q, width * 1.1F, a(alpha * 0.35F, AIR_EDGE));
            draw.seg(pose, col, 2, p, m, width, a(alpha, FLASH));
            draw.seg(pose, col, 2, m, q, width * 0.5F, a(alpha, FLASH));
        }
    }

    /** Onde de choc en volume : un tore (trois anneaux qui forment un tube) de rayon {@code r}, perpendiculaire à {@code d}. */
    private void torus(PoseStack pose, SubmitNodeCollector col, float[] c, float[] d, float[][] perp, float r, float tube, float width,
                       int color, int n) {
        draw.ring(pose, col, 2, c, perp[0], perp[1], r, width, color, n);
        draw.ring(pose, col, 2, add(c, d, tube), perp[0], perp[1], r - tube * 0.6F, width * 0.7F, color, n);
        draw.ring(pose, col, 2, add(c, d, -tube), perp[0], perp[1], r + tube * 0.6F, width * 0.7F, color, n);
    }

    /** Air déformé : anneau ondulé (rayon qui ondule en {@code lobes} bosses qui tournent), très translucide. */
    private void wavyRing(PoseStack pose, SubmitNodeCollector col, float[] c, float[] u, float[] w, float r, float amp, int lobes, float phase,
                          float width, int color) {
        int n = 36;
        float[] prev = null;
        for (int i = 0; i <= n; i++) {
            double ang = 2 * Math.PI * i / n;
            float rr = r + amp * (float) Math.sin(ang * lobes + phase);
            float[] p = HumanDraw.onCircle(c, u, w, rr, ang);
            if (prev != null) {
                draw.seg(pose, col, 3, prev, p, width, color);
            }
            prev = p;
        }
    }

    /** Air écrasé contre la cible : anneaux serrés en coupelle (bombés vers {@code toward}), qui s'écartent en s'aplatissant. */
    private void pressure(PoseStack pose, SubmitNodeCollector col, float[] c, float[] toward, float[][] perp, float size, float k, float alpha) {
        float[] radii = {0.08F, 0.16F, 0.25F, 0.34F, 0.44F};
        for (int i = 0; i < radii.length; i++) {
            float r = radii[i] * size * (1.0F + 1.6F * k);
            float[] cc = add(c, toward, (0.02F + 0.035F * i) * size * (1.0F - k));
            draw.ring(pose, col, 1, cc, perp[0], perp[1], r, 1.8F - 0.25F * i, a(alpha * (1.0F - 0.12F * i), i % 2 == 0 ? FLASH : AIR), 22);
        }
    }

    // ================================================================== IMPACT FULGURANT

    /**
     * Un coup de poing qui touche : l'air est écrasé contre la cible, des lignes d'impact jaillissent, deux ondes de choc en
     * tore partent du point de contact (la force qui se propage), la force traverse la cible (courants d'air droits vers
     * l'avant), l'air ondule autour, poussière et gravillons sont chassés. Aucune sphère : tout part du point de contact.
     */
    private void punch(State s, PoseStack pose, SubmitNodeCollector col) {
        float a = s.age, size = s.size;
        float[] d = dir(s);
        float[] back = v(-d[0], -d[1], -d[2]);
        float[][] perp = GoroLightning.perpendiculars(d);
        float[] o = v(0, 0, 0);
        float fade = out(s, 5.0F);
        int light = s.lightCoords;
        // 1. air écrasé contre la cible, à l'instant du contact
        if (a < 3.5F) {
            float k = a / 3.5F;
            pressure(pose, col, o, back, perp, size, k, 0.85F * (1.0F - k));
        }
        // 2. lignes d'impact qui jaillissent puis s'envolent
        if (a < 6.0F) {
            float k = a / 6.0F, e = easeOut(k);
            impactLines(pose, col, o, perp[0], perp[1], (0.15F + 0.9F * e) * size, (0.55F + 1.4F * e) * size, 1.6F * (1.0F - k) + 0.3F,
                    0.95F * (1.0F - k), 14, s.seed * 5L);
        }
        // 3. deux ondes de choc en tore, l'une après l'autre, qui partent du contact
        for (int i = 0; i < 2; i++) {
            float ar = a - i * 2.5F;
            if (ar <= 0.0F || ar > 11.0F) {
                continue;
            }
            float k = ar / 11.0F, e = easeOut(k);
            float[] c = add(o, d, (0.05F + 0.5F * e) * size);
            torus(pose, col, c, d, perp, (0.25F + 1.5F * e) * size * (1.0F - 0.2F * i), (0.07F * (1.0F - k) + 0.02F) * size,
                    2.2F * (1.0F - k) + 0.4F, a(0.8F * (1.0F - k) * fade, i == 0 ? AIR : AIR_EDGE), 30);
        }
        // 4. la force traverse la cible : courants d'air droits qui filent vers l'avant
        if (a < 9.0F) {
            float k = a / 9.0F, e = easeOut(k);
            Random r = new Random(s.seed * 3L);
            for (int i = 0; i < 10; i++) {
                float[] st = HumanDraw.onCircle(o, perp[0], perp[1], (0.05F + r.nextFloat() * 0.3F) * size, r.nextDouble() * Math.PI * 2);
                float len = (0.4F + (1.4F + r.nextFloat()) * e) * size;
                float[] p0 = add(st, d, 0.1F + 0.6F * e * size);
                draw.seg(pose, col, 2, p0, add(p0, d, len), 0.8F * (1.0F - k) + 0.2F, a(0.55F * (1.0F - k), i % 3 == 0 ? AIR_EDGE : AIR));
            }
        }
        // 5. air déformé : anneaux ondulés autour du contact
        if (a < 12.0F) {
            float k = a / 12.0F, e = easeOut(k);
            wavyRing(pose, col, o, perp[0], perp[1], (0.45F + 1.3F * e) * size, 0.06F * size, 7, a * 0.8F, 1.0F, a(0.28F * (1.0F - k), AIR));
            wavyRing(pose, col, add(o, d, 0.15F), perp[0], perp[1], (0.3F + 0.9F * e) * size, 0.05F * size, 5, -a * 1.1F, 0.8F,
                    a(0.2F * (1.0F - k), FLASH));
        }
        // 6. poussière chassée sur les côtés et vers le bas, gravillons qui sautent
        Random r = new Random(s.seed * 7L);
        float k = Mth.clamp(a / 15.0F, 0.0F, 1.0F);
        for (int i = 0; i < 7; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            float speed = 0.05F + r.nextFloat() * 0.05F, rot = r.nextFloat() * 360.0F;
            float[] dirOut = HumanDraw.onCircle(v(0, 0, 0), perp[0], perp[1], 1.0F, ang);
            float[] c = add(add(o, dirOut, speed * a * 3.0F), d, 0.05F * a);
            c[1] -= 0.2F + 0.012F * a;
            draw.dust(pose, col, c, rot + a * 3.0F, (0.22F + 0.5F * easeOut(k)) * size, 0.6F * (1.0F - k) * fade, i % 3 == 0 ? DUST_DARK : DUST, light);
        }
        for (int i = 0; i < 7; i++) {
            float[] vel = v((r.nextFloat() - 0.5F) * 0.24F + d[0] * 0.14F, 0.12F + r.nextFloat() * 0.14F, (r.nextFloat() - 0.5F) * 0.24F + d[2] * 0.14F);
            float spin = r.nextFloat(), yaw = r.nextFloat() * 360.0F, sc = 0.16F + r.nextFloat() * 0.12F;
            float[] p = ballistic(v(0, -0.2F, 0), vel, a, 0.04F, -1.6F);
            float shrink = 1.0F - Mth.clamp((a - 10.0F) / 5.0F, 0.0F, 1.0F);
            draw.rock(pose, col, p, a * 30.0F * spin, yaw, a * 20.0F, sc * shrink, i % 2, light);
        }
    }

    // ================================================================== RUÉE DESTRUCTRICE : la collision

    /**
     * La collision de l'épaule : air écrasé entre l'épaule et la cible, grande étoile de lignes d'impact, ondes de choc en tore
     * qui partent AVEC la cible projetée, air qui s'enroule autour d'elle et la pousse, onde au sol, couronne de poussière,
     * débris arrachés.
     */
    private void rush(State s, PoseStack pose, SubmitNodeCollector col) {
        float a = s.age, size = s.size;
        float[] d = dir(s);
        float[] back = v(-d[0], -d[1], -d[2]);
        float[][] perp = GoroLightning.perpendiculars(d);
        float[] o = v(0, 0, 0);
        float fade = out(s, 8.0F);
        int light = s.lightCoords;
        float floor = -0.95F;
        // 1. air écrasé entre l'épaule et la cible
        if (a < 4.0F) {
            float k = a / 4.0F;
            pressure(pose, col, o, back, perp, size * 1.3F, k, 0.9F * (1.0F - k));
        }
        // 2. étoile de lignes d'impact (larges, courtes : un choc sourd et lourd)
        if (a < 7.0F) {
            float k = a / 7.0F, e = easeOut(k);
            impactLines(pose, col, o, perp[0], perp[1], (0.25F + 0.8F * e) * size, (0.9F + 1.5F * e) * size, 2.6F * (1.0F - k) + 0.4F,
                    0.95F * (1.0F - k), 18, s.seed * 11L);
        }
        // 3. ondes de choc en tore qui partent avec la cible projetée
        for (int i = 0; i < 3; i++) {
            float ar = a - i * 2.5F;
            if (ar <= 0.0F || ar > 14.0F) {
                continue;
            }
            float k = ar / 14.0F, e = easeOut(k);
            float[] c = add(o, d, (0.25F + 2.2F * e) * size);
            torus(pose, col, c, d, perp, (0.45F + 1.5F * e) * size, (0.1F * (1.0F - k) + 0.03F) * size, 3.0F * (1.0F - k) + 0.5F,
                    a(0.8F * (1.0F - k) * fade, i == 1 ? AIR_EDGE : AIR), 32);
        }
        // 4. pression autour de la cible : l'air s'enroule autour d'elle et la pousse vers l'arrière (tirets qui défilent)
        if (a < 13.0F) {
            float k = a / 13.0F;
            float[] tc = add(o, d, 0.5F * size + 0.12F * a);
            for (int line = 0; line < 8; line++) {
                double ang = line * Math.PI * 2 / 8 + 0.2;
                float[] prev = null;
                for (int sg = 0; sg <= 7; sg++) {
                    float t = sg / 7.0F;
                    float along = (-0.35F + 2.6F * t) * size;
                    float rad = (0.22F + 0.55F * Math.min(1.0F, t * 1.8F)) * size;
                    float[] q = HumanDraw.onCircle(add(tc, d, along), perp[0], perp[1], rad, ang + t * 0.5);
                    if (prev != null) {
                        float dash = (t * 2.5F - a * 0.35F + line * 0.29F) % 1.0F;
                        if ((dash < 0.0F ? dash + 1.0F : dash) < 0.65F) {
                            draw.seg(pose, col, 2, prev, q, 1.1F * (1.0F - 0.5F * t), a(0.6F * (1.0F - k) * (1.0F - 0.5F * t), AIR));
                        }
                    }
                    prev = q;
                }
            }
        }
        // 5. le choc descend dans le sol : onde au ras du sol et petit mur de poussière
        if (a < 16.0F) {
            float k = a / 16.0F, e = easeOut(k);
            draw.flatRing(pose, col, 2, v(0, floor + 0.05F, 0), (0.5F + 3.0F * e) * size, 2.4F * (1.0F - k) + 0.4F, a(0.7F * (1.0F - k), AIR), 32);
            draw.wall(pose, col, 2, v(0, floor, 0), (0.4F + 2.6F * e) * size, 0.0F, 0.5F * (1.0F - k), a(0.3F * (1.0F - k), DUST),
                    a(0.5F * (1.0F - k), FLASH), 28);
        }
        // 6. couronne de poussière qui roule au sol autour de la collision
        Random r = new Random(s.seed * 11L);
        float kd = Mth.clamp(a / 22.0F, 0.0F, 1.0F), ed = easeOut(kd);
        for (int i = 0; i < 12; i++) {
            double ang = i * Math.PI * 2 / 12 + r.nextDouble() * 0.4;
            float rr = r.nextFloat(), rot = r.nextFloat() * 360.0F;
            float rad = (0.4F + 2.6F * ed) * size * (0.8F + rr * 0.4F);
            float[] c = v((float) Math.cos(ang) * rad, floor + 0.25F + 0.55F * ed, (float) Math.sin(ang) * rad);
            draw.dust(pose, col, c, rot + a * 4.0F, (0.45F + 0.75F * ed) * size * 0.6F, 0.75F * (1.0F - kd) * fade,
                    i % 3 == 0 ? DUST_DARK : DUST, light);
        }
        // 7. débris arrachés au sol et projetés (surtout dans le sens de la charge)
        for (int i = 0; i < 12; i++) {
            float[] vel = v((r.nextFloat() - 0.5F) * 0.3F + d[0] * 0.18F, 0.18F + r.nextFloat() * 0.22F, (r.nextFloat() - 0.5F) * 0.3F + d[2] * 0.18F);
            float spin = r.nextFloat(), yaw = r.nextFloat() * 360.0F, sc = 0.22F + r.nextFloat() * 0.2F;
            float[] p = ballistic(v(0, floor + 0.1F, 0), vel, a, 0.045F, floor);
            float shrink = 1.0F - Mth.clamp((a - 16.0F) / 6.0F, 0.0F, 1.0F);
            draw.rock(pose, col, p, a * 25.0F * spin, yaw, a * 17.0F, sc * shrink, i % 2, light);
        }
    }

    // ================================================================== RUÉE DESTRUCTRICE : la traînée de vitesse

    /** Trajets relevés : identifiant de l'effet -> points {x, y, z (relatifs à l'effet), âge de l'effet, vitesse, au sol}. */
    private static final Map<Integer, List<float[]>> TRAILS = new HashMap<>();
    /** Dernière image (temps de jeu) où chaque traînée a été vue (nettoyage des traînées disparues). */
    private static final Map<Integer, Long> TRAIL_SEEN = new HashMap<>();

    /** Relève la position du lanceur à cette image, tant qu'il fonce. */
    private static void recordTrail(HumanFx entity, State state, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        TRAIL_SEEN.put(entity.getId(), now);
        if (TRAIL_SEEN.size() > 8) { // traînées dont l'entité a disparu : oubliées
            TRAIL_SEEN.entrySet().removeIf(en -> now - en.getValue() > 100);
            TRAILS.keySet().retainAll(TRAIL_SEEN.keySet());
        }
        List<float[]> pts = TRAILS.computeIfAbsent(entity.getId(), k -> new ArrayList<>());
        Entity shooter = mc.level.getEntity(entity.shooter());
        state.head = null;
        if (shooter != null && HumanClient.kind(shooter) == nolan.bloxpiece.ability.Human.RUSH
                && HumanClient.elapsed(shooter, shooter.tickCount + partialTick) >= HumanDestructiveRushAbility.CROUCH) {
            net.minecraft.world.phys.Vec3 p = shooter.getPosition(partialTick);
            float x = (float) (p.x - entity.getX()), y = (float) (p.y - entity.getY()), z = (float) (p.z - entity.getZ());
            float[] last = pts.isEmpty() ? null : pts.get(pts.size() - 1);
            float dist = last == null ? 1.0F : (float) Math.sqrt((x - last[0]) * (x - last[0]) + (z - last[2]) * (z - last[2]));
            if (last == null || dist >= 0.15F) {
                float dt = last == null ? 1.0F : Math.max(0.05F, state.age - last[3]);
                float speed = last == null ? 0.0F : dist / dt;
                pts.add(new float[] {x, y, z, state.age, last == null ? 0.0F : Mth.lerp(0.5F, last[4], speed), shooter.onGround() ? 1.0F : 0.0F});
                if (pts.size() > 120) {
                    pts.remove(0);
                }
            }
            state.head = new float[] {x, y, z};
        }
        state.trail = pts.toArray(new float[0][]);
    }

    /**
     * La traînée de vitesse, dans le monde, le long du trajet RÉEL du joueur : trois voiles d'air verticaux (au centre et aux
     * épaules) qui donnent du volume au sillage, une gerbe de lignes de vitesse étirées qui suivent chaque virage, des anneaux
     * d'air déplacé qui s'élargissent derrière lui, et la poussière arrachée par ses pas (avec quelques gravillons). Chaque
     * portion est d'autant plus marquée que le joueur allait vite à cet endroit, et s'efface avec le temps.
     */
    private void trail(State s, PoseStack pose, SubmitNodeCollector col) {
        float[][] pts = s.trail;
        if (pts == null || pts.length == 0) {
            return;
        }
        float a = s.age;
        int light = s.lightCoords;
        float topSpeed = (float) HumanDestructiveRushAbility.SPEED;
        int n = pts.length + (s.head != null ? 1 : 0);
        float[][] path = new float[n][];
        for (int i = 0; i < pts.length; i++) {
            path[i] = pts[i];
        }
        if (s.head != null) { // le dernier morceau rejoint le joueur à cette image
            float[] last = pts[pts.length - 1];
            path[n - 1] = new float[] {s.head[0], s.head[1], s.head[2], a, last[4], last[5]};
        }
        // lignes de vitesse : décalages fixes (côté, hauteur) autour du corps
        Random lr = new Random(s.seed * 29L);
        int lines = 12;
        float[] lineSide = new float[lines], lineUp = new float[lines], lineGap = new float[lines];
        for (int l = 0; l < lines; l++) {
            lineSide[l] = (lr.nextFloat() - 0.5F) * 1.1F;
            lineUp[l] = 0.1F + lr.nextFloat() * 1.75F;
            lineGap[l] = lr.nextFloat();
        }
        float travelled = 0.0F, nextRing = 0.6F, nextDust = 0.3F;
        for (int i = 0; i < n - 1; i++) {
            float[] p = path[i], q = path[i + 1];
            float dx = q[0] - p[0], dz = q[2] - p[2];
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-3F) {
                continue;
            }
            float[] side = v(-dz / len, 0.0F, dx / len);
            float[] fwd = v(dx / len, 0.0F, dz / len);
            float age = a - q[3];
            float life = Mth.clamp(1.0F - age / 9.0F, 0.0F, 1.0F);
            float speed = Mth.clamp(q[4] / topSpeed, 0.0F, 1.0F);
            // plus intense à pleine vitesse, plus fin et plus pâle vers la queue de la traînée
            float tail = (i + 1) / (float) (n - 1);
            float k = life * speed * (0.35F + 0.65F * tail);
            if (k <= 0.01F) {
                travelled += len;
                continue;
            }
            // 1. voiles d'air verticaux (centre et épaules) : un sillage en volume
            float[][] offs = {{0.0F, 0.15F, 1.6F, 0.17F}, {-0.32F, 0.5F, 1.05F, 0.1F}, {0.32F, 0.5F, 1.05F, 0.1F}};
            for (float[] off : offs) {
                float h = off[2] * (0.6F + 0.4F * speed);
                float mid = off[1] + h * 0.5F;
                float[] pa = v(p[0] + side[0] * off[0], p[1] + mid, p[2] + side[2] * off[0]);
                float[] pb = v(q[0] + side[0] * off[0], q[1] + mid, q[2] + side[2] * off[0]);
                draw.slab(pose, col, 3, HumanDraw.glow(), pa, pb, 0.35F, h * 16.0F, a(off[3] * k, AIR), GoroLightning.FULL_BRIGHT);
            }
            // 2. lignes de vitesse étirées qui suivent le trajet (certaines s'interrompent : on lit des traits, pas un tube)
            for (int l = 0; l < lines; l++) {
                if (((travelled * 0.7F + lineGap[l]) % 1.0F) > 0.75F) {
                    continue;
                }
                float sw = lineSide[l] * (0.8F + 0.3F * (1.0F - tail));
                float[] la = v(p[0] + side[0] * sw, p[1] + lineUp[l], p[2] + side[2] * sw);
                float[] lb = v(q[0] + side[0] * sw, q[1] + lineUp[l], q[2] + side[2] * sw);
                draw.seg(pose, col, 2, la, lb, 0.45F + 0.5F * speed * tail, a(0.65F * k, l % 4 == 0 ? AIR_EDGE : FLASH));
            }
            travelled += len;
            // 3. anneaux d'air déplacé, laissés tous les 1,3 bloc : ils s'élargissent et s'effacent vite
            if (travelled >= nextRing) {
                nextRing += 1.3F;
                float k2 = Mth.clamp(age / 7.0F, 0.0F, 1.0F);
                if (k2 < 1.0F) {
                    float[] c = v(q[0], q[1] + 0.95F, q[2]);
                    float[] up = v(0, 1, 0);
                    draw.ring(pose, col, 2, c, side, up, 0.6F + 0.7F * easeOut(k2), 1.6F * (1.0F - k2) + 0.3F, a(0.5F * speed * (1.0F - k2), AIR), 22);
                }
            }
            // 4. poussière arrachée par les pas, qui reste sur le trajet, monte et se dissipe ; quelques gravillons
            if (travelled >= nextDust && q[5] > 0.5F) {
                nextDust += 0.7F;
                float k3 = Mth.clamp(age / 16.0F, 0.0F, 1.0F);
                if (k3 < 1.0F) {
                    int idx = (int) (travelled * 7.0F);
                    float[] c = HumanDraw.add(v(q[0], q[1] + 0.2F + 0.5F * easeOut(k3), q[2]), side, (idx % 2 == 0 ? -0.3F : 0.3F) * (1.0F + k3));
                    draw.dust(pose, col, c, idx * 37.0F + a * 3.0F, (0.3F + 0.6F * easeOut(k3)) * (0.6F + 0.4F * speed),
                            0.65F * speed * (1.0F - k3), idx % 3 == 0 ? DUST_DARK : DUST, light);
                    if (idx % 3 == 0 && age < 12.0F) {
                        float[] vel = v(-fwd[0] * 0.12F + side[0] * 0.05F, 0.2F, -fwd[2] * 0.12F + side[2] * 0.05F);
                        draw.rock(pose, col, ballistic(v(q[0], q[1] + 0.1F, q[2]), vel, age, 0.045F, q[1]), age * 30.0F, idx * 50.0F,
                                age * 20.0F, 0.2F * (1.0F - Mth.clamp((age - 8.0F) / 4.0F, 0.0F, 1.0F)), idx % 2, light);
                    }
                }
            }
        }
    }

    // ================================================================== BRISEUR TERRESTRE

    private void quake(State s, PoseStack pose, SubmitNodeCollector col) {
        float a = s.age;
        float R = s.size;
        int W = HumanEarthBreakerAbility.WAVE_TICKS;
        float front = (float) HumanEarthBreakerAbility.front(a) * (R / (float) HumanEarthBreakerAbility.RADIUS);
        float fade = out(s, 12.0F);
        int light = s.lightCoords;
        float[] o = v(0, 0, 0);
        Random r = new Random(s.seed * 13L);
        // 1. dôme d'éclat au point d'impact (or puis blanc), souffle d'air au ras du sol
        if (a < 10.0F) {
            float k = a / 10.0F, e = easeOut(k);
            draw.ball(pose, col, 0, v(0, 0.1F, 0), 0.5F + 1.3F * e, a(0.9F * (1.0F - k), FLASH));
            draw.ball(pose, col, 1, v(0, 0.1F, 0), 0.8F + 1.8F * e, a(0.4F * (1.0F - k), GOLD));
            draw.flatRing(pose, col, 1, v(0, 0.12F, 0), 0.4F + 3.2F * easeOut(Math.min(1.0F, a / 4.0F)), 2.6F * (1.0F - k) + 0.4F,
                    a(0.8F * (1.0F - k), AIR), 32);
        }
        // 2. marque d'impact : sol tassé et noirci au centre
        for (int i = 0; i < 4; i++) {
            draw.flatRing(pose, col, 0, v(0, 0.03F, 0), 0.25F + i * 0.3F, 5.0F, a(0.55F * fade * (1.0F - 0.2F * i), CRACK), 18);
        }
        // 3. fissures en zigzag qui s'ouvrent depuis l'impact (lèvres sombres, coeur incandescent qui refroidit)
        int rays = 9;
        float glow = 1.0F - Mth.clamp((a - 8.0F) / 22.0F, 0.0F, 1.0F);
        for (int i = 0; i < rays; i++) {
            float[][] pts = crackPath(r, i * (float) Math.PI * 2 / rays + r.nextFloat() * 0.5F, R * (0.55F + r.nextFloat() * 0.35F), 8);
            float len = Math.min(1.0F, front / (R * 0.9F)) * (pts.length - 1);
            draw.crack(pose, col, 1, pts, len, 3.2F, a(0.95F * fade, CRACK), light, false);
            if (glow > 0.0F) {
                draw.crack(pose, col, 2, lift(pts, 0.02F), len, 1.2F, a(0.85F * glow * fade, CRACK_GLOW), light, true);
            }
            // une branche par fissure
            if (pts.length > 4) {
                float[][] br = crackPath(r, (float) Math.atan2(pts[3][2], pts[3][0]) + (r.nextBoolean() ? 0.7F : -0.7F),
                        R * 0.25F, 4);
                for (float[] p : br) {
                    p[0] += pts[3][0];
                    p[2] += pts[3][2];
                }
                float bl = Mth.clamp((len - 3.0F) * 1.2F, 0.0F, br.length - 1);
                draw.crack(pose, col, 1, br, bl, 2.0F, a(0.9F * fade, CRACK), light, false);
            }
        }
        // 4. MUR de l'onde de choc : bande circulaire qui se propage et s'abaisse, crête blanche ; ondulations derrière
        if (a <= W + 6.0F) {
            float x = Mth.clamp(a / W, 0.0F, 1.0F);
            float k = Mth.clamp((a - W) / 6.0F, 0.0F, 1.0F); // après la portée : s'éteint
            float h = (1.5F * (1.0F - x) + 0.35F) * (1.0F - k);
            draw.wall(pose, col, 2, o, front, 0.0F, h, a((0.5F * (1.0F - x) + 0.2F) * (1.0F - k), DUST),
                    a(0.8F * (1.0F - k), FLASH), 40);
            draw.wall(pose, col, 3, o, front * 0.97F, 0.0F, h * 0.7F, a(0.22F * (1.0F - k), AIR), a(0.4F * (1.0F - k), AIR_EDGE), 40);
            draw.flatRing(pose, col, 2, v(0, 0.08F, 0), front * 0.72F, 1.6F, a(0.4F * (1.0F - k), AIR), 36);
            draw.flatRing(pose, col, 2, v(0, 0.06F, 0), front * 0.48F, 1.2F, a(0.28F * (1.0F - k), AIR), 32);
        }
        // 5. bourrelet de poussière qui roule sur le front, et nuages laissés derrière lui
        int puffs = 18;
        for (int j = 0; j < 5; j++) {
            float aj = a - j * 2.5F;
            if (aj < 0.0F) {
                continue;
            }
            float rj = (float) HumanEarthBreakerAbility.front(Math.min(aj, W)) * (R / (float) HumanEarthBreakerAbility.RADIUS);
            float since = j * 2.5F + Math.max(0.0F, a - W);
            float alpha = (0.7F - 0.12F * j) * (1.0F - Mth.clamp(since / 26.0F, 0.0F, 1.0F)) * fade;
            for (int i = 0; i < puffs; i++) {
                double ang = (i + 0.5 * (j % 2)) * Math.PI * 2 / puffs;
                float[] c = v((float) Math.cos(ang) * rj, 0.35F + 0.08F * since + 0.15F * j, (float) Math.sin(ang) * rj);
                draw.dust(pose, col, c, i * 47.0F + a * 3.0F, 0.55F + 0.05F * since, alpha, j % 2 == 0 ? DUST : DUST_DARK, light);
            }
        }
        // 6. fragments de pierre projetés sur le passage de l'onde
        for (int i = 0; i < 26; i++) {
            // (tous les tirages d'abord : chaque fragment garde la même trajectoire d'une image à l'autre)
            float t0 = r.nextFloat() * W;
            double ang = r.nextDouble() * Math.PI * 2;
            float outward = 0.06F + r.nextFloat() * 0.12F, up = 0.25F + r.nextFloat() * 0.25F;
            float spin = r.nextFloat() * 360.0F, scale = 0.3F + r.nextFloat() * 0.35F;
            float r0 = (float) HumanEarthBreakerAbility.front(t0) * (R / (float) HumanEarthBreakerAbility.RADIUS);
            float t = a - t0;
            if (t < 0.0F || t > 22.0F) {
                continue;
            }
            float[] vel = v((float) Math.cos(ang) * outward, up, (float) Math.sin(ang) * outward);
            float[] p = ballistic(v((float) Math.cos(ang) * r0, 0.1F, (float) Math.sin(ang) * r0), vel, t, 0.045F, 0.1F);
            float shrink = 1.0F - Mth.clamp((t - 16.0F) / 6.0F, 0.0F, 1.0F);
            draw.rock(pose, col, p, t * 22.0F, spin, t * 15.0F, scale * shrink, i % 2, light);
        }
        // 7. l'onde atteint sa portée : colonnes de poussière qui montent tout autour du bord, dernier anneau de souffle
        float since = a - W;
        if (since > 0.0F && since < 22.0F) {
            float k = since / 22.0F;
            for (int i = 0; i < 12; i++) {
                double ang = i * Math.PI * 2 / 12 + 0.2;
                for (int h = 0; h < 3; h++) {
                    float[] c = v((float) Math.cos(ang) * (R + 0.3F), 0.4F + h * 0.55F * easeOut(k) * 1.6F, (float) Math.sin(ang) * (R + 0.3F));
                    draw.dust(pose, col, c, i * 31.0F + h * 70.0F + a * 2.0F, 0.5F + 0.4F * k - h * 0.1F, 0.6F * (1.0F - k) * fade, DUST, light);
                }
            }
            draw.flatRing(pose, col, 2, v(0, 0.15F, 0), R + 1.8F * easeOut(Math.min(1.0F, since / 8.0F)), 2.0F * (1.0F - k),
                    a(0.6F * (1.0F - k), AIR), 44);
        }
    }

    /** Une fissure en zigzag au sol, de longueur {@code length}, partant du centre dans la direction {@code angle}. */
    private static float[][] crackPath(Random r, float angle, float length, int n) {
        float[][] pts = new float[n][];
        float ang = angle;
        for (int i = 0; i < n; i++) {
            float dist = 0.35F + length * i / (n - 1);
            ang += (r.nextFloat() - 0.5F) * 0.45F;
            pts[i] = v((float) Math.cos(ang) * dist, 0.04F, (float) Math.sin(ang) * dist);
        }
        return pts;
    }

    private static float[][] lift(float[][] pts, float dy) {
        float[][] out = new float[pts.length][];
        for (int i = 0; i < pts.length; i++) {
            out[i] = v(pts[i][0], pts[i][1] + dy, pts[i][2]);
        }
        return out;
    }

    // ================================================================== POING DU CONQUÉRANT : l'onde

    /**
     * L'onde du Conquérant : le POING D'AIR détaillé (doigts, jointures, pouce, manchette rivetée, avant-bras) en quatre couches
     * (coeur blanc doré, corps doré, gaine ambrée, halo), suivi de deux poings rémanents ; des anneaux de force en blocs tournent
     * autour de son avant-bras ; devant lui, l'air s'écrase en un cône d'anneaux (étrave) ; derrière, les anneaux de compression
     * laissés sur le trajet s'élargissent et s'effacent, dans un tunnel d'air déformé, avec des rubans de vent en spirale et des
     * stries de vitesse. À l'impact, le poing s'étire et s'efface : l'explosion prend le relais.
     */
    private void wave(State s, PoseStack pose, SubmitNodeCollector col) {
        float a = s.age;
        float[] d = dir(s);
        float[] reach = s.reach;
        float length = (float) Math.sqrt(reach[0] * reach[0] + reach[1] * reach[1] + reach[2] * reach[2]);
        float speed = (float) HumanConquerorFistAbility.WAVE_SPEED;
        float travel = length / speed;
        float front = Math.min(length, a * speed);
        float after = Math.max(0.0F, a - travel); // ticks depuis l'arrivée
        float fade = out(s, 8.0F);
        float clear = s.ownView ? OWN_CLEAR : 0.0F;
        float[][] perp = GoroLightning.perpendiculars(d);
        float rad = (float) HumanConquerorFistAbility.waveRadius(front);
        float[] o = v(0, 0, 0);
        // 1. tunnel d'air déformé sur le trajet parcouru (très translucide), qui s'efface après l'impact
        float tunnel = (1.0F - Mth.clamp(after / 10.0F, 0.0F, 1.0F)) * fade;
        if (front > clear + 0.2F && tunnel > 0.0F) {
            float[] start = add(o, d, clear);
            float[] tip = add(o, d, Math.max(clear, front - rad * 0.8F));
            draw.seg(pose, col, 4, start, tip, rad * 2.0F * 16.0F * 0.7F, a(0.05F * tunnel, AIR));
            draw.seg(pose, col, 4, start, tip, rad * 2.0F * 16.0F * 0.35F, a(0.08F * tunnel, GOLD));
        }
        // 2. anneaux de compression en blocs, laissés tous les 1,6 bloc au passage du front : ils tournent, s'élargissent,
        //    s'amincissent et s'effacent
        int index = 0;
        for (float x = Math.max(clear, 0.8F); x <= front - rad * 0.6F; x += 1.6F, index++) {
            float age = (front - x) / speed + after;
            if (age > 14.0F) {
                continue;
            }
            float k = age / 14.0F;
            float r = (float) HumanConquerorFistAbility.waveRadius(x) * (0.75F + 0.75F * easeOut(k));
            draw.ringModel(pose, col, 2, add(o, d, x), d, r, index * 23.0F + a * 6.0F, 1.1F * (1.0F - k) + 0.25F,
                    a(0.7F * (1.0F - k) * fade, index % 2 == 0 ? GOLD : FLASH));
        }
        // 3. rubans de vent en spirale autour du trajet, derrière le front
        if (after < 8.0F) {
            float k = 1.0F - after / 8.0F;
            for (int h = 0; h < 3; h++) {
                float[] prev = null;
                for (float x = Math.max(clear, front - 9.0F); x <= front - rad * 0.5F; x += 0.5F) {
                    float ang = x * 0.85F - a * 0.7F + h * 2.094F;
                    float rr = (float) HumanConquerorFistAbility.waveRadius(x) * 1.15F;
                    float[] p = HumanDraw.onCircle(add(o, d, x), perp[0], perp[1], rr, ang);
                    if (prev != null) {
                        float t = (x - (front - 9.0F)) / 9.0F;
                        draw.seg(pose, col, 2, prev, p, 1.5F, a(0.5F * k * Mth.clamp(t, 0.0F, 1.0F) * fade, h == 1 ? GOLD : AIR));
                    }
                    prev = p;
                }
            }
        }
        // 4. stries de vitesse le long du trajet
        Random r = new Random(s.seed * 17L + (long) (a * 0.5F));
        for (int i = 0; i < 14 && after < 6.0F; i++) {
            float x = front - r.nextFloat() * 9.0F;
            float rr = (float) HumanConquerorFistAbility.waveRadius(Math.max(0.0F, x)) * (0.5F + r.nextFloat() * 0.9F);
            double ang = r.nextDouble() * Math.PI * 2;
            float len = 1.5F + r.nextFloat() * 2.5F;
            if (x < clear + 0.5F) {
                continue;
            }
            float[] p = HumanDraw.onCircle(add(o, d, x), perp[0], perp[1], rr, ang);
            draw.seg(pose, col, 2, p, add(p, d, -len), 0.7F, a(0.4F * fade, FLASH));
        }
        // 5. le POING D'AIR en tête
        if (front >= clear && after < 5.0F) {
            float burst = after / 5.0F;
            float[] c = add(o, d, front);
            float scale = rad * 3.3F * (1.0F + 0.5F * burst);
            float alpha = 1.0F - burst;
            float roll = Mth.sin(a * 0.35F) * 8.0F;
            // poings rémanents : la même forme laissée derrière, de plus en plus pâle
            for (int i = 3; i >= 1; i--) {
                draw.fist(pose, col, 4, add(c, d, -rad * 1.05F * i), d, scale * (1.0F - 0.08F * i), roll - i * 4.0F,
                        a((0.26F - 0.07F * i) * alpha, i == 1 ? GOLD : AMBER));
            }
            // le poing : coeur, corps, gaine, halo
            draw.fist(pose, col, 0, c, d, scale * 0.8F, roll, a(0.95F * alpha, 0xFFF8E4));
            draw.fist(pose, col, 1, c, d, scale, roll, a(0.5F * alpha, GOLD));
            draw.fist(pose, col, 2, c, d, scale * 1.16F, roll, a(0.22F * alpha, AMBER));
            draw.fist(pose, col, 3, c, d, scale * 1.34F, roll, a(0.08F * alpha, FLASH));
            // anneaux de force qui tournent autour de son avant-bras
            for (int j = 0; j < 2; j++) {
                draw.ringModel(pose, col, 1, add(c, d, -rad * (0.75F + 0.35F * j)), d, rad * (0.5F + 0.08F * j) * (1.0F + burst),
                        a * (18.0F + 10.0F * j) * (j == 0 ? 1.0F : -1.0F), 0.9F, a(0.8F * alpha, j == 0 ? FLASH : GOLD));
            }
            // étrave : l'air s'écrase devant les jointures en un cône d'anneaux qui s'ouvre vers l'arrière
            for (int i = 0; i < 4; i++) {
                draw.ringModel(pose, col, 2, add(c, d, rad * (0.85F - 0.28F * i)), d, rad * (0.3F + 0.32F * i), -a * 9.0F + i * 11.0F,
                        0.6F, a((0.55F - 0.11F * i) * alpha, i == 0 ? FLASH : AIR));
            }
        }
    }

    // ================================================================== POING DU CONQUÉRANT : l'explosion

    /**
     * L'explosion du Conquérant, en volumes de blocs :
     * <ol>
     *   <li>un coeur aveuglant de voxels blancs qui éclate puis s'effondre ;</li>
     *   <li>une BOULE DE FEU EN VOXELS : des dizaines de blocs lumineux projetés dans toutes les directions, qui grossissent puis
     *       refroidissent (blanc, or, ambre, braise) avant de devenir fumée, qui monte en champignon ;</li>
     *   <li>un DÔME DE PRESSION fait d'anneaux en blocs empilés, qui s'étend et s'efface ;</li>
     *   <li>des ondes de choc en anneaux de blocs, au sol et dans le plan de la frappe ;</li>
     *   <li>braises, débris, poussière (aucune fissure au sol) ;</li>
     *   <li>l'ONDE SECONDAIRE : un second front, bas et doré, qui roule de la poussière devant lui.</li>
     * </ol>
     */
    private void blast(State s, PoseStack pose, SubmitNodeCollector col) {
        float a = s.age, R = s.size;
        float[] d = dir(s);
        float[][] perp = GoroLightning.perpendiculars(d);
        float[] o = v(0, 0, 0);
        float fade = out(s, 14.0F);
        int light = s.lightCoords;
        float[] up = v(0, 1, 0);
        // 1. coeur aveuglant : un amas de voxels blancs qui éclate puis s'effondre
        if (a < 10.0F) {
            float k = a / 10.0F;
            float sc = 2.6F * easeOut(Math.min(1.0F, a / 2.5F)) * (1.0F - k * k);
            Random cr = new Random(s.seed * 3L);
            for (int i = 0; i < 8; i++) {
                float[] off = v((cr.nextFloat() - 0.5F) * 0.8F, (cr.nextFloat() - 0.5F) * 0.8F, (cr.nextFloat() - 0.5F) * 0.8F);
                draw.chunk(pose, col, 0, add(o, off, sc), cr.nextFloat() * 90.0F + a * 20.0F, cr.nextFloat() * 90.0F, a * 13.0F,
                        sc * (0.8F + cr.nextFloat() * 0.5F), a(0.95F * (1.0F - k), 0xFFFBF0), true, light);
            }
        }
        // 2. boule de feu en voxels qui refroidit en fumée
        Random r = new Random(s.seed * 19L);
        int chunks = 46;
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < chunks; i++) {
            // (tous les tirages d'abord : chaque voxel garde sa trajectoire d'une image à l'autre)
            float delay = r.nextFloat() * 3.0F, reachK = 0.55F + r.nextFloat() * 0.45F, big = 1.1F + r.nextFloat() * 1.1F;
            float rx = r.nextFloat() * 360.0F, ry = r.nextFloat() * 360.0F, spin = (r.nextFloat() - 0.5F) * 14.0F;
            float life = 34.0F + r.nextFloat() * 12.0F;
            float t = a - delay;
            if (t <= 0.0F || t > life) {
                continue;
            }
            double yy = 1.0 - (i + 0.5) * 2.0 / chunks, rr = Math.sqrt(1.0 - yy * yy), th = golden * i;
            float[] dirI = v((float) (Math.cos(th) * rr), (float) yy * 0.75F + 0.1F, (float) (Math.sin(th) * rr));
            float dist = R * 0.8F * reachK * easeOut(Math.min(1.0F, t / 12.0F));
            float[] c = add(o, dirI, dist);
            c[1] += 0.035F * Math.max(0.0F, t - 8.0F) * (1.0F + reachK); // la fumée monte
            float heat = t / life;
            float sc = big * easeOut(Math.min(1.0F, t / 6.0F)) * (heat > 0.75F ? 1.0F - (heat - 0.75F) / 0.25F * 0.6F : 1.0F);
            if (heat < 0.6F) {
                int colr = heat < 0.12F ? 0xFFF6DC : heat < 0.28F ? GOLD : heat < 0.45F ? AMBER : EMBER;
                draw.chunk(pose, col, 1, c, rx + t * spin, ry, t * spin * 0.5F, sc, a(0.85F * (1.0F - heat * 0.6F) * fade, colr), true, light);
            } else {
                float smoke = 1.0F - (heat - 0.6F) / 0.4F;
                draw.chunk(pose, col, 3, c, rx + t * spin, ry, t * spin * 0.5F, sc * 1.15F, a(0.55F * smoke * fade, 0x5C5148), false, light);
            }
        }
        // 3. dôme de pression : anneaux en blocs empilés en hémisphère, qui s'étend et s'efface
        if (a < 15.0F) {
            float k = a / 15.0F, e = easeOut(k);
            float dome = 2.0F + 12.0F * e;
            for (int lat = 0; lat < 6; lat++) {
                double th = Math.toRadians(lat * 15.0);
                float h = dome * (float) Math.sin(th), rr = dome * (float) Math.cos(th);
                draw.ringModel(pose, col, 3, v(0, h - 0.3F, 0), up, rr, lat * 17.0F + a * 3.0F, 0.6F * (1.0F - k) + 0.2F,
                        a(0.32F * (1.0F - k) * (1.0F - lat * 0.1F), lat % 2 == 0 ? AIR : FLASH));
            }
        }
        // 4. ondes de choc en anneaux de blocs : au sol (deux), et dans le plan de la frappe
        if (a < 18.0F) {
            float k = a / 18.0F, e = easeOut(k);
            draw.ringModel(pose, col, 2, v(0, -0.35F, 0), up, 1.0F + 15.0F * e, a * 2.0F, 1.6F * (1.0F - k) + 0.3F, a(0.85F * (1.0F - k), FLASH));
            draw.ringModel(pose, col, 2, v(0, -0.32F, 0), up, 0.8F + 11.5F * e, -a * 3.0F, 1.2F * (1.0F - k) + 0.3F, a(0.7F * (1.0F - k), GOLD));
            draw.ringModel(pose, col, 2, add(o, d, 0.5F + 2.0F * e), d, 1.0F + 9.0F * e, a * 4.0F, 1.3F * (1.0F - k) + 0.3F,
                    a(0.65F * (1.0F - k), AIR));
        }
        // 6. braises projetées
        if (a < 22.0F) {
            Random sr = new Random(s.seed * 29L);
            for (int i = 0; i < 26; i++) {
                float[] vel = v((sr.nextFloat() - 0.5F) * 0.75F, sr.nextFloat() * 0.5F + 0.12F, (sr.nextFloat() - 0.5F) * 0.75F);
                float[] p = ballistic(o, vel, a, 0.03F, -0.5F);
                float[] q = ballistic(o, vel, Math.max(0.0F, a - 1.3F), 0.03F, -0.5F);
                draw.seg(pose, col, 1, q, p, 1.1F, a(0.9F * (1.0F - a / 22.0F), i % 3 == 0 ? FLASH : i % 3 == 1 ? GOLD : EMBER));
            }
        }
        // 7. débris projetés loin
        Random dr = new Random(s.seed * 31L);
        for (int i = 0; i < 30; i++) {
            float[] vel = v((dr.nextFloat() - 0.5F) * 0.8F, 0.3F + dr.nextFloat() * 0.45F, (dr.nextFloat() - 0.5F) * 0.8F);
            float yaw = dr.nextFloat() * 360.0F, sc = 0.35F + dr.nextFloat() * 0.45F;
            float[] p = ballistic(v(0, -0.3F, 0), vel, a, 0.045F, -0.45F);
            float shrink = 1.0F - Mth.clamp((a - 34.0F) / 8.0F, 0.0F, 1.0F);
            draw.rock(pose, col, p, a * 20.0F, yaw, a * 13.0F, sc * shrink, i % 2, light);
        }
        // 8. poussière : grand anneau qui s'étale au sol
        Random pr = new Random(s.seed * 37L);
        float kd = Mth.clamp(a / 40.0F, 0.0F, 1.0F), ed = easeOut(kd);
        for (int i = 0; i < 20; i++) {
            double ang = i * Math.PI * 2 / 20 + pr.nextDouble() * 0.3;
            float rad = 0.8F + R * 1.1F * ed;
            float[] c = v((float) Math.cos(ang) * rad, -0.1F + 0.9F * ed, (float) Math.sin(ang) * rad);
            draw.dust(pose, col, c, i * 41.0F + a * 2.0F, 0.9F + 1.6F * ed, 0.7F * (1.0F - kd) * fade, i % 3 == 0 ? DUST_DARK : DUST, light);
        }
        // 9. ONDE SECONDAIRE : un second front plus large, bas et doré, qui roule de la poussière devant lui
        float since = a - HumanConquerorFistAbility.SECOND_DELAY;
        if (since > 0.0F && since < 20.0F) {
            float k = since / 20.0F, e = easeOut(k);
            float rr = 2.0F + ((float) HumanConquerorFistAbility.SECOND_RADIUS) * e;
            draw.wall(pose, col, 2, v(0, -0.45F, 0), rr, 0.0F, 0.9F * (1.0F - k) + 0.1F, a(0.28F * (1.0F - k), GOLD), a(0.7F * (1.0F - k), FLASH), 48);
            draw.ringModel(pose, col, 2, v(0, -0.4F, 0), up, rr * 0.94F, since * 5.0F, 1.0F * (1.0F - k) + 0.3F, a(0.6F * (1.0F - k), GOLD));
            for (int i = 0; i < 16; i++) {
                double ang = i * Math.PI * 2 / 16 + 0.1;
                draw.dust(pose, col, v((float) Math.cos(ang) * rr, -0.1F, (float) Math.sin(ang) * rr), i * 29.0F + a * 3.0F,
                        0.7F + 0.5F * k, 0.6F * (1.0F - k), DUST, light);
            }
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(HumanFx entity, float partialTick) {
        float[] d = entity.reach();
        double margin = entity.kind() == HumanFx.TRAIL ? HumanDestructiveRushAbility.DISTANCE + 6.0 : entity.kind() == HumanFx.WAVE ? 6.0 : entity.kind() == HumanFx.BLAST ? 18.0
                : entity.kind() == HumanFx.QUAKE ? entity.size() + 4.0 : 5.0;
        AABB box = entity.getBoundingBox();
        if (entity.kind() == HumanFx.WAVE) {
            box = box.expandTowards(d[0], d[1], d[2]);
        }
        return box.inflate(margin);
    }
}
