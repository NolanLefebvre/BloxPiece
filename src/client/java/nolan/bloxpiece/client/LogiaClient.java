package nolan.bloxpiece.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.fruit.LogiaIntangibility;
import nolan.bloxpiece.registry.ModParticles;

/**
 * Passif Logia du fruit du Feu, côté client : le membre dispersé est invisible et, à sa place, un amas très dense de flammes de feu
 * pur jaillit en permanence de son volume, pour tous les joueurs visibles (aucun cube ni texture : uniquement des particules).
 *
 * Les particules sont placées à partir de la pose RÉELLE du membre (pivot et rotations calculés par les animations du modèle, que
 * LogiaFlameLayer relève ici à chaque image ; pose de repos si le joueur n'est pas dessiné, par exemple soi-même à la première
 * personne), puis convertie dans le monde comme le fait le rendu du joueur. Elles prennent
 * la vitesse du joueur, ne ralentissent pas et ne vivent que 5 à 8 ticks (style FIRE_LIMB) : elles restent collées au membre au
 * lieu de traîner derrière lui quand il marche, court ou saute.
 */
public final class LogiaClient {

    /** Pose (x, y, z en px ; xRot, yRot, zRot en radians) de chaque membre, par entité, à la dernière image dessinée. */
    private static final Map<Integer, float[][]> POSES = new HashMap<>();

    /** Boîtes des membres (px, repère du membre) dans l'ordre des bits de LogiaIntangibility (voir LogiaFlameLayer). */
    private static final float[][] BOXES = {
            {-4, -8, -4, 8, 8, 8}, {-4, 0, -2, 8, 12, 4},
            {-3, -2, -2, 4, 12, 4}, {-1, -2, -2, 4, 12, 4},
            {-2, 0, -2, 4, 12, 4}, {-2, 0, -2, 4, 12, 4}};

    /** Flammes émises par membre et par tick (tête et torse un peu plus). */
    private static final int LIMB_FLAMES = 36;
    private static final int TRUNK_FLAMES = 56;
    /** Retrait (px) depuis les bords du membre : la flamme (~1,2 px de rayon) ne déborde pas de la silhouette. */
    private static final double INSET = 1.0;
    /** Part des flammes posées sur la surface du membre (le reste remplit l'intérieur) : la silhouette reste nette. */
    private static final double SURFACE_SHARE = 0.75;

    /** Pose de repos des membres (pivots du modèle du joueur) quand aucune pose n'a été relevée. */
    private static final float[][] REST = {
            {0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0}, {-5, 2, 0, 0, 0, 0}, {5, 2, 0, 0, 0, 0},
            {-1.9F, 12, 0, 0, 0, 0}, {1.9F, 12, 0, 0, 0, 0}};

    private LogiaClient() {
    }

