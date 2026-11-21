package nolan.bloxpiece.client.haki;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.HakiBolt;

/**
 * Éclair du Haki des Rois, généré à chaque image (foudre procédurale, plus de formes fixes) :
 * <ul>
 *   <li><b>Canal principal</b> : part du corps dans sa direction (longue diagonale radiale), avec des courbes sinueuses qui
 *       ONDULENT en continu (somme de sinusoïdes animées : l'énergie serpente) et un tracé brisé qui CRÉPITE (zigzags tirés à
 *       nouveau tous les 2 ticks, comme une vraie décharge).</li>
 *   <li><b>Embranchements</b> : 2 à 4 fourches qui se détachent du canal en s'écartant, plus fines, dont certaines se divisent
 *       encore une fois ; elles apparaissent et disparaissent d'un crépitement à l'autre.</li>
 *   <li><b>Épaisseur organique</b> : épaisse à la racine, effilée au bout, avec des renflements irréguliers.</li>
 *   <li><b>Couleurs</b> : coeur NOIR profond, gaine rouge vif éclatante, et un halo rouge diffus autour du canal principal.</li>
 *   <li><b>Animation</b> : l'éclair JAILLIT (il pousse depuis le corps en un tick et demi), s'élance un peu dans sa direction,
 *       pulse d'intensité de façon irrégulière (surcharges), puis s'éteint en se rétractant vers sa pointe.</li>
 * </ul>
 * Chaque tronçon est un fin prisme étiré d'un point au suivant, qui chevauche ses voisins (ligne continue, sans coupure).
 * Direction : (rotation Y = yaw, angle depuis la verticale = pitch), même convention que ConquerorHaki.spawnBolt.
 */
