package nolan.bloxpiece.client.minks;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;

/**
 * Modèles 3D de la race Minks (textures pixel art : src/blockbench/minks/MakeMinksTextures.java) :
 * <ul>
 *   <li>{@link #EAR} / {@link #TAIL} : oreille et petite queue de lapin, fourrure blanche (forme normale) ;</li>
 *   <li>{@link #EAR_SULONG} / {@link #TAIL_SULONG} : modèles PROPRES à Sulong : oreille bien plus grande et large, bordée de
 *       touffes de poils, base duveteuse ; grosse queue en pompon fait d'un coeur, de touffes et de mèches ;</li>
 *   <li>{@link #GLOVE_RIGHT} / {@link #GLOVE_LEFT} : les GANTS ÉLECTRIQUES (un par main, en miroir) : coque qui recouvre la main
 *       depuis le poignet, manchette, arête des jointures, coussinet de paume, quatre doigts STYLISÉS (une lame anguleuse chacun,
 *       en éventail, dans le prolongement de la main) qui se continuent en griffes effilées sur trois étages, et le pouce. Origine : bout de la
 *       main (centre), doigts vers +Y (au-delà de la main de Minecraft). Dessinés translucides et lumineux en plusieurs couches,
 *       avec une texture de veines électriques animée : le gant EST l'électricité.</li>
 * </ul>
 */
public final class MinksModels {

    public static final ModelLayerLocation EAR = new ModelLayerLocation(BloxPiece.id("minks_ear"), "main");
    public static final ModelLayerLocation EAR_SULONG = new ModelLayerLocation(BloxPiece.id("minks_ear_sulong"), "main");
    public static final ModelLayerLocation TAIL = new ModelLayerLocation(BloxPiece.id("minks_tail"), "main");
    public static final ModelLayerLocation TAIL_SULONG = new ModelLayerLocation(BloxPiece.id("minks_tail_sulong"), "main");
    public static final ModelLayerLocation GLOVE_RIGHT = new ModelLayerLocation(BloxPiece.id("minks_glove"), "right");
    public static final ModelLayerLocation GLOVE_LEFT = new ModelLayerLocation(BloxPiece.id("minks_glove"), "left");

    public static final Identifier EARS_TEXTURE = BloxPiece.id("textures/entity/minks_ears.png");
    public static final Identifier EARS_SULONG_TEXTURE = BloxPiece.id("textures/entity/minks_ears_sulong.png");
    public static final Identifier TAIL_TEXTURE = BloxPiece.id("textures/entity/minks_tail.png");
    public static final Identifier TAIL_SULONG_TEXTURE = BloxPiece.id("textures/entity/minks_tail_sulong.png");
    /** Deux images de veines électriques, alternées en jeu : l'électricité du gant crépite. */
    public static final Identifier[] GLOVE_TEXTURES = {BloxPiece.id("textures/entity/minks_glove_a.png"),
            BloxPiece.id("textures/entity/minks_glove_b.png")};

    private MinksModels() {
    }

    /** Oreille normale : de la base (origine) vers le haut (-Y), environ 10 px. */
    public static LayerDefinition ear() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition ear = root.addOrReplaceChild("ear", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.0F, -5.0F, -0.5F, 2.0F, 5.0F, 1.0F)
                .texOffs(8, 0).addBox(-1.0F, -9.0F, -0.5F, 2.0F, 4.0F, 1.0F)
                .texOffs(16, 0).addBox(-0.5F, -10.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.ZERO);
        ear.addOrReplaceChild("inner", CubeListBuilder.create().texOffs(0, 8).addBox(-0.5F, -8.5F, -0.75F, 1.0F, 7.0F, 1.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 16);
    }

