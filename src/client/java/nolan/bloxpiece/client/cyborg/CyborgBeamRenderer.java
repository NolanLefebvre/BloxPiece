package nolan.bloxpiece.client.cyborg;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.client.goro.GoroLightning;
import nolan.bloxpiece.client.haki.HakiBoltRenderer;
import nolan.bloxpiece.fx.CyborgBeam;

/**
 * Radical Beam (race Cyborg) en 3D :
 * <ul>
 *   <li>le RAYON : un vrai volume, trois épaisseurs (coeur blanc-jaune éblouissant, gaine jaune, halo orangé) de prismes qui
 *       tournent sur leur axe, dont le front file jusqu'à l'impact en {@link CyborgBeam#TRAVEL} ticks, puis palpite et
 *       s'amincit avant de s'éteindre ; des anneaux d'énergie glissent le long du rayon et des arcs jaunes s'en échappent ;</li>
 *   <li>l'ÉCLAT au départ (devant les mains) ;</li>
 *   <li>l'EXPLOSION à l'impact : une boule de lumière qui gonfle et pâlit, et une onde de choc au sol qui s'étend loin.</li>
 * </ul>
 */
public class CyborgBeamRenderer extends EntityRenderer<CyborgBeam, CyborgBeamRenderer.State> {

    public static class State extends EntityRenderState {
        public float age, radius = 1.5F;
        public int life = 26, seed;
        public float[] reach = {0, 0, 1};
        /** Le joueur local regarde son PROPRE rayon à la première personne (rendu adapté à sa caméra). */
        public boolean ownView;
    }

    /**
     * Vue à la première personne du LANCEUR : le rayon part de juste devant ses yeux, donc ses couches épaisses et l'éclat de
     * départ rempliraient tout l'écran. On les fait naître un peu plus loin devant lui ({@link #OWN_CORE} / {@link #OWN_SHEATH} /
     * {@link #OWN_HAZE} blocs), sans rien changer à leur épaisseur ni à l'explosion : il voit le rayon partir, sa trajectoire
     * jaune et l'impact. Les autres joueurs (et lui en 3e personne) voient le rendu complet.
     */
    private static final float OWN_CORE = 1.2F, OWN_SHEATH = 2.4F, OWN_HAZE = 4.0F;

    private static final int CORE = 0xFFFBE0, YELLOW = 0xFFE030, ORANGE = 0xFF8A1E;
    private final ModelPart segment;

