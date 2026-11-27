package nolan.bloxpiece.client.fishman;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.ability.FishmanSpearAbility;
import nolan.bloxpiece.ability.FishmanTideAbility;
import nolan.bloxpiece.client.goro.GoroLightning;
import nolan.bloxpiece.client.human.HumanDraw;
import nolan.bloxpiece.fx.FishmanFx;

import static nolan.bloxpiece.client.fishman.FishmanDraw.DEEP;
import static nolan.bloxpiece.client.fishman.FishmanDraw.FOAM;
import static nolan.bloxpiece.client.fishman.FishmanDraw.LIGHT;
import static nolan.bloxpiece.client.fishman.FishmanDraw.SHINE;
import static nolan.bloxpiece.client.fishman.FishmanDraw.WATER;
import static nolan.bloxpiece.client.fishman.FishmanDraw.argb;

/**
 * Effets 3D des attaques Fishman (FishmanFx), en VOLUMES D'EAU pixel art (cubes d'eau translucides texturés, écume, gouttes en
 * larme) : rien n'est une simple boule bleue.
 * <ul>
 *   <li>WAVE : onde d'eau comprimée : coupe concave d'anneaux d'eau qui tournent, coeur de pression, sillage d'eau jusqu'au
 *       poing, éclaboussures arrachées au front ; au bout, l'onde éclate en couronne qui retombe ;</li>
 *   <li>DROP / SPLASH : goutte en larme avec traînée, puis couronne d'éclaboussures à l'impact ;</li>
 *   <li>TIDE : grande vague circulaire qui se forme au sol puis deux vagues plus petites, chacune avec base sombre, corps,
 *       crête d'écume recourbée, couche arrière, film d'eau au sol, embruns arrachés à la crête ; gerbe au point de frappe ;
 *       la vague s'affaisse et l'eau se dissipe ;</li>
 *   <li>SPIRAL : spirale d'eau comprimée : trois brins qui tournent les uns autour des autres en cône, coeur d'eau, anneaux de
 *       pression à la pointe, fragments arrachés, remous qui s'effacent derrière ;</li>
 *   <li>BLAST : explosion d'eau : éclair de compression, dôme d'eau qui s'étend, geyser, gerbes de gouttes qui retombent, onde
 *       de choc secondaire au ras du point d'impact, brume.</li>
 * </ul>
 */
public class FishmanFxRenderer extends EntityRenderer<FishmanFx, FishmanFxRenderer.State> {

    public static class State extends EntityRenderState {
        public float age, size = 1.0F;
        public int kind, life = 20, travel, seed;
        public float[] reach = {0, 0, 1};
    }

    private final FishmanDraw water;
    private final HumanDraw draw;

    public FishmanFxRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.water = new FishmanDraw(context);
        this.draw = new HumanDraw(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FishmanFx entity, State state, float partialTick) {
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
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        switch (state.kind) {
            case FishmanFx.WAVE -> wave(state, pose, collector);
            case FishmanFx.DROP -> drop(state, pose, collector);
            case FishmanFx.SPLASH -> splash(pose, collector, new float[] {0, 0, 0}, neg(dir(state)), state.age, 0.7F * state.size, state.seed,
                    state.lightCoords);
            case FishmanFx.TIDE -> tide(state, pose, collector);
            case FishmanFx.SPIRAL -> spiral(state, pose, collector);
            case FishmanFx.BLAST -> blast(state, pose, collector);
            default -> {
            }
        }
        super.submit(state, pose, collector, camera);
    }

    // ------------------------------------------------------------------ outils

