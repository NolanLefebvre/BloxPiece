package nolan.bloxpiece.client.goro;

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
import nolan.bloxpiece.fx.GoroWave;

/**
 * Onde de choc électrique au sol (GoroWave) : un anneau d'énergie bleue (coeur blanc + gaine bleue) qui s'élargit très vite
 * puis ralentit, suivi d'un mur de poussière plus bas et plus large, avec des arcs qui fusent devant le front. Avec
 * {@code dome} : un dôme de lumière qui gonfle au centre puis s'éteint. Tout s'amincit et s'efface en fin de vie.
 */
public class GoroWaveRenderer extends EntityRenderer<GoroWave, GoroWaveRenderer.State> {

    public static class State extends EntityRenderState {
        public float age;
        public int life = 16;
        public float radius = 6.0F;
        public boolean dome;
        public int seed;
    }

    private static final int DUST = 0x8A8476;
    private final ModelPart segment;

    public GoroWaveRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.segment = context.bakeLayer(HakiBoltRenderer.LAYER).getChild("segment");
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GoroWave entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.life = Math.max(4, entity.life());
        state.radius = entity.radius();
        state.dome = entity.dome();
        state.seed = entity.getId();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float t = Math.min(1.0F, state.age / state.life);
        float ease = 1.0F - (float) Math.pow(1.0F - t, 2.5F);
        float out = 1.0F - t;
        float R = state.radius * ease;
        if (out > 0.01F && R > 0.2F) {
            RenderType glow = RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
            int n = Math.max(24, Math.min(128, (int) (state.radius * 3.0F)));
            float width = Math.max(2.0F, (8.0F + state.radius * 0.5F) * out);
            // mur de poussière (derrière le front, plus large et plus haut)
            this.ring(pose, collector, 2, glow, R * 0.92F, 0.5F, width * 2.4F, n, GoroLightning.argb(0.4F * out, DUST));
            // anneau d'énergie
            this.ring(pose, collector, 1, glow, R, 0.25F, width * 1.4F, n, GoroLightning.argb(0.6F * out, GoroLightning.SHEATH));
            this.ring(pose, collector, 0, RenderTypes.entityCutout(GoroLightning.TEXTURE), R, 0.25F, width * 0.4F, n,
                    GoroLightning.argb(1.0F, GoroLightning.CORE));
            // arcs qui fusent devant le front
            Random jag = new Random(state.seed * 7_919L + (int) state.age * 104_729L);
            int arcs = 3 + (int) (state.radius / 4.0F);
            for (int i = 0; i < arcs; i++) {
                if (jag.nextFloat() < 0.4F) {
                    continue;
                }
                double a = jag.nextDouble() * Math.PI * 2;
                float[] from = {(float) Math.cos(a) * R * 0.95F, 0.2F, (float) Math.sin(a) * R * 0.95F};
                float len = 1.5F + state.radius * 0.12F * jag.nextFloat();
                double b = a + (jag.nextDouble() - 0.5) * 1.2;
                List<GoroLightning.Path> paths = GoroLightning.bolt(from, new float[] {(float) Math.cos(b) * len, 0.3F * jag.nextFloat(),
                        (float) Math.sin(b) * len}, 0.8F, state.age, state.seed + i, jag, jag, true);
                GoroLightning.draw(this.segment, pose, collector, paths, 1.0F, out, out);
            }
            // dôme de lumière
            if (state.dome && t < 0.55F) {
                float k = t / 0.55F;
                float dr = state.radius * 0.45F * (float) Math.sqrt(k);
                float a = (1.0F - k) * (1.0F - k);
                this.shell(pose, collector, glow, 0, dr * 0.6F, state.age * 3.0F, GoroLightning.argb(0.85F * a, GoroLightning.CORE));
                this.shell(pose, collector, glow, 1, dr, -state.age * 2.0F, GoroLightning.argb(0.35F * a, GoroLightning.SHEATH));
            }
        }
        super.submit(state, pose, collector, camera);
    }

    private void ring(PoseStack pose, SubmitNodeCollector collector, int order, RenderType type, float r, float y, float width, int n,
                      int color) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n, b = Math.PI * 2 * (i + 1) / n;
            GoroLightning.segment(this.segment, pose, collector, order, type, new float[] {(float) Math.cos(a) * r, y, (float) Math.sin(a) * r},
                    new float[] {(float) Math.cos(b) * r, y, (float) Math.sin(b) * r}, width, color);
        }
    }

    /** Masse ronde : quatre blocs de demi-côté {@code r} tournés dans plusieurs directions. */
    private void shell(PoseStack pose, SubmitNodeCollector collector, RenderType type, int order, float r, float spin, int color) {
        float side = r * 2.0F * 16.0F * 0.86F;
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.rotateDegrees(Axis.YP, spin + i * 22.5F);
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
    protected AABB getBoundingBoxForCulling(GoroWave entity, float partialTick) {
        return entity.getBoundingBox().inflate(entity.radius() + 4.0, 6.0, entity.radius() + 4.0);
    }
}
