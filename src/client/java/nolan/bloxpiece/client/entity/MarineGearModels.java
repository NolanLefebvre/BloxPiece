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
 * Géométrie des Marines : même construction que les pirates (PirateGearModels) — corps humanoïde classique + équipement 3D en
 * relief attaché aux parties du corps (il suit les animations). Texture 128x128 : skin en haut à gauche (disposition classique
 * 64x32), équipement en dessous. FICHIER GÉNÉRÉ (scratchpad/marines/make_marines.py) avec les textures marine_swordsman_N /
 * marine_rifleman_N.
 *
 * <ul>
 *   <li>Commun : casquette de la Marine (calotte blanche ajustée, inscription « MARINE » turquoise, petite visière plate), manches courtes blanches à liseré bleu, grand col
 *       marin bleu dans le dos, foulard bleu noué sur la poitrine, ceinture bleu marine à boucle en laiton, bottes noires.</li>
 *   <li>Épéiste : fourreau du sabre réglementaire à la ceinture (garnitures en laiton).</li>
 *   <li>Fusilier : bretelle du fusil en cuir en travers du torse, deux cartouchières à la ceinture, et le fusil rangé en
 *       diagonale dans le dos (« back_rifle », affiché seulement hors combat : ArmedPirateModel).</li>
 * </ul>
 */
public final class MarineGearModels {

    public static final ModelLayerLocation SWORDSMAN = new ModelLayerLocation(BloxPiece.id("marine_swordsman"), "main");
    public static final ModelLayerLocation RIFLEMAN = new ModelLayerLocation(BloxPiece.id("marine_rifleman"), "main");

    private MarineGearModels() {
    }