    private static float len(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static float[] dir(State s) {
        float l = len(s.reach);
        return l < 1.0E-4F ? new float[] {0, 0, 1} : new float[] {s.reach[0] / l, s.reach[1] / l, s.reach[2] / l};
    }

    private static float[] neg(float[] v) {
        return new float[] {-v[0], -v[1], -v[2]};
    }

    private static float[] add(float[] a, float[] d, float k) {
        return HumanDraw.add(a, d, k);
    }

    /** Position d'un projectile balistique : départ {@code p}, vitesse {@code v} (blocs/tick), gravité {@code g}, au temps {@code t}. */
    private static float[] fall(float[] p, float[] v, float t, float g) {
        return new float[] {p[0] + v[0] * t, p[1] + v[1] * t - 0.5F * g * t * t, p[2] + v[2] * t};
    }

    /**
     * Couronne d'éclaboussures en {@code c} (face à {@code normal}) au temps {@code t} : gouttes projetées en cercle qui
     * retombent, anneau d'eau qui s'élargit, petite gerbe centrale.
     */
    private void splash(PoseStack pose, SubmitNodeCollector col, float[] c, float[] normal, float t, float size, int seed, int light) {
        float life = 9.0F;
        if (t > life) {
            return;
        }
        float k = t / life, out = 1.0F - k;
        float[][] perp = GoroLightning.perpendiculars(GoroLightning.normalize(normal));
        Random r = new Random(seed * 7L);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + r.nextDouble() * 0.5;
            float sp = (0.08F + r.nextFloat() * 0.07F) * size * 2.0F;
            float[] v = add(HumanDraw.onCircle(new float[] {0, 0, 0}, perp[0], perp[1], sp, a), normal, sp * 0.9F);
            float[] p = fall(c, v, t, 0.03F);
            float[] vel = {v[0], v[1] - 0.03F * t, v[2]};
            this.water.drop(pose, col, p, vel, 0.07F * size + 0.03F, 0.6F, out, light);
        }
        this.draw.ring(pose, col, 2, add(c, normal, 0.05F), perp[0], perp[1], (0.15F + 0.6F * HumanDraw.easeOut(k)) * size, 1.4F * out + 0.2F,
                argb(0.6F * out, LIGHT), 18);
        if (t < 4.0F) {
            this.water.blob(pose, col, add(c, normal, 0.1F + t * 0.06F), 0.12F * size * (1.0F - t / 4.0F) + 0.02F, t * 30.0F, out, light);
        }
    }

    // ------------------------------------------------------------------ Onde aquatique

    private void wave(State s, PoseStack pose, SubmitNodeCollector col) {
        float a = s.age, T = Math.max(1, s.travel), L = len(s.reach);
        float[] d = dir(s);
        float[][] perp = GoroLightning.perpendiculars(d);
        float[] u = perp[0], w = perp[1];
        int light = s.lightCoords;
        float fade = Mth.clamp((s.life - a) / 6.0F, 0.0F, 1.0F);
        float R = s.size * (0.55F + 0.45F * FishmanClient.ramp(a, 0.0F, 3.0F));
        if (a <= T + 0.5F) {
            float f = L * Math.min(1.0F, a / T);
            float[] front = add(new float[] {0, 0, 0}, d, f);
            float spin = a * 24.0F;
            // 1. coupe concave : trois anneaux d'eau (l'extérieur en retrait) qui tournent en sens alternés
            for (int j = 0; j < 3; j++) {
                float rr = R * (1.0F - 0.3F * j);
                float[] c = add(front, d, -0.55F * R * (1.0F - 0.3F * j) * (1.0F - 0.3F * j) + 0.15F * j);
                int n = 12 - 2 * j;
                for (int i = 0; i < n; i++) {
                    double an = Math.toRadians(spin * (j % 2 == 0 ? 1 : -1.3F)) + i * 2 * Math.PI / n;
                    float[] p0 = HumanDraw.onCircle(c, u, w, rr, an), p1 = HumanDraw.onCircle(c, u, w, rr, an + 2 * Math.PI / n * 0.92);
                    this.water.bar(pose, col, false, p0, p1, 0.2F * R, 0.24F * R, 0.0F, argb((0.7F - 0.1F * j) * fade, j == 1 ? LIGHT : WATER), light);
                    if (j == 0 && i % 2 == 0) { // écume au bord de l'onde
                        this.water.bar(pose, col, true, add(p0, d, 0.08F), add(p1, d, 0.08F), 0.1F * R, 0.1F * R, 0.0F, argb(0.75F * fade, FOAM), light);
                    }
                }
            }
            // 2. coeur de pression : eau comprimée en pointe vers l'avant
            this.water.bar(pose, col, false, add(front, d, -0.9F * R), add(front, d, 0.15F * R), 0.55F * R, 0.55F * R, spin, argb(0.5F * fade, LIGHT), light);
            this.water.blob(pose, col, add(front, d, -0.1F * R), 0.3F * R, spin, fade, light);
            this.draw.ring(pose, col, 2, add(front, d, 0.2F), u, w, R * 1.15F, 1.2F, argb(0.35F * fade, SHINE), 22);
            // 3. sillage d'eau jusqu'au poing (s'efface vers l'arrière)
            float back = Math.max(0.0F, f - 3.5F);
            for (int i = 0; i < 4; i++) {
                double an = Math.toRadians(spin * 0.5F) + i * Math.PI / 2;
                float[] off = HumanDraw.onCircle(new float[] {0, 0, 0}, u, w, 0.45F * R, an);
                float[] p0 = add(off, d, back), p1 = add(off, d, f - 0.4F * R);
                this.water.bar(pose, col, false, p0, p1, 0.12F * R, 0.12F * R, 0.0F, argb(0.35F * fade, WATER), light);
                this.water.glint(pose, col, add(p0, d, (f - back) * 0.4F), p1, 0.5F, argb(0.5F * fade, SHINE));
            }
            // 4. éclaboussures arrachées au front
            Random r = new Random(s.seed * 5L);
            for (int i = 0; i < 12; i++) {
                double an = r.nextDouble() * Math.PI * 2;
                float ph = (a * 0.3F + r.nextFloat()) % 1.0F;
                float[] radial = HumanDraw.onCircle(new float[] {0, 0, 0}, u, w, 1.0F, an);
                float[] p = add(add(front, radial, R * (0.9F + 1.3F * ph)), d, -0.4F * ph);
                p[1] -= 0.4F * ph * ph;
                float[] vel = add(radial, d, 0.3F);
                this.water.drop(pose, col, p, vel, 0.07F + 0.04F * (1.0F - ph), 1.0F, (1.0F - ph) * fade, light);
            }
        } else {
            // l'onde éclate au bout : couronne qui s'élargit et gouttes qui retombent
            float t = a - T, k = Mth.clamp(t / 10.0F, 0.0F, 1.0F);
            float[] end = s.reach;
            int n = 14;
            for (int i = 0; i < n; i++) {
                double an = i * 2 * Math.PI / n;
                float rr = R * (1.0F + 1.4F * HumanDraw.easeOut(k));
                float[] p0 = HumanDraw.onCircle(end, u, w, rr, an), p1 = HumanDraw.onCircle(end, u, w, rr, an + 2 * Math.PI / n * 0.85);
                this.water.bar(pose, col, i % 2 == 0, p0, p1, 0.14F * R * (1.0F - k), 0.14F * R * (1.0F - k), 0.0F, argb(0.7F * (1.0F - k), i % 2 == 0 ? FOAM : WATER), light);
            }
            Random r = new Random(s.seed * 3L);
            for (int i = 0; i < 14; i++) {
                double an = r.nextDouble() * Math.PI * 2;
                float[] v = add(HumanDraw.onCircle(new float[] {0, 0, 0}, u, w, 0.12F + r.nextFloat() * 0.12F, an), d, 0.05F);
                v[1] += 0.12F;
                float[] p = fall(end, v, t, 0.04F);
                this.water.drop(pose, col, p, new float[] {v[0], v[1] - 0.04F * t, v[2]}, 0.08F, 0.8F, (1.0F - k), light);
            }
        }
    }

