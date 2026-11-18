package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;

import nolan.bloxpiece.BloxPiece;

/**
 * Le Tavernier, dans le style 3D des pirates du mod (même corps, mêmes proportions, même visage, même tissu, et les mêmes
 * reliefs que PirateGearModels : revers de manches et de bottes, écharpe de taille nouée, large ceinture de cuir à boucle en
 * laiton, bandana noué avec ses pans). Habillé en barman de village pirate : chemise de lin aux manches retroussées avec col
 * relevé, gilet de cuir ouvert surpiqué à boutons dorés et revers en relief, bandana vert sarcelle à pois, écharpe ocre,
 * moustache en volume aux pointes recourbées, barbiche et favoris. Ni toque ni tablier.
 * Texture 128x128 (tavern_keeper.png, générée par make_keeper3.py à partir des textures des pirates).
 * <p>
 * Animations : la tête suit le joueur (vanilla), respiration lente (tête, bras, pans du bandana et de l'écharpe) et posture
 * accueillante (bras légèrement écartés).
 */
public class TavernKeeperModel extends HumanoidModel<HumanoidRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("tavern_keeper"), "main");

    private final ModelPart bandanaTail;
    private final ModelPart sashTail;

    public TavernKeeperModel(ModelPart root) {
        super(root);
        this.bandanaTail = root.getChild("head").getChild("bandana_tail");
        this.sashTail = root.getChild("body").getChild("sash_tail");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.getChild("body");
        PartDefinition head = root.getChild("head");
        // ---- reliefs communs aux pirates (mêmes cubes et mêmes zones de texture que PirateGearModels)
        root.getChild("right_arm").addOrReplaceChild("cuff_right", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-3.0F, 4.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.4F)), PartPose.ZERO);
        root.getChild("left_arm").addOrReplaceChild("cuff_left", CubeListBuilder.create().texOffs(17, 32)
                .addBox(-1.0F, 4.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.4F)), PartPose.ZERO);
        root.getChild("right_leg").addOrReplaceChild("boot_cuff_right", CubeListBuilder.create().texOffs(34, 32)
                .addBox(-2.0F, 7.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.45F)), PartPose.ZERO);
        root.getChild("left_leg").addOrReplaceChild("boot_cuff_left", CubeListBuilder.create().texOffs(51, 32)
                .addBox(-2.0F, 7.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.45F)), PartPose.ZERO);
        body.addOrReplaceChild("sash_band", CubeListBuilder.create().texOffs(68, 32)
                .addBox(-4.0F, 8.0F, -2.0F, 8, 1, 4, new CubeDeformation(0.35F)), PartPose.ZERO);
        body.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(93, 32)
                .addBox(-4.0F, 9.0F, -2.0F, 8, 2, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        body.addOrReplaceChild("buckle", CubeListBuilder.create().texOffs(118, 32)
                .addBox(-1.5F, 9.0F, -2.95F, 3, 2, 1, new CubeDeformation(0.1F)), PartPose.ZERO);
        body.addOrReplaceChild("sash_knot", CubeListBuilder.create().texOffs(0, 39)
                .addBox(-1.0F, -1.0F, -0.5F, 2, 2, 1, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(2.6F, 8.6F, -2.5F, 0.0F, 0.0F, -0.2F));
        body.addOrReplaceChild("sash_tail", CubeListBuilder.create().texOffs(7, 39)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 4, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(2.2F, 9.4F, -2.7F, -0.08F, 0.0F, 0.12F));
        body.addOrReplaceChild("sash_tail_b", CubeListBuilder.create().texOffs(12, 39)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(3.2F, 9.4F, -2.6F, -0.05F, 0.0F, -0.18F));
        head.addOrReplaceChild("bandana", CubeListBuilder.create().texOffs(17, 39)
                .addBox(-4.0F, -8.0F, -4.0F, 8, 3, 8, new CubeDeformation(0.35F)), PartPose.ZERO);
        head.addOrReplaceChild("bandana_knot", CubeListBuilder.create().texOffs(50, 39)
                .addBox(-1.0F, -1.0F, 0.0F, 2, 2, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -6.6F, 4.4F, 0.0F, 0.0F, 0.785F));
        head.addOrReplaceChild("bandana_tail", CubeListBuilder.create().texOffs(57, 39)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 4, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-0.4F, -6.2F, 4.8F, 0.35F, 0.0F, 0.3F));
        head.addOrReplaceChild("bandana_tail_b", CubeListBuilder.create().texOffs(62, 39)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.5F, -6.2F, 4.8F, 0.25F, 0.0F, -0.32F));
        // ---- tenue de barman
        // gilet de cuir ouvert, surpiqué, boutons dorés ; revers en relief de chaque côté de l'ouverture
        body.addOrReplaceChild("vest", CubeListBuilder.create().texOffs(0, 64)
                .addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, new CubeDeformation(0.27F)), PartPose.ZERO);
        body.addOrReplaceChild("lapel_right", CubeListBuilder.create().texOffs(26, 64)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 6, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-1.6F, 0.2F, -2.55F, 0.0F, 0.0F, -0.18F));
        body.addOrReplaceChild("lapel_left", CubeListBuilder.create().texOffs(31, 64)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 6, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(1.6F, 0.2F, -2.55F, 0.0F, 0.0F, 0.18F));
        // col de chemise relevé
        body.addOrReplaceChild("collar_right", CubeListBuilder.create().texOffs(36, 64)
                .addBox(-1.0F, -0.5F, -0.5F, 2, 1, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-1.4F, 0.3F, -2.3F, 0.0F, 0.0F, 0.35F));
        body.addOrReplaceChild("collar_left", CubeListBuilder.create().texOffs(36, 64)
                .addBox(-1.0F, -0.5F, -0.5F, 2, 1, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(1.4F, 0.3F, -2.3F, 0.0F, 0.0F, -0.35F));
        // moustache en volume, pointes recourbées, barbiche et favoris
        head.addOrReplaceChild("mustache", CubeListBuilder.create().texOffs(44, 64)
                .addBox(-3.0F, -0.5F, -0.5F, 6, 1, 1, new CubeDeformation(0.08F)), PartPose.offset(0.0F, -2.6F, -4.35F));
        head.addOrReplaceChild("mustache_tip_left", CubeListBuilder.create().texOffs(60, 64)
                .addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(3.2F, -2.3F, -4.35F, 0.0F, 0.0F, 0.35F));
        head.addOrReplaceChild("mustache_tip_right", CubeListBuilder.create().texOffs(60, 64)
                .addBox(-0.5F, -2.0F, -0.5F, 1, 2, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-3.2F, -2.3F, -4.35F, 0.0F, 0.0F, -0.35F));
        head.addOrReplaceChild("goatee", CubeListBuilder.create().texOffs(66, 64)
                .addBox(-2.0F, 0.0F, -0.5F, 4, 2, 1, CubeDeformation.NONE), PartPose.offset(0.0F, -1.0F, -4.3F));
        head.addOrReplaceChild("sideburn_right", CubeListBuilder.create().texOffs(78, 64)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1, CubeDeformation.NONE), PartPose.offset(-4.3F, -5.0F, -2.5F));
        head.addOrReplaceChild("sideburn_left", CubeListBuilder.create().texOffs(78, 64)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1, CubeDeformation.NONE), PartPose.offset(4.3F, -5.0F, -2.5F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(HumanoidRenderState state) {
        super.setupAnim(state);
        float breath = Mth.sin(state.ageInTicks * 0.09F);
        // posture accueillante : bras légèrement écartés, qui suivent la respiration
        this.rightArm.zRot += 0.10F + 0.025F * breath;
        this.leftArm.zRot -= 0.10F + 0.025F * breath;
        this.rightArm.xRot -= 0.05F;
        this.leftArm.xRot -= 0.05F;
        // respiration : la tête monte et descend à peine, les pans du bandana et de l'écharpe bougent doucement
        this.head.y = 0.12F * breath; // position de repos de la tête : y = 0
        this.bandanaTail.xRot = 0.35F + 0.06F * breath;
        this.sashTail.xRot = -0.08F - 0.04F * breath;
    }
}
