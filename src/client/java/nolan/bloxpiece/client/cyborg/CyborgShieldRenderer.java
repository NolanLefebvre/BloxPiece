package nolan.bloxpiece.client.cyborg;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.client.goro.GoroLightning;
import nolan.bloxpiece.client.haki.HakiBoltRenderer;
import nolan.bloxpiece.fx.CyborgShield;

/**
 * Dôme du Bouclier énergétique (race Cyborg) : une SPHÈRE bleu clair, translucide, centrée sur le corps du joueur (il est au
 * milieu, entouré de tous côtés), couverte d'un NID D'ABEILLES continu (sphère de Goldberg, arêtes partagées : voir EDGES) ;
 * chaque face a un remplissage translucide (trois bandes qui forment l'hexagone) et ses arêtes lumineuses sont communes avec ses
 * voisines. Apparition : les hexagones s'allument du bas vers le
 * haut pendant que la sphère grandit ; disparition : ils s'éteignent du haut vers le bas. Un anneau éblouissant marque le sol,
 * et un reflet d'énergie balaie la surface.
 */
public class CyborgShieldRenderer extends EntityRenderer<CyborgShield, CyborgShieldRenderer.State> {

    public static class State extends EntityRenderState {
        public float age, radius = 2.3F;
        public int grow = 20, life = 180;
    }

    /** Bleu clair, translucide (remplissage), contours très visibles. */
    private static final int EDGE = 0xC8F0FF, FILL = 0x7FD0FF, BRIGHT = 0xFFFFFF;
    private final ModelPart segment;

    /** Finesse de la sphère géodésique : 4 = 150 hexagones + 12 pentagones (arêtes d'environ 0,4 bloc sur le dôme du jeu). */
    private static final int FREQUENCY = 4;

    /**
     * Le dôme est une SPHÈRE de nid d'abeilles (sphère de Goldberg), calculée une fois sur une sphère de rayon 1 : un icosaèdre
     * dont chaque face est découpée en triangles réguliers (projetés sur la sphère), puis son DUAL : chaque sommet devient une
     * face (hexagone, ou pentagone aux 12 sommets d'origine) et chaque arête de triangle devient l'arête qui relie les centres de
     * ses deux triangles. Chaque arête existe donc UNE fois et ses extrémités sont exactement partagées par les faces voisines :
     * une surface continue, régulière, sans espace ni décalage. Arêtes : {x1, y1, z1, x2, y2, z2, hauteur 0..1} ; faces :
     * {x, y, z, hauteur 0..1, rayon moyen, nombre de coins n, puis les n coins dans l'ordre (x, y, z)} : les coins sont les
     * MÊMES points que les extrémités des arêtes.
     */
    private static final java.util.List<float[]> EDGES = new java.util.ArrayList<>();
    private static final java.util.List<float[]> FACES = new java.util.ArrayList<>();

