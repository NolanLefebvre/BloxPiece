package nolan.bloxpiece.client.lunarian;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.ability.LunarianJudgmentAbility;
import nolan.bloxpiece.client.goro.GoroLightning;
import nolan.bloxpiece.client.human.HumanDraw;
import nolan.bloxpiece.fx.LunarianFx;

import static nolan.bloxpiece.client.lunarian.LunarianFire.BODY;
import static nolan.bloxpiece.client.lunarian.LunarianFire.CORE;
import static nolan.bloxpiece.client.lunarian.LunarianFire.EDGE;
import static nolan.bloxpiece.client.lunarian.LunarianFire.ORANGE;
import static nolan.bloxpiece.client.lunarian.LunarianFire.RED;
import static nolan.bloxpiece.client.lunarian.LunarianFire.WHITE_HOT;
import static nolan.bloxpiece.client.lunarian.LunarianFire.YELLOW;
import static nolan.bloxpiece.client.lunarian.LunarianFire.argb;

/**
 * Effets 3D des capacités Lunarian (LunarianFx), en FEU VOLUMÉTRIQUE (LunarianFire : chaînes de volumes texturés en trois
 * couches qui ondulent) ; chaque attaque a ses propres formes :
 * <ul>
 *   <li>FEATHER : la plume incandescente, des mèches de feu le long de ses bords couchées par la vitesse, une traînée fine ;</li>
 *   <li>BURST : « éclat de plume » : petite boule de feu et mèches qui partent en étoile ;</li>
 *   <li>FIST_BOOM : grosse boule de feu qui s'ouvre, langues projetées dans toutes les directions, onde de choc, feu au sol ;</li>
 *   <li>TRAIL : traînée en comète (dash, envol, plongée) : volumes étirés dans l'axe du mouvement qui s'éteignent ;</li>
 *   <li>IMPACT : éruption au sol : boule de feu, hautes langues qui jaillissent, couronne de feu qui s'ouvre, onde, débris ;</li>
 *   <li>TORNADO : masse de feu dense (couronnes de masses de flammes qui tournent et montent, spirales claires intégrées,
 *       coeur lumineux, large base, langues qui se détachent, braises, fumée) ;</li>
 * </ul>
 */
public class LunarianFxRenderer extends EntityRenderer<LunarianFx, LunarianFxRenderer.State> {

    public static class State extends EntityRenderState {
        public float age, size = 1.0F;
        public int kind, life = 20, travel, seed;
        public float[] reach = {0, 0, 1};
    }

    private static final float[] UP = {0.0F, 1.0F, 0.0F};
    private final LunarianFire fire;