    public CyborgBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.segment = context.bakeLayer(HakiBoltRenderer.LAYER).getChild("segment");
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
        state.life = Math.max(8, entity.life());
        state.reach = entity.reach();
        state.seed = entity.getId();
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        state.ownView = mc.player != null && entity.shooter() == mc.player.getId() && mc.getCameraEntity() == mc.player
                && mc.options.getCameraType().isFirstPerson();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float age = state.age;
        float[] d = state.reach;
        float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        if (len < 0.1F) {
            return;
        }
        float[] dir = {d[0] / len, d[1] / len, d[2] / len};
        float front = len * Math.min(1.0F, age / CyborgBeam.TRAVEL);
        float out = Mth.clamp((state.life - age) / 10.0F, 0.0F, 1.0F);
        float pulse = 0.9F + 0.1F * Mth.sin(age * 3.0F);
        float R = state.radius * (0.4F + 0.6F * out) * pulse;
        RenderType glow = RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
        float[] start = {0, 0, 0};
        float[] tip = {dir[0] * front, dir[1] * front, dir[2] * front};
        boolean own = state.ownView;
        if (out > 0.01F) {
            // le rayon : trois épaisseurs (en volume : chaque épaisseur est doublée, tournée, pour un faisceau bien rond) ;
            // vu par son lanceur à la première personne, chaque épaisseur naît un peu plus loin devant ses yeux
            beam(pose, collector, glow, ahead(dir, own ? OWN_CORE : 0.0F, front), tip, R * 0.45F, GoroLightning.argb(0.95F * out, CORE), 0);
            beam(pose, collector, glow, ahead(dir, own ? OWN_SHEATH : 0.0F, front), tip, R * 0.85F, GoroLightning.argb(0.5F * out, YELLOW), 1);
            beam(pose, collector, glow, ahead(dir, own ? OWN_HAZE : 0.0F, front), tip, R * 1.35F, GoroLightning.argb(0.18F * out, ORANGE), 2);
            // anneaux d'énergie qui filent le long du rayon
            float[][] perp = GoroLightning.perpendiculars(dir);
            for (int k = 0; k < 4; k++) {
                float at = ((age * 6.0F + k * front / 4.0F) % Math.max(1.0F, front));
                if (own && at < OWN_HAZE) {
                    continue; // pas d'anneau collé à la caméra du lanceur
                }
                float[] c = {dir[0] * at, dir[1] * at, dir[2] * at};
                ring(pose, collector, glow, c, perp, R * 1.25F, GoroLightning.argb(0.6F * out, YELLOW));
            }
            // arcs qui s'échappent du rayon
            Random random = new Random(state.seed * 13L + (int) (age * 2.0F));
            for (int k = 0; k < 6; k++) {
                float at = (own ? OWN_HAZE : 0.0F) + random.nextFloat() * Math.max(0.0F, front - (own ? OWN_HAZE : 0.0F));
                double a = random.nextDouble() * Math.PI * 2;
                float[] o = {perp[0][0] * (float) Math.cos(a) + perp[1][0] * (float) Math.sin(a),
                        perp[0][1] * (float) Math.cos(a) + perp[1][1] * (float) Math.sin(a),
                        perp[0][2] * (float) Math.cos(a) + perp[1][2] * (float) Math.sin(a)};
                float[] p = {dir[0] * at + o[0] * R, dir[1] * at + o[1] * R, dir[2] * at + o[2] * R};
                float l = R * (1.0F + random.nextFloat() * 1.5F);
                GoroLightning.segment(this.segment, pose, collector, 2, glow, p,
                        new float[] {p[0] + o[0] * l + dir[0] * l * 0.5F, p[1] + o[1] * l + dir[1] * l * 0.5F, p[2] + o[2] * l + dir[2] * l * 0.5F},
                        0.9F, GoroLightning.argb(0.8F * out, YELLOW));
            }
            // éclat au départ, devant les mains (vu par le lanceur : un peu plus loin devant lui, pour ne pas l'aveugler)
            sphere(pose, collector, glow, own ? ahead(dir, OWN_HAZE, front) : start, R * 1.4F * out, GoroLightning.argb((own ? 0.35F : 0.7F) * out, CORE));
        }
        // explosion à l'impact (quand le front est arrivé)
        float since = age - CyborgBeam.TRAVEL;
        if (since > 0.0F) {
            float k = Mth.clamp(since / (state.life - CyborgBeam.TRAVEL), 0.0F, 1.0F);
            float[] end = {d[0], d[1], d[2]};
            float ease = 1.0F - (1.0F - k) * (1.0F - k);
            float fade = 1.0F - k;
            sphere(pose, collector, glow, end, 2.0F + 7.0F * ease, GoroLightning.argb(0.55F * fade, YELLOW));
            sphere(pose, collector, glow, end, 1.0F + 4.0F * ease, GoroLightning.argb(0.9F * fade, CORE));
            sphere(pose, collector, glow, end, 3.0F + 9.0F * ease, GoroLightning.argb(0.2F * fade, ORANGE));
            // onde de choc au sol
            float[][] flat = {{1, 0, 0}, {0, 0, 1}};
            ring(pose, collector, glow, new float[] {end[0], end[1] + 0.2F, end[2]}, flat, 3.0F + 20.0F * ease,
                    GoroLightning.argb(0.8F * fade, YELLOW));
            ring(pose, collector, glow, new float[] {end[0], end[1] + 0.25F, end[2]}, flat, 2.0F + 14.0F * ease,
                    GoroLightning.argb(0.6F * fade, CORE));
        }
        super.submit(state, pose, collector, camera);
    }

    /** Point du rayon à {@code d} blocs du départ (sans dépasser le front {@code front}). */
    private static float[] ahead(float[] dir, float d, float front) {
        float k = Math.min(d, Math.max(0.0F, front - 0.1F));
        return new float[] {dir[0] * k, dir[1] * k, dir[2] * k};
    }

    /** Une épaisseur du rayon : deux prismes carrés tournés de 45° (section octogonale), largeur {@code r} (blocs). */
    private void beam(PoseStack pose, SubmitNodeCollector collector, RenderType type, float[] a, float[] b, float r, int color, int order) {
        float w = r * 2.0F * 16.0F;
        GoroLightning.segment(this.segment, pose, collector, order, type, a, b, w, color);
        GoroLightning.segment(this.segment, pose, collector, order, type, a, b, w * 0.82F, color);
    }

    /** Boule de lumière : six prismes croisés (une étoile pleine qui paraît ronde). */
    private void sphere(PoseStack pose, SubmitNodeCollector collector, RenderType type, float[] c, float r, int color) {
        if (r <= 0.01F) {
            return;
        }
        float[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0.7F, 0.7F, 0}, {0.7F, 0, 0.7F}, {0, 0.7F, 0.7F}};
        for (float[] ax : axes) {
            GoroLightning.segment(this.segment, pose, collector, 1, type, new float[] {c[0] - ax[0] * r, c[1] - ax[1] * r, c[2] - ax[2] * r},
                    new float[] {c[0] + ax[0] * r, c[1] + ax[1] * r, c[2] + ax[2] * r}, r * 16.0F * 1.15F, color);
        }
    }

    /** Anneau de rayon {@code r} centré en {@code c}, dans le plan des deux vecteurs de {@code perp}. */
    private void ring(PoseStack pose, SubmitNodeCollector collector, RenderType type, float[] c, float[][] perp, float r, int color) {
        int n = 28;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n, b = 2 * Math.PI * (i + 1) / n;
            float[] p = new float[3], q = new float[3];
            for (int k = 0; k < 3; k++) {
                p[k] = c[k] + (perp[0][k] * (float) Math.cos(a) + perp[1][k] * (float) Math.sin(a)) * r;
                q[k] = c[k] + (perp[0][k] * (float) Math.cos(b) + perp[1][k] * (float) Math.sin(b)) * r;
            }
            GoroLightning.segment(this.segment, pose, collector, 2, type, p, q, 1.6F, color);
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(CyborgBeam entity, float partialTick) {
        float[] d = entity.reach();
        return entity.getBoundingBox().expandTowards(d[0], d[1], d[2]).inflate(entity.radius() + 14.0);
    }
}
