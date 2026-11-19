package nolan.bloxpiece.client.haki;

import java.util.Deque;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;

/**
 * Ruban lumineux ROUGE des yeux (Haki de l'Observation, pendant une esquive) : pour chaque oeil, une ligne continue relie les
 * positions successives des pupilles (HakiEyeTrail), dessinée dans le repère du modèle du joueur. Chaque tronçon est un fin
 * prisme étiré d'un point au suivant, qui chevauche légèrement ses voisins : aucune coupure, aucun point visible.
 * Deux passes : un coeur fin presque blanc-rose, et un halo rouge translucide plus large. Le ruban s'effile et s'efface vers la
 * queue ; une petite lueur marque la pupille. Ne dessine rien hors esquive.
 */
public class HakiEyeTrailLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("haki_eye_trail"), "main");
    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/haki_eye_trail.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Épaisseurs (pixels de modèle) du coeur et du halo, à la tête du ruban. */
    private static final float CORE_WIDTH = 0.14F;
    private static final float GLOW_WIDTH = 0.42F;

    /** Prisme d'un pixel de section, long d'un pixel vers +Z : étiré à chaque tronçon. */
    private final ModelPart segment;

    /** Modèles de l'image rémanente (skin standard et skin fin). */
    private final HakiAfterimage.GhostModel ghostWide;
    private final HakiAfterimage.GhostModel ghostSlim;

    public HakiEyeTrailLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ModelPart root, ModelPart wideRoot,
                             ModelPart slimRoot) {
        super(parent);
        this.segment = root.getChild("segment");
        this.ghostWide = new HakiAfterimage.GhostModel(wideRoot, false);
        this.ghostSlim = new HakiAfterimage.GhostModel(slimRoot, true);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("segment", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        // Observation active : les yeux brillent en rouge en permanence (concentration, pré-réflexion)
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        net.minecraft.world.entity.Entity entity = level == null ? null : level.getEntity(state.id);
        RenderType type = RenderTypes.entityTranslucent(TEXTURE);
        if (entity != null && Boolean.TRUE.equals(entity.getAttached(nolan.bloxpiece.registry.ModAttachments.HAKI_OBSERVATION))) {
            drawGlowingEyes(pose, collector, type, state.ageInTicks);
        }
        // image rémanente laissée par la dernière esquive
        if (entity != null) {
            HakiAfterimage.GhostModel ghost = HakiArmamentLayer.isSlim(this.getParentModel()) ? this.ghostSlim : this.ghostWide;
            HakiAfterimage.render(pose, collector, state, entity, ghost, state.skin.body().texturePath());
        }
        float partial = state.ageInTicks - (float) Math.floor(state.ageInTicks);
        float time = HakiEyeTrail.now(partial);
        Deque<HakiEyeTrail.Point>[] eyes = HakiEyeTrail.trail(state.id, time);
        if (eyes == null) {
            return;
        }
        for (int eyeIndex = 0; eyeIndex < eyes.length; eyeIndex++) {
            Deque<HakiEyeTrail.Point> eye = eyes[eyeIndex];
            if (eye.isEmpty()) {
                continue;
            }
            HakiEyeTrail.Point[] points = eye.toArray(new HakiEyeTrail.Point[0]);
            // + la position de l'oeil À CETTE IMAGE : le ruban part exactement du point rouge, sans décalage
            float[][] at = new float[points.length + 1][];
            for (int i = 0; i < points.length; i++) {
                HakiEyeTrail.Point p = points[i];
                float age = Math.max(0.0F, time - p.time());
                float drift = age * HakiEyeTrail.DRIFT;
                at[i] = new float[] {p.x() + p.bx() * drift, p.y() + p.by() * drift, p.z() + p.bz() * drift, age};
            }
            boolean dodging = time - points[points.length - 1].time() < 0.75F; // esquive en cours (relevé à cette image)
            if (dodging) {
                float[] live = HakiEyeTrail.eye(this.getParentModel().head, eyeIndex);
                at[points.length] = new float[] {live[0], live[1], live[2], 0.0F};
            } else {
                at = java.util.Arrays.copyOf(at, points.length); // fin de l'esquive : la traînée s'efface sur place
            }
            if (at.length < 2) {
                continue;
            }
            // halo d'abord, coeur par-dessus
            for (int pass = 0; pass < 2; pass++) {
                boolean glow = pass == 0;
                for (int i = 0; i < at.length - 1; i++) {
                    float[] a = at[i];
                    float[] b = at[i + 1];
                    // 1 = tête du ruban (sur l'oeil), 0 = bout de la queue
                    float fresh = 1.0F - Math.min(1.0F, (a[3] + b[3]) * 0.5F / HakiEyeTrail.LIFE);
                    if (fresh <= 0.0F) {
                        continue;
                    }
                    float taper = 0.25F + 0.75F * fresh;
                    float width = (glow ? GLOW_WIDTH : CORE_WIDTH) * taper;
                    int color = glow ? argb(0.38F * fresh, 0xFF2236) : argb(Math.min(1.0F, 0.35F + fresh), lerp(0xFF1030, 0xFFE6E2, fresh));
                    drawSegment(pose, collector, type, a, b, width, color);
                }
            }
        }
    }

    /**
     * Observation active : un petit point rouge brillant sur chaque oeil (lueur de perception discrète), posé à l'endroit exact
     * d'où part le ruban d'esquive, juste devant la seconde couche du skin. Dans le repère de la tête : suit tous ses mouvements.
     */
    private void drawGlowingEyes(PoseStack pose, SubmitNodeCollector collector, RenderType type, float age) {
        PlayerModel model = this.getParentModel();
        if (!model.head.visible) {
            return;
        }
        float pulse = 0.8F + 0.2F * (float) Math.sin(age * 0.35F);
        pose.pushPose();
        model.head.translateAndRotate(pose);
        for (int side = -1; side <= 1; side += 2) {
            float cx = side * HakiEyeTrail.EYE_X;
            // halo très léger, puis point vif
            drawBox(pose, collector, type, cx, HakiEyeTrail.EYE_Y, HakiEyeTrail.EYE_Z, 1.1F, 1.1F, 0.02F,
                    argb(0.35F * pulse, 0xFF1E30));
            drawBox(pose, collector, type, cx, HakiEyeTrail.EYE_Y, HakiEyeTrail.EYE_Z - 0.08F, 0.45F, 0.45F, 0.02F,
                    argb(pulse, 0xFF4A58));
        }
        pose.popPose();
    }

    /** Plaque centrée en (x, y), face avant en z (px, repère de la tête), de taille w x h x d (px). */
    private void drawBox(PoseStack pose, SubmitNodeCollector collector, RenderType type, float x, float y, float z, float w, float h,
                         float d, int color) {
        pose.pushPose();
        pose.translate(x / 16.0F, y / 16.0F, z / 16.0F);
        pose.scale(w, h, d);
        collector.submitModelPart(this.segment, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    /** Prisme de section {@code width} (px) de a vers b (px, repère du modèle), un peu prolongé aux deux bouts (jointures). */
    private void drawSegment(PoseStack pose, SubmitNodeCollector collector, RenderType type, float[] a, float[] b, float width,
                             int color) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4F) {
            dx = 0.0F;
            dy = 0.0F;
            dz = 1.0F;
            len = 0.0F;
        }
        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));
        float overlap = width * 0.5F;
        pose.pushPose();
        pose.translate(a[0] / 16.0F, a[1] / 16.0F, a[2] / 16.0F);
        pose.rotateDegrees(Axis.YP, yaw);
        pose.rotateDegrees(Axis.XP, pitch);
        pose.translate(0.0F, 0.0F, -overlap / 16.0F);
        pose.scale(width, width, len + 2.0F * overlap);
        collector.submitModelPart(this.segment, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerp(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }
}