    // ------------------------------------------------------------------ Lance aquatique

    private void drop(State s, PoseStack pose, SubmitNodeCollector col) {
        float a = s.age, T = Math.max(1, s.travel), L = len(s.reach);
        float[] d = dir(s);
        int light = s.lightCoords;
        if (a <= T) {
            float[] p = add(new float[] {0, 0, 0}, d, L * a / T);
            this.water.drop(pose, col, p, d, 0.13F, 1.8F, 1.0F, light);
            for (int i = 1; i <= 3; i++) { // petites gouttes qui suivent
                float back = 0.35F * i + 0.15F * i * i;
                if (back < L * a / T) {
                    this.water.drop(pose, col, add(p, d, -back), d, 0.13F * (float) Math.pow(0.62, i), 1.0F, 0.85F - 0.2F * i, light);
                }
            }
            float tail = Math.min(2.2F, L * a / T);
            this.water.glint(pose, col, add(p, d, -tail), add(p, d, -0.1F), 0.45F, argb(0.55F, SHINE));
            this.water.bar(pose, col, false, add(p, d, -tail * 0.8F), add(p, d, -0.15F), 0.06F, 0.06F, 45.0F, argb(0.4F, LIGHT), light);
        } else if (L < FishmanSpearAbility.RANGE - 0.1F) {
            // éclate contre le bloc
            splash(pose, col, s.reach, neg(d), a - T, 0.6F, s.seed, light);
        }
    }

    // ------------------------------------------------------------------ Vague dévastatrice

