package nolan.bloxpiece.client.human;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;

/**
 * Modèles 3D des effets de la race Humaine (textures pixel art : src/blockbench/human/MakeHumanTextures.java) :
 * <ul>
 *   <li>{@link #ROCK} : deux éclats de pierre irréguliers (un bloc et son excroissance), projetés par les impacts ;</li>
 *   <li>{@link #DUST} : un nuage de poussière en volume (une boule et deux boules accolées), qui gonfle et se dissipe ;</li>
 *   <li>{@link #FIST} : le POING D'AIR géant du Poing du Conquérant (poing fermé, phalanges, doigts repliés, pouce,
 *       avant-bras), pointé vers -Z, dessiné translucide et lumineux en plusieurs couches.</li>
 *   <li>{@link #RING} : anneau de force en blocs (octogone) ; {@link #CHUNK} : morceau d'explosion en voxels ; {@link #FLAME} :
 *       langue de flamme pixel. Tous trois blancs, teintés et lumineux en jeu (Poing du Conquérant).</li>
 * </ul>
 */
public final class HumanModels {

    public static final ModelLayerLocation ROCK = new ModelLayerLocation(BloxPiece.id("human_rock"), "main");
    public static final ModelLayerLocation DUST = new ModelLayerLocation(BloxPiece.id("human_dust"), "main");
    public static final ModelLayerLocation FIST = new ModelLayerLocation(BloxPiece.id("human_fist"), "main");
    public static final ModelLayerLocation RING = new ModelLayerLocation(BloxPiece.id("human_ring"), "main");
    public static final ModelLayerLocation CHUNK = new ModelLayerLocation(BloxPiece.id("human_chunk"), "main");
    public static final ModelLayerLocation FLAME = new ModelLayerLocation(BloxPiece.id("human_flame"), "main");

    public static final Identifier ROCK_TEXTURE = BloxPiece.id("textures/entity/human_rock.png");
    public static final Identifier DUST_TEXTURE = BloxPiece.id("textures/entity/human_dust.png");
    public static final Identifier FIST_TEXTURE = BloxPiece.id("textures/entity/human_fist.png");
    public static final Identifier RING_TEXTURE = BloxPiece.id("textures/entity/human_ring.png");
    public static final Identifier CHUNK_TEXTURE = BloxPiece.id("textures/entity/human_chunk.png");
    public static final Identifier FLAME_TEXTURE = BloxPiece.id("textures/entity/human_flame.png");

    private HumanModels() {
    }

    /** Éclats : « a » (bloc 4x4x4 et excroissance) et « b » (éclat plat), centrés sur l'origine. */
    public static LayerDefinition rock() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition a = root.addOrReplaceChild("a", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.ZERO);
        a.addOrReplaceChild("chip", CubeListBuilder.create().texOffs(0, 8).addBox(0.5F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                PartPose.rotation(0.2F, 0.4F, 0.3F));
        PartDefinition b = root.addOrReplaceChild("b", CubeListBuilder.create().texOffs(16, 8).addBox(-1.5F, -1.0F, -1.0F, 3.0F, 2.0F, 2.0F),
                PartPose.ZERO);
        b.addOrReplaceChild("chip", CubeListBuilder.create().texOffs(0, 8).addBox(-1.0F, -2.5F, -0.5F, 2.0F, 3.0F, 2.0F),
                PartPose.rotation(-0.3F, 0.0F, 0.5F));
        return LayerDefinition.create(mesh, 32, 16);
    }

