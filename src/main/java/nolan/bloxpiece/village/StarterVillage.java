package nolan.bloxpiece.village;

import java.lang.reflect.Method;
import java.util.Optional;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.registry.ModAttachments;

import static nolan.bloxpiece.village.VillageBlocks.*;

/**
 * Village pirate de départ (style Fuchsia) : construit UNE seule fois, au tout premier démarrage d'un monde NEUF (survie ou
 * créatif).
 * <ol>
 *   <li>Emplacement : le point d'apparition du monde s'il convient (terrain sec ou côtier, assez plat) ; sinon le terrain plat
 *       ou côtier le plus proche, cherché en spirale autour du spawn ({@link #findSite}). Jamais au milieu de l'océan, sur un pic
 *       ou dans un ravin.</li>
 *   <li>Terrassement : rayon VillageLayout.TERRAIN_FLAT aplani, raccord en pente douce au-delà ; fondations de pierre sous
 *       chaque bâtiment.</li>
 *   <li>Construction : place, puits, étals, 11 bâtiments (VillageLayout), ruelles et plates-bandes, lampadaires, arbres,
 *       verdure.</li>
 *   <li>Village au spawn : le point d'apparition est placé sur la place, devant la taverne. Village déplacé : le spawn ne bouge
 *       pas, la Boussole du Village indique le chemin.</li>
 * </ol>
 * Boussole du Village : une boussole (aiguille vers le centre de la place, partout dans le monde) donnée à chaque joueur à sa
 * première connexion, et redonnée à la réapparition s'il l'a perdue. Un monde existant (déjà joué) n'est JAMAIS modifié.
 */
public final class StarterVillage {

    /** Un monde dont l'horloge a dépassé ce nombre de ticks n'est pas "neuf" : pas de village. */
    private static final long NEW_WORLD_TICKS = 200;
    /** Recherche d'emplacement autour du spawn : pas et rayon maximal (blocs), points testés autour de chaque candidat. */
    private static final int SEARCH_STEP = 24;
    private static final int SEARCH_RADIUS = 384;
    private static final int SITE_PROBE = 24;
    /** Écart de hauteur maximal accepté (blocs) et nombre de points d'eau tolérés en bordure (village côtier). */
    private static final int MAX_SLOPE = 8;
    private static final int COAST_WATER_POINTS = 3;
    /** Le village est considéré "au spawn" s'il est à moins de cette distance (blocs) du point d'apparition. */
    private static final int AT_SPAWN_DISTANCE = 40;

