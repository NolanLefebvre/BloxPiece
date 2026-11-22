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
 * Géométrie d'Ener : même construction que Gol D. Roger et Silvers Rayleigh (corps humanoïde classique + équipement 3D attaché
 * aux parties du corps, il suit les animations). Texture 128x128 : peau en haut à gauche (disposition classique 64x32),
 * équipement en dessous. FICHIER GÉNÉRÉ par src/blockbench/make_ener.py (avec la texture ener et le fichier Blockbench
 * src/blockbench/ener.bbmodel). Animations : EnerModel.
 *
 * <ul>
 *   <li>Tête : bandeau blanc bas sur le front, noué dans la nuque (deux pans) ; regard mi-clos, sourire narquois, favoris et
 *       cheveux blonds aux tempes, sans barbe ; lobes d'oreilles démesurés qui pendent devant les pectoraux, boucles dorées en losange gravées.</li>
 *   <li>Torse nu musclé ; écharpe violette nouée sur la hanche gauche, son pan tombe jusqu'aux chevilles ; pagne bleu-vert
 *       (devant en deux pans croisés, dos) à liseré original : zigzag d'éclair doré et rangée de losanges.</li>
 *   <li>Sarouel orange bouffant à côtes diagonales et bandes bleu nuit à pois ; revers, chevillères dorées gravées ; pieds nus.</li>
 *   <li>Bracelets dorés empilés aux poignets (gravure en zigzag) ; bâton doré à bagues dans la main droite.</li>
 *   <li>Dans le dos : grand anneau de métal clair juste derrière la tête, fixé sur le haut du dos par deux tiges recourbées
 *       (platines dorées), et ses quatre tambours
 *       (virgules bleu nuit autour d'un éclat doré, cercle doré gravé).</li>
 * </ul>
 */
public final class EnerModels {

    public static final ModelLayerLocation ENER = new ModelLayerLocation(BloxPiece.id("ener"), "main");

    private EnerModels() {
    }

    public static LayerDefinition ener() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition bandana_knot = root.getChild("head").addOrReplaceChild("bandana_knot", CubeListBuilder.create().texOffs(85, 76)
                .addBox(-1.5F, -1.5F, 0.0F, 3, 3, 1, CubeDeformation.NONE), PartPose.offset(0.0F, -5.0F, 4.3F));
        PartDefinition bandana_tail_right = bandana_knot.addOrReplaceChild("bandana_tail_right", CubeListBuilder.create().texOffs(89, 25)
                .addBox(-1.0F, 0.0F, 0.0F, 2, 5, 1, new CubeDeformation(-0.15F)), PartPose.offsetAndRotation(-0.6F, 1.0F, 0.3F, 0.25F, 0.0F, 0.2F));
        PartDefinition bandana_tail_left = bandana_knot.addOrReplaceChild("bandana_tail_left", CubeListBuilder.create().texOffs(107, 32)
                .addBox(-1.0F, 0.0F, 0.0F, 2, 4, 1, new CubeDeformation(-0.15F)), PartPose.offsetAndRotation(0.7F, 1.0F, 0.3F, 0.3F, 0.0F, -0.25F));
        PartDefinition ear_right = root.getChild("head").addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(98, 0)
                .addBox(-1.0F, 0.0F, -0.5F, 2, 11, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-3.9F, -3.6F, -0.3F, -0.3F, 0.0F, -0.15F));
        PartDefinition ear_left = root.getChild("head").addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(105, 0)
                .addBox(-1.0F, 0.0F, -0.5F, 2, 11, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(3.9F, -3.6F, -0.3F, -0.3F, 0.0F, 0.15F));
        PartDefinition earring_right = ear_right.addOrReplaceChild("earring_right", CubeListBuilder.create().texOffs(9, 82)
                .addBox(-1.0F, -1.0F, -0.5F, 2, 2, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(0.0F, 11.4F, 0.0F, 0.0F, 0.0F, 0.785F));
        PartDefinition earring_left = ear_left.addOrReplaceChild("earring_left", CubeListBuilder.create().texOffs(16, 82)
                .addBox(-1.0F, -1.0F, -0.5F, 2, 2, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(0.0F, 11.4F, 0.0F, 0.0F, 0.0F, 0.785F));
        PartDefinition sash = root.getChild("body").addOrReplaceChild("sash", CubeListBuilder.create().texOffs(64, 25)
                .addBox(-4.0F, 8.0F, -2.0F, 8, 3, 4, new CubeDeformation(0.7F)), PartPose.ZERO);
        PartDefinition sash_knot = root.getChild("body").addOrReplaceChild("sash_knot", CubeListBuilder.create().texOffs(94, 76)
                .addBox(-1.5F, -1.5F, -1.0F, 3, 3, 1, CubeDeformation.NONE), PartPose.offset(2.4F, 9.6F, -2.7F));
        PartDefinition sash_tail = root.getChild("body").addOrReplaceChild("sash_tail", CubeListBuilder.create().texOffs(112, 0)
                .addBox(-1.0F, 0.0F, -0.5F, 2, 11, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(3.0F, 10.4F, -5.1F, 0.0F, 0.0F, -0.06F));
        PartDefinition sash_tail_low = sash_tail.addOrReplaceChild("sash_tail_low", CubeListBuilder.create().texOffs(103, 76)
                .addBox(-1.5F, 0.0F, -0.5F, 3, 3, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(0.3F, 10.6F, 0.0F, 0.0F, 0.0F, 0.1F));
        PartDefinition wrap_waist = root.getChild("body").addOrReplaceChild("wrap_waist", CubeListBuilder.create().texOffs(96, 25)
                .addBox(-4.0F, 10.0F, -2.0F, 8, 2, 4, new CubeDeformation(0.85F)), PartPose.ZERO);
        PartDefinition wrap_front_right = root.getChild("body").addOrReplaceChild("wrap_front_right", CubeListBuilder.create().texOffs(47, 32)
                .addBox(-4.6F, 0.0F, 0.0F, 5, 8, 1, CubeDeformation.NONE), PartPose.offset(0.0F, 11.2F, -4.3F));
        PartDefinition wrap_front_left = root.getChild("body").addOrReplaceChild("wrap_front_left", CubeListBuilder.create().texOffs(60, 32)
                .addBox(-0.4F, 0.0F, 0.0F, 5, 7, 1, CubeDeformation.NONE), PartPose.offsetAndRotation(0.0F, 11.2F, -4.5F, 0.0F, 0.0F, -0.04F));
        PartDefinition wrap_back = root.getChild("body").addOrReplaceChild("wrap_back", CubeListBuilder.create().texOffs(64, 14)
                .addBox(-4.5F, 0.0F, 0.0F, 9, 9, 1, CubeDeformation.NONE), PartPose.offset(0.0F, 11.2F, 3.35F));
        PartDefinition bangle_right_0 = root.getChild("right_arm").addOrReplaceChild("bangle_right_0", CubeListBuilder.create().texOffs(0, 70)
                .addBox(-3.0F, 5.6F, -2.0F, 4, 1, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition bangle_left_0 = root.getChild("left_arm").addOrReplaceChild("bangle_left_0", CubeListBuilder.create().texOffs(17, 70)
                .addBox(-1.0F, 5.6F, -2.0F, 4, 1, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition bangle_right_1 = root.getChild("right_arm").addOrReplaceChild("bangle_right_1", CubeListBuilder.create().texOffs(34, 70)
                .addBox(-3.0F, 6.55F, -2.0F, 4, 1, 4, new CubeDeformation(0.27F)), PartPose.ZERO);
        PartDefinition bangle_left_1 = root.getChild("left_arm").addOrReplaceChild("bangle_left_1", CubeListBuilder.create().texOffs(51, 70)
                .addBox(-1.0F, 6.55F, -2.0F, 4, 1, 4, new CubeDeformation(0.27F)), PartPose.ZERO);
        PartDefinition bangle_right_2 = root.getChild("right_arm").addOrReplaceChild("bangle_right_2", CubeListBuilder.create().texOffs(68, 70)
                .addBox(-3.0F, 7.5F, -2.0F, 4, 1, 4, new CubeDeformation(0.24F)), PartPose.ZERO);
        PartDefinition bangle_left_2 = root.getChild("left_arm").addOrReplaceChild("bangle_left_2", CubeListBuilder.create().texOffs(85, 70)
                .addBox(-1.0F, 7.5F, -2.0F, 4, 1, 4, new CubeDeformation(0.24F)), PartPose.ZERO);
        PartDefinition staff = root.getChild("right_arm").addOrReplaceChild("staff", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-0.5F, -22.5F, -0.5F, 1, 36, 1, new CubeDeformation(0.08F)), PartPose.offsetAndRotation(-1.0F, 10.5F, -1.2F, 0.5F, 0.0F, 0.0F));
        PartDefinition staff_cap_top = staff.addOrReplaceChild("staff_cap_top", CubeListBuilder.create().texOffs(112, 76)
                .addBox(-1.0F, -1.0F, -1.0F, 2, 2, 2, new CubeDeformation(-0.1F)), PartPose.offset(0.0F, -22.5F, 0.0F));
        PartDefinition staff_cap_bottom = staff.addOrReplaceChild("staff_cap_bottom", CubeListBuilder.create().texOffs(0, 82)
                .addBox(-1.0F, -1.0F, -1.0F, 2, 2, 2, new CubeDeformation(-0.2F)), PartPose.offset(0.0F, 12.8F, 0.0F));
        PartDefinition pants_right = root.getChild("right_leg").addOrReplaceChild("pants_right", CubeListBuilder.create().texOffs(64, 0)
                .addBox(-2.0F, 0.0F, -2.0F, 4, 9, 4, new CubeDeformation(1.25F)), PartPose.ZERO);
        PartDefinition pants_puff_right = root.getChild("right_leg").addOrReplaceChild("pants_puff_right", CubeListBuilder.create().texOffs(73, 32)
                .addBox(-2.0F, 7.0F, -2.0F, 4, 2, 4, new CubeDeformation(1.55F)), PartPose.ZERO);
        PartDefinition cuff_right = root.getChild("right_leg").addOrReplaceChild("cuff_right", CubeListBuilder.create().texOffs(102, 70)
                .addBox(-2.0F, 9.0F, -2.0F, 4, 1, 4, new CubeDeformation(0.45F)), PartPose.ZERO);
        PartDefinition anklet_right_0 = root.getChild("right_leg").addOrReplaceChild("anklet_right_0", CubeListBuilder.create().texOffs(0, 76)
                .addBox(-2.0F, 10.0F, -2.0F, 4, 1, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition anklet_right_1 = root.getChild("right_leg").addOrReplaceChild("anklet_right_1", CubeListBuilder.create().texOffs(17, 76)
                .addBox(-2.0F, 10.9F, -2.0F, 4, 1, 4, new CubeDeformation(0.22F)), PartPose.ZERO);
        PartDefinition pants_left = root.getChild("left_leg").addOrReplaceChild("pants_left", CubeListBuilder.create().texOffs(81, 0)
                .addBox(-2.0F, 0.0F, -2.0F, 4, 9, 4, new CubeDeformation(1.25F)), PartPose.ZERO);
        PartDefinition pants_puff_left = root.getChild("left_leg").addOrReplaceChild("pants_puff_left", CubeListBuilder.create().texOffs(90, 32)
                .addBox(-2.0F, 7.0F, -2.0F, 4, 2, 4, new CubeDeformation(1.55F)), PartPose.ZERO);
        PartDefinition cuff_left = root.getChild("left_leg").addOrReplaceChild("cuff_left", CubeListBuilder.create().texOffs(34, 76)
                .addBox(-2.0F, 9.0F, -2.0F, 4, 1, 4, new CubeDeformation(0.45F)), PartPose.ZERO);
        PartDefinition anklet_left_0 = root.getChild("left_leg").addOrReplaceChild("anklet_left_0", CubeListBuilder.create().texOffs(51, 76)
                .addBox(-2.0F, 10.0F, -2.0F, 4, 1, 4, new CubeDeformation(0.3F)), PartPose.ZERO);
        PartDefinition anklet_left_1 = root.getChild("left_leg").addOrReplaceChild("anklet_left_1", CubeListBuilder.create().texOffs(68, 76)
                .addBox(-2.0F, 10.9F, -2.0F, 4, 1, 4, new CubeDeformation(0.22F)), PartPose.ZERO);
        PartDefinition ring_0 = root.getChild("body").addOrReplaceChild("ring_0", CubeListBuilder.create().texOffs(41, 82)
                .addBox(-2.5F, -0.5F, -0.5F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-10.431F, -6.7984F, 5.1F, 0.0F, 0.0F, -1.6855F));
        PartDefinition ring_1 = root.getChild("body").addOrReplaceChild("ring_1", CubeListBuilder.create().texOffs(54, 82)
                .addBox(-2.5F, -0.5F, -0.5F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-10.3904F, -9.5131F, 5.1F, 0.0F, 0.0F, -1.4262F));
        PartDefinition ring_2 = root.getChild("body").addOrReplaceChild("ring_2", CubeListBuilder.create().texOffs(67, 82)
                .addBox(-2.5F, -0.5F, -0.5F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-9.655F, -12.1268F, 5.1F, 0.0F, 0.0F, -1.1669F));
        PartDefinition ring_3 = root.getChild("body").addOrReplaceChild("ring_3", CubeListBuilder.create().texOffs(80, 82)
                .addBox(-2.5F, -0.5F, -0.5F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-8.2741F, -14.4644F, 5.1F, 0.0F, 0.0F, -0.9076F));
        PartDefinition ring_4 = root.getChild("body").addOrReplaceChild("ring_4", CubeListBuilder.create().texOffs(93, 82)
                .addBox(-2.5F, -0.5F, -0.5F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-6.3399F, -16.3699F, 5.1F, 0.0F, 0.0F, -0.6483F));
        PartDefinition ring_5 = root.getChild("body").addOrReplaceChild("ring_5", CubeListBuilder.create().texOffs(106, 82)
                .addBox(-2.5F, -0.5F, -0.5F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-3.9819F, -17.7157F, 5.1F, 0.0F, 0.0F, -0.389F));
        PartDefinition ring_6 = root.getChild("body").addOrReplaceChild("ring_6", CubeListBuilder.create().texOffs(0, 87)
                .addBox(-2.5F, -0.5F, -0.5F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-1.3575F, -18.4119F, 5.1F, 0.0F, 0.0F, -0.1297F));
        PartDefinition ring_7 = root.getChild("body").addOrReplaceChild("ring_7", CubeListBuilder.create().texOffs(13, 87)
                .addBox(-2.5F, -0.5F, -0.5F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(1.3575F, -18.4119F, 5.1F, 0.0F, 0.0F, 0.1297F));
        PartDefinition ring_8 = root.getChild("body").addOrReplaceChild("ring_8", CubeListBuilder.create().texOffs(26, 87)
                .addBox(-2.5F, -0.5F, -0.5F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(3.9819F, -17.7157F, 5.1F, 0.0F, 0.0F, 0.389F));
        PartDefinition ring_9 = root.getChild("body").addOrReplaceChild("ring_9", CubeListBuilder.create().texOffs(39, 87)
                .addBox(-2.5F, -0.5F, -0.5F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(6.3399F, -16.3699F, 5.1F, 0.0F, 0.0F, 0.6483F));
        PartDefinition ring_10 = root.getChild("body").addOrReplaceChild("ring_10", CubeListBuilder.create().texOffs(52, 87)
                .addBox(-2.5F, -0.5F, -0.5F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(8.2741F, -14.4644F, 5.1F, 0.0F, 0.0F, 0.9076F));
        PartDefinition ring_11 = root.getChild("body").addOrReplaceChild("ring_11", CubeListBuilder.create().texOffs(65, 87)
                .addBox(-2.5F, -0.5F, -0.5F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(9.655F, -12.1268F, 5.1F, 0.0F, 0.0F, 1.1669F));
        PartDefinition ring_12 = root.getChild("body").addOrReplaceChild("ring_12", CubeListBuilder.create().texOffs(78, 87)
                .addBox(-2.5F, -0.5F, -0.5F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(10.3904F, -9.5131F, 5.1F, 0.0F, 0.0F, 1.4262F));
        PartDefinition ring_13 = root.getChild("body").addOrReplaceChild("ring_13", CubeListBuilder.create().texOffs(91, 87)
                .addBox(-2.5F, -0.5F, -0.5F, 5, 1, 1, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(10.431F, -6.7984F, 5.1F, 0.0F, 0.0F, 1.6855F));
        PartDefinition drum_upper_right = root.getChild("body").addOrReplaceChild("drum_upper_right", CubeListBuilder.create().texOffs(85, 14)
                .addBox(-4.0F, -4.0F, -1.0F, 8, 8, 2, CubeDeformation.NONE), PartPose.offset(-6.4644F, -16.2741F, 5.1F));
        PartDefinition drum_upper_left = root.getChild("body").addOrReplaceChild("drum_upper_left", CubeListBuilder.create().texOffs(106, 14)
                .addBox(-4.0F, -4.0F, -1.0F, 8, 8, 2, CubeDeformation.NONE), PartPose.offset(6.4644F, -16.2741F, 5.1F));
        PartDefinition drum_lower_right = root.getChild("body").addOrReplaceChild("drum_lower_right", CubeListBuilder.create().texOffs(5, 32)
                .addBox(-4.0F, -4.0F, -1.0F, 8, 8, 2, CubeDeformation.NONE), PartPose.offset(-10.3405F, -6.1767F, 5.1F));
        PartDefinition drum_lower_left = root.getChild("body").addOrReplaceChild("drum_lower_left", CubeListBuilder.create().texOffs(26, 32)
                .addBox(-4.0F, -4.0F, -1.0F, 8, 8, 2, CubeDeformation.NONE), PartPose.offset(10.3405F, -6.1767F, 5.1F));
        PartDefinition strap_right_0 = root.getChild("body").addOrReplaceChild("strap_right_0", CubeListBuilder.create().texOffs(104, 87)
                .addBox(-0.5F, -0.5F, -0.5F, 4, 1, 1, new CubeDeformation(0.05F)), PartPose.offsetAndRotation(-9.5405F, -2.5767F, 4.9F, 0.0F, 0.2994F, 0.8427F));
        PartDefinition strap_right_1 = root.getChild("body").addOrReplaceChild("strap_right_1", CubeListBuilder.create().texOffs(115, 87)
                .addBox(-0.5F, -0.5F, -0.5F, 4, 1, 1, new CubeDeformation(0.05F)), PartPose.offsetAndRotation(-7.6F, -0.4F, 4.0F, 0.0F, 0.4184F, 0.245F));
        PartDefinition strap_right_2 = root.getChild("body").addOrReplaceChild("strap_right_2", CubeListBuilder.create().texOffs(0, 90)
                .addBox(-0.5F, -0.5F, -0.5F, 4, 1, 1, new CubeDeformation(0.05F)), PartPose.offsetAndRotation(-5.2F, 0.2F, 2.9F, 0.0F, 0.2422F, 0.1526F));
        PartDefinition strap_right_mount = root.getChild("body").addOrReplaceChild("strap_right_mount", CubeListBuilder.create().texOffs(23, 82)
                .addBox(-1.5F, -1.0F, 0.0F, 3, 2, 1, CubeDeformation.NONE), PartPose.offset(-2.6F, 1.0F, 1.7F));
        PartDefinition strap_left_0 = root.getChild("body").addOrReplaceChild("strap_left_0", CubeListBuilder.create().texOffs(11, 90)
                .addBox(-0.5F, -0.5F, -0.5F, 4, 1, 1, new CubeDeformation(0.05F)), PartPose.offsetAndRotation(9.5405F, -2.5767F, 4.9F, 0.0F, 0.2994F, 2.2989F));
        PartDefinition strap_left_1 = root.getChild("body").addOrReplaceChild("strap_left_1", CubeListBuilder.create().texOffs(22, 90)
                .addBox(-0.5F, -0.5F, -0.5F, 4, 1, 1, new CubeDeformation(0.05F)), PartPose.offsetAndRotation(7.6F, -0.4F, 4.0F, 0.0F, 0.4184F, 2.8966F));
        PartDefinition strap_left_2 = root.getChild("body").addOrReplaceChild("strap_left_2", CubeListBuilder.create().texOffs(33, 90)
                .addBox(-0.5F, -0.5F, -0.5F, 4, 1, 1, new CubeDeformation(0.05F)), PartPose.offsetAndRotation(5.2F, 0.2F, 2.9F, 0.0F, 0.2422F, 2.9889F));
        PartDefinition strap_left_mount = root.getChild("body").addOrReplaceChild("strap_left_mount", CubeListBuilder.create().texOffs(32, 82)
                .addBox(-1.5F, -1.0F, 0.0F, 3, 2, 1, CubeDeformation.NONE), PartPose.offset(2.6F, 1.0F, 1.7F));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
