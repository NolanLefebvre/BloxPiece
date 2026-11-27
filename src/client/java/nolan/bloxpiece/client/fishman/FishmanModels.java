package nolan.bloxpiece.client.fishman;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;

/**
 * Modèles 3D de la race Fishman (textures : src/blockbench/fishman/MakeFishmanTextures.java) :
 * <ul>
 *   <li>{@link #ARM_FIN_RIGHT} / {@link #ARM_FIN_LEFT} : aileron sur la face EXTÉRIEURE de l'avant-bras (repère du bras, px) :
 *       une crête épaisse collée au bras, une membrane en rangées qui s'élargissent vers le coude, une pointe qui file vers
 *       l'épaule ;</li>
 *   <li>{@link #BACK_FIN} : grand aileron dorsal (repère du buste, px) : crête le long de la colonne, membrane à base large
 *       collée au dos et POINTE VERS LE BAS et l'arrière, jusqu'au bas du buste (profil d'aileron de requin retourné).</li>
 * </ul>
 * La membrane est mince (moins d'un pixel) : on la lit de profil comme une vraie nageoire ; les rayons sont dans la texture.
 */
public final class FishmanModels {

    public static final ModelLayerLocation ARM_FIN_RIGHT = new ModelLayerLocation(BloxPiece.id("fishman_arm_fin"), "right");
    public static final ModelLayerLocation ARM_FIN_LEFT = new ModelLayerLocation(BloxPiece.id("fishman_arm_fin"), "left");
    public static final ModelLayerLocation BACK_FIN = new ModelLayerLocation(BloxPiece.id("fishman_back_fin"), "main");
    /** Cube d'eau de 16 px centré (1 bloc) : volumes d'eau, gouttes, vagues (texture eau ou écume). */
    public static final ModelLayerLocation WATER = new ModelLayerLocation(BloxPiece.id("fishman_water"), "main");

    public static final Identifier FIN_TEXTURE = BloxPiece.id("textures/entity/fishman_fin.png");
    public static final Identifier WATER_TEXTURE = BloxPiece.id("textures/entity/fishman_water.png");
    public static final Identifier FOAM_TEXTURE = BloxPiece.id("textures/entity/fishman_foam.png");

    private static final float THIN = 0.8F;

    private FishmanModels() {
    }

    public static LayerDefinition armFinRight() {
        return armFin(false);
    }

    public static LayerDefinition armFinLeft() {
        return armFin(true);
    }

    /**
     * Aileron de bras, origine sur la face extérieure du bras (x = 0), membrane vers l'extérieur ({@code s} : -1 bras droit,
     * +1 bras gauche), du poignet (y = 9) vers le coude, rangées de plus en plus larges, pointe vers l'épaule.
     */
    private static LayerDefinition armFin(boolean left) {
        float s = left ? 1.0F : -1.0F;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition fin = root.addOrReplaceChild("fin", CubeListBuilder.create()
                // crête épaisse collée au bras
                .texOffs(0, 0).addBox(left ? -0.2F : -0.6F, 0.5F, -1.0F, 0.8F, 8.5F, 2.0F), PartPose.ZERO);
        // membrane : 7 rangées, du poignet (étroite) au coude (large)
        float[] widths = {1.0F, 1.6F, 2.2F, 2.7F, 3.2F, 3.6F, 3.9F};
        for (int i = 0; i < widths.length; i++) {
            float y = 8.0F - i * 1.1F, w = widths[i];
            fin.addOrReplaceChild("row_" + i, CubeListBuilder.create().texOffs(8, 4 * (6 - i))
                    .addBox(left ? 0.4F : -0.4F - w, y - 1.1F, -THIN / 2, w, 1.2F, THIN), PartPose.ZERO);
        }
        // pointe qui file vers l'épaule, collée à la dernière rangée (aucune rotation : rien ne se détache)
        fin.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(8, 52)
                .addBox(left ? 1.8F : -4.4F, -1.3F, -THIN / 2, 2.6F, 1.7F, THIN), PartPose.ZERO);
        fin.addOrReplaceChild("tip_end", CubeListBuilder.create().texOffs(8, 58)
                .addBox(left ? 3.2F : -4.4F, -2.2F, -THIN / 2, 1.2F, 1.0F, THIN), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * Aileron dorsal, origine au milieu du dos (z = 2 : surface du buste), membrane vers l'arrière (+z), pointe vers le BAS
     * (y grand) et l'arrière.
     */
    public static LayerDefinition backFin() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition fin = root.addOrReplaceChild("fin", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.8F, 1.0F, -0.3F, 1.6F, 8.5F, 1.0F), PartPose.ZERO); // crête le long de la colonne
        // membrane : une rangée par pixel de hauteur, du haut du dos jusqu'à la pointe (en bas du buste). Le profil est calculé
        // dans le sens « pointe en haut » (y = -2 : pointe, y = 9 : haut de la base) puis RETOURNÉ par symétrie autour de y = 5 :
        // une rangée [y, y + 1,05] devient [10 - y - 1,05, 10 - y]. Base large collée au dos (y de 1 à 9), pointe vers le bas et
        // l'arrière (y jusqu'à 12 : le bas du buste, au-dessus des jambes, à plus de 4 px du dos : les jambes ne la touchent pas).
        // (repère du buste : -y = haut, +y = bas, +z = dos, comme la cape de Minecraft)
        int i = 0;
        for (float y = -2.0F; y < 9.0F; y += 1.0F, i++) {
            float yc = y + 0.5F;
            float back = 0.4F + 7.5F * (float) Math.pow(Math.max(0.0F, 9.5F - yc) / 12.0F, 1.2);
            float front = Math.min(yc < 1.5F ? (1.5F - yc) * 2.2F : 0.0F, back - 0.9F); // rangées jointives jusqu'à la pointe
            float depth = Math.max(0.6F, back - front);
            fin.addOrReplaceChild("row_" + i, CubeListBuilder.create().texOffs(8, 4 * (i % 16))
                    .addBox(-THIN / 2, 10.0F - y - 1.05F, 0.3F + front, THIN, 1.05F, depth), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Cube d'eau : 16 px de côté, centré sur l'origine (mise à l'échelle en blocs au rendu). */
    public static LayerDefinition water() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("cube", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }
}
