package nolan.bloxpiece.client.goro;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.client.haki.HakiBoltRenderer;
import nolan.bloxpiece.fx.GoroOrb;

/**
 * La sphère électrique du Raigo (GoroOrb), loin d'une boule statique :
 * <ul>
 *   <li>un noyau blanc-bleu éblouissant qui palpite ;</li>
 *   <li>deux couches d'énergie bleue (cubes orientés dans plusieurs directions, qui tournent en sens contraires : la masse
 *       d'énergie bouillonne) et un halo bleu profond ;</li>
 *   <li>des ARCS ÉLECTRIQUES qui courent en permanence sur toute la surface (grands cercles qui tournent, tracés qui crépitent
 *       à chaque tick) ;</li>
 *   <li>des RAMIFICATIONS qui se détachent de la surface et fouettent l'air autour ;</li>
 *   <li>des VEINES électriques qui traversent l'intérieur de la boule ;</li>
 *   <li>trois ANNEAUX d'éclairs inclinés qui gravitent autour d'elle, chacun sur son axe ;</li>
 *   <li>une COURONNE de pointes d'énergie qui jaillissent et se rétractent sans cesse, et une aura bleue très large.</li>
 * </ul>
 * Ce n'est pas une boule de feu recolorée : c'est une masse d'électricité vivante, de plus en plus chargée et agitée.
 * Tout grossit avec le rayon (la charge) ; les formes sont tirées d'après l'identifiant de l'entité (mêmes pour tous).
 */
public class GoroOrbRenderer extends EntityRenderer<GoroOrb, GoroOrbRenderer.State> {

    public static class State extends EntityRenderState {
        public float age;
        public float radius = 1.0F;
        public int seed;
    }

    private final ModelPart segment;

