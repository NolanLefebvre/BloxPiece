package nolan.bloxpiece.client.lunarian;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;

/**
 * Modèles 3D de la race Lunarian (textures : src/blockbench/lunarian/MakeLunarianTextures.java).
 * <ul>
 *   <li>{@link #WING} : les PIÈCES des ailes noires VOXEL (références : ailes voxel fournies par le joueur), assemblées à chaque
 *       image par LunarianLayer :
 *       <ul>
 *         <li>« bone0 » à « bone2 » : maillons de l'os du bord d'attaque (épais, moyen, fin) ; une chaîne de maillons forme l'arche ;</li>
 *         <li>« fa » à « fe2 » : les PLUMES VOXEL, à leur longueur réelle (A 4 px, B 6 px, C 9 px, D 11 et 14 px, E 14 et 17 px) : une vraie bande de 1 px d'épaisseur, dégradé noir violacé, reflet sur
 *             un bord, terminée par un BOUT EN ESCALIER (deux marches qui se rétrécissent) ; elle pend vers +y depuis son
 *             attache.</li>
 *       </ul></li>
 *   <li>{@link #FLAME} : volume de feu (cube de 16 px centré), assemblé par LunarianFire en langues de feu 3D, en trois couches
 *       (coeur, corps, bord troué) ;</li>
 *   <li>{@link #FEATHER} : la plume de PHÉNIX des Plumes incandescentes (rachis, barbes en relief, bord dentelé, pointe en braise).</li>
 * </ul>
 */
public final class LunarianModels {

    public static final ModelLayerLocation WING = new ModelLayerLocation(BloxPiece.id("lunarian_wing"), "main");
    public static final ModelLayerLocation FLAME = new ModelLayerLocation(BloxPiece.id("lunarian_flame"), "main");
    public static final ModelLayerLocation FEATHER = new ModelLayerLocation(BloxPiece.id("lunarian_feather"), "main");

    public static final Identifier WING_TEXTURE = BloxPiece.id("textures/entity/lunarian_wing.png");
    public static final Identifier FEATHER_TEXTURE = BloxPiece.id("textures/entity/lunarian_feather.png");
    /** Volumes de feu : [couche : 0 coeur, 1 corps, 2 bord troué][variante]. */
    public static final Identifier[][] FIRE_TEXTURES = new Identifier[3][2];

    static {
        String[] layers = {"core", "body", "edge"};
        for (int l = 0; l < 3; l++) {
            for (int v = 0; v < 2; v++) {
                FIRE_TEXTURES[l][v] = BloxPiece.id("textures/entity/lunarian_flame_" + layers[l] + "_" + v + ".png");
            }
        }
    }

    /** Longueur d'un maillon d'os (px) : un pas de l'arche (40 px / 16) plus le recouvrement. */
    public static final float BONE = 3.1F;
    /** Plumes (noms des pièces) et leur longueur RÉELLE (px) : A, B, C, D (courte), D2 (longue), E (courte), E2 (longue). */
    public static final String[] TIERS = {"fa", "fb", "fc", "fd", "fd2", "fe", "fe2"};
    public static final float[] TIER_LENGTH = {4.0F, 6.0F, 9.0F, 11.0F, 14.0F, 14.0F, 17.0F};

    // emplacements dans l'atlas (voir MakeLunarianTextures.TIERS) : {u, v, largeur, longueur}
    private static final int[][] ATLAS = {{0, 0, 3, 4}, {10, 0, 3, 6}, {20, 0, 4, 9}, {32, 0, 4, 11}, {44, 0, 4, 14}, {56, 0, 4, 14}, {68, 0, 4, 17}};

    private LunarianModels() {
    }

