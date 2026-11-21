package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import nolan.bloxpiece.BloxPiece;

/**
 * Géométrie de Morgan « main de hache », capitaine de la Marine : même style que Gol D. Roger et Silvers Rayleigh (corps
 * humanoïde + équipement 3D attaché aux membres, 1 texel par pixel), mais conçue à 48 px de haut = 3 blocs (pieds à y = 24,
 * sommet du crâne à y = -24) : tête 10, torse 16 x 17 x 8, bras de 19 (6 d'épaisseur, très écartés), jambes de 19 (7 d'épaisseur).
 * Mêmes noms de pièces que HumanoidModel (head, hat, body, bras, jambes). Texture morgan.png (256x128).
 * FICHIER GÉNÉRÉ par src/blockbench/make_morgan.py (avec la texture et src/blockbench/morgan.bbmodel). Animations : MorganModel.
 *
 * <ul>
 *   <li>Tête : cheveux blonds courts plaqués, sourcils froncés, barbe de quelques jours, mâchoire inférieure d'acier (plaque de la
 *       largeur du visage, créneaux carrés en haut, boulons ronds, mouette de la Marine ;
 *       accroches par en dessous, le long de la mâchoire jusqu'aux boulons sous les oreilles).</li>
 *   <li>Grand manteau bleu clair sans manches, ouvert, doublure et revers magenta cloutés, très haut col, ceinture à boucle
 *       dorée ; épaulettes dorées à franges ; ses pans (skirt_*) s'écartent avec les jambes.</li>
 *   <li>Gilet bleu nuit au décolleté profond ; bras nus très musclés ; pantalon bleu à rayures sombres ; hautes bottes lacées.</li>
 *   <li>Bras droit : gaine cloutée, manche de bois et énorme fer noir à demi-lune argentée (pièce « axe », pivot au poignet).</li>
 * </ul>
 */
public final class MorganModels {

    public static final ModelLayerLocation MORGAN = new ModelLayerLocation(BloxPiece.id("morgan"), "main");

    private MorganModels() {
    }

