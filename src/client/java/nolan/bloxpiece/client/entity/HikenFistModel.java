package nolan.bloxpiece.client.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import nolan.bloxpiece.BloxPiece;

/**
 * Modele 3D du projectile de Hiken (entite HikenFist) : un poing de feu ferme (4 doigts replies + pouce), un avant-bras COURT et
 * massif fait de 3 blocs de plaques incandescentes, puis une masse de flammes compacte qui propulse le poing : 6 troncons qui
 * s'affinent regulierement (grosse base, pointe courte) et sont flanques de langues de feu qui s'ecartent.
 * Le poing (groupe "fist") est inchange. Palette identique a Dai Enkai: Entei.
 *
 * Ce fichier etait genere par make_hiken_model.py (absent du depot) : il est desormais la source de verite, a editer directement.
 *
 * Repere : pivot = centre du poing ; l'avant du poing regarde vers -Z (devant l'entite), le bras et la masse de flammes vont vers +Z.
 * Le pivot du groupe "body" sert a incliner tout le bras selon la hauteur du vol (state.pitch) ; la direction horizontale
 * vient de la rotation appliquee par le renderer (state.yaw).
 * Texture : 512x78, une zone de depliage par cube (UV exacts). Les coeurs de la masse de flammes sont grossis avec CubeDeformation :
 * la zone de texture reste celle du cube d'origine (elle est simplement etiree), aucune nouvelle texture n'est necessaire.
 */
