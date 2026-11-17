package nolan.bloxpiece.spawn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.mob.BloxMob;

/**
 * Le système d'apparition naturelle des mobs du mod (un seul système, partagé par tous les mobs).
 * Chaque mob déclare une GroupSpawnRule (voir ModSpawns) : le reste est géré ici, côté serveur uniquement.
 *
 * Principe : régulièrement, pour chaque joueur dont le délai est écoulé, on cherche un point d'apparition
 * entre minDistance et maxDistance blocs, hors de sa vue, puis on y fait apparaître tout un groupe d'un coup.
 * Rien n'apparaît si la zone contient déjà trop de mobs ou si un groupe est apparu trop récemment près de là.
 */
public final class GroupSpawner {

    /** On ne vérifie les joueurs qu'une fois toutes les 2 secondes : le coût est négligeable. */
    private static final int CHECK_INTERVAL_TICKS = 40;

    private static final List<GroupSpawnRule> RULES = new ArrayList<>();
    private static final Map<GroupSpawnRule, RuleState> STATES = new IdentityHashMap<>();

    private GroupSpawner() {
    }

    /** Un groupe apparu récemment : sert à ne pas en créer un autre juste à côté. */
    private record RecentGroup(ResourceKey<Level> dimension, int x, int z, int tick) {
    }

    /** Ce que le système retient pour une règle (en mémoire uniquement, remis à zéro à chaque lancement). */
    private static final class RuleState {
        // joueur -> tick du serveur à partir duquel un nouveau groupe peut apparaître pour lui
        final Map<UUID, Integer> nextAt = new HashMap<>();
        final List<RecentGroup> recent = new ArrayList<>();
        int lastGroupTick = Integer.MIN_VALUE / 2;
    }