    static {
        double t = (1.0 + Math.sqrt(5.0)) / 2.0;
        double[][] ico = {{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
                {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
        int[][] faces = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6},
                {7, 1, 8}, {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7},
                {9, 8, 1}};
        java.util.List<double[]> verts = new java.util.ArrayList<>();
        java.util.Map<String, Integer> index = new java.util.HashMap<>();
        java.util.List<int[]> tris = new java.util.ArrayList<>();
        int f = FREQUENCY;
        for (int[] face : faces) {
            double[] a = unit(ico[face[0]]), b = unit(ico[face[1]]), c = unit(ico[face[2]]);
            int[][] grid = new int[f + 1][f + 1];
            for (int i = 0; i <= f; i++) {
                for (int j = 0; j <= f - i; j++) {
                    double[] p = new double[3];
                    for (int k = 0; k < 3; k++) {
                        p[k] = a[k] + (b[k] - a[k]) * i / f + (c[k] - a[k]) * j / f;
                    }
                    p = unit(p);
                    String key = Math.round(p[0] * 1e6) + ":" + Math.round(p[1] * 1e6) + ":" + Math.round(p[2] * 1e6);
                    Integer id = index.get(key);
                    if (id == null) {
                        id = verts.size();
                        index.put(key, id);
                        verts.add(p);
                    }
                    grid[i][j] = id;
                }
            }
            for (int i = 0; i < f; i++) {
                for (int j = 0; j < f - i; j++) {
                    tris.add(new int[] {grid[i][j], grid[i + 1][j], grid[i][j + 1]});
                    if (i + j < f - 1) {
                        tris.add(new int[] {grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1]});
                    }
                }
            }
        }
        // centre (sur la sphère) de chaque triangle = un sommet du nid d'abeilles
        double[][] centroid = new double[tris.size()][];
        for (int i = 0; i < tris.size(); i++) {
            int[] tr = tris.get(i);
            double[] p = new double[3];
            for (int k = 0; k < 3; k++) {
                p[k] = (verts.get(tr[0])[k] + verts.get(tr[1])[k] + verts.get(tr[2])[k]) / 3.0;
            }
            centroid[i] = unit(p);
        }
        // arête du nid d'abeilles = les deux triangles qui partagent une arête de triangle
        java.util.Map<String, int[]> shared = new java.util.HashMap<>();
        double[] faceRadius = new double[verts.size()];
        int[] faceCount = new int[verts.size()];
        java.util.List<java.util.List<Integer>> around = new java.util.ArrayList<>();
        for (int v = 0; v < verts.size(); v++) {
            around.add(new java.util.ArrayList<>());
        }
        for (int i = 0; i < tris.size(); i++) {
            int[] tr = tris.get(i);
            for (int e = 0; e < 3; e++) {
                int u = tr[e], v = tr[(e + 1) % 3];
                String key = Math.min(u, v) + ":" + Math.max(u, v);
                int[] pair = shared.computeIfAbsent(key, k -> new int[] {-1, -1});
                pair[pair[0] < 0 ? 0 : 1] = i;
                faceRadius[u] += dist(verts.get(u), centroid[i]);
                faceCount[u]++;
                around.get(u).add(i); // les triangles autour du sommet u = les coins de la face u
            }
        }
        for (int[] pair : shared.values()) {
            if (pair[0] < 0 || pair[1] < 0) {
                continue;
            }
            double[] p = centroid[pair[0]], e = centroid[pair[1]];
            EDGES.add(new float[] {(float) p[0], (float) p[1], (float) p[2], (float) e[0], (float) e[1], (float) e[2],
                    (float) ((p[1] + e[1]) * 0.25 + 0.5)});
        }
        for (int v = 0; v < verts.size(); v++) {
            double[] p = verts.get(v);
            // coins de la face rangés dans l'ordre autour de son centre (angle dans le plan tangent)
            double[] u0 = sub(centroid[around.get(v).get(0)], p);
            double[] e1 = unit(sub(u0, scale(p, dot(u0, p))));
            double[] e2 = cross(p, e1);
            java.util.List<double[]> corners = new java.util.ArrayList<>();
            for (int tri : around.get(v)) {
                corners.add(centroid[tri]);
            }
            corners.sort(java.util.Comparator.comparingDouble(c -> Math.atan2(dot(sub(c, p), e2), dot(sub(c, p), e1))));
            int n = corners.size();
            float[] face = new float[6 + 3 * n];
            face[0] = (float) p[0];
            face[1] = (float) p[1];
            face[2] = (float) p[2];
            face[3] = (float) (p[1] * 0.5 + 0.5);
            face[4] = (float) (faceRadius[v] / Math.max(1, faceCount[v]));
            face[5] = n;
            for (int k = 0; k < n; k++) {
                face[6 + 3 * k] = (float) corners.get(k)[0];
                face[7 + 3 * k] = (float) corners.get(k)[1];
                face[8 + 3 * k] = (float) corners.get(k)[2];
            }
            FACES.add(face);
        }
    }

    private static double[] unit(double[] p) {
        double l = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
        return new double[] {p[0] / l, p[1] / l, p[2] / l};
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] scale(double[] a, double k) {
        return new double[] {a[0] * k, a[1] * k, a[2] * k};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    public CyborgShieldRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.segment = context.bakeLayer(HakiBoltRenderer.LAYER).getChild("segment");
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CyborgShield entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.radius = entity.radius();
        state.grow = entity.grow();
        state.life = entity.life();
    }

