package nolan.bloxpiece.client.entity;

import java.util.ArrayList;
import java.util.List;

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
 * Modele 3D plat du sceau du Bouddha (entite BuddhaSeal) : le cercle dore qui marque au sol la zone d'impact de la Paume du
 * Bouddha. Remplace l'ancien cercle de particules.
 *
 * Trois anneaux concentriques qui tournent chacun a leur vitesse (et en sens inverse l'un de l'autre) :
 * <ul>
 *   <li>exterieur : 32 plaques + un liseré de 32 plaques, 16 runes en losange, 8 rayons vers le centre ;</li>
 *   <li>interieur : deux anneaux de 24 plaques ;</li>
 *   <li>centre : un lotus a 8 grands petales et 8 petits, autour d'un joyau.</li>
 * </ul>
 * Apparition : les anneaux se "dessinent" dans le sens des aiguilles d'une montre (state.reveal) et le lotus eclot.
 * Disparition (apres l'impact) : les plaques se detachent une a une, dans un ordre pseudo-aleatoire (state.dissolve), pendant que
 * le renderer elargit le sceau (onde de choc).
 *
 * Repere : rayon exterieur 24 px, a plat dans le plan XZ, centre sur le pivot (24 px = position de l'entite apres le renderer).
 * Texture 128x43, une zone de depliage par cube. Cubes places par un petit script (non fourni) : ce fichier est la source de verite.
 */
public class BuddhaSealModel extends EntityModel<BuddhaSealRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("buddha_seal"), "main");

    private final ModelPart outer;
    private final ModelPart inner;
    private final ModelPart lotus;

    /** Une plaque animee : sa position dans le trace (0..1, sens des aiguilles d'une montre) et son seuil de disparition (0..1). */
    private record Piece(ModelPart part, float along, float breakAt) {
    }

    private final List<Piece> pieces = new ArrayList<>();

    public BuddhaSealModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        ModelPart seal = root.getChild("seal");
        this.outer = seal.getChild("outer");
        this.inner = seal.getChild("inner");
        this.lotus = seal.getChild("lotus");
        collect(this.outer, "outer_", 32, 0.0F);
        collect(this.outer, "outer_thin_", 32, 0.05F);
        collect(this.outer, "rune_", 16, 0.1F);
        collect(this.outer, "spoke_", 8, 0.15F);
        collect(this.inner, "inner_", 24, 0.2F);
        collect(this.inner, "inner_thin_", 24, 0.25F);
        collect(this.lotus, "petal_", 8, 0.35F);
        collect(this.lotus, "petal_small_", 8, 0.4F);
        collect(this.lotus, "core_ring_", 8, 0.45F);
    }

    private void collect(ModelPart group, String prefix, int count, float delay) {
        for (int i = 0; i < count; i++) {
            float along = delay + (1.0F - 0.45F) * i / count; // chaque anneau commence un peu apres le precedent
            float breakAt = fract((i * 0.618034F + prefix.length() * 0.137F) * 1.0F); // ordre de disparition pseudo-aleatoire
            this.pieces.add(new Piece(group.getChild(prefix + i), along, breakAt));
        }
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    @Override
    public void setupAnim(BuddhaSealRenderState state) {
        super.setupAnim(state); // pose d'origine (rotations, echelles et visibilite remises a zero)
        float age = state.ageInTicks;

        // Les anneaux tournent (sens inverses), de plus en plus vite pendant l'onde de choc
        float spin = 1.0F + state.dissolve * 3.0F;
        this.outer.yRot += age * 0.020F * spin;
        this.inner.yRot -= age * 0.035F * spin;
        this.lotus.yRot += age * 0.012F * spin;

        // Le lotus eclot avec le trace, puis respire
        float bloom = Math.min(1.0F, state.reveal * 1.4F) * (1.0F + 0.05F * (float) Math.sin(age * 0.5F));
        this.lotus.xScale = bloom;
        this.lotus.zScale = bloom;

        // Trace progressif a l'apparition, plaques qui se detachent a la disparition
        for (Piece piece : this.pieces) {
            piece.part().visible = state.reveal >= piece.along() && state.dissolve <= piece.breakAt();
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition seal = group(root, "seal", 0F, 24F, 0F);
        PartDefinition outer = group(seal, "outer", 0F, 0F, 0F);
        cube(outer, "outer_0", 0, 0, 5, 1, 2, 24F, 0F, 0F, -90F);
        cube(outer, "outer_1", 14, 0, 5, 1, 2, 23.539F, 0F, 4.682F, -101.25F);
        cube(outer, "outer_2", 28, 0, 5, 1, 2, 22.173F, 0F, 9.184F, -112.5F);
        cube(outer, "outer_3", 42, 0, 5, 1, 2, 19.955F, 0F, 13.334F, -123.75F);
        cube(outer, "outer_4", 56, 0, 5, 1, 2, 16.971F, 0F, 16.971F, -135F);
        cube(outer, "outer_5", 70, 0, 5, 1, 2, 13.334F, 0F, 19.955F, -146.25F);
        cube(outer, "outer_6", 84, 0, 5, 1, 2, 9.184F, 0F, 22.173F, -157.5F);
        cube(outer, "outer_7", 98, 0, 5, 1, 2, 4.682F, 0F, 23.539F, -168.75F);
        cube(outer, "outer_8", 112, 0, 5, 1, 2, 0F, 0F, 24F, -180F);
        cube(outer, "outer_9", 0, 3, 5, 1, 2, -4.682F, 0F, 23.539F, -191.25F);
        cube(outer, "outer_10", 14, 3, 5, 1, 2, -9.184F, 0F, 22.173F, -202.5F);
        cube(outer, "outer_11", 28, 3, 5, 1, 2, -13.334F, 0F, 19.955F, -213.75F);
        cube(outer, "outer_12", 42, 3, 5, 1, 2, -16.971F, 0F, 16.971F, -225F);
        cube(outer, "outer_13", 56, 3, 5, 1, 2, -19.955F, 0F, 13.334F, -236.25F);
        cube(outer, "outer_14", 70, 3, 5, 1, 2, -22.173F, 0F, 9.184F, -247.5F);
        cube(outer, "outer_15", 84, 3, 5, 1, 2, -23.539F, 0F, 4.682F, -258.75F);
        cube(outer, "outer_16", 98, 3, 5, 1, 2, -24F, 0F, 0F, -270F);
        cube(outer, "outer_17", 112, 3, 5, 1, 2, -23.539F, 0F, -4.682F, -281.25F);
        cube(outer, "outer_18", 0, 6, 5, 1, 2, -22.173F, 0F, -9.184F, -292.5F);
        cube(outer, "outer_19", 14, 6, 5, 1, 2, -19.955F, 0F, -13.334F, -303.75F);
        cube(outer, "outer_20", 28, 6, 5, 1, 2, -16.971F, 0F, -16.971F, -315F);
        cube(outer, "outer_21", 42, 6, 5, 1, 2, -13.334F, 0F, -19.955F, -326.25F);
        cube(outer, "outer_22", 56, 6, 5, 1, 2, -9.184F, 0F, -22.173F, -337.5F);
        cube(outer, "outer_23", 70, 6, 5, 1, 2, -4.682F, 0F, -23.539F, -348.75F);
        cube(outer, "outer_24", 84, 6, 5, 1, 2, 0F, 0F, -24F, -360F);
        cube(outer, "outer_25", 98, 6, 5, 1, 2, 4.682F, 0F, -23.539F, -371.25F);
        cube(outer, "outer_26", 112, 6, 5, 1, 2, 9.184F, 0F, -22.173F, -382.5F);
        cube(outer, "outer_27", 0, 9, 5, 1, 2, 13.334F, 0F, -19.955F, -393.75F);
        cube(outer, "outer_28", 14, 9, 5, 1, 2, 16.971F, 0F, -16.971F, -405F);
        cube(outer, "outer_29", 28, 9, 5, 1, 2, 19.955F, 0F, -13.334F, -416.25F);
        cube(outer, "outer_30", 42, 9, 5, 1, 2, 22.173F, 0F, -9.184F, -427.5F);
        cube(outer, "outer_31", 56, 9, 5, 1, 2, 23.539F, 0F, -4.682F, -438.75F);
        cube(outer, "outer_thin_0", 70, 9, 5, 1, 1, 20.899F, 0F, 2.058F, -95.625F);
        cube(outer, "outer_thin_1", 82, 9, 5, 1, 1, 20.096F, 0F, 6.096F, -106.875F);
        cube(outer, "outer_thin_2", 94, 9, 5, 1, 1, 18.52F, 0F, 9.899F, -118.125F);
        cube(outer, "outer_thin_3", 106, 9, 5, 1, 1, 16.233F, 0F, 13.322F, -129.375F);
        cube(outer, "outer_thin_4", 0, 12, 5, 1, 1, 13.322F, 0F, 16.233F, -140.625F);
        cube(outer, "outer_thin_5", 12, 12, 5, 1, 1, 9.899F, 0F, 18.52F, -151.875F);
        cube(outer, "outer_thin_6", 24, 12, 5, 1, 1, 6.096F, 0F, 20.096F, -163.125F);
        cube(outer, "outer_thin_7", 36, 12, 5, 1, 1, 2.058F, 0F, 20.899F, -174.375F);
        cube(outer, "outer_thin_8", 48, 12, 5, 1, 1, -2.058F, 0F, 20.899F, -185.625F);
        cube(outer, "outer_thin_9", 60, 12, 5, 1, 1, -6.096F, 0F, 20.096F, -196.875F);
        cube(outer, "outer_thin_10", 72, 12, 5, 1, 1, -9.899F, 0F, 18.52F, -208.125F);
        cube(outer, "outer_thin_11", 84, 12, 5, 1, 1, -13.322F, 0F, 16.233F, -219.375F);
        cube(outer, "outer_thin_12", 96, 12, 5, 1, 1, -16.233F, 0F, 13.322F, -230.625F);
        cube(outer, "outer_thin_13", 108, 12, 5, 1, 1, -18.52F, 0F, 9.899F, -241.875F);
        cube(outer, "outer_thin_14", 0, 14, 5, 1, 1, -20.096F, 0F, 6.096F, -253.125F);
        cube(outer, "outer_thin_15", 12, 14, 5, 1, 1, -20.899F, 0F, 2.058F, -264.375F);
        cube(outer, "outer_thin_16", 24, 14, 5, 1, 1, -20.899F, 0F, -2.058F, -275.625F);
        cube(outer, "outer_thin_17", 36, 14, 5, 1, 1, -20.096F, 0F, -6.096F, -286.875F);
        cube(outer, "outer_thin_18", 48, 14, 5, 1, 1, -18.52F, 0F, -9.899F, -298.125F);
        cube(outer, "outer_thin_19", 60, 14, 5, 1, 1, -16.233F, 0F, -13.322F, -309.375F);
        cube(outer, "outer_thin_20", 72, 14, 5, 1, 1, -13.322F, 0F, -16.233F, -320.625F);
        cube(outer, "outer_thin_21", 84, 14, 5, 1, 1, -9.899F, 0F, -18.52F, -331.875F);
        cube(outer, "outer_thin_22", 96, 14, 5, 1, 1, -6.096F, 0F, -20.096F, -343.125F);
        cube(outer, "outer_thin_23", 108, 14, 5, 1, 1, -2.058F, 0F, -20.899F, -354.375F);
        cube(outer, "outer_thin_24", 0, 16, 5, 1, 1, 2.058F, 0F, -20.899F, -365.625F);
        cube(outer, "outer_thin_25", 12, 16, 5, 1, 1, 6.096F, 0F, -20.096F, -376.875F);
        cube(outer, "outer_thin_26", 24, 16, 5, 1, 1, 9.899F, 0F, -18.52F, -388.125F);
        cube(outer, "outer_thin_27", 36, 16, 5, 1, 1, 13.322F, 0F, -16.233F, -399.375F);
        cube(outer, "outer_thin_28", 48, 16, 5, 1, 1, 16.233F, 0F, -13.322F, -410.625F);
        cube(outer, "outer_thin_29", 60, 16, 5, 1, 1, 18.52F, 0F, -9.899F, -421.875F);
        cube(outer, "outer_thin_30", 72, 16, 5, 1, 1, 20.096F, 0F, -6.096F, -433.125F);
        cube(outer, "outer_thin_31", 84, 16, 5, 1, 1, 20.899F, 0F, -2.058F, -444.375F);
        cube(outer, "rune_0", 96, 16, 2, 1, 2, 22.491F, 0F, 2.215F, 39.375F);
        cube(outer, "rune_1", 104, 16, 2, 1, 2, 19.931F, 0F, 10.654F, 16.875F);
        cube(outer, "rune_2", 112, 16, 2, 1, 2, 14.337F, 0F, 17.47F, -5.625F);
        cube(outer, "rune_3", 120, 16, 2, 1, 2, 6.56F, 0F, 21.627F, -28.125F);
        cube(outer, "rune_4", 0, 19, 2, 1, 2, -2.215F, 0F, 22.491F, -50.625F);
        cube(outer, "rune_5", 8, 19, 2, 1, 2, -10.654F, 0F, 19.931F, -73.125F);
        cube(outer, "rune_6", 16, 19, 2, 1, 2, -17.47F, 0F, 14.337F, -95.625F);
        cube(outer, "rune_7", 24, 19, 2, 1, 2, -21.627F, 0F, 6.56F, -118.125F);
        cube(outer, "rune_8", 32, 19, 2, 1, 2, -22.491F, 0F, -2.215F, -140.625F);
        cube(outer, "rune_9", 40, 19, 2, 1, 2, -19.931F, 0F, -10.654F, -163.125F);
        cube(outer, "rune_10", 48, 19, 2, 1, 2, -14.337F, 0F, -17.47F, -185.625F);
        cube(outer, "rune_11", 56, 19, 2, 1, 2, -6.56F, 0F, -21.627F, -208.125F);
        cube(outer, "rune_12", 64, 19, 2, 1, 2, 2.215F, 0F, -22.491F, -230.625F);
        cube(outer, "rune_13", 72, 19, 2, 1, 2, 10.654F, 0F, -19.931F, -253.125F);
        cube(outer, "rune_14", 80, 19, 2, 1, 2, 17.47F, 0F, -14.337F, -275.625F);
        cube(outer, "rune_15", 88, 19, 2, 1, 2, 21.627F, 0F, -6.56F, -298.125F);
        cube(outer, "spoke_0", 96, 19, 6, 1, 1, 17.5F, 0F, 0F, 0F);
        cube(outer, "spoke_1", 110, 19, 6, 1, 1, 12.374F, 0F, 12.374F, -45F);
        cube(outer, "spoke_2", 0, 22, 6, 1, 1, 0F, 0F, 17.5F, -90F);
        cube(outer, "spoke_3", 14, 22, 6, 1, 1, -12.374F, 0F, 12.374F, -135F);
        cube(outer, "spoke_4", 28, 22, 6, 1, 1, -17.5F, 0F, 0F, -180F);
        cube(outer, "spoke_5", 42, 22, 6, 1, 1, -12.374F, 0F, -12.374F, -225F);
        cube(outer, "spoke_6", 56, 22, 6, 1, 1, 0F, 0F, -17.5F, -270F);
        cube(outer, "spoke_7", 70, 22, 6, 1, 1, 12.374F, 0F, -12.374F, -315F);
        PartDefinition inner = group(seal, "inner", 0F, 0F, 0F);
        cube(inner, "inner_0", 84, 22, 4, 1, 1, 14F, 0F, 0F, -90F);
        cube(inner, "inner_1", 94, 22, 4, 1, 1, 13.523F, 0F, 3.623F, -105F);
        cube(inner, "inner_2", 104, 22, 4, 1, 1, 12.124F, 0F, 7F, -120F);
        cube(inner, "inner_3", 114, 22, 4, 1, 1, 9.899F, 0F, 9.899F, -135F);
        cube(inner, "inner_4", 0, 24, 4, 1, 1, 7F, 0F, 12.124F, -150F);
        cube(inner, "inner_5", 10, 24, 4, 1, 1, 3.623F, 0F, 13.523F, -165F);
        cube(inner, "inner_6", 20, 24, 4, 1, 1, 0F, 0F, 14F, -180F);
        cube(inner, "inner_7", 30, 24, 4, 1, 1, -3.623F, 0F, 13.523F, -195F);
        cube(inner, "inner_8", 40, 24, 4, 1, 1, -7F, 0F, 12.124F, -210F);
        cube(inner, "inner_9", 50, 24, 4, 1, 1, -9.899F, 0F, 9.899F, -225F);
        cube(inner, "inner_10", 60, 24, 4, 1, 1, -12.124F, 0F, 7F, -240F);
        cube(inner, "inner_11", 70, 24, 4, 1, 1, -13.523F, 0F, 3.623F, -255F);
        cube(inner, "inner_12", 80, 24, 4, 1, 1, -14F, 0F, 0F, -270F);
        cube(inner, "inner_13", 90, 24, 4, 1, 1, -13.523F, 0F, -3.623F, -285F);
        cube(inner, "inner_14", 100, 24, 4, 1, 1, -12.124F, 0F, -7F, -300F);
        cube(inner, "inner_15", 110, 24, 4, 1, 1, -9.899F, 0F, -9.899F, -315F);
        cube(inner, "inner_16", 0, 26, 4, 1, 1, -7F, 0F, -12.124F, -330F);
        cube(inner, "inner_17", 10, 26, 4, 1, 1, -3.623F, 0F, -13.523F, -345F);
        cube(inner, "inner_18", 20, 26, 4, 1, 1, 0F, 0F, -14F, -360F);
        cube(inner, "inner_19", 30, 26, 4, 1, 1, 3.623F, 0F, -13.523F, -375F);
        cube(inner, "inner_20", 40, 26, 4, 1, 1, 7F, 0F, -12.124F, -390F);
        cube(inner, "inner_21", 50, 26, 4, 1, 1, 9.899F, 0F, -9.899F, -405F);
        cube(inner, "inner_22", 60, 26, 4, 1, 1, 12.124F, 0F, -7F, -420F);
        cube(inner, "inner_23", 70, 26, 4, 1, 1, 13.523F, 0F, -3.623F, -435F);
        cube(inner, "inner_thin_0", 80, 26, 3, 1, 1, 11.897F, 0F, 1.566F, -97.5F);
        cube(inner, "inner_thin_1", 88, 26, 3, 1, 1, 11.087F, 0F, 4.592F, -112.5F);
        cube(inner, "inner_thin_2", 96, 26, 3, 1, 1, 9.52F, 0F, 7.305F, -127.5F);
        cube(inner, "inner_thin_3", 104, 26, 3, 1, 1, 7.305F, 0F, 9.52F, -142.5F);
        cube(inner, "inner_thin_4", 112, 26, 3, 1, 1, 4.592F, 0F, 11.087F, -157.5F);
        cube(inner, "inner_thin_5", 120, 26, 3, 1, 1, 1.566F, 0F, 11.897F, -172.5F);
        cube(inner, "inner_thin_6", 0, 28, 3, 1, 1, -1.566F, 0F, 11.897F, -187.5F);
        cube(inner, "inner_thin_7", 8, 28, 3, 1, 1, -4.592F, 0F, 11.087F, -202.5F);
        cube(inner, "inner_thin_8", 16, 28, 3, 1, 1, -7.305F, 0F, 9.52F, -217.5F);
        cube(inner, "inner_thin_9", 24, 28, 3, 1, 1, -9.52F, 0F, 7.305F, -232.5F);
        cube(inner, "inner_thin_10", 32, 28, 3, 1, 1, -11.087F, 0F, 4.592F, -247.5F);
        cube(inner, "inner_thin_11", 40, 28, 3, 1, 1, -11.897F, 0F, 1.566F, -262.5F);
        cube(inner, "inner_thin_12", 48, 28, 3, 1, 1, -11.897F, 0F, -1.566F, -277.5F);
        cube(inner, "inner_thin_13", 56, 28, 3, 1, 1, -11.087F, 0F, -4.592F, -292.5F);
        cube(inner, "inner_thin_14", 64, 28, 3, 1, 1, -9.52F, 0F, -7.305F, -307.5F);
        cube(inner, "inner_thin_15", 72, 28, 3, 1, 1, -7.305F, 0F, -9.52F, -322.5F);
        cube(inner, "inner_thin_16", 80, 28, 3, 1, 1, -4.592F, 0F, -11.087F, -337.5F);
        cube(inner, "inner_thin_17", 88, 28, 3, 1, 1, -1.566F, 0F, -11.897F, -352.5F);
        cube(inner, "inner_thin_18", 96, 28, 3, 1, 1, 1.566F, 0F, -11.897F, -367.5F);
        cube(inner, "inner_thin_19", 104, 28, 3, 1, 1, 4.592F, 0F, -11.087F, -382.5F);
        cube(inner, "inner_thin_20", 112, 28, 3, 1, 1, 7.305F, 0F, -9.52F, -397.5F);
        cube(inner, "inner_thin_21", 120, 28, 3, 1, 1, 9.52F, 0F, -7.305F, -412.5F);
        cube(inner, "inner_thin_22", 0, 30, 3, 1, 1, 11.087F, 0F, -4.592F, -427.5F);
        cube(inner, "inner_thin_23", 8, 30, 3, 1, 1, 11.897F, 0F, -1.566F, -442.5F);
        PartDefinition lotus = group(seal, "lotus", 0F, 0F, 0F);
        cube(lotus, "petal_0", 16, 30, 7, 1, 3, 7.5F, 0F, 0F, 0F);
        cube(lotus, "petal_small_0", 36, 30, 4, 1, 2, 4.619F, 0F, 1.913F, -22.5F);
        cube(lotus, "petal_1", 48, 30, 7, 1, 3, 5.303F, 0F, 5.303F, -45F);
        cube(lotus, "petal_small_1", 68, 30, 4, 1, 2, 1.913F, 0F, 4.619F, -67.5F);
        cube(lotus, "petal_2", 80, 30, 7, 1, 3, 0F, 0F, 7.5F, -90F);
        cube(lotus, "petal_small_2", 100, 30, 4, 1, 2, -1.913F, 0F, 4.619F, -112.5F);
        cube(lotus, "petal_3", 0, 34, 7, 1, 3, -5.303F, 0F, 5.303F, -135F);
        cube(lotus, "petal_small_3", 20, 34, 4, 1, 2, -4.619F, 0F, 1.913F, -157.5F);
        cube(lotus, "petal_4", 32, 34, 7, 1, 3, -7.5F, 0F, 0F, -180F);
        cube(lotus, "petal_small_4", 52, 34, 4, 1, 2, -4.619F, 0F, -1.913F, -202.5F);
        cube(lotus, "petal_5", 64, 34, 7, 1, 3, -5.303F, 0F, -5.303F, -225F);
        cube(lotus, "petal_small_5", 84, 34, 4, 1, 2, -1.913F, 0F, -4.619F, -247.5F);
        cube(lotus, "petal_6", 96, 34, 7, 1, 3, 0F, 0F, -7.5F, -270F);
        cube(lotus, "petal_small_6", 116, 34, 4, 1, 2, 1.913F, 0F, -4.619F, -292.5F);
        cube(lotus, "petal_7", 0, 38, 7, 1, 3, 5.303F, 0F, -5.303F, -315F);
        cube(lotus, "petal_small_7", 20, 38, 4, 1, 2, 4.619F, 0F, -1.913F, -337.5F);
        cube(lotus, "core", 32, 38, 4, 1, 4, 0F, 0F, 0F, 45F);
        cube(lotus, "core_ring_0", 48, 38, 2, 1, 1, 2.8F, 0F, 0F, -90F);
        cube(lotus, "core_ring_1", 54, 38, 2, 1, 1, 1.98F, 0F, 1.98F, -135F);
        cube(lotus, "core_ring_2", 60, 38, 2, 1, 1, 0F, 0F, 2.8F, -180F);
        cube(lotus, "core_ring_3", 66, 38, 2, 1, 1, -1.98F, 0F, 1.98F, -225F);
        cube(lotus, "core_ring_4", 72, 38, 2, 1, 1, -2.8F, 0F, 0F, -270F);
        cube(lotus, "core_ring_5", 78, 38, 2, 1, 1, -1.98F, 0F, -1.98F, -315F);
        cube(lotus, "core_ring_6", 84, 38, 2, 1, 1, 0F, 0F, -2.8F, -360F);
        cube(lotus, "core_ring_7", 90, 38, 2, 1, 1, 1.98F, 0F, -1.98F, -405F);

        return LayerDefinition.create(mesh, 128, 43);
    }

    private static PartDefinition group(PartDefinition parent, String name, float x, float y, float z) {
        return parent.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(x, y, z));
    }

    /** Cube de dimensions (dx,dy,dz) CENTRE sur (ox,oy,oz), tourne autour de l'axe vertical de {@code rotY} degres. */
    private static void cube(PartDefinition group, String name, int u, int v, float dx, float dy, float dz,
                             float ox, float oy, float oz, float rotY) {
        group.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(-dx / 2.0F, -dy / 2.0F, -dz / 2.0F, dx, dy, dz),
                PartPose.offsetAndRotation(ox, oy, oz, 0.0F, (float) Math.toRadians(rotY), 0.0F));
    }
}
