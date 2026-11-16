package nolan.bloxpiece.client.entity;

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
 * GENERE par make_entei_model.py : ne pas editer a la main.
 * Gros soleil de feu : coeur blanc-jaune, deux coques de disques orange, langues de feu croisees et braises.
 * Pour changer l'allure, modifie les parametres en haut de make_entei_model.py, relance-le et recopie
 * EnteiSunModel.java + dai_enkai_entei.png. La texture est 4x plus detaillee que la taille declaree dans
 * la LayerDefinition : c'est voulu (les UV sont normalises).
 */
public class EnteiSunModel extends EntityModel<EnteiSunRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("entei_sun"), "main");

    private static final float[] CORE_SPEED = {0.020F, -0.060F, 0.015F};
    private static final float[] MID_SPEED = {-0.015F, 0.045F, -0.020F};
    private static final float[] OUTER_SPEED = {0.010F, -0.030F, 0.012F};
    private static final float[] TONGUES_A_SPEED = {0.012F, 0.050F, -0.020F};
    private static final float[] TONGUES_B_SPEED = {-0.018F, -0.035F, 0.025F};
    private static final float[] EMBERS_SPEED = {0.015F, -0.040F, 0.020F};

    private final ModelPart core;
    private final ModelPart midShell;
    private final ModelPart outerShell;
    private final ModelPart tonguesA;
    private final ModelPart tonguesB;
    private final ModelPart embers;

    public EnteiSunModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        this.core = root.getChild("core");
        this.midShell = root.getChild("mid_shell");
        this.outerShell = root.getChild("outer_shell");
        this.tonguesA = root.getChild("tongues_a");
        this.tonguesB = root.getChild("tongues_b");
        this.embers = root.getChild("embers");
    }

    @Override
    public void setupAnim(EnteiSunRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        spin(this.core, CORE_SPEED, age);
        spin(this.midShell, MID_SPEED, age);
        spin(this.outerShell, OUTER_SPEED, age);
        spin(this.tonguesA, TONGUES_A_SPEED, age);
        spin(this.tonguesB, TONGUES_B_SPEED, age);
        spin(this.embers, EMBERS_SPEED, age);
        // Les langues de feu "vacillent" : petite oscillation en plus de la rotation
        this.tonguesA.xRot += (float) Math.sin(age * 0.37F) * 0.10F;
        this.tonguesA.zRot += (float) Math.cos(age * 0.29F) * 0.10F;
        this.tonguesB.xRot += (float) Math.cos(age * 0.43F) * 0.12F;
        this.tonguesB.zRot += (float) Math.sin(age * 0.31F) * 0.12F;
    }

    private static void spin(ModelPart group, float[] speed, float age) {
        group.xRot = age * speed[0];
        group.yRot = age * speed[1];
        group.zRot = age * speed[2];
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition core = group(root, "core", 0F, 24F, 0F);
        cube(core, "core_01", 0, 0, -6F, -6F, 4.601F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, 74.065F, 69.281F, 146.658F);
        cube(core, "core_02", 0, 0, -6F, -6F, 5.002F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, -76.322F, -2.404F, 30.365F);
        cube(core, "core_03", 0, 0, -6F, -6F, 5.31F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, 150.764F, 42.79F, -149.382F);
        cube(core, "core_04", 0, 0, -6F, -6F, 4.75F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, 41.722F, -5.891F, 162.38F);
        cube(core, "core_05", 0, 0, -6F, -6F, 4.878F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, -130.771F, 52.657F, 22.574F);
        cube(core, "core_06", 0, 0, -6F, -6F, 5.039F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, -105.069F, 21.027F, -71.885F);
        cube(core, "core_07", 0, 0, -6F, -6F, 5.055F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, -31.804F, -17.033F, 36.254F);
        cube(core, "core_08", 0, 0, -6F, -6F, 4.731F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, -166.109F, -1.836F, 53.87F);
        cube(core, "core_09", 0, 0, -6F, -6F, 4.935F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, -42.516F, 34.921F, -54.023F);
        cube(core, "core_10", 0, 0, -6F, -6F, 5.193F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, -71.498F, -73.296F, 75.427F);
        cube(core, "core_11", 0, 0, -6F, -6F, 5.441F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, 136.862F, -0.564F, 75.291F);
        cube(core, "core_12", 0, 0, -6F, -6F, 5.398F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, -3.253F, -15.806F, 102.566F);
        cube(core, "core_13", 0, 0, -6F, -6F, 4.766F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, 137.475F, 23.302F, -34.778F);
        cube(core, "core_14", 0, 0, -6F, -6F, 5.366F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, -84.768F, 37.536F, -116.894F);
        cube(core, "core_15", 0, 0, -6F, -6F, 4.863F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, -52.42F, -37.463F, 109.215F);
        cube(core, "core_16", 0, 0, -6F, -6F, 5.061F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, 136.892F, -21.004F, -11.69F);
        cube(core, "core_17", 0, 0, -6F, -6F, 4.979F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, 22.965F, -59.65F, 83.555F);
        cube(core, "core_18", 0, 0, -6F, -6F, 4.937F, 12F, 12F, 1F, 0F, 0F, 0F, 0F, 103.292F, 68.551F, -6.193F);

        PartDefinition midShell = group(root, "mid_shell", 0F, 24F, 0F);
        cube(midShell, "mid_shell_01", 26, 0, -5F, -5F, 8.639F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 80.623F, 28.256F, 171.214F);
        cube(midShell, "mid_shell_02", 26, 0, -5F, -5F, 8.931F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -106.294F, -50.174F, 32.57F);
        cube(midShell, "mid_shell_03", 26, 0, -5F, -5F, 9.134F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -179.546F, -80.389F, 67.809F);
        cube(midShell, "mid_shell_04", 26, 0, -5F, -5F, 8.946F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 55.345F, 37.454F, 172.176F);
        cube(midShell, "mid_shell_05", 26, 0, -5F, -5F, 8.912F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -126.464F, 14.191F, 3.79F);
        cube(midShell, "mid_shell_06", 26, 0, -5F, -5F, 9.209F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 57.534F, 53.288F, 115.929F);
        cube(midShell, "mid_shell_07", 26, 0, -5F, -5F, 8.611F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 83.314F, -4.18F, -135.953F);
        cube(midShell, "mid_shell_08", 26, 0, -5F, -5F, 8.556F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 138.422F, 33.101F, -179.819F);
        cube(midShell, "mid_shell_09", 26, 0, -5F, -5F, 9.232F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -2.119F, -42.281F, -98.537F);
        cube(midShell, "mid_shell_10", 26, 0, -5F, -5F, 9.162F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -155.499F, -53.56F, 107.29F);
        cube(midShell, "mid_shell_11", 26, 0, -5F, -5F, 8.598F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 81.275F, -83.997F, 132.876F);
        cube(midShell, "mid_shell_12", 26, 0, -5F, -5F, 8.701F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 50.449F, -7.192F, -120.747F);
        cube(midShell, "mid_shell_13", 26, 0, -5F, -5F, 9.021F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -171.147F, 22.508F, -68.468F);
        cube(midShell, "mid_shell_14", 26, 0, -5F, -5F, 9.251F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -45.003F, -17.152F, -70.913F);
        cube(midShell, "mid_shell_15", 26, 0, -5F, -5F, 8.976F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -100.357F, -62.623F, 77.027F);
        cube(midShell, "mid_shell_16", 26, 0, -5F, -5F, 8.599F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -138.744F, -45.855F, -28.368F);
        cube(midShell, "mid_shell_17", 26, 0, -5F, -5F, 8.748F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 14.415F, -13.379F, -99.711F);
        cube(midShell, "mid_shell_18", 26, 0, -5F, -5F, 9.188F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -171.185F, 33.293F, -11.72F);
        cube(midShell, "mid_shell_19", 26, 0, -5F, -5F, 9.431F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -6.545F, -74.714F, -161.859F);
        cube(midShell, "mid_shell_20", 26, 0, -5F, -5F, 9.449F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -53.125F, 53.332F, 114.829F);
        cube(midShell, "mid_shell_21", 26, 0, -5F, -5F, 8.891F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -160.705F, -18.845F, -27.221F);
        cube(midShell, "mid_shell_22", 26, 0, -5F, -5F, 8.656F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 16.941F, 10.974F, 71.723F);
        cube(midShell, "mid_shell_23", 26, 0, -5F, -5F, 8.926F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 119.189F, 20.799F, -83.038F);
        cube(midShell, "mid_shell_24", 26, 0, -5F, -5F, 9.135F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 106.878F, -20.558F, 81.344F);
        cube(midShell, "mid_shell_25", 26, 0, -5F, -5F, 8.821F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -31.754F, -15.029F, 74.246F);
        cube(midShell, "mid_shell_26", 26, 0, -5F, -5F, 8.673F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 174.908F, -8.524F, -125.81F);
        cube(midShell, "mid_shell_27", 26, 0, -5F, -5F, 8.952F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -3.48F, 51.335F, -18.718F);
        cube(midShell, "mid_shell_28", 26, 0, -5F, -5F, 8.736F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 79.726F, -68.147F, -70.477F);
        cube(midShell, "mid_shell_29", 26, 0, -5F, -5F, 9.146F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 139.197F, 14.834F, 82.208F);
        cube(midShell, "mid_shell_30", 26, 0, -5F, -5F, 8.771F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 5.698F, -18.719F, 65.277F);
        cube(midShell, "mid_shell_31", 26, 0, -5F, -5F, 8.946F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -140.909F, -28.919F, 150.662F);
        cube(midShell, "mid_shell_32", 26, 0, -5F, -5F, 9.406F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 85.786F, 22.353F, 67.649F);
        cube(midShell, "mid_shell_33", 26, 0, -5F, -5F, 9.128F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 55.751F, -0.892F, -62.062F);
        cube(midShell, "mid_shell_34", 26, 0, -5F, -5F, 8.822F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 157.186F, -12.82F, -11.016F);
        cube(midShell, "mid_shell_35", 26, 0, -5F, -5F, 9.354F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -9.427F, 39.306F, -59.498F);
        cube(midShell, "mid_shell_36", 26, 0, -5F, -5F, 9.059F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -101.216F, -4.543F, 124.502F);
        cube(midShell, "mid_shell_37", 26, 0, -5F, -5F, 8.574F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -116.79F, 38.854F, -149.36F);
        cube(midShell, "mid_shell_38", 26, 0, -5F, -5F, 8.88F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 39.056F, 18.378F, -41.669F);
        cube(midShell, "mid_shell_39", 26, 0, -5F, -5F, 8.869F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -131.719F, 0.423F, 158.204F);
        cube(midShell, "mid_shell_40", 26, 0, -5F, -5F, 9.018F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -50.604F, -58.727F, -172.637F);
        cube(midShell, "mid_shell_41", 26, 0, -5F, -5F, 9.032F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 76.111F, 49.348F, -47.944F);
        cube(midShell, "mid_shell_42", 26, 0, -5F, -5F, 8.828F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 139.756F, -46.674F, -17.333F);
        cube(midShell, "mid_shell_43", 26, 0, -5F, -5F, 8.957F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -41.337F, 47.111F, -132.409F);
        cube(midShell, "mid_shell_44", 26, 0, -5F, -5F, 9.392F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, 107.322F, 2.492F, -23.8F);
        cube(midShell, "mid_shell_45", 26, 0, -5F, -5F, 8.875F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -93.84F, -51.702F, -159.163F);
        cube(midShell, "mid_shell_46", 26, 0, -5F, -5F, 8.798F, 10F, 10F, 1F, 0F, 0F, 0F, 0F, -76.826F, 50.485F, -175.592F);

        PartDefinition outerShell = group(root, "outer_shell", 0F, 24F, 0F);
        cube(outerShell, "outer_shell_01", 0, 13, -5.5F, -5.5F, 12.308F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -52.82F, 73.078F, 38.52F);
        cube(outerShell, "outer_shell_02", 0, 13, -5.5F, -5.5F, 12.302F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -123.688F, 61.261F, -25.736F);
        cube(outerShell, "outer_shell_03", 0, 13, -5.5F, -5.5F, 12.104F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 75.733F, -46.732F, 171.23F);
        cube(outerShell, "outer_shell_04", 0, 13, -5.5F, -5.5F, 12.836F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -85.409F, -19.262F, 23.41F);
        cube(outerShell, "outer_shell_05", 0, 13, -5.5F, -5.5F, 12.598F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -114.299F, -12.308F, -11.388F);
        cube(outerShell, "outer_shell_06", 0, 13, -5.5F, -5.5F, 12.783F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -57.171F, 5.08F, -3.559F);
        cube(outerShell, "outer_shell_07", 0, 13, -5.5F, -5.5F, 12.513F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -116.983F, -31.214F, 41.065F);
        cube(outerShell, "outer_shell_08", 0, 13, -5.5F, -5.5F, 12.083F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 90.332F, 29.311F, 141.277F);
        cube(outerShell, "outer_shell_09", 0, 13, -5.5F, -5.5F, 12.358F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -59.381F, 21.124F, 45.88F);
        cube(outerShell, "outer_shell_10", 0, 13, -5.5F, -5.5F, 12.122F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -134.517F, 12.573F, -12.154F);
        cube(outerShell, "outer_shell_11", 0, 13, -5.5F, -5.5F, 12.51F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 18.126F, -55.878F, -144.539F);
        cube(outerShell, "outer_shell_12", 0, 13, -5.5F, -5.5F, 12.915F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 96.3F, 3.019F, -131.493F);
        cube(outerShell, "outer_shell_13", 0, 13, -5.5F, -5.5F, 12.352F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 172.855F, 65.32F, -141.731F);
        cube(outerShell, "outer_shell_14", 0, 13, -5.5F, -5.5F, 12.585F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 29.568F, 24.434F, 161.447F);
        cube(outerShell, "outer_shell_15", 0, 13, -5.5F, -5.5F, 12.105F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 137.117F, 2.186F, -141.895F);
        cube(outerShell, "outer_shell_16", 0, 13, -5.5F, -5.5F, 12.499F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -71.117F, 34.841F, -43.626F);
        cube(outerShell, "outer_shell_17", 0, 13, -5.5F, -5.5F, 12.575F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 67.529F, -3.359F, -121.645F);
        cube(outerShell, "outer_shell_18", 0, 13, -5.5F, -5.5F, 12.801F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -144.632F, -2.119F, -31.689F);
        cube(outerShell, "outer_shell_19", 0, 13, -5.5F, -5.5F, 12.141F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 25.269F, -25.019F, -172.054F);
        cube(outerShell, "outer_shell_20", 0, 13, -5.5F, -5.5F, 12.354F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -135.699F, -56.239F, 103.428F);
        cube(outerShell, "outer_shell_21", 0, 13, -5.5F, -5.5F, 12.096F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -105.772F, -19.686F, -62.306F);
        cube(outerShell, "outer_shell_22", 0, 13, -5.5F, -5.5F, 12.251F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -30.618F, -24.233F, 17.118F);
        cube(outerShell, "outer_shell_23", 0, 13, -5.5F, -5.5F, 12.84F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 179.038F, -22.806F, 116.927F);
        cube(outerShell, "outer_shell_24", 0, 13, -5.5F, -5.5F, 12.7F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 30.444F, -48.189F, 162.975F);
        cube(outerShell, "outer_shell_25", 0, 13, -5.5F, -5.5F, 12.923F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 83.467F, -16.789F, -104.373F);
        cube(outerShell, "outer_shell_26", 0, 13, -5.5F, -5.5F, 12.531F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 140.551F, 23.516F, 137.989F);
        cube(outerShell, "outer_shell_27", 0, 13, -5.5F, -5.5F, 12.166F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -10.898F, -5.651F, -17.335F);
        cube(outerShell, "outer_shell_28", 0, 13, -5.5F, -5.5F, 12.571F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -137.42F, -3.069F, 73.926F);
        cube(outerShell, "outer_shell_29", 0, 13, -5.5F, -5.5F, 12.662F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -79.798F, -72.553F, -88.222F);
        cube(outerShell, "outer_shell_30", 0, 13, -5.5F, -5.5F, 12.169F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -48.515F, -24.729F, 59.083F);
        cube(outerShell, "outer_shell_31", 0, 13, -5.5F, -5.5F, 12.604F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -169.881F, 7.11F, -95.727F);
        cube(outerShell, "outer_shell_32", 0, 13, -5.5F, -5.5F, 12.928F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 19.763F, -28.549F, 150.047F);
        cube(outerShell, "outer_shell_33", 0, 13, -5.5F, -5.5F, 12.231F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 104.909F, -27.058F, -103.033F);
        cube(outerShell, "outer_shell_34", 0, 13, -5.5F, -5.5F, 12.705F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -132.276F, 42.347F, -119.466F);
        cube(outerShell, "outer_shell_35", 0, 13, -5.5F, -5.5F, 12.852F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 19.235F, -1.859F, -81.266F);
        cube(outerShell, "outer_shell_36", 0, 13, -5.5F, -5.5F, 12.885F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -159.307F, -12.794F, 127.632F);
        cube(outerShell, "outer_shell_37", 0, 13, -5.5F, -5.5F, 12.451F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -65.115F, 22.581F, -81.148F);
        cube(outerShell, "outer_shell_38", 0, 13, -5.5F, -5.5F, 12.223F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 73.274F, 24.517F, -90.808F);
        cube(outerShell, "outer_shell_39", 0, 13, -5.5F, -5.5F, 12.659F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 150.558F, -8.126F, 59.967F);
        cube(outerShell, "outer_shell_40", 0, 13, -5.5F, -5.5F, 12.573F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -8.343F, 10.528F, -71.808F);
        cube(outerShell, "outer_shell_41", 0, 13, -5.5F, -5.5F, 12.762F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -130.384F, 29.396F, 79.247F);
        cube(outerShell, "outer_shell_42", 0, 13, -5.5F, -5.5F, 12.168F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 99.74F, 49.297F, 87.146F);
        cube(outerShell, "outer_shell_43", 0, 13, -5.5F, -5.5F, 12.266F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -24.731F, -33.016F, 60.899F);
        cube(outerShell, "outer_shell_44", 0, 13, -5.5F, -5.5F, 12.073F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 167.54F, -3.596F, -36.676F);
        cube(outerShell, "outer_shell_45", 0, 13, -5.5F, -5.5F, 12.703F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -2.466F, 46.765F, -27.083F);
        cube(outerShell, "outer_shell_46", 0, 13, -5.5F, -5.5F, 12.479F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -94.375F, 50.689F, 102.312F);
        cube(outerShell, "outer_shell_47", 0, 13, -5.5F, -5.5F, 12.678F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -128.71F, -2.871F, -112.631F);
        cube(outerShell, "outer_shell_48", 0, 13, -5.5F, -5.5F, 12.572F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 4.429F, -22.746F, 65.249F);
        cube(outerShell, "outer_shell_49", 0, 13, -5.5F, -5.5F, 12.757F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 147.289F, 23.964F, -21.929F);
        cube(outerShell, "outer_shell_50", 0, 13, -5.5F, -5.5F, 12.42F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 68.26F, -52.157F, 80.325F);
        cube(outerShell, "outer_shell_51", 0, 13, -5.5F, -5.5F, 12.884F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 23.085F, -59.757F, 4.075F);
        cube(outerShell, "outer_shell_52", 0, 13, -5.5F, -5.5F, 12.525F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -156.855F, 26.819F, 164.42F);
        cube(outerShell, "outer_shell_53", 0, 13, -5.5F, -5.5F, 12.529F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 27.005F, 30.515F, -12.701F);
        cube(outerShell, "outer_shell_54", 0, 13, -5.5F, -5.5F, 12.922F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 134.012F, -58.907F, -95.312F);
        cube(outerShell, "outer_shell_55", 0, 13, -5.5F, -5.5F, 12.315F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 112.126F, -38.713F, 38.054F);
        cube(outerShell, "outer_shell_56", 0, 13, -5.5F, -5.5F, 12.713F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -8.401F, 43.853F, -133.311F);
        cube(outerShell, "outer_shell_57", 0, 13, -5.5F, -5.5F, 12.235F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -141.069F, 14.084F, 145.83F);
        cube(outerShell, "outer_shell_58", 0, 13, -5.5F, -5.5F, 12.113F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -66.975F, 14.376F, -127.368F);
        cube(outerShell, "outer_shell_59", 0, 13, -5.5F, -5.5F, 12.362F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -82.794F, -2.802F, 132.05F);
        cube(outerShell, "outer_shell_60", 0, 13, -5.5F, -5.5F, 12.566F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -166.773F, -55.843F, -75.871F);
        cube(outerShell, "outer_shell_61", 0, 13, -5.5F, -5.5F, 12.36F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -46.002F, -8.623F, 176.576F);
        cube(outerShell, "outer_shell_62", 0, 13, -5.5F, -5.5F, 12.878F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -163.564F, 62.419F, 73.508F);
        cube(outerShell, "outer_shell_63", 0, 13, -5.5F, -5.5F, 12.408F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -90.231F, 28.099F, -139.7F);
        cube(outerShell, "outer_shell_64", 0, 13, -5.5F, -5.5F, 12.656F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 67.3F, -8.821F, -21.657F);
        cube(outerShell, "outer_shell_65", 0, 13, -5.5F, -5.5F, 12.722F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -141.051F, -47.676F, -131.885F);
        cube(outerShell, "outer_shell_66", 0, 13, -5.5F, -5.5F, 12.372F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -61.884F, 29.816F, -147.147F);
        cube(outerShell, "outer_shell_67", 0, 13, -5.5F, -5.5F, 12.465F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -92.942F, 36.255F, 153.661F);
        cube(outerShell, "outer_shell_68", 0, 13, -5.5F, -5.5F, 12.856F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 115.26F, 60.924F, 41.699F);
        cube(outerShell, "outer_shell_69", 0, 13, -5.5F, -5.5F, 12.079F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, -69.684F, 39.828F, -171.523F);
        cube(outerShell, "outer_shell_70", 0, 13, -5.5F, -5.5F, 12.237F, 11F, 11F, 1F, 0F, 0F, 0F, 0F, 104.734F, -55.553F, -18.388F);

        PartDefinition tonguesA = group(root, "tongues_a", 0F, 24F, 0F);
        cube(tonguesA, "tongues_a_01a", 24, 13, -3F, 10.431F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, 20.086F, 51.807F, -7.039F);
        cube(tonguesA, "tongues_a_01b", 24, 13, -3F, 10.431F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, 164.881F, 35.5F, 148.01F);
        cube(tonguesA, "tongues_a_02a", 24, 13, -3F, 9.741F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, 174.802F, 43.188F, -148.127F);
        cube(tonguesA, "tongues_a_02b", 24, 13, -3F, 9.741F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, 174.487F, -46.562F, -140.556F);
        cube(tonguesA, "tongues_a_03a", 24, 13, -3F, 10.488F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, -138.471F, 18.507F, 158.181F);
        cube(tonguesA, "tongues_a_03b", 24, 13, -3F, 10.488F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, -116.788F, -45.228F, 87.899F);
        cube(tonguesA, "tongues_a_04a", 24, 13, -3F, 10.032F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, 77.315F, -33.039F, -80.709F);
        cube(tonguesA, "tongues_a_04b", 24, 13, -3F, 10.032F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, 56.311F, 10.607F, 2.295F);
        cube(tonguesA, "tongues_a_05a", 24, 13, -3F, 9.544F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, -105.833F, 47.3F, -47.043F);
        cube(tonguesA, "tongues_a_05b", 24, 13, -3F, 9.544F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, -138.403F, -10.662F, -125.27F);
        cube(tonguesA, "tongues_a_06a", 24, 13, -3F, 10.246F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, -7.31F, -8.213F, -78.331F);
        cube(tonguesA, "tongues_a_06b", 24, 13, -3F, 10.246F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, -41.398F, 79.022F, -120.255F);
        cube(tonguesA, "tongues_a_07a", 24, 13, -3F, 9.832F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, 69.691F, 38.177F, 137.37F);
        cube(tonguesA, "tongues_a_07b", 24, 13, -3F, 9.832F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, 129.976F, 15.834F, -119.746F);
        cube(tonguesA, "tongues_a_08a", 24, 13, -3F, 9.548F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, -86.781F, -8.09F, -115.029F);
        cube(tonguesA, "tongues_a_08b", 24, 13, -3F, 9.548F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, -81.898F, 3.187F, 155.424F);
        cube(tonguesA, "tongues_a_09a", 24, 13, -3F, 10.557F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, 97.161F, 43.952F, 0.786F);
        cube(tonguesA, "tongues_a_09b", 24, 13, -3F, 10.557F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, 134.176F, -5.149F, 85.802F);
        cube(tonguesA, "tongues_a_10a", 24, 13, -3F, 9.586F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, -172.658F, 0.138F, -72.937F);
        cube(tonguesA, "tongues_a_10b", 24, 13, -3F, 9.586F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, -91.076F, -82.657F, -161.87F);
        cube(tonguesA, "tongues_a_11a", 24, 13, -3F, 10.321F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, -150.788F, -33.912F, 33.777F);
        cube(tonguesA, "tongues_a_11b", 24, 13, -3F, 10.321F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, -35.978F, -46.415F, -101.159F);
        cube(tonguesA, "tongues_a_12a", 24, 13, -3F, 9.82F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, 124.483F, 21.08F, -51.535F);
        cube(tonguesA, "tongues_a_12b", 24, 13, -3F, 9.82F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, 115.062F, -31.889F, 24.589F);
        cube(tonguesA, "tongues_a_13a", 24, 13, -3F, 10.444F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, -135.869F, 26.628F, 1.959F);
        cube(tonguesA, "tongues_a_13b", 24, 13, -3F, 10.444F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, -125.756F, -39.913F, -63.244F);
        cube(tonguesA, "tongues_a_14a", 24, 13, -3F, 9.887F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, 171.652F, -33.9F, 24.086F);
        cube(tonguesA, "tongues_a_14b", 24, 13, -3F, 9.887F, -0.5F, 6F, 13F, 1F, 0F, 0F, 0F, 0F, 12.191F, -55.207F, -170.653F);

        PartDefinition tonguesB = group(root, "tongues_b", 0F, 24F, 0F);
        cube(tonguesB, "tongues_b_01a", 38, 13, -2F, 10.968F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 167.034F, -72.903F, -155.934F);
        cube(tonguesB, "tongues_b_01b", 38, 13, -2F, 10.968F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 3.948F, -16.648F, 10.523F);
        cube(tonguesB, "tongues_b_02a", 38, 13, -2F, 11.562F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -40.795F, -27.226F, 3.337F);
        cube(tonguesB, "tongues_b_02b", 38, 13, -2F, 11.562F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -51.78F, 42.312F, -58.734F);
        cube(tonguesB, "tongues_b_03a", 38, 13, -2F, 10.733F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 36.861F, 30.226F, -1.7F);
        cube(tonguesB, "tongues_b_03b", 38, 13, -2F, 10.733F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 134.164F, 43.734F, 122.178F);
        cube(tonguesB, "tongues_b_04a", 38, 13, -2F, 10.593F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -141.305F, -46.769F, -159.239F);
        cube(tonguesB, "tongues_b_04b", 38, 13, -2F, 10.593F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -30.443F, -32.316F, 68.472F);
        cube(tonguesB, "tongues_b_05a", 38, 13, -2F, 10.76F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -28.634F, 55.389F, -81.434F);
        cube(tonguesB, "tongues_b_05b", 38, 13, -2F, 10.76F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -161.7F, 29.903F, 132.126F);
        cube(tonguesB, "tongues_b_06a", 38, 13, -2F, 10.756F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 125.588F, -11.297F, -111.614F);
        cube(tonguesB, "tongues_b_06b", 38, 13, -2F, 10.756F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 76.199F, -34.797F, -13.635F);
        cube(tonguesB, "tongues_b_07a", 38, 13, -2F, 11.308F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -106.501F, -13.608F, 159.62F);
        cube(tonguesB, "tongues_b_07b", 38, 13, -2F, 11.308F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -75.831F, -16.025F, 65.633F);
        cube(tonguesB, "tongues_b_08a", 38, 13, -2F, 10.713F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 78.063F, -56.365F, -158.335F);
        cube(tonguesB, "tongues_b_08b", 38, 13, -2F, 10.713F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 33.06F, 6.578F, -78.317F);
        cube(tonguesB, "tongues_b_09a", 38, 13, -2F, 11.271F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 166.257F, -44.959F, -83.821F);
        cube(tonguesB, "tongues_b_09b", 38, 13, -2F, 11.271F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 13.382F, -43.421F, 77.088F);
        cube(tonguesB, "tongues_b_10a", 38, 13, -2F, 11.046F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -120.757F, -22.073F, 60.784F);
        cube(tonguesB, "tongues_b_10b", 38, 13, -2F, 11.046F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -64.738F, -28.289F, -41.822F);
        cube(tonguesB, "tongues_b_11a", 38, 13, -2F, 11.09F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 92.439F, 9.823F, -104.152F);
        cube(tonguesB, "tongues_b_11b", 38, 13, -2F, 11.09F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 99.831F, -2.404F, -14.569F);
        cube(tonguesB, "tongues_b_12a", 38, 13, -2F, 11.235F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -95.858F, 43.624F, 11.979F);
        cube(tonguesB, "tongues_b_12b", 38, 13, -2F, 11.235F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -133.774F, -4.237F, -73.972F);
        cube(tonguesB, "tongues_b_13a", 38, 13, -2F, 11.506F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 179.473F, 39.003F, 62.329F);
        cube(tonguesB, "tongues_b_13b", 38, 13, -2F, 11.506F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 179.349F, -50.994F, 63.166F);
        cube(tonguesB, "tongues_b_14a", 38, 13, -2F, 10.936F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 76.119F, 60.769F, -165.403F);
        cube(tonguesB, "tongues_b_14b", 38, 13, -2F, 10.936F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 151.487F, 6.728F, -63.234F);
        cube(tonguesB, "tongues_b_15a", 38, 13, -2F, 11F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -78.999F, 34.644F, 131.55F);
        cube(tonguesB, "tongues_b_15b", 38, 13, -2F, 11F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -125.142F, 9.032F, 35.244F);
        cube(tonguesB, "tongues_b_16a", 38, 13, -2F, 10.611F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 103.657F, -57.434F, 100.655F);
        cube(tonguesB, "tongues_b_16b", 38, 13, -2F, 10.611F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, 31.825F, -7.302F, -157.772F);
        cube(tonguesB, "tongues_b_17a", 38, 13, -2F, 10.627F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -163.074F, -60.128F, -45.104F);
        cube(tonguesB, "tongues_b_17b", 38, 13, -2F, 10.627F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -9.493F, -28.457F, 154.235F);
        cube(tonguesB, "tongues_b_18a", 38, 13, -2F, 10.879F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -9.265F, -50.98F, -150.143F);
        cube(tonguesB, "tongues_b_18b", 38, 13, -2F, 10.879F, -0.5F, 4F, 8F, 1F, 0F, 0F, 0F, 0F, -7.433F, 38.417F, -162.001F);

        PartDefinition embers = group(root, "embers", 0F, 24F, 0F);
        cube(embers, "ember_01", 48, 13, -13.938F, -17.971F, -16.728F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 335.617F, 80.924F, 296.274F);
        cube(embers, "ember_02", 48, 13, 22.398F, -13.535F, 3.988F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 204.603F, 346.957F, 186.587F);
        cube(embers, "ember_03", 48, 13, -0.114F, 19.999F, 9.331F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 306.907F, 205.912F, 87.173F);
        cube(embers, "ember_04", 48, 13, -13.796F, 14.009F, -4.85F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 51.49F, 99.575F, 280.245F);
        cube(embers, "ember_05", 48, 13, -30.6F, 8.101F, -11.401F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 236.771F, 300.977F, 158.777F);
        cube(embers, "ember_06", 48, 13, 3.156F, 9.126F, 18.188F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 203.241F, 45.274F, 116.35F);
        cube(embers, "ember_07", 48, 13, 7.957F, -5.403F, 17.475F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 50.993F, 203.933F, 256.059F);
        cube(embers, "ember_08", 48, 13, 19.583F, 0.583F, -21.375F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 56.549F, 335.916F, 46.304F);
        cube(embers, "ember_09", 48, 13, -3.404F, 8.639F, -23.574F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 278.78F, 280.79F, 232.867F);
        cube(embers, "ember_10", 48, 13, -5.756F, -23.199F, -1.221F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 184.452F, 298.064F, 197.282F);
        cube(embers, "ember_11", 48, 13, 3.04F, 6.127F, -19.786F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 65.367F, 30.534F, 116.66F);
        cube(embers, "ember_12", 48, 13, -8.685F, 4.076F, -31.638F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 117.021F, 190.888F, 14.875F);
        cube(embers, "ember_13", 48, 13, 3.924F, -20.161F, -24.848F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 347.854F, 232.578F, 80.546F);
        cube(embers, "ember_14", 48, 13, -25.208F, -0.51F, 3.773F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 342.251F, 226.065F, 329.65F);
        cube(embers, "ember_15", 48, 13, -24.927F, 0.405F, -1.997F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 168.262F, 41.888F, 300.861F);
        cube(embers, "ember_16", 48, 13, 6.058F, 19.498F, 1.732F, 2F, 2F, 2F, 0F, 0F, 0F, 0F, 246.51F, 30.665F, 91.596F);

        return LayerDefinition.create(mesh, 64, 27);
    }

    private static PartDefinition group(PartDefinition root, String name, float x, float y, float z) {
        return root.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(x, y, z));
    }

    /** Cube (boite relative a son pivot) place a (ox,oy,oz) du groupe, avec rotation en degres. */
    private static void cube(PartDefinition group, String name, int u, int v,
                             float x, float y, float z, float dx, float dy, float dz, float inflate,
                             float ox, float oy, float oz, float rotX, float rotY, float rotZ) {
        group.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, dx, dy, dz, new CubeDeformation(inflate)),
                PartPose.offsetAndRotation(ox, oy, oz,
                        (float) Math.toRadians(rotX), (float) Math.toRadians(rotY), (float) Math.toRadians(rotZ)));
    }
}