    private void tide(State s, PoseStack pose, SubmitNodeCollector col) {
        float t = s.age;
        int light = s.lightCoords;
        float[] o = {0, 0, 0};
        float spread = FishmanTideAbility.SPREAD;
        // 1. gerbe au point de frappe : l'eau jaillit des mains et retombe
        if (t < 12.0F) {
            float k = t / 12.0F;
            Random r = new Random(s.seed * 11L);
            for (int i = 0; i < 10; i++) {
                double an = i * Math.PI / 5 + r.nextDouble() * 0.4;
                float[] v = {(float) Math.cos(an) * (0.06F + r.nextFloat() * 0.06F), 0.28F + r.nextFloat() * 0.18F, (float) Math.sin(an) * (0.06F + r.nextFloat() * 0.06F)};
                float[] p = fall(new float[] {0, 0.3F, 0}, v, t, 0.06F);
                if (p[1] > 0.0F) {
                    this.water.blob(pose, col, p, 0.18F * (1.0F - 0.6F * k), t * 20.0F + i * 40.0F, 1.0F - k, light);
                }
            }
            this.draw.flatRing(pose, col, 2, new float[] {0, 0.05F, 0}, 0.4F + 1.8F * HumanDraw.easeOut(k), 3.0F * (1.0F - k) + 0.3F,
                    argb(0.7F * (1.0F - k), FOAM), 24);
        }
        // 2. trois vagues successives : la première immense, les suivantes plus petites
        for (int wv = 0; wv < 3; wv++) {
            float tw = t - wv * 5.0F;
            if (tw <= 0.0F || tw > spread + 12.0F) {
                continue;
            }
            float r = (float) FishmanTideAbility.front(tw) * (1.0F - 0.15F * wv);
            float k = Mth.clamp(tw / spread, 0.0F, 1.0F);
            float rise = FishmanClient.ramp(tw, 0.0F, 3.0F), sink = 1.0F - FishmanClient.ramp(tw, spread - 3.0F, spread + 12.0F);
            float h = ((2.1F - 0.6F * wv) * (1.0F - 0.45F * k) + 0.15F) * rise * sink;
            float alpha = (1.0F - 0.2F * wv) * (0.4F + 0.6F * sink);
            int n = Mth.clamp((int) (r * 6.0F), 14, 60);
            float phase = t * 0.35F + wv * 2.0F;
            this.water.wall(pose, col, o, r, h, 0.5F, 0.32F * h, alpha, 0.0, Math.PI * 2, n, phase, light);
            // couche arrière : l'eau qui suit le front (profondeur)
            if (r > 1.4F) {
                this.water.wall(pose, col, o, r - 1.0F, h * 0.42F, 0.7F, 0.08F, 0.6F * alpha, 0.1, Math.PI * 2 + 0.1, Math.max(12, n - 8),
                        phase + 1.3F, light);
            }
            // film d'eau au sol, derrière le front
            this.draw.flatRing(pose, col, 1, new float[] {0, 0.04F, 0}, r * 0.7F, r * 9.0F, argb(0.18F * alpha * sink, WATER), Math.max(16, n / 2));
            // embruns arrachés à la crête
            Random sr = new Random(s.seed * 13L + wv);
            for (int i = 0; i < 18 - wv * 5; i++) {
                double an = sr.nextDouble() * Math.PI * 2;
                float ph = (tw * 0.12F + sr.nextFloat()) % 1.0F;
                float cx = (float) Math.cos(an), cz = (float) Math.sin(an);
                float rr = r + 0.3F * h + 1.2F * ph;
                float[] p = {cx * rr, h * (1.0F + 0.5F * ph) - 1.6F * ph * ph, cz * rr};
                if (p[1] > 0.0F) {
                    this.water.drop(pose, col, p, new float[] {cx, 0.4F - 2.0F * ph, cz}, 0.09F, 0.8F, alpha * (1.0F - ph), light);
                }
            }
        }
        // 3. dissipation : flaque qui s'efface et dernières gouttes
        float end = Mth.clamp((t - spread - 4.0F) / 22.0F, 0.0F, 1.0F);
        if (end > 0.0F && end < 1.0F) {
            float R = (float) FishmanTideAbility.RADIUS;
            this.draw.flatRing(pose, col, 1, new float[] {0, 0.04F, 0}, R * 0.55F, R * 14.0F, argb(0.16F * (1.0F - end), LIGHT), 40);
        }
    }

    // ------------------------------------------------------------------ Perforation des profondeurs