    public GoroOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.segment = context.bakeLayer(HakiBoltRenderer.LAYER).getChild("segment");
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GoroOrb entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.radius = entity.radius();
        state.seed = entity.getId();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float R = state.radius, age = state.age;
        RenderType glow = RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
        float pulse = 0.9F + 0.1F * (float) Math.sin(age * 0.9F);
        // couches d'énergie : cubes orientés (de loin, une masse ronde qui bouillonne)
        shell(pose, collector, glow, 0, R * 0.55F * pulse, age * 4.0F, GoroLightning.argb(0.95F, GoroLightning.CORE), 3);
        shell(pose, collector, glow, 1, R * 0.82F, -age * 2.2F, GoroLightning.argb(0.42F, GoroLightning.SHEATH), 5);
        shell(pose, collector, glow, 2, R * 1.04F, age * 1.3F, GoroLightning.argb(0.16F, GoroLightning.HAZE), 5);
        shell(pose, collector, glow, 2, R * 1.45F * (0.95F + 0.05F * (float) Math.sin(age * 0.5F)), -age * 0.7F,
                GoroLightning.argb(0.06F, GoroLightning.SHEATH), 3); // aura
        this.veins(pose, collector, state, R, age);
        this.rings(pose, collector, state, R, age);
        this.corona(pose, collector, state, R, age);
        // arcs qui courent sur la surface
        int crackle = (int) age;
        Random shape = new Random(state.seed * 1_000_003L);
        Random jag = new Random(state.seed * 92_821L + crackle * 15_485_863L);
        List<GoroLightning.Path> arcs = new ArrayList<>();
        int count = 8 + (int) Math.min(10, R * 1.5F);
        for (int i = 0; i < count; i++) {
            float[] axis = GoroLightning.normalize(new float[] {shape.nextFloat() - 0.5F, shape.nextFloat() - 0.5F, shape.nextFloat() - 0.5F});
            float[][] b = GoroLightning.perpendiculars(axis);
            float start = shape.nextFloat() * 6.28F + age * (0.08F + 0.06F * shape.nextFloat()) * (i % 2 == 0 ? 1 : -1);
            float span = 0.9F + shape.nextFloat() * 1.3F;
            int n = 10;
            List<float[]> pts = new ArrayList<>();
            float[] widths = new float[n];
            for (int k = 0; k < n; k++) {
                float a = start + span * k / (n - 1);
                float rr = R * (1.0F + (k == 0 || k == n - 1 ? 0.0F : (jag.nextFloat() - 0.3F) * 0.1F));
                float c = (float) Math.cos(a), s = (float) Math.sin(a);
                pts.add(new float[] {(b[0][0] * c + b[1][0] * s) * rr, (b[0][1] * c + b[1][1] * s) * rr, (b[0][2] * c + b[1][2] * s) * rr});
                widths[k] = (0.45F + 0.35F * (float) Math.sin(Math.PI * k / (n - 1))) * (float) Math.sqrt(R);
            }
            arcs.add(new GoroLightning.Path(pts, widths));
        }
        GoroLightning.draw(this.segment, pose, collector, arcs, 1.0F, 1.1F, 1.0F);
        // ramifications qui se détachent de la surface
        int forks = 4 + (int) Math.min(8, R);
        for (int i = 0; i < forks; i++) {
            if (jag.nextFloat() < 0.35F) {
                continue;
            }
            float[] dir = GoroLightning.normalize(new float[] {jag.nextFloat() - 0.5F, jag.nextFloat() - 0.5F, jag.nextFloat() - 0.5F});
            float[] from = {dir[0] * R * 0.98F, dir[1] * R * 0.98F, dir[2] * R * 0.98F};
            float len = R * (0.5F + jag.nextFloat() * 0.8F);
            float[] d = GoroLightning.deviate(dir, 0.4F, jag);
            var paths = GoroLightning.bolt(from, new float[] {d[0] * len, d[1] * len, d[2] * len}, 0.7F * (float) Math.sqrt(R), age,
                    state.seed + i, jag, jag, true);
            GoroLightning.draw(this.segment, pose, collector, paths, 1.0F, 1.0F, 1.0F);
        }
        super.submit(state, pose, collector, camera);
    }

    /** Veines électriques qui traversent l'intérieur de la boule (d'un point de la surface à un autre). */
    private void veins(PoseStack pose, SubmitNodeCollector collector, State state, float R, float age) {
        Random jag = new Random(state.seed * 4_099L + (int) age * 7_757L);
        int n = 3 + (int) Math.min(8, R * 0.7F);
        for (int i = 0; i < n; i++) {
            float[] a = GoroLightning.normalize(new float[] {jag.nextFloat() - 0.5F, jag.nextFloat() - 0.5F, jag.nextFloat() - 0.5F});
            float[] b = GoroLightning.normalize(new float[] {jag.nextFloat() - 0.5F, jag.nextFloat() - 0.5F, jag.nextFloat() - 0.5F});
            float[] from = {a[0] * R * 0.75F, a[1] * R * 0.75F, a[2] * R * 0.75F};
            float[] d = {b[0] * R * 0.75F - from[0], b[1] * R * 0.75F - from[1], b[2] * R * 0.75F - from[2]};
            List<GoroLightning.Path> paths = GoroLightning.bolt(from, d, 0.6F * (float) Math.sqrt(R), age, state.seed + 300 + i, jag, jag, false);
            GoroLightning.draw(this.segment, pose, collector, paths, 1.0F, 0.9F, 1.0F);
        }
    }

    /** Trois anneaux d'éclairs inclinés qui gravitent autour de la boule. */
    private void rings(PoseStack pose, SubmitNodeCollector collector, State state, float R, float age) {
        Random jag = new Random(state.seed * 6_151L + (int) age * 3_571L);
        List<GoroLightning.Path> rings = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            float tilt = 0.5F + k * 1.05F + age * 0.01F * (k + 1);
            float[] axis = GoroLightning.normalize(new float[] {(float) Math.sin(tilt), (float) Math.cos(tilt * 0.7F) + 0.6F,
                    (float) Math.cos(tilt)});
            float[][] b = GoroLightning.perpendiculars(axis);
            float rr = R * (1.25F + 0.1F * k);
            float spin = age * (0.12F - 0.05F * k);
            int n = 28;
            List<float[]> pts = new ArrayList<>(n + 1);
            float[] widths = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                float a = spin + (float) (Math.PI * 2 * i / n);
                float wob = 1.0F + (jag.nextFloat() - 0.5F) * 0.08F;
                float c = (float) Math.cos(a) * rr * wob, s = (float) Math.sin(a) * rr * wob;
                pts.add(new float[] {b[0][0] * c + b[1][0] * s, b[0][1] * c + b[1][1] * s, b[0][2] * c + b[1][2] * s});
                // l'anneau est plus épais d'un côté : on le voit tourner
                widths[i] = (0.35F + 0.45F * (0.5F + 0.5F * (float) Math.sin(a * 2.0F + k))) * (float) Math.sqrt(R);
            }
            rings.add(new GoroLightning.Path(pts, widths));
        }
        GoroLightning.draw(this.segment, pose, collector, rings, 1.0F, 0.8F, 1.0F);
    }

    /** Couronne : pointes d'énergie qui jaillissent de la surface et se rétractent. */
    private void corona(PoseStack pose, SubmitNodeCollector collector, State state, float R, float age) {
        Random shape = new Random(state.seed * 9_973L);
        RenderType glow = RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
        int n = 10 + (int) Math.min(18, R * 1.5F);
        for (int i = 0; i < n; i++) {
            float[] dir = GoroLightning.normalize(new float[] {shape.nextFloat() - 0.5F, shape.nextFloat() - 0.5F, shape.nextFloat() - 0.5F});
            float phase = shape.nextFloat() * 6.28F, speed = 0.4F + shape.nextFloat() * 0.5F;
            float len = R * (0.15F + 0.35F * Math.max(0.0F, (float) Math.sin(age * speed + phase)));
            if (len < R * 0.05F) {
                continue;
            }
            float[] a = {dir[0] * R * 0.95F, dir[1] * R * 0.95F, dir[2] * R * 0.95F};
            float[] b = {dir[0] * (R + len), dir[1] * (R + len), dir[2] * (R + len)};
            float w = 3.0F * (float) Math.sqrt(R);
            GoroLightning.segment(this.segment, pose, collector, 1, glow, a, b, w, GoroLightning.argb(0.45F, GoroLightning.SHEATH));
            GoroLightning.segment(this.segment, pose, collector, 0, glow, a, b, w * 0.35F, GoroLightning.argb(0.9F, GoroLightning.CORE));
        }
    }

    /** Une couche d'énergie : {@code n} cubes centrés, de demi-côté {@code r} (blocs), tournés dans plusieurs directions. */
    private void shell(PoseStack pose, SubmitNodeCollector collector, RenderType type, int order, float r, float spin, int color, int n) {
        float side = r * 2.0F * 16.0F * 0.86F;
        for (int i = 0; i < n; i++) {
            pose.pushPose();
            pose.rotateDegrees(Axis.YP, spin + i * (90.0F / n));
            pose.rotateDegrees(Axis.XP, 35.0F * i + spin * 0.5F);
            pose.rotateDegrees(Axis.ZP, 20.0F * i);
            pose.translate(0.0F, 0.0F, -side / 32.0F);
            pose.scale(side, side, side);
            collector.order(order).submitModelPart(this.segment, pose, type, GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                    color);
            pose.popPose();
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(GoroOrb entity, float partialTick) {
        return entity.getBoundingBox().inflate(entity.radius() * 3.0 + 3.0);
    }
}
