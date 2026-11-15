package nolan.bloxpiece.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Raycast côté serveur : trouve le point que le joueur vise avec son curseur.
 * Le serveur connaît la position des yeux et la direction du regard de chaque joueur (le client les lui envoie
 * en permanence) : c'est donc le serveur qui décide, un client ne peut pas "tricher" sur la cible.
 */
public final class AimHelper {

    private static final double STEP = 0.1;      // précision du rayon (en blocs)
    private static final int REFINE_STEPS = 6;   // affinage du point d'impact

    private AimHelper() {
    }

    /**
     * Point visé par le joueur, juste devant la première surface touchée (sol, mur, eau...),
     * ou null si le curseur ne vise rien dans les {@code maxDistance} blocs (ciel, trop loin, zone non chargée).
     */
    public static Vec3 findTarget(ServerPlayer player, double maxDistance) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle();

        Vec3 lastFree = eye;
        for (double distance = STEP; distance <= maxDistance; distance += STEP) {
            Vec3 point = eye.add(direction.scale(distance));
            if (isBlocked(level, point)) {
                return refine(level, lastFree, point);
            }
            lastFree = point;
        }
        return null;
    }

    /** Hauteur libre au-dessus d'un point, en blocs (jusqu'à {@code max}) : s'arrête au premier plafond. */
    public static double clearanceAbove(ServerLevel level, Vec3 from, double max) {
        for (double height = 0.5; height <= max; height += 0.5) {
            Vec3 point = from.add(0, height, 0);
            BlockPos pos = BlockPos.containing(point.x, point.y, point.z);
            if (level.isOutsideBuildHeight(pos) || isBlocked(level, point)) {
                return height - 0.5;
            }
        }
        return max;
    }

    /** Un point est "bloqué" s'il est dans un bloc solide ou dans un liquide. Les herbes et fleurs ne bloquent pas. */
    public static boolean isBlocked(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point.x, point.y, point.z);
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return false; // hors du monde ou zone non chargée : on ne charge rien, le rayon continue
        }
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty();
    }

    /** Affine le point d'impact entre le dernier point libre et le premier point bloqué (dichotomie). */
    private static Vec3 refine(ServerLevel level, Vec3 free, Vec3 blocked) {
        for (int i = 0; i < REFINE_STEPS; i++) {
            Vec3 middle = free.add(blocked).scale(0.5);
            if (isBlocked(level, middle)) {
                blocked = middle;
            } else {
                free = middle;
            }
        }
        return free;
    }
}
