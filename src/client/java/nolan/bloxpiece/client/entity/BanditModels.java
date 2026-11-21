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
 * Géométrie des bandits (camp de bandits) : même construction que les pirates (PirateGearModels) : corps humanoïde classique +
 * équipement 3D en relief attaché aux parties du corps (il suit les animations). Texture 128x128 : peau en haut à gauche
 * (disposition classique 64x32), équipement en dessous. FICHIER GÉNÉRÉ par src/blockbench/make_bandits.py (avec les textures
 * bandit_N / bandit_chief_0 et les fichiers Blockbench src/blockbench/bandit.bbmodel, bandit_chief.bbmodel).
 *
 * <ul>
 *   <li>Bandit : capuche ample (brune, olive, grise ou rouge sombre selon la variante), foulard sur le bas du visage noué
 *       derrière la tête, gilet de cuir ouvert, bandoulière, épaulière cloutée, brassards, bandes molletières, fourreau.</li>
 *   <li>Chef : bandana noir noué à longs pans, barbe noire, cicatrice sur l'oeil gauche, manteau de fourrure sur les épaules et
 *       courte cape rouge sombre doublée de fourrure, gilet ouvert sur le torse, grosse boucle dorée, brassards à fourrure.</li>
 * </ul>
 */
public final class BanditModels {

    public static final ModelLayerLocation BANDIT = new ModelLayerLocation(BloxPiece.id("bandit"), "main");
    public static final ModelLayerLocation CHIEF = new ModelLayerLocation(BloxPiece.id("bandit_chief"), "main");

    private BanditModels() {
    }

