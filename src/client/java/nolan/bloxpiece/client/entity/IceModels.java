package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;

/**
 * Modèles 3D du fruit de la Glace : trident (en main et lancé), aigle aux ailes animées, pic de glace, statue de glace.
 *
 * Tous partagent la même texture de glace translucide (textures/entity/ice.png : bleu glacé, veines claires, givre sur les
 * arêtes) et sont dessinés en rendu translucide : on voit à travers la glace. Repère commun à tous (comme les modèles des autres
 * fruits) : groupe "body" à 24 px, que les renderers ramènent sur la position de l'entité ; l'avant regarde vers -Z.
 */
public final class IceModels {

    public static final Identifier TEXTURE = BloxPiece.id("textures/entity/ice.png");
    private static final int TEX = 128;

    private IceModels() {
    }

    /** Données de rendu partagées par les entités de glace. */
    public static class IceRenderState extends EntityRenderState {
        public float yaw;
        public float pitch;
        /** Échelle d'affichage (apparition, croissance d'un pic...). */
        public float scale = 1.0F;
        /** Statue : largeur et hauteur de l'entité gelée (blocs). */
        public float width = 0.6F;
        public float height = 1.8F;
    }

    // ------------------------------------------------------------------ outils

    private static PartDefinition group(PartDefinition parent, String name, float x, float y, float z,
                                        float rx, float ry, float rz) {
        return parent.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offsetAndRotation(x, y, z,
                (float) Math.toRadians(rx), (float) Math.toRadians(ry), (float) Math.toRadians(rz)));
    }

    /** Cube de dimensions (dx, dy, dz) centré sur (x, y, z) dans son groupe, tourné (degrés) autour de son centre. */
    private static void cube(PartDefinition parent, String name, float dx, float dy, float dz,
                             float x, float y, float z, float rx, float ry, float rz) {
        parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 0).addBox(-dx / 2, -dy / 2, -dz / 2, dx, dy, dz),
                PartPose.offsetAndRotation(x, y, z, (float) Math.toRadians(rx), (float) Math.toRadians(ry), (float) Math.toRadians(rz)));
    }

    // ------------------------------------------------------------------ trident

    /**
     * Trident de glace sculpté (~50 px de long) : hampe fine, trois bagues de poignée, cristal au pommeau, garde, dent centrale
     * longue et deux dents latérales qui se referment légèrement, avec leurs barbillons, et des cristaux de givre sur la hampe.
     * La poignée est à z = +17 px (repère de prise en main) ; les dents pointent vers -Z.
     */
    public static class TridentModel extends EntityModel<IceRenderState> {

        public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("ice_trident"), "main");
        /** Position de la poignée le long du trident (px). */
        public static final float GRIP_Z = 17.0F;

        private final ModelPart body;

        public TridentModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
            this.body = root.getChild("body");
        }

        @Override
        public void setupAnim(IceRenderState state) {
            super.setupAnim(state);
            this.body.xRot = (float) Math.toRadians(state.pitch);
        }

        /** La pièce racine, pour dessiner le trident sans état d'entité (dans la main). */
        public ModelPart rootPart() {
            return this.root();
        }

        public static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition body = group(mesh.getRoot(), "body", 0, 24, 0, 0, 0, 0);
            cube(body, "shaft", 1.5F, 1.5F, 34, 0, 0, 11, 0, 0, 0);
            for (int i = 0; i < 3; i++) {
                cube(body, "grip_" + i, 2.5F, 2.5F, 1.5F, 0, 0, 14 + i * 3, 0, 0, 45);
            }
            cube(body, "pommel", 2.6F, 2.6F, 3, 0, 0, 29.5F, 0, 0, 45);
            cube(body, "pommel_tip", 1.2F, 1.2F, 2, 0, 0, 31.5F, 0, 0, 45);
            cube(body, "guard", 9, 1.8F, 1.8F, 0, 0, -6, 0, 0, 0);
            cube(body, "guard_gem", 2.4F, 2.4F, 2.4F, 0, 0, -6, 45, 45, 0);
            cube(body, "prong_mid", 1.7F, 1.7F, 12, 0, 0, -13, 0, 0, 45);
            cube(body, "prong_mid_tip", 1.0F, 1.0F, 3.5F, 0, 0, -20.5F, 0, 0, 45);
            for (int side = -1; side <= 1; side += 2) {
                String s = side < 0 ? "r" : "l";
                cube(body, "prong_" + s, 1.4F, 1.4F, 8, side * 4.0F, 0, -10.5F, 0, side * 9.0F, 45);
                cube(body, "prong_" + s + "_tip", 0.9F, 0.9F, 3, side * 3.2F, 0, -15.8F, 0, side * 14.0F, 45);
                cube(body, "barb_" + s, 0.8F, 0.8F, 2.4F, side * 5.0F, 0, -9.0F, 0, side * -35.0F, 45);
            }
            float[][] frost = {{0.9F, 0.3F, 4}, {-0.9F, -0.2F, 9}, {0.2F, 0.9F, 22}, {-0.3F, -0.9F, 25}};
            for (int i = 0; i < frost.length; i++) {
                cube(body, "frost_" + i, 0.9F, 1.8F, 0.9F, frost[i][0], frost[i][1], frost[i][2], 25 * i, 40, 30);
            }
            return LayerDefinition.create(mesh, TEX, TEX);
        }
    }

    // ------------------------------------------------------------------ aigle

    /**
     * Aigle de glace aux ailes déployées : corps, poitrail, cou, tête avec crête et bec crochu, queue en éventail de 5 plumes,
     * et deux ailes de trois segments (épaule, avant-bras, rémiges en éventail). Les ailes battent (le bout suit l'épaule avec un
     * léger retard, comme un vrai battement), la queue ondule.
     */
    public static class EagleModel extends EntityModel<IceRenderState> {

        public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("ice_eagle"), "main");

        private final ModelPart body;
        private final ModelPart leftWing, leftMid, rightWing, rightMid, tail;

        public EagleModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
            this.body = root.getChild("body");
            this.leftWing = this.body.getChild("wing_l");
            this.leftMid = this.leftWing.getChild("wing_l_mid");
            this.rightWing = this.body.getChild("wing_r");
            this.rightMid = this.rightWing.getChild("wing_r_mid");
            this.tail = this.body.getChild("tail");
        }

        @Override
        public void setupAnim(IceRenderState state) {
            super.setupAnim(state);
            // Battement dynamique : très ample et rapide au lancement (propulsion), puis un battement puissant et régulier
            float boost = Math.max(0.0F, 1.0F - state.ageInTicks / 12.0F);
            float t = state.ageInTicks * (0.6F + 0.5F * boost);
            this.body.xRot = (float) Math.toRadians(state.pitch);
            this.body.y += (float) Math.sin(t) * (0.8F + 0.8F * boost); // le corps monte quand les ailes s'abaissent
            float flap = (float) Math.sin(t) * (0.55F + 0.35F * boost);
            float lag = (float) Math.sin(t - 0.7F) * (0.45F + 0.25F * boost);
            this.leftWing.zRot += flap;
            this.leftMid.zRot += lag;
            this.rightWing.zRot -= flap;
            this.rightMid.zRot -= lag;
            this.tail.yRot += (float) Math.sin(t * 0.5F) * 0.12F;
        }

        public static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition body = group(mesh.getRoot(), "body", 0, 24, 0, 0, 0, 0);
            cube(body, "torso", 6, 5, 14, 0, 0, 0, 0, 0, 0);
            cube(body, "chest", 7, 6, 6, 0, 0.5F, -5, 0, 0, 0);
            cube(body, "neck", 4, 4, 4, 0, -1, -9.5F, -15, 0, 0);
            cube(body, "head", 5, 5, 6, 0, -2, -13, 0, 0, 0);
            cube(body, "crest", 1, 2, 4, 0, -4.8F, -12, -10, 0, 0);
            cube(body, "beak", 2, 2, 4, 0, -1.2F, -17, 20, 0, 0);
            cube(body, "beak_tip", 1.2F, 1.6F, 2, 0, 0.3F, -18.8F, 45, 0, 0);
            cube(body, "talon_l", 1.2F, 3, 1.2F, 1.6F, 3.5F, 2, 20, 0, 0);
            cube(body, "talon_r", 1.2F, 3, 1.2F, -1.6F, 3.5F, 2, 20, 0, 0);

            PartDefinition tail = group(body, "tail", 0, -0.5F, 7, 0, 0, 0);
            for (int i = 0; i < 5; i++) {
                float a = -24 + i * 12;
                cube(tail, "tail_" + i, 2, 1, 9, (float) Math.sin(Math.toRadians(a)) * 4.5F, 0, (float) Math.cos(Math.toRadians(a)) * 4.5F, 0, a, 0);
            }

            for (int side = -1; side <= 1; side += 2) {
                String s = side > 0 ? "l" : "r";
                PartDefinition wing = group(body, "wing_" + s, side * 3.0F, -1.5F, -2, 0, 0, side * -8.0F);
                cube(wing, "wing_" + s + "_shoulder", 12, 1.6F, 9, side * 6.0F, 0, 0, 0, 0, 0);
                PartDefinition mid = group(wing, "wing_" + s + "_mid", side * 12.0F, 0, 0, 0, 0, side * -6.0F);
                cube(mid, "wing_" + s + "_arm", 12, 1.2F, 8, side * 6.0F, 0, 0.5F, 0, 0, 0);
                for (int f = 0; f < 4; f++) {
                    float a = side * (-20 + f * 14);
                    cube(mid, "wing_" + s + "_feather_" + f, 11, 0.8F, 2.6F,
                            side * 12.0F + (float) Math.cos(Math.toRadians(a)) * side * 4.5F, 0, 2.5F - f * 2.0F + (float) Math.sin(Math.toRadians(a)) * 2.0F,
                            0, -a, 0);
                }
            }
            return LayerDefinition.create(mesh, TEX, TEX);
        }
    }

    // ------------------------------------------------------------------ pic

    /** Pic de glace qui sort du sol : trois étages qui s'affinent et se tordent, pointe en losange, deux éclats latéraux. */
    public static class SpikeModel extends EntityModel<IceRenderState> {

        public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("ice_spike"), "main");

        public SpikeModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
        }

        public static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition body = group(mesh.getRoot(), "body", 0, 24, 0, 0, 0, 0);
            cube(body, "base", 6, 7, 6, 0, -3.5F, 0, 0, 0, 0);
            cube(body, "mid", 4.6F, 8, 4.6F, 0, -10.5F, 0, 0, 25, 0);
            cube(body, "upper", 3.2F, 8, 3.2F, 0, -18, 0, 0, 50, 0);
            cube(body, "tip", 2, 5, 2, 0, -24, 0, 0, 45, 0);
            cube(body, "tip_point", 1, 2.5F, 1, 0, -27.5F, 0, 0, 45, 0);
            cube(body, "shard_a", 2.2F, 9, 2.2F, 3.4F, -5, 0.5F, 0, 20, -24);
            cube(body, "shard_b", 2, 7, 2, -3.2F, -4, -0.8F, 10, -30, 22);
            return LayerDefinition.create(mesh, TEX, TEX);
        }
    }

    // ------------------------------------------------------------------ statue

    /** Statue de glace : un bloc de glace (1 x 1 x 1 bloc, étiré à la taille de la cible par le renderer) hérissé de cristaux. */
    public static class StatueModel extends EntityModel<IceRenderState> {

        public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("ice_statue"), "main");

        public StatueModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
        }

        public static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition body = group(mesh.getRoot(), "body", 0, 24, 0, 0, 0, 0);
            cube(body, "block", 16, 16, 16, 0, -8, 0, 0, 0, 0);
            cube(body, "crystal_top", 4, 7, 4, 2, -17.5F, -1, 10, 30, 12);
            cube(body, "crystal_top_b", 3, 5, 3, -3.5F, -16.5F, 2.5F, -14, 10, -18);
            cube(body, "crystal_side", 3, 8, 3, 8.5F, -9, 3, 0, 20, -30);
            cube(body, "crystal_side_b", 3, 7, 3, -8.5F, -5, -4, 15, -10, 28);
            cube(body, "crystal_front", 2.5F, 6, 2.5F, -2, -12, -8.5F, 35, 15, 0);
            return LayerDefinition.create(mesh, TEX, TEX);
        }
    }
}