    /** Marine Épéiste. */
    public static LayerDefinition swordsman() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition cap_crown = root.getChild("head").addOrReplaceChild("cap_crown", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-4.0F, -8.4F, -4.0F, 8, 3, 8, new CubeDeformation(0.32F)), PartPose.ZERO);
        PartDefinition cap_visor = root.getChild("head").addOrReplaceChild("cap_visor", CubeListBuilder.create().texOffs(33, 32)
                .addBox(-4.0F, -0.5F, -3.0F, 8, 1, 3, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.0F, -5.9F, -3.9F, 0.14F, 0.0F, 0.0F));
        PartDefinition sleeve_right = root.getChild("right_arm").addOrReplaceChild("sleeve_right", CubeListBuilder.create().texOffs(56, 32)
                .addBox(-3.0F, -2.0F, -2.0F, 4, 5, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition sleeve_left = root.getChild("left_arm").addOrReplaceChild("sleeve_left", CubeListBuilder.create().texOffs(73, 32)
                .addBox(-1.0F, -2.0F, -2.0F, 4, 5, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition collar_flap = root.getChild("body").addOrReplaceChild("collar_flap", CubeListBuilder.create().texOffs(90, 32)
                .addBox(-4.0F, 0.0F, -0.5F, 8, 5, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.1F, 2.6F, 0.1F, 0.0F, 0.0F));
        PartDefinition scarf_knot = root.getChild("body").addOrReplaceChild("scarf_knot", CubeListBuilder.create().texOffs(109, 32)
                .addBox(-1.0F, -1.0F, -0.5F, 2, 2, 1, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 4.2F, -2.6F, 0.0F, 0.0F, 0.785F));
        PartDefinition scarf_tail_a = root.getChild("body").addOrReplaceChild("scarf_tail_a", CubeListBuilder.create().texOffs(116, 32)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 4, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-0.6F, 4.9F, -2.7F, -0.06F, 0.0F, 0.28F));
        PartDefinition scarf_tail_b = root.getChild("body").addOrReplaceChild("scarf_tail_b", CubeListBuilder.create().texOffs(121, 32)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.6F, 4.9F, -2.65F, -0.06F, 0.0F, -0.28F));
        PartDefinition belt = root.getChild("body").addOrReplaceChild("belt", CubeListBuilder.create().texOffs(0, 44)
                .addBox(-4.0F, 9.0F, -2.0F, 8, 2, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition buckle = root.getChild("body").addOrReplaceChild("buckle", CubeListBuilder.create().texOffs(25, 44)
                .addBox(-1.5F, 9.0F, -2.95F, 3, 2, 1, new CubeDeformation(0.1F)), PartPose.ZERO);
        PartDefinition boot_right = root.getChild("right_leg").addOrReplaceChild("boot_right", CubeListBuilder.create().texOffs(34, 44)
                .addBox(-2.0F, 8.0F, -2.0F, 4, 4, 4, new CubeDeformation(0.35F)), PartPose.ZERO);
        PartDefinition boot_left = root.getChild("left_leg").addOrReplaceChild("boot_left", CubeListBuilder.create().texOffs(51, 44)
                .addBox(-2.0F, 8.0F, -2.0F, 4, 4, 4, new CubeDeformation(0.35F)), PartPose.ZERO);
        PartDefinition scabbard = root.getChild("body").addOrReplaceChild("scabbard", CubeListBuilder.create().texOffs(68, 44)
                .addBox(-0.5F, -2.0F, -1.0F, 1, 10, 2, CubeDeformation.NONE), PartPose.offsetAndRotation(4.5F, 9.5F, 0.4F, 1.0F, 0.0F, -0.22F));
        PartDefinition scabbard_throat = scabbard.addOrReplaceChild("scabbard_throat", CubeListBuilder.create().texOffs(75, 44)
                .addBox(-0.5F, -2.0F, -1.0F, 1, 1, 2, new CubeDeformation(0.15F)), PartPose.ZERO);
        PartDefinition scabbard_chape = scabbard.addOrReplaceChild("scabbard_chape", CubeListBuilder.create().texOffs(82, 44)
                .addBox(-0.5F, 7.0F, -1.0F, 1, 1, 2, new CubeDeformation(0.15F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Marine Fusilier. */
    public static LayerDefinition rifleman() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition cap_crown = root.getChild("head").addOrReplaceChild("cap_crown", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-4.0F, -8.4F, -4.0F, 8, 3, 8, new CubeDeformation(0.32F)), PartPose.ZERO);
        PartDefinition cap_visor = root.getChild("head").addOrReplaceChild("cap_visor", CubeListBuilder.create().texOffs(33, 32)
                .addBox(-4.0F, -0.5F, -3.0F, 8, 1, 3, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.0F, -5.9F, -3.9F, 0.14F, 0.0F, 0.0F));
        PartDefinition sleeve_right = root.getChild("right_arm").addOrReplaceChild("sleeve_right", CubeListBuilder.create().texOffs(56, 32)
                .addBox(-3.0F, -2.0F, -2.0F, 4, 5, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition sleeve_left = root.getChild("left_arm").addOrReplaceChild("sleeve_left", CubeListBuilder.create().texOffs(73, 32)
                .addBox(-1.0F, -2.0F, -2.0F, 4, 5, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition collar_flap = root.getChild("body").addOrReplaceChild("collar_flap", CubeListBuilder.create().texOffs(90, 32)
                .addBox(-4.0F, 0.0F, -0.5F, 8, 5, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.1F, 2.6F, 0.1F, 0.0F, 0.0F));
        PartDefinition scarf_knot = root.getChild("body").addOrReplaceChild("scarf_knot", CubeListBuilder.create().texOffs(109, 32)
                .addBox(-1.0F, -1.0F, -0.5F, 2, 2, 1, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 4.2F, -2.6F, 0.0F, 0.0F, 0.785F));
        PartDefinition scarf_tail_a = root.getChild("body").addOrReplaceChild("scarf_tail_a", CubeListBuilder.create().texOffs(116, 32)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 4, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-0.6F, 4.9F, -2.7F, -0.06F, 0.0F, 0.28F));
        PartDefinition scarf_tail_b = root.getChild("body").addOrReplaceChild("scarf_tail_b", CubeListBuilder.create().texOffs(121, 32)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.6F, 4.9F, -2.65F, -0.06F, 0.0F, -0.28F));
        PartDefinition belt = root.getChild("body").addOrReplaceChild("belt", CubeListBuilder.create().texOffs(0, 44)
                .addBox(-4.0F, 9.0F, -2.0F, 8, 2, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition buckle = root.getChild("body").addOrReplaceChild("buckle", CubeListBuilder.create().texOffs(25, 44)
                .addBox(-1.5F, 9.0F, -2.95F, 3, 2, 1, new CubeDeformation(0.1F)), PartPose.ZERO);
        PartDefinition boot_right = root.getChild("right_leg").addOrReplaceChild("boot_right", CubeListBuilder.create().texOffs(34, 44)
                .addBox(-2.0F, 8.0F, -2.0F, 4, 4, 4, new CubeDeformation(0.35F)), PartPose.ZERO);
        PartDefinition boot_left = root.getChild("left_leg").addOrReplaceChild("boot_left", CubeListBuilder.create().texOffs(51, 44)
                .addBox(-2.0F, 8.0F, -2.0F, 4, 4, 4, new CubeDeformation(0.35F)), PartPose.ZERO);
        PartDefinition rifle_sling = root.getChild("body").addOrReplaceChild("rifle_sling", CubeListBuilder.create().texOffs(68, 44)
                .addBox(-1.0F, -6.5F, -2.0F, 2, 13, 4, new CubeDeformation(0.42F)), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.0F, 0.0F, 0.588F));
        PartDefinition pouch_left = root.getChild("body").addOrReplaceChild("pouch_left", CubeListBuilder.create().texOffs(81, 44)
                .addBox(-3.6F, 8.7F, -3.2F, 2, 2, 1, new CubeDeformation(0.1F)), PartPose.ZERO);
        PartDefinition pouch_right = root.getChild("body").addOrReplaceChild("pouch_right", CubeListBuilder.create().texOffs(88, 44)
                .addBox(1.6F, 8.7F, -3.2F, 2, 2, 1, new CubeDeformation(0.1F)), PartPose.ZERO);
        PartDefinition back_rifle = root.getChild("body").addOrReplaceChild("back_rifle", CubeListBuilder.create().texOffs(95, 44)
                .addBox(-1.0F, 2.0F, -1.0F, 2, 7, 2, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 6.5F, 3.4F, 0.0F, 0.0F, 0.72F));
        PartDefinition back_rifle_fore = back_rifle.addOrReplaceChild("back_rifle_fore", CubeListBuilder.create().texOffs(104, 44)
                .addBox(-1.0F, -7.0F, -1.0F, 2, 9, 2, new CubeDeformation(-0.25F)), PartPose.ZERO);
        PartDefinition back_rifle_barrel = back_rifle.addOrReplaceChild("back_rifle_barrel", CubeListBuilder.create().texOffs(113, 44)
                .addBox(-0.5F, -16.0F, -1.2F, 1, 17, 1, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition back_rifle_band_a = back_rifle.addOrReplaceChild("back_rifle_band_a", CubeListBuilder.create().texOffs(118, 44)
                .addBox(-1.0F, -4.0F, -1.0F, 2, 1, 2, new CubeDeformation(0.05F)), PartPose.ZERO);
        PartDefinition back_rifle_band_b = back_rifle.addOrReplaceChild("back_rifle_band_b", CubeListBuilder.create().texOffs(0, 63)
                .addBox(-1.0F, 0.0F, -1.0F, 2, 1, 2, new CubeDeformation(0.05F)), PartPose.ZERO);
        PartDefinition back_rifle_butt = back_rifle.addOrReplaceChild("back_rifle_butt", CubeListBuilder.create().texOffs(9, 63)
                .addBox(-1.0F, 9.0F, -1.0F, 2, 1, 2, new CubeDeformation(0.05F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }
}
