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
import nolan.bloxpiece.fx.GoroColumn;

/**
 * Colonne de foudre géante d'El Thor (GoroColumn), en 3D :
 * <ul>
 *   <li>un pilier d'énergie à trois épaisseurs (coeur blanc-bleu, gaine bleue, halo profond), chacune faite de prismes
 *       octogonaux qui tournent : il s'abat du nuage jusqu'au sol en 3 ticks, palpite, puis s'amincit et s'éteint ;</li>
 *   <li>quatre éclairs épais qui s'enroulent en hélice autour du pilier (tracés qui crépitent à chaque tick) ;</li>
 *   <li>des ramifications qui jaillissent de ses flancs, des anneaux de choc qui dévalent la colonne, et deux évasements
 *       éblouissants : sous le nuage et au sol.</li>
 * </ul>
 */
public class GoroColumnRenderer extends EntityRenderer<GoroColumn, GoroColumnRenderer.State> {

    public static class State extends EntityRenderState {
        public float age;
        public int life = 20;
        public float radius = 3.0F;
        public float height = 24.0F;
        public int seed;
    }

    private static final int DESCEND = 3;
    private final ModelPart segment;

    public GoroColumnRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.segment = context.bakeLayer(HakiBoltRenderer.LAYER).getChild("segment");
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GoroColumn entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.life = Math.max(6, entity.life());
        state.radius = entity.radius();
        state.height = entity.height();
        state.seed = entity.getId();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float age = state.age, H = state.height;
        float down = H * Math.min(1.0F, age / DESCEND);
        float out = Math.max(0.0F, Math.min(1.0F, (state.life - age) / 8.0F));
        float pulse = 0.88F + 0.12F * (float) Math.sin(age * 2.3F);
        float R = state.radius * (0.35F + 0.65F * out) * pulse;
        if (out > 0.01F && down > 0.1F) {
            RenderType glow = RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
            // le pilier : trois épaisseurs de prismes octogonaux
            this.prism(pose, collector, 0, glow, R * 0.42F, down, age * 9.0F, GoroLightning.argb(0.95F * out, GoroLightning.CORE));
            this.prism(pose, collector, 1, glow, R * 0.78F, down, -age * 5.0F, GoroLightning.argb(0.42F * out, GoroLightning.SHEATH));
            this.prism(pose, collector, 2, glow, R * 1.25F, down, age * 2.0F, GoroLightning.argb(0.15F * out, GoroLightning.HAZE));
            Random jag = new Random(state.seed * 92_821L + (int) age * 15_485_863L);
            // éclairs en hélice autour du pilier
            List<GoroLightning.Path> helix = new ArrayList<>();
            for (int h = 0; h < 4; h++) {
                int n = Math.max(12, (int) (down * 1.2F));
                List<float[]> pts = new ArrayList<>(n);
                float[] widths = new float[n];
                float base = h * 1.5708F + age * 0.35F, twist = 0.22F + 0.04F * h;
                for (int k = 0; k < n; k++) {
                    float t = k / (float) (n - 1);
                    float y = -t * down;
                    float a = base + y * twist;
                    float rr = R * (0.85F + 0.25F * jag.nextFloat());
                    pts.add(new float[] {(float) Math.cos(a) * rr, y, (float) Math.sin(a) * rr});
                    widths[k] = (1.6F + 0.8F * (float) Math.abs(Math.sin(t * 11.0F + h + age))) * (float) Math.sqrt(state.radius);
                }
                helix.add(new GoroLightning.Path(pts, widths));
            }
            GoroLightning.draw(this.segment, pose, collector, helix, 1.0F, out, out);
            // ramifications qui jaillissent des flancs
            int forks = 6 + (int) (H / 5.0F);
            for (int i = 0; i < forks; i++) {
                if (jag.nextFloat() < 0.35F) {
                    continue;
                }
                float y = -jag.nextFloat() * down;
                double a = jag.nextDouble() * Math.PI * 2;
                float[] from = {(float) Math.cos(a) * R * 0.8F, y, (float) Math.sin(a) * R * 0.8F};
                float len = R * (1.2F + jag.nextFloat() * 2.0F);
                float[] d = {(float) Math.cos(a) * len, -len * (0.2F + jag.nextFloat() * 0.6F), (float) Math.sin(a) * len};
                List<GoroLightning.Path> paths = GoroLightning.bolt(from, d, 1.1F, age, state.seed + i, jag, jag, true);
                GoroLightning.draw(this.segment, pose, collector, paths, 1.0F, out, out);
            }
            // anneaux de choc qui dévalent la colonne
            if (age >= DESCEND) {
                for (int k = 0; k < 2; k++) {
                    float y = -((age * 4.0F + k * H * 0.5F) % H);
                    this.ring(pose, collector, glow, y, R * 1.5F, 10.0F * out, GoroLightning.argb(0.5F * out, GoroLightning.SHEATH));
                }
            }
            // évasements : sous le nuage et au sol (quand la colonne l'a atteint)
            this.flare(pose, collector, glow, 0.0F, R * 2.2F, out);
            if (age >= DESCEND) {
                this.flare(pose, collector, glow, -H + 0.3F, R * 3.2F, out);
            }
        }
        super.submit(state, pose, collector, camera);
    }

    /** Prisme octogonal vertical de rayon {@code r} (blocs), du point d'origine jusqu'à {@code length} blocs plus bas. */
    private void prism(PoseStack pose, SubmitNodeCollector collector, int order, RenderType type, float r, float length, float spin,
                       int color) {
        float w = r * 2.0F * 16.0F * 0.92F;
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.rotateDegrees(Axis.YP, spin + i * 45.0F);
            pose.rotateDegrees(Axis.XP, 90.0F); // l'axe du prisme pointe vers le bas
            pose.scale(w, w, length * 16.0F);
            collector.order(order).submitModelPart(this.segment, pose, type, GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                    color);
            pose.popPose();
        }
    }

    /** Anneau horizontal de rayon {@code r} à la hauteur {@code y}. */
    private void ring(PoseStack pose, SubmitNodeCollector collector, RenderType type, float y, float r, float width, int color) {
        int n = 24;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n, b = Math.PI * 2 * (i + 1) / n;
            GoroLightning.segment(this.segment, pose, collector, 1, type, new float[] {(float) Math.cos(a) * r, y, (float) Math.sin(a) * r},
                    new float[] {(float) Math.cos(b) * r, y, (float) Math.sin(b) * r}, width, color);
        }
    }

    /** Disque éblouissant (deux blocs plats croisés). */
    private void flare(PoseStack pose, SubmitNodeCollector collector, RenderType type, float y, float r, float alpha) {
        float w = r * 2.0F * 16.0F;
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.translate(0.0F, y, 0.0F);
            pose.rotateDegrees(Axis.YP, i * 45.0F);
            pose.translate(0.0F, 0.0F, -w / 32.0F);
            pose.scale(w, w * 0.08F, w);
            collector.order(2).submitModelPart(this.segment, pose, type, GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                    GoroLightning.argb((i == 0 ? 0.7F : 0.35F) * alpha, i == 0 ? GoroLightning.CORE : GoroLightning.SHEATH));
            pose.popPose();
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(GoroColumn entity, float partialTick) {
        return entity.getBoundingBox().inflate(entity.radius() * 5.0 + 4.0).expandTowards(0.0, -entity.height() - 2.0, 0.0);
    }
}