    /**
     * La spirale (taille visuelle FishmanDepthsAbility.VISUAL_RADIUS) : toutes les longueurs suivent son rayon ({@code g} = 1 pour
     * l'ancienne taille de 1,3 bloc), pour garder les proportions. Au départ du poing, elle s'ouvre en quelques blocs (la caméra
     * du lanceur n'est jamais noyée dedans).
     */
    private void spiral(State s, PoseStack pose, SubmitNodeCollector col) {
        float a = s.age, T = Math.max(1, s.travel), L = len(s.reach);
        float[] d = dir(s);
        float[][] perp = GoroLightning.perpendiculars(d);
        float[] u = perp[0], w = perp[1];
        int light = s.lightCoords;
        float f = L * Math.min(1.0F, a / T);
        float fade = a <= T ? 1.0F : Mth.clamp(1.0F - (a - T) / 5.0F, 0.0F, 1.0F);
        if (fade <= 0.0F) {
            return;
        }
        float g = s.size / 1.3F;
        float R = s.size * Math.min(1.0F, 0.3F + f / (2.5F * g)); // s'ouvre en quittant le poing
        float[] tip = add(new float[] {0, 0, 0}, d, f);
        float Ls = Math.min(4.2F * g, f + 0.01F);
        float shrink = a <= T ? 1.0F : fade; // à l'impact, la spirale se resserre dans l'explosion
        // 1. trois brins qui s'enroulent les uns autour des autres en cône depuis la pointe, et une gaine extérieure qui tourne
        //    en sens inverse (couches d'eau qui tournent les unes autour des autres)
        int seg = 22;
        for (int layer = 0; layer < 2; layer++) {
            float lr = layer == 0 ? 1.0F : 1.22F, spinDir = layer == 0 ? 1.0F : -0.8F;
            for (int st = 0; st < 3; st++) {
                float[] prev = null;
                for (int i = 0; i <= seg; i++) {
                    float t = i / (float) seg;
                    float along = t * Ls * shrink;
                    float rad = lr * R * shrink * (0.22F + 0.78F * Math.min(1.0F, t * 2.4F)) * (1.0F - 0.45F * Math.max(0.0F, t - 0.6F) / 0.4F);
                    double an = (t * 7.5 + a * 1.4) * spinDir + st * Math.PI * 2 / 3 + layer * 0.5;
                    float[] p = add(HumanDraw.onCircle(tip, u, w, rad, an), d, -along);
                    if (prev != null) {
                        float th = (0.26F * R * (1.0F - 0.5F * t) + 0.05F) * (layer == 0 ? 1.0F : 0.6F);
                        float al = (0.75F - 0.3F * t) * fade * (layer == 0 ? 1.0F : 0.55F);
                        this.water.bar(pose, col, false, prev, p, th, th * 0.8F, (float) Math.toDegrees(an), argb(al, layer == 1 || st == 1 ? LIGHT : WATER), light);
                        if (st == 0 && i % 3 == 0) {
                            this.water.bar(pose, col, true, prev, p, th * 0.5F, th * 0.5F, 0.0F, argb(0.6F * fade * (1.0F - t), FOAM), light);
                        }
                    }
                    prev = p;
                }
            }
        }
        // 2. coeur d'eau comprimée et reflets
        this.water.bar(pose, col, false, add(tip, d, -Ls * 0.9F * shrink), tip, 0.5F * R * shrink, 0.5F * R * shrink, a * 40.0F, argb(0.55F * fade, DEEP), light);
        this.water.bar(pose, col, false, add(tip, d, -Ls * 0.7F * shrink), add(tip, d, 0.2F * g), 0.28F * R * shrink, 0.28F * R * shrink, -a * 50.0F,
                argb(0.6F * fade, LIGHT), light);
        this.water.glint(pose, col, add(tip, d, -Ls * shrink), add(tip, d, 0.3F * g), 1.0F * g, argb(0.6F * fade, SHINE));
        // 3. anneaux de pression à la pointe
        for (int i = 0; i < 3; i++) {
            float ph = (a * 0.25F + i / 3.0F) % 1.0F;
            this.draw.ring(pose, col, 2, add(tip, d, -ph * 2.0F * g), u, w, R * (0.9F + 0.8F * ph) * shrink, (1.6F * (1.0F - ph) + 0.3F) * g,
                    argb(0.5F * (1.0F - ph) * fade, i == 0 ? SHINE : LIGHT), 32);
        }
        // 4. fragments et éclaboussures arrachés à la spirale
        Random r = new Random(s.seed * 17L);
        for (int i = 0; i < 22; i++) {
            float ph = (a * 0.3F + r.nextFloat()) % 1.0F;
            double an = r.nextDouble() * Math.PI * 2 + a * 0.6;
            float[] radial = HumanDraw.onCircle(new float[] {0, 0, 0}, u, w, 1.0F, an);
            float[] tang = HumanDraw.onCircle(new float[] {0, 0, 0}, u, w, 1.0F, an + Math.PI / 2);
            float[] p = add(add(tip, d, -ph * 3.5F * g), radial, R * (0.8F + 1.0F * ph));
            p[1] -= 0.5F * g * ph * ph;
            this.water.drop(pose, col, p, add(tang, radial, 0.6F), 0.09F * g * 0.75F + 0.03F, 0.9F, (1.0F - ph) * fade, light);
        }
        // 5. remous qui s'effacent derrière la spirale
        for (float back = 4.5F * g; back < Math.min(f, 12.0F * g); back += 1.4F * g) {
            float al = (1.0F - back / (12.0F * g)) * 0.4F * fade;
            this.draw.ring(pose, col, 2, add(tip, d, -back), u, w, R * (0.7F + back * 0.03F), 1.0F * g, argb(al, LIGHT), 24);
        }
    }

