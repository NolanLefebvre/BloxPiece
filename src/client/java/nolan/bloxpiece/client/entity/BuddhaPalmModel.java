package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import nolan.bloxpiece.BloxPiece;

/**
 * Modele 3D du projectile de la Paume du Bouddha (entite BuddhaPalm) : la gigantesque main ouverte doree de Sengoku.
 *
 * <ul>
 *   <li>une paume ouverte face a l'avant (le sens de la chute), bords biseautes, dos blinde ambre, et au centre un joyau (urna)
 *       pose sur un lotus ;</li>
 *   <li>4 doigts tendus, legerement ecartes, en 2 phalanges (bout courbe vers l'avant, ongles plus clairs) + le pouce ;</li>
 *   <li>un poignet et une manche de robe evasee (lisere dore) ;</li>
 *   <li>derriere la paume, une aureole (le halo des statues du Bouddha) : un anneau de 16 plaques avec 16 rayons, qui tourne,
 *       et un anneau interieur qui tourne en sens inverse.</li>
 * </ul>
 * Plus aucune traine "collee" a la main : la traine d'energie est faite de particules laissees dans le monde (BuddhaEffects),
 * qui restent sur place et se dispersent naturellement derriere la main.
 *
 * Les doigts s'ouvrent pendant la chute (state.spread, de 0 a 1) : la main arrive a demi fermee et se deploie avant l'impact.
 *
 * Repere : pivot = centre de la paume ; l'avant regarde vers -Z ; le groupe "body" s'incline selon la hauteur du vol
 * (state.pitch), la direction horizontale et l'echelle (apparition, impact) viennent du renderer.
 * Texture 256x64, une zone de depliage par cube (UV exacts), palette or / jaune / orange en plusieurs nuances pour le relief.
 */