    /** Bandit : capuche, foulard sur le bas du visage, gilet de cuir, bandoulière, épaulière cloutée. */
    public static LayerDefinition bandit() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition hood = root.getChild("head").addOrReplaceChild("hood", CubeListBuilder.create().texOffs(77, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, new CubeDeformation(0.6F)), PartPose.ZERO);
        PartDefinition hood_drape = root.getChild("head").addOrReplaceChild("hood_drape", CubeListBuilder.create().texOffs(97, 32)
                .addBox(-3.5F, 0.0F, 0.0F, 7, 3, 2, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -0.5F, 2.6F, 0.35F, 0.0F, 0.0F));
        PartDefinition mask = root.getChild("head").addOrReplaceChild("mask", CubeListBuilder.create().texOffs(0, 42)
                .addBox(-4.0F, -4.0F, -0.5F, 8, 4, 1, new CubeDeformation(0.12F)), PartPose.offset(0.0F, 0.0F, -4.1F));
        PartDefinition mask_knot = root.getChild("head").addOrReplaceChild("mask_knot", CubeListBuilder.create().texOffs(19, 42)
                .addBox(-1.0F, -1.0F, 0.0F, 2, 2, 1, CubeDeformation.NONE), PartPose.offset(0.0F, -2.5F, 4.5F));
        PartDefinition vest = root.getChild("body").addOrReplaceChild("vest", CubeListBuilder.create().texOffs(64, 18)
                .addBox(-4.0F, 0.0F, -2.0F, 8, 10, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition strap = root.getChild("body").addOrReplaceChild("strap", CubeListBuilder.create().texOffs(64, 0)
                .addBox(-1.0F, -6.5F, -2.0F, 2, 13, 4, new CubeDeformation(0.45F)), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.0F, 0.0F, -0.588F));
        PartDefinition pauldron = root.getChild("right_arm").addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(17, 32)
                .addBox(-4.0F, -2.6F, -2.5F, 5, 3, 5, new CubeDeformation(0.1F)), PartPose.ZERO);
        PartDefinition belt = root.getChild("body").addOrReplaceChild("belt", CubeListBuilder.create().texOffs(72, 32)
                .addBox(-4.0F, 9.0F, -2.0F, 8, 2, 4, new CubeDeformation(0.42F)), PartPose.ZERO);
        PartDefinition buckle = root.getChild("body").addOrReplaceChild("buckle", CubeListBuilder.create().texOffs(26, 42)
                .addBox(-1.0F, 9.0F, -2.95F, 2, 2, 1, new CubeDeformation(0.12F)), PartPose.ZERO);
        PartDefinition bracer_right = root.getChild("right_arm").addOrReplaceChild("bracer_right", CubeListBuilder.create().texOffs(38, 32)
                .addBox(-3.0F, 6.0F, -2.0F, 4, 4, 4, new CubeDeformation(0.35F)), PartPose.ZERO);
        PartDefinition bracer_left = root.getChild("left_arm").addOrReplaceChild("bracer_left", CubeListBuilder.create().texOffs(55, 32)
                .addBox(-1.0F, 6.0F, -2.0F, 4, 4, 4, new CubeDeformation(0.35F)), PartPose.ZERO);
        PartDefinition wrap_right = root.getChild("right_leg").addOrReplaceChild("wrap_right", CubeListBuilder.create().texOffs(96, 18)
                .addBox(-2.0F, 6.0F, -2.0F, 4, 5, 4, new CubeDeformation(0.38F)), PartPose.ZERO);
        PartDefinition wrap_left = root.getChild("left_leg").addOrReplaceChild("wrap_left", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-2.0F, 6.0F, -2.0F, 4, 5, 4, new CubeDeformation(0.38F)), PartPose.ZERO);
        PartDefinition scabbard = root.getChild("body").addOrReplaceChild("scabbard", CubeListBuilder.create().texOffs(89, 18)
                .addBox(-0.5F, -2.0F, -1.0F, 1, 11, 2, CubeDeformation.NONE), PartPose.offsetAndRotation(4.5F, 9.5F, 0.4F, 1.0F, 0.0F, -0.22F));
        PartDefinition scabbard_chape = scabbard.addOrReplaceChild("scabbard_chape", CubeListBuilder.create().texOffs(33, 42)
                .addBox(-0.5F, 8.0F, -1.0F, 1, 1, 2, new CubeDeformation(0.15F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Chef des bandits : bandana noir, barbe, cicatrice, manteau de fourrure, courte cape, gilet ouvert. */
    public static LayerDefinition chief() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition bandana = root.getChild("head").addOrReplaceChild("bandana", CubeListBuilder.create().texOffs(64, 16)
                .addBox(-4.0F, -8.0F, -4.0F, 8, 3, 8, new CubeDeformation(0.4F)), PartPose.ZERO);
        PartDefinition bandana_knot = root.getChild("head").addOrReplaceChild("bandana_knot", CubeListBuilder.create().texOffs(88, 28)
                .addBox(-1.0F, -1.0F, 0.0F, 2, 2, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, -6.6F, 4.5F, 0.0F, 0.0F, 0.785F));
        PartDefinition bandana_tail_a = root.getChild("head").addOrReplaceChild("bandana_tail_a", CubeListBuilder.create().texOffs(105, 32)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 5, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(-0.4F, -6.2F, 4.9F, 0.35F, 0.0F, 0.3F));
        PartDefinition bandana_tail_b = root.getChild("head").addOrReplaceChild("bandana_tail_b", CubeListBuilder.create().texOffs(42, 43)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 4, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.5F, -6.2F, 4.9F, 0.25F, 0.0F, -0.32F));
        PartDefinition beard = root.getChild("head").addOrReplaceChild("beard", CubeListBuilder.create().texOffs(64, 28)
                .addBox(-3.0F, -2.2F, -1.0F, 6, 3, 1, new CubeDeformation(0.1F)), PartPose.offset(0.0F, 0.0F, -4.0F));
        PartDefinition beard_tip = beard.addOrReplaceChild("beard_tip", CubeListBuilder.create().texOffs(95, 28)
                .addBox(-1.5F, 0.0F, -1.0F, 3, 2, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.8F, 0.0F, -0.2F, 0.0F, 0.0F));
        PartDefinition fur_mantle = root.getChild("body").addOrReplaceChild("fur_mantle", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-5.5F, -2.0F, -3.5F, 11, 3, 7, new CubeDeformation(0.35F)), PartPose.ZERO);
        PartDefinition fur_cape = root.getChild("body").addOrReplaceChild("fur_cape", CubeListBuilder.create().texOffs(64, 0)
                .addBox(-5.0F, 0.0F, 0.0F, 10, 14, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 0.5F, 2.8F, 0.15F, 0.0F, 0.0F));
        PartDefinition vest = root.getChild("body").addOrReplaceChild("vest", CubeListBuilder.create().texOffs(87, 0)
                .addBox(-4.0F, 0.0F, -2.0F, 8, 10, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition fur_cuff_right = root.getChild("right_arm").addOrReplaceChild("fur_cuff_right", CubeListBuilder.create().texOffs(110, 32)
                .addBox(-3.0F, -2.5F, -2.0F, 4, 2, 4, new CubeDeformation(0.6F)), PartPose.ZERO);
        PartDefinition fur_cuff_left = root.getChild("left_arm").addOrReplaceChild("fur_cuff_left", CubeListBuilder.create().texOffs(0, 43)
                .addBox(-1.0F, -2.5F, -2.0F, 4, 2, 4, new CubeDeformation(0.6F)), PartPose.ZERO);
        PartDefinition belt = root.getChild("body").addOrReplaceChild("belt", CubeListBuilder.create().texOffs(17, 43)
                .addBox(-4.0F, 9.0F, -2.0F, 8, 2, 4, new CubeDeformation(0.42F)), PartPose.ZERO);
        PartDefinition buckle = root.getChild("body").addOrReplaceChild("buckle", CubeListBuilder.create().texOffs(104, 28)
                .addBox(-1.0F, 9.0F, -2.95F, 2, 2, 1, new CubeDeformation(0.12F)), PartPose.ZERO);
        PartDefinition bracer_right = root.getChild("right_arm").addOrReplaceChild("bracer_right", CubeListBuilder.create().texOffs(71, 32)
                .addBox(-3.0F, 6.0F, -2.0F, 4, 4, 4, new CubeDeformation(0.35F)), PartPose.ZERO);
        PartDefinition bracer_left = root.getChild("left_arm").addOrReplaceChild("bracer_left", CubeListBuilder.create().texOffs(88, 32)
                .addBox(-1.0F, 6.0F, -2.0F, 4, 4, 4, new CubeDeformation(0.35F)), PartPose.ZERO);
        PartDefinition wrap_right = root.getChild("right_leg").addOrReplaceChild("wrap_right", CubeListBuilder.create().texOffs(37, 32)
                .addBox(-2.0F, 6.0F, -2.0F, 4, 5, 4, new CubeDeformation(0.38F)), PartPose.ZERO);
        PartDefinition wrap_left = root.getChild("left_leg").addOrReplaceChild("wrap_left", CubeListBuilder.create().texOffs(54, 32)
                .addBox(-2.0F, 6.0F, -2.0F, 4, 5, 4, new CubeDeformation(0.38F)), PartPose.ZERO);
        PartDefinition scabbard = root.getChild("body").addOrReplaceChild("scabbard", CubeListBuilder.create().texOffs(112, 0)
                .addBox(-0.5F, -2.0F, -1.0F, 1, 11, 2, CubeDeformation.NONE), PartPose.offsetAndRotation(4.5F, 9.5F, 0.4F, 1.0F, 0.0F, -0.22F));
        PartDefinition scabbard_chape = scabbard.addOrReplaceChild("scabbard_chape", CubeListBuilder.create().texOffs(111, 28)
                .addBox(-0.5F, 8.0F, -1.0F, 1, 1, 2, new CubeDeformation(0.15F)), PartPose.ZERO);
        PartDefinition big_buckle = root.getChild("body").addOrReplaceChild("big_buckle", CubeListBuilder.create().texOffs(79, 28)
                .addBox(-1.5F, 8.6F, -3.1F, 3, 3, 1, new CubeDeformation(0.1F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }
}
