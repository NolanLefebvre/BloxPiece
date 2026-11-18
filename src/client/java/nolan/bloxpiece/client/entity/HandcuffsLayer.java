package nolan.bloxpiece.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.handcuffs.Handcuffs;

/**
 * Menottes en Granite Marin portées par un joueur : un bracelet d'acier autour de chaque poignet et une courte chaîne entre les
 * deux. Les bracelets suivent les bras du joueur (mains dans le dos, voir PlayerModelMixin) : chacun est dessiné dans le repère
 * de son bras, au niveau du poignet. Texture : textures/entity/handcuffs.png (acier, reflets turquoise).
 */
public class HandcuffsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("handcuffs"), "main");
    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/handcuffs.png");

    private final ModelPart rightCuff;
    private final ModelPart leftCuff;
    private final ModelPart chain;

    public HandcuffsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ModelPart root) {
        super(parent);
        this.rightCuff = root.getChild("right_cuff");
        this.leftCuff = root.getChild("left_cuff");
        this.chain = root.getChild("chain");
    }

    /** Bracelets (repère du bras : le poignet est vers y = 8 px) et chaîne (part du poignet droit vers le poignet gauche). */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("right_cuff", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.6F, 7.4F, -2.6F, 5.2F, 1.8F, 5.2F), PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("left_cuff", CubeListBuilder.create().texOffs(0, 8)
                .addBox(-1.6F, 7.4F, -2.6F, 5.2F, 1.8F, 5.2F), PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("chain", CubeListBuilder.create().texOffs(0, 16)
                .addBox(1.6F, 7.9F, -0.4F, 2.6F, 0.8F, 0.8F), PartPose.offset(0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level == null ? null : level.getEntity(state.id);
        if (entity == null || state.isInvisible || !Handcuffs.isCuffed(entity)) {
            return;
        }
        PlayerModel model = this.getParentModel();
        RenderType type = RenderTypes.entityCutout(TEXTURE);
        int overlay = OverlayTexture.NO_OVERLAY;

        pose.pushPose();
        model.rightArm.translateAndRotate(pose);
        collector.submitModelPart(this.rightCuff, pose, type, light, overlay, null, 0xFFFFFFFF);
        collector.submitModelPart(this.chain, pose, type, light, overlay, null, 0xFFFFFFFF);
        pose.popPose();

        pose.pushPose();
        model.leftArm.translateAndRotate(pose);
        collector.submitModelPart(this.leftCuff, pose, type, light, overlay, null, 0xFFFFFFFF);
        pose.popPose();
    }
}
