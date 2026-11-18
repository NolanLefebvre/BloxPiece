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
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (!Boolean.TRUE.equals(player.getAttached(ModAttachments.VILLAGE_COMPASS_GIVEN))) {
                secureSpawn(player, true); // toute première connexion : apparition au point de spawn du monde
            }
            giveCompassOnFirstJoin(player);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                secureSpawn(newPlayer, false);
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
        center = build(level, center); // la place peut être surélevée au-dessus d'un lac voisin
        level.setAttached(ModAttachments.VILLAGE_CENTER, center);
        boolean atSpawn = horizontalDistance(center, worldSpawn) <= AT_SPAWN_DISTANCE;
        if (atSpawn) {
            BlockPos spawn = safeSpawn(level, center);
            level.setAttached(ModAttachments.VILLAGE_SPAWN, spawn);
            run(server, "setworldspawn " + spawn.getX() + " " + spawn.getY() + " " + spawn.getZ());
            // pas d'apparition au hasard autour du point (sinon on peut tomber sur un toit) ; le nom de la règle dépend des versions
            run(server, "gamerule spawn_radius 0");
            run(server, "gamerule spawnRadius 0");
            run(server, "gamerule respawn_radius 0");
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

    // ------------------------------------------------------------------ point d'apparition sûr

    /** Hauteur libre exigée au-dessus des pieds (le joueur fait 2 blocs : 3 de marge) et ciel dégagé au-dessus (pas sous un toit). */
    private static final int SPAWN_HEADROOM = 3;
    private static final int SPAWN_SKY_CHECK = 40;

    /**
     * Point d'apparition SÛR sur la place : départ devant la taverne (PLAZA_SPAWN), puis recherche en spirale sur la place. Une case
     * convient si : sol plein dessous, 3 blocs d'air libres sur une zone 3 x 3 autour des pieds, et rien au-dessus jusqu'au ciel
     * (jamais sous le toit du puits ni sur/dans un bâtiment). Repli : le centre de la place, 1 bloc au-dessus du dernier bloc.
     */
    static BlockPos safeSpawn(ServerLevel level, BlockPos center) {
        BlockPos start = center.offset(VillageLayout.PLAZA_SPAWN);
        int half = VillageLayout.PLAZA_RADIUS;
        for (int r = 0; r <= half; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    BlockPos feet = new BlockPos(start.getX() + dx, center.getY(), start.getZ() + dz);
                    if (VillageLayout.onPlaza(feet.getX() - center.getX(), feet.getZ() - center.getZ()) && isSafeSpot(level, feet)) {
                        return feet;
                    }
                }
            }
        }
        BlockPos fallback = new BlockPos(start.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, start.getX(), start.getZ()), start.getZ());
        BloxPiece.LOGGER.warn("[BloxPiece] Aucun point de spawn dégagé trouvé sur la place, repli en {}", fallback);
        return fallback;
    }

    private static boolean isSafeSpot(ServerLevel level, BlockPos feet) {
        BlockState ground = level.getBlockState(feet.below());
        if (ground.isAir() || !ground.getFluidState().isEmpty() || !ground.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy < SPAWN_HEADROOM; dy++) {
                    BlockState s = level.getBlockState(feet.offset(dx, dy, dz));
                    if (!s.isAir() || !s.getFluidState().isEmpty()) {
                        return false;
                    }
                }
            }
        }
        for (int dy = SPAWN_HEADROOM; dy <= SPAWN_SKY_CHECK; dy++) {
            if (!level.getBlockState(feet.above(dy)).isAir()) {
                return false; // un toit au-dessus
            }
        }
        return true;
    }

    /**
     * Après une apparition au point de spawn du monde (première connexion, ou mort sans lit / ancre) : si le joueur n'est pas
     * exactement sur le point sûr du village, ou s'il est coincé dans un bloc / sur un toit, il est replacé sur la place.
     * Un joueur qui réapparaît à son lit n'est jamais déplacé.
     */
    private static void secureSpawn(ServerPlayer player, boolean firstJoin) {
        ServerLevel overworld = player.level().getServer().overworld();
        BlockPos spawn = overworld.getAttached(ModAttachments.VILLAGE_SPAWN);
        if (spawn == null || player.level() != overworld) {
            return; // village déplacé (pas au spawn) ou autre dimension
        }
        if (!firstJoin && hasPersonalRespawn(player)) {
            return;
        }
        BlockPos at = player.blockPosition();
        double dx = at.getX() - spawn.getX(), dz = at.getZ() - spawn.getZ();
        if (dx * dx + dz * dz > 48 * 48) {
            return; // apparu ailleurs (lit, ancre...) : on ne touche à rien
        }
        if (at.equals(spawn)) {
            return;
        }
        if (!isSafeSpot(overworld, spawn)) {
            spawn = safeSpawn(overworld, overworld.getAttached(ModAttachments.VILLAGE_CENTER) == null ? spawn
                    : overworld.getAttached(ModAttachments.VILLAGE_CENTER));
            overworld.setAttached(ModAttachments.VILLAGE_SPAWN, spawn);
        }
        run(overworld.getServer(), "tp " + player.getStringUUID() + " " + (spawn.getX() + 0.5) + " " + spawn.getY() + " "
                + (spawn.getZ() + 0.5) + " 180 0");
    }

    /** Le joueur a-t-il un point de réapparition personnel (lit, ancre) ? Nom de méthode selon les versions : plusieurs essais. */
    private static boolean hasPersonalRespawn(ServerPlayer player) {
        for (String name : new String[] {"getRespawnConfig", "getRespawnPosition"}) {
            try {
                Object value = player.getClass().getMethod(name).invoke(player);
                return value != null;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // essai suivant
            }
        }
        return false;
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
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, wet = 0, wetTop = Integer.MIN_VALUE;
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
                wetTop = Math.max(wetTop, h);
                centerWet |= i < 0;
                continue; // la hauteur de l'eau ne compte pas dans la pente (bord de mer)
            }
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        if (centerWet || max < min) {
            return 10_000;
        }
        int centerGround = ground(level, x, z);
        // lac ou mer PLUS HAUT que l'emplacement : l'eau inonderait la place (au-delà de MAX_RAISE, impossible à corriger)
        int lakeAbove = wetTop == Integer.MIN_VALUE ? 0 : wetTop - centerGround;
        int lakePenalty = lakeAbove <= 0 ? 0 : lakeAbove <= MAX_RAISE ? lakeAbove * 3 : 1_000;
        return (max - min) + Math.max(0, wet - COAST_WATER_POINTS) * 100 + lakePenalty;
    }

    /** Hauteur du sol (premier bloc libre au-dessus du terrain, sans les feuilles). Charge / génère le tronçon si besoin. */
    private static int ground(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    // ------------------------------------------------------------------ construction

    /** Surélévation maximale de la place au-dessus d'un lac voisin (blocs). */
    private static final int MAX_RAISE = 6;

    /**
     * Construit le village ; renvoie le centre réellement utilisé. Si un lac, une mare ou la mer (même gelés) touche la zone du
     * village à une hauteur égale ou supérieure à la place, la place est surélevée juste au-dessus de l'eau (jusqu'à MAX_RAISE
     * blocs) : l'eau ne peut jamais couler dans le village, et les berges descendent en pente douce vers l'eau.
     */
    static BlockPos build(ServerLevel level, BlockPos center) {
        int water = highestWater(level, center, VillageLayout.TERRAIN_BLEND + 4);
        if (water != Integer.MIN_VALUE && water + 1 > center.getY() && water + 1 - center.getY() <= MAX_RAISE) {
            BloxPiece.LOGGER.info("[BloxPiece] Village : eau voisine à y={}, place surélevée de {} à {}", water, center.getY(), water + 1);
            center = new BlockPos(center.getX(), water + 1, center.getZ());
        }
        VillageBuilder b = new VillageBuilder(level);
        terraform(b, center);
        removeOrphanLeaves(b, center);
        removeUnsupported(b, center);
        for (String skipped : VillagePlan.buildAll(b, center, (x, z) -> ground(level, x, z))) {
            BloxPiece.LOGGER.warn("[BloxPiece] Village : élément ignoré (place occupée) : {}", skipped);
        }
        removeDroppedItems(level, center);
        spawnTavernKeeper(level, center);
        return center;
    }

    /**
     * Le Tavernier, derrière le comptoir de la taverne (première place de PNJ de la taverne, voir VillageLayout), tourné vers
     * la salle. Créé une seule fois, avec le village.
     */
    private static void spawnTavernKeeper(ServerLevel level, BlockPos center) {
        for (VillageLayout.Entry entry : VillageLayout.BUILDINGS) {
            if (!entry.name().equals("Taverne") || entry.npcSpots().isEmpty()) {
                continue;
            }
            BlockPos spot = center.offset(entry.origin()).offset(entry.npcSpots().get(0).rotate(entry.rotation()));
            nolan.bloxpiece.mob.TavernKeeper keeper = nolan.bloxpiece.registry.ModEntities.TAVERN_KEEPER.create(level,
                    net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
            if (keeper == null) {
                return;
            }
            float yaw = switch (entry.rotation()) {     // la façade (et la salle) est vers +z local
                case CLOCKWISE_90 -> 90.0F;
                case CLOCKWISE_180 -> 180.0F;
                case COUNTERCLOCKWISE_90 -> -90.0F;
                default -> 0.0F;
            };
            keeper.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            keeper.setYRot(yaw);
            keeper.setYHeadRot(yaw);
            keeper.setYBodyRot(yaw);
            level.addFreshEntity(keeper);
            BloxPiece.LOGGER.info("[BloxPiece] Tavernier placé derrière le comptoir en {}", spot);
            return;
        }
    }

    /** Plus haut niveau d'eau (ou de glace d'un lac gelé) dans le rayon donné, ou MIN_VALUE s'il n'y en a pas. */
    private static int highestWater(ServerLevel level, BlockPos center, int radius) {
        int best = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                int x = center.getX() + dx, z = center.getZ() + dz;
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockState top = level.getBlockState(new BlockPos(x, y, z));
                if (isWaterLike(top)) {
                    best = Math.max(best, y);
                }
            }
        }
        return best;
    }

    /** Eau, eau gelée (glace de lac, glace fondante) et blocs immergés (algues, herbes marines) : à traiter comme de l'eau. */
    private static boolean isWaterLike(BlockState s) {
        return !s.getFluidState().isEmpty() || s.is(ICE) || s.is(FROSTED_ICE);
    }

    /**
     * Feuillages coupés en bordure : un arbre dont le tronc a été rasé par le terrassement laisse des feuilles sans tronc qui
     * pourriraient ensuite en lâchant pousses, bâtons et pommes. Les feuilles naturelles sans tronc à moins de 6 blocs (portée vanilla) sont
     * retirées tout de suite, sans objet lâché.
     */
    private static void removeOrphanLeaves(VillageBuilder b, BlockPos center) {
        int inner = VillageLayout.TERRAIN_FLAT - 4, outer = VillageLayout.TERRAIN_BLEND + 8, y0 = center.getY();
        int removed = 0;
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < inner || d > outer) {
                    continue;
                }
                for (int y = y0 - 12; y <= y0 + 40; y++) {
                    BlockPos p = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    BlockState s = b.get(p);
                    if (!isNaturalLeaves(s) || hasLogNear(b, p, 6)) {
                        continue;
                    }
                    b.setTerrain(p, AIR.defaultBlockState());
                    removed++;
                }
            }
        }
        if (removed > 0) {
            BloxPiece.LOGGER.info("[BloxPiece] Village : {} feuillage(s) sans tronc retiré(s) en bordure", removed);
        }
    }

    private static boolean isNaturalLeaves(BlockState s) {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        if (!id.endsWith("leaves")) {
            return false;
        }
        for (var property : s.getProperties()) {
            if (property.getName().equals("persistent")) {
                return !Boolean.TRUE.equals(s.getValue(property));
            }
        }
        return true;
    }

    private static boolean hasLogNear(VillageBuilder b, BlockPos p, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockState s = b.get(p.offset(dx, dy, dz));
                    if (!s.isAir() && isTreePart(s) && !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock())
                            .getPath().endsWith("leaves")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Après le terrassement : tout bloc qui ne tient plus (liane, liane des cavernes ou du Nether, lichen, racines suspendues,
     * cacao, mousse suspendue, fleur ou herbe dont le sol a changé, neige sans support...) est retiré sans objet lâché. Chaque
     * colonne est parcourue de haut en bas : une liane qui ne tenait que grâce à celle du dessus disparaît avec elle.
     */
    private static void removeUnsupported(VillageBuilder b, BlockPos center) {
        ServerLevel level = b.level();
        int outer = VillageLayout.TERRAIN_BLEND + 10, y0 = center.getY();
        int removed = 0;
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                if (dx * dx + dz * dz > outer * outer) {
                    continue;
                }
                int x = center.getX() + dx, z = center.getZ() + dz;
                int top = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), y0 + 70);
                for (int y = top; y >= y0 - 16; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || !s.getFluidState().isEmpty() || s.canSurvive(level, p)) {
                        continue;
                    }
                    b.setTerrain(p, AIR.defaultBlockState());
                    removed++;
                }
            }
        }
        if (removed > 0) {
            BloxPiece.LOGGER.info("[BloxPiece] Village : {} bloc(s) suspendu(s) sans support retiré(s) (lianes, plantes...)", removed);
        }
    }

    /**
     * Filet de sécurité : supprime les objets lâchés au sol dans la zone du village pendant la construction (herbes, fleurs,
     * feuilles mortes, pousses...). Les blocs sont déjà posés sans objets lâchés ; ceci rattrape les cas restants.
     */
    private static void removeDroppedItems(ServerLevel level, BlockPos center) {
        int r = VillageLayout.TERRAIN_BLEND + 25;
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(center.getX() - r, center.getY() - 64, center.getZ() - r,
                center.getX() + r, center.getY() + 96, center.getZ() + r);
        int removed = 0;
        for (net.minecraft.world.entity.item.ItemEntity item
                : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area, e -> true)) {
            item.discard();
            removed++;
        }
        if (removed > 0) {
            BloxPiece.LOGGER.info("[BloxPiece] Village : {} objet(s) lâché(s) pendant la construction supprimé(s)", removed);
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
                // plus haut bloc de la colonne, TOUT compris : feuillages, eau, mais aussi lianes, lichens, racines suspendues,
                // cacao... (MOTION_BLOCKING ignore ce qui ne bloque pas le passage : une colonne de lianes restait suspendue)
                int top = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z),
                        level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                boolean inside = d <= VillageLayout.TERRAIN_FLAT;
                double t = inside ? 0.0 : (d - VillageLayout.TERRAIN_FLAT) / (outer - VillageLayout.TERRAIN_FLAT);
                t = t * t * (3 - 2 * t);
                int target = (int) Math.round(Mth.lerp(t, y0, floor));
                // dégagement au-dessus du sol cible : dans le village tout part (eau et glace comprises : la place est sèche) ;
                // en bordure, l'eau et la glace des lacs restent. La place étant au-dessus du niveau de l'eau voisine (voir
                // build), une berge remblayée est toujours au moins au niveau de l'eau : l'eau ne peut pas s'y répandre.
                for (int y = target; y <= Math.min(Math.max(top, floor) + 1, y0 + 45); y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = b.get(p);
                    if (!s.isAir() && (inside || !isWaterLike(s))) {
                        b.setTerrain(p, AIR.defaultBlockState()); // hors du village, l'eau ET la glace d'un lac restent intactes
                    }
                }
                // remblai jusqu'au sol cible
                for (int y = Math.min(floor, target) - 1; y < target; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (y >= floor || b.get(p).isAir() || isWaterLike(b.get(p))) {
                        b.setTerrain(p, DIRT.defaultBlockState());
                    }
                }
                // dessus : herbe à l'air libre ; sous l'eau d'un lac en bordure, fond de sable (berge propre, pas d'herbe noyée)
                boolean underwater = !inside && isWaterLike(b.get(new BlockPos(x, target, z)));
                b.setTerrain(new BlockPos(x, target - 1, z), (underwater ? SAND : GRASS_BLOCK).defaultBlockState());
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
            if (!s.isAir() && !isWaterLike(s) && !s.canBeReplaced() && !isTreePart(s)) {
                break; // vrai sol (la glace d'un lac gelé n'en est pas : c'est de l'eau)
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
