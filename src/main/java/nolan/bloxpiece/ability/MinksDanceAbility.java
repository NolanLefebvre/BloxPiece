package nolan.bloxpiece.ability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.MinksFx;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Race Minks, niveau 30 : DANSE DES GRIFFES ÉCLAIR. Tout est géré côté serveur.
 * <ol>
 *   <li>Espace : la zone est mesurée avant le départ ({@link #space}) : refusée (rien n'est consommé) s'il n'y a pas la place,
 *       sinon réduite à la place disponible ({@link #MIN_RADIUS} à {@link #RADIUS} blocs). La zone est INVISIBLE (MinksFx.ZONE
 *       sert seulement à la caméra cinématique du lanceur, MinksCamera).</li>
 *   <li>Enchaînement : {@link #STEPS} apparitions ({@link #STEPS_SULONG} en Sulong), une tous les {@link #STEP} ticks. Les
 *       mouvements alternent sol et air sans jamais se répéter : autour des adversaires (devant, côté, derrière, dash rasant,
 *       au-dessus puis chute, diagonale aérienne, rafale) et sur TOUTE la zone (positions éloignées, au sol ou en hauteur),
 *       choisies parmi des points libres relevés au départ ({@link #spots}) sans réutiliser les dernières. Chaque position est
 *       vérifiée : dans la zone, sans collision, en vue directe (jamais à travers un mur).</li>
 *   <li>Frappes : à chaque apparition, au moment de l'impact, la griffe touche TOUS les mobs de SA zone d'impact (devant le
 *       joueur, dans le sens du coup, et tout près de lui), simultanément ; une cible prend au plus {@link #MAX_HITS} coups,
 *       espacés d'au moins {@link #MIN_GAP} ticks (cette limite ne réduit jamais le nombre de mobs touchés par une frappe).</li>
 *   <li>Final : il se place au-dessus du centre ({@link #DIVE} ticks, l'électricité se concentre), plonge en {@link #FALL}
 *       ticks et S'ÉCRASE AU CENTRE : onde de choc électrique sur toute la zone (MinksFx.CRASH), dégâts dans toute la zone.</li>
 * </ol>
 */
public class MinksDanceAbility implements Ability {

    public static final int PREP = 10, STEPS = 24, STEPS_SULONG = 30, STEP = 2, STEP_SULONG = 2, STRIKE = 1, RECOVER = 12;
    /** Final : temps au-dessus du centre, durée de la plongée ; l'écrasement a lieu à DIVE + FALL. */
    public static final int DIVE = 6, FALL = 5, FINAL_DELAY = DIVE + FALL;
    /** Zone minimale (en dessous : refusée), nombre de positions libres exigées, hauteur maximale de la plongée. */
    public static final double MIN_RADIUS = 3.0, DIVE_HEIGHT = 3.5;
    /** Hauteur de la chute finale (le double des positions en l'air), réduite s'il n'y a pas la place au-dessus du centre. */
    public static final double FINAL_HEIGHT = 7.0;
    public static final int MIN_SPOTS = 10;
    public static final int COOLDOWN = 20 * 25;
    public static final double RADIUS = 7.0;
    /** Zone d'impact d'une griffe : sphère de {@link #HIT_REACH} blocs centrée à {@link #HIT_AHEAD} bloc devant le joueur, plus tout ce qui est à {@link #HIT_CLOSE} bloc de lui. */
    public static final double HIT_REACH = 2.2, HIT_AHEAD = 1.3, HIT_CLOSE = 1.5;
    public static final float DAMAGE = 3.0F, FINAL_DAMAGE = 10.0F;
    public static final int MAX_HITS = 7, MAX_HITS_SULONG = 9, MIN_GAP = 2, STUN = 10;

    /** Types de mouvements (synchronisés : MINKS_MOVE, pour l'animation de frappe adaptée). */
    public static final int FRONT = 1, SIDE = 2, BEHIND = 3, DASH = 4, ABOVE = 5, AIR_DIAG = 6, MULTI = 7, FAR = 8, RISE = 9;

    private final int level;

    public MinksDanceAbility(int level) {
        this.level = level;
    }

    /** Instant (ticks) du placement au-dessus du centre, et fin, selon la version. Partagé avec l'animation. */
    public static int returnTick(boolean sulong) {
        return PREP + (sulong ? STEPS_SULONG * STEP_SULONG : STEPS * STEP);
    }

    public static int duration(boolean sulong) {
        return returnTick(sulong) + FINAL_DELAY + RECOVER;
    }

    /** Ticks (dans le mouvement) où tombent les coups : la rafale frappe dès l'apparition puis encore une fois. */
    public static int[] strikeTicks(int type) {
        return type == MULTI ? new int[] {0, 1} : new int[] {STRIKE};
    }

    public static boolean aerial(int type) {
        return type == ABOVE || type == AIR_DIAG || type == RISE;
    }

    @Override
    public String id() {
        return "minks_lightning_dance";
    }

    @Override
    public int cooldownTicks() {
        return COOLDOWN;
    }

    @Override
    public int requiredLevel() {
        return level;
    }

    @Override
    public String nameKey() {
        return "race.bloxpiece.attack." + id();
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        if (Minks.refuseBusy(player)) {
            return false;
        }
        if (space((ServerLevel) player.level(), player) < 0.0) { // pas la place : refusée, rien n'est consommé
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.bloxpiece.minks.no_space"), true);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ espace

    /**
     * Rayon de zone utilisable autour du joueur, ou -1 si l'espace est insuffisant. On essaie {@link #RADIUS}, puis des rayons de
     * plus en plus petits jusqu'à {@link #MIN_RADIUS} : pour chacun, 32 positions réparties sur deux cercles sont testées
     * ({@link #free}) ; il en faut au moins {@link #MIN_SPOTS}, et de la place au-dessus du centre pour la plongée finale.
     */
    public static double space(ServerLevel level, ServerPlayer player) {
        Vec3 center = player.position();
        if (headroom(level, player, center, DIVE_HEIGHT) < 1.5) {
            return -1.0;
        }
        for (double r = RADIUS; r >= MIN_RADIUS - 1.0E-3; r -= 1.0) {
            int ok = 0;
            for (int i = 0; i < 32; i++) {
                double ang = i * Math.PI / 8.0 + (i >= 16 ? Math.PI / 16.0 : 0.0);
                if (free(level, player, center, i >= 16 ? r : r * 0.55, ang) != null) {
                    ok++;
                }
            }
            if (ok >= MIN_SPOTS) {
                return r;
            }
        }
        return -1.0;
    }

    /**
     * Position au sol libre à {@code d} blocs du centre dans la direction {@code ang} : sol à moins de 3 blocs de hauteur du
     * départ, place pour le joueur, vue directe depuis le centre (jamais à travers un mur) ; null sinon.
     */
    private static Vec3 free(ServerLevel level, ServerPlayer player, Vec3 center, double d, double ang) {
        double x = center.x + Math.cos(ang) * d, z = center.z + Math.sin(ang) * d;
        double ground = Human.ground(level, x, z, center.y + 1.0);
        if (Math.abs(ground - center.y) > 3.0) {
            return null;
        }
        Vec3 pos = new Vec3(x, ground, z);
        boolean ok = level.noCollision(player, player.getBoundingBox().move(pos.subtract(player.position())))
                && level.clip(new ClipContext(center.add(0.0, player.getEyeHeight(), 0.0), pos.add(0.0, player.getEyeHeight(), 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
        return ok ? pos : null;
    }

    /** Points libres de toute la zone (trois cercles), relevés au départ : les apparitions « sur toute la zone » y piochent. */
    private static List<Vec3> spots(ServerLevel level, ServerPlayer player, Vec3 center, double radius) {
        List<Vec3> list = new ArrayList<>();
        double[] rings = {0.35, 0.65, 0.92};
        for (int ring = 0; ring < rings.length; ring++) {
            int n = 10 + ring * 4;
            for (int i = 0; i < n; i++) {
                Vec3 p = free(level, player, center, radius * rings[ring], i * Math.PI * 2 / n + ring * 0.3);
                if (p != null) {
                    list.add(p);
                }
            }
        }
        return list;
    }

    /** Place libre au-dessus de {@code pos} pour le joueur (blocs, au plus {@code max}). */
    private static double headroom(ServerLevel level, ServerPlayer player, Vec3 pos, double max) {
        double h = 0.0;
        for (double y = 0.5; y <= max + 1.0E-3; y += 0.5) {
            if (!level.noCollision(player, player.getBoundingBox().move(pos.add(0.0, y, 0.0).subtract(player.position())))) {
                break;
            }
            h = y;
        }
        return h;
    }

    // ------------------------------------------------------------------ déroulement

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        boolean sulong = Minks.sulong(player);
        int steps = sulong ? STEPS_SULONG : STEPS, step = sulong ? STEP_SULONG : STEP;
        int maxHits = sulong ? MAX_HITS_SULONG : MAX_HITS;
        Minks.setAction(player, sulong ? Minks.DANCE_S : Minks.DANCE);
        Minks.showClaws(player, duration(sulong) + 10);
        Vec3 center = player.position();
        // zone adaptée à la place disponible (vérifiée par canUse : jamais plus petite que MIN_RADIUS) et ses points libres
        double radius = Math.max(MIN_RADIUS, space(level, player));
        // hauteur de la chute finale : FINAL_HEIGHT si le ciel est libre au-dessus du centre, sinon toute la place disponible
        double dive = Math.max(1.5, headroom(level, player, center, FINAL_HEIGHT));
        List<Vec3> spots = spots(level, player, center, radius);
        // la zone (INVISIBLE) : son centre et son rayon servent à la caméra cinématique du lanceur
        MinksFx.spawn(level, player, MinksFx.ZONE, center, Human.flatLook(player), (float) radius, duration(sulong) + 16);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
        Map<LivingEntity, int[]> hits = new HashMap<>(); // cible -> {coups, dernier tick}
        Deque<Integer> recent = new ArrayDeque<>();      // derniers points de zone utilisés (pas de répétition)
        Goro.start(new Goro.Session() {
            int t;
            int type, groundRun, zoneTurn;
            LivingEntity target;

            @Override
            public boolean tick() {
                if (!Minks.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                player.fallDistance = 0.0;
                if (t < PREP) {
                    if (t % 2 == 0) { // l'électricité parcourt le corps
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.3, 0.6, 0.3, 0.15);
                    }
                    return true;
                }
                int k = t - PREP;
                if (k < steps * step) {
                    int s = k % step;
                    if (s == 0) { // nouvelle apparition : type varié, alternance sol / air, autour des mobs ou sur toute la zone
                        type = pick(level.getRandom(), type, groundRun, zoneTurn++);
                        groundRun = aerial(type) ? 0 : groundRun + 1;
                        target = (type == FAR || type == RISE)
                                ? zoneMove(level, player, center, radius, type, spots, recent, target)
                                : move(level, player, center, radius, type, target);
                    }
                    for (int at : strikeTicks(type)) {
                        if (s == at) {
                            strike(level, player, hits, maxHits, t, type);
                        }
                    }
                    if (type == ABOVE && s == STRIKE) { // frappe plongeante puis chute rapide
                        player.setDeltaMovement(0.0, -1.2, 0.0);
                        player.connection.send(new ClientboundSetEntityMotionPacket(player));
                    }
                    return true;
                }
                int ret = returnTick(sulong);
                if (t == ret) { // il se place AU-DESSUS du centre, face à l'avant : l'électricité se concentre
                    player.removeAttached(ModAttachments.MINKS_MOVE);
                    jump(level, player, center.add(0.0, dive, 0.0), player.getYRot(), 20.0F);
                    player.setNoGravity(true);
                }
                if (t > ret && t < ret + DIVE) { // il tient en l'air pendant la concentration
                    player.setDeltaMovement(0.0, 0.0, 0.0);
                    player.connection.send(new ClientboundSetEntityMotionPacket(player));
                    if (t % 2 == 0) {
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.4, 0.5, 0.4, 0.15);
                    }
                }
                if (t >= ret + DIVE && t < ret + FINAL_DELAY) { // plongée : vitesse réglée pour arriver pile au sol, au centre
                    if (t == ret + DIVE) {
                        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS,
                                1.0F, 1.4F);
                    }
                    int left = ret + FINAL_DELAY - t;
                    double dy = (center.y - player.getY()) / left;
                    double dx = (center.x - player.getX()) / left, dz = (center.z - player.getZ()) / left;
                    player.setDeltaMovement(dx, Math.min(-0.2, dy), dz);
                    player.connection.send(new ClientboundSetEntityMotionPacket(player));
                }
                if (t == ret + FINAL_DELAY) { // il s'écrase AU CENTRE de la zone
                    player.setNoGravity(false);
                    player.teleportTo(level, center.x, center.y, center.z, Set.of(), player.getYRot(), player.getXRot(), false);
                    player.setDeltaMovement(0.0, 0.0, 0.0);
                    player.connection.send(new ClientboundSetEntityMotionPacket(player));
                    finish(level, player, center, radius);
                }
                if (t >= duration(sulong)) {
                    Minks.clearAction(player);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Minks.clearAction(player);
                player.removeAttached(ModAttachments.MINKS_MOVE);
                player.setNoGravity(false);
                player.fallDistance = 0.0;
            }
        });
    }

    // ------------------------------------------------------------------ chorégraphie

    /**
     * Prochain mouvement : jamais deux fois le même d'affilée ; après un mouvement aérien, retour au sol ; après deux ou trois
     * au sol, un aérien ; un mouvement sur trois environ part ailleurs dans la zone (loin) : la séquence couvre toute la zone.
     */
    private static int pick(RandomSource random, int previous, int groundRun, int turn) {
        boolean air = !aerial(previous) && (groundRun >= 3 || (groundRun >= 2 && random.nextBoolean()));
        boolean zone = turn % 3 == 2 || random.nextFloat() < 0.15F;
        int[] pool = air ? (zone ? new int[] {RISE, ABOVE} : new int[] {ABOVE, AIR_DIAG, RISE})
                : (zone ? new int[] {FAR, DASH} : new int[] {FRONT, SIDE, BEHIND, DASH, MULTI, FAR});
        int type;
        do {
            type = pool[random.nextInt(pool.length)];
        } while (type == previous && pool.length > 1);
        return type;
    }

    /**
     * Mouvement autour d'une cible (ou, sans cible, d'un point de la zone) : devant, sur le côté, derrière, dash rasant,
     * au-dessus, diagonale aérienne, rafale de face ; distances et hauteurs variées. La position voulue est essayée, puis des
     * variantes (angles décalés). Aucune position sûre : le joueur reste où il est (il frappe quand même).
     */
    private static LivingEntity move(ServerLevel level, ServerPlayer player, Vec3 center, double radius, int type, LivingEntity last) {
        RandomSource random = level.getRandom();
        LivingEntity target = chooseTarget(level, player, center, radius, last);
        Vec3 base;
        double facing; // direction (radians) vers laquelle la cible regarde (devant elle)
        if (target != null) {
            base = target.position();
            facing = Math.toRadians(target.getYRot() + 90.0);
        } else {
            double a = random.nextDouble() * Math.PI * 2, d = radius * (0.2 + random.nextDouble() * 0.6);
            base = center.add(Math.cos(a) * d, 0.0, Math.sin(a) * d);
            facing = random.nextDouble() * Math.PI * 2;
        }
        double side = random.nextBoolean() ? 1.0 : -1.0;
        double angle = switch (type) {
            case SIDE -> facing + side * (Math.PI / 2 + (random.nextDouble() - 0.5) * 0.6);
            case BEHIND -> facing + Math.PI + (random.nextDouble() - 0.5) * 0.6;
            case AIR_DIAG -> facing + side * (Math.PI / 4 + random.nextDouble() * Math.PI / 4);
            case DASH -> facing + side * (Math.PI / 3 + random.nextDouble() * Math.PI / 2);
            default -> facing + (random.nextDouble() - 0.5) * 0.7;
        };
        double dist = switch (type) {
            case DASH -> Math.min(3.5, radius * 0.7);
            case ABOVE -> 0.2 + random.nextDouble() * 0.4;
            case AIR_DIAG -> 1.5 + random.nextDouble() * 0.8;
            case MULTI -> 1.2;
            default -> 1.3 + random.nextDouble() * 0.6;
        };
        double tall = target != null ? target.getBbHeight() : 1.8;
        double lift = switch (type) {
            case ABOVE -> tall + 0.4 + random.nextDouble() * 0.6;
            case AIR_DIAG -> Math.min(2.2, 0.8 + radius * 0.2 + random.nextDouble() * 0.5);
            default -> 0.0;
        };
        double[] tries = {0.0, 0.7, -0.7, 1.4, -1.4, Math.PI};
        for (double tr : tries) {
            double a = angle + tr;
            Vec3 pos = clampToZone(base.add(Math.cos(a) * dist, 0.0, Math.sin(a) * dist), center, radius);
            double ground = Human.ground(level, pos.x, pos.z, pos.y + 1.0);
            double y = ground + Math.min(lift, DIVE_HEIGHT);
            pos = new Vec3(pos.x, y, pos.z);
            if (Math.abs(ground - center.y) > 3.5 || y > center.y + DIVE_HEIGHT + 1.0 || !safe(level, player, pos)) {
                continue;
            }
            arrive(level, player, pos, target != null ? target.position().add(0.0, tall * 0.5, 0.0) : base.add(0.0, 1.0, 0.0), type);
            if (type == DASH) { // dash rasant : il fonce sur la cible et la frappe en arrivant
                Vec3 to = new Vec3(base.x - pos.x, 0.0, base.z - pos.z);
                double len = to.length();
                if (len > 1.0E-3) {
                    Vec3 v = to.scale(Math.min(2.0, Math.max(0.0, len - 1.0)) / len);
                    player.setDeltaMovement(v.x, -0.05, v.z);
                    player.connection.send(new ClientboundSetEntityMotionPacket(player));
                }
            }
            return target;
        }
        player.setAttached(ModAttachments.MINKS_MOVE, Minks.now(player) * 8 + type);
        return last;
    }

    /**
     * Apparition ailleurs dans la zone : un point libre relevé au départ, LOIN de la position actuelle et pas parmi les derniers
     * utilisés ; au sol (FAR) ou en hauteur (RISE) ; le joueur se tourne vers le mob le plus proche (ou le centre).
     */
    private static LivingEntity zoneMove(ServerLevel level, ServerPlayer player, Vec3 center, double radius, int type, List<Vec3> spots,
                                         Deque<Integer> recent, LivingEntity last) {
        RandomSource random = level.getRandom();
        if (spots.isEmpty()) {
            return move(level, player, center, radius, type == RISE ? AIR_DIAG : SIDE, last);
        }
        for (int tryN = 0; tryN < 12; tryN++) {
            int i = random.nextInt(spots.size());
            Vec3 spot = spots.get(i);
            if (recent.contains(i) || (tryN < 8 && horizontal(spot, player.position()) < radius * 0.6)) {
                continue;
            }
            Vec3 pos = type == RISE ? spot.add(0.0, Math.min(DIVE_HEIGHT - 0.5, 1.3 + random.nextDouble() * 1.2), 0.0) : spot;
            if (!safe(level, player, pos)) {
                continue;
            }
            recent.addLast(i);
            if (recent.size() > 6) {
                recent.removeFirst();
            }
            LivingEntity target = nearest(level, player, pos, center, radius);
            arrive(level, player, pos, target != null ? target.position().add(0.0, target.getBbHeight() * 0.5, 0.0) : center.add(0.0, 1.0, 0.0), type);
            return target != null ? target : last;
        }
        return move(level, player, center, radius, type == RISE ? AIR_DIAG : SIDE, last);
    }

    /** Arrivée sur une position : regard vers le point visé (lacet et tangage), transport, mouvement synchronisé. */
    private static void arrive(ServerLevel level, ServerPlayer player, Vec3 pos, Vec3 aim, int type) {
        Vec3 look = aim.subtract(pos.add(0.0, player.getEyeHeight(), 0.0));
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
        jump(level, player, pos, yaw, pitch);
        player.setAttached(ModAttachments.MINKS_MOVE, Minks.now(player) * 8 + type);
    }

    /** Une cible au hasard dans la zone (souvent différente de la précédente : la danse passe d'un mob à l'autre). */
    private static LivingEntity chooseTarget(ServerLevel level, ServerPlayer player, Vec3 center, double radius, LivingEntity last) {
        List<LivingEntity> targets = new ArrayList<>(Goro.targets(level, player, new AABB(center, center).inflate(radius + 1.0, 4.0, radius + 1.0)));
        targets.removeIf(e -> horizontal(e.position(), center) > radius + 1.0);
        if (targets.isEmpty()) {
            return null;
        }
        LivingEntity target = targets.get(level.getRandom().nextInt(targets.size()));
        if (target == last && targets.size() > 1) {
            target = targets.get((targets.indexOf(target) + 1) % targets.size());
        }
        return target;
    }

    private static LivingEntity nearest(ServerLevel level, ServerPlayer player, Vec3 pos, Vec3 center, double radius) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Goro.targets(level, player, new AABB(center, center).inflate(radius + 1.0, 4.0, radius + 1.0))) {
            double d = e.position().distanceToSqr(pos);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private static Vec3 clampToZone(Vec3 pos, Vec3 center, double radius) {
        if (horizontal(pos, center) <= radius) {
            return pos;
        }
        Vec3 dir = new Vec3(pos.x - center.x, 0.0, pos.z - center.z).normalize();
        return new Vec3(center.x + dir.x * radius, pos.y, center.z + dir.z * radius);
    }

    /** Position sûre : sans collision, et en vue directe depuis la position actuelle (jamais à travers un mur). */
    private static boolean safe(ServerLevel level, ServerPlayer player, Vec3 pos) {
        AABB box = player.getBoundingBox().move(pos.subtract(player.position()));
        if (!level.noCollision(player, box)) {
            return false;
        }
        Vec3 from = player.getEyePosition(), to = pos.add(0.0, player.getEyeHeight(), 0.0);
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
    }

    private static void jump(ServerLevel level, ServerPlayer player, Vec3 pos, float yaw) {
        jump(level, player, pos, yaw, player.getXRot());
    }

    /** Transporte le joueur (côté serveur, synchronisé par Minecraft), regard (lacet, tangage) ; éclair fin et poussière. */
    private static void jump(ServerLevel level, ServerPlayer player, Vec3 pos, float yaw, float pitch) {
        Vec3 from = player.position();
        if (from.distanceToSqr(pos) > 0.01) {
            MinksFx.spawn(level, player, MinksFx.BLINK, from, pos.subtract(from), Minks.sulong(player) ? 1.2F : 1.0F, 7);
            Human.groundBurst(level, from, 4, 0.3, 0.1); // appui : poussière au départ
        }
        player.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), yaw, pitch, false);
        player.setDeltaMovement(0.0, 0.0, 0.0);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.fallDistance = 0.0;
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.15F, 2.0F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1.0, pos.z, 5, 0.3, 0.6, 0.3, 0.2);
        if (Human.ground(level, pos.x, pos.z, pos.y) > pos.y - 0.3) {
            Human.groundBurst(level, pos, 4, 0.3, 0.08); // atterrissage
        }
    }

    // ------------------------------------------------------------------ frappes

    /**
     * Une frappe : TOUS les mobs de la zone d'impact de cette griffe (sphère devant le joueur dans l'axe de son regard, plus ce
     * qui est tout près de lui) sont touchés EN MÊME TEMPS ; seule la limite par cible (au plus {@code maxHits} coups, espacés
     * de {@link #MIN_GAP} ticks) peut en épargner un. Les mobs de la zone globale hors de cette zone d'impact ne sont pas touchés.
     */
    private static void strike(ServerLevel level, ServerPlayer player, Map<LivingEntity, int[]> hits, int maxHits, int t, int type) {
        float p = Minks.power(player);
        Vec3 c = player.position().add(0.0, 1.0, 0.0);
        Vec3 look = player.getLookAngle();
        Vec3 impact = c.add(look.scale(HIT_AHEAD)); // centre de la zone d'impact de la griffe
        int touched = 0;
        for (LivingEntity e : Goro.targets(level, player, new AABB(impact, impact).inflate(HIT_REACH + 1.5))) {
            Vec3 ec = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
            double reach = e.getBbWidth() * 0.5 + e.getBbHeight() * 0.25;
            if (ec.distanceTo(impact) > HIT_REACH + reach && ec.distanceTo(c) > HIT_CLOSE + reach) {
                continue; // hors de la zone d'impact de cette griffe
            }
            int[] h = hits.computeIfAbsent(e, x -> new int[] {0, -100});
            if (h[0] >= maxHits || t - h[1] < MIN_GAP) {
                continue;
            }
            h[0]++;
            h[1] = t;
            Vec3 away = new Vec3(ec.x - c.x, 0.0, ec.z - c.z);
            away = away.lengthSqr() < 1.0E-4 ? look : away.normalize();
            Minks.claw(level, player, e, DAMAGE * p, away.scale(0.3).add(0.0, type == ABOVE ? -0.1 : 0.15, 0.0), (int) (STUN * p));
            touched++;
        }
        Minks.slash(level, player, MinksFx.IMPACT, impact, look, (touched > 0 ? 1.0F : 0.75F) * p, 10);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F,
                1.4F + level.getRandom().nextFloat() * 0.3F);
    }

    /**
     * L'écrasement au centre : l'onde de choc électrique couvre TOUTE la zone (MinksFx.CRASH, rayon de la zone) et touche tous
     * les mobs de la zone (recul vers l'extérieur, paralysie limitée), poussière et éclats du sol. Pas de coup de griffe devant
     * lui : c'est une chute.
     */
    private static void finish(ServerLevel level, ServerPlayer player, Vec3 center, double radius) {
        float p = Minks.power(player);
        Vec3 c = center.add(0.0, 0.5, 0.0);
        for (LivingEntity e : Goro.targets(level, player, new AABB(c, c).inflate(radius + 1.0, 3.0, radius + 1.0))) {
            Vec3 ec = e.position();
            double d = horizontal(ec, center);
            if (d > radius + e.getBbWidth() * 0.5 || Math.abs(ec.y - center.y) > 3.0) {
                continue;
            }
            Vec3 away = new Vec3(ec.x - c.x, 0.0, ec.z - c.z);
            away = away.lengthSqr() < 1.0E-4 ? Human.flatLook(player) : away.normalize();
            float damage = (float) (FINAL_DAMAGE * p * (1.0 - 0.4 * Math.min(1.0, d / radius))); // plus fort au centre
            Minks.claw(level, player, e, damage, away.scale(1.1).add(0.0, 0.5, 0.0), (int) (STUN * 1.5F * p));
        }
        MinksFx.spawn(level, player, MinksFx.CRASH, center, Human.flatLook(player), (float) radius, 40);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.6F, 0.9F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 1.2F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 0.6F);
        BuddhaHits.shake(level, c, 20.0, 1.3F, 12);
        Human.groundBurst(level, center, 50, Math.min(3.0, radius * 0.45), 0.3);
    }
}