    // ------------------------------------------------------------------ explosion d'eau

    /**
     * L'explosion (taille visuelle FishmanDepthsAbility.VISUAL_BLAST) : toutes les dimensions suivent la taille ({@code g} = 1
     * pour l'ancienne taille de 5,5 blocs) ; les gerbes vont plus loin ET retombent au même rythme (gravité mise à l'échelle).
     */
    private void blast(State s, PoseStack pose, SubmitNodeCollector col) {
        float t = s.age, R = s.size, g = R / 5.5F;
        int light = s.lightCoords;
        float[] o = {0, 0, 0};
        float[] d = dir(s);
        float[][] perp = GoroLightning.perpendiculars(d);
        // 1. éclair de compression
        if (t < 5.0F) {
            float k = t / 5.0F;
            this.draw.ball(pose, col, 1, o, (0.5F + 0.9F * k) * g, argb(0.75F * (1.0F - k), SHINE));
            this.draw.ring(pose, col, 1, o, perp[0], perp[1], (0.6F + 2.5F * HumanDraw.easeOut(k)) * g, (3.0F * (1.0F - k) + 0.4F) * g,
                    argb(0.8F * (1.0F - k), FOAM), 36);
        }
        // 1b. GROSSE EXPLOSION D'EAU au point d'impact (distincte de l'onde de choc, qui file au ras du sol plus loin) :
        //     coeur d'eau qui enfle et éclate, grands jets d'eau projetés dans toutes les directions (épais à la base, coiffés
        //     d'écume, qui se détachent du centre et retombent en se brisant), couronne de jets inclinés vers le ciel
        explosion(pose, col, t, R, g, s.seed, light);
        // 2. dôme d'eau qui s'étend (deux couches : la plus extérieure, la plus claire)
        if (t < 18.0F) {
            float k = HumanDraw.easeOut(t / 14.0F), out = 1.0F - t / 18.0F;
            for (int layer = 0; layer < 2; layer++) {
                float rad = R * k * (layer == 0 ? 0.9F : 0.62F);
                for (int lat = -1; lat <= 3; lat++) {
                    double th = Math.toRadians(lat * 22.0);
                    float y = rad * (float) Math.sin(th), rr = rad * (float) Math.cos(th);
                    int n = Math.max(5, (int) (rr * 2.6F / g));
                    for (int i = 0; i < n; i++) {
                        double an = i * 2 * Math.PI / n + lat * 0.4 + layer * 0.3;
                        float[] p = {(float) Math.cos(an) * rr, y, (float) Math.sin(an) * rr};
                        this.water.blob(pose, col, p, ((0.42F - 0.12F * layer) * (1.0F - 0.5F * k) + 0.08F) * g, t * 15.0F + i * 30.0F,
                                out * (layer == 0 ? 0.8F : 1.0F), light);
                    }
                }
            }
        }
        // 3. geyser : colonne d'eau qui monte puis retombe
        if (t < 30.0F) {
            float up = HumanDraw.easeOut(Math.min(1.0F, t / 9.0F)) * 5.0F * g * (1.0F - Mth.clamp((t - 12.0F) / 18.0F, 0.0F, 1.0F));
            float out = 1.0F - Mth.clamp((t - 18.0F) / 12.0F, 0.0F, 1.0F);
            for (int i = 0; i < 7; i++) {
                float y = up * i / 6.0F;
                this.water.blob(pose, col, new float[] {Mth.sin(t * 0.3F + i) * 0.15F * g, y, Mth.cos(t * 0.3F + i) * 0.15F * g},
                        (0.55F - 0.05F * i) * g, t * 20.0F + i * 50.0F, out, light);
            }
            if (up > 0.5F) {
                this.water.cube(pose, col, true, new float[] {0, up + 0.2F * g, 0}, t * 25.0F, 10.0F, 20.0F, 0.9F * g, 0.35F * g, 0.9F * g,
                        argb(0.8F * out, FOAM), light);
            }
        }
        // 4. gerbes de gouttes projetées loin, qui retombent
        Random r = new Random(s.seed * 23L);
        float out = 1.0F - Mth.clamp((t - 26.0F) / (s.life - 26.0F), 0.0F, 1.0F);
        for (int i = 0; i < 54; i++) {
            float[] v = GoroLightning.normalize(new float[] {r.nextFloat() - 0.5F, 0.25F + r.nextFloat() * 0.75F, r.nextFloat() - 0.5F});
            float sp = (0.3F + r.nextFloat() * 0.4F) * g;
            v = new float[] {v[0] * sp, v[1] * sp, v[2] * sp};
            float grav = 0.035F * g;
            float[] p = fall(o, v, t, grav);
            if (p[1] < -6.0F * g) {
                continue;
            }
            this.water.drop(pose, col, p, new float[] {v[0], v[1] - grav * t, v[2]}, (0.1F + 0.06F * (i % 3)) * (0.5F + 0.5F * g), 1.0F, out, light);
        }
        // 5. ondes de choc secondaires : deux anneaux d'eau qui filent au ras du point d'impact, le second un peu après
        for (int wv = 0; wv < 2; wv++) {
            float ts = t - 5.0F - wv * 5.0F;
            if (ts > 0.0F && ts < 26.0F) {
                float k = ts / 26.0F;
                float r2 = 0.8F * g + (2.2F - 0.5F * wv) * R * HumanDraw.easeOut(k);
                float al = (1.0F - k) * (wv == 0 ? 1.0F : 0.6F);
                this.water.wall(pose, col, new float[] {0, -0.9F, 0}, r2, (0.9F * (1.0F - k) + 0.1F) * g * (wv == 0 ? 1.0F : 0.7F), 0.35F * g,
                        0.25F * g, al, 0.0, Math.PI * 2, Mth.clamp((int) (r2 * 3.0F), 20, 110), t * 0.4F + wv, light);
                this.draw.flatRing(pose, col, 2, new float[] {0, -0.85F, 0}, r2 * 0.92F, (2.5F * (1.0F - k) + 0.3F) * g, argb(0.6F * al, FOAM), 60);
            }
        }
        // 6. brume d'eau qui se dissipe
        float km = Mth.clamp(t / 34.0F, 0.0F, 1.0F);
        for (int i = 0; i < 14; i++) {
            double an = i * Math.PI / 7;
            float rr = 0.8F * g + R * 0.9F * HumanDraw.easeOut(km);
            this.draw.dust(pose, col, new float[] {(float) Math.cos(an) * rr, (0.4F + 1.2F * km) * g, (float) Math.sin(an) * rr}, i * 40.0F + t * 2.0F,
                    (0.9F + 1.2F * km) * g, 0.4F * (1.0F - km), 0xCFEAFF, light);
        }
    }

