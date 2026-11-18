package nolan.bloxpiece.client.haki;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/**
 * Haki de l'Observation : trajectoire des deux yeux pendant une esquive, pour le RUBAN rouge continu dessiné par
 * HakiEyeTrailLayer (plus de particules : une vraie ligne lisse).
 *
 * Les points sont relevés à CHAQUE IMAGE par PlayerModelMixin, juste après l'animation, dans le repère du MODÈLE du joueur
 * (pixels) : la position exacte des pupilles avec la pose réelle de la tête (pivot et rotations de l'esquive). Le ruban est
 * donc attaché au corps et suit strictement la tête, sans dépendre des ticks ni de la position du joueur dans le monde. Chaque
 * point garde aussi la direction « arrière » de la tête au moment où il a été posé : en vieillissant, il glisse dans cette
 * direction, ce qui étire le trait derrière le regard (comme dans l'anime).
 */
public final class HakiEyeTrail {

    /**
     * Yeux dans la tête (pixels, repère de la tête) : à 2 px du centre, 3,5 px au-dessus du cou. Le ruban part DEVANT la seconde
     * couche du skin (le « chapeau », qui dépasse le visage de 0,5 px) : il ne rentre jamais dans le crâne.
     */
    public static final float EYE_X = 2.0F;
    public static final float EYE_Y = -3.5F;
    public static final float EYE_Z = -4.6F;
    /** Durée de vie d'un point du ruban (ticks) : longueur de la traînée dans le temps. */
    public static final float LIFE = 9.0F;
    /**
     * Glissement des points (pixels par tick), dans une direction COMMUNE aux deux yeux (vers l'avant du visage, jamais dans le
     * crâne) : les deux traînées restent parallèles. Avant, chaque oeil s'écartait du centre de la tête de son côté, donc les deux
     * traînées partaient dans des sens opposés.
     */
    public static final float DRIFT = 0.12F;
    /** Distance minimale entre deux points relevés (pixels) : ligne lisse sans surcharge. */
    private static final float MIN_STEP = 0.12F;

    /** Point du ruban : position (px, repère du modèle), direction arrière de la tête (unitaire), instant (ticks). */
    public record Point(float x, float y, float z, float bx, float by, float bz, float time) {
    }

    /** Par entité : les deux yeux (0 = droit, 1 = gauche), du plus ancien au plus récent. */
    private static final Map<Integer, Deque<Point>[]> TRAILS = new HashMap<>();

    private HakiEyeTrail() {
    }

    /** Instant présent (ticks, avec fraction) pour le ruban. */
    public static float now(float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0.0F : (float) (level.getGameTime() % 100000L) + partialTick;
    }

    /** Appelé par PlayerModelMixin après l'animation d'esquive (à chaque image, pendant l'esquive seulement). */
    @SuppressWarnings("unchecked")
    public static void record(Entity entity, ModelPart head, float partialTick) {
        float time = now(partialTick);
        Deque<Point>[] eyes = TRAILS.computeIfAbsent(entity.getId(), id -> new Deque[] {new ArrayDeque<>(), new ArrayDeque<>()});
        // les deux yeux sont relevés ENSEMBLE (ou pas du tout) : mêmes instants, donc deux traînées synchronisées
        float[] right = eye(head, 0);
        float[] left = eye(head, 1);
        Point last = eyes[0].peekLast();
        if (last != null && last.time() <= time) {
            float dx = right[0] - last.x(), dy = right[1] - last.y(), dz = right[2] - last.z();
            if (dx * dx + dy * dy + dz * dz < MIN_STEP * MIN_STEP && time - last.time() < 0.5F) {
                return;
            }
        }
        float[] out = forward(head);
        eyes[0].addLast(new Point(right[0], right[1], right[2], out[0], out[1], out[2], time));
        eyes[1].addLast(new Point(left[0], left[1], left[2], out[0], out[1], out[2], time));
    }

    /** Points encore vivants des deux yeux (les trop vieux sont retirés), ou null si aucune traînée. */
    public static Deque<Point>[] trail(int entityId, float time) {
        Deque<Point>[] eyes = TRAILS.get(entityId);
        if (eyes == null) {
            return null;
        }
        boolean any = false;
        for (Deque<Point> list : eyes) {
            while (!list.isEmpty() && (time - list.peekFirst().time() > LIFE || list.peekFirst().time() > time + 1.0F)) {
                list.removeFirst();
            }
            any |= !list.isEmpty();
        }
        if (!any) {
            boolean empty = eyes[0].isEmpty() && eyes[1].isEmpty();
            if (empty) {
                TRAILS.remove(entityId);
            }
            return null;
        }
        return eyes;
    }

    /** Position actuelle d'un oeil (0 = droit, 1 = gauche) dans le repère du modèle (px) : là où brille le point rouge. */
    public static float[] eye(ModelPart head, int eye) {
        float[] p = rotate(head, eye == 0 ? -EYE_X : EYE_X, EYE_Y, EYE_Z);
        return new float[] {p[0] + head.x, p[1] + head.y, p[2] + head.z};
    }

    /** Direction avant du visage (unitaire), la même pour les deux yeux. */
    private static float[] forward(ModelPart head) {
        return rotate(head, 0.0F, 0.0F, -1.0F);
    }

    /** Point (lx, ly, lz) de la tête -> repère du modèle, sans le pivot : rotations X, puis Y, puis Z (comme ModelPart). */
    private static float[] rotate(ModelPart head, float lx, float ly, float lz) {
        double cx = Math.cos(head.xRot), sx = Math.sin(head.xRot);
        double y1 = ly * cx - lz * sx, z1 = ly * sx + lz * cx, x1 = lx;
        double cy = Math.cos(head.yRot), sy = Math.sin(head.yRot);
        double x2 = x1 * cy + z1 * sy, z2 = -x1 * sy + z1 * cy, y2 = y1;
        double cz = Math.cos(head.zRot), sz = Math.sin(head.zRot);
        return new float[] {(float) (x2 * cz - y2 * sz), (float) (x2 * sz + y2 * cz), (float) z2};
    }

    public static void clear() {
        TRAILS.clear();
    }
}
