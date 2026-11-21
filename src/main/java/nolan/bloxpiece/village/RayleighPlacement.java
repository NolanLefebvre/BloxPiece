package nolan.bloxpiece.village;

import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.mob.Rayleigh;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModEntities;

/**
 * Silvers Rayleigh, devant la taverne du village de départ (le monde où le village a été construit : VILLAGE_CENTER).
 * <p>
 * Place (repère de la taverne, façade vers la place en +z) : sur la place, juste au pied du porche, 2 blocs à l'est de l'allée
 * qui descend de la porte double (x -1..0) : bien visible en arrivant du point d'apparition (devant la taverne), tourné vers
 * la place, sans gêner la porte, les marches du porche ni l'allée. Si cette case est occupée (lampadaire, banc...), la case
 * symétrique à l'ouest de l'allée, puis un peu plus loin sur la place.
 * <p>
 * Marche aussi pour un monde où le village existe DÉJÀ (Rayleigh ajouté après coup) : toutes les 5 s, si sa place est chargée
 * et qu'aucun Rayleigh n'est dans les environs, il y est posé. Aucun bloc n'est modifié. Il est sauvegardé avec le monde
 * (persistant) : après un redémarrage, il est simplement rechargé à sa place.
 */
public final class RayleighPlacement {

    /** Cases candidates (repère local de la taverne : x, z ; y = niveau de la place). */
    private static final int[][] SPOTS = {{2, 5}, {-3, 5}, {3, 6}, {-4, 6}};
    /** Rayon (blocs) dans lequel un Rayleigh existant compte comme « déjà là ». */
    private static final double SEARCH = 24.0;
    private static final int CHECK_TICKS = 100;

    private static int timer;

    private RayleighPlacement() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(RayleighPlacement::tick);
    }

    private static void tick(MinecraftServer server) {
        if (++timer < CHECK_TICKS) {
            return;
        }
        timer = 0;
        ServerLevel level = server.overworld();
        BlockPos center = level.getAttached(ModAttachments.VILLAGE_CENTER);
        BlockPos tavern = tavernOrigin(center);
        if (tavern == null) {
            return; // pas de village de départ dans ce monde
        }
        BlockPos probe = tavern.offset(0, 0, 5);
        if (!level.isPositionEntityTicking(probe)) {
            return; // personne près de la taverne : on réessaie plus tard (les entités du coin ne sont pas chargées)
        }
        List<Rayleigh> present = level.getEntitiesOfClass(Rayleigh.class, new AABB(probe).inflate(SEARCH));
        if (!present.isEmpty()) {
            return;
        }
        BlockPos spot = findSpot(level, tavern, center.getY());
        if (spot == null) {
            BloxPiece.LOGGER.warn("[BloxPiece] Rayleigh : aucune place libre devant la taverne ({})", tavern);
            return;
        }
        Rayleigh rayleigh = ModEntities.SILVERS_RAYLEIGH.create(level, EntitySpawnReason.STRUCTURE);
        if (rayleigh == null) {
            return;
        }
        float yaw = 0.0F; // la façade de la taverne (Rotation.NONE) regarde vers +z : vers la place
        rayleigh.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        rayleigh.setYRot(yaw);
        rayleigh.setYHeadRot(yaw);
        rayleigh.setYBodyRot(yaw);
        level.addFreshEntity(rayleigh);
        BloxPiece.LOGGER.info("[BloxPiece] Silvers Rayleigh placé devant la taverne en {}", spot);
    }

    /** Origine de la taverne (VillageLayout, entrée « Taverne »), ou null sans village. */
    private static BlockPos tavernOrigin(BlockPos center) {
        if (center == null) {
            return null;
        }
        for (VillageLayout.Entry entry : VillageLayout.BUILDINGS) {
            if (entry.name().equals("Taverne")) {
                return center.offset(entry.origin());
            }
        }
        return null;
    }

    /** Première case candidate avec un sol (pavé, chemin de terre...) et 2 blocs d'air au-dessus (cherchée autour du niveau de la place). */
    private static BlockPos findSpot(ServerLevel level, BlockPos tavern, int plazaY) {
        for (int[] s : SPOTS) {
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos feet = new BlockPos(tavern.getX() + s[0], plazaY + dy, tavern.getZ() + s[1]);
                BlockState ground = level.getBlockState(feet.below());
                if (!ground.getCollisionShape(level, feet.below()).isEmpty() && ground.getFluidState().isEmpty() // pavé, chemin...
                        && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) {
                    return feet;
                }
            }
        }
        return null;
    }
}