    public LunarianFxRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.fire = new LunarianFire(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(LunarianFx entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.kind = entity.kind();
        state.size = entity.size();
        state.life = Math.max(2, entity.life());
        state.travel = entity.travel();
        state.reach = entity.reach();
        state.seed = entity.getId();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector col, CameraRenderState camera) {
        this.fire.time = s.age;
        switch (s.kind) {
            case LunarianFx.FEATHER -> feather(s, pose, col);
            case LunarianFx.BURST -> burst(s, pose, col);
            case LunarianFx.FIST_BOOM -> fistBoom(s, pose, col);
            case LunarianFx.TRAIL -> trail(s, pose, col);
            case LunarianFx.IMPACT -> impact(s, pose, col);
            case LunarianFx.TORNADO -> tornado(s, pose, col);
            default -> {
            }
        }
        super.submit(s, pose, col, camera);
    }

    private static float len(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static float[] unit(float[] v) {
        float l = len(v);
        return l < 1.0E-4F ? new float[] {0, 0, 1} : new float[] {v[0] / l, v[1] / l, v[2] / l};
    }

    private static float[] add(float[] a, float[] d, float k) {
        return HumanDraw.add(a, d, k);
    }
    // ------------------------------------------------------------------ plumes incandescentes

    /**
     * La plume en vol : PLUME DE PHÉNIX EN FEU (LunarianFire.phoenixFeather : plume en relief, enveloppe de chaleur, barbes de feu
     * qui vacillent et s'allongent avec la vitesse), qui file pointe en avant avec un ROULIS doux et un léger battement (pas de
     * vrille), une TRAÎNÉE DE FEU proportionnée à sa vitesse (rubans lumineux qui s'amincissent et langues qui s'en détachent),
     * des braises qui tombent derrière elle. Au bout du trajet, elle se fond dans l'explosion (BURST).
     */
    private void feather(State s, PoseStack pose, SubmitNodeCollector col) {
        float a = s.age, T = Math.max(1, s.travel), L = len(s.reach);
        if (a > T + 0.5F) {
            return;
        }
        float[] d = unit(s.reach);
        float[][] perp = GoroLightning.perpendiculars(d);
        float f = L * Math.min(1.0F, a / T);
        float[] p = add(new float[] {0, 0, 0}, d, f);
        float appear = Math.min(1.0F, a / 1.5F);
        pose.pushPose();
        pose.translate(p[0], p[1], p[2]);
        LunarianFire.face(pose, d);
        pose.rotateDegrees(Axis.ZP, Mth.sin(a * 0.55F + s.seed) * 28.0F);   // roulis doux
        pose.rotateDegrees(Axis.YP, Mth.sin(a * 1.3F + s.seed * 2) * 6.0F);  // léger battement
        this.fire.phoenixFeather(pose, col, 1.05F * appear, 1.0F, s.seed, true);
        pose.popPose();
        // traînée de feu, plus longue quand la plume a pris de la vitesse
        float tail = Math.min(2.8F, f) * appear;
        float[] back = add(p, d, -0.6F);
        this.fire.seg(pose, col, add(back, d, -tail), back, 0.08F, 0.08F, a * 20.0F, CORE, argb(0.8F, 0xFFFFFF));
        this.fire.seg(pose, col, add(back, d, -tail * 0.75F), back, 0.17F, 0.13F, -a * 15.0F, BODY, argb(0.45F, 0xFFFFFF));
        this.fire.seg(pose, col, add(back, d, -tail * 0.5F), back, 0.26F, 0.2F, a * 11.0F, EDGE, argb(0.35F, 0xFFFFFF));
        Random rnd = new Random(s.seed * 5L);
        for (int i = 0; i < 3; i++) { // langues qui se détachent de la traînée et montent
            float bk = tail * (0.25F + 0.25F * i);
            float[] b = add(add(back, d, -bk), perp[0], (rnd.nextFloat() - 0.5F) * 0.15F);
            this.fire.tongue(pose, col, b, UP, 0.3F * (1.0F - bk / 3.0F), 0.07F, new float[] {0, 0, 0}, 3, 0.7F, 0.9F, 2, 0.8F * (1.0F - bk / 3.0F),
                    s.seed + i, 0.0F, false);
        }
        for (int i = 0; i < 5; i++) {
            float bk = tail * (i + rnd.nextFloat()) / 5.0F;
            float[] e = add(add(back, d, -bk), perp[0], (rnd.nextFloat() - 0.5F) * 0.25F);
            e[1] -= 0.04F * bk * bk; // les braises tombent
            this.fire.ember(pose, col, e, i * 50.0F, 0.04F * (1.0F - bk / 3.0F), argb(1.0F - bk / 3.0F, i % 2 == 0 ? ORANGE : RED));
        }
    }

    /**
     * Éclat de plume : la plume se brise au contact : petite boule de feu, morceaux de plume en feu (petites plumes de phénix)
     * projetés en étoile qui se consument, mèches, braises, fumée ; tout s'éteint progressivement.
     */
    private void burst(State s, PoseStack pose, SubmitNodeCollector col) {
        float t = s.age, life = 14.0F, size = s.size;
        if (t > life) {
            return;
        }
        float k = t / life, out = 1.0F - k;
        float[] o = {0, 0, 0};
        this.fire.fireball(pose, col, o, 0.4F * size * HumanDraw.easeOut(Math.min(1.0F, t / 3.0F)), k, out, s.seed);
        float[] d = unit(s.reach);
        Random fr = new Random(s.seed * 29L);
        for (int i = 0; i < 4; i++) { // morceaux de la plume qui volent en éclats et se consument
            float[] v = GoroLightning.normalize(add(new float[] {fr.nextFloat() - 0.5F, fr.nextFloat() * 0.7F, fr.nextFloat() - 0.5F}, d, -0.4F));
            float sp = (0.08F + fr.nextFloat() * 0.06F) * size;
            float[] p = {v[0] * sp * t, v[1] * sp * t - 0.015F * t * t, v[2] * sp * t};
            pose.pushPose();
            pose.translate(p[0], p[1], p[2]);
            LunarianFire.face(pose, v);
            pose.rotateDegrees(Axis.ZP, t * 25.0F + i * 90.0F);
            this.fire.phoenixFeather(pose, col, 0.38F * size * out, out, s.seed + i, false);
            pose.popPose();
        }
        Random r = new Random(s.seed * 17L);
        for (int i = 0; i < 7; i++) {
            float[] dir = GoroLightning.normalize(new float[] {r.nextFloat() - 0.5F, r.nextFloat() * 0.9F - 0.1F, r.nextFloat() - 0.5F});
            this.fire.wisp(pose, col, add(o, dir, 0.1F + 0.5F * k * size), dir, 0.45F * size * out, 0.08F, 0.2F, out, s.seed + i);
        }
        for (int i = 0; i < 8; i++) {
            float[] v = GoroLightning.normalize(new float[] {r.nextFloat() - 0.5F, r.nextFloat() * 0.8F, r.nextFloat() - 0.5F});
            float sp = (0.1F + r.nextFloat() * 0.1F) * size;
            float[] p = {v[0] * sp * t, v[1] * sp * t - 0.02F * t * t, v[2] * sp * t};
            this.fire.ember(pose, col, p, i * 30.0F, 0.04F * size * out, argb(out, i % 2 == 0 ? YELLOW : ORANGE));
        }
        if (t > 4.0F) {
            this.fire.smoke(pose, col, new float[] {0, 0.2F + 0.05F * t, 0}, t * 4.0F, (0.3F + 0.04F * t) * size, 0.35F * out, s.lightCoords);
        }
    }

    // ------------------------------------------------------------------ poing embrasé

    /** Explosion du poing : grosse boule de feu qui s'ouvre, langues projetées, onde de choc, feu qui court au sol. */
    private void fistBoom(State s, PoseStack pose, SubmitNodeCollector col) {
        float t = s.age, life = 22.0F;
        float[] o = {0, 0, 0};
        float[] d = unit(s.reach);
        float[][] perp = GoroLightning.perpendiculars(d);
        if (t < life) {
            float k = t / life, out = 1.0F - k;
            float grow = HumanDraw.easeOut(Math.min(1.0F, t / 4.0F));
            this.fire.fireball(pose, col, add(o, d, 0.2F * grow), 1.25F * grow, k, out, s.seed);
            Random r = new Random(s.seed * 23L);
            for (int i = 0; i < 12; i++) { // langues projetées dans toutes les directions, surtout vers l'avant du coup
                float[] dir = GoroLightning.normalize(add(new float[] {r.nextFloat() - 0.5F, r.nextFloat() - 0.3F, r.nextFloat() - 0.5F}, d, 0.6F));
                this.fire.tongue(pose, col, add(o, dir, 0.4F * grow), dir, (0.9F + 0.5F * r.nextFloat()) * grow * out, 0.22F,
                        new float[] {0, 0.3F, 0}, 4, 0.7F, 0.6F, 3, out, s.seed + i);
            }
        }
        if (t < 14.0F) { // onde de choc dans l'axe du coup
            float k = t / 14.0F, e = HumanDraw.easeOut(k);
            this.fire.draw.ring(pose, col, 2, add(o, d, 0.2F + 0.6F * e), perp[0], perp[1], 0.4F + 2.8F * e, 1.8F * (1.0F - k) + 0.3F, argb(0.7F * (1.0F - k), ORANGE), 32);
            this.fire.draw.ring(pose, col, 2, add(o, d, 0.1F), perp[0], perp[1], 0.3F + 2.0F * e, 1.2F * (1.0F - k) + 0.2F, argb(0.55F * (1.0F - k), WHITE_HOT), 28);
        }
        if (t < 20.0F) { // le feu court brièvement au sol autour du point de contact
            float k = t / 20.0F;
            for (int i = 0; i < 10; i++) {
                double an = i * Math.PI / 5 + s.seed;
                float rr = 0.5F + 1.8F * HumanDraw.easeOut(Math.min(1.0F, t / 8.0F));
                this.fire.tongue(pose, col, new float[] {(float) Math.cos(an) * rr, -0.85F, (float) Math.sin(an) * rr}, UP, 0.55F * (1.0F - k),
                        0.16F, new float[] {(float) Math.cos(an) * 0.3F, 0, (float) Math.sin(an) * 0.3F}, 3, 0.8F, 0.7F, 2, 1.0F - k, s.seed + 40 + i);
            }
        }
    }

    /** Traînée en comète : volumes étirés dans l'axe du mouvement, qui s'amincissent et s'éteignent, langues qui montent. */
    private void trail(State s, PoseStack pose, SubmitNodeCollector col) {
        float t = s.age, k = Mth.clamp(t / s.life, 0.0F, 1.0F), out = 1.0F - k;
        float size = s.size;
        float[] d = unit(s.reach);
        float[] o = {0, 0.1F * t, 0};
        float w = 0.32F * size * out;
        this.fire.seg(pose, col, add(o, d, -0.9F * size), add(o, d, 0.5F * size), w, w * 0.8F, t * 25.0F, BODY, argb(0.75F * out, 0xFFFFFF));
        this.fire.seg(pose, col, add(o, d, -0.6F * size), add(o, d, 0.4F * size), w * 0.5F, w * 0.45F, -t * 30.0F, CORE, argb(out, 0xFFFFFF));
        this.fire.seg(pose, col, add(o, d, -1.1F * size), add(o, d, 0.55F * size), w * 1.4F, w * 1.2F, t * 12.0F, EDGE, argb(0.5F * out, 0xFFFFFF));
        Random r = new Random(s.seed * 3L);
        for (int i = 0; i < 2; i++) {
            float[] b = {(r.nextFloat() - 0.5F) * 0.4F * size, 0.1F * t, (r.nextFloat() - 0.5F) * 0.4F * size};
            this.fire.tongue(pose, col, b, UP, 0.5F * size * out, 0.13F * size, new float[] {0, 0, 0}, 3, 0.8F, 0.8F, 2, out, s.seed + i);
        }
        for (int i = 0; i < 3; i++) {
            float[] p = {(r.nextFloat() - 0.5F) * 0.7F * size, 0.08F * t * (1.0F + i * 0.3F), (r.nextFloat() - 0.5F) * 0.7F * size};
            this.fire.ember(pose, col, p, i * 70.0F, 0.04F * size * out, argb(out, i % 2 == 0 ? YELLOW : RED));
        }
        if (k > 0.4F) {
            this.fire.smoke(pose, col, new float[] {0, 0.3F + 0.05F * t, 0}, t * 3.0F + s.seed, 0.25F * size + 0.02F * t, 0.25F * out, s.lightCoords);
        }
    }

    // ------------------------------------------------------------------ impact au sol

    /** Éruption au sol : boule de feu, hautes langues qui jaillissent, couronne de feu qui s'ouvre, onde, débris, fumée. */
    private void impact(State s, PoseStack pose, SubmitNodeCollector col) {
        float t = s.age, R = s.size;
        int light = s.lightCoords;
        float[] o = {0, 0.05F, 0};
        if (t < 16.0F) {
            float k = t / 16.0F, grow = HumanDraw.easeOut(Math.min(1.0F, t / 4.0F));
            this.fire.fireball(pose, col, new float[] {0, 0.6F, 0}, 1.8F * grow, k, 1.0F - k, s.seed);
            Random r = new Random(s.seed * 13L);
            for (int i = 0; i < 14; i++) { // hautes langues qui jaillissent vers le ciel et l'extérieur
                float[] dir = GoroLightning.normalize(new float[] {r.nextFloat() - 0.5F, 1.2F + r.nextFloat(), r.nextFloat() - 0.5F});
                this.fire.tongue(pose, col, add(o, dir, 0.3F), dir, (2.0F + 1.5F * r.nextFloat()) * grow * (1.0F - k * k), 0.34F,
                        new float[] {dir[0] * 0.4F, 0, dir[2] * 0.4F}, 5, 0.8F, 0.5F, 3, 1.0F - k * 0.7F, s.seed + i);
            }
        }
        if (t < 26.0F) { // couronne de feu qui s'ouvre au sol
            float k = t / 26.0F;
            for (int i = 0; i < 20; i++) {
                double an = i * Math.PI / 10;
                float rr = 0.8F + R * 0.75F * HumanDraw.easeOut(Math.min(1.0F, t / 7.0F));
                float c = (float) Math.cos(an), sn = (float) Math.sin(an);
                this.fire.tongue(pose, col, new float[] {c * rr, 0.0F, sn * rr}, UP, 1.1F * (1.0F - k * k) * Math.min(1.0F, t / 2.0F), 0.26F,
                        new float[] {c * 0.45F, 0, sn * 0.45F}, 4, 0.8F, 0.6F, 3, 1.0F - k, s.seed + 30 + i);
            }
        }
        if (t < 24.0F) { // onde au ras du sol (anneaux fins)
            float k = t / 24.0F, e = HumanDraw.easeOut(k);
            this.fire.draw.wall(pose, col, 2, o, 0.8F + R * 1.9F * e, 0.0F, 0.8F * (1.0F - k) + 0.1F, argb(0.28F * (1.0F - k), ORANGE),
                    argb(0.7F * (1.0F - k), YELLOW), 56);
            this.fire.draw.flatRing(pose, col, 2, o, 0.8F + R * 1.9F * e, 2.4F * (1.0F - k) + 0.3F, argb(0.6F * (1.0F - k), RED), 56);
        }
        Random r = new Random(s.seed * 9L); // débris et fumée
        for (int i = 0; i < 18; i++) {
            float vx = (r.nextFloat() - 0.5F) * 0.7F, vy = 0.3F + r.nextFloat() * 0.35F, vz = (r.nextFloat() - 0.5F) * 0.7F;
            float y = 0.1F + vy * t - 0.5F * 0.045F * t * t;
            float tt = y < 0.05F ? Math.max(0.0F, (vy + (float) Math.sqrt(vy * vy + 2 * 0.045F * 0.05F)) / 0.045F) : t;
            float shrink = 1.0F - Mth.clamp((t - 28.0F) / 10.0F, 0.0F, 1.0F);
            this.fire.draw.rock(pose, col, new float[] {vx * tt, Math.max(0.05F, y), vz * tt}, t * 20.0F, r.nextFloat() * 360.0F, t * 14.0F,
                    (0.3F + r.nextFloat() * 0.3F) * shrink, i % 2, light);
        }
        float km = Mth.clamp(t / 36.0F, 0.0F, 1.0F);
        for (int i = 0; i < 12; i++) {
            double an = i * Math.PI / 6;
            float rr = 1.0F + R * 0.9F * HumanDraw.easeOut(km);
            this.fire.smoke(pose, col, new float[] {(float) Math.cos(an) * rr, 0.4F + 1.4F * km, (float) Math.sin(an) * rr}, i * 30.0F + t * 2.0F,
                    0.8F + 1.2F * km, 0.5F * (1.0F - km), light);
        }
    }

    // ------------------------------------------------------------------ tornade de feu

    /** Rayon de la tornade à la hauteur {@code y} : base large au sol, col plus étroit, entonnoir qui s'évase vers le haut. */
    private static float radius(float y, float H, float R) {
        float k = Mth.clamp(y / H, 0.0F, 1.0F);
        float funnel = R * (0.42F + 0.8F * (float) Math.pow(k, 1.25));
        float skirt = R * 1.25F * (float) Math.exp(-y * 0.7F);
        return Math.max(funnel, skirt);
    }

    /**
     * TORNADE DE FEU : une MASSE de feu dense, pas des lignes.
     * <ol>
     *   <li>COQUILLE : des couronnes de MASSES DE FEU (LunarianFire.mass : langues larges et courtes, corps translucide et bord
     *       troué) espacées en hauteur et décalées d'une couronne à l'autre, assez nombreuses pour se chevaucher : un volume
     *       continu en cône irrégulier (rayon qui varie avec la hauteur, l'angle et le temps) ; elles TOURNENT autour de l'axe
     *       et MONTENT en continu (les couronnes défilent vers le haut, naissent à la base, s'effacent au sommet) ;</li>
     *   <li>trois SPIRALES plus claires intégrées dans la masse (masses chaudes, avec coeur, dans la coquille) ;</li>
     *   <li>un COEUR dense et lumineux (masses chaudes serrées autour de l'axe) ;</li>
     *   <li>une BASE large : couronne basse de grandes masses couchées dans le sens de rotation et une seconde, intérieure ;</li>
     *   <li>des LANGUES qui se détachent de la masse et s'en écartent, des braises en spirale, de la fumée au sommet.</li>
     * </ol>
     * Construction depuis le sol (20 ticks), rotation continue, puis ralentissement, dispersion et extinction après la
     * projection des créatures.
     */
    private void tornado(State s, PoseStack pose, SubmitNodeCollector col) {
        float t = s.age, R = s.size, H = Math.max(4.0F, s.reach[1]);
        int light = s.lightCoords;
        float life = s.life, fling = LunarianJudgmentAbility.FLING;
        float build = HumanDraw.easeOut(Mth.clamp(t / 20.0F, 0.0F, 1.0F));
        float top = H * build;
        float end = Mth.clamp((t - fling) / (life - fling), 0.0F, 1.0F);
        float alpha = Math.min(1.0F, t / 3.0F) * (1.0F - end * end);
        float scatter = 1.0F + 0.5F * end;
        // rotation : rapide, puis elle ralentit en se dissipant (angle intégré : aucun saut)
        float spin = t < fling ? t * 0.38F : fling * 0.38F + (t - fling) * 0.38F * (1.0F - 0.5F * end);
        float rise = t * 0.09F; // montée continue des couronnes
        if (alpha > 0.01F) {
            // 1. coquille de masses de feu
            float gap = 1.15F;
            int rings = (int) Math.ceil(H / gap) + 1;
            for (int ring = 0; ring < rings; ring++) {
                float level = ring + (rise % 1.0F);
                float y = level * gap - gap * 0.5F;
                if (y < 0.4F || y > top) {
                    continue;
                }
                float fadeIn = Mth.clamp((y - 0.4F) / 1.2F, 0.0F, 1.0F), fadeTop = Mth.clamp((top - y) / 2.0F, 0.0F, 1.0F);
                float a = alpha * Math.min(1.0F, 0.35F + fadeIn) * fadeTop;
                float r0 = radius(y, H, R) * scatter;
                int n = Math.max(9, (int) Math.ceil(2 * Math.PI * r0 / 1.05F));
                int id = (int) Math.floor(level) + (int) Math.floor(rise);
                for (int i = 0; i < n; i++) {
                    double an = spin * (1.0F - 0.25F * y / H) + i * Math.PI * 2 / n + id * 0.61;
                    float wob = 1.0F + 0.12F * Mth.sin(t * 0.21F + i * 2.3F + id) + 0.08F * Mth.sin((float) an * 3.0F + t * 0.1F);
                    float r = r0 * wob;
                    float c = (float) Math.cos(an), sn = (float) Math.sin(an);
                    float[] tang = {-sn, 0.0F, c};
                    float[] up = GoroLightning.normalize(new float[] {tang[0] * 0.75F, 1.0F, tang[2] * 0.75F});
                    float size = (0.75F + 0.55F * r0 / R) * (0.85F + 0.25F * Mth.sin(i * 1.7F + id * 2.9F + t * 0.15F));
                    boolean hot = Math.floorMod(i * 3 + id, n) < 3 + n / 8; // les spirales plus claires, intégrées à la masse
                    this.fire.mass(pose, col, new float[] {c * r, y, sn * r}, up, size * 1.7F, size * 0.62F,
                            new float[] {tang[0] * 0.25F, 0.0F, tang[2] * 0.25F}, a * (hot ? 1.0F : 0.92F), id * 131 + i, hot);
                }
                // 2. coeur dense et lumineux
                if (y < top * 0.95F) {
                    for (int i = 0; i < 3; i++) {
                        double an = -spin * 1.4F + i * Math.PI * 2 / 3 + id;
                        float rc = r0 * 0.28F;
                        this.fire.mass(pose, col, new float[] {(float) Math.cos(an) * rc, y, (float) Math.sin(an) * rc}, UP, 1.5F, 0.55F,
                                new float[] {0, 0, 0}, a * 0.9F, id * 17 + i, true);
                    }
                }
            }
            // 3. base large : grandes masses couchées dans le sens de rotation (couronne extérieure et intérieure)
            for (int ring = 0; ring < 2; ring++) {
                int n = ring == 0 ? 20 : 14;
                float rr = R * (ring == 0 ? 1.35F : 0.95F) * scatter;
                for (int i = 0; i < n; i++) {
                    double an = i * Math.PI * 2 / n + spin * (ring == 0 ? 0.5F : 0.7F);
                    float c = (float) Math.cos(an), sn = (float) Math.sin(an);
                    float[] up = GoroLightning.normalize(new float[] {-sn * 1.1F + c * 0.25F, 1.0F, c * 1.1F + sn * 0.25F});
                    this.fire.mass(pose, col, new float[] {c * rr, 0.05F, sn * rr}, up, (ring == 0 ? 1.6F : 2.2F) * (0.85F + 0.2F * Mth.sin(t * 0.3F + i * 1.9F)),
                            ring == 0 ? 0.75F : 0.85F, new float[] {0, 0.1F, 0}, alpha, 700 + ring * 50 + i, ring == 1);
                }
            }
            // 4. langues qui se détachent de la masse et s'en écartent
            Random r = new Random(s.seed * 29L);
            for (int i = 0; i < 14; i++) {
                float cyc = (t * 0.05F + r.nextFloat()) % 1.0F;
                float y = (0.15F + 0.75F * r.nextFloat()) * top;
                double an = r.nextDouble() * Math.PI * 2 + spin;
                float rr = radius(y, H, R) * (1.05F + 0.5F * cyc) * scatter;
                float c = (float) Math.cos(an), sn = (float) Math.sin(an);
                float[] out = GoroLightning.normalize(new float[] {c * 0.6F - sn * 0.6F, 0.8F, sn * 0.6F + c * 0.6F});
                this.fire.tongue(pose, col, new float[] {c * rr, y, sn * rr}, out, 1.4F * (1.0F - cyc), 0.32F, new float[] {0, 0.3F, 0}, 4, 0.5F, 0.4F,
                        2, alpha * (1.0F - cyc), s.seed + i, 0.0F, false);
            }
        }
        // 5. braises emportées en spirale (elles montent encore pendant la dissipation)
        Random r = new Random(s.seed * 17L);
        for (int i = 0; i < 46; i++) {
            float ph = (t * (0.012F + r.nextFloat() * 0.012F) + r.nextFloat()) % 1.0F;
            float y = ph * (top + 3.0F);
            double an = t * 0.4 + y * 0.5 + r.nextDouble() * Math.PI * 2;
            float rr = radius(Math.min(y, H), H, R) * (0.9F + 0.6F * r.nextFloat()) * (1.0F + end);
            float a = (1.0F - ph) * (1.0F - end * end * 0.9F);
            this.fire.ember(pose, col, new float[] {(float) Math.cos(an) * rr, y, (float) Math.sin(an) * rr}, i * 30.0F, i % 6 == 0 ? 0.12F : 0.06F,
                    argb(a, i % 3 == 0 ? YELLOW : i % 3 == 1 ? ORANGE : RED));
        }
        // 6. fumée au sommet
        for (int i = 0; i < 8; i++) {
            double an = i * Math.PI / 4 + t * 0.05;
            float rr = radius(top, H, R) * (0.8F + 0.3F * Mth.sin(t * 0.1F + i));
            this.fire.smoke(pose, col, new float[] {(float) Math.cos(an) * rr, top + 0.8F + 0.3F * Mth.sin(t * 0.2F + i), (float) Math.sin(an) * rr},
                    t * 2.0F + i * 45.0F, 1.8F + 0.6F * Mth.sin(i), 0.5F * alpha * build, light);
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(LunarianFx entity, float partialTick) {
        float[] d = entity.reach();
        AABB box = entity.getBoundingBox();
        return switch (entity.kind()) {
            case LunarianFx.FEATHER -> box.expandTowards(d[0], d[1], d[2]).inflate(3.0);
            case LunarianFx.TORNADO -> box.expandTowards(0.0, d[1] + 4.0, 0.0).inflate(entity.size() * 2.5 + 3.0, 1.0, entity.size() * 2.5 + 3.0);
            case LunarianFx.IMPACT -> box.inflate(entity.size() * 2.5 + 4.0);
            default -> box.inflate(5.0);
        };
    }
}
