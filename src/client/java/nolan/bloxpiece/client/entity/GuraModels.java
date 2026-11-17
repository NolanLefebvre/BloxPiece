package nolan.bloxpiece.client.entity;

import java.util.Random;

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
 * Modèles 3D du Gura Gura no Mi, dans le style des pouvoirs de Barbe Blanche : air comprimé diaphane, fissures blanches très
 * fines et transparentes ramifiées comme des éclairs. Texture quake.png en trois bandes : air (~20 % d'opacité, texOffs 0,0),
 * fissures (~45 %, texOffs 0,43), coeur lumineux de la boule (texOffs 0,86). Rayon de base 16 px (= 1 bloc) : le renderer met le modèle à
 * l'échelle de la taille de l'effet.
 * <ul>
 *   <li>{@link CrackDiscModel} : éclatement de fissures en étoile (une vingtaine de fissures en zigzag, chacune ramifiée), sans
 *       cercle ni bordure autour, face à +Z ;</li>
 *   <li>{@link GroundCrackModel} : fissures au sol qui se propagent depuis le centre (sans cercle) ;</li>
 *   <li>{@link CrackOrbModel} : bulle sismique ronde, enveloppe d'air diaphane + coeur blanc (mains), ou enveloppe seule (déflagration).</li>
 * </ul>
 * Les formes sont tirées avec une graine fixe : identiques chez tous les joueurs.
 */
public final class GuraModels {

    public static final Identifier TEXTURE = BloxPiece.id("textures/entity/quake.png");
    private static final int TEX = 128;
    /** Zones de la texture : air diaphane, fissures transparentes, coeur lumineux. */
    private static final int GLASS_V = 0;
    private static final int CRACK_V = 43;
    private static final int CORE_V = 86;

    private GuraModels() {
    }

    /** Données de rendu des effets sismiques. */
    public static class QuakeRenderState extends EntityRenderState {
        public int kind;
        public float yaw;
        public float pitch;
        public float scale;
        public float age;
        /** Fissures au sol : part déjà propagée (0 = rien, 1 = jusqu'au bout). */
        public float reveal = 1.0F;
        /** Décalage (blocs) entre la position de l'entité et la main qui la porte (calculé chaque image). */
        public double offX;
        public double offY;
        public double offZ;
    }

    // ------------------------------------------------------------------ outils

    private static int counter;

    /** Barre fine de (x1, y1) à (x2, y2) dans le plan XY (épaisseur {@code w}, profondeur {@code d}). */
    private static void line(PartDefinition parent, float x1, float y1, float x2, float y2, float w, float d, int v) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float angle = (float) Math.atan2(dy, dx);
        parent.addOrReplaceChild("l" + (counter++),
                CubeListBuilder.create().texOffs(0, v).addBox(-len / 2, -w / 2, -d / 2, len, w, d),
                PartPose.offsetAndRotation((x1 + x2) / 2, (y1 + y2) / 2, 0, 0, 0, angle));
    }

    /** Fissure en zigzag depuis (x, y) dans la direction {@code angle} : segments qui s'amincissent, branches. */
    private static void crack(PartDefinition parent, Random rnd, float x, float y, float angle, float width, int segments, int depth) {
        for (int s = 0; s < segments; s++) {
            float len = 2.5F + rnd.nextFloat() * 3.5F;
            angle += (rnd.nextFloat() - 0.5F) * 0.7F;
            float nx = x + (float) Math.cos(angle) * len, ny = y + (float) Math.sin(angle) * len;
            line(parent, x, y, nx, ny, width, 0.08F, CRACK_V);
            if (depth > 0 && rnd.nextFloat() < 0.55F) {
                float side = rnd.nextBoolean() ? 1 : -1;
                crack(parent, rnd, nx, ny, angle + side * (0.5F + rnd.nextFloat() * 0.5F), width * 0.65F, 2, depth - 1);
            }
            x = nx;
            y = ny;
            width = Math.max(0.08F, width * 0.82F);
        }
    }

    // ------------------------------------------------------------------ éclatement de fissures

    public static class CrackDiscModel extends EntityModel<QuakeRenderState> {
        public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("quake_disc"), "main");
        private final ModelPart body;

        public CrackDiscModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
            this.body = root.getChild("body");
        }

        @Override
        public void setupAnim(QuakeRenderState state) {
            super.setupAnim(state);
            this.body.zRot = state.age * 0.01F;
        }

        public static LayerDefinition createLayer() {
            counter = 0;
            Random rnd = new Random(1337L);
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
            // Coeur : point d'impact éclatant (petite étoile)
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 4;
                line(body, (float) (-Math.cos(a) * 1.4), (float) (-Math.sin(a) * 1.4), (float) (Math.cos(a) * 1.4), (float) (Math.sin(a) * 1.4), 0.22F, 0.1F, CRACK_V);
            }
            // Fissures en étoile, ramifiées
            int rays = 20;
            for (int i = 0; i < rays; i++) {
                float a = (float) (i * Math.PI * 2 / rays + (rnd.nextFloat() - 0.5F) * 0.25F);
                crack(body, rnd, (float) Math.cos(a) * 0.8F, (float) Math.sin(a) * 0.8F, a, 0.24F, 3 + rnd.nextInt(2), 2);
            }
            return LayerDefinition.create(mesh, TEX, TEX);
        }
    }

    // ------------------------------------------------------------------ fissures au sol (propagation)

    /**
     * Fissures au sol (Kabutowari, Shima Yurashi) : seulement des branches ramifiées, sans cercle autour. Elles se propagent depuis
     * le centre : chaque segment n'apparaît que quand le front ({@link QuakeRenderState#reveal}) l'a atteint.
     */
    public static class GroundCrackModel extends EntityModel<QuakeRenderState> {
        public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("quake_ground"), "main");
        private static int lineCount;
        private final ModelPart[] lines;
        private final float[] distance;

        public GroundCrackModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
            ModelPart body = root.getChild("body");
            this.lines = new ModelPart[lineCount];
            this.distance = new float[lineCount];
            for (int i = 0; i < lineCount; i++) {
                this.lines[i] = body.getChild("l" + i);
                this.distance[i] = (float) Math.sqrt(this.lines[i].x * this.lines[i].x + this.lines[i].y * this.lines[i].y);
            }
        }

        @Override
        public void setupAnim(QuakeRenderState state) {
            super.setupAnim(state);
            float front = state.reveal * 40.0F; // les fissures les plus longues font environ 40 px
            for (int i = 0; i < this.lines.length; i++) {
                this.lines[i].visible = this.distance[i] <= front;
            }
        }

        public static LayerDefinition createLayer() {
            counter = 0;
            Random rnd = new Random(2024L);
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
            int rays = 13;
            for (int i = 0; i < rays; i++) {
                float a = (float) (i * Math.PI * 2 / rays + (rnd.nextFloat() - 0.5F) * 0.4F);
                crack(body, rnd, (float) Math.cos(a) * 0.6F, (float) Math.sin(a) * 0.6F, a, 0.28F, 5 + rnd.nextInt(2), 2);
            }
            lineCount = counter;
            return LayerDefinition.create(mesh, TEX, TEX);
        }
    }

    // ------------------------------------------------------------------ bulle sismique

    /**
     * Boule sismique : sphère lisse, finement découpée (facettes très fines et nombreuses, d'épaisseur presque nulle : on ne voit
     * plus de plaques), en double couche pour la boule des mains ({@link #LAYER}) : un grand coeur blanc lumineux (rayon 14 px)
     * presque collé à l'enveloppe externe d'air très transparente (rayon 16 px). La sphère des déflagrations ({@link #SHELL_LAYER})
     * n'a que l'enveloppe, parcourue de fissures fines.
     */
    public static class CrackOrbModel extends EntityModel<QuakeRenderState> {
        public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("quake_orb"), "main");
        public static final ModelLayerLocation SHELL_LAYER = new ModelLayerLocation(BloxPiece.id("quake_shell"), "main");
        private final ModelPart body;

        public CrackOrbModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
            this.body = root.getChild("body");
        }

        @Override
        public void setupAnim(QuakeRenderState state) {
            super.setupAnim(state);
            this.body.yRot = state.age * 0.04F;
            this.body.xRot = state.age * 0.015F;
        }

        /** Bulle des mains : enveloppe diaphane + coeur blanc. */
        public static LayerDefinition createLayer() {
            return build(true);
        }

        /** Sphère de déflagration : enveloppe + fissures fines, sans coeur. */
        public static LayerDefinition createShellLayer() {
            return build(false);
        }

        private static LayerDefinition build(boolean core) {
            counter = 0;
            Random rnd = new Random(4242L);
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
            sphere(body, 16.0F, 24, 48, GLASS_V, 0.02F);          // enveloppe externe : air très diaphane
            if (core) {
                sphere(body, 14.0F, 20, 40, CORE_V, 0.02F);       // grand coeur blanc lumineux, presque collé à l'enveloppe
            }
            int cracks = core ? 0 : 24;                            // la boule des mains reste lisse et propre
            for (int i = 0; i < cracks; i++) {
                double lat = Math.asin(rnd.nextFloat() * 2 - 1);
                double lon = rnd.nextFloat() * Math.PI * 2;
                plate(body, 16.1F, lat, lon, 2.5F + rnd.nextFloat() * 4.0F, 0.1F, 0.02F, CRACK_V, rnd.nextFloat() * (float) Math.PI);
            }
            return LayerDefinition.create(mesh, TEX, TEX);
        }

        /** Sphère de {@code bands} bandes de latitude, jusqu'à {@code maxPerBand} plaques par bande (moins près des pôles). */
        private static void sphere(PartDefinition parent, float radius, int bands, int maxPerBand, int v, float thickness) {
            for (int b = 0; b < bands; b++) {
                double lat = -Math.PI / 2 + Math.PI * (b + 0.5) / bands;
                int n = Math.max(4, (int) Math.round(maxPerBand * Math.cos(lat)));
                double widest = Math.cos(Math.max(0.0, Math.abs(lat) - Math.PI / (2 * bands))); // bord de la bande le plus près de l'équateur
                float plateW = (float) (2 * Math.PI * radius * widest / n);
                float plateH = (float) (Math.PI * radius / bands);
                for (int i = 0; i < n; i++) {
                    plate(parent, radius, lat, 2 * Math.PI * (i + (b % 2) * 0.5) / n, plateW, plateH, thickness, v, 0.0F);
                }
            }
        }

        /** Plaque tangente à la sphère au point (lat, lon), tournée de {@code twist} autour de sa normale. */
        private static void plate(PartDefinition parent, float radius, double lat, double lon, float w, float h, float d, int v, float twist) {
            float x = (float) (radius * Math.cos(lat) * Math.sin(lon));
            float y = (float) (-radius * Math.sin(lat));
            float z = (float) (radius * Math.cos(lat) * Math.cos(lon));
            PartDefinition holder = parent.addOrReplaceChild("p" + (counter++), CubeListBuilder.create(),
                    PartPose.offsetAndRotation(x, y, z, (float) lat, (float) lon, 0));
            holder.addOrReplaceChild("q", CubeListBuilder.create().texOffs(0, v).addBox(-w / 2, -h / 2, -d / 2, w, h, d),
                    PartPose.rotation(0, 0, twist));
        }
    }

    // ------------------------------------------------------------------ tsunami (Shima Yurashi)

    public static final Identifier WATER_TEXTURE = BloxPiece.id("textures/entity/quake_water.png");
    private static final int WATER_V = 0;
    private static final int FOAM_V = 96;

    private static final float TSUNAMI_HALF_WIDTH = 17.0F;

    /**
     * Tsunami (largeur 34 px ≈ 2 blocs à la taille 1, hauteur ~28 px), profil de vraie déferlante en « C » :
     * <ul>
     *   <li>montée : courbe de Bézier lisse, base large et épaisse inclinée en pente douce à l'arrière, qui s'incurve
     *       progressivement vers l'avant jusqu'au sommet ;</li>
     *   <li>rouleau : arc de cercle TANGENT à la montée (aucune cassure) qui s'enroule et retombe vers l'avant ;</li>
     *   <li>épaisseur qui diminue continûment du pied (13 px) à la lèvre (2,5 px) ; la courbe est découpée finement (16 + 12
     *       segments qui se recouvrent légèrement) pour un arc fluide, sans angles.</li>
     * </ul>
     * Animation : le rouleau (pivot au sommet) s'enroule progressivement pendant la course et respire légèrement ; le front
     * ondule sur la largeur (4 tronçons décalés) ; l'écume blanche ne borde QUE la lèvre supérieure du rouleau et bouillonne.
     * Texture quake_water.png (128 x 128) : dégradé bleu profond → turquoise selon la hauteur, écume (texOffs 0,96).
     */
    public static class TsunamiModel extends EntityModel<QuakeRenderState> {
        public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("quake_tsunami"), "main");
        private static final int CHUNKS = 4;
        private static final int RISE_SEGMENTS = 16;
        private static final int CURL_SEGMENTS = 12;
        // Montée : Bézier cubique (z, h)
        private static final float[] P0 = {-12, 0}, P1 = {-8, 11}, P2 = {-3, 24}, P3 = {3, 27};
        // Rouleau : arc tangent à la montée en P3, rayon 5, de l'angle de départ jusqu'à -40° (sens horaire)
        private static final float CURL_R = 5.0F;
        private static final float BASE_THICK = 13.0F, LIP_THICK = 2.5F;
        private static int risePerChunk;
        private static int curlPerChunk;
        private static int foamPerChunk;

        private final ModelPart body;
        private final ModelPart[] rise;     // tronçons de la montée
        private final float[] riseZ;
        private final int[] riseSeg;
        private final ModelPart[] curlPivot; // un rouleau par tronçon de largeur
        private final ModelPart[] foam;
        private final float[] foamY;

        public TsunamiModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
            this.body = root.getChild("body");
            this.rise = new ModelPart[risePerChunk * CHUNKS];
            this.riseZ = new float[this.rise.length];
            this.riseSeg = new int[this.rise.length];
            this.curlPivot = new ModelPart[CHUNKS];
            this.foam = new ModelPart[foamPerChunk * CHUNKS];
            this.foamY = new float[this.foam.length];
            for (int c = 0; c < CHUNKS; c++) {
                for (int i = 0; i < risePerChunk; i++) {
                    ModelPart p = this.body.getChild("w" + c + "_" + i);
                    int k = c * risePerChunk + i;
                    this.rise[k] = p;
                    this.riseZ[k] = p.z;
                    this.riseSeg[k] = i;
                }
                this.curlPivot[c] = this.body.getChild("curl" + c);
                for (int i = 0; i < foamPerChunk; i++) {
                    ModelPart f = this.curlPivot[c].getChild("f" + i);
                    this.foam[c * foamPerChunk + i] = f;
                    this.foamY[c * foamPerChunk + i] = f.y;
                }
            }
        }

        @Override
        public void setupAnim(QuakeRenderState state) {
            super.setupAnim(state);
            float t = state.age;
            float form = Math.min(1.0F, t / 22.0F);
            float curlEase = form * form * (3.0F - 2.0F * form);
            this.body.xRot = (float) Math.sin(t * 0.12F) * 0.03F; // la masse se penche lourdement
            // Montée : houle qui court le long du front, plus ample en haut (la base reste ancrée)
            for (int k = 0; k < this.rise.length; k++) {
                int chunk = k / risePerChunk;
                float height = (float) this.riseSeg[k] / risePerChunk;
                float swell = 0.7F * (float) Math.sin(t * 0.22F + chunk * 1.2F + height * 2.0F)
                        + 0.3F * (float) Math.sin(t * 0.47F - chunk * 0.8F + height * 3.5F);
                this.rise[k].z = this.riseZ[k] + swell * height * height * 1.4F;
            }
            // Rouleau : déroulé (relevé) au départ, il s'enroule vers l'avant pendant la course, puis respire
            for (int c = 0; c < CHUNKS; c++) {
                float breath = 0.06F * (float) Math.sin(t * 0.3F + c * 1.1F);
                this.curlPivot[c].xRot = 0.75F * (1.0F - curlEase) + breath;
                float swell = 0.7F * (float) Math.sin(t * 0.22F + c * 1.2F + 2.0F);
                this.curlPivot[c].z = P3[0] + swell * 1.4F;
            }
            // Écume de la lèvre : bouillonne
            for (int i = 0; i < this.foam.length; i++) {
                float b = t * 0.8F + i * 1.3F;
                this.foam[i].y = this.foamY[i] + (float) Math.sin(b) * 0.25F;
                float boil = 1.0F + 0.2F * (float) Math.sin(b * 1.2F);
                this.foam[i].xScale = boil;
                this.foam[i].yScale = 2.0F - boil;
                this.foam[i].zScale = boil;
            }
        }

        /** Point de la montée (Bézier) au paramètre u. */
        private static float[] bezier(float u) {
            float a = (1 - u) * (1 - u) * (1 - u), b = 3 * u * (1 - u) * (1 - u), c = 3 * u * u * (1 - u), d = u * u * u;
            return new float[] {a * P0[0] + b * P1[0] + c * P2[0] + d * P3[0], a * P0[1] + b * P1[1] + c * P2[1] + d * P3[1]};
        }

        private static float thickness(float f) { // f = 0 au pied, 1 au bout de la lèvre
            return LIP_THICK + (BASE_THICK - LIP_THICK) * (float) Math.pow(1.0F - f, 1.15);
        }

        /** Bande de couleur de la texture selon la hauteur relative (0 = bleu profond, 1 = turquoise). */
        private static int band(float f) {
            return Math.min(96 - 18, Math.round(f * 78.0F));
        }

        public static LayerDefinition createLayer() {
            Random rnd = new Random(55L);
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
            float w = TSUNAMI_HALF_WIDTH * 2;
            float chunkW = w / CHUNKS;
            int total = RISE_SEGMENTS + CURL_SEGMENTS;

            // Arc du rouleau : tangent à la montée en P3
            float tz = P3[0] - P2[0], th = P3[1] - P2[1];
            float tl = (float) Math.sqrt(tz * tz + th * th);
            tz /= tl;
            th /= tl;
            float nz = th, nh = -tz;                       // normale vers l'intérieur du virage (sens horaire)
            float cz = P3[0] + nz * CURL_R, ch = P3[1] + nh * CURL_R;
            double startAngle = Math.atan2(P3[1] - ch, P3[0] - cz);
            double endAngle = Math.toRadians(-40.0);

            for (int c = 0; c < CHUNKS; c++) {
                float cx = -w / 2 + chunkW * (c + 0.5F);
                // --- montée : segments le long de la Bézier
                for (int i = 0; i < RISE_SEGMENTS; i++) {
                    float[] a = bezier((float) i / RISE_SEGMENTS), b = bezier((float) (i + 1) / RISE_SEGMENTS);
                    float f = (i + 0.5F) / total;
                    segment(body, "w" + c + "_" + i, cx, chunkW, a, b, thickness(f), band(f * 1.15F), c);
                }
                // --- rouleau : groupe qui pivote au sommet (P3), segments en coordonnées relatives à P3
                PartDefinition curl = body.addOrReplaceChild("curl" + c, CubeListBuilder.create(),
                        PartPose.offset(0, -P3[1], P3[0]));
                int foamCount = 0;
                for (int i = 0; i < CURL_SEGMENTS; i++) {
                    double a1 = startAngle + (endAngle - startAngle) * i / CURL_SEGMENTS;
                    double a2 = startAngle + (endAngle - startAngle) * (i + 1) / CURL_SEGMENTS;
                    float[] pa = {cz + CURL_R * (float) Math.cos(a1) - P3[0], ch + CURL_R * (float) Math.sin(a1) - P3[1]};
                    float[] pb = {cz + CURL_R * (float) Math.cos(a2) - P3[0], ch + CURL_R * (float) Math.sin(a2) - P3[1]};
                    float f = (RISE_SEGMENTS + i + 0.5F) / total;
                    float thick = thickness(f);
                    segment(curl, "c" + i, cx, chunkW, pa, pb, thick, band(Math.min(1.0F, f * 1.15F)), c);
                    // Écume uniquement sur la lèvre supérieure (moitié avant du rouleau), du côté extérieur
                    if (i >= CURL_SEGMENTS / 3) {
                        double am = (a1 + a2) / 2;
                        float off = thick / 2 + 0.35F;
                        float ez = cz + (CURL_R + off) * (float) Math.cos(am) - P3[0];
                        float eh = ch + (CURL_R + off) * (float) Math.sin(am) - P3[1];
                        for (int k = 0; k < 2; k++) {
                            float fw = chunkW * (0.35F + rnd.nextFloat() * 0.3F), fh = 0.7F + rnd.nextFloat() * 0.8F, fd = 1.4F + rnd.nextFloat() * 1.2F;
                            curl.addOrReplaceChild("f" + (foamCount++), CubeListBuilder.create().texOffs(rnd.nextInt(16), FOAM_V + rnd.nextInt(8))
                                            .addBox(-fw / 2, -fh / 2, -fd / 2, fw, fh, fd),
                                    PartPose.offsetAndRotation(cx + (k == 0 ? -1 : 1) * chunkW * 0.22F + (rnd.nextFloat() - 0.5F),
                                            -eh, ez, (float) (am + Math.PI / 2) + (rnd.nextFloat() - 0.5F) * 0.3F, (rnd.nextFloat() - 0.5F) * 0.3F, 0)); // à plat sur la lèvre
                        }
                    }
                }
                foamPerChunk = foamCount;
            }
            risePerChunk = RISE_SEGMENTS;
            curlPerChunk = CURL_SEGMENTS;
            return LayerDefinition.create(mesh, 128, 128); // texture 128 x 128
        }

        /** Segment d'eau de {@code a} à {@code b} (z, hauteur), épaisseur centrée sur la courbe, avec un léger recouvrement. */
        private static void segment(PartDefinition parent, String name, float cx, float chunkW, float[] a, float[] b, float thick, int band, int chunk) {
            float dz = b[0] - a[0], dh = b[1] - a[1];
            float len = (float) Math.sqrt(dz * dz + dh * dh) + 0.7F;
            parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(chunk * 8, band)
                            .addBox(-chunkW / 2 - 0.2F, -thick / 2, -len / 2, chunkW + 0.4F, thick, len),
                    PartPose.offsetAndRotation(cx, -(a[1] + b[1]) / 2, (a[0] + b[0]) / 2, (float) Math.atan2(dh, dz), 0, 0));
        }
    }
}
