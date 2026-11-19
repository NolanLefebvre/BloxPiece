package nolan.bloxpiece.client.entity;

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
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.AceSlash;
import nolan.bloxpiece.fx.AceSlashShape;

/**
 * Affiche la Lame dévastatrice du Haki des Rois : UN SEUL modèle 3D (AceSlashModel, pleine luminosité), qui se déplace avec
 * l'entité. Aucune copie du modèle, aucune image rémanente.
 * <ul>
 *   <li>rien pendant l'armé (l'énergie autour du lanceur vient des particules et des éclairs envoyés par le serveur) ;</li>
 *   <li>en vol : le croissant couché dans le plan du coup, la bosse courbée (tranchant rouge) vers l'avant, incliné en diagonale
 *       (AceSlashShape.ROLL, même repère que les collisions). L'orientation est celle fixée au lancer : elle ne change plus
 *       pendant le trajet. Il jaillit en grandissant d'un coup ; son halo cramoisi pulse ;</li>
 *   <li>traînée lumineuse : un voile rouge très diffus, étiré vers l'arrière mais ATTACHÉ à la lame (il avance avec elle) ;</li>
 *   <li>éclairs noirs et rouges qui crépitent sur la lame (redessinés tous les 2 ticks, ils la suivent) ;</li>
 *   <li>en fin de course : tout s'estompe (AceSlash.fade).</li>
 * </ul>
 */
public class AceSlashRenderer extends EntityRenderer<AceSlash, AceSlashRenderer.SlashState> {

    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/ace_slash.png");
    /** Texture blanche unie des éclairs du Haki des Rois (celle de HakiBoltRenderer). */
    private static final Identifier BOLT_TEXTURE = BloxPiece.id("textures/entity/haki_eye_trail.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int GLOW_COLOR = 0xFF2A2A;
    private static final int BOLT_CORE = 0x050001;
    private static final int BOLT_SHEATH = 0xFF1A1A;
    /** Éclairs sur la lame : nombre et nombre de tronçons de chacun. */
    private static final int BOLTS = 6;
    private static final int BOLT_STEPS = 6;

    public static class SlashState extends EntityRenderState {
        public float yaw;
        public float pitch;
        public boolean flying;
        public float flightAge;
        public float fade = 1.0F;
        public int seed;
        public float scale = 1.0F;
    }

    private final AceSlashModel model;
    /** Le prisme unitaire des éclairs du Haki des Rois (calque de HakiBoltRenderer, réutilisé). */
    private final ModelPart segment;

    public AceSlashRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new AceSlashModel(context.bakeLayer(AceSlashModel.LAYER));
        this.segment = context.bakeLayer(nolan.bloxpiece.client.haki.HakiBoltRenderer.LAYER).getChild("segment");
    }

    @Override
    public SlashState createRenderState() {
        return new SlashState();
    }

    @Override
    public void extractRenderState(AceSlash entity, SlashState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        // orientation fixée au lancer (le serveur ne la change plus) : pas d'interpolation, aucune rotation parasite en vol
        state.yaw = entity.getYRot();
        state.pitch = entity.getXRot();
        state.flying = entity.flying();
        state.flightAge = entity.tickCount + partialTick - entity.launchedAt();
        state.fade = entity.fade(partialTick);
        state.seed = entity.getId();
        state.scale = entity.scale();
    }

