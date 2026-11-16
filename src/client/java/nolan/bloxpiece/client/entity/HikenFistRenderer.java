package nolan.bloxpiece.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.HikenFist;

/**
 * Affiche le projectile de Hiken avec le modèle 3D HikenFistModel : pleine luminosité + rendu opaque (cutout), comme le soleil de
 * Dai Enkai: Entei. HikenFist est une entité projectile (pas un être vivant) : ce renderer est donc un EntityRenderer direct,
 * qui reproduit à la main l'orientation et l'échelle que MobRenderer appliquait à l'ancien poing (même repère, même rendu).
 */
public class HikenFistRenderer extends EntityRenderer<HikenFist, HikenFistRenderState> {

    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/hiken_fist.png");

    private final HikenFistModel model;

    public HikenFistRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new HikenFistModel(context.bakeLayer(HikenFistModel.LAYER));
    }

    @Override
    public HikenFistRenderState createRenderState() {
        return new HikenFistRenderState();
    }

    @Override
    public void extractRenderState(HikenFist entity, HikenFistRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        // Direction du poing = rotation de l'entité (envoyée par le serveur) : le poing regarde toujours dans le sens du vol
        state.yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        state.pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
    }

    @Override
    public void submit(HikenFistRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        float scale = (float) HikenFist.MODEL_SCALE;
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.yaw);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F); // le pivot du modèle (24 px) retombe sur la position de l'entité = centre du poing
        collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE),
                state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    protected AABB getBoundingBoxForCulling(HikenFist entity, float partialTick) {
        // Le poing et sa masse de flammes s'étendent sur ~30 blocs autour de l'entité (hitbox 1x1) : sans cette marge,
        // l'ensemble disparaîtrait dès que le centre du poing sort du champ de vision.
        return entity.getBoundingBox().inflate(HikenFist.MODEL_SCALE * 8.0);
    }
}