    /** Nuage : une boule 8x8x8 et deux boules 6x6x6 accolées (silhouette bosselée), centré sur l'origine. */
    public static LayerDefinition dust() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition puff = root.addOrReplaceChild("puff", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.ZERO);
        puff.addOrReplaceChild("lobe_a", CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offsetAndRotation(3.5F, -1.5F, 1.0F, 0.3F, 0.6F, 0.2F));
        puff.addOrReplaceChild("lobe_b", CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offsetAndRotation(-3.0F, 1.0F, -1.5F, -0.4F, 0.2F, 0.5F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    /**
     * Poing d'air du Poing du Conquérant, pointé vers -Z, centré sur le poing (environ 9 x 9 x 22 px avec l'avant-bras) :
     * dos de la main, quatre doigts repliés (phalanges du dessus et du devant, longueurs inégales : le majeur dépasse), jointures
     * saillantes, pouce replié devant les doigts, manchette rivetée au poignet, avant-bras qui s'affine vers l'arrière.
     */
    public static LayerDefinition fist() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition fist = root.addOrReplaceChild("fist", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -2.0F, 8.0F, 8.0F, 5.0F),
                PartPose.ZERO);
        float[] reach = {0.2F, 0.45F, 0.3F, 0.0F}; // index, majeur, annulaire, auriculaire : le majeur dépasse le plus
        for (int i = 0; i < 4; i++) {
            float x = -4.0F + i * 2.0F;
            // phalange du dessus (couchée vers l'avant) et phalange repliée devant la paume
            fist.addOrReplaceChild("finger_top_" + i, CubeListBuilder.create().texOffs(0, 16)
                    .addBox(x, -4.0F, -4.6F - reach[i], 2.0F, 3.0F, 3.0F), PartPose.ZERO);
            fist.addOrReplaceChild("finger_front_" + i, CubeListBuilder.create().texOffs(12, 16)
                    .addBox(x, -1.0F, -4.2F - reach[i] * 0.6F, 2.0F, 3.0F, 2.0F), PartPose.ZERO);
            // jointure saillante
            fist.addOrReplaceChild("knuckle_" + i, CubeListBuilder.create().texOffs(22, 16)
                    .addBox(x + 0.5F, -4.6F, -3.4F - reach[i], 1.0F, 1.0F, 1.0F), PartPose.ZERO);
        }
        // pouce : base sur le côté, bout replié devant le bas des doigts
        fist.addOrReplaceChild("thumb", CubeListBuilder.create().texOffs(0, 24).addBox(4.0F, -1.0F, -2.6F, 2.0F, 3.0F, 3.0F), PartPose.ZERO);
        fist.addOrReplaceChild("thumb_tip", CubeListBuilder.create().texOffs(12, 24).addBox(0.4F, 2.0F, -4.9F, 4.0F, 2.0F, 2.0F),
                PartPose.ZERO);
        // manchette rivetée au poignet, avant-bras qui s'affine
        fist.addOrReplaceChild("cuff", CubeListBuilder.create().texOffs(28, 0).addBox(-3.5F, -3.5F, 3.0F, 7.0F, 7.0F, 2.0F), PartPose.ZERO);
        fist.addOrReplaceChild("forearm", CubeListBuilder.create().texOffs(0, 32).addBox(-3.0F, -3.0F, 5.0F, 6.0F, 6.0F, 6.0F), PartPose.ZERO);
        fist.addOrReplaceChild("forearm_end", CubeListBuilder.create().texOffs(28, 16).addBox(-2.5F, -2.5F, 11.0F, 5.0F, 5.0F, 4.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * Anneau de force en blocs (octogone de 8 pièces, rayon 6 px, dans le plan XZ, autour de l'axe Y) : une bande épaisse et
     * un rebord extérieur. Sert aux anneaux du poignet pendant la charge, aux anneaux de compression de l'onde et aux ondes de
     * choc de l'explosion (mis à l'échelle).
     */
    public static LayerDefinition ring() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition ring = root.addOrReplaceChild("ring", CubeListBuilder.create(), PartPose.ZERO);
        for (int i = 0; i < 8; i++) {
            float a = (float) (i * Math.PI / 4);
            ring.addOrReplaceChild("band_" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F)
                            .texOffs(0, 8).addBox(-2.5F, -0.5F, 1.0F, 5.0F, 1.0F, 1.0F),
                    PartPose.offsetAndRotation((float) Math.sin(a) * 6.0F, 0.0F, (float) Math.cos(a) * 6.0F, 0.0F, a, 0.0F));
        }
        return LayerDefinition.create(mesh, 32, 16);
    }

    /** Morceau d'explosion en voxels : un bloc 8x8x8 et une bosse 4x4x4 (silhouette irrégulière), centré sur l'origine. */
    public static LayerDefinition chunk() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition chunk = root.addOrReplaceChild("chunk", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.ZERO);
        chunk.addOrReplaceChild("bump", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(3.0F, -3.0F, 2.0F, 0.4F, 0.6F, 0.2F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** Langue de flamme pixel : quatre blocs de plus en plus fins vers le haut (-Y), base à l'origine. */
    public static LayerDefinition flame() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("flame", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F)
                .texOffs(0, 8).addBox(-1.5F, -8.0F, -1.5F, 3.0F, 4.0F, 3.0F)
                .texOffs(0, 16).addBox(-1.0F, -11.0F, -1.0F, 2.0F, 3.0F, 2.0F)
                .texOffs(16, 16).addBox(-0.5F, -13.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }
}
