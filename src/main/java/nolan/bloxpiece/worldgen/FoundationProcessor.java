package nolan.bloxpiece.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Intégration au relief des grandes structures posées à plat (Base de la Marine) :
 * <ul>
 *   <li><b>Fondation</b> : sous le bloc le plus bas de chaque colonne de la structure, de la brique de pierre jusqu'au premier
 *       sol solide (vide, eau, herbes... comblés), au plus {@link #MAX_DEPTH} blocs : elle ne flotte jamais.</li>
 *   <li><b>Raccord</b> : autour de l'emprise, sur {@link #BLEND} blocs, le terrain descend ou monte en pente douce jusqu'au niveau
 *       du sol de la structure (colline adoucie au lieu d'être tranchée, creux comblé au lieu d'une falaise de fondation). Le
 *       raccord reprend le bloc de surface d'origine (herbe, sable...) et celui juste dessous (terre, sable...).</li>
 *   <li>Au-dessus de l'emprise : tout ce qui dépasse du haut de la structure (sommet de colline) est retiré.</li>
 * </ul>
 * La structure doit laisser au moins {@link #BLEND} blocs vides autour de l'emprise dans son gabarit (marine_base.nbt) : ainsi tous
 * les tronçons du raccord sont traités. On ne lit et n'écrit que dans le tronçon en cours de génération.
 */
public class FoundationProcessor implements StructureProcessor { // interface en 26.3

    public static final FoundationProcessor INSTANCE = new FoundationProcessor();
    public static final MapCodec<FoundationProcessor> CODEC = MapCodec.unit(INSTANCE);

    private static final int MAX_DEPTH = 48;
    /** Largeur du raccord au terrain autour de l'emprise (blocs). */
    private static final int BLEND = 12;
    /** Au plus autant de blocs ajoutés ou retirés par colonne du raccord (gros ravin ou falaise : on n'insiste pas). */
    private static final int MAX_BLEND_CHANGE = 24;

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos offset, BlockPos pos,
            List<StructureTemplate.StructureBlockInfo> original, List<StructureTemplate.StructureBlockInfo> processed,
            StructurePlaceSettings settings) {
        BoundingBox box = settings.getBoundingBox(); // le tronçon généré : on ne lit le terrain que là
        if (box == null) {
            return processed;
        }
        // Emprise (rectangle des blocs non vides), haut de la structure, bloc le plus bas de chaque colonne
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE, top = Integer.MIN_VALUE;
        Map<Long, Integer> lowest = new HashMap<>();
        for (StructureTemplate.StructureBlockInfo info : processed) {
            BlockPos p = info.pos();
            top = Math.max(top, p.getY());
            if (info.state().isAir()) {
                continue;
            }
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            lowest.merge(BlockPos.asLong(p.getX(), 0, p.getZ()), p.getY(), Math::min);
        }
        if (lowest.isEmpty()) {
            return processed;
        }
        // Niveau du sol de la structure : la hauteur la plus fréquente du bloc le plus bas (le dallage de la cour)
        Map<Integer, Integer> counts = new HashMap<>();
        for (int y : lowest.values()) {
            counts.merge(y, 1, Integer::sum);
        }
        int ground = counts.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();

        List<StructureTemplate.StructureBlockInfo> result = new ArrayList<>(processed);
        BlockState fill = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                int dx = Math.max(Math.max(minX - x, x - maxX), 0);
                int dz = Math.max(Math.max(minZ - z, z - maxZ), 0);
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (dx == 0 && dz == 0) {
                    // Dans l'emprise : fondation sous la structure, sommet de colline retiré au-dessus
                    Integer low = lowest.get(BlockPos.asLong(x, 0, z));
                    if (low != null) {
                        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos(x, low - 1, z);
                        for (int i = 0; i < MAX_DEPTH; i++, below.move(0, -1, 0)) {
                            BlockState under = level.getBlockState(below);
                            if (!under.isAir() && !under.canBeReplaced() && under.getFluidState().isEmpty()) {
                                break; // sol solide atteint
                            }
                            result.add(new StructureTemplate.StructureBlockInfo(below.immutable(), fill, null));
                        }
                    }
                    for (int y = top + 1; y <= surface; y++) {
                        result.add(new StructureTemplate.StructureBlockInfo(new BlockPos(x, y, z), air, null));
                    }
                    continue;
                }
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > BLEND) {
                    continue;
                }
                // Raccord : hauteur visée entre le sol de la structure (contre l'emprise) et le terrain d'origine (au bout)
                double t = distance / BLEND;
                double weight = t * t * (3.0 - 2.0 * t);
                int terrain = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                int target = ground + (int) Math.round((terrain - ground) * weight);
                target = Math.max(terrain - MAX_BLEND_CHANGE, Math.min(terrain + MAX_BLEND_CHANGE, target));
                if (target == terrain) {
                    continue;
                }
                BlockState topBlock = level.getBlockState(new BlockPos(x, terrain, z));
                BlockState subBlock = level.getBlockState(new BlockPos(x, terrain - 1, z));
                if (topBlock.isAir() || !topBlock.getFluidState().isEmpty()) {
                    topBlock = Blocks.GRASS_BLOCK.defaultBlockState();
                }
                if (subBlock.isAir() || !subBlock.getFluidState().isEmpty()) {
                    subBlock = Blocks.DIRT.defaultBlockState();
                }
                if (target < terrain) {
                    // Colline adoucie : on retire le dessus, on remet la surface d'origine au nouveau niveau
                    for (int y = target + 1; y <= Math.max(terrain, surface); y++) {
                        result.add(new StructureTemplate.StructureBlockInfo(new BlockPos(x, y, z), air, null));
                    }
                    result.add(new StructureTemplate.StructureBlockInfo(new BlockPos(x, target, z), topBlock, null));
                    result.add(new StructureTemplate.StructureBlockInfo(new BlockPos(x, target - 1, z), subBlock, null));
                } else {
                    // Creux comblé : remblai de terre (ou sable...) coiffé de la surface d'origine
                    for (int y = terrain; y < target; y++) {
                        result.add(new StructureTemplate.StructureBlockInfo(new BlockPos(x, y, z), subBlock, null));
                    }
                    result.add(new StructureTemplate.StructureBlockInfo(new BlockPos(x, target, z), topBlock, null));
                }
            }
        }
        return result;
    }

    /** En 26.3 un processeur donne directement son codec (plus de getType). */
    @Override
    public MapCodec<? extends StructureProcessor> codec() {
        return CODEC;
    }
}
