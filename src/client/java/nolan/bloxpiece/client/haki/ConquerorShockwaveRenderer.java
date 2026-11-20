package nolan.bloxpiece.client.haki;

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
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.ConquerorShockwave;

/**
 * Onde de choc de l'activation du Haki des Rois (ConquerorShockwave) : rien pendant la montée, puis, depuis la décharge :
 * <ul>
 *   <li>l'anneau principal : coeur noir, gaine rouge vif translucide (même rendu que les éclairs du Haki des Rois), qui jaillit
 *       du joueur et s'étend jusqu'à 16 blocs en ralentissant ;</li>
 *   <li>deux anneaux secondaires rouge sombre, partis un peu après, plus petits ;</li>
 *   <li>le « mur d'air » : une bande verticale rouge sombre très transparente au front de l'onde, et un voile pâle qui ondule
 *       juste derrière (impression d'air repoussé et de légère distorsion) ;</li>
 *   <li>un anneau au ras du sol ;</li>
 *   <li>tout s'estompe progressivement, sans coupure.</li>
 * </ul>
 */
public class ConquerorShockwaveRenderer extends EntityRenderer<ConquerorShockwave, ConquerorShockwaveRenderer.WaveState> {

    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/haki_eye_trail.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int SEGMENTS = 64;

    public static class WaveState extends EntityRenderState {
        public float age = -1.0F;
    }

    private final ModelPart segment;

    public ConquerorShockwaveRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.segment = context.bakeLayer(HakiBoltRenderer.LAYER).getChild("segment");
    }

    @Override
    public WaveState createRenderState() {
        return new WaveState();
    }

    @Override
    public void extractRenderState(ConquerorShockwave entity, WaveState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.waveAge(partialTick);
    }

    @Override
    public void submit(WaveState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float age = state.age;
        if (age >= 0.0F) {
            RenderType type = RenderTypes.entityTranslucent(TEXTURE);
            float total = ConquerorShockwave.EXPAND_TICKS + ConquerorShockwave.FADE_TICKS;
            float fade = age < ConquerorShockwave.EXPAND_TICKS * 0.6F ? 1.0F : Math.max(0.0F, 1.0F - (age - ConquerorShockwave.EXPAND_TICKS * 0.6F)
                    / (total - ConquerorShockwave.EXPAND_TICKS * 0.6F));
            if (fade > 0.0F) {
                float r = (float) ConquerorShockwave.radiusAt(age);
                // anneau principal (à hauteur de taille) : coeur noir, gaine rouge vif
                ring(pose, collector, 0, type, r, 1.0F, 6.0F, 4.0F, argb(0.95F * fade, 0x050001));
                ring(pose, collector, 1, type, r, 1.0F, 12.0F, 9.0F, argb(0.45F * fade, 0xFF1A1A));
                // anneaux secondaires rouge sombre, partis après
                float r2 = (float) ConquerorShockwave.radiusAt(age - 2.0F) * 0.85F;
                if (r2 > 0.3F) {
                    ring(pose, collector, 1, type, r2, 0.6F, 7.0F, 5.0F, argb(0.4F * fade, 0x8A0010));
                }
                float r3 = (float) ConquerorShockwave.radiusAt(age - 4.0F) * 0.65F;
                if (r3 > 0.3F) {
                    ring(pose, collector, 1, type, r3, 1.5F, 5.0F, 4.0F, argb(0.35F * fade, 0x5A000A));
                }
                // mur d'air repoussé au front de l'onde, et voile pâle qui ondule juste derrière (distorsion)
                ring(pose, collector, 2, type, r, 1.0F, 2.0F, 34.0F, argb(0.12F * fade, 0xB0101A));
                float wobble = 0.15F * (float) Math.sin(age * 1.9F);
                ring(pose, collector, 2, type, Math.max(0.2F, r - 0.6F), 1.0F + wobble, 3.0F, 26.0F, argb(0.06F * fade, 0xFFE0E0));
                // anneau au ras du sol
                ring(pose, collector, 1, type, r * 0.97F, 0.05F, 16.0F, 1.0F, argb(0.5F * fade, 0xC0101C));
            }
        }
        super.submit(state, pose, collector, camera);
    }

    /**
     * Un anneau horizontal de rayon {@code radius} (blocs) à la hauteur {@code y} (blocs) : {@link #SEGMENTS} tronçons, chacun de
     * section {@code width} (px, vers l'extérieur) x {@code height} (px, vertical).
     */
    private void ring(PoseStack pose, SubmitNodeCollector collector, int order, RenderType type, float radius, float y, float width,
                      float height, int color) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2.0 * i / SEGMENTS, a1 = Math.PI * 2.0 * (i + 1) / SEGMENTS;
            float x0 = (float) Math.cos(a0) * radius, z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius, z1 = (float) Math.sin(a1) * radius;
            float dx = x1 - x0, dz = z1 - z0;
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            float overlap = 0.02F;
            pose.pushPose();
            pose.translate(x0, y, z0);
            pose.rotateDegrees(Axis.YP, (float) Math.toDegrees(Math.atan2(dx, dz)));
            pose.translate(0.0F, 0.0F, -overlap);
            pose.scale(width, height, (len + 2.0F * overlap) * 16.0F);
            collector.order(order).submitModelPart(this.segment, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
            pose.popPose();
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(ConquerorShockwave entity, float partialTick) {
        return entity.getBoundingBox().inflate(ConquerorShockwave.MAX_RADIUS + 2.0);
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
