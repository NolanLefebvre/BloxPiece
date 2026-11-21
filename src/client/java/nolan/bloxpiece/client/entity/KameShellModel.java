package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import nolan.bloxpiece.BloxPiece;

/**
 * Carapace du Kame Kame no Mi (fruit de la Tortue), ajustée au torse du joueur (repère de la pièce « body » du joueur : x -4..4,
 * y 0..12 vers le bas, l'avant en -z). FICHIER GÉNÉRÉ par src/blockbench/kame/make_kame_shell.py (source Blockbench :
 * kame_shell.bbmodel) : ne pas éditer ici, modifier le générateur puis le relancer.
 * <ul>
 *   <li>{@link #dorsal} : grande carapace bombée dans le dos (gradins, écailles vertébrales et costales, bordure marginale),
 *       pivot au milieu du dos (pour la faire pousser) ;</li>
 *   <li>{@link #ventral} : plastron devant le torse (6 écailles claires) et ponts sur les flancs, pivot au milieu du ventre.</li>
 * </ul>
 * Utilisé sur le joueur (KameShellLayer) et seul dans le monde (KameShellRenderer, carapace posée ou tournoyante).
 */
public class KameShellModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("kame_shell"), "main");

    /** groupe (0 dos, 1 ventre), u, v, coin x y z (relatif au pivot du groupe), taille x y z. */
    private static final float[][] CUBES = {
            {0, 0, 0, -4.400F, -7.400F, 0.000F, 8.800F, 14.800F, 0.900F},
            {0, 40, 0, -5.600F, -6.200F, 0.000F, 11.200F, 12.400F, 0.900F},
            {0, 20, 0, -3.600F, -6.800F, 0.900F, 7.200F, 13.600F, 1.100F},
            {0, 66, 0, -5.000F, -5.400F, 0.900F, 10.000F, 10.800F, 1.100F},
            {0, 90, 0, -2.700F, -5.800F, 2.000F, 5.400F, 11.600F, 1.000F},
            {0, 34, 16, -4.200F, -4.300F, 2.000F, 8.400F, 8.600F, 1.000F},
            {0, 24, 16, -1.700F, -4.600F, 3.000F, 3.400F, 9.200F, 0.900F},
            {0, 54, 16, -3.100F, -3.200F, 3.000F, 6.200F, 6.400F, 0.900F},
            {0, 70, 16, -0.900F, -3.200F, 3.900F, 1.800F, 6.400F, 0.600F},
            {0, 76, 16, -1.900F, -2.200F, 3.900F, 3.800F, 4.400F, 0.600F},
            {0, 60, 28, -1.000F, -4.400F, 4.300F, 2.000F, 2.300F, 0.600F},
            {0, 114, 28, -1.500F, -3.900F, 4.300F, 3.000F, 1.300F, 0.600F},
            {0, 66, 28, -1.000F, -1.900F, 4.300F, 2.000F, 2.300F, 0.600F},
            {0, 0, 33, -1.500F, -1.400F, 4.300F, 3.000F, 1.300F, 0.600F},
            {0, 72, 28, -1.000F, 0.600F, 4.300F, 2.000F, 2.300F, 0.600F},
            {0, 8, 33, -1.500F, 1.100F, 4.300F, 3.000F, 1.300F, 0.600F},
            {0, 16, 33, -1.000F, 3.100F, 4.300F, 2.000F, 1.800F, 0.600F},
            {0, 74, 33, -1.500F, 3.600F, 4.300F, 3.000F, 0.800F, 0.600F},
            {0, 78, 28, -3.900F, -5.200F, 2.900F, 1.800F, 2.900F, 0.700F},
            {0, 84, 28, -3.900F, -2.000F, 2.900F, 1.800F, 2.900F, 0.700F},
            {0, 90, 28, -3.900F, 1.200F, 2.900F, 1.800F, 2.900F, 0.700F},
            {0, 22, 33, -3.900F, 4.400F, 2.900F, 1.800F, 1.200F, 0.700F},
            {0, 96, 28, 2.100F, -5.200F, 2.900F, 1.800F, 2.900F, 0.700F},
            {0, 102, 28, 2.100F, -2.000F, 2.900F, 1.800F, 2.900F, 0.700F},
            {0, 108, 28, 2.100F, 1.200F, 2.900F, 1.800F, 2.900F, 0.700F},
            {0, 28, 33, 2.100F, 4.400F, 2.900F, 1.800F, 1.200F, 0.700F},
            {0, 34, 33, -4.400F, 7.100F, 0.200F, 1.600F, 0.900F, 1.200F},
            {0, 42, 33, -2.450F, 7.100F, 0.200F, 1.600F, 0.900F, 1.200F},
            {0, 50, 33, -0.500F, 7.100F, 0.200F, 1.600F, 0.900F, 1.200F},
            {0, 58, 33, 1.450F, 7.100F, 0.200F, 1.600F, 0.900F, 1.200F},
            {0, 66, 33, 3.400F, 7.100F, 0.200F, 1.600F, 0.900F, 1.200F},
            {0, 86, 16, -6.100F, -4.800F, 0.100F, 0.600F, 2.600F, 1.200F},
            {0, 92, 16, -6.100F, -1.200F, 0.100F, 0.600F, 2.600F, 1.200F},
            {0, 98, 16, -6.100F, 2.400F, 0.100F, 0.600F, 2.600F, 1.200F},
            {0, 104, 16, 5.500F, -4.800F, 0.100F, 0.600F, 2.600F, 1.200F},
            {0, 110, 16, 5.500F, -1.200F, 0.100F, 0.600F, 2.600F, 1.200F},
            {0, 116, 16, 5.500F, 2.400F, 0.100F, 0.600F, 2.600F, 1.200F},
            {1, 104, 0, -4.300F, -5.700F, -1.000F, 8.600F, 11.600F, 1.000F},
            {1, 0, 28, -4.000F, -5.300F, -1.500F, 3.850F, 3.300F, 0.600F},
            {1, 10, 28, -4.000F, -1.700F, -1.500F, 3.850F, 3.500F, 0.600F},
            {1, 20, 28, -4.000F, 2.100F, -1.500F, 3.850F, 3.400F, 0.600F},
            {1, 30, 28, 0.150F, -5.300F, -1.500F, 3.850F, 3.300F, 0.600F},
            {1, 40, 28, 0.150F, -1.700F, -1.500F, 3.850F, 3.500F, 0.600F},
            {1, 50, 28, 0.150F, 2.100F, -1.500F, 3.850F, 3.400F, 0.600F},
            {1, 0, 16, -4.300F, -3.000F, -0.600F, 0.400F, 7.000F, 5.000F},
            {1, 12, 16, 3.900F, -3.000F, -0.600F, 0.400F, 7.000F, 5.000F},
    };

    public final ModelPart root;
    public final ModelPart dorsal;
    public final ModelPart ventral;

    public KameShellModel(ModelPart root) {
        this.root = root;
        this.dorsal = root.getChild("dorsal");
        this.ventral = root.getChild("ventral");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder dorsal = CubeListBuilder.create();
        CubeListBuilder ventral = CubeListBuilder.create();
        for (float[] c : CUBES) {
            (c[0] == 0 ? dorsal : ventral).texOffs((int) c[1], (int) c[2]).addBox(c[3], c[4], c[5], c[6], c[7], c[8]);
        }
        root.addOrReplaceChild("dorsal", dorsal, PartPose.offset(0.0F, 6.0F, 2.0F));
        root.addOrReplaceChild("ventral", ventral, PartPose.offset(0.0F, 6.0F, -2.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