    private StarterVillage() {
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(StarterVillage::onServerStarted);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> giveCompassOnFirstJoin(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                giveCompassIfMissing(newPlayer);
            }
        });
    }

    private static void onServerStarted(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (Boolean.TRUE.equals(level.getAttached(ModAttachments.STARTER_VILLAGE))) {
            return; // déjà réglé pour ce monde
        }
        level.setAttached(ModAttachments.STARTER_VILLAGE, true);
        if (level.getGameTime() > NEW_WORLD_TICKS) {
            BloxPiece.LOGGER.info("[BloxPiece] Monde existant : pas de village de départ (il n'est construit que dans un monde neuf).");
            return;
        }
        long start = System.currentTimeMillis();
        BlockPos worldSpawn = worldSpawn(level);
        BlockPos center = findSite(level, worldSpawn);
        build(level, center);
        level.setAttached(ModAttachments.VILLAGE_CENTER, center);
        boolean atSpawn = horizontalDistance(center, worldSpawn) <= AT_SPAWN_DISTANCE;
        if (atSpawn) {
            BlockPos spawn = center.offset(VillageLayout.PLAZA_SPAWN);
            run(server, "setworldspawn " + spawn.getX() + " " + spawn.getY() + " " + spawn.getZ());
            run(server, "gamerule spawn_radius 0");
            run(server, "gamerule spawnRadius 0");
        }
        BloxPiece.LOGGER.info("[BloxPiece] Village de départ construit en {} ms, centre de la place {} ({}, spawn du monde {})",
                System.currentTimeMillis() - start, center, atSpawn ? "au spawn" : "déplacé : suivre la Boussole du Village", worldSpawn);
        logPlan(center);
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------------ point d'apparition du monde

    /**
     * Point d'apparition du monde. Le nom de la méthode de Minecraft change selon les versions : plusieurs noms sont essayés
     * (sans risque : si aucun n'existe, le centre du monde est utilisé).
     */
    static BlockPos worldSpawn(ServerLevel level) {
        for (Object target : new Object[] {level, level.getLevelData()}) {
            for (String name : new String[] {"getRespawnData", "getSharedSpawnPos", "getSpawnPos", "getRespawnPosition"}) {
                try {
                    Method m = target.getClass().getMethod(name);
                    BlockPos pos = asBlockPos(m.invoke(target));
                    if (pos != null) {
                        return pos;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // essai suivant
                }
            }
        }
        return new BlockPos(0, 64, 0);
    }

    private static BlockPos asBlockPos(Object value) throws ReflectiveOperationException {
        if (value instanceof BlockPos pos) {
            return pos;
        }
        if (value instanceof GlobalPos global) {
            return global.pos();
        }
        if (value != null) {
            for (String accessor : new String[] {"pos", "globalPos"}) {
                try {
                    BlockPos pos = asBlockPos(value.getClass().getMethod(accessor).invoke(value));
                    if (pos != null) {
                        return pos;
                    }
                } catch (NoSuchMethodException ignored) {
                    // accesseur suivant
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ boussole du village

    /** La Boussole du Village : boussole vanilla aimantée sur le centre de la place (marche partout, sans magnétite). */
    public static ItemStack villageCompass(BlockPos center) {
        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(Level.OVERWORLD, center)), false));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.bloxpiece.village_compass").withStyle(ChatFormatting.GOLD));
        return stack;
    }

    private static boolean isVillageCompass(ItemStack stack, BlockPos center) {
        LodestoneTracker tracker = stack.is(Items.COMPASS) ? stack.get(DataComponents.LODESTONE_TRACKER) : null;
        return tracker != null && tracker.target().map(g -> g.pos().equals(center)).orElse(false);
    }

    private static void giveCompassOnFirstJoin(ServerPlayer player) {
        if (Boolean.TRUE.equals(player.getAttached(ModAttachments.VILLAGE_COMPASS_GIVEN))) {
            return;
        }
        BlockPos center = player.level().getServer().overworld().getAttached(ModAttachments.VILLAGE_CENTER);
        if (center == null) {
            return; // monde sans village de départ
        }
        player.setAttached(ModAttachments.VILLAGE_COMPASS_GIVEN, true);
        give(player, villageCompass(center));
        player.sendSystemMessage(Component.translatable("message.bloxpiece.village_compass.given"));
    }

    private static void giveCompassIfMissing(ServerPlayer player) {
        BlockPos center = player.level().getServer().overworld().getAttached(ModAttachments.VILLAGE_CENTER);
        if (center == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isVillageCompass(inventory.getItem(i), center)) {
                return;
            }
        }
        give(player, villageCompass(center));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.spawnAtLocation((ServerLevel) player.level(), stack);
        }
    }

    // ------------------------------------------------------------------ emplacement

    /** Le spawn s'il convient, sinon le terrain plat ou côtier le plus proche (spirale autour du spawn). */
    static BlockPos findSite(ServerLevel level, BlockPos around) {
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int r = 0; r <= SEARCH_RADIUS; r += SEARCH_STEP) {
            int steps = r == 0 ? 1 : Math.max(8, (int) (2 * Math.PI * r / SEARCH_STEP));
            for (int i = 0; i < steps; i++) {
                double a = 2 * Math.PI * i / steps;
                int x = around.getX() + (int) Math.round(Math.cos(a) * r);
                int z = around.getZ() + (int) Math.round(Math.sin(a) * r);
                int score = siteScore(level, x, z);
                if (score < bestScore) {
                    bestScore = score;
                    best = new BlockPos(x, ground(level, x, z), z);
                }
                if (score <= MAX_SLOPE) {
                    return best;
                }
            }
        }
        return best;
    }

    /**
     * Note d'un emplacement (plus c'est bas, mieux c'est) : écart de hauteur entre le centre et 8 points autour. Le centre doit
     * être sur la terre ferme ; quelques points d'eau en bordure sont tolérés (village côtier), au-delà +100 par point.
     */
    private static int siteScore(ServerLevel level, int x, int z) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, wet = 0;
        boolean centerWet = false;
        for (int i = -1; i < 8; i++) {
            int px = x, pz = z;
            if (i >= 0) {
                double a = Math.PI / 4 * i;
                px += (int) Math.round(Math.cos(a) * SITE_PROBE);
                pz += (int) Math.round(Math.sin(a) * SITE_PROBE);
            }
            int h = ground(level, px, pz);
            BlockState top = level.getBlockState(new BlockPos(px, h - 1, pz));
            boolean isWet = !top.getFluidState().isEmpty() || top.is(ICE) || top.is(PACKED_ICE);
            if (isWet) {
                wet++;
                centerWet |= i < 0;
                continue; // la hauteur de l'eau ne compte pas dans la pente (bord de mer)
            }
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        if (centerWet || max < min) {
            return 10_000;
        }
        return (max - min) + Math.max(0, wet - COAST_WATER_POINTS) * 100;
    }

    /** Hauteur du sol (premier bloc libre au-dessus du terrain, sans les feuilles). Charge / génère le tronçon si besoin. */
    private static int ground(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    // ------------------------------------------------------------------ construction

    static void build(ServerLevel level, BlockPos center) {
        VillageBuilder b = new VillageBuilder(level);
        terraform(b, center);
        for (String skipped : VillagePlan.buildAll(b, center, (x, z) -> ground(level, x, z))) {
            BloxPiece.LOGGER.warn("[BloxPiece] Village : élément ignoré (place occupée) : {}", skipped);
        }
    }

    /**
     * Terrassement : dans TERRAIN_FLAT, sol à la hauteur de la place (on rase les reliefs et les arbres, on remblaie les creux
     * avec de la terre, dessus en herbe) ; entre TERRAIN_FLAT et TERRAIN_BLEND, hauteur intermédiaire pour une pente douce.
     */
    private static void terraform(VillageBuilder b, BlockPos center) {
        ServerLevel level = b.level();
        int y0 = center.getY();
        int outer = VillageLayout.TERRAIN_BLEND;
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > outer) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                level.getChunk(x >> 4, z >> 4);
                int floor = solidGround(level, x, z);                                // dessus du VRAI sol (sans arbres)
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);   // feuillages et eau compris
                boolean inside = d <= VillageLayout.TERRAIN_FLAT;
                double t = inside ? 0.0 : (d - VillageLayout.TERRAIN_FLAT) / (outer - VillageLayout.TERRAIN_FLAT);
                t = t * t * (3 - 2 * t);
                int target = (int) Math.round(Mth.lerp(t, y0, floor));
                // dégagement au-dessus du sol cible (garde l'eau en bordure, hors du village)
                for (int y = target; y <= Math.min(Math.max(top, floor) + 1, y0 + 45); y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = b.get(p);
                    if (!s.isAir() && (inside || s.getFluidState().isEmpty())) {
                        b.setTerrain(p, AIR.defaultBlockState());
                    }
                }
                // remblai jusqu'au sol cible
                for (int y = Math.min(floor, target) - 1; y < target; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (y >= floor || b.get(p).isAir() || !b.get(p).getFluidState().isEmpty()) {
                        b.setTerrain(p, DIRT.defaultBlockState());
                    }
                }
                b.setTerrain(new BlockPos(x, target - 1, z), GRASS_BLOCK.defaultBlockState());
            }
        }
    }

    /**
     * Dessus du vrai sol naturel : on descend depuis le plus haut bloc en ignorant l'air, l'eau, les feuillages, les troncs, la
     * neige et les petites plantes. (La carte de hauteur OCEAN_FLOOR compte les feuilles comme du sol : en bordure de village, on
     * posait alors de l'herbe et de la terre PAR-DESSUS les arbres.)
     */
    private static int solidGround(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        int min = y - 96;
        while (y > min) {
            BlockState s = level.getBlockState(new BlockPos(x, y, z));
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced() && !isTreePart(s)) {
                break;
            }
            y--;
        }
        return y + 1;
    }

    private static boolean isTreePart(BlockState state) {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return id.endsWith("leaves") || id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem")
                || id.endsWith("mushroom_block") || id.equals("vine") || id.equals("bee_nest") || id.contains("propagule")
                || id.equals("mangrove_roots") || id.equals("snow_block") || id.equals("powder_snow");
    }

    /** Console : coordonnées monde de chaque bâtiment et des places réservées aux futurs PNJ. */
    private static void logPlan(BlockPos center) {
        for (VillageLayout.Entry entry : VillageLayout.BUILDINGS) {
            BlockPos origin = center.offset(entry.origin());
            BloxPiece.LOGGER.info("[BloxPiece] {} : {}", entry.name(), origin);
            for (BlockPos npc : entry.npcSpots()) {
                BloxPiece.LOGGER.info("[BloxPiece]   place PNJ : {}", origin.offset(npc.rotate(entry.rotation())));
            }
        }
        for (BlockPos stall : VillagePlan.STALLS_PLACED) {
            BloxPiece.LOGGER.info("[BloxPiece] Étal de marché (vendeur) : {}", stall);
        }
    }
}
