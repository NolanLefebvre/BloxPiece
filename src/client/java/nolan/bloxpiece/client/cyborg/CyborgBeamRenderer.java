package nolan.bloxpiece.client.cyborg;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.ability.CyborgRadicalBeamAbility;
import nolan.bloxpiece.client.goro.GoroLightning;
import nolan.bloxpiece.client.haki.HakiBoltRenderer;
import nolan.bloxpiece.client.human.HumanDraw;
import nolan.bloxpiece.fx.CyborgBeam;

/**
 * Radical Beam (race Cyborg) en 3D :
 * <ul>
 *   <li>le RAYON, plus fin que la zone touchée : trois épaisseurs (coeur blanc-jaune, gaine jaune, halo orangé) de prismes ;
 *       son front PARCOURT la distance jusqu'à l'impact (CyborgRadicalBeamAbility.BEAM_SPEED), précédé d'une tête de plasma ;
 *       des anneaux HEXAGONAUX en plaques glissent le long du rayon, une double bobine d'énergie s'enroule autour, des arcs
 *       s'en échappent ; il tient un instant après l'impact puis s'amincit et s'éteint ;</li>
 *   <li>l'EXPLOSION (même qualité de construction que celle du Poing du Conquérant, mais technologique et énergétique) :
 *       coeur de plasma aveuglant, colonne de lumière, BOULE DE PLASMA en voxels qui refroidit (blanc, jaune, orange, rouge)
 *       puis devient fumée métallique, DÔME DE PRESSION en anneaux, ondes de choc rondes au sol et dans le plan du tir
 *       (aucun hexagone à l'impact), arcs électriques, débris, braises, fumée, puis une ONDE
 *       ÉLECTROMAGNÉTIQUE secondaire.</li>
 * </ul>
 * Tout s'efface avant la fin de vie de l'entité (rien ne reste affiché).
 */
public class CyborgBeamRenderer extends EntityRenderer<CyborgBeam, CyborgBeamRenderer.State> {

    public static class State extends EntityRenderState {
        public float age, radius = 1.5F;
        public int life = 70, travel = 3, seed;
        public float[] reach = {0, 0, 1};
        /** Le joueur local regarde son PROPRE rayon à la première personne (rendu adapté à sa caméra). */
        public boolean ownView;
    }

    /**
     * Vue à la première personne du LANCEUR : ses couches épaisses naissent un peu plus loin devant ses yeux
     * ({@link #OWN_CORE} / {@link #OWN_SHEATH} / {@link #OWN_HAZE} blocs) ; il voit le rayon partir, sa trajectoire et l'impact.
     */
    private static final float OWN_CORE = 1.2F, OWN_SHEATH = 2.0F, OWN_HAZE = 3.2F;
    /** Le rayon dessiné est plus fin que la zone qu'il touche (BEAM_RADIUS) : il garde de la place pour l'explosion. */
    private static final float THIN = 0.55F;

    private static final int CORE = 0xFFFBE0, YELLOW = 0xFFE030, ORANGE = 0xFF8A1E, RED = 0xD8401E, SMOKE = 0x3E3A42;
    private final ModelPart segment, hex, plasma;
    private final HumanDraw draw;

