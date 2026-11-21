package nolan.bloxpiece.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import nolan.bloxpiece.BloxPiece;

/**
 * Où le Camp de bandits a le droit d'apparaître (appliqué au choix de l'emplacement, AVANT toute génération :
 * StructureSpawnExclusionMixin, comme pour la Base de la Marine). Un emplacement refusé ne donne aucun camp (pas de blocs, pas
 * de bandits ; /locate ne le trouve pas) : jamais de camp à moitié posé.
 * <ul>
 *   <li>Biomes (structure/bandit_camp.json, tags/worldgen/biome/has_structure/bandit_camp.json) : plaines, plaines de tournesols,
 *       prairies, savanes ; vérifiés par Minecraft au point de départ.</li>
 *   <li>Terrain : sur toute la zone que le camp peut couvrir (quelle que soit sa rotation), l'écart entre le point le plus haut et
 *       le plus bas ne dépasse pas {@link #MAX_RELIEF} blocs : ni pente raide, ni montagne, ni ravin ; le raccord vanilla
 *       « beard_thin » comble ensuite ces petits écarts (rien n'est suspendu ni enterré).</li>
 *   <li>Eau : aucun point de la zone sous l'eau (lac, rivière, mer, marécage) : jamais de camp dans l'eau ni au bord d'un plan
 *       d'eau qui le noierait.</li>
 *   <li>Pas trop près du spawn ni du village de départ (même règle que la Base de la Marine : MarineBaseExclusion.isForbidden).</li>
 *   <li>Autres structures : jamais près d'une Base de la Marine (MarineBaseExclusion refuse toute structure de surface à moins de
 *       8 tronçons d'une base), ni d'un village (exclusion_zone de structure_set/bandit_camps.json) ; posé sur la surface (pas
 *       dans une grotte : point de départ projeté sur la surface du terrain).</li>
 * </ul>
 * Calcul léger : quelques dizaines de hauteurs de terrain lues sur le générateur (aucun bloc chargé).
 */
public final class BanditCampExclusion {

    public static final Identifier BANDIT_CAMP = BloxPiece.id("bandit_camp");
    /** Écart de hauteur maximal accepté sur la zone du camp (blocs). */
    public static final int MAX_RELIEF = 3;
    /** Demi-côté de la zone mesurée autour du point d'ancrage (blocs) : le camp fait 31 x 27 et peut tourner autour de ce point. */
    private static final int AREA = 32;
    private static final int PROBE_STEP = 8;

    private BanditCampExclusion() {
    }

    /** Cette structure est-elle le Camp de bandits ? */
    public static boolean isCamp(Structure.GenerationContext context, Structure structure) {
        return BANDIT_CAMP.equals(context.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure));
    }

    /** Le camp proposé sur ce tronçon doit-il être refusé (spawn, relief, eau) ? */
    public static boolean refuse(Structure.GenerationContext context) {
        int x0 = context.chunkPos().getMinBlockX(), z0 = context.chunkPos().getMinBlockZ(); // point d'ancrage du camp
        if (MarineBaseExclusion.isForbidden(x0 + 8, z0 + 8)) {
            return true;
        }
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = -AREA; dx <= AREA; dx += PROBE_STEP) {
            for (int dz = -AREA; dz <= AREA; dz += PROBE_STEP) {
                int surface = height(context, x0 + dx, z0 + dz, Heightmap.Types.WORLD_SURFACE_WG);
                int floor = height(context, x0 + dx, z0 + dz, Heightmap.Types.OCEAN_FLOOR_WG);
                if (surface > floor) {
                    return true; // de l'eau au-dessus du sol à cet endroit
                }
                min = Math.min(min, surface);
                max = Math.max(max, surface);
                if (max - min > MAX_RELIEF) {
                    return true; // pente trop forte
                }
            }
        }
        return false;
    }

    private static int height(Structure.GenerationContext context, int x, int z, Heightmap.Types type) {
        return context.chunkGenerator().getBaseHeight(x, z, type, context.heightAccessor(), context.randomState());
    }
}