    /**
     * Oreille de Sulong : environ 15 px de haut et 3 de large, base duveteuse, intérieur rose plus grand, et des touffes de
     * poils qui débordent des deux bords et du bout (silhouette hérissée, bien différente de l'oreille normale).
     */
    public static LayerDefinition earSulong() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition ear = root.addOrReplaceChild("ear", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.5F, -7.0F, -0.75F, 3.0F, 7.0F, 2.0F)      // base
                .texOffs(12, 0).addBox(-1.5F, -13.0F, -0.75F, 3.0F, 6.0F, 2.0F)    // haut
                .texOffs(24, 0).addBox(-1.0F, -15.0F, -0.5F, 2.0F, 2.0F, 2.0F),    // bout
                PartPose.ZERO);
        ear.addOrReplaceChild("inner", CubeListBuilder.create().texOffs(0, 10).addBox(-0.75F, -12.5F, -1.1F, 2.0F, 11.0F, 1.0F),
                PartPose.ZERO);
        // base duveteuse autour de la racine
        ear.addOrReplaceChild("root_fluff", CubeListBuilder.create().texOffs(8, 10).addBox(-2.2F, -2.0F, -1.2F, 4.0F, 2.0F, 3.0F),
                PartPose.ZERO);
        // touffes sur les bords (alternées à gauche et à droite) et au bout
        float[][] tufts = {{-1.8F, -4.0F, 0.4F}, {1.6F, -5.5F, -0.4F}, {-1.9F, -8.0F, 0.3F}, {1.7F, -9.5F, -0.3F}, {-1.6F, -11.8F, 0.4F},
                {1.4F, -12.8F, -0.4F}, {0.0F, -15.5F, 0.0F}};
        for (int i = 0; i < tufts.length; i++) {
            ear.addOrReplaceChild("tuft_" + i, CubeListBuilder.create().texOffs(24, 10).addBox(-0.75F, -1.0F, -0.5F, 1.5F, 2.0F, 1.5F),
                    PartPose.offsetAndRotation(tufts[i][0], tufts[i][1], 0.0F, 0.0F, 0.2F * i, tufts[i][2]));
        }
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** Queue normale : petite boule duveteuse (environ 5 px). */
    public static LayerDefinition tail() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -2.0F, -1.5F, 4.0F, 4.0F, 3.0F),
                PartPose.ZERO);
        float[][] tufts = {{-1.6F, -1.2F, 0.4F}, {1.6F, -1.0F, 0.2F}, {0.0F, 1.6F, 0.5F}, {0.2F, -1.8F, -0.3F}};
        for (int i = 0; i < tufts.length; i++) {
            tail.addOrReplaceChild("tuft_" + i, CubeListBuilder.create().texOffs(0, 8).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F),
                    PartPose.offsetAndRotation(tufts[i][0], tufts[i][1], tufts[i][2], 0.3F * i, 0.5F * i, 0.2F * i));
        }
        return LayerDefinition.create(mesh, 32, 16);
    }

    /** Queue de Sulong : gros pompon (environ 11 px) : coeur, huit touffes et six mèches qui dépassent. */
    public static LayerDefinition tailSulong() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -2.5F, 6.0F, 6.0F, 5.0F),
                PartPose.ZERO);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            tail.addOrReplaceChild("tuft_" + i, CubeListBuilder.create().texOffs(0, 12).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                    PartPose.offsetAndRotation((float) Math.cos(a) * 2.8F, (float) Math.sin(a) * 2.8F, 0.6F * (i % 2), 0.4F * i, 0.3F * i, 0.25F * i));
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + 0.4;
            tail.addOrReplaceChild("wisp_" + i, CubeListBuilder.create().texOffs(16, 12).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                    PartPose.offsetAndRotation((float) Math.cos(a) * 4.6F, (float) Math.sin(a) * 4.6F, 1.2F, 0.3F, 0.0F, (float) a + 1.57F));
        }
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** Gant de la main droite (paume vers +X, côté du corps). */
    public static LayerDefinition gloveRight() {
        return glove(1.0F);
    }

    /** Gant de la main gauche (miroir : paume vers -X). */
    public static LayerDefinition gloveLeft() {
        return glove(-1.0F);
    }

    /** Positions (le long de la main, Z) et longueurs des quatre doigts : index, majeur, annulaire, auriculaire. */
    static final float[] FINGER_Z = {-1.6F, -0.55F, 0.5F, 1.55F}, FINGER_LEN = {1.9F, 2.1F, 2.0F, 1.6F};

    private static LayerDefinition glove(float p) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition glove = root.addOrReplaceChild("glove", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.2F, -3.8F, -2.2F, 4.4F, 3.8F, 4.4F)       // coque : paume et dos de la main
                .texOffs(0, 10).addBox(-2.45F, -4.9F, -2.45F, 4.9F, 1.3F, 4.9F),   // manchette au poignet
                PartPose.ZERO);
        // arête des jointures sur le dos de la main, coussinet sur la paume
        glove.addOrReplaceChild("knuckles", CubeListBuilder.create().texOffs(0, 18).addBox(p > 0 ? -2.7F : 2.2F, -1.2F, -2.0F, 0.5F, 1.0F, 4.0F),
                PartPose.ZERO);
        glove.addOrReplaceChild("palm", CubeListBuilder.create().texOffs(12, 18).addBox(p > 0 ? 2.2F : -2.7F, -3.2F, -1.8F, 0.5F, 2.6F, 3.6F),
                PartPose.ZERO);
        // doigts STYLISÉS : une lame anguleuse par doigt, en éventail, dans le prolongement de la main, qui se continue en griffe
        for (int i = 0; i < 4; i++) {
            PartDefinition finger = glove.addOrReplaceChild("finger_" + i, CubeListBuilder.create().texOffs(24, 0)
                    .addBox(-0.5F, 0.0F, -0.38F, 1.0F, FINGER_LEN[i] + 0.9F, 0.76F), PartPose.offsetAndRotation(0.35F * p, -0.3F, FINGER_Z[i],
                    FINGER_Z[i] * 0.08F, 0.0F, -0.2F * p));
            claw(finger, p, FINGER_LEN[i] + 0.8F);
        }
        // pouce : même construction, côté paume, vers l'avant
        PartDefinition thumb = glove.addOrReplaceChild("thumb", CubeListBuilder.create().texOffs(24, 0).addBox(-0.5F, 0.0F, -0.38F, 1.0F, 2.0F, 0.76F),
                PartPose.offsetAndRotation(1.5F * p, -2.7F, -1.9F, -0.45F, 0.0F, -0.35F * p));
        claw(thumb, p, 1.9F);
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** Griffe dans le prolongement du doigt : trois étages de plus en plus fins, recourbés vers la paume, pointe aiguë. */
    private static void claw(PartDefinition finger, float p, float at) {
        PartDefinition claw = finger.addOrReplaceChild("claw", CubeListBuilder.create().texOffs(24, 4).addBox(-0.36F, 0.0F, -0.3F, 0.72F, 1.5F, 0.6F),
                PartPose.offsetAndRotation(0.0F, at, 0.0F, 0.0F, 0.0F, -0.3F * p));
        PartDefinition mid = claw.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(24, 8).addBox(-0.22F, 0.0F, -0.2F, 0.44F, 1.3F, 0.4F),
                PartPose.offsetAndRotation(0.0F, 1.4F, 0.0F, 0.0F, 0.0F, -0.25F * p));
        mid.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(28, 8).addBox(-0.1F, 0.0F, -0.1F, 0.2F, 0.9F, 0.2F),
                PartPose.offsetAndRotation(0.0F, 1.2F, 0.0F, 0.0F, 0.0F, -0.2F * p));
    }
}