    public CyborgBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.segment = context.bakeLayer(HakiBoltRenderer.LAYER).getChild("segment");
        this.hex = context.bakeLayer(CyborgFxModels.HEX).getChild("hex");
        this.plasma = context.bakeLayer(CyborgFxModels.PLASMA).getChild("cell");
        this.draw = new HumanDraw(context); // (débris, fumée, murs d'onde : outils d'effets partagés du mod)
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CyborgBeam entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.radius = entity.radius();
        state.travel = Math.max(1, entity.travel());
        state.life = Math.max(state.travel + 8, entity.life());
        state.reach = entity.reach();
        state.seed = entity.getId();
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        state.ownView = mc.player != null && entity.shooter() == mc.player.getId() && mc.getCameraEntity() == mc.player
                && mc.options.getCameraType().isFirstPerson();
    }

    private static int a(float alpha, int rgb) {
        return GoroLightning.argb(Mth.clamp(alpha, 0.0F, 1.0F), rgb);
    }

    private static float easeOut(float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        return 1.0F - (1.0F - k) * (1.0F - k);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float[] d = state.reach;
        float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        if (len >= 0.1F) {
            beam(state, pose, collector, d, len);
            if (state.age > state.travel) {
                explosion(state, pose, collector, d, len);
            }
        }
        super.submit(state, pose, collector, camera);
    }

    // ================================================================== le rayon

    private void beam(State s, PoseStack pose, SubmitNodeCollector col, float[] d, float len) {
        float age = s.age;
        float[] dir = {d[0] / len, d[1] / len, d[2] / len};
        float front = len * Math.min(1.0F, age / s.travel);
        float end = s.travel + CyborgRadicalBeamAbility.HOLD;
        float out = Mth.clamp((end + 8.0F - age) / 8.0F, 0.0F, 1.0F); // s'amincit et s'éteint après l'impact
        if (out <= 0.01F) {
            return;
        }
        float pulse = 0.9F + 0.1F * Mth.sin(age * 3.0F);
        float R = s.radius * THIN * (0.45F + 0.55F * out) * pulse;
        RenderType glow = RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
        float[] tip = {dir[0] * front, dir[1] * front, dir[2] * front};
        boolean own = s.ownView;
        float[][] perp = GoroLightning.perpendiculars(dir);
        // 1. trois épaisseurs (section octogonale)
        beamLayer(pose, col, glow, ahead(dir, own ? OWN_CORE : 0.0F, front), tip, R * 0.42F, a(0.95F * out, CORE), 0);
        beamLayer(pose, col, glow, ahead(dir, own ? OWN_SHEATH : 0.0F, front), tip, R * 0.8F, a(0.5F * out, YELLOW), 1);
        beamLayer(pose, col, glow, ahead(dir, own ? OWN_HAZE : 0.0F, front), tip, R * 1.25F, a(0.16F * out, ORANGE), 2);
        // 2. double bobine d'énergie qui s'enroule autour du rayon et défile vers l'avant
        float from = own ? OWN_HAZE : 0.3F;
        for (int h = 0; h < 2; h++) {
            float[] prev = null;
            for (float x = from; x <= front; x += 0.45F) {
                double ang = x * 1.3 - age * 0.9 + h * Math.PI;
                float[] p = HumanDraw.onCircle(new float[] {dir[0] * x, dir[1] * x, dir[2] * x}, perp[0], perp[1], R * 1.05F, ang);
                if (prev != null) {
                    GoroLightning.segment(this.segment, pose, col, 2, glow, prev, p, 0.9F, a(0.65F * out, h == 0 ? YELLOW : CORE));
                }
                prev = p;
            }
        }
        // 3. anneaux hexagonaux en plaques qui glissent le long du rayon
        for (int k = 0; k < 5; k++) {
            float at = (age * 5.0F + k * Math.max(1.0F, front) / 5.0F) % Math.max(1.0F, front);
            if (at < from) {
                continue;
            }
            hexModel(pose, col, 1, new float[] {dir[0] * at, dir[1] * at, dir[2] * at}, dir, R * 1.45F, age * 12.0F + k * 20.0F, 0.6F,
                    a(0.7F * out, k % 2 == 0 ? YELLOW : CORE));
        }
        // 4. arcs qui s'échappent du rayon
        Random random = new Random(s.seed * 13L + (int) (age * 2.0F));
        for (int k = 0; k < 5; k++) {
            float at = from + random.nextFloat() * Math.max(0.0F, front - from);
            double ang = random.nextDouble() * Math.PI * 2;
            float l = R * (1.0F + random.nextFloat() * 1.5F);
            float[] o = HumanDraw.onCircle(new float[] {0, 0, 0}, perp[0], perp[1], 1.0F, ang);
            float[] p = {dir[0] * at + o[0] * R, dir[1] * at + o[1] * R, dir[2] * at + o[2] * R};
            GoroLightning.segment(this.segment, pose, col, 2, glow, p,
                    new float[] {p[0] + o[0] * l + dir[0] * l * 0.5F, p[1] + o[1] * l + dir[1] * l * 0.5F, p[2] + o[2] * l + dir[2] * l * 0.5F},
                    0.8F, a(0.8F * out, YELLOW));
        }
        // 5. tête du rayon pendant le trajet : cellules de plasma qui tournent et un anneau hexagonal serré
        if (age < s.travel + 1.0F) {
            for (int k = 0; k < 3; k++) {
                plasmaCell(pose, col, 0, tip, age * 40.0F + k * 120.0F, k * 50.0F, age * 25.0F, R * (1.1F - 0.25F * k), a(0.9F, k == 0 ? CORE : YELLOW),
                        true, s.lightCoords);
            }
            hexModel(pose, col, 1, tip, dir, R * 1.8F, -age * 30.0F, 0.8F, a(0.85F, CORE));
        }
        // 6. sortie des mains : anneau hexagonal et petite cellule (vu par le lanceur : un peu plus loin devant lui)
        float[] start = own ? ahead(dir, OWN_HAZE, front) : new float[] {0, 0, 0};
        hexModel(pose, col, 1, start, dir, R * (1.3F + 0.2F * Mth.sin(age * 2.0F)), age * 18.0F, 0.7F, a((own ? 0.35F : 0.75F) * out, YELLOW));
        if (!own) {
            plasmaCell(pose, col, 0, start, age * 30.0F, 0.0F, age * 20.0F, R * 0.9F, a(0.8F * out, CORE), true, s.lightCoords);
        }
    }

    /** Point du rayon à {@code d} blocs du départ (sans dépasser le front {@code front}). */
    private static float[] ahead(float[] dir, float d, float front) {
        float k = Math.min(d, Math.max(0.0F, front - 0.1F));
        return new float[] {dir[0] * k, dir[1] * k, dir[2] * k};
    }

    /** Une épaisseur du rayon : deux prismes carrés tournés (section octogonale), rayon {@code r} (blocs). */
    private void beamLayer(PoseStack pose, SubmitNodeCollector collector, RenderType type, float[] a, float[] b, float r, int color, int order) {
        float w = r * 2.0F * 16.0F;
        GoroLightning.segment(this.segment, pose, collector, order, type, a, b, w, color);
        GoroLightning.segment(this.segment, pose, collector, order, type, a, b, w * 0.82F, color);
    }

    // ================================================================== l'explosion

    private void explosion(State s, PoseStack pose, SubmitNodeCollector col, float[] d, float len) {
        float since = s.age - s.travel;
        float L = Math.max(10.0F, s.life - s.travel);
        float fade = Mth.clamp((L - since) / 14.0F, 0.0F, 1.0F);
        float[] c = {d[0], d[1], d[2]};
        float[] dir = {d[0] / len, d[1] / len, d[2] / len};
        float[][] perp = GoroLightning.perpendiculars(dir);
        float B = (float) CyborgRadicalBeamAbility.BLAST_RADIUS;
        float[] up = {0, 1, 0};
        int light = s.lightCoords;
        RenderType glow = RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
        // 1. coeur de plasma aveuglant : un amas de cellules blanches qui éclate puis s'effondre
        if (since < 10.0F) {
            float k = since / 10.0F;
            float sc = 3.4F * easeOut(Math.min(1.0F, since / 2.5F)) * (1.0F - k * k);
            Random cr = new Random(s.seed * 3L);
            for (int i = 0; i < 9; i++) {
                float[] off = {(cr.nextFloat() - 0.5F) * 0.9F * sc, (cr.nextFloat() - 0.5F) * 0.9F * sc, (cr.nextFloat() - 0.5F) * 0.9F * sc};
                plasmaCell(pose, col, 0, HumanDraw.add(c, off, 1.0F), cr.nextFloat() * 90.0F + since * 25.0F, cr.nextFloat() * 90.0F, since * 15.0F,
                        sc * (0.9F + cr.nextFloat() * 0.5F), a(0.95F * (1.0F - k), CORE), true, light);
            }
        }
        // 2. colonne de lumière : l'énergie du rayon jaillit vers le ciel, puis s'amincit
        if (since < 16.0F) {
            float k = since / 16.0F;
            float h = 18.0F * easeOut(Math.min(1.0F, since / 5.0F));
            float w = 3.2F * (1.0F - k) * 16.0F;
            float[] top = {c[0], c[1] + h, c[2]};
            GoroLightning.segment(this.segment, pose, col, 0, glow, c, top, w * 0.35F, a(0.9F * (1.0F - k), CORE));
            GoroLightning.segment(this.segment, pose, col, 1, glow, c, top, w * 0.7F, a(0.45F * (1.0F - k), YELLOW));
            GoroLightning.segment(this.segment, pose, col, 2, glow, c, top, w, a(0.15F * (1.0F - k), ORANGE));
            for (int i = 0; i < 3; i++) {
                float y = (since * 2.5F + i * h / 3.0F) % Math.max(1.0F, h);
                roundRing(pose, col, 1, new float[] {c[0], c[1] + y, c[2]}, up, w / 16.0F * 0.6F, since * 20.0F + i * 30.0F, 0.6F,
                        a(0.7F * (1.0F - k), YELLOW));
            }
        }
        // 3. boule de plasma en voxels : projetée dans toutes les directions, refroidit puis devient fumée métallique
        Random r = new Random(s.seed * 19L);
        int cells = 54;
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < cells; i++) {
            // (tous les tirages d'abord : chaque cellule garde sa trajectoire d'une image à l'autre)
            float delay = r.nextFloat() * 3.0F, reachK = 0.55F + r.nextFloat() * 0.45F, big = 1.3F + r.nextFloat() * 1.3F;
            float rx = r.nextFloat() * 360.0F, ry = r.nextFloat() * 360.0F, spin = (r.nextFloat() - 0.5F) * 16.0F;
            float life = 34.0F + r.nextFloat() * 14.0F, flick = r.nextFloat() * 6.28F;
            float t = since - delay;
            if (t <= 0.0F || t > life) {
                continue;
            }
            double yy = 1.0 - (i + 0.5) * 2.0 / cells, rr = Math.sqrt(1.0 - yy * yy), th = golden * i;
            float[] di = {(float) (Math.cos(th) * rr), (float) yy * 0.75F + 0.12F, (float) (Math.sin(th) * rr)};
            float[] p = HumanDraw.add(c, di, B * 0.85F * reachK * easeOut(Math.min(1.0F, t / 11.0F)));
            p[1] += 0.04F * Math.max(0.0F, t - 8.0F) * (1.0F + reachK); // la fumée monte
            float heat = t / life;
            float sc = big * easeOut(Math.min(1.0F, t / 6.0F)) * (heat > 0.75F ? 1.0F - (heat - 0.75F) / 0.25F * 0.6F : 1.0F);
            if (heat < 0.6F) {
                int color = heat < 0.1F ? CORE : heat < 0.25F ? YELLOW : heat < 0.42F ? ORANGE : RED;
                float flicker = 0.85F + 0.15F * Mth.sin(since * 2.7F + flick); // le plasma grésille
                plasmaCell(pose, col, 1, p, rx + t * spin, ry, t * spin * 0.5F, sc, a(0.85F * flicker * (1.0F - heat * 0.6F) * fade, color),
                        true, light);
            } else {
                float smoke = 1.0F - (heat - 0.6F) / 0.4F;
                plasmaCell(pose, col, 3, p, rx + t * spin, ry, t * spin * 0.5F, sc * 1.2F, a(0.55F * smoke * fade, SMOKE), false, light);
            }
        }
        // 4. dôme de pression : anneaux ronds empilés en hémisphère, qui s'étend
        if (since < 15.0F) {
            float k = since / 15.0F, e = easeOut(k);
            float dome = 2.0F + 14.0F * e;
            for (int lat = 0; lat < 7; lat++) {
                double th = Math.toRadians(lat * 13.0);
                roundRing(pose, col, 3, new float[] {c[0], c[1] + dome * (float) Math.sin(th) - 0.3F, c[2]}, up, dome * (float) Math.cos(th),
                        lat * 9.0F + since * (lat % 2 == 0 ? 4.0F : -4.0F), 0.6F * (1.0F - k) + 0.2F,
                        a(0.32F * (1.0F - k) * (1.0F - lat * 0.09F), lat % 2 == 0 ? YELLOW : CORE));
            }
        }
        // 5. ondes de choc rondes : deux au sol, une dans le plan du tir
        if (since < 18.0F) {
            float k = since / 18.0F, e = easeOut(k);
            roundRing(pose, col, 2, new float[] {c[0], c[1] - 0.3F, c[2]}, up, 1.0F + 21.0F * e, since * 2.0F, 1.6F * (1.0F - k) + 0.3F,
                    a(0.85F * (1.0F - k), CORE));
            roundRing(pose, col, 2, new float[] {c[0], c[1] - 0.28F, c[2]}, up, 0.8F + 15.0F * e, 30.0F - since * 3.0F, 1.2F * (1.0F - k) + 0.3F,
                    a(0.75F * (1.0F - k), ORANGE));
            roundRing(pose, col, 2, HumanDraw.add(c, dir, -(0.5F + 2.0F * e)), dir, 1.0F + 11.0F * e, since * 4.0F, 1.3F * (1.0F - k) + 0.3F,
                    a(0.7F * (1.0F - k), YELLOW));
        }
        // 7. arcs électriques qui crépitent autour de l'impact (changent à chaque tick)
        if (since < 26.0F) {
            float k = since / 26.0F;
            Random ar = new Random(s.seed * 7L + (long) since);
            for (int i = 0; i < 8; i++) {
                double ang = ar.nextDouble() * Math.PI * 2;
                float r0 = 1.5F + ar.nextFloat() * B * 0.6F;
                float[] p = {c[0] + (float) Math.cos(ang) * r0, c[1] + ar.nextFloat() * 2.5F, c[2] + (float) Math.sin(ang) * r0};
                for (int sgm = 0; sgm < 4; sgm++) {
                    float[] q = {p[0] + (ar.nextFloat() - 0.5F) * 1.4F, p[1] + (ar.nextFloat() - 0.6F) * 1.2F, p[2] + (ar.nextFloat() - 0.5F) * 1.4F};
                    GoroLightning.segment(this.segment, pose, col, 1, glow, p, q, 1.1F, a(0.9F * (1.0F - k), sgm % 2 == 0 ? CORE : YELLOW));
                    p = q;
                }
            }
        }
        // 8. débris projetés loin, braises
        Random dr = new Random(s.seed * 31L);
        for (int i = 0; i < 32; i++) {
            float[] vel = {(dr.nextFloat() - 0.5F) * 0.85F, 0.3F + dr.nextFloat() * 0.5F, (dr.nextFloat() - 0.5F) * 0.85F};
            float yaw = dr.nextFloat() * 360.0F, sc = 0.35F + dr.nextFloat() * 0.5F;
            float[] p = ballistic(new float[] {c[0], c[1] - 0.3F, c[2]}, vel, since, 0.045F, c[1] - 0.45F);
            float shrink = 1.0F - Mth.clamp((since - 34.0F) / 8.0F, 0.0F, 1.0F);
            this.draw.rock(pose, col, p, since * 20.0F, yaw, since * 13.0F, sc * shrink, i % 2, light);
        }
        if (since < 22.0F) {
            Random er = new Random(s.seed * 29L);
            for (int i = 0; i < 28; i++) {
                float[] vel = {(er.nextFloat() - 0.5F) * 0.8F, er.nextFloat() * 0.55F + 0.12F, (er.nextFloat() - 0.5F) * 0.8F};
                float[] p = ballistic(c, vel, since, 0.03F, c[1] - 0.5F);
                float[] q = ballistic(c, vel, Math.max(0.0F, since - 1.3F), 0.03F, c[1] - 0.5F);
                GoroLightning.segment(this.segment, pose, col, 1, glow, q, p, 1.1F,
                        a(0.9F * (1.0F - since / 22.0F), i % 3 == 0 ? CORE : i % 3 == 1 ? YELLOW : ORANGE));
            }
        }
        // 9. fumée sombre qui s'étale et monte
        float kd = Mth.clamp(since / 42.0F, 0.0F, 1.0F), ed = easeOut(kd);
        for (int i = 0; i < 18; i++) {
            double ang = i * Math.PI * 2 / 18 + 0.2;
            float rad = 1.0F + B * 1.1F * ed;
            this.draw.dust(pose, col, new float[] {c[0] + (float) Math.cos(ang) * rad, c[1] - 0.1F + 1.0F * ed, c[2] + (float) Math.sin(ang) * rad},
                    i * 41.0F + since * 2.0F, 1.0F + 1.7F * ed, 0.6F * (1.0F - kd) * fade, i % 3 == 0 ? 0x6A6470 : 0x8C8690, light);
        }
        // 10. ONDE ÉLECTROMAGNÉTIQUE secondaire : un mur d'énergie fin, un anneau et des arcs le long du front
        float emp = since - 10.0F;
        if (emp > 0.0F && emp < 20.0F) {
            float k = emp / 20.0F, e = easeOut(k);
            float rr = 2.0F + 22.0F * e;
            this.draw.wall(pose, col, 2, new float[] {c[0], c[1] - 0.45F, c[2]}, rr, 0.0F, 1.2F * (1.0F - k) + 0.15F,
                    a(0.3F * (1.0F - k), YELLOW), a(0.75F * (1.0F - k), CORE), 48);
            roundRing(pose, col, 2, new float[] {c[0], c[1] - 0.4F, c[2]}, up, rr * 0.96F, emp * 5.0F, 1.0F * (1.0F - k) + 0.3F,
                    a(0.6F * (1.0F - k), YELLOW));
            Random ar = new Random(s.seed * 11L + (long) emp);
            for (int i = 0; i < 10; i++) {
                double ang = ar.nextDouble() * Math.PI * 2;
                float[] p = {c[0] + (float) Math.cos(ang) * rr, c[1] - 0.3F, c[2] + (float) Math.sin(ang) * rr};
                float[] q = {p[0] + (ar.nextFloat() - 0.5F) * 1.2F, p[1] + 0.6F + ar.nextFloat() * 0.8F, p[2] + (ar.nextFloat() - 0.5F) * 1.2F};
                GoroLightning.segment(this.segment, pose, col, 1, glow, p, q, 1.0F, a(0.85F * (1.0F - k), CORE));
            }
        }
    }

    /**
     * Anneau d'onde de choc ROND (prismes lumineux) de rayon {@code radius} autour de {@code axis} : l'explosion n'a aucune forme
     * hexagonale. {@code spin} (degrés) décale les jointures, {@code thick} règle l'épaisseur.
     */
    private void roundRing(PoseStack pose, SubmitNodeCollector col, int order, float[] c, float[] axis, float radius, float spin, float thick,
                           int color) {
        if (radius <= 0.01F || (color >>> 24) == 0) {
            return;
        }
        float[][] perp = GoroLightning.perpendiculars(GoroLightning.normalize(axis));
        int n = Math.max(24, Math.min(64, (int) (radius * 6.0F)));
        double off = Math.toRadians(spin);
        RenderType glow = RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
        for (int i = 0; i < n; i++) {
            double a0 = off + 2 * Math.PI * i / n, a1 = off + 2 * Math.PI * (i + 1) / n;
            GoroLightning.segment(this.segment, pose, col, order, glow, HumanDraw.onCircle(c, perp[0], perp[1], radius, a0),
                    HumanDraw.onCircle(c, perp[0], perp[1], radius, a1), 2.2F * thick + 0.5F, color);
        }
    }

    /** Position balistique d'un fragment lancé à {@code vel} depuis {@code p0}, {@code t} ticks plus tard, posé au sol {@code floor}. */
    private static float[] ballistic(float[] p0, float[] vel, float t, float gravity, float floor) {
        float y = p0[1] + vel[1] * t - 0.5F * gravity * t * t;
        float k = t;
        if (y < floor) {
            float aa = 0.5F * gravity, b = -vel[1], cc = floor - p0[1];
            float disc = b * b - 4 * aa * cc;
            k = disc > 0 ? Math.max(0.0F, Math.min(t, (float) ((-b + Math.sqrt(disc)) / (2 * aa)))) : t;
            y = floor;
        }
        return new float[] {p0[0] + vel[0] * k, y, p0[2] + vel[2] * k};
    }

    // ================================================================== modèles

    /** Anneau hexagonal en plaques (CyborgFxModels.HEX, apothème 6 px) de rayon {@code radius} blocs autour de {@code axis}. */
    private void hexModel(PoseStack pose, SubmitNodeCollector col, int order, float[] c, float[] axis, float radius, float spin, float thick,
                          int color) {
        if (radius <= 0.01F || (color >>> 24) == 0) {
            return;
        }
        float k = radius / 0.375F;
        pose.pushPose();
        pose.translate(c[0], c[1], c[2]);
        HumanDraw.orientY(pose, axis);
        pose.rotateDegrees(Axis.YP, spin);
        pose.scale(k, thick * Math.max(1.0F, k * 0.35F), k);
        col.order(order).submitModelPart(this.hex, pose, RenderTypes.entityTranslucentEmissive(CyborgFxModels.HEX_TEXTURE),
                GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    /** Cellule de plasma (CyborgFxModels.PLASMA, 1 = 6 px) : lumineuse (plasma) ou éclairée par le monde (fumée). */
    private void plasmaCell(PoseStack pose, SubmitNodeCollector col, int order, float[] c, float rx, float ry, float rz, float scale, int color,
                            boolean emissive, int light) {
        if (scale <= 0.01F || (color >>> 24) == 0) {
            return;
        }
        pose.pushPose();
        pose.translate(c[0], c[1], c[2]);
        pose.rotateDegrees(Axis.YP, ry);
        pose.rotateDegrees(Axis.XP, rx);
        pose.rotateDegrees(Axis.ZP, rz);
        pose.scale(scale, scale, scale);
        col.order(order).submitModelPart(this.plasma, pose, emissive ? RenderTypes.entityTranslucentEmissive(CyborgFxModels.PLASMA_TEXTURE)
                : RenderTypes.entityTranslucent(CyborgFxModels.PLASMA_TEXTURE), emissive ? GoroLightning.FULL_BRIGHT : light,
                OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    @Override
    protected AABB getBoundingBoxForCulling(CyborgBeam entity, float partialTick) {
        float[] d = entity.reach();
        return entity.getBoundingBox().expandTowards(d[0], d[1], d[2]).inflate(entity.radius() + 24.0);
    }
}
