package nolan.bloxpiece.client.cyborg;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;

/**
 * Modèles 3D de l'explosion du Radical Beam (race Cyborg), identité technologique (textures pixel art :
 * src/blockbench/cyborg/MakeCyborgBlastTextures.java, blanches, teintées et lumineuses en jeu) :
 * <ul>
 *   <li>{@link #HEX} : anneau HEXAGONAL en plaques (6 plaques, rayon 6 px, plan XZ autour de l'axe Y), avec un filet de circuit
 *       et un rebord : ondes de choc, dôme de pression, anneaux qui glissent le long du rayon ;</li>
 *   <li>{@link #PLASMA} : cellule de plasma en voxels (un bloc 6x6x6 et une bosse 3x3x3, grille de circuit) : la boule de feu
 *       qui refroidit en fumée métallique.</li>
 * </ul>
 */
public final class CyborgFxModels {

    public static final ModelLayerLocation HEX = new ModelLayerLocation(BloxPiece.id("cyborg_hex"), "main");
    public static final ModelLayerLocation PLASMA = new ModelLayerLocation(BloxPiece.id("cyborg_plasma"), "main");
    public static final Identifier HEX_TEXTURE = BloxPiece.id("textures/entity/cyborg_hex.png");
    public static final Identifier PLASMA_TEXTURE = BloxPiece.id("textures/entity/cyborg_plasma.png");

    private CyborgFxModels() {
    }

    public static LayerDefinition hex() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hex = root.addOrReplaceChild("hex", CubeListBuilder.create(), PartPose.ZERO);
        for (int i = 0; i < 6; i++) {
            float a = (float) (i * Math.PI / 3);
            // une plaque par côté (côté de l'hexagone d'apothème 6 px : 6,93 px), son rebord tourné vers l'extérieur
            hex.addOrReplaceChild("plate_" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F)
                            .texOffs(0, 8).addBox(-3.5F, -0.5F, 1.0F, 7.0F, 1.0F, 1.0F),
                    PartPose.offsetAndRotation((float) Math.sin(a) * 6.0F, 0.0F, (float) Math.cos(a) * 6.0F, 0.0F, a, 0.0F));
        }
        return LayerDefinition.create(mesh, 32, 16);
    }

    public static LayerDefinition plasma() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition cell = root.addOrReplaceChild("cell", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F),
                PartPose.ZERO);
        cell.addOrReplaceChild("bump", CubeListBuilder.create().texOffs(0, 12).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F),
                PartPose.offsetAndRotation(2.5F, -2.5F, 1.5F, 0.5F, 0.3F, 0.4F));
        return LayerDefinition.create(mesh, 32, 32);
    }
}
