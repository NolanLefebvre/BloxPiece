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
 * Géométrie des pirates armés : corps humanoïde classique + équipement 3D en relief, attaché aux parties du corps (il suit
 * les animations). Texture 128x128 : skin en haut à gauche (disposition classique 64x32, membres gauches en miroir),
 * équipement en dessous. FICHIER GÉNÉRÉ (scratchpad/make_pirates.py) avec les textures pirate_swordsman_N / pirate_gunner_N.
 *
 * <ul>
 *   <li>Commun : manchettes de chemise, revers de bottes, ceinture avec boucle en laiton, écharpe nouée à la taille.</li>
 *   <li>Sabreur : bandana noué (nœud et pans à l'arrière), fourreau de sabre en biais à la ceinture.</li>
 *   <li>Tireur : tricorne à galon doré et plume, bandoulière en cuir avec trois poches à poudre.</li>
 *   <li>Capitaine : grand tricorne à galon doré et plumes, grande cape ample bordée d'or, grosses épaulettes à franges, manteau.</li>
 * </ul>
 */
public final class PirateGearModels {

    public static final ModelLayerLocation SWORDSMAN = new ModelLayerLocation(BloxPiece.id("pirate_swordsman"), "main");
    public static final ModelLayerLocation GUNNER = new ModelLayerLocation(BloxPiece.id("pirate_gunner"), "main");
    public static final ModelLayerLocation CAPTAIN = new ModelLayerLocation(BloxPiece.id("pirate_captain"), "main");

    private PirateGearModels() {
    }

    /** Pirate Sabreur. */
    public static LayerDefinition swordsman() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition cuff_right = root.getChild("right_arm").addOrReplaceChild("cuff_right", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-3.0F, 4.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.4F)), PartPose.ZERO);
        PartDefinition cuff_left = root.getChild("left_arm").addOrReplaceChild("cuff_left", CubeListBuilder.create().texOffs(17, 32)
                .addBox(-1.0F, 4.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.4F)), PartPose.ZERO);
        PartDefinition boot_cuff_right = root.getChild("right_leg").addOrReplaceChild("boot_cuff_right", CubeListBuilder.create().texOffs(34, 32)
                .addBox(-2.0F, 7.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.45F)), PartPose.ZERO);
        PartDefinition boot_cuff_left = root.getChild("left_leg").addOrReplaceChild("boot_cuff_left", CubeListBuilder.create().texOffs(51, 32)
                .addBox(-2.0F, 7.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.45F)), PartPose.ZERO);
        PartDefinition sash_band = root.getChild("body").addOrReplaceChild("sash_band", CubeListBuilder.create().texOffs(68, 32)
                .addBox(-4.0F, 8.0F, -2.0F, 8, 1, 4, new CubeDeformation(0.35F)), PartPose.ZERO);
        PartDefinition belt = root.getChild("body").addOrReplaceChild("belt", CubeListBuilder.create().texOffs(93, 32)
                .addBox(-4.0F, 9.0F, -2.0F, 8, 2, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition buckle = root.getChild("body").addOrReplaceChild("buckle", CubeListBuilder.create().texOffs(118, 32)
                .addBox(-1.5F, 9.0F, -2.95F, 3, 2, 1, new CubeDeformation(0.1F)), PartPose.ZERO);
        PartDefinition sash_knot = root.getChild("body").addOrReplaceChild("sash_knot", CubeListBuilder.create().texOffs(0, 39)
                .addBox(-1.0F, -1.0F, -0.5F, 2, 2, 1, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(2.6F, 8.6F, -2.5F, 0.0F, 0.0F, -0.2F));
        PartDefinition sash_tail_a = root.getChild("body").addOrReplaceChild("sash_tail_a", CubeListBuilder.create().texOffs(7, 39)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 4, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(2.2F, 9.4F, -2.7F, -0.08F, 0.0F, 0.12F));
        PartDefinition sash_tail_b = root.getChild("body").addOrReplaceChild("sash_tail_b", CubeListBuilder.create().texOffs(12, 39)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(3.2F, 9.4F, -2.6F, -0.05F, 0.0F, -0.18F));
        PartDefinition bandana = root.getChild("head").addOrReplaceChild("bandana", CubeListBuilder.create().texOffs(17, 39)
                .addBox(-4.0F, -8.0F, -4.0F, 8, 3, 8, new CubeDeformation(0.35F)), PartPose.ZERO);
        PartDefinition bandana_knot = root.getChild("head").addOrReplaceChild("bandana_knot", CubeListBuilder.create().texOffs(50, 39)
                .addBox(-1.0F, -1.0F, 0.0F, 2, 2, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -6.6F, 4.4F, 0.0F, 0.0F, 0.785F));
        PartDefinition bandana_tail_a = root.getChild("head").addOrReplaceChild("bandana_tail_a", CubeListBuilder.create().texOffs(57, 39)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 4, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-0.4F, -6.2F, 4.8F, 0.35F, 0.0F, 0.3F));
        PartDefinition bandana_tail_b = root.getChild("head").addOrReplaceChild("bandana_tail_b", CubeListBuilder.create().texOffs(62, 39)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.5F, -6.2F, 4.8F, 0.25F, 0.0F, -0.32F));
        PartDefinition scabbard = root.getChild("body").addOrReplaceChild("scabbard", CubeListBuilder.create().texOffs(67, 39)
                .addBox(-0.5F, -2.0F, -1.0F, 1, 10, 2, CubeDeformation.NONE), PartPose.offsetAndRotation(4.5F, 9.5F, 0.4F, 1.0F, 0.0F, -0.22F));
        PartDefinition scabbard_throat = scabbard.addOrReplaceChild("scabbard_throat", CubeListBuilder.create().texOffs(74, 39)
                .addBox(-0.5F, -2.0F, -1.0F, 1, 1, 2, new CubeDeformation(0.15F)), PartPose.ZERO);
        PartDefinition scabbard_chape = scabbard.addOrReplaceChild("scabbard_chape", CubeListBuilder.create().texOffs(81, 39)
                .addBox(-0.5F, 7.0F, -1.0F, 1, 1, 2, new CubeDeformation(0.15F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Pirate Tireur. */
    public static LayerDefinition gunner() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition cuff_right = root.getChild("right_arm").addOrReplaceChild("cuff_right", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-3.0F, 4.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.4F)), PartPose.ZERO);
        PartDefinition cuff_left = root.getChild("left_arm").addOrReplaceChild("cuff_left", CubeListBuilder.create().texOffs(17, 32)
                .addBox(-1.0F, 4.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.4F)), PartPose.ZERO);
        PartDefinition boot_cuff_right = root.getChild("right_leg").addOrReplaceChild("boot_cuff_right", CubeListBuilder.create().texOffs(34, 32)
                .addBox(-2.0F, 7.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.45F)), PartPose.ZERO);
        PartDefinition boot_cuff_left = root.getChild("left_leg").addOrReplaceChild("boot_cuff_left", CubeListBuilder.create().texOffs(51, 32)
                .addBox(-2.0F, 7.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.45F)), PartPose.ZERO);
        PartDefinition sash_band = root.getChild("body").addOrReplaceChild("sash_band", CubeListBuilder.create().texOffs(68, 32)
                .addBox(-4.0F, 8.0F, -2.0F, 8, 1, 4, new CubeDeformation(0.35F)), PartPose.ZERO);
        PartDefinition belt = root.getChild("body").addOrReplaceChild("belt", CubeListBuilder.create().texOffs(93, 32)
                .addBox(-4.0F, 9.0F, -2.0F, 8, 2, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition buckle = root.getChild("body").addOrReplaceChild("buckle", CubeListBuilder.create().texOffs(118, 32)
                .addBox(-1.5F, 9.0F, -2.95F, 3, 2, 1, new CubeDeformation(0.1F)), PartPose.ZERO);
        PartDefinition sash_knot = root.getChild("body").addOrReplaceChild("sash_knot", CubeListBuilder.create().texOffs(0, 39)
                .addBox(-1.0F, -1.0F, -0.5F, 2, 2, 1, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(2.6F, 8.6F, -2.5F, 0.0F, 0.0F, -0.2F));
        PartDefinition sash_tail_a = root.getChild("body").addOrReplaceChild("sash_tail_a", CubeListBuilder.create().texOffs(7, 39)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 4, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(2.2F, 9.4F, -2.7F, -0.08F, 0.0F, 0.12F));
        PartDefinition sash_tail_b = root.getChild("body").addOrReplaceChild("sash_tail_b", CubeListBuilder.create().texOffs(12, 39)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(3.2F, 9.4F, -2.6F, -0.05F, 0.0F, -0.18F));
        PartDefinition tricorne_crown = root.getChild("head").addOrReplaceChild("tricorne_crown", CubeListBuilder.create().texOffs(17, 39)
                .addBox(-4.0F, -10.5F, -4.0F, 8, 3, 8, new CubeDeformation(0.5F)), PartPose.ZERO);
        PartDefinition tricorne_back = root.getChild("head").addOrReplaceChild("tricorne_back", CubeListBuilder.create().texOffs(50, 39)
                .addBox(-6.5F, -3.0F, -0.5F, 13, 3, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -7.4F, 4.6F, 0.12F, 0.0F, 0.0F));
        PartDefinition tricorne_left = root.getChild("head").addOrReplaceChild("tricorne_left", CubeListBuilder.create().texOffs(79, 39)
                .addBox(0.0F, -3.0F, -0.5F, 13, 3, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -7.4F, -6.6F, 0.0F, -1.041F, -0.1F));
        PartDefinition tricorne_right = root.getChild("head").addOrReplaceChild("tricorne_right", CubeListBuilder.create().texOffs(0, 51)
                .addBox(0.0F, -3.0F, -0.5F, 13, 3, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -7.4F, -6.6F, 0.0F, -2.1005F, 0.1F));
        PartDefinition feather = root.getChild("head").addOrReplaceChild("feather", CubeListBuilder.create().texOffs(29, 51)
                .addBox(-0.5F, -6.0F, -0.5F, 1, 6, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(4.2F, -10.2F, 2.5F, -0.55F, 0.0F, 0.45F));
        PartDefinition bandolier = root.getChild("body").addOrReplaceChild("bandolier", CubeListBuilder.create().texOffs(34, 51)
                .addBox(-1.0F, -6.5F, -2.0F, 2, 13, 4, new CubeDeformation(0.45F)), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.0F, 0.0F, -0.588F));
        PartDefinition pouch_top = bandolier.addOrReplaceChild("pouch_top", CubeListBuilder.create().texOffs(47, 51)
                .addBox(-1.0F, -4.0F, -3.2F, 2, 2, 1, new CubeDeformation(0.12F)), PartPose.ZERO);
        PartDefinition pouch_mid = bandolier.addOrReplaceChild("pouch_mid", CubeListBuilder.create().texOffs(54, 51)
                .addBox(-1.0F, -1.0F, -3.2F, 2, 2, 1, new CubeDeformation(0.12F)), PartPose.ZERO);
        PartDefinition pouch_low = bandolier.addOrReplaceChild("pouch_low", CubeListBuilder.create().texOffs(61, 51)
                .addBox(-1.0F, 2.0F, -3.2F, 2, 2, 1, new CubeDeformation(0.12F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Capitaine pirate : grand tricorne à plumes, grande cape, épaulettes dorées. */
    public static LayerDefinition captain() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition cuff_right = root.getChild("right_arm").addOrReplaceChild("cuff_right", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-3.0F, 4.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.4F)), PartPose.ZERO);
        PartDefinition cuff_left = root.getChild("left_arm").addOrReplaceChild("cuff_left", CubeListBuilder.create().texOffs(17, 32)
                .addBox(-1.0F, 4.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.4F)), PartPose.ZERO);
        PartDefinition boot_cuff_right = root.getChild("right_leg").addOrReplaceChild("boot_cuff_right", CubeListBuilder.create().texOffs(34, 32)
                .addBox(-2.0F, 7.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.45F)), PartPose.ZERO);
        PartDefinition boot_cuff_left = root.getChild("left_leg").addOrReplaceChild("boot_cuff_left", CubeListBuilder.create().texOffs(51, 32)
                .addBox(-2.0F, 7.0F, -2.0F, 4, 2, 4, new CubeDeformation(0.45F)), PartPose.ZERO);
        PartDefinition sash_band = root.getChild("body").addOrReplaceChild("sash_band", CubeListBuilder.create().texOffs(68, 32)
                .addBox(-4.0F, 8.0F, -2.0F, 8, 1, 4, new CubeDeformation(0.35F)), PartPose.ZERO);
        PartDefinition belt = root.getChild("body").addOrReplaceChild("belt", CubeListBuilder.create().texOffs(93, 32)
                .addBox(-4.0F, 9.0F, -2.0F, 8, 2, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition buckle = root.getChild("body").addOrReplaceChild("buckle", CubeListBuilder.create().texOffs(118, 32)
                .addBox(-1.5F, 9.0F, -2.95F, 3, 2, 1, new CubeDeformation(0.1F)), PartPose.ZERO);
        PartDefinition sash_knot = root.getChild("body").addOrReplaceChild("sash_knot", CubeListBuilder.create().texOffs(0, 39)
                .addBox(-1.0F, -1.0F, -0.5F, 2, 2, 1, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(2.6F, 8.6F, -2.5F, 0.0F, 0.0F, -0.2F));
        PartDefinition sash_tail_a = root.getChild("body").addOrReplaceChild("sash_tail_a", CubeListBuilder.create().texOffs(7, 39)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 4, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(2.2F, 9.4F, -2.7F, -0.08F, 0.0F, 0.12F));
        PartDefinition sash_tail_b = root.getChild("body").addOrReplaceChild("sash_tail_b", CubeListBuilder.create().texOffs(12, 39)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(3.2F, 9.4F, -2.6F, -0.05F, 0.0F, -0.18F));
        PartDefinition hat_crown = root.getChild("head").addOrReplaceChild("hat_crown", CubeListBuilder.create().texOffs(17, 39)
                .addBox(-4.5F, -12.0F, -4.5F, 9, 4, 9, new CubeDeformation(0.5F)), PartPose.ZERO);
        PartDefinition hat_back = root.getChild("head").addOrReplaceChild("hat_back", CubeListBuilder.create().texOffs(54, 39)
                .addBox(-8.5F, -4.0F, -0.5F, 17, 4, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -7.4F, 6.1F, 0.15F, 0.0F, 0.0F));
        PartDefinition hat_left = root.getChild("head").addOrReplaceChild("hat_left", CubeListBuilder.create().texOffs(91, 39)
                .addBox(0.0F, -4.0F, -0.5F, 17, 4, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -7.4F, -8.5F, 0.0F, -1.0436F, -0.12F));
        PartDefinition hat_right = root.getChild("head").addOrReplaceChild("hat_right", CubeListBuilder.create().texOffs(0, 53)
                .addBox(0.0F, -4.0F, -0.5F, 17, 4, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -7.4F, -8.5F, 0.0F, -2.098F, 0.12F));
        PartDefinition hat_plume = root.getChild("head").addOrReplaceChild("hat_plume", CubeListBuilder.create().texOffs(37, 53)
                .addBox(-1.0F, -8.0F, -0.5F, 2, 8, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(4.6F, -11.6F, 1.5F, -0.7F, 0.0F, 0.5F));
        PartDefinition hat_plume_b = root.getChild("head").addOrReplaceChild("hat_plume_b", CubeListBuilder.create().texOffs(44, 53)
                .addBox(-0.5F, -6.0F, -0.5F, 1, 6, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(3.8F, -11.4F, 3.0F, -1.0F, 0.0F, 0.3F));
        PartDefinition mantle = root.getChild("body").addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(49, 53)
                .addBox(-5.0F, -1.5F, -3.0F, 10, 2, 6, new CubeDeformation(0.4F)), PartPose.ZERO);
        PartDefinition cape = root.getChild("body").addOrReplaceChild("cape", CubeListBuilder.create().texOffs(82, 53)
                .addBox(-6.5F, 0.0F, 0.0F, 13, 22, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -0.6F, 3.0F, 0.2F, 0.0F, 0.0F));
        PartDefinition cape_side_right = cape.addOrReplaceChild("cape_side_right", CubeListBuilder.create().texOffs(111, 53)
                .addBox(-3.0F, 0.0F, -0.5F, 3, 22, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-6.5F, 0.0F, 0.5F, 0.0F, -0.75F, 0.0F));
        PartDefinition cape_side_left = cape.addOrReplaceChild("cape_side_left", CubeListBuilder.create().texOffs(120, 53)
                .addBox(0.0F, 0.0F, -0.5F, 3, 22, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(6.5F, 0.0F, 0.5F, 0.0F, 0.75F, 0.0F));
        PartDefinition epaulette_right = root.getChild("right_arm").addOrReplaceChild("epaulette_right", CubeListBuilder.create().texOffs(0, 77)
                .addBox(-5.0F, -3.4F, -3.0F, 7, 2, 6, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition fringe_right = root.getChild("right_arm").addOrReplaceChild("fringe_right", CubeListBuilder.create().texOffs(27, 77)
                .addBox(-5.0F, -1.4F, -3.0F, 7, 2, 6, new CubeDeformation(0.05F)), PartPose.ZERO);
        PartDefinition epaulette_left = root.getChild("left_arm").addOrReplaceChild("epaulette_left", CubeListBuilder.create().texOffs(54, 77)
                .addBox(-2.0F, -3.4F, -3.0F, 7, 2, 6, CubeDeformation.NONE), PartPose.ZERO);
        PartDefinition fringe_left = root.getChild("left_arm").addOrReplaceChild("fringe_left", CubeListBuilder.create().texOffs(81, 77)
                .addBox(-2.0F, -1.4F, -3.0F, 7, 2, 6, new CubeDeformation(0.05F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }
}
