package nolan.bloxpiece.client.humandrill;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import nolan.bloxpiece.BloxPiece;

/**
 * Géométrie du Humandrill (babouin sabreur de Kuraigana), environ 2 blocs, 1 texel par pixel, texture humandrill.png
 * (128x64).
 * FICHIER GÉNÉRÉ par src/blockbench/humandrill/MakeHumandrill.java (avec la texture) : modifier le générateur, pas ce fichier.
 * Hiérarchie : waist (bassin) > torse > tête > mâchoire ; torse > bras > avant-bras > main ; waist > cuisse > tibia > pied ;
 * waist > queue > bout de queue ; waist > pagne. Rotations : toutes calculées par HumandrillAnimator (repos à zéro).
 */
public final class HumandrillModels {

    public static final ModelLayerLocation HUMANDRILL = new ModelLayerLocation(BloxPiece.id("humandrill"), "main");

    private HumandrillModels() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition waist = root.addOrReplaceChild("waist", CubeListBuilder.create()
                .texOffs(52, 15).addBox(-5.0F, -1.5F, -3.5F, 10.0F, 4.0F, 7.0F)
                .texOffs(0, 38).addBox(-5.5F, -1.0F, -4.0F, 11.0F, 1.0F, 8.0F),
                PartPose.offset(0.0F, 9.0F, 1.0F));
        PartDefinition cloth = waist.addOrReplaceChild("cloth", CubeListBuilder.create()
                .texOffs(60, 47).addBox(-3.5F, 0.0F, -0.5F, 7.0F, 6.0F, 1.0F),
                PartPose.offset(0.0F, 0.0F, -3.9F));
        PartDefinition tail = waist.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(76, 47).addBox(-1.0F, -1.0F, -0.3F, 2.0F, 2.0F, 5.0F),
                PartPose.offset(0.0F, -0.6F, 3.3F));
        PartDefinition tail_tip = tail.addOrReplaceChild("tail_tip", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F)
                .texOffs(36, 55).addBox(-1.5F, -1.5F, 4.8F, 3.0F, 3.0F, 2.0F),
                PartPose.offset(0.0F, 0.0F, 4.6F));
        PartDefinition torso = waist.addOrReplaceChild("torso", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.5F, -11.0F, -4.2F, 13.0F, 7.0F, 8.0F)
                .texOffs(0, 15).addBox(-5.5F, -4.5F, -3.6F, 11.0F, 5.0F, 7.0F)
                .texOffs(78, 38).addBox(-4.0F, -9.5F, -4.8F, 8.0F, 7.0F, 1.0F)
                .texOffs(86, 15).addBox(-7.0F, -12.2F, -1.8F, 14.0F, 5.0F, 6.0F)
                .texOffs(26, 27).addBox(-6.0F, -10.0F, 3.2F, 12.0F, 8.0F, 2.0F),
                PartPose.offset(0.0F, -1.5F, 0.0F));
        PartDefinition head = torso.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(42, 0).addBox(-4.0F, -7.0F, -4.5F, 8.0F, 7.0F, 7.0F)
                .texOffs(98, 55).addBox(-4.5F, -6.0F, -5.2F, 9.0F, 1.0F, 1.0F)
                .texOffs(54, 27).addBox(-5.5F, -5.0F, -3.5F, 11.0F, 6.0F, 4.0F)
                .texOffs(90, 47).addBox(-3.0F, -8.0F, -3.5F, 6.0F, 1.0F, 6.0F)
                .texOffs(84, 27).addBox(-2.5F, -4.0F, -10.2F, 5.0F, 4.0F, 6.0F)
                .texOffs(20, 55).addBox(-1.5F, -4.7F, -9.6F, 3.0F, 1.0F, 5.0F)
                .texOffs(82, 55).addBox(-1.5F, -3.4F, -10.9F, 3.0F, 2.0F, 1.0F)
                .texOffs(64, 55).addBox(-5.2F, -6.8F, -0.5F, 1.0F, 2.0F, 2.0F)
                .texOffs(70, 55).addBox(4.2F, -6.8F, -0.5F, 1.0F, 2.0F, 2.0F)
                .texOffs(90, 55).addBox(-2.7F, -0.4F, -9.3F, 1.0F, 2.0F, 1.0F)
                .texOffs(94, 55).addBox(1.7F, -0.4F, -9.3F, 1.0F, 2.0F, 1.0F),
                PartPose.offset(0.0F, -11.0F, -2.5F));
        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create()
                .texOffs(96, 38).addBox(-2.0F, 0.0F, -5.8F, 4.0F, 2.0F, 6.0F)
                .texOffs(118, 55).addBox(-1.5F, -0.6F, -5.7F, 3.0F, 1.0F, 1.0F)
                .texOffs(46, 55).addBox(-2.5F, 1.2F, -4.6F, 5.0F, 1.0F, 4.0F),
                PartPose.offset(0.0F, 0.0F, -4.2F));
        PartDefinition right_arm = torso.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(72, 0).addBox(-2.5F, -1.5F, -2.5F, 5.0F, 9.0F, 5.0F)
                .texOffs(38, 38).addBox(-3.0F, -2.3F, -3.0F, 6.0F, 3.0F, 6.0F),
                PartPose.offset(-7.0F, -9.0F, -0.3F));
        PartDefinition right_forearm = right_arm.addOrReplaceChild("right_forearm", CubeListBuilder.create()
                .texOffs(36, 15).addBox(-2.0F, -0.3F, -2.0F, 4.0F, 8.0F, 4.0F)
                .texOffs(0, 55).addBox(-2.5F, 5.0F, -2.5F, 5.0F, 2.0F, 5.0F),
                PartPose.offset(0.0F, 7.5F, 0.0F));
        PartDefinition right_hand = right_forearm.addOrReplaceChild("right_hand", CubeListBuilder.create()
                .texOffs(0, 47).addBox(-2.0F, -0.3F, -2.5F, 4.0F, 3.0F, 5.0F)
                .texOffs(76, 55).addBox(1.6F, 0.2F, -2.0F, 1.0F, 2.0F, 2.0F),
                PartPose.offset(0.0F, 7.7F, 0.0F));
        PartDefinition left_arm = torso.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(72, 0).mirror().addBox(-2.5F, -1.5F, -2.5F, 5.0F, 9.0F, 5.0F)
                .texOffs(0, 27).mirror(false).addBox(-2.0F, -3.0F, -3.5F, 6.0F, 4.0F, 7.0F)
                .texOffs(18, 47).addBox(-1.5F, 0.6F, -3.2F, 5.0F, 2.0F, 6.0F),
                PartPose.offset(7.0F, -9.0F, -0.3F));
        PartDefinition left_forearm = left_arm.addOrReplaceChild("left_forearm", CubeListBuilder.create()
                .texOffs(36, 15).mirror().addBox(-2.0F, -0.3F, -2.0F, 4.0F, 8.0F, 4.0F)
                .texOffs(0, 55).addBox(-2.5F, 5.0F, -2.5F, 5.0F, 2.0F, 5.0F),
                PartPose.offset(-0.0F, 7.5F, 0.0F));
        PartDefinition left_hand = left_forearm.addOrReplaceChild("left_hand", CubeListBuilder.create()
                .texOffs(0, 47).mirror().addBox(-2.0F, -0.3F, -2.5F, 4.0F, 3.0F, 5.0F)
                .texOffs(76, 55).addBox(-2.6F, 0.2F, -2.0F, 1.0F, 2.0F, 2.0F),
                PartPose.offset(-0.0F, 7.7F, 0.0F));
        PartDefinition right_thigh = waist.addOrReplaceChild("right_thigh", CubeListBuilder.create()
                .texOffs(92, 0).addBox(-2.5F, -1.2F, -2.5F, 5.0F, 8.0F, 5.0F),
                PartPose.offset(-3.0F, 2.0F, 0.3F));
        PartDefinition right_shin = right_thigh.addOrReplaceChild("right_shin", CubeListBuilder.create()
                .texOffs(106, 27).addBox(-2.0F, -0.3F, -2.0F, 4.0F, 6.0F, 4.0F),
                PartPose.offset(0.0F, 6.2F, 0.0F));
        PartDefinition right_foot = right_shin.addOrReplaceChild("right_foot", CubeListBuilder.create()
                .texOffs(40, 47).addBox(-2.0F, 0.0F, -4.5F, 4.0F, 2.0F, 6.0F)
                .texOffs(0, 62).addBox(-2.0F, 0.9F, -5.4F, 4.0F, 1.0F, 1.0F),
                PartPose.offset(0.0F, 5.5F, 0.0F));
        PartDefinition left_thigh = waist.addOrReplaceChild("left_thigh", CubeListBuilder.create()
                .texOffs(92, 0).mirror().addBox(-2.5F, -1.2F, -2.5F, 5.0F, 8.0F, 5.0F),
                PartPose.offset(3.0F, 2.0F, 0.3F));
        PartDefinition left_shin = left_thigh.addOrReplaceChild("left_shin", CubeListBuilder.create()
                .texOffs(106, 27).mirror().addBox(-2.0F, -0.3F, -2.0F, 4.0F, 6.0F, 4.0F),
                PartPose.offset(-0.0F, 6.2F, 0.0F));
        PartDefinition left_foot = left_shin.addOrReplaceChild("left_foot", CubeListBuilder.create()
                .texOffs(40, 47).mirror().addBox(-2.0F, 0.0F, -4.5F, 4.0F, 2.0F, 6.0F)
                .texOffs(0, 62).addBox(-2.0F, 0.9F, -5.4F, 4.0F, 1.0F, 1.0F),
                PartPose.offset(-0.0F, 5.5F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
}
