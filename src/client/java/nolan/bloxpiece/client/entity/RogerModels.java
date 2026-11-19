package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import nolan.bloxpiece.BloxPiece;

/**
 * Géométrie de Gol D. Roger, le Roi des Pirates : même construction que les pirates et les Marines (corps humanoïde classique +
 * équipement 3D attaché aux parties du corps, il suit les animations). Texture 128x128 : peau en haut à gauche (disposition
 * classique 64x32), équipement en dessous. FICHIER GÉNÉRÉ par src/blockbench/make_gol_d_roger.py (avec la texture
 * gol_d_roger_0 et le fichier Blockbench src/blockbench/gol_d_roger.bbmodel).
 *
 * <ul>
 *   <li>Tête : longs cheveux noirs (2e couche + mèches 3D sur les côtés et dans le dos), grande moustache noire relevée aux
 *       pointes, sourire carnassier ; chapeau rouge à liseré doré (large bord, calotte haute, fronton relevé avec le Jolly Roger,
 *       côtés et arrière relevés) et une plume blanche.</li>
 *   <li>Long manteau de capitaine rouge posé sur les épaules : grand col relevé, dos en 3 lés arrondis avec plis en relief et
 *       ourlet irrégulier, pans jusqu'aux mollets, galons et brandebourgs
 *       dorés, doublure grise, épaulettes dorées à franges.</li>
 *   <li>Chemise rose ouverte sur le torse musclé, collier d'or ; large ceinture de tissu jaune nouée sur le côté et ceinture de
 *       cuir ; collier de perles ; pantalon orange ample à volants blancs ; bottes de cuir.</li>
 *   <li>Sabre au côté gauche : fourreau, et poignée dorée « sheathed_hilt » affichée seulement quand le sabre est rangé.</li>
 * </ul>
 */
public final class RogerModels {

    public static final ModelLayerLocation ROGER = new ModelLayerLocation(BloxPiece.id("gol_d_roger"), "main");

    private RogerModels() {
    }