    /**
     * L'explosion d'eau centrale de la Perforation : trois couches synchronisées avec l'impact.
     * <ol>
     *   <li>coeur : amas de masses d'eau qui enflent d'un coup puis éclatent (0 à 10 ticks) ;</li>
     *   <li>jets : {@code JETS} grands jets d'eau dans toutes les directions (surtout vers le haut), qui jaillissent en 6 ticks,
     *       se détachent du centre, ploient sous leur poids, s'amincissent et se brisent en gouttes ;</li>
     *   <li>couronne : un cercle de jets inclinés vers le ciel autour du point d'impact, comme une énorme gerbe.</li>
     * </ol>
     */
    private void explosion(PoseStack pose, SubmitNodeCollector col, float t, float R, float g, int seed, int light) {
        if (t > 30.0F) {
            return;
        }
        float out = 1.0F - Mth.clamp((t - 14.0F) / 16.0F, 0.0F, 1.0F);
        // 1. coeur qui enfle et éclate
        if (t < 10.0F) {
            float k = t / 10.0F, sw = HumanDraw.easeOut(Math.min(1.0F, t / 3.0F));
            for (int i = 0; i < 7; i++) {
                double an = i * Math.PI * 2 / 7;
                float off = (i == 0 ? 0.0F : 0.55F * g) * (1.0F + 2.0F * k);
                float[] p = {(float) Math.cos(an) * off, (i % 2 == 0 ? 0.3F : -0.2F) * g * (1.0F + k), (float) Math.sin(an) * off};
                this.water.blob(pose, col, p, (0.6F + 0.9F * sw) * g * (1.0F - 0.5F * k), t * 25.0F + i * 51.0F, 1.0F - k, light);
            }
        }
        // 2. grands jets dans toutes les directions
        Random r = new Random(seed * 41L);
        int jets = 22;
        for (int i = 0; i < jets + 14; i++) {
            boolean crown = i >= jets;
            float[] dir;
            if (crown) { // couronne : cercle de jets inclinés vers le ciel
                double an = (i - jets) * Math.PI * 2 / 14 + r.nextDouble() * 0.2;
                dir = GoroLightning.normalize(new float[] {(float) Math.cos(an), 1.4F, (float) Math.sin(an)});
            } else {
                dir = GoroLightning.normalize(new float[] {r.nextFloat() * 2 - 1, r.nextFloat() * 1.6F - 0.35F, r.nextFloat() * 2 - 1});
            }
            float reach = R * (crown ? 0.55F : 0.5F + 0.35F * r.nextFloat());
            float grow = HumanDraw.easeOut(Math.min(1.0F, t / 6.0F));
            float head = reach * grow + Math.max(0.0F, t - 6.0F) * 0.12F * reach;     // le jet continue sur sa lancée
            float tail = reach * FishmanClient.ramp(t, 7.0F, 18.0F);                  // il se détache du centre
            float width = (crown ? 0.45F : 0.6F) * g * (1.0F - 0.6F * Math.min(1.0F, t / 22.0F));
            if (head - tail < 0.2F || out <= 0.01F) {
                continue;
            }
            float hz = Math.max(1.0E-3F, (float) Math.sqrt(dir[0] * dir[0] + dir[2] * dir[2]));
            float[] base = crown ? new float[] {dir[0] / hz * 0.25F * R, -0.3F * g, dir[2] / hz * 0.25F * R} : new float[] {0, 0, 0}; // couronne : autour du centre
            float[] prev = null;
            for (int k = 0; k <= 4; k++) {
                float along = tail + (head - tail) * k / 4.0F;
                float[] p = add(base, dir, along);
                p[1] -= 0.012F * t * t * (along / Math.max(0.1F, reach)); // il ploie sous son poids
                if (prev != null) {
                    float wk = width * (1.0F - 0.35F * k / 4.0F);
                    this.water.bar(pose, col, false, prev, p, wk, wk, i * 37.0F, argb(0.75F * out, k % 2 == 0 ? WATER : LIGHT), light);
                }
                prev = p;
            }
            // coiffe d'écume au bout, et gouttes qui s'en détachent
            this.water.cube(pose, col, true, prev, i * 50.0F + t * 20.0F, 30.0F, 15.0F, width * 1.3F, width * 0.9F, width * 1.3F, argb(0.85F * out, FOAM), light);
            if (t > 8.0F) {
                for (int k = 0; k < 2; k++) {
                    float[] p = add(prev, dir, -0.4F * g * (k + 1));
                    p[1] -= 0.15F * g * (t - 8.0F) * (k + 1) * 0.3F;
                    this.water.drop(pose, col, p, new float[] {dir[0], dir[1] - 0.6F, dir[2]}, 0.16F * g, 1.0F, out, light);
                }
            }
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(FishmanFx entity, float partialTick) {
        float[] d = entity.reach();
        AABB box = entity.getBoundingBox();
        if (entity.kind() == FishmanFx.WAVE || entity.kind() == FishmanFx.DROP || entity.kind() == FishmanFx.SPIRAL) {
            box = box.expandTowards(d[0], d[1], d[2]);
        }
        return box.inflate(entity.kind() == FishmanFx.TIDE ? entity.size() + 4.0 : entity.kind() == FishmanFx.BLAST ? entity.size() * 2.5 + 4.0
                : entity.kind() == FishmanFx.SPIRAL ? entity.size() * 2.0 + 4.0 : 4.0);
    }
}
