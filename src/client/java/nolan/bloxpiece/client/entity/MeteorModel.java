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
 * Calques generes par un script (make_meteor_model.py, hors du depot) : ce fichier est desormais la source de verite.
 * Modele 3D d'un meteore de la Pluie de meteores, meme style volumetrique que Hiken et Dai Enkai: Entei. Deux calques :
 * <ul>
 *   <li>{@link #CORE} (opaque, meteor_core.png) : noyau rocheux irregulier, des blocs imbriques de roche volcanique sombre et de
 *       magma fissure, avec un coeur incandescent jaune-blanc dont les coins percent entre les roches.</li>
 *   <li>{@link #FLAMES} (translucide, meteor_flames.png) : une enveloppe interieure orange vif, une enveloppe exterieure rouge
 *       ardent, et une trainee de flammes qui s'affine derriere le meteore.</li>
 * </ul>
 * Animation : le noyau tourne sur ses 3 axes (tangage, lacet, roulis) et les deux enveloppes tournent chacune dans un autre sens ;
 * vitesse, sens et phase changent d'un meteore a l'autre (voir MeteorRenderer). La trainee reste derriere le meteore et vacille.
 *
 * Repere : pixels (16 px = 1 bloc), centre = centre du meteore, avant (sens de la chute) = -Z, trainee vers +Z, +Y = haut du monde
 * (le renderer n'applique pas le retournement des mobs). Texture 2x plus detaillee que la taille declaree (UV normalises).
 */
public class MeteorModel extends EntityModel<MeteorRenderState> {

    public static final ModelLayerLocation CORE = new ModelLayerLocation(BloxPiece.id("meteor"), "core");
    public static final ModelLayerLocation FLAMES = new ModelLayerLocation(BloxPiece.id("meteor"), "flames");

    private static final int TAIL_SEGMENTS = 6;

    // Vitesses de rotation (radians par tick) : x = tangage, y = lacet, z = roulis
    private static final float[] CORE_SPEED = {0.110F, 0.075F, 0.050F};
    private static final float[] INNER_SPEED = {-0.070F, 0.130F, -0.060F};
    private static final float[] OUTER_SPEED = {0.050F, -0.100F, 0.085F};

    private final ModelPart core;
    private final ModelPart innerShell;
    private final ModelPart outerShell;
    private final ModelPart[] tail = new ModelPart[TAIL_SEGMENTS];

    public MeteorModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        // Un calque n'a pas toutes les parties : celles qui manquent restent null et ne sont pas animees
        this.core = root.hasChild("core") ? root.getChild("core") : null;
        this.innerShell = root.hasChild("inner_shell") ? root.getChild("inner_shell") : null;
        this.outerShell = root.hasChild("outer_shell") ? root.getChild("outer_shell") : null;
        for (int i = 0; i < TAIL_SEGMENTS; i++) {
            this.tail[i] = root.hasChild("tail_" + i) ? root.getChild("tail_" + i) : null;
        }
    }

    @Override
    public void setupAnim(MeteorRenderState state) {
        super.setupAnim(state); // remet la pose d'origine
        float age = state.ageInTicks * state.spin * state.spinSign + state.phase;
        spin(this.core, CORE_SPEED, age);
        spin(this.innerShell, INNER_SPEED, age);
        spin(this.outerShell, OUTER_SPEED, age);
        if (this.outerShell != null) {
            // l'enveloppe exterieure "respire" un peu
            float pulse = 1.0F + 0.08F * (float) Math.sin(state.ageInTicks * 0.9F + state.phase);
            this.outerShell.xScale = pulse;
            this.outerShell.yScale = pulse;
            this.outerShell.zScale = pulse;
        }
        // Trainee : chaque troncon vacille seul, de plus en plus fort vers la pointe
        float t = state.ageInTicks;
        for (int i = 0; i < TAIL_SEGMENTS; i++) {
            ModelPart seg = this.tail[i];
            if (seg == null) {
                continue;
            }
            float phase = i * 2.399F + state.phase;
            float amp = 0.3F + i * 0.35F;
            seg.x += ((float) Math.sin(t * 0.83F + phase) + 0.6F * (float) Math.sin(t * 1.91F + phase * 1.7F)) * amp;
            seg.y += ((float) Math.cos(t * 0.71F + phase * 1.3F) + 0.5F * (float) Math.sin(t * 2.23F + phase)) * amp;
            seg.zRot += t * 0.06F * (i % 2 == 0 ? 1 : -1);
        }
    }

    private static void spin(ModelPart group, float[] speed, float age) {
        if (group != null) {
            group.xRot = age * speed[0];
            group.yRot = age * speed[1];
            group.zRot = age * speed[2];
        }
    }

    /** Noyau opaque : roche volcanique, magma fissuré, coeur incandescent (texture meteor_core.png). */
    public static LayerDefinition createCoreLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition g_core = root.addOrReplaceChild("core", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        cube(g_core, "heart", 0, 0, -6F, -6F, -6F, 12, 12, 12, 0F, 0F, 0F, 45F, 35F, 0F);
        cube(g_core, "rock_main", 48, 0, -7F, -6F, -6.5F, 14, 12, 13, 0F, 0F, 0F, 8F, 15F, -6F);
        cube(g_core, "rock_a", 0, 25, -4.5F, -4F, -5F, 9, 8, 10, 6F, 3F, -2F, 20F, -30F, 10F);
        cube(g_core, "rock_b", 38, 25, -5F, -4.5F, -4F, 10, 9, 8, -5F, -4F, 4F, -15F, 40F, 25F);
        cube(g_core, "rock_c", 74, 25, -4F, -3.5F, -4F, 8, 7, 8, -3F, 6F, -5F, 35F, 10F, -20F);
        cube(g_core, "rock_d", 0, 43, -3.5F, -4F, -3.5F, 7, 8, 7, 4F, -6F, 5F, -25F, -15F, 30F);
        cube(g_core, "rock_e", 28, 43, -3F, -3F, -3.5F, 6, 6, 7, -1F, -2F, -8F, 10F, 50F, 5F);
        cube(g_core, "magma_a", 54, 43, -3.5F, -3.5F, -3.5F, 7, 7, 7, -7F, 1F, -3F, 30F, 45F, 15F);
        cube(g_core, "magma_b", 82, 43, -3F, -3F, -3F, 6, 6, 6, 5F, 5F, 5F, -20F, 25F, 40F);
        cube(g_core, "magma_c", 0, 58, -3F, -2.5F, -3F, 6, 5, 6, 2F, -7F, -5F, 45F, 0F, 30F);
        cube(g_core, "magma_d", 24, 58, -2.5F, -2.5F, -2.5F, 5, 5, 5, -6F, -6F, -3F, 15F, -35F, 45F);
        cube(g_core, "magma_e", 44, 58, -2.5F, -2F, -2.5F, 5, 4, 5, 1F, 3F, 8F, -30F, 20F, -15F);

        return LayerDefinition.create(mesh, 128, 69);
    }

    /** Flammes translucides : deux enveloppes et traînée (texture meteor_flames.png). */
    public static LayerDefinition createFlameLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition g_inner_shell = root.addOrReplaceChild("inner_shell", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        cube(g_inner_shell, "inner_0", 0, 0, -8.5F, -8.5F, -8.5F, 17, 17, 17, 0F, 0F, 0F, 0F, 0F, 0F);
        cube(g_inner_shell, "inner_1", 68, 0, -8.5F, -8.5F, -8.5F, 17, 17, 17, 0F, 0F, 0F, 45F, 0F, 45F);
        cube(g_inner_shell, "inner_2", 136, 0, -8.5F, -8.5F, -8.5F, 17, 17, 17, 0F, 0F, 0F, 0F, 45F, 45F);

        PartDefinition g_outer_shell = root.addOrReplaceChild("outer_shell", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        cube(g_outer_shell, "outer_0", 0, 34, -11.5F, -11.5F, -11.5F, 23, 23, 23, 0F, 0F, 0F, 20F, 30F, 10F);
        cube(g_outer_shell, "outer_1", 92, 34, -11.5F, -11.5F, -11.5F, 23, 23, 23, 0F, 0F, 0F, 60F, 10F, 40F);
        cube(g_outer_shell, "outer_2", 0, 80, -11.5F, -11.5F, -11.5F, 23, 23, 23, 0F, 0F, 0F, 10F, 70F, -30F);

        PartDefinition g_tail_0 = root.addOrReplaceChild("tail_0", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        cube(g_tail_0, "tail_seg_0", 92, 80, -9F, -9F, -4.5F, 18, 18, 9, 0F, 0F, 13.5F, 0F, 0F, 0F);

        PartDefinition g_tail_1 = root.addOrReplaceChild("tail_1", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        cube(g_tail_1, "tail_seg_1", 146, 80, -7.5F, -7.5F, -4F, 15, 15, 8, 0F, 0F, 21F, 0F, 0F, 45F);

        PartDefinition g_tail_2 = root.addOrReplaceChild("tail_2", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        cube(g_tail_2, "tail_seg_2", 192, 80, -6F, -6F, -3.5F, 12, 12, 7, 0F, 0F, 27.5F, 0F, 0F, 0F);

        PartDefinition g_tail_3 = root.addOrReplaceChild("tail_3", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        cube(g_tail_3, "tail_seg_3", 0, 126, -4.5F, -4.5F, -3F, 9, 9, 6, 0F, 0F, 33F, 0F, 0F, 45F);

        PartDefinition g_tail_4 = root.addOrReplaceChild("tail_4", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        cube(g_tail_4, "tail_seg_4", 30, 126, -3F, -3F, -2.5F, 6, 6, 5, 0F, 0F, 37.5F, 0F, 0F, 0F);

        PartDefinition g_tail_5 = root.addOrReplaceChild("tail_5", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        cube(g_tail_5, "tail_seg_5", 52, 126, -2F, -2F, -2F, 4, 4, 4, 0F, 0F, 41F, 0F, 0F, 45F);

        return LayerDefinition.create(mesh, 256, 141);
    }

    /** Cube (boite relative a son pivot) place a (ox,oy,oz) du groupe, avec rotation en degres. */
    private static void cube(PartDefinition group, String name, int u, int v,
                             float x, float y, float z, int dx, int dy, int dz,
                             float ox, float oy, float oz, float rotX, float rotY, float rotZ) {
        group.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, dx, dy, dz, CubeDeformation.NONE),
                PartPose.offsetAndRotation(ox, oy, oz,
                        (float) Math.toRadians(rotX), (float) Math.toRadians(rotY), (float) Math.toRadians(rotZ)));
    }
}
