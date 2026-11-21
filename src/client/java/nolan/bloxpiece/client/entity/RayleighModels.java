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
 * Géométrie de Silvers Rayleigh, le Second du Roi des Pirates : même construction que Gol D. Roger (corps humanoïde classique +
 * équipement 3D attaché aux parties du corps). Texture 128x128 : peau en haut à gauche (disposition classique 64x32),
 * équipement en dessous. FICHIER GÉNÉRÉ par src/blockbench/make_silvers_rayleigh.py (avec la texture silvers_rayleigh et le
 * fichier Blockbench src/blockbench/silvers_rayleigh.bbmodel). Animations : RayleighModel.
 *
 * <ul>
 *   <li>Tête : longs cheveux blancs ondulés plaqués vers l'arrière (2e couche + volume sur le dessus + mèches 3D dans le dos et
 *       sur les côtés), deux longues mèches devant le visage ; lunettes (deux verres à cadre fin) ; cicatrice sur
 *       l'oeil droit, rides ; moustache blanche et bouc en pointe.</li>
 *   <li>Grande cape blanche à capuche rabattue, doublure vert olive, posée sur les épaules : elle enveloppe les bras (mains
 *       dans les poches) et tombe jusqu'aux chevilles ; ourlet déchiré, taches de crasse.</li>
 *   <li>Chemise moutarde à boutons noirs, col ouvert ; pantacourt bordeaux à revers ; mollets nus ; tongs.</li>
 * </ul>
 */
public final class RayleighModels {

    public static final ModelLayerLocation RAYLEIGH = new ModelLayerLocation(BloxPiece.id("silvers_rayleigh"), "main");

    private RayleighModels() {
    }

