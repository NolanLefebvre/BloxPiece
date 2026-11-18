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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.Meteor;

/**
 * Affiche un météore de la Pluie de météores avec MeteorModel, en pleine luminosité comme Hiken et Dai Enkai: Entei :
 * le noyau rocheux en rendu opaque (cutout), puis les flammes en rendu translucide émissif (elles brillent et laissent voir
 * le noyau). Le modèle est centré sur le centre de la hitbox, sa traînée est placée derrière lui selon la direction de chute.
 * Chaque météore a sa propre taille (de x{@link #MIN_SCALE} à x{@link #MAX_SCALE}), sa vitesse et son sens de rotation, tirés de
 * son UUID : purement visuel, la hitbox et les dégâts ne changent pas.
 */
public class MeteorRenderer extends EntityRenderer<Meteor, MeteorRenderState> {

    private static final Identifier CORE_TEXTURE = BloxPiece.id("textures/entity/meteor_core.png");
    private static final Identifier FLAME_TEXTURE = BloxPiece.id("textures/entity/meteor_flames.png");

    /** Taille de base du modèle (enveloppe extérieure : ~1,1 bloc de rayon à x1). */
    private static final float MODEL_SCALE = 1.4F;
    private static final float MIN_SCALE = 0.8F;
    private static final float MAX_SCALE = 1.3F;

    private final MeteorModel core;
    private final MeteorModel flames;

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.core = new MeteorModel(context.bakeLayer(MeteorModel.CORE));
        this.flames = new MeteorModel(context.bakeLayer(MeteorModel.FLAMES));
    }

    @Override
    public MeteorRenderState createRenderState() {
        return new MeteorRenderState();
    }

    @Override
    public void extractRenderState(Meteor entity, MeteorRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;

        // Direction de la chute (vers le bas si le météore est immobile)
        Vec3 dir = entity.getDeltaMovement();
        if (dir.lengthSqr() < 1.0E-6) {
            dir = new Vec3(0.0, -1.0, 0.0);
        }
        dir = dir.normalize();
        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        state.yaw = (float) Math.toDegrees(Math.atan2(-dir.x, -dir.z));
        state.pitch = (float) Math.toDegrees(Math.atan2(dir.y, horizontal));

        // Variations propres à ce météore (même résultat chez tous les joueurs)
        long bits = entity.getUUID().getLeastSignificantBits();
        state.scale = MIN_SCALE + (MAX_SCALE - MIN_SCALE) * ((bits & 0xFFFF) / 65535.0F);
        state.spin = 0.75F + 0.6F * (((bits >>> 16) & 0xFFFF) / 65535.0F);
        state.spinSign = ((bits >>> 48) & 1L) == 0L ? 1.0F : -1.0F;
        state.phase = ((bits >>> 32) & 0xFFFF) / 65535.0F * 100.0F;
    }

    @Override
    public void submit(MeteorRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        float scale = MODEL_SCALE * state.scale;
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.5F, 0.0F); // centre de la hitbox 1x1
        // L'avant du modèle (-Z) regarde dans le sens de la chute : la traînée (+Z) part derrière
        poseStack.rotateDegrees(Axis.YP, state.yaw);
        poseStack.rotateDegrees(Axis.XP, state.pitch);
        poseStack.scale(scale, scale, scale);
        collector.submitModel(this.core, state, poseStack, RenderTypes.entityCutout(CORE_TEXTURE),
                state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        collector.submitModel(this.flames, state, poseStack, RenderTypes.entityTranslucentEmissive(FLAME_TEXTURE),
                state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    protected AABB getBoundingBoxForCulling(Meteor entity, float partialTick) {
        // Le modèle et sa traînée dépassent largement de la hitbox 1x1
        return entity.getBoundingBox().inflate(4.0);
    }
}
