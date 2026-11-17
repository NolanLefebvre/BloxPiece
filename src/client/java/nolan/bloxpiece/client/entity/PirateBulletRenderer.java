package nolan.bloxpiece.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.client.entity.IceModels.IceRenderState;
import nolan.bloxpiece.fx.PirateBullet;

/** Affiche la balle de pistolet : une bille de plomb arrondie (cubes croisés), qui tourne sur elle-même en vol. */
public class PirateBulletRenderer extends EntityRenderer<PirateBullet, IceRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("pirate_bullet"), "main");
    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/pirate_bullet.png");

    /** Bille de plomb : un cube et deux cubes plus fins croisés, pour un contour presque rond. */
    public static class BulletModel extends EntityModel<IceRenderState> {
        private final ModelPart ball;

        public BulletModel(ModelPart root) {
            super(root, RenderTypes::entityCutout);
            this.ball = root.getChild("ball");
        }

        @Override
        public void setupAnim(IceRenderState state) {
            super.setupAnim(state);
            this.ball.xRot = state.ageInTicks * 0.8F;
            this.ball.yRot = state.ageInTicks * 0.5F;
        }

        public static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition ball = mesh.getRoot().addOrReplaceChild("ball", CubeListBuilder.create()
                            .texOffs(0, 0).addBox(-1.5F, -1.5F, -1.5F, 3, 3, 3)
                            .texOffs(0, 0).addBox(-1.0F, -2.0F, -1.0F, 2, 4, 2)
                            .texOffs(0, 0).addBox(-2.0F, -1.0F, -1.0F, 4, 2, 2)
                            .texOffs(0, 0).addBox(-1.0F, -1.0F, -2.0F, 2, 2, 4),
                    PartPose.offset(0.0F, 0.0F, 0.0F));
            return LayerDefinition.create(mesh, 16, 16);
        }
    }

    private final BulletModel model;

    public PirateBulletRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new BulletModel(context.bakeLayer(LAYER));
    }

    @Override
    public IceRenderState createRenderState() {
        return new IceRenderState();
    }

    @Override
    public void submit(IceRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.0F, 0.12F, 0.0F); // centre de la hitbox (0,25 bloc)
        collector.submitModel(this.model, state, pose, RenderTypes.entityCutout(TEXTURE), state.lightCoords,
                OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
}