    @Override
    public void submit(SlashState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.flying && state.fade > 0.0F) {
            RenderType type = RenderTypes.entityTranslucent(TEXTURE);
            // jaillissement : la lame passe de 60 % à sa taille réelle en 2 ticks ; en s'estompant elle s'affine un peu
            float grow = 0.6F + 0.4F * Math.min(1.0F, Math.max(0.0F, state.flightAge / 2.0F));
            float thin = 0.75F + 0.25F * state.fade;
            // charge : lame un peu plus grande, halo un peu plus intense (0 = sans charge, 1 = pleine charge)
            float charge = (state.scale - 1.0F) / AceSlash.MAX_SCALE_BONUS;
            float pulse = (0.75F + 0.25F * (float) Math.sin(state.flightAge * 2.3F)) * (1.0F + 0.3F * charge);
            pose.pushPose();
            pose.rotateDegrees(Axis.YP, -state.yaw);
            pose.rotateDegrees(Axis.XP, state.pitch);
            pose.rotateDegrees(Axis.ZP, AceSlashShape.ROLL);
            pose.scale(grow * state.scale, grow * thin * state.scale, grow * state.scale);
            // la lame : corps et bords déchiquetés, puis halo cramoisi (et un second halo plus large, très diffus)
            collector.order(0).submitModelPart(this.model.core, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                    argb(state.fade, 0xFFFFFF));
            collector.order(0).submitModelPart(this.model.spikes, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                    argb(state.fade, 0xFFFFFF));
            this.bolts(state, pose, collector);
            collector.order(2).submitModelPart(this.model.glow, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                    argb(0.38F * pulse * state.fade, GLOW_COLOR));
            pose.pushPose();
            pose.scale(1.1F, 1.6F, 1.3F);
            collector.order(3).submitModelPart(this.model.glow, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                    argb(0.14F * pulse * state.fade, GLOW_COLOR));
            pose.popPose();
            // traînée lumineuse attachée : le halo étiré vers l'arrière (il avance avec la lame, rien ne reste en place)
            pose.pushPose();
            pose.translate(0.0F, 0.0F, -1.6F);
            pose.scale(0.92F, 0.8F, 2.4F);
            collector.order(3).submitModelPart(this.model.glow, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                    argb(0.1F * state.fade * Math.min(1.0F, state.flightAge / 3.0F), GLOW_COLOR));
            pose.popPose();
            pose.popPose();
        }
        super.submit(state, pose, collector, camera);
    }

    /**
     * Éclairs du Haki des Rois sur la lame (même rendu que HakiBoltRenderer : coeur noir, gaine rouge vif) : chacun part d'un
     * point du croissant et zigzague autour, surtout dans son plan. Tirés à nouveau tous les 2 ticks : ils crépitent.
     */
    private void bolts(SlashState state, PoseStack pose, SubmitNodeCollector collector) {
        RenderType core = RenderTypes.entityTranslucent(BOLT_TEXTURE);
        Random random = new Random(state.seed * 7919L + (long) (state.flightAge / 2.0F) * 104_729L);
        for (int b = 0; b < BOLTS; b++) {
            double s = (random.nextDouble() * 2.0 - 1.0) * 0.85;
            Vec3 p = AceSlashShape.point(s, random.nextDouble());
            float[] at = {(float) p.x, (float) p.y, (float) p.z};
            double angle = random.nextDouble() * Math.PI * 2.0;
            float width = 0.9F + random.nextFloat() * 0.5F;
            for (int k = 0; k < BOLT_STEPS; k++) {
                angle += (random.nextDouble() - 0.5) * 1.8;
                float len = 0.25F + random.nextFloat() * 0.25F;
                float[] next = {at[0] + (float) Math.cos(angle) * len, at[1] + (random.nextFloat() - 0.5F) * 0.3F,
                        at[2] + (float) Math.sin(angle) * len};
                float w = width * (1.0F - k / (float) BOLT_STEPS * 0.7F);
                this.segment(pose, collector, 1, core, at, next, w * 1.6F, argb(state.fade, BOLT_CORE));
                this.segment(pose, collector, 2, core, at, next, w * 2.6F, argb(0.45F * state.fade, BOLT_SHEATH));
                at = next;
            }
        }
    }

    /** Prisme de section {@code width} (px) de a vers b (blocs, repère de la lame), prolongé d'une demi-largeur aux deux bouts. */
    private void segment(PoseStack pose, SubmitNodeCollector collector, int order, RenderType type, float[] a, float[] b, float width,
                         int color) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5F) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float overlap = width * 0.5F / 16.0F;
        pose.pushPose();
        pose.translate(a[0], a[1], a[2]);
        pose.rotateDegrees(Axis.YP, yaw);
        pose.rotateDegrees(Axis.XP, pitch);
        pose.translate(0.0F, 0.0F, -overlap);
        pose.scale(width, width, (len + 2.0F * overlap) * 16.0F);
        collector.order(order).submitModelPart(this.segment, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    @Override
    protected AABB getBoundingBoxForCulling(AceSlash entity, float partialTick) {
        // la lame fait 8 blocs d'envergure autour d'une entité 1x1 (et sa traînée s'étend derrière)
        return entity.getBoundingBox().inflate(7.0 * entity.scale());
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