    public static LayerDefinition rayleigh() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition hair_top = root.getChild("head").addOrReplaceChild("hair_top", CubeListBuilder.create().texOffs(91, 57)
                .addBox(-4.0F, -1.5F, -3.5F, 8, 2, 8, new CubeDeformation(0.35F)), PartPose.offsetAndRotation(0.0F, -7.8F, 0.4F, -0.1F, 0.0F, 0.0F));
        PartDefinition hair_back = root.getChild("head").addOrReplaceChild("hair_back", CubeListBuilder.create().texOffs(0, 57)
                .addBox(-4.5F, -7.5F, 0.0F, 9, 11, 2, CubeDeformation.NONE), PartPose.offset(0.0F, 0.0F, 3.6F));
        PartDefinition hair_back_low = hair_back.addOrReplaceChild("hair_back_low", CubeListBuilder.create().texOffs(55, 82)
                .addBox(-5.0F, 0.0F, 0.0F, 10, 4, 2, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 3.0F, 0.3F, 0.4F, 0.0F, 0.0F));
        PartDefinition hair_side_right = root.getChild("head").addOrReplaceChild("hair_side_right", CubeListBuilder.create().texOffs(85, 32)
                .addBox(-2.0F, -7.5F, -1.5F, 2, 8, 6, CubeDeformation.NONE), PartPose.offset(-3.6F, 0.0F, 0.5F));
        PartDefinition hair_side_right_low = hair_side_right.addOrReplaceChild("hair_side_right_low", CubeListBuilder.create().texOffs(0, 71)
                .addBox(-2.0F, 0.0F, -1.5F, 2, 5, 5, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.5F, 0.5F, 0.1F, 0.0F, 0.25F));
        PartDefinition hair_side_left = root.getChild("head").addOrReplaceChild("hair_side_left", CubeListBuilder.create().texOffs(102, 32)
                .addBox(0.0F, -7.5F, -1.5F, 2, 8, 6, CubeDeformation.NONE), PartPose.offset(3.6F, 0.0F, 0.5F));
        PartDefinition hair_side_left_low = hair_side_left.addOrReplaceChild("hair_side_left_low", CubeListBuilder.create().texOffs(15, 71)
                .addBox(0.0F, 0.0F, -1.5F, 2, 5, 5, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.5F, 0.5F, 0.1F, 0.0F, -0.25F));
        PartDefinition hair_lock_front_right = root.getChild("head").addOrReplaceChild("hair_lock_front_right", CubeListBuilder.create().texOffs(30, 71)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 8, 1, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-1.3F, -8.2F, -4.6F, 0.0F, 0.0F, 0.06F));
        PartDefinition hair_lock_front_right_low = hair_lock_front_right.addOrReplaceChild("hair_lock_front_right_low", CubeListBuilder.create().texOffs(42, 91)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, 7.6F, 0.0F, 0.0F, 0.0F, -0.45F));
        PartDefinition hair_lock_front_left = root.getChild("head").addOrReplaceChild("hair_lock_front_left", CubeListBuilder.create().texOffs(84, 71)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 7, 1, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(4.1F, -7.8F, -4.2F, 0.0F, 0.0F, -0.06F));
        PartDefinition hair_lock_front_left_low = hair_lock_front_left.addOrReplaceChild("hair_lock_front_left_low", CubeListBuilder.create().texOffs(47, 91)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, 6.6F, 0.0F, 0.0F, 0.0F, 0.4F));
        PartDefinition lens_right = root.getChild("head").addOrReplaceChild("lens_right", CubeListBuilder.create().texOffs(52, 91)
                .addBox(-1.5F, -1.5F, -0.5F, 3, 3, 1, new CubeDeformation(-0.3F)), PartPose.offset(-1.7F, -4.5F, -4.2F));
        PartDefinition lens_left = root.getChild("head").addOrReplaceChild("lens_left", CubeListBuilder.create().texOffs(61, 91)
                .addBox(-1.5F, -1.5F, -0.5F, 3, 3, 1, new CubeDeformation(-0.3F)), PartPose.offset(1.7F, -4.5F, -4.2F));
        PartDefinition stache = root.getChild("head").addOrReplaceChild("stache", CubeListBuilder.create().texOffs(106, 91)
                .addBox(-2.5F, -0.5F, -1.0F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offset(0.0F, -2.5F, -4.0F));
        PartDefinition stache_right = root.getChild("head").addOrReplaceChild("stache_right", CubeListBuilder.create().texOffs(70, 91)
                .addBox(-1.0F, 0.0F, -0.5F, 1, 2, 1, new CubeDeformation(-0.15F)), PartPose.offsetAndRotation(-2.2F, -2.7F, -4.3F, 0.0F, 0.0F, 0.15F));
        PartDefinition stache_left = root.getChild("head").addOrReplaceChild("stache_left", CubeListBuilder.create().texOffs(75, 91)
                .addBox(0.0F, 0.0F, -0.5F, 1, 2, 1, new CubeDeformation(-0.15F)), PartPose.offsetAndRotation(2.2F, -2.7F, -4.3F, 0.0F, 0.0F, -0.15F));
        PartDefinition goatee = root.getChild("head").addOrReplaceChild("goatee", CubeListBuilder.create().texOffs(80, 91)
                .addBox(-1.0F, 0.0F, -1.0F, 2, 2, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -0.8F, -4.0F, -0.25F, 0.0F, 0.0F));
        PartDefinition goatee_tip = goatee.addOrReplaceChild("goatee_tip", CubeListBuilder.create().texOffs(87, 91)
                .addBox(-0.5F, 0.0F, -1.0F, 1, 2, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, -0.15F, 0.0F, 0.0F));
        PartDefinition cape_mantle = root.getChild("body").addOrReplaceChild("cape_mantle", CubeListBuilder.create().texOffs(35, 71)
                .addBox(-8.5F, -1.5F, -3.5F, 17, 2, 7, new CubeDeformation(0.15F)), PartPose.ZERO);
        PartDefinition cape_hood = root.getChild("body").addOrReplaceChild("cape_hood", CubeListBuilder.create().texOffs(89, 71)
                .addBox(-5.0F, -4.0F, 0.0F, 10, 5, 3, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.5F, 2.4F, -0.25F, 0.0F, 0.0F));
        PartDefinition hood_rim_right = root.getChild("body").addOrReplaceChild("hood_rim_right", CubeListBuilder.create().texOffs(0, 82)
                .addBox(-1.0F, -3.0F, -2.5F, 2, 3, 5, CubeDeformation.NONE), PartPose.offsetAndRotation(-4.8F, 0.2F, 0.6F, 0.0F, 0.0F, 0.25F));
        PartDefinition hood_rim_left = root.getChild("body").addOrReplaceChild("hood_rim_left", CubeListBuilder.create().texOffs(15, 82)
                .addBox(-1.0F, -3.0F, -2.5F, 2, 3, 5, CubeDeformation.NONE), PartPose.offsetAndRotation(4.8F, 0.2F, 0.6F, 0.0F, 0.0F, -0.25F));
        PartDefinition cape_back = root.getChild("body").addOrReplaceChild("cape_back", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-9.5F, 0.0F, 0.0F, 19, 23, 1, CubeDeformation.NONE), PartPose.offset(0.0F, 0.0F, 3.4F));
        PartDefinition cape_fold_a = cape_back.addOrReplaceChild("cape_fold_a", CubeListBuilder.create().texOffs(71, 32)
                .addBox(-1.0F, 3.0F, 0.0F, 2, 16, 1, CubeDeformation.NONE), PartPose.offset(-3.5F, 0.0F, 0.6F));
        PartDefinition cape_fold_b = cape_back.addOrReplaceChild("cape_fold_b", CubeListBuilder.create().texOffs(78, 32)
                .addBox(-1.0F, 5.0F, 0.0F, 2, 14, 1, CubeDeformation.NONE), PartPose.offset(3.5F, 0.0F, 0.6F));
        PartDefinition cape_side_right = root.getChild("body").addOrReplaceChild("cape_side_right", CubeListBuilder.create().texOffs(64, 0)
                .addBox(-1.0F, 0.0F, -7.0F, 1, 23, 8, CubeDeformation.NONE), PartPose.offset(-8.5F, 0.0F, 3.4F));
        PartDefinition cape_side_left = root.getChild("body").addOrReplaceChild("cape_side_left", CubeListBuilder.create().texOffs(83, 0)
                .addBox(0.0F, 0.0F, -7.0F, 1, 23, 8, CubeDeformation.NONE), PartPose.offset(8.5F, 0.0F, 3.4F));
        PartDefinition cape_front_right = cape_side_right.addOrReplaceChild("cape_front_right", CubeListBuilder.create().texOffs(41, 32)
                .addBox(-1.0F, 0.0F, 0.0F, 6, 23, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.0F, -7.5F, 0.0F, 0.1F, 0.0F));
        PartDefinition cape_front_left = cape_side_left.addOrReplaceChild("cape_front_left", CubeListBuilder.create().texOffs(56, 32)
                .addBox(-4.0F, 0.0F, 0.0F, 6, 23, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.0F, -7.5F, 0.0F, -0.1F, 0.0F));
        PartDefinition shirt_collar_right = root.getChild("body").addOrReplaceChild("shirt_collar_right", CubeListBuilder.create().texOffs(92, 91)
                .addBox(-1.0F, -1.0F, -0.5F, 2, 2, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-2.2F, 0.6F, -2.4F, 0.2F, 0.0F, 0.45F));
        PartDefinition shirt_collar_left = root.getChild("body").addOrReplaceChild("shirt_collar_left", CubeListBuilder.create().texOffs(99, 91)
                .addBox(-1.0F, -1.0F, -0.5F, 2, 2, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(2.2F, 0.6F, -2.4F, 0.2F, 0.0F, -0.45F));
        PartDefinition shirt_hem = root.getChild("body").addOrReplaceChild("shirt_hem", CubeListBuilder.create().texOffs(30, 82)
                .addBox(-4.0F, 9.0F, -2.0F, 8, 3, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition sleeve_right = root.getChild("right_arm").addOrReplaceChild("sleeve_right", CubeListBuilder.create().texOffs(23, 57)
                .addBox(-3.0F, -2.0F, -2.0F, 4, 9, 4, new CubeDeformation(0.25F)), PartPose.ZERO);
        PartDefinition sleeve_left = root.getChild("left_arm").addOrReplaceChild("sleeve_left", CubeListBuilder.create().texOffs(40, 57)
                .addBox(-1.0F, -2.0F, -2.0F, 4, 9, 4, new CubeDeformation(0.25F)), PartPose.ZERO);
        PartDefinition pants_right = root.getChild("right_leg").addOrReplaceChild("pants_right", CubeListBuilder.create().texOffs(57, 57)
                .addBox(-2.0F, 0.0F, -2.0F, 4, 7, 4, new CubeDeformation(0.38F)), PartPose.ZERO);
        PartDefinition pants_left = root.getChild("left_leg").addOrReplaceChild("pants_left", CubeListBuilder.create().texOffs(74, 57)
                .addBox(-2.0F, 0.0F, -2.0F, 4, 7, 4, new CubeDeformation(0.38F)), PartPose.ZERO);
        PartDefinition cuff_right = root.getChild("right_leg").addOrReplaceChild("cuff_right", CubeListBuilder.create().texOffs(80, 82)
                .addBox(-2.0F, 6.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.5F)), PartPose.ZERO);
        PartDefinition cuff_left = root.getChild("left_leg").addOrReplaceChild("cuff_left", CubeListBuilder.create().texOffs(97, 82)
                .addBox(-2.0F, 6.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.5F)), PartPose.ZERO);
        PartDefinition sandal_right = root.getChild("right_leg").addOrReplaceChild("sandal_right", CubeListBuilder.create().texOffs(0, 91)
                .addBox(-2.5F, 11.0F, -3.0F, 5, 1, 5, new CubeDeformation(-0.2F)), PartPose.ZERO);
        PartDefinition sandal_left = root.getChild("left_leg").addOrReplaceChild("sandal_left", CubeListBuilder.create().texOffs(21, 91)
                .addBox(-2.5F, 11.0F, -3.0F, 5, 1, 5, new CubeDeformation(-0.2F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }
}