    public static LayerDefinition morgan() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 28)
                .addBox(-5.0F, -10.0F, -5.0F, 10, 10, 10, CubeDeformation.NONE), PartPose.offset(0.0F, -12.0F, 0.0F));
        PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(41, 28)
                .addBox(-5.0F, -10.0F, -5.0F, 10, 10, 10, new CubeDeformation(0.5F)), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(75, 0)
                .addBox(-8.0F, 0.0F, -4.0F, 16, 17, 8, CubeDeformation.NONE), PartPose.offset(0.0F, -12.0F, 0.0F));
        PartDefinition right_arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(216, 28)
                .addBox(-3.0F, -2.0F, -3.0F, 6, 11, 6, CubeDeformation.NONE), PartPose.offset(-12.3F, -10.0F, 0.0F));
        PartDefinition forearm_right = right_arm.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(0, 49)
                .addBox(-2.5F, 0.0F, -2.5F, 5, 12, 5, CubeDeformation.NONE), PartPose.offset(0.0F, 9.0F, 0.0F));
        PartDefinition left_arm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(124, 0)
                .addBox(-3.0F, -2.0F, -3.0F, 6, 19, 6, CubeDeformation.NONE), PartPose.offset(12.3F, -10.0F, 0.0F));
        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(46, 0)
                .addBox(-3.5F, 0.0F, -3.5F, 7, 19, 7, CubeDeformation.NONE), PartPose.offset(-4.0F, 5.0F, 0.0F));
        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(46, 0).mirror()
                .addBox(-3.5F, 0.0F, -3.5F, 7, 19, 7, CubeDeformation.NONE), PartPose.offset(4.0F, 5.0F, 0.0F));
        PartDefinition hair_top = head.addOrReplaceChild("hair_top", CubeListBuilder.create().texOffs(146, 49)
                .addBox(-5.5F, -2.0F, -5.5F, 11, 2, 11, CubeDeformation.NONE), PartPose.offset(0.0F, -10.0F, 0.0F));
        PartDefinition hair_front = head.addOrReplaceChild("hair_front", CubeListBuilder.create().texOffs(27, 91)
                .addBox(-4.5F, -1.0F, -1.0F, 9, 1, 2, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -9.8F, -5.0F, 0.3F, 0.0F, 0.0F));
        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(140, 81)
                .addBox(-4.5F, -3.0F, -1.0F, 9, 3.5F, 1, CubeDeformation.NONE), PartPose.offset(0.0F, 0.0F, -5.0F));
        PartDefinition jaw_round = jaw.addOrReplaceChild("jaw_round", CubeListBuilder.create().texOffs(85, 91)
                .addBox(-3.5F, 0.0F, -1.0F, 7, 1, 1, CubeDeformation.NONE), PartPose.offset(0.0F, 0.5F, 0.0F));
        PartDefinition jaw_under = jaw.addOrReplaceChild("jaw_under", CubeListBuilder.create().texOffs(161, 81)
                .addBox(-4.0F, 0.0F, 0.0F, 8, 0.6F, 3.5F, CubeDeformation.NONE), PartPose.offset(0.0F, 0.2F, 0.0F));
        PartDefinition jaw_tooth_0 = jaw.addOrReplaceChild("jaw_tooth_0", CubeListBuilder.create().texOffs(50, 91)
                .addBox(0.0F, -1.25F, -1.0F, 1.5F, 1.25F, 1, CubeDeformation.NONE), PartPose.offset(-4.5F, -3.0F, 0.0F));
        PartDefinition jaw_tooth_1 = jaw.addOrReplaceChild("jaw_tooth_1", CubeListBuilder.create().texOffs(57, 91)
                .addBox(0.0F, -1.25F, -1.0F, 1.5F, 1.25F, 1, CubeDeformation.NONE), PartPose.offset(-2.0F, -3.0F, 0.0F));
        PartDefinition jaw_tooth_2 = jaw.addOrReplaceChild("jaw_tooth_2", CubeListBuilder.create().texOffs(64, 91)
                .addBox(0.0F, -1.25F, -1.0F, 1.5F, 1.25F, 1, CubeDeformation.NONE), PartPose.offset(0.5F, -3.0F, 0.0F));
        PartDefinition jaw_tooth_3 = jaw.addOrReplaceChild("jaw_tooth_3", CubeListBuilder.create().texOffs(71, 91)
                .addBox(0.0F, -1.25F, -1.0F, 1.5F, 1.25F, 1, CubeDeformation.NONE), PartPose.offset(3.0F, -3.0F, 0.0F));
        PartDefinition jaw_side_right = head.addOrReplaceChild("jaw_side_right", CubeListBuilder.create().texOffs(100, 81)
                .addBox(0.0F, -3.0F, 0.0F, 0.6F, 3.5F, 2.5F, CubeDeformation.NONE), PartPose.offset(-5.05F, 0.0F, -5.0F));
        PartDefinition jaw_side_left = head.addOrReplaceChild("jaw_side_left", CubeListBuilder.create().texOffs(109, 81)
                .addBox(-0.6F, -3.0F, 0.0F, 0.6F, 3.5F, 2.5F, CubeDeformation.NONE), PartPose.offset(5.05F, 0.0F, -5.0F));
        PartDefinition jaw_bolt_right = head.addOrReplaceChild("jaw_bolt_right", CubeListBuilder.create().texOffs(246, 81)
                .addBox(-0.6F, -0.6F, -0.6F, 1.2F, 1.2F, 1.2F, CubeDeformation.NONE), PartPose.offset(-4.4F, -3.6F, -5.6F));
        PartDefinition jaw_bolt_left = head.addOrReplaceChild("jaw_bolt_left", CubeListBuilder.create().texOffs(0, 91)
                .addBox(-0.6F, -0.6F, -0.6F, 1.2F, 1.2F, 1.2F, CubeDeformation.NONE), PartPose.offset(4.4F, -3.6F, -5.6F));
        PartDefinition jaw_strap_right = head.addOrReplaceChild("jaw_strap_right", CubeListBuilder.create().texOffs(186, 81)
                .addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 4, CubeDeformation.NONE), PartPose.offsetAndRotation(-5.2F, -1.6F, -4.6F, 0.12F, 0.0F, 0.0F));
        PartDefinition jaw_anchor_right = head.addOrReplaceChild("jaw_anchor_right", CubeListBuilder.create().texOffs(9, 91)
                .addBox(-0.6F, -0.9F, -0.9F, 1.2F, 1.8F, 1.8F, CubeDeformation.NONE), PartPose.offset(-5.3F, -2.1F, -0.6F));
        PartDefinition jaw_strap_left = head.addOrReplaceChild("jaw_strap_left", CubeListBuilder.create().texOffs(197, 81)
                .addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 4, CubeDeformation.NONE), PartPose.offsetAndRotation(5.2F, -1.6F, -4.6F, 0.12F, 0.0F, 0.0F));
        PartDefinition jaw_anchor_left = head.addOrReplaceChild("jaw_anchor_left", CubeListBuilder.create().texOffs(18, 91)
                .addBox(-0.6F, -0.9F, -0.9F, 1.2F, 1.8F, 1.8F, CubeDeformation.NONE), PartPose.offset(5.3F, -2.1F, -0.6F));
        PartDefinition coat_back = body.addOrReplaceChild("coat_back", CubeListBuilder.create().texOffs(82, 28)
                .addBox(-9.2F, 0.0F, 0.0F, 18.4F, 17, 1, CubeDeformation.NONE), PartPose.offset(0.0F, 0.0F, 4.0F));
        PartDefinition coat_side_right = body.addOrReplaceChild("coat_side_right", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.0F, 0.0F, -9.5F, 1, 17, 10, CubeDeformation.NONE), PartPose.offset(-8.2F, 0.0F, 5.0F));
        PartDefinition coat_side_left = body.addOrReplaceChild("coat_side_left", CubeListBuilder.create().texOffs(23, 0)
                .addBox(0.0F, 0.0F, -9.5F, 1, 17, 10, CubeDeformation.NONE), PartPose.offset(8.2F, 0.0F, 5.0F));
        PartDefinition coat_front_right = body.addOrReplaceChild("coat_front_right", CubeListBuilder.create().texOffs(123, 28)
                .addBox(0.0F, 0.0F, -1.0F, 5, 17, 1, CubeDeformation.NONE), PartPose.offset(-9.2F, 0.0F, -4.5F));
        PartDefinition coat_front_left = body.addOrReplaceChild("coat_front_left", CubeListBuilder.create().texOffs(136, 28)
                .addBox(-5.0F, 0.0F, -1.0F, 5, 17, 1, CubeDeformation.NONE), PartPose.offset(9.2F, 0.0F, -4.5F));
        PartDefinition coat_collar = body.addOrReplaceChild("coat_collar", CubeListBuilder.create().texOffs(149, 67)
                .addBox(-7.0F, -8.0F, 0.0F, 14, 8, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.6F, 3.4F, -0.22F, 0.0F, 0.0F));
        PartDefinition collar_right = body.addOrReplaceChild("collar_right", CubeListBuilder.create().texOffs(98, 67)
                .addBox(-1.0F, -7.0F, -5.0F, 1, 7, 5, CubeDeformation.NONE), PartPose.offsetAndRotation(-6.4F, 0.6F, 3.6F, 0.0F, 0.3F, 0.22F));
        PartDefinition collar_left = body.addOrReplaceChild("collar_left", CubeListBuilder.create().texOffs(111, 67)
                .addBox(0.0F, -7.0F, -5.0F, 1, 7, 5, CubeDeformation.NONE), PartPose.offsetAndRotation(6.4F, 0.6F, 3.6F, 0.0F, -0.3F, -0.22F));
        PartDefinition coat_belt = body.addOrReplaceChild("coat_belt", CubeListBuilder.create().texOffs(191, 49)
                .addBox(-9.2F, 13.5F, -5.5F, 18.4F, 2, 11, new CubeDeformation(0.1F)), PartPose.ZERO);
        PartDefinition coat_buckle = body.addOrReplaceChild("coat_buckle", CubeListBuilder.create().texOffs(78, 91)
                .addBox(-1.0F, -1.0F, -0.5F, 2, 2, 1, CubeDeformation.NONE), PartPose.offset(6.7F, 14.5F, -5.9F));
        PartDefinition skirt_back = body.addOrReplaceChild("skirt_back", CubeListBuilder.create().texOffs(21, 49)
                .addBox(-9.2F, 0.0F, 0.0F, 18.4F, 15, 1, CubeDeformation.NONE), PartPose.offset(0.0F, 17.0F, 4.0F));
        PartDefinition skirt_side_right = body.addOrReplaceChild("skirt_side_right", CubeListBuilder.create().texOffs(149, 0)
                .addBox(-1.0F, 0.0F, -9.5F, 1, 15, 10, CubeDeformation.NONE), PartPose.offset(-8.2F, 17.0F, 5.0F));
        PartDefinition skirt_side_left = body.addOrReplaceChild("skirt_side_left", CubeListBuilder.create().texOffs(172, 0)
                .addBox(0.0F, 0.0F, -9.5F, 1, 15, 10, CubeDeformation.NONE), PartPose.offset(8.2F, 17.0F, 5.0F));
        PartDefinition skirt_front_right = body.addOrReplaceChild("skirt_front_right", CubeListBuilder.create().texOffs(62, 49)
                .addBox(0.0F, 0.0F, -1.0F, 5, 15, 1, CubeDeformation.NONE), PartPose.offset(-9.2F, 17.0F, -4.5F));
        PartDefinition skirt_front_left = body.addOrReplaceChild("skirt_front_left", CubeListBuilder.create().texOffs(75, 49)
                .addBox(-5.0F, 0.0F, -1.0F, 5, 15, 1, CubeDeformation.NONE), PartPose.offset(9.2F, 17.0F, -4.5F));
        PartDefinition epaulette_right = body.addOrReplaceChild("epaulette_right", CubeListBuilder.create().texOffs(180, 67)
                .addBox(-4.0F, -3.4F, -3.5F, 7, 2, 7, CubeDeformation.NONE), PartPose.offset(-12.3F, 2.0F, 0.0F));
        PartDefinition fringe_right = body.addOrReplaceChild("fringe_right", CubeListBuilder.create().texOffs(209, 67)
                .addBox(-4.0F, -1.4F, -3.5F, 7, 2, 7, new CubeDeformation(0.05F)), PartPose.offset(-12.3F, 2.0F, 0.0F));
        PartDefinition epaulette_left = body.addOrReplaceChild("epaulette_left", CubeListBuilder.create().texOffs(0, 81)
                .addBox(-3.0F, -3.4F, -3.5F, 7, 2, 7, CubeDeformation.NONE), PartPose.offset(12.3F, 2.0F, 0.0F));
        PartDefinition fringe_left = body.addOrReplaceChild("fringe_left", CubeListBuilder.create().texOffs(29, 81)
                .addBox(-3.0F, -1.4F, -3.5F, 7, 2, 7, new CubeDeformation(0.05F)), PartPose.offset(12.3F, 2.0F, 0.0F));
        PartDefinition bicep_right = right_arm.addOrReplaceChild("bicep_right", CubeListBuilder.create().texOffs(0, 67)
                .addBox(-3.0F, 0.0F, -3.0F, 6, 7, 6, new CubeDeformation(0.5F)), PartPose.ZERO);
        PartDefinition bicep_left = left_arm.addOrReplaceChild("bicep_left", CubeListBuilder.create().texOffs(25, 67)
                .addBox(-3.0F, 0.0F, -3.0F, 6, 7, 6, new CubeDeformation(0.5F)), PartPose.ZERO);
        PartDefinition forearm_left = left_arm.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(124, 67)
                .addBox(-3.0F, 9.0F, -3.0F, 6, 5, 6, new CubeDeformation(0.35F)), PartPose.ZERO);
        PartDefinition fist_left = left_arm.addOrReplaceChild("fist_left", CubeListBuilder.create().texOffs(58, 81)
                .addBox(-3.0F, 14.0F, -3.0F, 6, 3, 6, new CubeDeformation(0.45F)), PartPose.ZERO);
        PartDefinition pants_right = right_leg.addOrReplaceChild("pants_right", CubeListBuilder.create().texOffs(149, 28)
                .addBox(-3.5F, 0.0F, -3.5F, 7, 11, 7, new CubeDeformation(0.5F)), PartPose.ZERO);
        PartDefinition pants_left = left_leg.addOrReplaceChild("pants_left", CubeListBuilder.create().texOffs(178, 28)
                .addBox(-3.5F, 0.0F, -3.5F, 7, 11, 7, new CubeDeformation(0.5F)), PartPose.ZERO);
        PartDefinition boot_right = right_leg.addOrReplaceChild("boot_right", CubeListBuilder.create().texOffs(88, 49)
                .addBox(-3.5F, 11.3F, -3.5F, 7, 7, 7, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition boot_left = left_leg.addOrReplaceChild("boot_left", CubeListBuilder.create().texOffs(117, 49)
                .addBox(-3.5F, 11.3F, -3.5F, 7, 7, 7, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition boot_toe_right = right_leg.addOrReplaceChild("boot_toe_right", CubeListBuilder.create().texOffs(208, 81)
                .addBox(-3.5F, 16.0F, -5.5F, 7, 3, 2, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition boot_toe_left = left_leg.addOrReplaceChild("boot_toe_left", CubeListBuilder.create().texOffs(227, 81)
                .addBox(-3.5F, 16.0F, -5.5F, 7, 3, 2, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition socket_tip = forearm_right.addOrReplaceChild("socket_tip", CubeListBuilder.create().texOffs(83, 81)
                .addBox(-2.0F, 0.0F, -2.0F, 4, 5, 4, new CubeDeformation(0.2F)), PartPose.ZERO);
        PartDefinition socket = forearm_right.addOrReplaceChild("socket", CubeListBuilder.create().texOffs(50, 67)
                .addBox(-3.0F, 5.0F, -3.0F, 6, 7, 6, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition axe = forearm_right.addOrReplaceChild("axe", CubeListBuilder.create().texOffs(207, 28)
                .addBox(-1.0F, 0.0F, -1.0F, 2, 16, 2, CubeDeformation.NONE), PartPose.offset(0.0F, 12.0F, 0.0F));
        PartDefinition axe_head = axe.addOrReplaceChild("axe_head", CubeListBuilder.create().texOffs(75, 67)
                .addBox(-2.5F, 0.0F, -3.0F, 5, 7, 6, CubeDeformation.NONE), PartPose.offset(0.0F, 0.5F, 0.0F));
        PartDefinition axe_blade = axe.addOrReplaceChild("axe_blade", CubeListBuilder.create().texOffs(195, 0)
                .addBox(-1.0F, 0.0F, -10.0F, 2, 15, 10, CubeDeformation.NONE), PartPose.offset(0.0F, -3.5F, -3.0F));
        PartDefinition axe_edge = axe.addOrReplaceChild("axe_edge", CubeListBuilder.create().texOffs(220, 0)
                .addBox(-0.75F, 0.0F, -3.0F, 1.5F, 21, 3, CubeDeformation.NONE), PartPose.offset(0.0F, -6.5F, -13.0F));
        PartDefinition axe_horn_top = axe_edge.addOrReplaceChild("axe_horn_top", CubeListBuilder.create().texOffs(118, 81)
                .addBox(-0.75F, -3.0F, -2.0F, 1.5F, 4, 3, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.8F, -0.5F, 0.6F, 0.0F, 0.0F));
        PartDefinition axe_horn_bottom = axe_edge.addOrReplaceChild("axe_horn_bottom", CubeListBuilder.create().texOffs(129, 81)
                .addBox(-0.75F, -1.0F, -2.0F, 1.5F, 4, 3, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 20.2F, -0.5F, -0.6F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 256, 128);
    }
}