    /** Allumage d'un élément à la hauteur {@code height} (0 au sol, 1 au sommet) : du sol vers le sommet, éteint du sommet. */
    private static float on(float height, float grow, float out) {
        return Mth.clamp(grow * 1.6F - height * 0.6F, 0.0F, 1.0F) * Mth.clamp(out * 1.6F - (1.0F - height) * 0.6F, 0.0F, 1.0F);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float age = state.age;
        float grow = Mth.clamp(age / state.grow, 0.0F, 1.0F);
        float out = Mth.clamp((state.life - age) / CyborgShield.FADE, 0.0F, 1.0F);
        float R = state.radius * (1.0F - (1.0F - grow) * (1.0F - grow) * (1.0F - grow)) * (0.6F + 0.4F * out);
        if (R < 0.2F || out <= 0.0F) {
            super.submit(state, pose, collector, camera);
            return;
        }
        RenderType glow = RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
        pose.pushPose();
        pose.translate(0.0F, CyborgShield.CENTER_Y, 0.0F); // centre de la sphère : le milieu du corps du joueur
        // remplissage de chaque hexagone (bleu clair translucide, reflet d'énergie qui balaie le dôme)
        for (int i = 0; i < FACES.size(); i++) {
            float[] c = FACES.get(i);
            float alpha = on(c[3], grow, out) * out;
            if (alpha <= 0.01F) {
                continue;
            }
            float az = (float) Math.atan2(c[2], c[0]);
            float sweep = 0.5F + 0.5F * Mth.sin(age * 0.25F - az * 2.0F + c[3] * 4.0F);
            float flicker = ((i * 73 + (int) (age * 0.5F)) % 41 == 0) ? 1.0F : 0.0F;
            face(pose, collector, glow, c, R, alpha, sweep, flicker);
        }
        // arêtes du nid d'abeilles : chacune une fois, extrémités partagées entre voisins (structure continue)
        for (float[] e : EDGES) {
            float alpha = on(e[6], grow, out) * out;
            if (alpha <= 0.01F) {
                continue;
            }
            float pulse = 0.75F + 0.25F * Mth.sin(age * 0.3F + e[6] * 6.0F);
            GoroLightning.segment(this.segment, pose, collector, 2, glow, new float[] {e[0] * R, e[1] * R, e[2] * R},
                    new float[] {e[3] * R, e[4] * R, e[5] * R}, 0.55F, GoroLightning.argb(Math.min(1.0F, 0.9F * pulse * alpha), EDGE));
        }
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }

    /**
     * Une face du nid d'abeilles, propre vue de l'EXTÉRIEUR comme de l'INTÉRIEUR (tout est en volume : rien ne disparaît selon le
     * côté) : un remplissage translucide ALIGNÉ sur ses vrais coins (un coin du remplissage pointe vers le premier coin de la
     * face), un peu en retrait de ses arêtes, et un contour intérieur qui suit exactement ses coins, comme une tuile serrée dans
     * son cadre. Les arêtes communes (le cadre) sont dessinées une seule fois à part.
     */
    private void face(PoseStack pose, SubmitNodeCollector collector, RenderType type, float[] f, float R, float alpha, float sweep,
                      float flicker) {
        int n = (int) f[5];
        float cx = f[0], cy = f[1], cz = f[2];
        // repère tangent : u vers le premier coin, n vers l'extérieur, v = n x u
        float dx = f[6] - cx, dy = f[7] - cy, dz = f[8] - cz;
        float along = dx * cx + dy * cy + dz * cz;
        float ux = dx - cx * along, uy = dy - cy * along, uz = dz - cz * along;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float vx = cy * uz - cz * uy, vy = cz * ux - cx * uz, vz = cx * uy - cy * ux;
        int fill = GoroLightning.argb(Math.min(1.0F, (0.10F + 0.08F * sweep + 0.30F * flicker) * alpha), flicker > 0 ? BRIGHT : FILL);
        if (n == 6) {
            // hexagone à coins sur u : trois bandes (largeur s, hauteur s x racine de 3) à 0°, 60°, 120°, un peu en retrait
            float s = f[4] * R * 0.80F; // juste à l'intérieur du contour (0,84) : le remplissage ne déborde jamais du cadre
            pose.pushPose();
            pose.translate(cx * R, cy * R, cz * R);
            pose.mulPose(new Matrix4f(new Matrix3f(ux, uy, uz, vx, vy, vz, cx, cy, cz)));
            for (int r = 0; r < 3; r++) {
                pose.pushPose();
                pose.rotateDegrees(Axis.ZP, r * 60.0F);
                pose.scale(s * 16.0F, s * (float) Math.sqrt(3.0) * 16.0F, 0.15F);
                collector.order(1).submitModelPart(this.segment, pose, type, GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                        fill);
                pose.popPose();
            }
            pose.popPose();
        }
        // contour intérieur : exactement les coins de la face, resserrés vers son centre (pentagones compris)
        int inner = GoroLightning.argb(Math.min(1.0F, (0.35F + 0.25F * sweep + 0.4F * flicker) * alpha), EDGE);
        float k = 0.84F;
        for (int c = 0; c < n; c++) {
            int d = (c + 1) % n;
            float[] a = {(cx + (f[6 + 3 * c] - cx) * k) * R, (cy + (f[7 + 3 * c] - cy) * k) * R, (cz + (f[8 + 3 * c] - cz) * k) * R};
            float[] b = {(cx + (f[6 + 3 * d] - cx) * k) * R, (cy + (f[7 + 3 * d] - cy) * k) * R, (cz + (f[8 + 3 * d] - cz) * k) * R};
            GoroLightning.segment(this.segment, pose, collector, 2, type, a, b, 0.32F, inner);
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(CyborgShield entity, float partialTick) {
        return entity.getBoundingBox().inflate(entity.radius() + 1.0);
    }
}