    public static void register(GroupSpawnRule rule) {
        RULES.add(rule);
        STATES.put(rule, new RuleState());
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(GroupSpawner::tick);

        // Un joueur qui part ne doit pas laisser de données en mémoire
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
            UUID id = listener.getPlayer().getUUID();
            STATES.values().forEach(state -> state.nextAt.remove(id));
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            STATES.values().forEach(state -> {
                state.nextAt.clear();
                state.recent.clear();
                state.lastGroupTick = Integer.MIN_VALUE / 2;
            });
        });
    }

    // ------------------------------------------------------------------------------------------
    // Boucle principale
    // ------------------------------------------------------------------------------------------

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        for (GroupSpawnRule rule : RULES) {
            RuleState state = STATES.get(rule);
            state.recent.removeIf(group -> now - group.tick() > rule.maxInterval);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isAlive() || player.isSpectator()) {
                    continue;
                }
                if (!(player.level() instanceof ServerLevel level)) {
                    continue;
                }
                if (level.dimension() != rule.dimension || level.getDifficulty() == Difficulty.PEACEFUL) {
                    continue;
                }

                UUID id = player.getUUID();
                Integer nextAt = state.nextAt.get(id);
                RandomSource random = level.getRandom();

                // Première fois qu'on voit ce joueur : on attend un peu avant le premier groupe (pas d'embuscade à l'arrivée)
                if (nextAt == null) {
                    state.nextAt.put(id, now + random.nextInt(rule.minInterval / 2, rule.minInterval + 1));
                    continue;
                }
                if (now < nextAt || now - state.lastGroupTick < rule.globalGapTicks) {
                    continue;
                }

                boolean spawned = trySpawnGroup(rule, state, level, player, now);
                int delay = spawned
                        ? random.nextInt(rule.minInterval, rule.maxInterval + 1)
                        : rule.retryTicks;
                state.nextAt.put(id, now + delay);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Création d'un groupe
    // ------------------------------------------------------------------------------------------

    private static boolean trySpawnGroup(GroupSpawnRule rule, RuleState state, ServerLevel level, ServerPlayer player, int now) {
        // 1. Population : combien de mobs de ce type autour du joueur, et dans toute la dimension ?
        AABB zone = player.getBoundingBox().inflate(rule.populationRadius);
        int nearPlayer = level.getEntitiesOfClass(BloxMob.class, zone, mob -> rule.matches(mob.getType())).size();
        int inLevel = level.getEntities(EntityTypeTest.forClass(BloxMob.class), mob -> rule.matches(mob.getType())).size();

        int room = Math.min(rule.maxNearPlayer - nearPlayer, rule.maxPerLevel - inLevel);
        if (room < rule.minGroup) {
            return false; // pas assez de place : on ne fait pas apparaître de groupe plus petit que prévu
        }

        // 2. Taille du groupe : aléatoire, mais sans jamais dépasser les limites
        RandomSource random = level.getRandom();
        int size = random.nextInt(rule.minGroup, Math.min(rule.maxGroup, room) + 1);

        // 3. On cherche un emplacement valide (plusieurs essais)
        for (int attempt = 0; attempt < rule.anchorAttempts; attempt++) {
            List<BloxMob> group = buildGroup(rule, state, level, player, size);
            if (group == null) {
                continue;
            }

            for (BloxMob mob : group) {
                level.addFreshEntity(mob);
            }
            BlockPos anchor = group.get(0).blockPosition();
            state.recent.add(new RecentGroup(level.dimension(), anchor.getX(), anchor.getZ(), now));
            state.lastGroupTick = now;

            if (rule.debug) {
                BloxPiece.LOGGER.info("[spawn] groupe de {} x {} en {} (à {} blocs de {})",
                        group.size(), group.get(0).getName().getString(), anchor.toShortString(),
                        (int) Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(anchor))), player.getName().getString());
            }
            return true;
        }
        return false;
    }

    /** Prépare un groupe complet SANS l'ajouter au monde : rien n'apparaît si le groupe est incomplet. */
    private static List<BloxMob> buildGroup(GroupSpawnRule rule, RuleState state, ServerLevel level, ServerPlayer player, int size) {
        RandomSource random = level.getRandom();

        // Point d'apparition : tirage uniforme dans l'anneau [minDistance, maxDistance] autour du joueur
        double angle = random.nextDouble() * Math.PI * 2.0;
        double min2 = (double) rule.minDistance * rule.minDistance;
        double max2 = (double) rule.maxDistance * rule.maxDistance;
        double distance = Math.sqrt(min2 + random.nextDouble() * (max2 - min2));
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);

        // Pas à côté d'un groupe récent (même celui d'un autre joueur)
        if (tooCloseToRecentGroup(rule, state, level, x, z)) {
            return null;
        }

        BlockPos anchor = groundAt(level, x, z, player.getBlockY(), rule.maxVerticalDelta);
        if (anchor == null) {
            return null;
        }

        List<BloxMob> group = new ArrayList<>();
        BloxMob first = createAt(rule, level, anchor, player);
        if (first == null) {
            return null;
        }
        group.add(first);

        // Les autres membres : autour du premier, à quelques blocs
        for (int i = 1; i < size; i++) {
            BloxMob member = placeMember(rule, level, anchor, group, player);
            if (member != null) {
                group.add(member);
            }
        }

        return group.size() >= rule.minGroup ? group : null;
    }

    private static BloxMob placeMember(GroupSpawnRule rule, ServerLevel level, BlockPos anchor, List<BloxMob> group, ServerPlayer player) {
        RandomSource random = level.getRandom();

        for (int attempt = 0; attempt < rule.memberAttempts; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 1.5 + random.nextDouble() * Math.max(0.5, rule.groupSpread - 1.5);
            int x = Mth.floor(anchor.getX() + 0.5 + Math.cos(angle) * radius);
            int z = Mth.floor(anchor.getZ() + 0.5 + Math.sin(angle) * radius);

            // Le terrain ne doit pas être trop différent de celui du premier (pas de falaise entre deux membres)
            BlockPos ground = groundAt(level, x, z, anchor.getY(), 3);
            if (ground == null) {
                continue;
            }

            BloxMob mob = createAt(rule, level, ground, player);
            if (mob == null) {
                continue;
            }

            boolean overlaps = false;
            for (BloxMob other : group) {
                if (mob.distanceToSqr(other) < 1.5 * 1.5) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                return mob;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------
    // Vérifications de l'emplacement
    // ------------------------------------------------------------------------------------------

    private static boolean tooCloseToRecentGroup(GroupSpawnRule rule, RuleState state, ServerLevel level, int x, int z) {
        double limit = (double) rule.minGroupSeparation * rule.minGroupSeparation;
        for (RecentGroup group : state.recent) {
            if (group.dimension() != level.dimension()) {
                continue;
            }
            double dx = group.x() - x;
            double dz = group.z() - z;
            if (dx * dx + dz * dz < limit) {
                return true;
            }
        }
        return false;
    }

    /**
     * Le bloc où se tient un mob en surface à cet endroit, ou null si l'endroit ne convient pas :
     * chunk non chargé, trop haut / trop bas, pas de sol solide, eau ou lave.
     */
    private static BlockPos groundAt(ServerLevel level, int x, int z, int referenceY, int maxDelta) {
        // Jamais de génération ni de chargement de chunk juste pour une apparition
        if (!level.hasChunkAt(new BlockPos(x, referenceY, z))) {
            return null;
        }

        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - referenceY) > maxDelta) {
            return null;
        }

        BlockPos pos = new BlockPos(x, y, z);
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return null;
        }
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(below).isEmpty()) {
            return null;
        }
        return pos;
    }

    /** Crée le mob à cet endroit (sans l'ajouter au monde). Null s'il manque de place ou s'il serait visible du joueur. */
    private static BloxMob createAt(GroupSpawnRule rule, ServerLevel level, BlockPos pos, ServerPlayer player) {
        BloxMob mob = rule.pickType(level.getRandom()).create(level, EntitySpawnReason.NATURAL);
        if (mob == null) {
            return null;
        }

        mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        mob.setYRot(level.getRandom().nextFloat() * 360.0F);
        mob.setYHeadRot(mob.getYRot());

        // Assez d'espace (pas de bloc dans le corps du mob) et pas de liquide
        if (!level.noCollision(mob) || level.containsAnyLiquid(mob.getBoundingBox())) {
            return null;
        }
        if (isInPlayerView(rule, player, mob)) {
            return null;
        }
        return mob;
    }

    /** Vrai si le joueur regarde dans cette direction et voit le mob (ligne de vue dégagée). */
    private static boolean isInPlayerView(GroupSpawnRule rule, ServerPlayer player, BloxMob mob) {
        double limit = (double) rule.viewCheckDistance * rule.viewCheckDistance;
        if (mob.distanceToSqr(player) > limit) {
            return false;
        }
        Vec3 toMob = mob.getEyePosition().subtract(player.getEyePosition());
        if (toMob.lengthSqr() < 1.0E-6) {
            return true;
        }
        boolean inFront = player.getLookAngle().dot(toMob.normalize()) > 0.1;
        return inFront && player.hasLineOfSight(mob);
    }
}
