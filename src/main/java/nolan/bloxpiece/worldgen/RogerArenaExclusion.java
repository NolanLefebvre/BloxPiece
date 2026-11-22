package nolan.bloxpiece.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import nolan.bloxpiece.BloxPiece;

/**
 * Où l'Arène de Gol D. Roger a le droit d'apparaître (appliqué au choix de l'emplacement, AVANT toute génération :
 * StructureSpawnExclusionMixin, exactement comme le Camp de bandits, voir BanditCampExclusion). Un emplacement refusé ne donne
 * aucune arène (pas de blocs, pas de Roger ; /locate ne la trouve pas) : jamais d'arène à moitié posée.
 * <ul>
 *   <li>Biomes (structure/roger_arena.json, tags/worldgen/biome/has_structure/roger_arena.json) : plaines, plaines de
 *       tournesols, prairies, savanes ; vérifiés par Minecraft au point de départ.</li>
 *   <li>Terrain : l'arène (disque de 45 blocs) pivote autour du coin du tronçon d'ancrage, son centre peut donc être à l'un des
 *       4 coins décalés de 22 blocs. Sur les 4 disques possibles, l'écart entre le point le plus haut et le plus bas ne dépasse
 *       pas {@link #MAX_RELIEF} blocs : ni montagne, ni falaise, ni ravin ; le raccord vanilla « beard_thin » comble ensuite
 *       ces petits écarts (rien n'est suspendu ni enterré).</li>
 *   <li>Eau : aucun point mesuré sous l'eau (lac, rivière, mer, marécage).</li>
 *   <li>Pas trop près du spawn ni du village de départ (MarineBaseExclusion.isForbidden, même règle que le camp).</li>
 *   <li>Autres structures : jamais près d'une Base de la Marine (MarineBaseExclusion refuse toute structure de surface à moins de
 *       8 tronçons d'une base), ni d'un village (exclusion_zone de structure_set/roger_arenas.json) ; posée sur la surface (point
 *       de départ projeté sur la surface du terrain, pas dans une grotte).</li>
 * </ul>
 * Calcul léger : une centaine de hauteurs de terrain lues sur le générateur (aucun bloc chargé), seulement pour les rares
 * tronçons candidats.
 */
public final class RogerArenaExclusion {

    public static final Identifier ROGER_ARENA = BloxPiece.id("roger_arena");
    /** Écart de hauteur maximal accepté sur la zone de l'arène (blocs). */
    public static final int MAX_RELIEF = 4;
    /** Rayon de l'arène (blocs) et décalage de son centre par rapport au point d'ancrage (centre du gabarit 45 x 45). */
    private static final int RADIUS = 23;
    private static final int CENTER_OFFSET = 22;
    private static final int PROBE_STEP = 8;

    private RogerArenaExclusion() {
    }

    /** Cette structure est-elle l'Arène de Roger ? */
    public static boolean isArena(Structure.GenerationContext context, Structure structure) {
        return ROGER_ARENA.equals(context.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure));
    }

    /** L'arène proposée sur ce tronçon doit-elle être refusée (spawn, relief, eau) ? */
    public static boolean refuse(Structure.GenerationContext context) {
        int x0 = context.chunkPos().getMinBlockX(), z0 = context.chunkPos().getMinBlockZ(); // point d'ancrage de l'arène
        if (MarineBaseExclusion.isForbidden(x0 + 8, z0 + 8)) {
            return true;
        }
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int cx = x0 + sx * CENTER_OFFSET, cz = z0 + sz * CENTER_OFFSET; // centre possible (selon la rotation)
                for (int dx = -RADIUS; dx <= RADIUS; dx += PROBE_STEP) {
                    for (int dz = -RADIUS; dz <= RADIUS; dz += PROBE_STEP) {
                        if (dx * dx + dz * dz > RADIUS * RADIUS) {
                            continue;
                        }
                        int surface = height(context, cx + dx, cz + dz, Heightmap.Types.WORLD_SURFACE_WG);
                        int floor = height(context, cx + dx, cz + dz, Heightmap.Types.OCEAN_FLOOR_WG);
                        if (surface > floor) {
                            return true; // de l'eau au-dessus du sol à cet endroit
                        }
                        min = Math.min(min, surface);
                        max = Math.max(max, surface);
                        if (max - min > MAX_RELIEF) {
                            return true; // terrain trop accidenté
                        }
                    }
                }
            }
        }
        return false;
    }

    private static int height(Structure.GenerationContext context, int x, int z, Heightmap.Types type) {
        return context.chunkGenerator().getBaseHeight(x, z, type, context.heightAccessor(), context.randomState());
    }
}
