package nolan.bloxpiece.worldgen;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.village.StarterVillage;

/**
 * Où la Base de la Marine a le droit d'apparaître (appliqué au choix de l'emplacement : StructureSpawnExclusionMixin). Une base
 * refusée n'existe pas du tout (pas de blocs, pas de Marines, /locate ne la trouve pas).
 * <ul>
 *   <li>Jamais à moins de {@link #SPAWN_RADIUS} blocs du spawn du monde (le village de départ est à 384 blocs du spawn au plus),
 *       ni à moins de {@link #VILLAGE_RADIUS} blocs du village de départ une fois construit.</li>
 *   <li>Terrain assez plat : sur toute la zone que la base peut couvrir (quelle que soit sa rotation), l'écart entre le point le
 *       plus haut et le plus bas ne dépasse pas {@link #MAX_RELIEF} blocs. Flancs de montagne, falaises : refusés.</li>
 *   <li>Aucune autre structure de surface (avant-poste de pillards, temple, cabane, structure d'un autre mod...) à moins de
 *       {@link #CLEARANCE_CHUNKS} tronçons d'une base qui va réellement apparaître : c'est cette autre structure qui est refusée.
 *       Les villages vanilla sont déjà tenus à distance par exclusion_zone (structure_set/marine_bases.json).</li>
 * </ul>
 * Les emplacements possibles des bases sont recalculés comme le fait Minecraft (placement "random_spread") : {@link #SPACING},
 * {@link #SEPARATION} et {@link #SALT} doivent rester identiques à structure_set/marine_bases.json.
 */
public final class MarineBaseExclusion {

    public static final Identifier MARINE_BASE = BloxPiece.id("marine_base");
    public static final int SPAWN_RADIUS = 500;
    public static final int VILLAGE_RADIUS = 400;

    /** Écart de hauteur maximal (blocs) accepté sur la zone de la base. */
    public static final int MAX_RELIEF = 10;
    /** Zone mesurée autour du point d'ancrage (blocs) et pas de la mesure : la base fait ~80 x 70 blocs, plus le raccord. */
    private static final int AREA = 104;
    private static final int PROBE_STEP = 26;
    /** Distance minimale (tronçons) entre une base et toute autre structure de surface. */
    public static final int CLEARANCE_CHUNKS = 8;

    /** Placement de structure_set/marine_bases.json (à garder identique). */
    private static final int SPACING = 32;
    private static final int SEPARATION = 12;
    private static final int SALT = 27183049;