public class HikenFistModel extends EntityModel<HikenFistRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("hiken_fist"), "main");

    private static final int TRAIL_SEGMENTS = 6;

    private final ModelPart body;
    private final ModelPart flames;
    private final ModelPart plates;
    private final ModelPart[] trail = new ModelPart[TRAIL_SEGMENTS];

    /** Les langues de feu de la masse de flammes (chacune vacille a son propre rythme) et leur decalage de phase. */
    private final List<ModelPart> tongues = new ArrayList<>();
    private final List<Float> tonguePhases = new ArrayList<>();

    public HikenFistModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        this.body = root.getChild("body");
        this.flames = this.body.getChild("flames");
        this.plates = this.body.getChild("plates");
        for (int i = 0; i < this.trail.length; i++) {
            this.trail[i] = this.body.getChild("trail_" + i);
            for (String side : new String[] {"l", "r", "u"}) {
                String name = "trail_flame_" + side + "_" + i;
                if (this.trail[i].hasChild(name)) {
                    this.tongues.add(this.trail[i].getChild(name));
                    this.tonguePhases.add(i * 1.7F + side.charAt(0) * 0.37F);
                }
            }
        }
    }

    @Override
    public void setupAnim(HikenFistRenderState state) {
        super.setupAnim(state); // remet le modele a sa pose d'origine : les animations ci-dessous s'ajoutent a cette pose
        float age = state.ageInTicks;

        // Le bras entier suit la hauteur du vol (pitch positif = vers le bas, comme la tete d'un mob)
        this.body.xRot = (float) Math.toRadians(state.pitch);

        // Masse de flammes : chaque troncon vacille SEUL (deux oscillations de frequences differentes, phase propre) au lieu de
        // suivre une onde qui parcourt tout le corps. Amplitudes faibles : la masse reste compacte, sans ondulation de serpent.
        for (int i = 0; i < this.trail.length; i++) {
            float phase = i * 2.399F; // "angle d'or" : deux troncons voisins ne sont jamais synchronises
            float amp = 0.35F + i * 0.18F;
            ModelPart seg = this.trail[i];
            seg.x += ((float) Math.sin(age * 0.83F + phase) + 0.6F * (float) Math.sin(age * 1.91F + phase * 1.7F)) * amp;
            seg.y += ((float) Math.cos(age * 0.71F + phase * 1.3F) + 0.5F * (float) Math.sin(age * 2.23F + phase)) * amp * 0.8F;
            seg.zRot += (float) Math.sin(age * 1.07F + phase) * 0.025F;
            // La masse "respire" : legere pulsation de sa section (jamais en longueur, pour ne pas allonger ni raccourcir le bras)
            seg.xScale = 1.0F + 0.045F * (float) Math.sin(age * 1.37F + phase * 0.9F) + 0.025F * (float) Math.sin(age * 3.1F + phase);
            seg.yScale = 1.0F + 0.04F * (float) Math.sin(age * 1.19F + phase * 1.4F);
        }

        // Les langues de feu crepitent, chacune a son rythme
        for (int i = 0; i < this.tongues.size(); i++) {
            ModelPart tongue = this.tongues.get(i);
            float phase = this.tonguePhases.get(i);
            tongue.yRot += (float) Math.sin(age * 1.9F + phase) * 0.10F;
            tongue.zRot += (float) Math.sin(age * 2.6F + phase * 1.3F) * 0.08F;
        }

        // Les flammes de l'avant-bras vacillent ; les plaques frissonnent a peine (le poing, lui, ne bouge jamais : forme stable)
        this.flames.yRot += (float) Math.sin(age * 0.9F) * 0.06F;
        this.flames.zRot += (float) Math.cos(age * 0.7F) * 0.05F;
        this.plates.zRot += (float) Math.sin(age * 0.5F) * 0.012F;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = group(root, "body", 0F, 24F, 0F);
        PartDefinition fist = group(body, "fist", 0F, 0F, 0F);
        PartDefinition forearm = group(body, "forearm", 0F, 0F, 0F);
        PartDefinition plates = group(body, "plates", 0F, 0F, 0F);
        PartDefinition flames = group(body, "flames", 0F, 0F, 0F);
        PartDefinition trail0 = group(body, "trail_0", 0F, 0F, 0F);
        PartDefinition trail1 = group(body, "trail_1", 0F, 0F, 0F);
        PartDefinition trail2 = group(body, "trail_2", 0F, 0F, 0F);
        PartDefinition trail3 = group(body, "trail_3", 0F, 0F, 0F);
        PartDefinition trail4 = group(body, "trail_4", 0F, 0F, 0F);
        PartDefinition trail5 = group(body, "trail_5", 0F, 0F, 0F);

        // --- Le poing (inchange) ---
        cube(fist, "back_of_hand", 0, 0, -9F, -6F, -5.5F, 18, 12, 11, 0F, -0.5F, 1.5F, 0F, 0F, 0F);
        cube(fist, "back_of_hand_top", 161, 57, -7F, -1F, -4F, 14, 2, 8, 0F, -7F, 3F, 0.732F, 0F, -1.596F);
        cube(fist, "finger_1", 221, 0, -2F, -6F, -3.5F, 4, 12, 7, -6.75F, -0.25F, -6.5F, -1.87F, 1.861F, -0.58F);
        cube(fist, "fingertip_1", 365, 57, -2F, -1.5F, -3F, 4, 3, 6, -6.75F, 6F, -5F, -3.331F, 0F, 0.669F);
        cube(fist, "knuckle_1", 143, 69, -1.5F, -1F, -1.5F, 3, 2, 3, -6.75F, -7.2F, -7.5F, 1.115F, 5.65F, 2.838F);
        cube(fist, "finger_2", 155, 0, -2F, -6.5F, -3.5F, 4, 13, 7, -2.25F, -0.5F, -7F, -1.133F, -0.158F, -1.818F);
        cube(fist, "fingertip_2", 386, 57, -2F, -1.5F, -3F, 4, 3, 6, -2.25F, 6F, -5.5F, 1.969F, 0F, -1.391F);
        cube(fist, "knuckle_2", 156, 69, -1.5F, -1F, -1.5F, 3, 2, 3, -2.25F, -7.2F, -8F, -6.278F, 3.716F, 2.559F);
        cube(fist, "finger_3", 244, 0, -2F, -6F, -3.5F, 4, 12, 7, 2.25F, -0.25F, -6.5F, 1.661F, -0.561F, -1.923F);
        cube(fist, "fingertip_3", 407, 57, -2F, -1.5F, -3F, 4, 3, 6, 2.25F, 6F, -5F, -3.466F, 0F, 2.073F);
        cube(fist, "knuckle_3", 169, 69, -1.5F, -1F, -1.5F, 3, 2, 3, 2.25F, -7.2F, -7.5F, 1.638F, 5.87F, 4.65F);
        cube(fist, "finger_4", 267, 0, -2F, -5.5F, -3.5F, 4, 11, 7, 6.75F, 0F, -6F, -1.942F, -0.07F, -1.485F);
        cube(fist, "fingertip_4", 428, 57, -2F, -1.5F, -3F, 4, 3, 6, 6.75F, 6F, -4.5F, -1.062F, 0F, 1.197F);
        cube(fist, "knuckle_4", 182, 69, -1.5F, -1F, -1.5F, 3, 2, 3, 6.75F, -7.2F, -7F, -4.807F, 0.541F, 4.204F);
        cube(fist, "thumb_base", 142, 24, -2F, -3F, -4.5F, 4, 6, 9, -9.5F, 2.5F, -1F, 0F, 0F, 8F);
        cube(fist, "thumb_tip", 206, 57, -4.5F, -2F, -3F, 9, 4, 6, -5.5F, 3.5F, -10F, 0F, 4F, -3F);
        cube(fist, "wrist_ring", 290, 0, -10F, -7.5F, -1.5F, 20, 15, 3, 0F, -0.5F, 8.5F, 0F, 0F, 0F);

        // --- Avant-bras court et massif : 3 blocs, plaques et flammes qui lechent les cotes ---
        cube(forearm, "arm_1", 59, 0, -8F, -7F, -4F, 16, 14, 8, -0.191F, -0.276F, 11F, 0F, 0F, -1.694F);
        cube(forearm, "arm_2", 108, 0, -7.5F, -6.5F, -4F, 15, 13, 8, -0.36F, -0.64F, 18F, 0F, 0F, 2.653F);
        cube(forearm, "arm_3", 178, 0, -6.5F, -6F, -4F, 13, 12, 8, 0.131F, -0.351F, 25F, 0F, 0F, -2.87F);

        cube(plates, "band_1", 337, 0, -9F, -8F, -1F, 18, 16, 2, 0F, -0.5F, 15F, 0F, 0F, -0.338F);
        cube(plates, "plate_top_1", 449, 57, -6F, -1F, -3F, 12, 2, 6, -0.951F, -8F, 11F, 2.358F, 7.854F, 6.695F);
        cube(plates, "plate_l_1", 169, 24, -1F, -4.5F, -3F, 2, 9, 6, -8.5F, 0.656F, 11F, -7.946F, 3.155F, 4.369F);
        cube(plates, "plate_r_1", 186, 24, -1F, -4.5F, -3F, 2, 9, 6, 8.5F, -0.827F, 11F, 0.338F, -8.498F, -5.78F);
        cube(plates, "band_2", 456, 0, -8.5F, -7.5F, -1F, 17, 15, 2, 0F, -0.5F, 22F, 0F, 0F, -1.935F);
        cube(plates, "plate_top_2", 0, 69, -5.5F, -1F, -3F, 11, 2, 6, -0.638F, -7.5F, 18F, 3.693F, 3.468F, -5.354F);
        cube(plates, "plate_l_2", 203, 24, -1F, -4.5F, -3F, 2, 9, 6, -8F, -0.258F, 18F, -2.828F, 7.875F, 0.298F);
        cube(plates, "plate_r_2", 220, 24, -1F, -4.5F, -3F, 2, 9, 6, 8F, -0.104F, 18F, -0.633F, -7.19F, -0.808F);
        cube(plates, "band_3", 37, 24, -7.5F, -7F, -1F, 15, 14, 2, 0F, -0.5F, 29F, 0F, 0F, -2.785F);
        cube(plates, "plate_top_3", 35, 69, -4.5F, -1F, -3F, 9, 2, 6, 0.156F, -7F, 25F, 3.85F, 3.685F, 1.711F);
        cube(plates, "plate_l_3", 362, 24, -1F, -4F, -3F, 2, 8, 6, -7F, 0.898F, 25F, -3.106F, 5.813F, 4.023F);
        cube(plates, "plate_r_3", 379, 24, -1F, -4F, -3F, 2, 8, 6, 7F, 0.251F, 25F, -7.035F, -5.761F, -3.609F);

        cube(flames, "arm_flame_1", 437, 42, -1F, -1F, -4.5F, 2, 2, 9, -8.106F, 2.592F, 14F, 1.778F, -27.071F, 4.598F);
        cube(flames, "arm_flame_2", 460, 42, -1F, -1F, -4.5F, 2, 2, 9, 7.049F, -1.981F, 18.2F, -7.879F, 21.876F, -13.13F);
        cube(flames, "arm_flame_3", 483, 42, -1F, -1F, -4.5F, 2, 2, 9, -7.366F, 5.461F, 22.4F, 4.033F, -29.234F, -1.299F);
        cube(flames, "arm_flame_4", 0, 57, -1F, -1F, -4.5F, 2, 2, 9, 8.663F, -6.43F, 26.6F, 8.741F, 20.324F, -2.628F);
        cube(flames, "arm_flame_5", 23, 57, -1F, -1F, -4.5F, 2, 2, 9, -7.211F, 3.938F, 30.8F, 9.867F, -17.212F, -10.068F);
        cube(flames, "arm_flame_6", 46, 57, -1F, -1F, -4.5F, 2, 2, 9, 8.36F, -2.099F, 35F, 5.452F, 20.475F, -12.003F);

        // --- Masse de flammes : un coeur qui s'affine de 13 a 4 px, flanque de langues de feu qui s'ecartent ---
        coreCube(trail0, "trail_core_0", 417, 0, -4.5F, -4F, -5F, 9, 8, 10, 0.4F, -0.5F, 34F, 1.5F, -2F, 3F, 2F);
        cube(trail0, "trail_flame_l_0", 237, 24, -1F, -3F, -4.5F, 2, 6, 9, -8.2F, -0.8F, 36F, -3F, -24F, 10F);
        cube(trail0, "trail_flame_r_0", 260, 24, -1F, -3F, -4.5F, 2, 6, 9, 8.2F, -0.6F, 36F, -5F, 22F, 12F);
        cube(trail0, "trail_flame_u_0", 271, 57, -2F, -1F, -4F, 4, 2, 8, 1.4F, -7.4F, 36.5F, -18F, -6F, -2F);

        coreCube(trail1, "trail_core_1", 0, 24, -4F, -3.5F, -5F, 8, 7, 10, -0.8F, 0.6F, 42.5F, -2F, 2.5F, -3F, 2.2F);
        cube(trail1, "trail_flame_l_1", 283, 24, -1F, -3F, -4.5F, 2, 6, 9, -7.6F, 0.8F, 44F, -2F, -20F, -14F);
        cube(trail1, "trail_flame_r_1", 306, 24, -1F, -3F, -4.5F, 2, 6, 9, 7.4F, -0.5F, 44.5F, -3F, 26F, 16F);

        coreCube(trail2, "trail_core_2", 107, 24, -3.5F, -3F, -5F, 7, 6, 10, 0.6F, 0.9F, 50.5F, 2F, -3F, 3F, 1.8F);
        cube(trail2, "trail_flame_l_2", 458, 24, -1F, -2.5F, -4.5F, 2, 5, 9, -6.6F, -0.6F, 52F, -4F, -18F, -12F);
        cube(trail2, "trail_flame_r_2", 481, 24, -1F, -2.5F, -4.5F, 2, 5, 9, 6.8F, 0.8F, 52.5F, -6F, 16F, 9F);
        cube(trail2, "trail_flame_u_2", 296, 57, -2F, -1F, -4F, 4, 2, 8, -1F, -6F, 52F, -15F, 5F, 4F);

        coreCube(trail3, "trail_core_3", 329, 24, -3F, -2.5F, -5F, 6, 5, 10, -0.5F, -0.6F, 57.5F, -3F, 3F, -2F, 1.2F);
        cube(trail3, "trail_flame_l_3", 0, 42, -1F, -2.5F, -4.5F, 2, 5, 9, -5.7F, 1F, 59F, -2F, -22F, -9F);
        cube(trail3, "trail_flame_r_3", 23, 42, -1F, -2.5F, -4.5F, 2, 5, 9, 5.5F, -0.4F, 59.5F, -4F, 20F, 11F);

        coreCube(trail4, "trail_core_4", 46, 42, -2.5F, -2F, -5F, 5, 4, 10, 0.7F, 0.5F, 63.5F, 2F, -2F, 4F, 0.6F);
        cube(trail4, "trail_flame_l_4", 111, 42, -1F, -2F, -4.5F, 2, 4, 9, -4.7F, 0F, 65F, -3F, -26F, -8F);
        cube(trail4, "trail_flame_r_4", 134, 42, -1F, -2F, -4.5F, 2, 4, 9, 4.9F, 0.7F, 65.5F, -5F, 24F, 10F);
        cube(trail4, "trail_flame_u_4", 321, 57, -1.5F, -1F, -4F, 3, 2, 8, 0.4F, -4.6F, 65.5F, -14F, -4F, 3F);

        cube(trail5, "trail_core_5", 157, 42, -2F, -1.5F, -5F, 4, 3, 10, -0.3F, 0.2F, 68.5F, 1F, 2F, -3F);
        cube(trail5, "trail_flame_l_5", 186, 42, -1F, -2F, -4.5F, 2, 4, 9, -3.4F, 0.4F, 70F, -2F, -30F, -6F);
        cube(trail5, "trail_flame_r_5", 209, 42, -1F, -2F, -4.5F, 2, 4, 9, 3.5F, -0.3F, 70.5F, -3F, 28F, 8F);

        return LayerDefinition.create(mesh, 512, 78);
    }

    private static PartDefinition group(PartDefinition parent, String name, float x, float y, float z) {
        return parent.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(x, y, z));
    }

    /** Cube centre sur (ox,oy,oz) dans son groupe (la boite est decrite autour de son propre centre), rotation en degres. */
    private static void cube(PartDefinition group, String name, int u, int v,
                             float x, float y, float z, float dx, float dy, float dz,
                             float ox, float oy, float oz, float rotX, float rotY, float rotZ) {
        coreCube(group, name, u, v, x, y, z, dx, dy, dz, ox, oy, oz, rotX, rotY, rotZ, 0F);
    }

    /** Pareil, mais grossi de {@code grow} pixels sur chaque face (la zone de texture reste celle de la boite d'origine). */
    private static void coreCube(PartDefinition group, String name, int u, int v,
                                 float x, float y, float z, float dx, float dy, float dz,
                                 float ox, float oy, float oz, float rotX, float rotY, float rotZ, float grow) {
        group.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, dx, dy, dz, new CubeDeformation(grow)),
                PartPose.offsetAndRotation(ox, oy, oz,
                        (float) Math.toRadians(rotX), (float) Math.toRadians(rotY), (float) Math.toRadians(rotZ)));
    }
}