    public static LayerDefinition roger() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition hair_back = root.getChild("head").addOrReplaceChild("hair_back", CubeListBuilder.create().texOffs(0, 71)
                .addBox(-4.5F, -7.5F, 0.0F, 9, 10, 2, CubeDeformation.NONE), PartPose.offset(0.0F, 0.0F, 3.6F));
        PartDefinition hair_back_low = hair_back.addOrReplaceChild("hair_back_low", CubeListBuilder.create().texOffs(64, 26)
                .addBox(-5.0F, 0.0F, 0.0F, 10, 4, 2, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 2.0F, 0.3F, 0.35F, 0.0F, 0.0F));
        PartDefinition hair_side_right = root.getChild("head").addOrReplaceChild("hair_side_right", CubeListBuilder.create().texOffs(40, 32)
                .addBox(-2.0F, -7.0F, -2.5F, 2, 7, 7, CubeDeformation.NONE), PartPose.offset(-3.6F, 0.0F, 0.0F));
        PartDefinition hair_side_right_low = hair_side_right.addOrReplaceChild("hair_side_right_low", CubeListBuilder.create().texOffs(57, 71)
                .addBox(-2.0F, 0.0F, -2.0F, 2, 5, 6, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.0F, 0.6F, 0.0F, 0.0F, 0.3F));
        PartDefinition hair_side_left = root.getChild("head").addOrReplaceChild("hair_side_left", CubeListBuilder.create().texOffs(59, 32)
                .addBox(0.0F, -7.0F, -2.5F, 2, 7, 7, CubeDeformation.NONE), PartPose.offset(3.6F, 0.0F, 0.0F));
        PartDefinition hair_side_left_low = hair_side_left.addOrReplaceChild("hair_side_left_low", CubeListBuilder.create().texOffs(74, 71)
                .addBox(0.0F, 0.0F, -2.0F, 2, 5, 6, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.0F, 0.6F, 0.0F, 0.0F, -0.3F));
        PartDefinition hair_lock_right = root.getChild("head").addOrReplaceChild("hair_lock_right", CubeListBuilder.create().texOffs(89, 26)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 5, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-3.9F, -6.2F, -3.6F, 0.0F, 0.0F, 0.1F));
        PartDefinition hair_lock_left = root.getChild("head").addOrReplaceChild("hair_lock_left", CubeListBuilder.create().texOffs(94, 26)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 5, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(3.9F, -6.2F, -3.6F, 0.0F, 0.0F, -0.1F));
        PartDefinition stache = root.getChild("head").addOrReplaceChild("stache", CubeListBuilder.create().texOffs(86, 109)
                .addBox(-2.5F, -0.5F, -1.0F, 5, 1, 1, new CubeDeformation(0.1F)), PartPose.offset(0.0F, -2.6F, -4.05F));
        PartDefinition stache_right = root.getChild("head").addOrReplaceChild("stache_right", CubeListBuilder.create().texOffs(99, 109)
                .addBox(-4.0F, -0.5F, -0.5F, 4, 1, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-2.3F, -2.6F, -4.55F, 0.0F, 0.25F, 0.42F));
        PartDefinition stache_left = root.getChild("head").addOrReplaceChild("stache_left", CubeListBuilder.create().texOffs(110, 109)
                .addBox(0.0F, -0.5F, -0.5F, 4, 1, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(2.3F, -2.6F, -4.55F, 0.0F, -0.25F, -0.42F));
        PartDefinition stache_tip_right = stache_right.addOrReplaceChild("stache_tip_right", CubeListBuilder.create().texOffs(121, 109)
                .addBox(-1.0F, -1.5F, -0.5F, 1, 1, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-3.6F, 0.0F, 0.0F, 0.0F, 0.0F, 0.5F));
        PartDefinition stache_tip_left = stache_left.addOrReplaceChild("stache_tip_left", CubeListBuilder.create().texOffs(0, 115)
                .addBox(0.0F, -1.5F, -0.5F, 1, 1, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(3.6F, 0.0F, 0.0F, 0.0F, 0.0F, -0.5F));
        PartDefinition hat_brim = root.getChild("head").addOrReplaceChild("hat_brim", CubeListBuilder.create().texOffs(78, 32)
                .addBox(-6.0F, -0.5F, -6.5F, 12, 1, 12, CubeDeformation.NONE), PartPose.offset(0.0F, -8.4F, 0.4F));
        PartDefinition hat_crown = root.getChild("head").addOrReplaceChild("hat_crown", CubeListBuilder.create().texOffs(0, 57)
                .addBox(-4.0F, -5.0F, -4.0F, 8, 5, 8, new CubeDeformation(0.4F)), PartPose.offset(0.0F, -8.6F, 0.3F));
        PartDefinition hat_crown_top = root.getChild("head").addOrReplaceChild("hat_crown_top", CubeListBuilder.create().texOffs(32, 93)
                .addBox(-3.0F, -1.0F, -3.0F, 6, 1, 6, new CubeDeformation(0.2F)), PartPose.offset(0.0F, -14.0F, 0.3F));
        PartDefinition hat_front = root.getChild("head").addOrReplaceChild("hat_front", CubeListBuilder.create().texOffs(105, 71)
                .addBox(-3.5F, -7.0F, -0.5F, 7, 7, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -8.6F, -6.0F, 0.2F, 0.0F, 0.0F));
        PartDefinition hat_front_left = root.getChild("head").addOrReplaceChild("hat_front_left", CubeListBuilder.create().texOffs(57, 93)
                .addBox(0.0F, -6.0F, -0.5F, 5, 6, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(3.4F, -8.6F, -6.0F, 0.2F, -0.5F, 0.0F));
        PartDefinition hat_front_right = root.getChild("head").addOrReplaceChild("hat_front_right", CubeListBuilder.create().texOffs(70, 93)
                .addBox(-5.0F, -6.0F, -0.5F, 5, 6, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-3.4F, -8.6F, -6.0F, 0.2F, 0.5F, 0.0F));
        PartDefinition hat_wing_left = root.getChild("head").addOrReplaceChild("hat_wing_left", CubeListBuilder.create().texOffs(33, 57)
                .addBox(-0.5F, -4.0F, 0.0F, 1, 4, 9, CubeDeformation.NONE), PartPose.offsetAndRotation(7.6F, -8.6F, -3.8F, 0.0F, 0.12F, -0.3F));
        PartDefinition hat_wing_right = root.getChild("head").addOrReplaceChild("hat_wing_right", CubeListBuilder.create().texOffs(54, 57)
                .addBox(-0.5F, -4.0F, 0.0F, 1, 4, 9, CubeDeformation.NONE), PartPose.offsetAndRotation(-7.6F, -8.6F, -3.8F, 0.0F, -0.12F, 0.3F));
        PartDefinition hat_back = root.getChild("head").addOrReplaceChild("hat_back", CubeListBuilder.create().texOffs(0, 109)
                .addBox(-6.0F, -4.0F, -0.5F, 12, 4, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -8.6F, 6.6F, -0.3F, 0.0F, 0.0F));
        PartDefinition plume = root.getChild("head").addOrReplaceChild("plume", CubeListBuilder.create().texOffs(91, 71)
                .addBox(-1.0F, -9.0F, -0.5F, 2, 9, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(4.6F, -12.4F, 1.0F, -0.55F, 0.0F, 0.55F));
        PartDefinition plume_b = root.getChild("head").addOrReplaceChild("plume_b", CubeListBuilder.create().texOffs(122, 71)
                .addBox(-0.5F, -7.0F, -0.5F, 1, 7, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(4.0F, -12.2F, 2.6F, -0.9F, 0.0F, 0.35F));
        PartDefinition coat_collar = root.getChild("body").addOrReplaceChild("coat_collar", CubeListBuilder.create().texOffs(99, 26)
                .addBox(-5.5F, -5.0F, 0.0F, 11, 5, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.4F, 2.6F, -0.28F, 0.0F, 0.0F));
        PartDefinition collar_right = root.getChild("body").addOrReplaceChild("collar_right", CubeListBuilder.create().texOffs(83, 93)
                .addBox(-1.0F, -4.0F, -3.0F, 1, 4, 3, CubeDeformation.NONE), PartPose.offsetAndRotation(-5.0F, 0.4F, 2.8F, 0.0F, 0.25F, 0.15F));
        PartDefinition collar_left = root.getChild("body").addOrReplaceChild("collar_left", CubeListBuilder.create().texOffs(92, 93)
                .addBox(0.0F, -4.0F, -3.0F, 1, 4, 3, CubeDeformation.NONE), PartPose.offsetAndRotation(5.0F, 0.4F, 2.8F, 0.0F, -0.25F, -0.15F));
        PartDefinition mantle = root.getChild("body").addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(0, 84)
                .addBox(-5.0F, -1.5F, -3.0F, 10, 2, 6, new CubeDeformation(0.4F)), PartPose.ZERO);
        PartDefinition coat_back = root.getChild("body").addOrReplaceChild("coat_back", CubeListBuilder.create().texOffs(64, 0)
                .addBox(-7.5F, 0.0F, 0.0F, 15, 23, 2, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -0.6F, 3.0F, 0.12F, 0.0F, 0.0F));
        PartDefinition coat_yoke = root.getChild("body").addOrReplaceChild("coat_yoke", CubeListBuilder.create().texOffs(0, 102)
                .addBox(-7.0F, 0.0F, 0.0F, 14, 4, 2, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -1.0F, 2.4F, 0.05F, 0.0F, 0.0F));
        PartDefinition coat_fold_a = coat_back.addOrReplaceChild("coat_fold_a", CubeListBuilder.create().texOffs(26, 32)
                .addBox(-1.0F, 3.0F, 0.0F, 2, 15, 1, CubeDeformation.NONE), PartPose.offset(-3.0F, 0.0F, 1.5F));
        PartDefinition coat_fold_b = coat_back.addOrReplaceChild("coat_fold_b", CubeListBuilder.create().texOffs(33, 32)
                .addBox(-1.0F, 4.0F, 0.0F, 2, 14, 1, CubeDeformation.NONE), PartPose.offset(3.0F, 0.0F, 1.5F));
        PartDefinition coat_corner_left = coat_back.addOrReplaceChild("coat_corner_left", CubeListBuilder.create().texOffs(99, 0)
                .addBox(0.0F, 0.0F, 0.0F, 2, 23, 2, CubeDeformation.NONE), PartPose.offsetAndRotation(7.5F, 0.0F, 0.0F, 0.0F, 0.63F, 0.0F));
        PartDefinition coat_corner_right = coat_back.addOrReplaceChild("coat_corner_right", CubeListBuilder.create().texOffs(108, 0)
                .addBox(-2.0F, 0.0F, 0.0F, 2, 23, 2, CubeDeformation.NONE), PartPose.offsetAndRotation(-7.5F, 0.0F, 0.0F, 0.0F, -0.63F, 0.0F));
        PartDefinition coat_front_left = coat_corner_left.addOrReplaceChild("coat_front_left", CubeListBuilder.create().texOffs(0, 32)
                .addBox(0.0F, 0.0F, -0.5F, 5, 23, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(2.0F, 0.0F, 1.0F, 0.0F, 0.64F, 0.0F));
        PartDefinition coat_front_right = coat_corner_right.addOrReplaceChild("coat_front_right", CubeListBuilder.create().texOffs(13, 32)
                .addBox(-5.0F, 0.0F, -0.5F, 5, 23, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-2.0F, 0.0F, 1.0F, 0.0F, -0.64F, 0.0F));
        PartDefinition epaulette_right = root.getChild("right_arm").addOrReplaceChild("epaulette_right", CubeListBuilder.create().texOffs(33, 84)
                .addBox(-5.0F, -3.4F, -3.0F, 7, 2, 6, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition fringe_right = root.getChild("right_arm").addOrReplaceChild("fringe_right", CubeListBuilder.create().texOffs(60, 84)
                .addBox(-5.0F, -1.4F, -3.0F, 7, 2, 6, new CubeDeformation(0.05F)), PartPose.ZERO);
        PartDefinition epaulette_left = root.getChild("left_arm").addOrReplaceChild("epaulette_left", CubeListBuilder.create().texOffs(87, 84)
                .addBox(-2.0F, -3.4F, -3.0F, 7, 2, 6, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition fringe_left = root.getChild("left_arm").addOrReplaceChild("fringe_left", CubeListBuilder.create().texOffs(0, 93)
                .addBox(-2.0F, -1.4F, -3.0F, 7, 2, 6, new CubeDeformation(0.05F)), PartPose.ZERO);
        PartDefinition sleeve_right = root.getChild("right_arm").addOrReplaceChild("sleeve_right", CubeListBuilder.create().texOffs(75, 57)
                .addBox(-3.0F, -2.0F, -2.0F, 4, 9, 4, new CubeDeformation(0.32F)), PartPose.ZERO);
        PartDefinition sleeve_left = root.getChild("left_arm").addOrReplaceChild("sleeve_left", CubeListBuilder.create().texOffs(92, 57)
                .addBox(-1.0F, -2.0F, -2.0F, 4, 9, 4, new CubeDeformation(0.32F)), PartPose.ZERO);
        PartDefinition shirt_collar_right = root.getChild("body").addOrReplaceChild("shirt_collar_right", CubeListBuilder.create().texOffs(65, 109)
                .addBox(-1.0F, -1.0F, -0.5F, 2, 2, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-2.4F, 0.6F, -2.4F, 0.2F, 0.0F, 0.5F));
        PartDefinition shirt_collar_left = root.getChild("body").addOrReplaceChild("shirt_collar_left", CubeListBuilder.create().texOffs(72, 109)
                .addBox(-1.0F, -1.0F, -0.5F, 2, 2, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(2.4F, 0.6F, -2.4F, 0.2F, 0.0F, -0.5F));
        PartDefinition sash = root.getChild("body").addOrReplaceChild("sash", CubeListBuilder.create().texOffs(101, 93)
                .addBox(-4.0F, 8.5F, -2.0F, 8, 3, 4, new CubeDeformation(0.4F)), PartPose.ZERO);
        PartDefinition belt = root.getChild("body").addOrReplaceChild("belt", CubeListBuilder.create().texOffs(27, 109)
                .addBox(-4.0F, 9.5F, -2.0F, 8, 1, 4, new CubeDeformation(0.5F)), PartPose.ZERO);
        PartDefinition buckle = root.getChild("body").addOrReplaceChild("buckle", CubeListBuilder.create().texOffs(5, 115)
                .addBox(-1.0F, 9.5F, -2.95F, 2, 1, 1, new CubeDeformation(0.12F)), PartPose.ZERO);
        PartDefinition sash_knot = root.getChild("body").addOrReplaceChild("sash_knot", CubeListBuilder.create().texOffs(79, 109)
                .addBox(-1.0F, -1.0F, -0.5F, 2, 2, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(3.2F, 10.2F, -2.6F, 0.0F, 0.0F, -0.3F));
        PartDefinition sash_tail = root.getChild("body").addOrReplaceChild("sash_tail", CubeListBuilder.create().texOffs(98, 71)
                .addBox(-1.0F, 0.0F, -0.5F, 2, 9, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(3.4F, 11.0F, -2.5F, -0.1F, 0.0F, -0.08F));
        PartDefinition sash_tail_b = root.getChild("body").addOrReplaceChild("sash_tail_b", CubeListBuilder.create().texOffs(27, 93)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 7, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(4.6F, 11.0F, -2.2F, -0.08F, 0.0F, -0.2F));
        PartDefinition pearls = root.getChild("body").addOrReplaceChild("pearls", CubeListBuilder.create().texOffs(33, 102)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 5, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-2.6F, 10.8F, -2.75F, -0.12F, 0.0F, 0.18F));
        PartDefinition pants_right = root.getChild("right_leg").addOrReplaceChild("pants_right", CubeListBuilder.create().texOffs(23, 71)
                .addBox(-2.0F, 0.0F, -2.0F, 4, 8, 4, new CubeDeformation(0.42F)), PartPose.ZERO);
        PartDefinition pants_left = root.getChild("left_leg").addOrReplaceChild("pants_left", CubeListBuilder.create().texOffs(40, 71)
                .addBox(-2.0F, 0.0F, -2.0F, 4, 8, 4, new CubeDeformation(0.42F)), PartPose.ZERO);
        PartDefinition frill_right = root.getChild("right_leg").addOrReplaceChild("frill_right", CubeListBuilder.create().texOffs(38, 102)
                .addBox(-2.0F, 8.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.62F)), PartPose.ZERO);
        PartDefinition frill_left = root.getChild("left_leg").addOrReplaceChild("frill_left", CubeListBuilder.create().texOffs(55, 102)
                .addBox(-2.0F, 8.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.62F)), PartPose.ZERO);
        PartDefinition boot_right = root.getChild("right_leg").addOrReplaceChild("boot_right", CubeListBuilder.create().texOffs(72, 102)
                .addBox(-2.0F, 10.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.32F)), PartPose.ZERO);
        PartDefinition boot_left = root.getChild("left_leg").addOrReplaceChild("boot_left", CubeListBuilder.create().texOffs(89, 102)
                .addBox(-2.0F, 10.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.32F)), PartPose.ZERO);
        PartDefinition scabbard = root.getChild("body").addOrReplaceChild("scabbard", CubeListBuilder.create().texOffs(109, 57)
                .addBox(-0.5F, -2.0F, -1.0F, 1, 11, 2, CubeDeformation.NONE), PartPose.offsetAndRotation(4.6F, 9.6F, 0.4F, 0.95F, 0.0F, -0.22F));
        PartDefinition sheathed_hilt = root.getChild("body").addOrReplaceChild("sheathed_hilt", CubeListBuilder.create().texOffs(106, 102)
                .addBox(-0.5F, -5.0F, -0.5F, 1, 5, 1, new CubeDeformation(0.05F)), PartPose.offsetAndRotation(4.4F, 9.2F, -1.6F, -0.6F, 0.0F, -0.22F));
        PartDefinition hilt_guard = sheathed_hilt.addOrReplaceChild("hilt_guard", CubeListBuilder.create().texOffs(52, 109)
                .addBox(-1.5F, -0.2F, -1.5F, 3, 1, 3, CubeDeformation.NONE), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }
}
