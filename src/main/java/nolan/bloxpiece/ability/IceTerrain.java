package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Gel TEMPORAIRE du terrain (fruit de la Glace) : l'eau devient de la glace et la surface du sol de la glace compactée, puis tout
 * redevient comme avant au bout d'un moment (chaque bloc avec un petit décalage aléatoire, pour un dégel progressif).
 *
 * Sécurités : seulement si la règle mobGriefing est active ; jamais de bloc avec un contenu (coffre...), ni de bloc incassable,
 * ni de bloc qui n'est pas plein ; seulement si le dessus est libre (une fleur ou une torche ne casse jamais). Un bloc gelé que
 * quelqu'un a cassé ou remplacé entre-temps n'est pas restauré par-dessus. Tout est restauré à l'arrêt du serveur.
 */
public final class IceTerrain {

    private record Frozen(ServerLevel level, BlockState original, BlockState placed, long until) {
    }

    private static final Map<BlockPos, Frozen> BLOCKS = new HashMap<>();

    private IceTerrain() {
    }

    /**
     * Gèle la colonne (x, z) : le premier bloc de surface trouvé entre {@code y + 2} et {@code y - 4}. Renvoie la hauteur du dessus
     * de la surface gelée (ou NaN si rien n'a été trouvé), utile pour poser des pics de glace.
     */
    public static double freezeColumn(ServerLevel level, int x, int y, int z, int durationTicks) {
        boolean allowed = level.getGameRules().get(GameRules.MOB_GRIEFING);
        for (int dy = 2; dy >= -4; dy--) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            BlockState top = level.getBlockState(pos.above());
            if (state.is(Blocks.WATER)) {
                if (!top.getFluidState().isEmpty()) {
                    continue; // eau profonde : seule la surface gèle
                }
                if (top.isAir() && state.getFluidState().isSource() && allowed) {
                    place(level, pos, state, Blocks.ICE.defaultBlockState(), durationTicks);
                }
                return pos.getY() + 1.0;
            }
            if (!state.getFluidState().isEmpty()) {
                return Double.NaN; // lave, eau courante... : rien
            }
            boolean freezable = allowed && top.isAir() && state.isCollisionShapeFullBlock(level, pos)
                    && level.getBlockEntity(pos) == null && state.getDestroySpeed(level, pos) >= 0.0F
                    && !state.is(Blocks.ICE) && !state.is(Blocks.PACKED_ICE) && !state.is(Blocks.SNOW_BLOCK) && !BLOCKS.containsKey(pos);
            if (freezable) {
                // surtout de la glace compactée, avec des plaques de neige pour un rendu naturel
                BlockState ice = level.getRandom().nextFloat() < 0.25F ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
                place(level, pos, state, ice, durationTicks);
            }
            return pos.getY() + 1.0;
        }
        return Double.NaN;
    }

    /** Gèle un disque de sol et d'eau de rayon {@code radius} autour de {@code center} (impact du trident, de l'aigle). */
    public static void freezeDisc(ServerLevel level, net.minecraft.world.phys.Vec3 center, double radius, int durationTicks) {
        int r = (int) Math.ceil(radius);
        int cx = net.minecraft.util.Mth.floor(center.x), cy = net.minecraft.util.Mth.floor(center.y), cz = net.minecraft.util.Mth.floor(center.z);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    freezeColumn(level, cx + dx, cy, cz + dz, durationTicks);
                }
            }
        }
    }

    /**
     * Amas de glace dans l'air autour de {@code center} (impact de l'aigle) : des blocs de glace poussent dans les cases vides
     * jusqu'à {@code radius} blocs, de plus en plus clairsemés vers le bord, puis fondent (redeviennent de l'air). Jamais dans une
     * case occupée par une entité (personne n'étouffe dans la glace).
     */
    public static void iceCluster(ServerLevel level, net.minecraft.world.phys.Vec3 center, double radius, int durationTicks) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return;
        }
        int r = (int) Math.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -1; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
                    if (d > radius || level.getRandom().nextDouble() > 0.85 * (1.0 - d / radius) + 0.05) {
                        continue;
                    }
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() || BLOCKS.containsKey(pos)
                            || !level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(pos)).isEmpty()) {
                        continue;
                    }
                    BlockState ice = level.getRandom().nextFloat() < 0.6F ? Blocks.ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
                    place(level, pos, state, ice, durationTicks);
                }
            }
        }
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState original, BlockState placed, int durationTicks) {
        long until = level.getGameTime() + durationTicks + level.getRandom().nextInt(60);
        BLOCKS.put(pos.immutable(), new Frozen(level, original, placed, until));
        level.setBlock(pos, placed, 3);
    }

    /** À appeler à chaque tick serveur (ModEvents) : dégèle les blocs arrivés à échéance. */
    public static void tick() {
        if (BLOCKS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<BlockPos, Frozen>> it = BLOCKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Frozen> entry = it.next();
            Frozen f = entry.getValue();
            if (f.level().getGameTime() >= f.until()) {
                restore(entry.getKey(), f);
                it.remove();
            }
        }
    }

    private static void restore(BlockPos pos, Frozen f) {
        if (f.level().getBlockState(pos) == f.placed()) {
            f.level().setBlock(pos, f.original(), 3);
        }
    }

    /** Restaure immédiatement tout le terrain gelé (arrêt du serveur). */
    public static void clear() {
        BLOCKS.forEach(IceTerrain::restore);
        BLOCKS.clear();
    }
}