public class HakiBoltRenderer extends EntityRenderer<HakiBolt, HakiBoltRenderer.BoltState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("haki_bolt"), "main");
    /** Texture blanche unie, teintée à chaque tronçon. */
    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/haki_eye_trail.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Points du canal principal. */
    private static final int MAIN_POINTS = 18;
    /** Épaisseur du coeur noir à la racine (pixels de modèle, 16 px = 1 bloc). */
    private static final float ROOT_WIDTH = 0.95F;
    private static final int CORE_COLOR = 0x050001;
    /** Largeurs relatives : coeur noir épais, gaine rouge juste un peu plus large (liseré), halo diffus. */
    private static final float CORE_SCALE = 1.7F;
    private static final float SHEATH_SCALE = 2.6F;
    private static final float HAZE_SCALE = 5.5F;
    private static final int SHEATH_COLOR = 0xFF1A1A;
    private static final int HAZE_COLOR = 0xE01020;

    public static class BoltState extends EntityRenderState {
        public float yaw;
        public float pitch;
        public float size = 1.0F;
        public int seed;
        public float age;
        public int life = 6;
        public boolean rising;
        public float drift = 1.0F;
        public float width = 1.0F;
    }

    /** Couche de modèle : un seul prisme d'un pixel de section, long d'un pixel vers +Z (étiré à chaque tronçon). */
    public static class BoltModel {
        private BoltModel() {
        }

        public static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("segment", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F),
                    PartPose.ZERO);
            return LayerDefinition.create(mesh, 16, 16);
        }
    }

    private final ModelPart segment;

    public HakiBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.segment = context.bakeLayer(LAYER).getChild("segment");
    }

    @Override
    public BoltState createRenderState() {
        return new BoltState();
    }

    @Override
    public void extractRenderState(HakiBolt entity, BoltState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yaw = entity.getYRot();
        state.pitch = entity.getXRot();
        state.size = Math.abs(entity.size());
        state.rising = entity.size() < 0.0F;
        state.seed = entity.seed();
        state.age = entity.tickCount + partialTick;
        state.life = Math.max(2, entity.life());
        state.drift = entity.drift();
        state.width = entity.width();
    }

    /** Un tracé d'éclair : points (blocs, repère de l'entité) et largeur (px) à chaque point. */
    private record Path(List<float[]> points, float[] widths, int level) {
    }

    @Override
    public void submit(BoltState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float age = state.age;
        float life = state.life;
        // DÉCHARGE LIBRE : la tête de l'éclair jaillit du corps et file vers l'extérieur (40 % de la vie), puis la queue se
        // détache du joueur et la suit : l'éclair entier s'éloigne et traverse l'espace avant de s'éteindre au loin
        float life01 = Math.min(1.0F, age / life);
        float head = smooth(Math.min(1.0F, life01 / 0.4F));
        float tail = life01 < 0.22F ? 0.0F : smooth((life01 - 0.22F) / 0.78F) * 0.92F;
        float grow = 1.0F;
        float fade = life01 < 0.75F ? 1.0F : Math.max(0.0F, (1.0F - life01) / 0.25F);
        if (head - tail <= 0.02F || fade <= 0.0F) {
            super.submit(state, pose, collector, camera);
            return;
        }
        // surcharges : l'intensité pulse de façon irrégulière, avec de brefs pics
        int crackle = (int) (age / 2.0F);
        Random flash = new Random(state.seed * 31L + crackle * 7919L);
        float surge = 0.7F + 0.3F * (float) Math.sin(age * 5.3F + state.seed) + (flash.nextFloat() < 0.25F ? 0.35F : 0.0F);
        float intensity = Math.min(1.25F, surge) * fade;

        float[] dir = direction(state);
        // longue portée : environ 3,5 à 5 blocs de canal, qui en plus s'éloigne du joueur pendant sa vie (jusqu'à ~7 blocs)
        float length = 5.4F * state.size;
        float push = life01 * life01 * 2.0F * state.drift;
        float[] origin = {dir[0] * push, dir[1] * push, dir[2] * push};

        List<Path> paths = new ArrayList<>();
        Random shape = new Random(state.seed * 1_000_003L);               // stable : direction des fourches
        Random jag = new Random(state.seed * 92_821L + crackle * 15_485_863L); // crépitement : change tous les 2 ticks
        // canal complet (forme stable, elle ne s'étire pas), dont on ne dessine que la portion [queue, tête] qui file
        Path full = channel(origin, dir, length * grow, MAIN_POINTS, ROOT_WIDTH, 0.12F, age, state.seed, shape, jag, 0);
        Path main = trim(full, tail, head);
        paths.add(main);
        int forks = 2 + shape.nextInt(3);
        for (int f = 0; f < forks; f++) {
            if (jag.nextFloat() < 0.25F) {
                continue; // une fourche manque à ce crépitement : elles clignotent
            }
            int at = 3 + shape.nextInt(MAIN_POINTS - 7);
            float atT = at / (float) (MAIN_POINTS - 1);
            if (atT < tail || atT > head) {
                continue; // la fourche ne naît que sur la partie visible de la décharge
            }
            float[] start = full.points().get(at);
            float[] forkDir = deviate(dir, 0.45F + shape.nextFloat() * 0.5F, shape);
            float forkLen = length * grow * (0.28F + shape.nextFloat() * 0.3F) * (1.0F - at / (float) MAIN_POINTS * 0.5F);
            forkLen *= Math.min(1.0F, (head - atT) * 3.0F + 0.3F); // elle pousse en même temps que la tête avance
            Path fork = channel(start, forkDir, forkLen, 8, full.widths()[at] * 0.55F, 0.1F, age, state.seed + f * 17, shape, jag, 1);
            paths.add(fork);
            if (shape.nextFloat() < 0.5F) {
                int at2 = 2 + shape.nextInt(4);
                float[] start2 = fork.points().get(Math.min(at2, fork.points().size() - 1));
                float[] dir2 = deviate(forkDir, 0.5F + shape.nextFloat() * 0.4F, shape);
                paths.add(channel(start2, dir2, forkLen * 0.45F, 5, fork.widths()[Math.min(at2, 7)] * 0.6F, 0.08F, age,
                        state.seed + f * 31, shape, jag, 2));
            }
        }

        RenderType glowType = RenderTypes.entityTranslucent(TEXTURE);
        RenderType coreType = RenderTypes.entityCutout(TEXTURE);
        // 1) coeur NOIR OPAQUE, épais : dessiné en premier, il reste bien noir (avant, il était enfermé dans la gaine rouge
        //    presque opaque, qui le recouvrait entièrement). En s'éteignant, il s'amincit (un opaque ne peut pas s'estomper).
        for (Path p : paths) {
            drawPath(pose, collector, 0, coreType, p, CORE_SCALE * (0.35F + 0.65F * fade) * state.width, argb(1.0F, CORE_COLOR));
        }
        // 2) gaine rouge vif, translucide : par-dessus le noir elle ne fait qu'un voile sombre, sur les bords (où il n'y a plus
        //    de noir derrière) elle brille en rouge éclatant -> liseré rouge net autour d'un corps noir
        for (Path p : paths) {
            drawPath(pose, collector, 1, glowType, p, SHEATH_SCALE * state.width, argb(Math.min(1.0F, 0.42F * intensity), SHEATH_COLOR));
        }
        // 3) halo rouge diffus (canal principal seulement)
        drawPath(pose, collector, 2, glowType, main, HAZE_SCALE * state.width, argb(0.12F * intensity, HAZE_COLOR));
        super.submit(state, pose, collector, camera);
    }

    private static float smooth(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Portion [from, to] (0 = racine, 1 = pointe) d'un canal, avec des extrémités interpolées : la décharge visible. Sa largeur
     * est maximale au milieu et s'effile aux deux bouts (une décharge libre n'est rattachée à rien).
     */
    private static Path trim(Path full, float from, float to) {
        List<float[]> pts = full.points();
        int n = pts.size();
        List<float[]> out = new ArrayList<>();
        List<Float> widths = new ArrayList<>();
        float maxWidth = 0.0F;
        for (float w : full.widths()) {
            maxWidth = Math.max(maxWidth, w);
        }
        int steps = Math.max(2, (int) Math.ceil((to - from) * (n - 1)) + 1);
        for (int k = 0; k < steps; k++) {
            float t = from + (to - from) * k / (steps - 1);
            float f = t * (n - 1);
            int i = Math.min(n - 2, (int) f);
            float r = f - i;
            float[] a = pts.get(i), b = pts.get(i + 1);
            out.add(new float[] {a[0] + (b[0] - a[0]) * r, a[1] + (b[1] - a[1]) * r, a[2] + (b[2] - a[2]) * r});
            float u = k / (float) (steps - 1);
            float bulge = full.widths()[i] / Math.max(1.0E-4F, maxWidth);
            widths.add(ROOT_WIDTH * (0.3F + 0.7F * (float) Math.sin(Math.PI * u)) * (0.7F + 0.3F * bulge));
        }
        float[] w = new float[widths.size()];
        for (int k = 0; k < w.length; k++) {
            w[k] = widths.get(k);
        }
        return new Path(out, w, full.level());
    }

    /** Direction unitaire de l'éclair (même convention que le serveur : rotation Y, puis angle depuis la verticale). */
    private static float[] direction(BoltState state) {
        double yaw = Math.toRadians(state.yaw);
        double p = Math.toRadians(state.pitch);
        if (!state.rising) {
            p += Math.PI; // ancienne convention (éclair non orienté) : pointe vers le bas
        }
        double h = Math.sin(p);
        return new float[] {(float) (h * Math.sin(yaw)), (float) Math.cos(p), (float) (h * Math.cos(yaw))};
    }

    /** Direction écartée de {@code base} d'un angle (radians), dans un sens tiré au hasard. */
    private static float[] deviate(float[] base, float angle, Random random) {
        float[][] basis = perpendiculars(base);
        double t = random.nextDouble() * Math.PI * 2;
        double c = Math.cos(angle), s = Math.sin(angle);
        float[] d = new float[3];
        for (int i = 0; i < 3; i++) {
            d[i] = (float) (base[i] * c + (basis[0][i] * Math.cos(t) + basis[1][i] * Math.sin(t)) * s);
        }
        return d;
    }

    /** Deux vecteurs unitaires perpendiculaires à {@code d} (et entre eux). */
    private static float[][] perpendiculars(float[] d) {
        float[] ref = Math.abs(d[1]) > 0.9F ? new float[] {1.0F, 0.0F, 0.0F} : new float[] {0.0F, 1.0F, 0.0F};
        float[] u = normalize(cross(d, ref));
        float[] v = normalize(cross(d, u));
        return new float[][] {u, v};
    }

    /**
     * Un canal de foudre : de {@code start}, sur {@code length} blocs dans la direction {@code dir}. Écart latéral = ondulation
     * douce animée (serpente) + zigzags du crépitement, nul à la racine. Largeur effilée vers la pointe, avec renflements.
     */
    private static Path channel(float[] start, float[] dir, float length, int count, float rootWidth, float sway, float age,
                                int seed, Random shape, Random jag, int level) {
        float[][] basis = perpendiculars(dir);
        float[] u = basis[0], v = basis[1];
        float p1 = shape.nextFloat() * 6.28F, p2 = shape.nextFloat() * 6.28F, p3 = shape.nextFloat() * 6.28F;
        float f1 = 1.3F + shape.nextFloat(), f2 = 2.6F + shape.nextFloat() * 1.5F;
        List<float[]> points = new ArrayList<>(count);
        float[] widths = new float[count];
        for (int i = 0; i < count; i++) {
            float t = i / (float) (count - 1);
            float envelope = (float) Math.pow(t, 0.75);
            // ondulation : courbes sinueuses qui se déplacent le long du canal (l'énergie serpente)
            float waveU = (float) (Math.sin(t * f1 * Math.PI + p1 + age * 0.9F) * 0.65 + Math.sin(t * f2 * Math.PI + p2 - age * 1.7F) * 0.35);
            float waveV = (float) (Math.sin(t * f1 * Math.PI * 0.8 + p3 - age * 1.1F) * 0.6 + Math.sin(t * f2 * Math.PI + p1 + age * 2.1F) * 0.4);
            // crépitement : brisures franches (pas de zigzag sur le premier point)
            float jagU = i == 0 ? 0.0F : (jag.nextFloat() - 0.5F) * 2.0F;
            float jagV = i == 0 ? 0.0F : (jag.nextFloat() - 0.5F) * 2.0F;
            float offU = (waveU * sway + jagU * 0.045F) * length * envelope;
            float offV = (waveV * sway + jagV * 0.045F) * length * envelope;
            float along = t * length;
            float[] p = new float[3];
            for (int k = 0; k < 3; k++) {
                p[k] = start[k] + dir[k] * along + u[k] * offU + v[k] * offV;
            }
            points.add(p);
            float taper = 1.0F - 0.8F * t;
            float bulge = 0.75F + 0.5F * (float) Math.abs(Math.sin(t * 9.0F + seed + age * 0.6F));
            widths[i] = rootWidth * taper * bulge;
        }
        return new Path(points, widths, level);
    }

    /** Dessine un tracé : chaque tronçon d'un point au suivant, largeur = largeur du tracé x {@code scale}. */
    private void drawPath(PoseStack pose, SubmitNodeCollector collector, int order, RenderType type, Path path, float scale, int color) {
        List<float[]> pts = path.points();
        for (int i = 0; i < pts.size() - 1; i++) {
            float width = (path.widths()[i] + path.widths()[i + 1]) * 0.5F * scale;
            drawSegment(pose, collector, order, type, pts.get(i), pts.get(i + 1), width, color);
        }
    }

    /** Prisme de section {@code width} (px) de a vers b (blocs), prolongé d'une demi-largeur aux deux bouts (jointures). */
    private void drawSegment(PoseStack pose, SubmitNodeCollector collector, int order, RenderType type, float[] a, float[] b, float width,
                             int color) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5F) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float overlap = width * 0.5F / 16.0F;
        pose.pushPose();
        pose.translate(a[0], a[1], a[2]);
        pose.rotateDegrees(Axis.YP, yaw);
        pose.rotateDegrees(Axis.XP, pitch);
        pose.translate(0.0F, 0.0F, -overlap);
        pose.scale(width, width, (len + 2.0F * overlap) * 16.0F);
        collector.order(order).submitModelPart(this.segment, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float[] normalize(float[] a) {
        float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return l < 1.0E-6F ? new float[] {1.0F, 0.0F, 0.0F} : new float[] {a[0] / l, a[1] / l, a[2] / l};
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