    private static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME, BloxPiece.id("has_structure/marine_base"));

    /** Le serveur en cours (le spawn y est lu) ; null avant son démarrage : le spawn est alors supposé en 0, 0. */
    private static volatile MinecraftServer server;
    /** Résultat déjà calculé pour un emplacement possible de base (graine + tronçon). */
    private static final Map<Long, Boolean> CACHE = new ConcurrentHashMap<>();

    private MarineBaseExclusion() {
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            CACHE.clear();
        });
    }

    /** Cette structure est-elle la Base de la Marine ? */
    public static boolean isMarineBase(Structure.GenerationContext context, Structure structure) {
        return MARINE_BASE.equals(context.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure));
    }

    /** La base proposée sur ce tronçon doit-elle être refusée (spawn, village de départ, relief) ? */
    public static boolean refuseBase(Structure.GenerationContext context) {
        return !allowedAt(context, chunkX(context), chunkZ(context));
    }

    /** Cette autre structure (de surface) est-elle trop près d'une base qui va apparaître ? Elle est alors refusée. */
    public static boolean refuseNearBase(Structure.GenerationContext context, Structure structure) {
        if (structure.step() != GenerationStep.Decoration.SURFACE_STRUCTURES) {
            return false; // structures souterraines (mines, forteresses...) : non concernées
        }
        int cx = chunkX(context), cz = chunkZ(context);
        int r = CLEARANCE_CHUNKS;
        for (int rx = Math.floorDiv(cx - r, SPACING); rx <= Math.floorDiv(cx + r, SPACING); rx++) {
            for (int rz = Math.floorDiv(cz - r, SPACING); rz <= Math.floorDiv(cz + r, SPACING); rz++) {
                int[] base = potentialBase(context.seed(), rx, rz);
                int bx = base[0], bz = base[1];
                if (Math.abs(bx - cx) <= r && Math.abs(bz - cz) <= r) {
                    long key = context.seed() * 31L + (((long) bx << 32) ^ (bz & 0xFFFFFFFFL));
                    if (CACHE.computeIfAbsent(key, k -> biomeOk(context, bx, bz) && allowedAt(context, bx, bz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Emplacement possible de la base dans la région (rx, rz), calculé comme le placement "random_spread" de Minecraft. */
    private static int[] potentialBase(long seed, int rx, int rz) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, rx, rz, SALT);
        int range = SPACING - SEPARATION;
        int x = rx * SPACING + random.nextInt(range);
        return new int[] {x, rz * SPACING + random.nextInt(range)};
    }

    /** Coordonnées (en tronçons) du tronçon étudié. */
    private static int chunkX(Structure.GenerationContext context) {
        return context.chunkPos().getMiddleBlockX() >> 4;
    }

    private static int chunkZ(Structure.GenerationContext context) {
        return context.chunkPos().getMiddleBlockZ() >> 4;
    }

    /**
     * Biome de la base (plaines, plages, savanes, prairies) à cet emplacement ? Lu par réflexion : le nom de l'échantillonneur de
     * climat (ancien RandomState.sampler()) a changé en 26.3. On cherche getNoiseBiome(x, y, z, échantillonneur) sur la source de
     * biomes, et dans RandomState la méthode sans paramètre qui fournit cet échantillonneur. Si rien n'est trouvé : supposé bon.
     */
    @SuppressWarnings("unchecked")
    private static boolean biomeOk(Structure.GenerationContext context, int chunkX, int chunkZ) {
        int x = (chunkX << 4) + 8, z = (chunkZ << 4) + 8;
        int y = surface(context, x, z);
        Object source = context.biomeSource();
        Object state = context.randomState();
        try {
            for (Method noise : source.getClass().getMethods()) {
                if (!noise.getName().equals("getNoiseBiome") || noise.getParameterCount() != 4) {
                    continue;
                }
                Class<?> samplerType = noise.getParameterTypes()[3];
                for (Method getter : state.getClass().getMethods()) {
                    if (getter.getParameterCount() == 0 && samplerType.isAssignableFrom(getter.getReturnType())) {
                        Object biome = noise.invoke(source, QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                                getter.invoke(state));
                        return !(biome instanceof Holder<?> holder) || ((Holder<Biome>) holder).is(BIOMES);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // API introuvable : supposé bon
        }
        return true;
    }

    /** Spawn, village de départ et relief. */
    private static boolean allowedAt(Structure.GenerationContext context, int chunkX, int chunkZ) {
        int x0 = chunkX << 4, z0 = chunkZ << 4; // coin du tronçon = point d'ancrage de la base
        if (isForbidden(x0 + 8, z0 + 8)) {
            return false;
        }
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = -AREA; dx <= AREA; dx += PROBE_STEP) {
            for (int dz = -AREA; dz <= AREA; dz += PROBE_STEP) {
                int h = surface(context, x0 + dx, z0 + dz);
                min = Math.min(min, h);
                max = Math.max(max, h);
                if (max - min > MAX_RELIEF) {
                    return false; // pente trop raide, montagne, falaise
                }
            }
        }
        return true;
    }

    private static int surface(Structure.GenerationContext context, int x, int z) {
        return context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(),
                context.randomState());
    }

    /** Une base centrée ici serait-elle trop près du spawn ou du village de départ ? */
    public static boolean isForbidden(int x, int z) {
        BlockPos spawn = BlockPos.ZERO;
        BlockPos village = null;
        MinecraftServer current = server;
        ServerLevel overworld = current == null ? null : current.overworld();
        if (overworld != null) {
            BlockPos worldSpawn = StarterVillage.worldSpawn(overworld);
            if (worldSpawn != null) {
                spawn = worldSpawn;
            }
            village = overworld.getAttached(ModAttachments.VILLAGE_CENTER);
        }
        return closer(x, z, spawn, SPAWN_RADIUS) || (village != null && closer(x, z, village, VILLAGE_RADIUS));
    }

    private static boolean closer(int x, int z, BlockPos center, int radius) {
        double dx = x - center.getX(), dz = z - center.getZ();
        return dx * dx + dz * dz < (double) radius * radius;
    }
}