    public static LayerDefinition wing() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // maillons de l'os du bord d'attaque, à leur taille réelle (épais à l'attache, fins au bout) : aucun étirement
        float[][] bones = {{2.6F, 1.8F}, {2.2F, 1.5F}, {1.6F, 1.2F}};
        int[] boneU = {0, 12, 24};
        for (int i = 0; i < bones.length; i++) {
            root.addOrReplaceChild("bone" + i, CubeListBuilder.create().texOffs(boneU[i], 40)
                    .addBox(0.0F, -bones[i][0] / 2.0F, -bones[i][1] / 2.0F, BONE, bones[i][0], bones[i][1]), PartPose.ZERO);
        }
        for (int i = 0; i < TIERS.length; i++) {
            int[] t = ATLAS[i];
            int w = t[2], len = t[3];
            // bande principale, puis le bout en escalier (deux marches, côté gauche conservé : pointe pixelisée) ; chaque face tombe
            // sur sa zone de l'atlas (MakeLunarianTextures.TIERS)
            root.addOrReplaceChild(TIERS[i], CubeListBuilder.create()
                    .texOffs(t[0], t[1]).addBox(-w / 2.0F, 0.0F, -0.5F, w, len - 2, 1.0F)
                    .texOffs(t[0], t[1] + len - 3).addBox(-w / 2.0F, len - 2, -0.5F, w - 1, 1.0F, 1.0F)
                    .texOffs(t[0], t[1] + len - 2).addBox(-w / 2.0F, len - 1, -0.5F, Math.max(1, w - 2), 1.0F, 1.0F), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 128, 64);
    }

    /** Volume de feu : cube de 16 px CENTRÉ (mis à l'échelle et orienté pièce par pièce : LunarianFire). */
    public static LayerDefinition flame() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("cube", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** Demi-largeur de la plume de phénix (px) à chacune de ses 12 tranches, de la base vers la pointe (silhouette en feuille effilée). */
    public static final float[] FEATHER_PROFILE = {1.2F, 2.0F, 2.6F, 3.0F, 3.1F, 3.0F, 2.8F, 2.5F, 2.1F, 1.6F, 1.1F, 0.6F};
    /** Position (px, le long de z) de la première tranche et pas entre deux tranches. */
    public static final float FEATHER_START = -7.0F, FEATHER_STEP = 1.4F;

    /**
     * Plume de PHÉNIX (pointe vers +z, environ 22 px) : rachis incandescent sur toute la longueur (calamus à la base) ; douze
     * tranches de barbes en feuille effilée (plus large au premier tiers) ; dans chaque tranche, une barbe extérieure orange-rouge
     * fine et une barbe intérieure jaune plus épaisse, en RELIEF sur la première ; un bord dentelé (une tranche sur deux déborde
     * un peu) ; une pointe en braise. LunarianFire.phoenixFeather l'enveloppe de chaleur et la borde de langues de feu animées.
     */
    public static LayerDefinition feather() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition f = root.addOrReplaceChild("feather", CubeListBuilder.create()
                .texOffs(16, 0).addBox(-0.45F, -0.45F, -9.0F, 0.9F, 0.9F, 19.0F)        // rachis
                .texOffs(16, 0).addBox(-0.35F, -0.35F, -11.0F, 0.7F, 0.7F, 2.5F)        // calamus
                .texOffs(0, 16).addBox(-0.5F, -0.3F, 9.5F, 1.0F, 0.6F, 2.5F)            // pointe
                .texOffs(0, 16).addBox(-0.25F, -0.25F, 11.5F, 0.5F, 0.5F, 1.5F), PartPose.ZERO);
        for (int i = 0; i < FEATHER_PROFILE.length; i++) {
            float z = FEATHER_START + i * FEATHER_STEP, hw = FEATHER_PROFILE[i] + (i % 2 == 1 ? 0.35F : 0.0F); // bord dentelé
            f.addOrReplaceChild("vane_" + i, CubeListBuilder.create()
                    .texOffs(0, 8).addBox(-hw, -0.35F, z, hw * 2.0F, 0.7F, 1.6F)                      // barbe extérieure
                    .texOffs(0, 0).addBox(-hw * 0.55F, -0.6F, z + 0.1F, hw * 1.1F, 1.2F, 1.4F),       // barbe intérieure en relief
                    PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 32);
    }
}
