package nolan.bloxpiece.client.cyborg;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import nolan.bloxpiece.BloxPiece;

/**
 * Bras gauche mécanique de la race Cyborg (rendu et animations : CyborgArmLayer). FICHIER GÉNÉRÉ par
 * src/blockbench/cyborg/make_cyborg_arm.py (avec la texture cyborg_arm.png, 64 x 64). Repère : celui du bras gauche vanilla
 * (pivot à l'épaule). Pièces animées : plate_front / plate_back / plate_outer / plate_inner (charnière en haut), barrel (canon
 * caché qui sort de l'avant-bras), cap_left / cap_right (volets de l'embout, qui s'écartent pour laisser sortir le canon).
 */
public final class CyborgArmModels {

    public static final ModelLayerLocation ARM = new ModelLayerLocation(BloxPiece.id("cyborg_arm"), "main");

    private CyborgArmModels() {
    }

    public static LayerDefinition arm() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition arm = root.addOrReplaceChild("arm", CubeListBuilder.create().texOffs(48, 0)
                .addBox(-0.6F, -1.4F, -1.4F, 3.2F, 5.4F, 2.8F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition pauldron = arm.addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.8F, -2.9F, -2.6F, 5.6F, 3.4F, 5.2F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition pauldron_ridge = arm.addOrReplaceChild("pauldron_ridge", CubeListBuilder.create().texOffs(40, 32)
                .addBox(-1.2F, -3.5F, -1.0F, 4.4F, 0.7F, 2.0F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition pauldron_rim = arm.addOrReplaceChild("pauldron_rim", CubeListBuilder.create().texOffs(30, 10)
                .addBox(-1.95F, 0.3F, -2.75F, 5.9F, 0.6F, 5.5F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition upper_plate_outer = arm.addOrReplaceChild("upper_plate_outer", CubeListBuilder.create().texOffs(20, 10)
                .addBox(2.5F, 1.0F, -1.6F, 0.9F, 3.6F, 3.2F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition upper_plate_front = arm.addOrReplaceChild("upper_plate_front", CubeListBuilder.create().texOffs(40, 26)
                .addBox(-0.4F, 1.0F, -2.1F, 2.8F, 3.4F, 0.7F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition upper_plate_back = arm.addOrReplaceChild("upper_plate_back", CubeListBuilder.create().texOffs(48, 26)
                .addBox(-0.4F, 1.0F, 1.4F, 2.8F, 3.4F, 0.7F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition piston_a = arm.addOrReplaceChild("piston_a", CubeListBuilder.create().texOffs(12, 19)
                .addBox(0.0F, 3.0F, 1.9F, 0.7F, 4.6F, 0.7F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition piston_b = arm.addOrReplaceChild("piston_b", CubeListBuilder.create().texOffs(16, 19)
                .addBox(1.6F, 3.0F, 1.9F, 0.7F, 4.6F, 0.7F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition piston_sleeve_a = arm.addOrReplaceChild("piston_sleeve_a", CubeListBuilder.create().texOffs(54, 32)
                .addBox(-0.1F, 2.6F, 1.8F, 0.9F, 1.6F, 0.9F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition piston_sleeve_b = arm.addOrReplaceChild("piston_sleeve_b", CubeListBuilder.create().texOffs(58, 32)
                .addBox(1.5F, 2.6F, 1.8F, 0.9F, 1.6F, 0.9F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition elbow = arm.addOrReplaceChild("elbow", CubeListBuilder.create().texOffs(20, 19)
                .addBox(-0.9F, 3.8F, -1.8F, 3.8F, 2.0F, 3.6F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition elbow_cap_outer = arm.addOrReplaceChild("elbow_cap_outer", CubeListBuilder.create().texOffs(16, 32)
                .addBox(2.85F, 4.0F, -1.0F, 0.7F, 1.6F, 2.0F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition elbow_cap_inner = arm.addOrReplaceChild("elbow_cap_inner", CubeListBuilder.create().texOffs(22, 32)
                .addBox(-1.25F, 4.0F, -1.0F, 0.7F, 1.6F, 2.0F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition forearm_core = arm.addOrReplaceChild("forearm_core", CubeListBuilder.create().texOffs(0, 19)
                .addBox(-0.5F, 5.6F, -1.3F, 3.0F, 4.0F, 2.6F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition cable = arm.addOrReplaceChild("cable", CubeListBuilder.create().texOffs(56, 26)
                .addBox(-1.05F, 5.8F, -0.3F, 0.5F, 3.6F, 0.6F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition plate_front = arm.addOrReplaceChild("plate_front", CubeListBuilder.create().texOffs(36, 19)
                .addBox(-1.9F, 0.0F, -0.75F, 3.8F, 4.2F, 0.75F, CubeDeformation.NONE), PartPose.offset(1.0F, 5.5F, -1.55F));
        PartDefinition plate_back = arm.addOrReplaceChild("plate_back", CubeListBuilder.create().texOffs(46, 19)
                .addBox(-1.9F, 0.0F, 0.0F, 3.8F, 4.2F, 0.75F, CubeDeformation.NONE), PartPose.offset(1.0F, 5.5F, 1.55F));
        PartDefinition plate_outer = arm.addOrReplaceChild("plate_outer", CubeListBuilder.create().texOffs(0, 10)
                .addBox(0.0F, 0.0F, -1.6F, 0.75F, 4.2F, 3.2F, CubeDeformation.NONE), PartPose.offset(2.55F, 5.5F, 0.0F));
        PartDefinition plate_inner = arm.addOrReplaceChild("plate_inner", CubeListBuilder.create().texOffs(10, 10)
                .addBox(-0.75F, 0.0F, -1.6F, 0.75F, 4.2F, 3.2F, CubeDeformation.NONE), PartPose.offset(-0.55F, 5.5F, 0.0F));
        PartDefinition barrel = arm.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(24, 0)
                .addBox(-1.2F, 0.0F, -1.2F, 2.4F, 7.0F, 2.4F, CubeDeformation.NONE), PartPose.offset(1.0F, 4.5F, 0.0F));
        PartDefinition barrel_x = barrel.addOrReplaceChild("barrel_x", CubeListBuilder.create().texOffs(36, 0)
                .addBox(-1.2F, 0.0F, -1.2F, 2.4F, 7.0F, 2.4F, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.785F, 0.0F));
        PartDefinition muzzle = barrel.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(0, 26)
                .addBox(-1.55F, 6.3F, -1.55F, 3.1F, 1.2F, 3.1F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition barrel_band = barrel.addOrReplaceChild("barrel_band", CubeListBuilder.create().texOffs(28, 32)
                .addBox(-1.4F, 2.0F, -1.4F, 2.8F, 0.8F, 2.8F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition wrist = arm.addOrReplaceChild("wrist", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-0.8F, 9.0F, -1.6F, 3.6F, 0.8F, 3.2F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition cap_left = arm.addOrReplaceChild("cap_left", CubeListBuilder.create().texOffs(16, 26)
                .addBox(0.0F, 0.0F, -1.55F, 1.95F, 1.1F, 3.1F, CubeDeformation.NONE), PartPose.offset(-0.95F, 9.4F, 0.0F));
        PartDefinition cap_right = arm.addOrReplaceChild("cap_right", CubeListBuilder.create().texOffs(28, 26)
                .addBox(-1.95F, 0.0F, -1.55F, 1.95F, 1.1F, 3.1F, CubeDeformation.NONE), PartPose.offset(2.95F, 9.4F, 0.0F));
        PartDefinition cap_vent_left = cap_left.addOrReplaceChild("cap_vent_left", CubeListBuilder.create().texOffs(0, 37)
                .addBox(0.3F, 1.1F, -1.0F, 1.4F, 0.3F, 2.0F, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition cap_vent_right = cap_right.addOrReplaceChild("cap_vent_right", CubeListBuilder.create().texOffs(8, 37)
                .addBox(-1.7F, 1.1F, -1.0F, 1.4F, 0.3F, 2.0F, CubeDeformation.NONE), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