    private static float[][] copy(float[][] source) {
        float[][] out = new float[source.length][];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i].clone();
        }
        return out;
    }

    /** Appelé par LogiaFlameLayer après les animations : mémorise la pose des membres de ce joueur. */
    public static void record(int entityId, ModelPart[] limbs) {
        float[][] poses = POSES.computeIfAbsent(entityId, id -> new float[6][6]);
        for (int i = 0; i < 6; i++) {
            ModelPart p = limbs[i];
            float[] d = poses[i];
            d[0] = p.x; d[1] = p.y; d[2] = p.z;
            d[3] = p.xRot; d[4] = p.yRot; d[5] = p.zRot;
        }
    }

    /** À chaque tick client : flammes dans le volume des membres dispersés. */
    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || client.isPaused()) {
            POSES.clear();
            return;
        }
        RandomSource random = level.getRandom();
        // Tous les joueurs avec un membre dispersé, même ceux qui ne sont pas dessinés (soi-même à la première personne)
        for (Entity entity : level.players()) {
            if (entity.getAttached(ModAttachments.LOGIA_PARTS) != null) {
                POSES.computeIfAbsent(entity.getId(), id -> copy(REST));
            }
        }
        POSES.entrySet().removeIf(entry -> {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || living.isRemoved()) {
                return true;
            }
            Integer parts = living.getAttached(ModAttachments.LOGIA_PARTS);
            if (parts == null || living.isInvisible()) {
                return parts == null;
            }
            Vec3 velocity = living.getDeltaMovement();
            // Élément : flammes (Feu) ou éclats de glace (Glace, bit ICE envoyé par le serveur)
            var particle = (parts & LogiaIntangibility.ICE) != 0 ? ModParticles.ICE_SHARD : ModParticles.FIRE_LIMB;
            // Soi-même à la première personne : pas de flammes de tête collées à la caméra (elles cacheraient toute la vue)
            boolean ownView = living == client.player && client.options.getCameraType().isFirstPerson();
            for (int i = 0; i < 6; i++) {
                if ((parts & (1 << i)) == 0 || (ownView && i == 0)) {
                    continue;
                }
                int count = i <= 1 ? TRUNK_FLAMES : LIMB_FLAMES;
                float[] b = BOXES[i];
                for (int n = 0; n < count; n++) {
                    double[] local = pointIn(b, random, random.nextDouble() < SURFACE_SHARE);
                    Vec3 p = toWorld(living, entry.getValue()[i], local[0], local[1], local[2]);
                    // Aucune vitesse propre : la flamme reste à sa place sur le membre, elle suit seulement le joueur
                    level.addParticle(particle, p.x, p.y, p.z, velocity.x, velocity.y, velocity.z);
                }
            }
            return false;
        });
    }

    /**
     * Un point du membre (px, repère du membre), légèrement en retrait des bords : sur sa surface (une face choisie selon son
     * aire, pour une densité régulière), ou à l'intérieur.
     */
    private static double[] pointIn(float[] b, RandomSource random, boolean surface) {
        double x0 = b[0] + INSET, y0 = b[1] + INSET, z0 = b[2] + INSET;
        double w = b[3] - 2 * INSET, h = b[4] - 2 * INSET, d = b[5] - 2 * INSET;
        double x = x0 + w * random.nextDouble(), y = y0 + h * random.nextDouble(), z = z0 + d * random.nextDouble();
        if (surface) {
            double xy = w * h, xz = w * d, yz = h * d;
            double pick = random.nextDouble() * (xy + xz + yz);
            boolean far = random.nextBoolean();
            if (pick < xy) {
                z = far ? z0 + d : z0;      // face avant / arrière
            } else if (pick < xy + xz) {
                y = far ? y0 + h : y0;      // dessus / dessous
            } else {
                x = far ? x0 + w : x0;      // côtés
            }
        }
        return new double[] {x, y, z};
    }

    /**
     * Point (lx, ly, lz) du repère d'un membre (px) -> monde, comme le rendu du joueur : rotation du membre (ordre X puis Y puis Z,
     * comme ModelPart), pivot, passage du repère du modèle (24 px = pieds, axes X et Y inversés) au repère du corps, rotation du
     * corps, taille du joueur, position.
     */
    private static Vec3 toWorld(LivingEntity entity, float[] pose, double lx, double ly, double lz) {
        // rotations du membre : X, puis Y, puis Z
        double cx = Math.cos(pose[3]), sx = Math.sin(pose[3]);
        double y1 = ly * cx - lz * sx, z1 = ly * sx + lz * cx, x1 = lx;
        double cy = Math.cos(pose[4]), sy = Math.sin(pose[4]);
        double x2 = x1 * cy + z1 * sy, z2 = -x1 * sy + z1 * cy, y2 = y1;
        double cz = Math.cos(pose[5]), sz = Math.sin(pose[5]);
        double x3 = x2 * cz - y2 * sz, y3 = x2 * sz + y2 * cz, z3 = z2;
        // repère du modèle (px) -> repère du corps (blocs) : le rendu retourne X et Y et place le pivot du modèle à 1,501 bloc
        double bx = -(x3 + pose[0]) / 16.0;
        double by = -(y3 + pose[1]) / 16.0 + 1.501;
        double bz = (z3 + pose[2]) / 16.0;
        // rotation du corps (180 - yBodyRot autour de Y), taille, position
        double yaw = Math.toRadians(180.0F - entity.yBodyRot);
        double c = Math.cos(yaw), s = Math.sin(yaw);
        double scale = entity.getScale();
        double wx = (bx * c + bz * s) * scale;
        double wz = (-bx * s + bz * c) * scale;
        return new Vec3(entity.getX() + wx, entity.getY() + by * scale, entity.getZ() + wz);
    }
}
