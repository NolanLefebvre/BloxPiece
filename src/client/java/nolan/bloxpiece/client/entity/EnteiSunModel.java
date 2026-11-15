package nolan.bloxpiece.client.entity;

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
 * Modèle de Dai Enkai: Entei : un soleil facetté fait de couches de plaques (noyau blanc-jaune, plaques jaunes puis
 * orange, croûte rouge sombre), entouré de pointes de plasma plates et de braises. Généré d'après le visuel de référence.
 *
 * 6 groupes centrés sur le soleil, qui tournent à des vitesses différentes :
 * - core : le noyau incandescent
 * - inner_layers : plaques internes très chaudes
 * - fire_layers : croûte rouge sombre + plaques orange (le corps visible)
 * - plasma_rays : pointes de plasma plates et fines stries
 * - embers : braises qui flottent autour
 *
 * La texture fait 256x512 ; chaque taille de cube a sa propre zone dans la texture (voir dai_enkai_entei.png).
 */
public class EnteiSunModel extends EntityModel<EnteiSunRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("entei_sun"), "main");

    private static final float[] CORE_SPEED = {0.03F, 0.07F, 0.02F};
    private static final float[] INNER_SPEED = {-0.05F, 0.04F, -0.03F};
    private static final float[] FIRE_SPEED = {-0.02F, -0.03F, 0.015F};
    private static final float[] RAYS_SPEED = {0.012F, 0.05F, -0.03F};
    private static final float[] EMBERS_SPEED = {0.015F, -0.035F, 0.02F};

    private final ModelPart core;
    private final ModelPart innerLayers;
    private final ModelPart fireLayers;
    private final ModelPart plasmaRays;
    private final ModelPart embers;

    public EnteiSunModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        this.core = root.getChild("core");
        this.innerLayers = root.getChild("inner_layers");
        this.fireLayers = root.getChild("fire_layers");
        this.plasmaRays = root.getChild("plasma_rays");
        this.embers = root.getChild("embers");
    }

    @Override
    public void setupAnim(EnteiSunRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        spin(this.core, CORE_SPEED, age);
        spin(this.innerLayers, INNER_SPEED, age);
        spin(this.fireLayers, FIRE_SPEED, age);
        spin(this.plasmaRays, RAYS_SPEED, age);
        spin(this.embers, EMBERS_SPEED, age);

        // Les pointes de plasma vibrent légèrement : cela évite un contour trop rigide en jeu.
        this.plasmaRays.y = 24.0F + (float) Math.sin(age * 0.22F) * 0.65F;

        // Les braises flottent indépendamment du corps principal.
        this.embers.y = 24.0F + (float) Math.sin(age * 0.15F) * 1.5F;
    }

    private static void spin(ModelPart group, float[] speed, float age) {
        group.xRot = age * speed[0];
        group.yRot = age * speed[1];
        group.zRot = age * speed[2];
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ---- groupe "core" : noyau incandescent blanc-jaune (3 cubes, comme dans le .bbmodel)
        PartDefinition core = group(root, "core");
        cube(core, "core_center", 89, 0, -9.5F, -9.5F, -9.5F, 19F, 19F, 19F, 0F, 0F, 0F);
        cube(core, "core_rot_xy", 89, 0, -9.5F, -9.5F, -9.5F, 19F, 19F, 19F, 45F, 45F, 0F);
        cube(core, "core_rot_yz", 89, 0, -9.5F, -9.5F, -9.5F, 19F, 19F, 19F, 0F, 45F, 45F);

        // ---- groupe "inner_layers" : couche interne de plaques très chaudes (jaune vif)
        PartDefinition inner_layers = group(root, "inner_layers");
        cubeAt(inner_layers, "p_in_01", 54, 56, 5.884F, 1.23F, -3.892F, 9F, 9F, 2F, -109.605F, -54.64F, -77.866F);
        cubeAt(inner_layers, "p_in_02", 95, 40, -3.43F, -1.533F, -6.122F, 7F, 11F, 2F, 163.273F, 35.953F, 7.985F);
        cubeAt(inner_layers, "p_in_03", 77, 56, 4.962F, -2.818F, 3.409F, 11F, 8F, 2F, 62.711F, -7.25F, 80.879F);
        cubeAt(inner_layers, "p_in_04", 54, 56, -6.632F, 6.37F, 1.53F, 9F, 9F, 2F, 93.152F, 73.342F, -135.723F);
        cubeAt(inner_layers, "p_in_05", 77, 56, -5.371F, -4.037F, 2.806F, 11F, 8F, 2F, 15.546F, -47.487F, 21.605F);
        cubeAt(inner_layers, "p_in_06", 54, 56, 3.706F, -5.511F, -4.781F, 9F, 9F, 2F, 165.076F, 18.676F, 86.496F);
        cubeAt(inner_layers, "p_in_07", 77, 56, 2.256F, 6.807F, 0.702F, 11F, 8F, 2F, 95.613F, -7.565F, 163.032F);
        cubeAt(inner_layers, "p_in_08", 54, 56, -7.488F, 1.922F, -2.296F, 9F, 9F, 2F, -120.585F, -3.876F, 55.178F);
        cubeAt(inner_layers, "p_in_09", 77, 56, -2.776F, 1.617F, 8.738F, 11F, 8F, 2F, 21.736F, 7.016F, -140.872F);
        cubeAt(inner_layers, "p_in_10", 54, 56, 2.462F, -6.454F, 1.994F, 9F, 9F, 2F, -42.207F, -44.158F, 135.921F);
        cubeAt(inner_layers, "p_in_11", 77, 56, 1.578F, 4.722F, -5.022F, 11F, 8F, 2F, -139.494F, 10.831F, -68.327F);
        cubeAt(inner_layers, "p_in_12", 77, 56, 4.845F, 4.569F, 6.759F, 11F, 8F, 2F, -33.636F, 38.81F, -5.312F);

        // ---- groupe "fire_layers" : croûte rouge sombre + couches de plaques orange / jaune qui forment le corps facetté du soleil
        PartDefinition fire_layers = group(root, "fire_layers");
        cube(fire_layers, "fire_crust_1", 0, 0, -14F, -11.5F, -8F, 28F, 23F, 16F, -35F, 20F, 40F);
        cube(fire_layers, "fire_crust_2", 0, 0, -14F, -11.5F, -8F, 28F, 23F, 16F, 40F, -35F, 20F);
        cube(fire_layers, "fire_crust_3", 0, 0, -14F, -11.5F, -8F, 28F, 23F, 16F, 20F, 40F, -35F);
        cube(fire_layers, "fire_crust_4", 0, 0, -14F, -11.5F, -8F, 28F, 23F, 16F, 35F, 20F, -40F);
        cube(fire_layers, "fire_crust_5", 0, 0, -14F, -11.5F, -8F, 28F, 23F, 16F, -40F, -35F, -20F);
        cubeAt(fire_layers, "p_mid_01", 41, 40, 2.361F, 13.276F, 0.182F, 10F, 13F, 2F, 96.556F, 45.694F, 177.87F);
        cubeAt(fire_layers, "p_mid_02", 176, 40, -5.308F, -7.231F, -5.955F, 11F, 11F, 2F, -123.054F, -2.336F, 171.562F);
        cubeAt(fire_layers, "p_mid_03", 0, 56, -12.666F, 2.796F, 3.478F, 13F, 9F, 2F, -62.808F, -43.694F, 61.287F);
        cubeAt(fire_layers, "p_mid_04", 176, 40, 6.522F, -9.646F, -1.028F, 11F, 11F, 2F, 77.586F, -44.484F, 53.616F);
        cubeAt(fire_layers, "p_mid_05", 41, 40, -10.556F, -1.564F, -5.791F, 10F, 13F, 2F, 127.671F, -42.626F, -130.42F);
        cubeAt(fire_layers, "p_mid_06", 176, 40, 11.049F, 0.295F, 1.969F, 11F, 11F, 2F, -101.303F, 37.707F, -119.351F);
        cubeAt(fire_layers, "p_mid_07", 41, 40, -1.21F, -8.218F, 9.883F, 10F, 13F, 2F, -33.178F, 16.413F, -146.461F);
        cubeAt(fire_layers, "p_mid_08", 176, 40, 0.194F, 8.963F, -6.432F, 11F, 11F, 2F, -140.002F, -33.787F, 52.459F);
        cubeAt(fire_layers, "p_mid_09", 0, 56, -3.506F, 5.805F, 10.932F, 13F, 9F, 2F, 18.698F, -3.058F, -130.855F);
        cubeAt(fire_layers, "p_mid_10", 0, 56, -2.334F, 11.124F, 2.491F, 13F, 9F, 2F, -47.025F, 77.063F, 50.516F);
        cubeAt(fire_layers, "p_mid_11", 176, 40, -11.081F, -7.487F, 0.623F, 11F, 11F, 2F, 104.133F, -53.502F, -83.502F);
        cubeAt(fire_layers, "p_mid_12", 176, 40, -6.723F, -0.826F, -9.09F, 11F, 11F, 2F, 157.545F, 41.694F, -18.905F);
        cubeAt(fire_layers, "p_mid_13", 0, 56, -3.266F, -3.54F, -11.212F, 13F, 9F, 2F, -178.053F, -4.901F, 24.632F);
        cubeAt(fire_layers, "p_mid_14", 176, 40, 11.593F, -1.765F, -0.064F, 11F, 11F, 2F, 84.375F, -49.645F, 70.796F);
        cubeAt(fire_layers, "p_mid_15", 176, 40, 5.873F, 6.802F, 8.08F, 11F, 11F, 2F, 33.997F, -45.121F, 178.417F);
        cubeAt(fire_layers, "p_mid_16", 0, 56, -0.938F, -9.256F, -3.851F, 13F, 9F, 2F, -117.589F, 66.733F, 177.647F);
        cubeAt(fire_layers, "p_mid_17", 176, 40, 6.881F, -7.808F, -5.278F, 11F, 11F, 2F, 121.019F, -40.279F, 18.929F);
        cubeAt(fire_layers, "p_mid_18", 0, 56, -6.468F, 7.805F, 0.254F, 13F, 9F, 2F, -101.869F, 79.98F, 25.329F);
        cubeAt(fire_layers, "p_mid_19", 41, 40, 7.561F, -5.164F, 6.021F, 10F, 13F, 2F, 74.077F, -21.704F, 35.759F);
        cubeAt(fire_layers, "p_mid_20", 0, 56, 5.328F, 3.325F, -11.329F, 13F, 9F, 2F, -144.142F, -33.146F, 2.647F);
        cubeAt(fire_layers, "p_mid_21", 0, 56, 4.337F, 3.819F, 10.304F, 13F, 9F, 2F, -29.028F, 32.77F, -46.789F);
        cubeAt(fire_layers, "p_mid_22", 176, 40, -6.039F, -3.633F, 10.518F, 11F, 11F, 2F, -42.744F, 6.963F, 125F);
        cubeAt(fire_layers, "p_mid_23", 0, 56, -4.329F, -11.645F, 1.843F, 13F, 9F, 2F, -70.397F, -66.825F, 141.541F);
        cubeAt(fire_layers, "p_mid_24", 41, 40, 8.691F, 7.347F, -6.704F, 10F, 13F, 2F, 132.319F, 78.811F, 154.153F);
        cubeAt(fire_layers, "p_out_01", 166, 0, 2.163F, -8.62F, -12.29F, 11F, 16F, 2F, -178.051F, 32.704F, 126.774F);
        cubeAt(fire_layers, "p_out_02", 166, 0, 11.49F, -4.353F, -10.662F, 11F, 16F, 2F, 158.263F, -40.539F, -7.425F);
        cubeAt(fire_layers, "p_out_03", 0, 40, -11.1F, 13.165F, -2.979F, 18F, 13F, 2F, -116.832F, -80.772F, 65.53F);
        cubeAt(fire_layers, "p_out_04", 114, 40, 4.344F, 3.294F, 13.661F, 15F, 11F, 2F, 65.799F, -6.875F, -96.201F);
        cubeAt(fire_layers, "p_out_05", 66, 40, 11.365F, 7.214F, -7.674F, 12F, 12F, 2F, 156.961F, -21.724F, 71.062F);
        cubeAt(fire_layers, "p_out_06", 31, 56, 11.88F, 5.907F, 5.464F, 9F, 9F, 2F, 60.318F, -30.795F, 137.358F);
        cubeAt(fire_layers, "p_out_07", 31, 56, -2.592F, -12.852F, 6.269F, 9F, 9F, 2F, -36.917F, -45.088F, 126.555F);
        cubeAt(fire_layers, "p_out_08", 114, 40, -9.498F, -9.703F, -3.86F, 15F, 11F, 2F, -106.603F, 40.667F, 135.446F);
        cubeAt(fire_layers, "p_out_09", 66, 40, -14.793F, 3.585F, 6.123F, 12F, 12F, 2F, -45.08F, -32.692F, 52.716F);
        cubeAt(fire_layers, "p_out_10", 166, 0, -10.232F, -7.812F, 7.737F, 11F, 16F, 2F, -37.971F, -19.501F, 83.185F);
        cubeAt(fire_layers, "p_out_11", 66, 40, -12.456F, -4.17F, -9.155F, 12F, 12F, 2F, 106.353F, 7.602F, -62.033F);
        cubeAt(fire_layers, "p_out_12", 166, 0, 13.623F, -4.667F, -2.673F, 11F, 16F, 2F, 129.398F, -69.827F, 33.234F);
        cubeAt(fire_layers, "p_out_13", 114, 40, -3.032F, 10.788F, 9.382F, 15F, 11F, 2F, 33.702F, -38.973F, -119.203F);
        cubeAt(fire_layers, "p_out_14", 166, 0, -0.668F, 11.709F, -7.685F, 11F, 16F, 2F, 29.235F, 73.092F, 58.487F);
        cubeAt(fire_layers, "p_out_15", 0, 40, 16.57F, 2.36F, -2.509F, 18F, 13F, 2F, -115.821F, -10.418F, -83.826F);
        cubeAt(fire_layers, "p_out_16", 114, 40, -8.636F, 14.377F, 3.562F, 15F, 11F, 2F, -24.427F, 51.844F, 68.942F);
        cubeAt(fire_layers, "p_out_17", 66, 40, 5.343F, -9.619F, 11.256F, 12F, 12F, 2F, -28.826F, -37.367F, 160.962F);
        cubeAt(fire_layers, "p_out_18", 66, 40, -16.959F, -0.465F, 1.817F, 12F, 12F, 2F, 84.809F, 39.805F, -85.884F);
        cubeAt(fire_layers, "p_out_19", 114, 40, 3.074F, 15.437F, -1.947F, 15F, 11F, 2F, 86.447F, 74.579F, 159.222F);
        cubeAt(fire_layers, "p_out_20", 114, 40, -0.106F, -15.449F, -4.509F, 15F, 11F, 2F, -149.699F, -62.985F, -122.859F);
        cubeAt(fire_layers, "p_out_21", 31, 56, 2.973F, -15.129F, 1.399F, 9F, 9F, 2F, 131.554F, 80.485F, 64.447F);
        cubeAt(fire_layers, "p_out_22", 166, 0, 5.977F, 5.292F, -14.337F, 11F, 16F, 2F, 132.789F, 27.618F, 120.799F);
        cubeAt(fire_layers, "p_out_23", 31, 56, -0.953F, 0.634F, -14.261F, 9F, 9F, 2F, 156.032F, -13.763F, 137.889F);
        cubeAt(fire_layers, "p_out_24", 66, 40, 8.395F, -11.62F, -6.28F, 12F, 12F, 2F, 169.157F, -47.633F, -61.641F);
        cubeAt(fire_layers, "p_out_25", 166, 0, -12.634F, 3.778F, -5.309F, 11F, 16F, 2F, -111.402F, 5.756F, 69.599F);
        cubeAt(fire_layers, "p_out_26", 166, 0, -4.738F, 9.167F, -13.635F, 11F, 16F, 2F, -93.439F, 10.033F, -47.928F);
        cubeAt(fire_layers, "p_out_27", 66, 40, -7.811F, 4.132F, 14.682F, 12F, 12F, 2F, -20.853F, 46.094F, 149.202F);
        cubeAt(fire_layers, "p_out_28", 66, 40, 1.74F, -4.137F, 15.928F, 12F, 12F, 2F, -10.158F, -22.195F, 137.732F);
        cubeAt(fire_layers, "p_out_29", 114, 40, 11.707F, -3.144F, 9.045F, 15F, 11F, 2F, 42.523F, -26.94F, 100.417F);
        cubeAt(fire_layers, "p_out_30", 66, 40, 2.734F, 12.001F, 7.126F, 12F, 12F, 2F, -33.033F, 67.006F, 26.411F);
        cubeAt(fire_layers, "p_hot_01", 203, 40, 4.344F, 0.004F, 15.82F, 13F, 10F, 2F, 8.382F, 13.448F, 32.806F);
        cubeAt(fire_layers, "p_hot_02", 203, 40, 15.116F, -0.217F, -5.799F, 13F, 10F, 2F, -122.022F, -13.885F, -104.171F);
        cubeAt(fire_layers, "p_hot_03", 193, 0, -9.356F, -11.713F, 2.424F, 9F, 13F, 2F, -40.478F, -58.436F, 90.235F);
        cubeAt(fire_layers, "p_hot_04", 203, 40, -6.119F, 8.878F, -12.389F, 13F, 10F, 2F, 137.889F, -8.535F, 179.202F);
        cubeAt(fire_layers, "p_hot_05", 203, 40, 8.076F, -7.595F, -9.552F, 13F, 10F, 2F, 110.08F, 25.867F, 64.783F);
        cubeAt(fire_layers, "p_hot_06", 193, 0, 5.411F, 2.25F, -14.177F, 9F, 13F, 2F, -163.578F, 10.434F, -64.902F);
        cubeAt(fire_layers, "p_hot_07", 193, 0, -0.083F, 12.372F, -9.444F, 9F, 13F, 2F, -99.917F, -53.542F, 8.243F);
        cubeAt(fire_layers, "p_hot_08", 203, 40, 10.848F, -9.332F, 1.755F, 13F, 10F, 2F, -61.32F, 63.318F, -111.361F);
        cubeAt(fire_layers, "p_hot_09", 149, 40, 6.068F, 11.166F, 5.197F, 11F, 11F, 2F, 77.525F, 29.057F, 152.414F);
        cubeAt(fire_layers, "p_hot_10", 193, 0, 13.383F, 3.745F, 6.152F, 9F, 13F, 2F, -84.206F, -7.412F, -95.314F);
        cubeAt(fire_layers, "p_hot_11", 203, 40, 3.047F, 13.818F, 1.095F, 13F, 10F, 2F, -98.158F, -2.494F, -3.052F);
        cubeAt(fire_layers, "p_hot_12", 203, 40, -3.475F, -13.829F, -1.542F, 13F, 10F, 2F, -117.823F, 25.307F, 148.251F);
        cubeAt(fire_layers, "p_hot_13", 149, 40, 8.973F, 8.188F, -6.854F, 11F, 11F, 2F, -123.891F, -46.804F, -28.618F);
        cubeAt(fire_layers, "p_hot_14", 203, 40, -4.544F, 9.033F, 9.785F, 13F, 10F, 2F, 43.332F, -4.515F, -121.577F);
        cubeAt(fire_layers, "p_hot_15", 193, 0, 0.347F, -7.589F, 11.435F, 9F, 13F, 2F, -11.474F, -19.946F, -172.683F);
        cubeAt(fire_layers, "p_hot_16", 149, 40, -11.384F, 8.564F, -2.451F, 11F, 11F, 2F, 133.716F, -47.206F, -140.529F);
        cubeAt(fire_layers, "p_hot_17", 149, 40, -3.53F, 14.773F, 5.577F, 11F, 11F, 2F, 47.472F, -54.447F, -108.869F);
        cubeAt(fire_layers, "p_hot_18", 149, 40, -8.516F, -7.094F, -8.685F, 11F, 11F, 2F, -126.271F, -11.03F, 132.42F);
        cubeAt(fire_layers, "p_hot_19", 193, 0, -7.877F, -1.957F, 13.534F, 9F, 13F, 2F, -44.273F, 18.768F, 108.613F);
        cubeAt(fire_layers, "p_hot_20", 149, 40, -0.344F, -13.33F, -8.172F, 11F, 11F, 2F, 109.572F, 15.974F, -25.545F);
        cubeAt(fire_layers, "p_hot_21", 149, 40, -12.745F, -6.678F, 2.454F, 11F, 11F, 2F, -68.665F, 24.273F, 101.744F);
        cubeAt(fire_layers, "p_hot_22", 149, 40, -3.337F, -3.411F, -14.09F, 11F, 11F, 2F, -143.58F, 5.708F, 155.569F);
        cubeAt(fire_layers, "p_hot_23", 203, 40, 3.487F, -14.925F, 5.618F, 13F, 10F, 2F, -57.391F, 80.259F, -135.283F);
        cubeAt(fire_layers, "p_hot_24", 203, 40, -11.552F, 2.936F, -7.148F, 13F, 10F, 2F, 122.613F, 17.873F, -93.088F);
        cubeAt(fire_layers, "p_hot_25", 149, 40, 8.945F, -4.469F, 9.584F, 11F, 11F, 2F, -34.148F, 2.822F, -97.394F);
        cubeAt(fire_layers, "p_hot_26", 203, 40, -14.613F, -0.182F, 0.966F, 13F, 10F, 2F, 95.834F, 18.976F, -87.465F);

        // ---- groupe "plasma_rays" : pointes de plasma fines et courtes + quelques stries qui dépassent
        PartDefinition plasma_rays = group(root, "plasma_rays");
        cube(plasma_rays, "spike_01", 113, 56, 18.267F, -1F, -0.5F, 14F, 2F, 1F, 126.86F, 9.308F, 86.026F);
        cube(plasma_rays, "spike_02", 112, 71, 16.292F, -0.5F, -0.5F, 6F, 1F, 1F, -87.16F, -12.248F, 96.216F);
        cube(plasma_rays, "spike_03", 144, 56, 18.811F, -0.75F, -0.5F, 11F, 1.5F, 1F, 7.277F, 15.887F, 91.98F);
        cube(plasma_rays, "spike_04", 127, 71, 17.381F, -0.5F, -0.5F, 4F, 1F, 1F, -167.112F, -12.999F, 69.124F);
        cube(plasma_rays, "spike_05", 169, 56, 18.905F, -0.75F, -0.5F, 8F, 1.5F, 1F, -84.993F, -0.618F, 113.653F);
        cube(plasma_rays, "spike_06", 144, 56, 16.836F, -0.75F, -0.5F, 11F, 1.5F, 1F, 142.588F, 30.498F, 60.716F);
        cube(plasma_rays, "spike_07", 127, 71, 15.688F, -0.5F, -0.5F, 4F, 1F, 1F, -3.319F, -26.92F, 82.053F);
        cube(plasma_rays, "spike_08", 144, 56, 17.509F, -0.75F, -0.5F, 11F, 1.5F, 1F, -152.838F, 28.781F, 110.462F);
        cube(plasma_rays, "spike_09", 144, 56, 17.617F, -0.75F, -0.5F, 11F, 1.5F, 1F, 131.686F, -13.699F, 66.445F);
        cube(plasma_rays, "spike_10", 169, 56, 15.859F, -0.75F, -0.5F, 8F, 1.5F, 1F, -94.115F, -11.99F, 127.434F);
        cube(plasma_rays, "spike_11", 169, 56, 18.501F, -0.75F, -0.5F, 8F, 1.5F, 1F, 123.196F, 14.111F, 71.422F);
        cube(plasma_rays, "spike_12", 169, 56, 16.382F, -0.75F, -0.5F, 8F, 1.5F, 1F, 103.637F, -40.916F, 84.383F);
        cube(plasma_rays, "spike_13", 112, 71, 17.571F, -0.5F, -0.5F, 6F, 1F, 1F, 15.111F, 24.471F, 126.297F);
        cube(plasma_rays, "spike_14", 144, 56, 16.487F, -0.75F, -0.5F, 11F, 1.5F, 1F, 77.002F, 13.3F, 54.263F);
        cube(plasma_rays, "spike_15", 113, 56, 15.949F, -1F, -0.5F, 14F, 2F, 1F, 45.138F, -45.262F, 118.2F);
        cube(plasma_rays, "spike_16", 127, 71, 18.803F, -0.5F, -0.5F, 4F, 1F, 1F, -55.225F, 49.171F, 92.432F);
        cube(plasma_rays, "spike_17", 169, 56, 16.689F, -0.75F, -0.5F, 8F, 1.5F, 1F, 132.719F, -24.746F, 57.634F);
        cube(plasma_rays, "spike_18", 169, 56, 18.286F, -0.75F, -0.5F, 8F, 1.5F, 1F, 19.056F, -13.181F, 138.738F);
        cube(plasma_rays, "spike_19", 144, 56, 15.651F, -0.75F, -0.5F, 11F, 1.5F, 1F, -24.435F, 34.736F, 36.078F);
        cube(plasma_rays, "spike_20", 112, 71, 18.91F, -0.5F, -0.5F, 6F, 1F, 1F, -41.24F, -72.098F, 89.815F);
        cube(plasma_rays, "spike_21", 169, 56, 16.302F, -0.75F, -0.5F, 8F, 1.5F, 1F, -57.862F, 42.654F, 146.378F);
        cube(plasma_rays, "spike_22", 169, 56, 18.102F, -0.75F, -0.5F, 8F, 1.5F, 1F, 44.132F, -0.119F, 31.761F);
        cube(plasma_rays, "spike_23", 169, 56, 18.152F, -0.75F, -0.5F, 8F, 1.5F, 1F, 132.049F, -39.594F, 150.416F);
        cube(plasma_rays, "spike_24", 112, 71, 18.442F, -0.5F, -0.5F, 6F, 1F, 1F, 12.234F, 54.971F, 68.199F);
        cube(plasma_rays, "spike_25", 112, 71, 17.034F, -0.5F, -0.5F, 6F, 1F, 1F, 62.726F, -42.654F, 46.062F);
        cube(plasma_rays, "spike_26", 127, 71, 18.283F, -0.5F, -0.5F, 4F, 1F, 1F, -18.033F, 17.551F, 161.392F);
        cube(plasma_rays, "spike_27", 144, 56, 17.661F, -0.75F, -0.5F, 11F, 1.5F, 1F, -68.907F, 29.725F, 43.066F);
        cube(plasma_rays, "spike_28", 112, 71, 15.731F, -0.5F, -0.5F, 6F, 1F, 1F, -153.251F, -56.189F, 137.298F);
        cube(plasma_rays, "spike_29", 127, 71, 17F, -0.5F, -0.5F, 4F, 1F, 1F, 152.07F, 65.083F, 101.023F);
        cube(plasma_rays, "spike_30", 127, 71, 18.837F, -0.5F, -0.5F, 4F, 1F, 1F, 63.841F, -22.844F, 21.098F);
        cube(plasma_rays, "spike_31", 112, 71, 15.592F, -0.5F, -0.5F, 6F, 1F, 1F, -16.156F, -29.426F, 171.487F);
        cube(plasma_rays, "spike_32", 144, 56, 17.89F, -0.75F, -0.5F, 11F, 1.5F, 1F, 75.595F, 39.901F, 23.806F);
        cube(plasma_rays, "spike_33", 169, 56, 17.804F, -0.75F, -0.5F, 8F, 1.5F, 1F, -8.878F, -65.404F, 79.524F);
        cube(plasma_rays, "spike_34", 127, 71, 16.646F, -0.5F, -0.5F, 4F, 1F, 1F, -90.079F, 37.152F, 160.411F);
        cube(plasma_rays, "spike_35", 144, 56, 16.78F, -0.75F, -0.5F, 11F, 1.5F, 1F, 41.234F, 8.491F, 5.219F);
        cube(plasma_rays, "spike_36", 127, 71, 16.508F, -0.5F, -0.5F, 4F, 1F, 1F, 113.23F, -43.865F, 150.929F);
        cube(plasma_rays, "spike_37", 169, 56, 18.852F, -0.75F, -0.5F, 8F, 1.5F, 1F, -61.956F, 76.589F, 55.976F);
        cube(plasma_rays, "spike_38", 127, 71, 16.026F, -0.5F, -0.5F, 4F, 1F, 1F, 65.999F, -54.891F, 19.271F);
        cube(plasma_rays, "spike_39", 144, 56, 17.079F, -0.75F, -0.5F, 11F, 1.5F, 1F, -77.203F, 4.307F, 173.304F);
        cube(plasma_rays, "spike_40", 127, 71, 16.993F, -0.5F, -0.5F, 4F, 1F, 1F, -152.809F, 40.932F, 4.899F);
        cube(plasma_rays, "spike_41", 127, 71, 17.379F, -0.5F, -0.5F, 4F, 1F, 1F, 13.818F, -78.283F, 109.782F);
        cube(plasma_rays, "spike_42", 127, 71, 18.056F, -0.5F, -0.5F, 4F, 1F, 1F, -168.395F, 52.613F, 167.7F);
        cube(plasma_rays, "spike_43", 169, 56, 15.932F, -0.75F, -0.5F, 8F, 1.5F, 1F, 141.129F, -4.252F, -6.112F);
        cube(plasma_rays, "spike_44", 144, 56, 17.727F, -0.75F, -0.5F, 11F, 1.5F, 1F, 147.585F, -19.79F, 170.709F);
        cube(plasma_rays, "spike_45", 112, 71, 17.073F, -0.5F, -0.5F, 6F, 1F, 1F, 67.463F, 66.941F, 71.283F);
        cube(plasma_rays, "spike_46", 112, 71, 15.818F, -0.5F, -0.5F, 6F, 1F, 1F, 2.465F, -69.039F, 33.806F);
        cube(plasma_rays, "spike_47", 113, 56, 15.808F, -1F, -0.5F, 14F, 2F, 1F, -19.787F, 20.161F, -166.42F);
        cube(plasma_rays, "spike_48", 112, 71, 15.844F, -0.5F, -0.5F, 6F, 1F, 1F, -138.134F, 15.277F, -7.374F);
        cube(plasma_rays, "spike_49", 127, 71, 16.298F, -0.5F, -0.5F, 4F, 1F, 1F, 6.925F, -51.893F, -156.376F);
        cube(plasma_rays, "spike_50", 169, 56, 16.714F, -0.75F, -0.5F, 8F, 1.5F, 1F, -26.959F, 75.674F, -173.212F);
        cube(plasma_rays, "spike_51", 169, 56, 16.919F, -0.75F, -0.5F, 8F, 1.5F, 1F, 74.304F, -41.495F, -9.367F);
        cube(plasma_rays, "spike_52", 144, 56, 16.571F, -0.75F, -0.5F, 11F, 1.5F, 1F, -109.668F, -3.246F, -176.537F);
        cube(plasma_rays, "spike_53", 144, 56, 15.645F, -0.75F, -0.5F, 11F, 1.5F, 1F, -123.875F, 46.21F, 0.333F);
        cube(plasma_rays, "spike_54", 127, 71, 15.785F, -0.5F, -0.5F, 4F, 1F, 1F, -166.25F, -86.192F, -119.336F);
        cube(plasma_rays, "spike_55", 127, 71, 16.663F, -0.5F, -0.5F, 4F, 1F, 1F, 46.869F, 48.576F, -167.236F);
        cube(plasma_rays, "spike_56", 144, 56, 17.668F, -0.75F, -0.5F, 11F, 1.5F, 1F, 134.981F, 0.086F, -11.117F);
        cube(plasma_rays, "spike_57", 127, 71, 18.598F, -0.5F, -0.5F, 4F, 1F, 1F, 55.224F, -41.251F, -159.018F);
        cube(plasma_rays, "spike_58", 113, 56, 16.866F, -1F, -0.5F, 14F, 2F, 1F, 120.706F, 54.771F, -56.605F);
        cube(plasma_rays, "spike_59", 112, 71, 16.473F, -0.5F, -0.5F, 6F, 1F, 1F, -97.394F, -45.62F, -23.325F);
        cube(plasma_rays, "spike_60", 112, 71, 16.394F, -0.5F, -0.5F, 6F, 1F, 1F, -162.26F, 12.004F, -158.651F);
        cube(plasma_rays, "spike_61", 127, 71, 18.364F, -0.5F, -0.5F, 4F, 1F, 1F, 73.021F, 34.831F, -27.774F);
        cube(plasma_rays, "spike_62", 169, 56, 17.263F, -0.75F, -0.5F, 8F, 1.5F, 1F, -109.454F, -58.268F, -116.432F);
        cube(plasma_rays, "spike_63", 127, 71, 18.77F, -0.5F, -0.5F, 4F, 1F, 1F, -138.413F, 56.233F, -141.812F);
        cube(plasma_rays, "spike_64", 169, 56, 15.66F, -0.75F, -0.5F, 8F, 1.5F, 1F, 79.998F, -28.325F, -12.125F);
        cube(plasma_rays, "spike_65", 127, 71, 15.927F, -0.5F, -0.5F, 4F, 1F, 1F, -168.739F, -29.006F, -158.186F);
        cube(plasma_rays, "spike_66", 112, 71, 15.729F, -0.5F, -0.5F, 6F, 1F, 1F, -178.207F, 52.218F, -35.106F);
        cube(plasma_rays, "spike_67", 144, 56, 16.393F, -0.75F, -0.5F, 11F, 1.5F, 1F, 30.061F, -71.382F, -96.169F);
        cube(plasma_rays, "spike_68", 127, 71, 18.02F, -0.5F, -0.5F, 4F, 1F, 1F, 57.295F, 30.045F, -142.592F);
        cube(plasma_rays, "spike_69", 169, 56, 18.06F, -0.75F, -0.5F, 8F, 1.5F, 1F, 42.797F, 10.224F, -27.13F);
        cube(plasma_rays, "spike_70", 169, 56, 16.489F, -0.75F, -0.5F, 8F, 1.5F, 1F, 75.015F, -42.022F, -124.727F);
        cube(plasma_rays, "spike_71", 112, 71, 16.789F, -0.5F, -0.5F, 6F, 1F, 1F, 104.197F, 59.041F, -104.783F);
        cube(plasma_rays, "spike_72", 144, 56, 17.573F, -0.75F, -0.5F, 11F, 1.5F, 1F, -122.889F, -40.548F, -28.301F);
        cube(plasma_rays, "spike_73", 144, 56, 16.533F, -0.75F, -0.5F, 11F, 1.5F, 1F, -61.492F, -2.016F, -136.381F);
        cube(plasma_rays, "spike_74", 112, 71, 15.519F, -0.5F, -0.5F, 6F, 1F, 1F, 84.003F, 37.621F, -50.338F);
        cube(plasma_rays, "spike_75", 112, 71, 18.057F, -0.5F, -0.5F, 6F, 1F, 1F, 45.394F, -61.671F, -92.29F);
        cube(plasma_rays, "spike_76", 169, 56, 17.967F, -0.75F, -0.5F, 8F, 1.5F, 1F, 24.125F, 34.978F, -119.454F);
        cube(plasma_rays, "spike_77", 127, 71, 16.153F, -0.5F, -0.5F, 4F, 1F, 1F, -38.72F, -13.074F, -53.53F);
        cube(plasma_rays, "spike_78", 144, 56, 16.713F, -0.75F, -0.5F, 11F, 1.5F, 1F, -88.913F, -25.241F, -138.865F);
        cube(plasma_rays, "spike_79", 169, 56, 17.794F, -0.75F, -0.5F, 8F, 1.5F, 1F, -5.162F, 39.325F, -86.512F);
        cube(plasma_rays, "spike_80", 169, 56, 17.756F, -0.75F, -0.5F, 8F, 1.5F, 1F, -6.502F, -27.782F, -66.297F);
        cube(plasma_rays, "spike_81", 144, 56, 15.573F, -0.75F, -0.5F, 11F, 1.5F, 1F, 111.786F, 6.886F, -122.676F);
        cube(plasma_rays, "spike_82", 112, 71, 16.065F, -0.5F, -0.5F, 6F, 1F, 1F, 136.172F, 15.83F, -58.524F);
        cube(plasma_rays, "spike_83", 169, 56, 15.573F, -0.75F, -0.5F, 8F, 1.5F, 1F, -1.265F, -32.799F, -111.878F);
        cube(plasma_rays, "spike_84", 112, 71, 16.401F, -0.5F, -0.5F, 6F, 1F, 1F, 165.259F, 18.397F, -103.304F);
        cube(plasma_rays, "spike_85", 169, 56, 17.381F, -0.75F, -0.5F, 8F, 1.5F, 1F, 128.13F, 1.832F, -53.581F);
        cube(plasma_rays, "spike_86", 127, 71, 17.268F, -0.5F, -0.5F, 4F, 1F, 1F, 131.328F, 2.536F, -103.028F);
        cube(plasma_rays, "spike_87", 169, 56, 17.845F, -0.75F, -0.5F, 8F, 1.5F, 1F, 109.399F, 27.032F, -72.943F);
        cube(plasma_rays, "spike_88", 169, 56, 16.307F, -0.75F, -0.5F, 8F, 1.5F, 1F, 96.111F, -25.583F, -96.358F);
        cube(plasma_rays, "spike_89", 169, 56, 17.333F, -0.75F, -0.5F, 8F, 1.5F, 1F, 75.937F, 20.933F, -97.227F);
        cube(plasma_rays, "spike_90", 113, 56, 17.051F, -1F, -0.5F, 14F, 2F, 1F, -105.825F, 3.914F, -91.57F);
        cube(plasma_rays, "streak_1", 0, 71, -26F, -0.5F, -0.5F, 52F, 1F, 1F, -176.807F, -5.801F, 61.791F);
        cube(plasma_rays, "streak_2", 0, 71, -26F, -0.5F, -0.5F, 52F, 1F, 1F, -50.835F, -31.642F, 134.915F);
        cube(plasma_rays, "streak_3", 95, 68, -29F, -0.5F, -0.5F, 58F, 1F, 1F, -147.993F, 42.928F, 107.524F);
        cube(plasma_rays, "streak_4", 0, 71, -26F, -0.5F, -0.5F, 52F, 1F, 1F, 135.042F, -64.856F, 34.69F);
        cube(plasma_rays, "streak_5", 0, 68, -23F, -0.5F, -0.5F, 46F, 1F, 1F, -95.421F, 11.569F, 164.908F);
        cube(plasma_rays, "streak_6", 0, 71, -26F, -0.5F, -0.5F, 52F, 1F, 1F, -134.001F, 26.692F, 23.053F);

        // ---- groupe "embers" : braises : petits cubes qui flottent autour
        PartDefinition embers = group(root, "embers");
        cubeAt(embers, "ember_01", 104, 56, 1.528F, 20.261F, -2.834F, 2F, 2F, 2F, 126.856F, 6.144F, -155.295F);
        cubeAt(embers, "ember_02", 107, 71, -5.206F, 9.654F, 22.886F, 1F, 1F, 1F, 167.95F, -17.303F, 175.505F);
        cubeAt(embers, "ember_03", 104, 56, 1.898F, 18.096F, -3.618F, 2F, 2F, 2F, -39.994F, -22.862F, -120.831F);
        cubeAt(embers, "ember_04", 107, 71, 4.547F, 19.088F, 6.875F, 1F, 1F, 1F, -173.706F, -46.562F, 54.125F);
        cubeAt(embers, "ember_05", 104, 56, -8.264F, 18.908F, -2.718F, 2F, 2F, 2F, -157.08F, -7.858F, -114.703F);
        cubeAt(embers, "ember_06", 104, 56, -0.861F, 25.264F, -14.9F, 2F, 2F, 2F, 168.652F, -52.959F, 26.417F);
        cubeAt(embers, "ember_07", 107, 71, 1.725F, 11.551F, 20.204F, 1F, 1F, 1F, -121.338F, 49.368F, -138.627F);
        cubeAt(embers, "ember_08", 104, 56, -9.506F, 15.427F, -11.696F, 2F, 2F, 2F, 172.965F, 63.772F, 152.763F);
        cubeAt(embers, "ember_09", 104, 56, 20.673F, 9.394F, 18.38F, 2F, 2F, 2F, -98.294F, 26.398F, 100.647F);
        cubeAt(embers, "ember_10", 104, 56, -14.842F, -11.07F, 12.703F, 2F, 2F, 2F, -69.597F, -6.867F, 87.604F);
        cubeAt(embers, "ember_11", 104, 56, 16.45F, 7.553F, -12.75F, 2F, 2F, 2F, 61.629F, 81.008F, -174.182F);
        cubeAt(embers, "ember_12", 104, 56, 8.395F, 6.141F, 23.005F, 2F, 2F, 2F, 151.549F, -58.997F, 79.189F);
        cubeAt(embers, "ember_13", 107, 71, -25.359F, 6.845F, -6.347F, 1F, 1F, 1F, -24.242F, 11.142F, -43.047F);
        cubeAt(embers, "ember_14", 107, 71, 24.638F, 11.445F, -2.932F, 1F, 1F, 1F, 147.435F, 26.082F, 9.174F);
        cubeAt(embers, "ember_15", 104, 56, -7F, 3.614F, 24.842F, 2F, 2F, 2F, 40.936F, -0.629F, 133.142F);
        cubeAt(embers, "ember_16", 104, 56, -8.372F, -7.613F, -15.699F, 2F, 2F, 2F, 13.22F, -17.29F, -17.941F);
        cubeAt(embers, "ember_17", 104, 56, 16.839F, -5.859F, 8.801F, 2F, 2F, 2F, 119.694F, -57.996F, 111.899F);
        cubeAt(embers, "ember_18", 104, 56, -19.77F, -2.182F, -2.493F, 2F, 2F, 2F, -41.703F, -25.675F, 16.734F);
        cubeAt(embers, "ember_19", 104, 56, 20.925F, 1.5F, -6.413F, 2F, 2F, 2F, -148.635F, 1.019F, 167.697F);
        cubeAt(embers, "ember_20", 107, 71, -3.195F, -6.675F, 20.324F, 1F, 1F, 1F, 38.639F, -1.141F, -4.395F);
        cubeAt(embers, "ember_21", 104, 56, -15.669F, -9.045F, -8.505F, 2F, 2F, 2F, -108.888F, 38.373F, 174.273F);
        cubeAt(embers, "ember_22", 104, 56, 24.805F, -11.796F, -9.811F, 2F, 2F, 2F, 43.815F, -32.185F, 104.303F);
        cubeAt(embers, "ember_23", 104, 56, -18.379F, -10.288F, 13.794F, 2F, 2F, 2F, 38.117F, -88.111F, -175.091F);
        cubeAt(embers, "ember_24", 104, 56, 1.897F, -17.321F, -19.629F, 2F, 2F, 2F, -61.638F, 88.282F, -170.625F);
        cubeAt(embers, "ember_25", 107, 71, 9.172F, -24.448F, 11.112F, 1F, 1F, 1F, -158.561F, -20.631F, -112.688F);
        cubeAt(embers, "ember_26", 107, 71, -21.307F, -15.558F, -1.25F, 1F, 1F, 1F, 78.936F, -3.931F, 66.124F);
        cubeAt(embers, "ember_27", 104, 56, 19.618F, -22.048F, -4.104F, 2F, 2F, 2F, -65.296F, -71.427F, -106.713F);
        cubeAt(embers, "ember_28", 104, 56, -1.929F, -10.137F, 24.43F, 2F, 2F, 2F, 49.879F, -60.382F, 103.425F);
        cubeAt(embers, "ember_29", 104, 56, -1.249F, -22.734F, -0.23F, 2F, 2F, 2F, -16.445F, -78.47F, -66.781F);
        cubeAt(embers, "ember_30", 107, 71, -4.046F, -23.915F, 17.207F, 1F, 1F, 1F, 56.72F, 10.348F, 22.3F);

        return LayerDefinition.create(mesh, 256, 512);
    }

    private static PartDefinition group(PartDefinition root, String name) {
        return root.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
    }

    /** Cube dont la boîte est donnée en coordonnées du groupe (tourne autour du centre du soleil). */
    private static void cube(PartDefinition group, String name, int u, int v,
                             float x, float y, float z, float dx, float dy, float dz,
                             float rotX, float rotY, float rotZ) {
        group.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, dx, dy, dz),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F,
                        (float) Math.toRadians(rotX), (float) Math.toRadians(rotY), (float) Math.toRadians(rotZ)));
    }

    /** Cube centré sur (cx, cy, cz), qui tourne sur lui-même. */
    private static void cubeAt(PartDefinition group, String name, int u, int v,
                               float cx, float cy, float cz, float dx, float dy, float dz,
                               float rotX, float rotY, float rotZ) {
        group.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(-dx / 2.0F, -dy / 2.0F, -dz / 2.0F, dx, dy, dz),
                PartPose.offsetAndRotation(cx, cy, cz,
                        (float) Math.toRadians(rotX), (float) Math.toRadians(rotY), (float) Math.toRadians(rotZ)));
    }
}
