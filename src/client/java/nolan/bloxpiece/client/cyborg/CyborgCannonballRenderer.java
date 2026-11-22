package nolan.bloxpiece.client.cyborg;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.CyborgCannonball;

/**
 * Boulet du Cannon Arm : une grosse sphère de fer en cubes (un coeur et trois blocs croisés pour l'arrondir), cerclée d'une bande
 * rivetée (texture cyborg_cannonball.png, générée par src/blockbench/cyborg/make_cyborg_textures.py) ; il tourne sur lui-même
 * en vol.
 */
public class CyborgCannonballRenderer extends EntityRenderer<CyborgCannonball, CyborgCannonballRenderer.State> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("cyborg_cannonball"), "main");
    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/cyborg_cannonball.png");

    public static class State extends EntityRenderState {
        public float age;
    }

    private final ModelPart ball;

    public CyborgCannonballRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.ball = context.bakeLayer(LAYER).getChild("ball");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition ball = root.addOrReplaceChild("ball", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.ZERO);
        ball.addOrReplaceChild("round_x", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -2.4F, -2.4F, 7.0F, 4.8F, 4.8F), PartPose.ZERO);
        ball.addOrReplaceChild("round_y", CubeListBuilder.create().texOffs(0, 0).addBox(-2.4F, -3.5F, -2.4F, 4.8F, 7.0F, 4.8F), PartPose.ZERO);
        ball.addOrReplaceChild("round_z", CubeListBuilder.create().texOffs(0, 0).addBox(-2.4F, -2.4F, -3.5F, 4.8F, 4.8F, 7.0F), PartPose.ZERO);
        ball.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 16).addBox(-3.25F, -0.6F, -3.25F, 6.5F, 1.2F, 6.5F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CyborgCannonball entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.scale(1.6F, 1.6F, 1.6F); // gros boulet : environ 0,7 bloc
        pose.rotateDegrees(Axis.XP, state.age * 25.0F);
        pose.rotateDegrees(Axis.YP, state.age * 9.0F);
        collector.submitModelPart(this.ball, pose, RenderTypes.entityCutout(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, null,
                0xFFFFFFFF);
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
}