public class BuddhaPalmModel extends EntityModel<BuddhaPalmRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("buddha_palm"), "main");

    private static final int FINGERS = 4;

    private final ModelPart body;
    private final ModelPart palm;
    private final ModelPart halo;
    private final ModelPart rays;
    private final ModelPart haloInner;
    private final ModelPart[] fingerTips = new ModelPart[FINGERS];
    private final ModelPart[] fingers = new ModelPart[FINGERS];
    private final ModelPart thumb;

    public BuddhaPalmModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        this.body = root.getChild("body");
        this.palm = this.body.getChild("palm");
        this.halo = this.body.getChild("halo");
        this.rays = this.halo.getChild("rays");
        this.haloInner = this.body.getChild("halo_inner");
        for (int i = 0; i < FINGERS; i++) {
            this.fingers[i] = this.palm.getChild("finger_" + i);
            this.fingerTips[i] = this.fingers[i].getChild("finger_" + i + "_tip_group");
        }
        this.thumb = this.palm.getChild("thumb");
    }

    @Override
    public void setupAnim(BuddhaPalmRenderState state) {
        super.setupAnim(state); // pose d'origine : les animations ci-dessous s'y ajoutent
        float age = state.ageInTicks;

        // Toute la main suit la direction du vol
        this.body.xRot = (float) Math.toRadians(state.pitch);

        // Les doigts se deploient pendant la chute : a demi replies a l'apparition, grands ouverts a l'impact
        float closed = 1.0F - state.spread;
        for (int i = 0; i < FINGERS; i++) {
            this.fingers[i].xRot += closed * 0.55F; // xRot positif : le doigt se replie vers l avant (la paume)
            this.fingerTips[i].xRot += closed * 0.65F;
            this.fingers[i].zRot *= 0.4F + 0.6F * state.spread; // ecartement progressif
        }
        this.thumb.zRot -= closed * 0.5F;

        // La paume "respire" a peine : impression d'energie contenue (la forme reste stable et lisible)
        float pulse = 1.0F + 0.025F * (float) Math.sin(age * 0.6F);
        this.palm.xScale = pulse;
        this.palm.yScale = pulse;

        // Aureole : l'anneau tourne, l'anneau interieur en sens inverse, les rayons pulsent et s'allongent pendant la chute
        this.halo.zRot += age * 0.06F;
        this.haloInner.zRot -= age * 0.10F;
        float rayPulse = (0.85F + 0.15F * state.spread) * (1.0F + 0.06F * (float) Math.sin(age * 0.9F));
        this.rays.xScale = rayPulse;
        this.rays.yScale = rayPulse;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = group(root, "body", 0F, 24F, 0F, 0F, 0F, 0F);
        PartDefinition palm = group(body, "palm", 0F, 0F, 0F, 0F, 0F, 0F);
        cube(palm, "palm_core", 0, 0, 16, 16, 5, 0F, 0F, 0F, 0F, 0F, 0F);
        cube(palm, "palm_back", 42, 0, 14, 14, 2, 0F, 0F, 3F, 0F, 0F, 0F);
        cube(palm, "palm_heel", 74, 0, 14, 4, 6, 0F, 8F, 0.5F, 0F, 0F, 0F);
        cube(palm, "palm_rim_l", 114, 0, 1, 14, 4, -8.5F, 0F, 0F, 0F, 0F, 0F);
        cube(palm, "palm_rim_r", 124, 0, 1, 14, 4, 8.5F, 0F, 0F, 0F, 0F, 0F);
        cube(palm, "palm_lotus", 134, 0, 7, 7, 1, 0F, 0F, -3F, 0F, 0F, 45F);
        cube(palm, "palm_gem", 150, 0, 5, 5, 1, 0F, 0F, -3.75F, 0F, 0F, 0F);
        cube(palm, "palm_gem_core", 162, 0, 3, 3, 1, 0F, 0F, -4.5F, 0F, 0F, 0F);
        PartDefinition finger_0 = group(palm, "finger_0", -6F, -8F, 0F, 0F, 0F, -10F);
        cube(finger_0, "finger_0_base", 170, 0, 3, 8, 3, 0F, -4F, 0F, 0F, 0F, 0F);
        cube(finger_0, "finger_0_knuckle", 182, 0, 4, 2, 4, 0F, -1F, 0F, 0F, 0F, 0F);
        PartDefinition finger_0_tip_group = group(finger_0, "finger_0_tip_group", 0F, -8F, 0F, 10F, 0F, 0F);
        cube(finger_0_tip_group, "finger_0_tip", 198, 0, 2, 5, 2, 0F, -2.5F, 0F, 0F, 0F, 0F);
        cube(finger_0_tip_group, "finger_0_nail", 206, 0, 2, 2, 3, 0F, -4F, -0.5F, 0F, 0F, 0F);
        PartDefinition finger_1 = group(palm, "finger_1", -2F, -8F, 0F, 0F, 0F, -4F);
        cube(finger_1, "finger_1_base", 216, 0, 4, 10, 4, 0F, -5F, 0F, 0F, 0F, 0F);
        cube(finger_1, "finger_1_knuckle", 232, 0, 5, 2, 5, 0F, -1F, 0F, 0F, 0F, 0F);
        PartDefinition finger_1_tip_group = group(finger_1, "finger_1_tip_group", 0F, -10F, 0F, 10F, 0F, 0F);
        cube(finger_1_tip_group, "finger_1_tip", 0, 21, 3, 7, 3, 0F, -3.5F, 0F, 0F, 0F, 0F);
        cube(finger_1_tip_group, "finger_1_nail", 12, 21, 3, 2, 4, 0F, -6F, -0.5F, 0F, 0F, 0F);
        PartDefinition finger_2 = group(palm, "finger_2", 2F, -8F, 0F, 0F, 0F, 2F);
        cube(finger_2, "finger_2_base", 26, 21, 4, 11, 4, 0F, -5.5F, 0F, 0F, 0F, 0F);
        cube(finger_2, "finger_2_knuckle", 42, 21, 5, 2, 5, 0F, -1F, 0F, 0F, 0F, 0F);
        PartDefinition finger_2_tip_group = group(finger_2, "finger_2_tip_group", 0F, -11F, 0F, 10F, 0F, 0F);
        cube(finger_2_tip_group, "finger_2_tip", 62, 21, 3, 8, 3, 0F, -4F, 0F, 0F, 0F, 0F);
        cube(finger_2_tip_group, "finger_2_nail", 74, 21, 3, 2, 4, 0F, -7F, -0.5F, 0F, 0F, 0F);
        PartDefinition finger_3 = group(palm, "finger_3", 6F, -8F, 0F, 0F, 0F, 8F);
        cube(finger_3, "finger_3_base", 88, 21, 4, 10, 4, 0F, -5F, 0F, 0F, 0F, 0F);
        cube(finger_3, "finger_3_knuckle", 104, 21, 5, 2, 5, 0F, -1F, 0F, 0F, 0F, 0F);
        PartDefinition finger_3_tip_group = group(finger_3, "finger_3_tip_group", 0F, -10F, 0F, 10F, 0F, 0F);
        cube(finger_3_tip_group, "finger_3_tip", 124, 21, 3, 7, 3, 0F, -3.5F, 0F, 0F, 0F, 0F);
        cube(finger_3_tip_group, "finger_3_nail", 136, 21, 3, 2, 4, 0F, -6F, -0.5F, 0F, 0F, 0F);
        PartDefinition thumb = group(palm, "thumb", 8F, 3F, 0F, 0F, -20F, 50F);
        cube(thumb, "thumb_base", 150, 21, 4, 6, 4, 0F, -3F, 0F, 0F, 0F, 0F);
        PartDefinition thumb_tip_group = group(thumb, "thumb_tip_group", 0F, -6F, 0F, 0F, 0F, -15F);
        cube(thumb_tip_group, "thumb_tip", 166, 21, 3, 5, 3, 0F, -2.5F, 0F, 0F, 0F, 0F);
        cube(thumb_tip_group, "thumb_nail", 178, 21, 3, 2, 4, 0F, -4F, -0.5F, 0F, 0F, 0F);
        PartDefinition sleeve = group(body, "sleeve", 0F, 0F, 0F, 0F, 0F, 0F);
        cube(sleeve, "wrist", 192, 21, 12, 4, 5, 0F, 10F, 0.5F, 0F, 0F, 0F);
        cube(sleeve, "cuff_trim", 0, 36, 19, 1, 10, 0F, 12.5F, 1.5F, 0F, 0F, 0F);
        cube(sleeve, "cuff", 58, 36, 18, 5, 9, 0F, 15.5F, 1.5F, 0F, 0F, 0F);
        cube(sleeve, "cuff_hem", 112, 36, 20, 2, 11, 0F, 19F, 1.5F, 0F, 0F, 0F);
        PartDefinition halo = group(body, "halo", 0F, -2F, 8F, 0F, 0F, 0F);
        cube(halo, "halo_plate_0", 174, 36, 7, 2, 1, 17F, 0F, 0F, 0F, 0F, 90F);
        cube(halo, "halo_plate_1", 190, 36, 7, 2, 1, 15.706F, 6.506F, 0F, 0F, 0F, 112.5F);
        cube(halo, "halo_plate_2", 206, 36, 7, 2, 1, 12.021F, 12.021F, 0F, 0F, 0F, 135F);
        cube(halo, "halo_plate_3", 222, 36, 7, 2, 1, 6.506F, 15.706F, 0F, 0F, 0F, 157.5F);
        cube(halo, "halo_plate_4", 238, 36, 7, 2, 1, 0F, 17F, 0F, 0F, 0F, 180F);
        cube(halo, "halo_plate_5", 0, 50, 7, 2, 1, -6.506F, 15.706F, 0F, 0F, 0F, 202.5F);
        cube(halo, "halo_plate_6", 16, 50, 7, 2, 1, -12.021F, 12.021F, 0F, 0F, 0F, 225F);
        cube(halo, "halo_plate_7", 32, 50, 7, 2, 1, -15.706F, 6.506F, 0F, 0F, 0F, 247.5F);
        cube(halo, "halo_plate_8", 48, 50, 7, 2, 1, -17F, 0F, 0F, 0F, 0F, 270F);
        cube(halo, "halo_plate_9", 64, 50, 7, 2, 1, -15.706F, -6.506F, 0F, 0F, 0F, 292.5F);
        cube(halo, "halo_plate_10", 80, 50, 7, 2, 1, -12.021F, -12.021F, 0F, 0F, 0F, 315F);
        cube(halo, "halo_plate_11", 96, 50, 7, 2, 1, -6.506F, -15.706F, 0F, 0F, 0F, 337.5F);
        cube(halo, "halo_plate_12", 112, 50, 7, 2, 1, 0F, -17F, 0F, 0F, 0F, 360F);
        cube(halo, "halo_plate_13", 128, 50, 7, 2, 1, 6.506F, -15.706F, 0F, 0F, 0F, 382.5F);
        cube(halo, "halo_plate_14", 144, 50, 7, 2, 1, 12.021F, -12.021F, 0F, 0F, 0F, 405F);
        cube(halo, "halo_plate_15", 160, 50, 7, 2, 1, 15.706F, -6.506F, 0F, 0F, 0F, 427.5F);
        PartDefinition rays = group(halo, "rays", 0F, 0F, 0F, 0F, 0F, 0F);
        cube(rays, "ray_0", 176, 50, 2, 6, 1, 21.087F, 4.194F, 0.5F, 0F, 0F, 101.25F);
        cube(rays, "ray_1", 182, 50, 2, 3, 1, 16.629F, 11.111F, 0.5F, 0F, 0F, 123.75F);
        cube(rays, "ray_2", 188, 50, 2, 6, 1, 11.945F, 17.877F, 0.5F, 0F, 0F, 146.25F);
        cube(rays, "ray_3", 194, 50, 2, 3, 1, 3.902F, 19.616F, 0.5F, 0F, 0F, 168.75F);
        cube(rays, "ray_4", 200, 50, 2, 6, 1, -4.194F, 21.087F, 0.5F, 0F, 0F, 191.25F);
        cube(rays, "ray_5", 206, 50, 2, 3, 1, -11.111F, 16.629F, 0.5F, 0F, 0F, 213.75F);
        cube(rays, "ray_6", 212, 50, 2, 6, 1, -17.877F, 11.945F, 0.5F, 0F, 0F, 236.25F);
        cube(rays, "ray_7", 218, 50, 2, 3, 1, -19.616F, 3.902F, 0.5F, 0F, 0F, 258.75F);
        cube(rays, "ray_8", 224, 50, 2, 6, 1, -21.087F, -4.194F, 0.5F, 0F, 0F, 281.25F);
        cube(rays, "ray_9", 230, 50, 2, 3, 1, -16.629F, -11.111F, 0.5F, 0F, 0F, 303.75F);
        cube(rays, "ray_10", 236, 50, 2, 6, 1, -11.945F, -17.877F, 0.5F, 0F, 0F, 326.25F);
        cube(rays, "ray_11", 242, 50, 2, 3, 1, -3.902F, -19.616F, 0.5F, 0F, 0F, 348.75F);
        cube(rays, "ray_12", 248, 50, 2, 6, 1, 4.194F, -21.087F, 0.5F, 0F, 0F, 371.25F);
        cube(rays, "ray_13", 0, 57, 2, 3, 1, 11.111F, -16.629F, 0.5F, 0F, 0F, 393.75F);
        cube(rays, "ray_14", 6, 57, 2, 6, 1, 17.877F, -11.945F, 0.5F, 0F, 0F, 416.25F);
        cube(rays, "ray_15", 12, 57, 2, 3, 1, 19.616F, -3.902F, 0.5F, 0F, 0F, 438.75F);
        PartDefinition halo_inner = group(body, "halo_inner", 0F, -2F, 8F, 0F, 0F, 0F);
        cube(halo_inner, "halo_inner_0", 18, 57, 6, 1, 1, 12.5F, 0F, 1F, 0F, 0F, 90F);
        cube(halo_inner, "halo_inner_1", 32, 57, 6, 1, 1, 10.825F, 6.25F, 1F, 0F, 0F, 120F);
        cube(halo_inner, "halo_inner_2", 46, 57, 6, 1, 1, 6.25F, 10.825F, 1F, 0F, 0F, 150F);
        cube(halo_inner, "halo_inner_3", 60, 57, 6, 1, 1, 0F, 12.5F, 1F, 0F, 0F, 180F);
        cube(halo_inner, "halo_inner_4", 74, 57, 6, 1, 1, -6.25F, 10.825F, 1F, 0F, 0F, 210F);
        cube(halo_inner, "halo_inner_5", 88, 57, 6, 1, 1, -10.825F, 6.25F, 1F, 0F, 0F, 240F);
        cube(halo_inner, "halo_inner_6", 102, 57, 6, 1, 1, -12.5F, 0F, 1F, 0F, 0F, 270F);
        cube(halo_inner, "halo_inner_7", 116, 57, 6, 1, 1, -10.825F, -6.25F, 1F, 0F, 0F, 300F);
        cube(halo_inner, "halo_inner_8", 130, 57, 6, 1, 1, -6.25F, -10.825F, 1F, 0F, 0F, 330F);
        cube(halo_inner, "halo_inner_9", 144, 57, 6, 1, 1, 0F, -12.5F, 1F, 0F, 0F, 360F);
        cube(halo_inner, "halo_inner_10", 158, 57, 6, 1, 1, 6.25F, -10.825F, 1F, 0F, 0F, 390F);
        cube(halo_inner, "halo_inner_11", 172, 57, 6, 1, 1, 10.825F, -6.25F, 1F, 0F, 0F, 420F);

        return LayerDefinition.create(mesh, 256, 64);
    }

    /** Groupe vide (pivot + rotation en degres) qui sert de parent a des cubes. */
    private static PartDefinition group(PartDefinition parent, String name, float x, float y, float z,
                                        float rotX, float rotY, float rotZ) {
        return parent.addOrReplaceChild(name, CubeListBuilder.create(),
                PartPose.offsetAndRotation(x, y, z,
                        (float) Math.toRadians(rotX), (float) Math.toRadians(rotY), (float) Math.toRadians(rotZ)));
    }

    /** Cube de dimensions (dx,dy,dz) CENTRE sur (ox,oy,oz) dans son groupe, rotation en degres autour de son centre. */
    private static void cube(PartDefinition group, String name, int u, int v, float dx, float dy, float dz,
                             float ox, float oy, float oz, float rotX, float rotY, float rotZ) {
        group.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(-dx / 2.0F, -dy / 2.0F, -dz / 2.0F, dx, dy, dz),
                PartPose.offsetAndRotation(ox, oy, oz,
                        (float) Math.toRadians(rotX), (float) Math.toRadians(rotY), (float) Math.toRadians(rotZ)));
    }
}
