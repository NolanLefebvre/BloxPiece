package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.LunarianFx;

/**
 * Race Lunarian, niveau 50 (ultime) : TEMPÊTE DU JUGEMENT CÉLESTE. Séquence en plusieurs phases, toutes calculées par le
 * serveur (positions, vitesses, dégâts, projections) et vues identiques par tous (LUNARIAN_ACTION : poses, ailes, caméra) :
 * <ol>
 *   <li>PRÉPARATION (0-{@link #PREP}) : posture basse, ailes qui se déploient, flammes de plus en plus fortes au poing, puis un
 *       puissant battement d'ailes ;</li>
 *   <li>ASCENSION : envol VERTICAL réel (sans téléportation) jusqu'à {@link #MAX_HEIGHT} blocs, adapté à la place au-dessus
 *       (au moins {@link #MIN_HEIGHT}) ; ailes qui battent, traînée de flammes ;</li>
 *   <li>SURPLACE ({@link #HOVER}) : il bascule tête vers le sol, poing en feu devant lui ;</li>
 *   <li>PLONGÉE vers le point d'activation (là où il était au sol), de plus en plus vite ;</li>
 *   <li>IMPACT au sol, au centre : explosion de feu, onde au sol, débris ; dégâts autour ;</li>
 *   <li>TORNADE DE FEU née du point d'impact, qui monte jusqu'à la hauteur atteinte : elle aspire les créatures proches, les
 *       fait tourner et monter, les brûle à intervalle régulier ({@link #BURN_EVERY} ticks), puis les PROJETTE vers l'extérieur
 *       ({@link #FLING}) avant de se dissiper ({@link #TORNADO_LIFE}).</li>
 * </ol>
 * Interruption (mort, déconnexion, changement de dimension ou de race) : gravité rendue, plus de chute mortelle (fallDistance
 * remis à zéro pendant toute l'attaque).
 */
public class LunarianJudgmentAbility implements Ability {

    public static final int PREP = 22, ASCENT_MAX = 30, HOVER = 9, DIVE_MAX = 30, RECOVER = 22;
    public static final int COOLDOWN = 20 * 75;
    public static final double MAX_HEIGHT = 26.0, MIN_HEIGHT = 6.0;
    public static final double IMPACT_RADIUS = 6.0;
    public static final float IMPACT_DAMAGE = 12.0F, IMPACT_EDGE = 6.0F;
    /** Tornade : rayon de base (rendu), rayon d'aspiration, durée, fréquence des brûlures, instant de la projection. */
    public static final float TORNADO_RADIUS = 4.5F;
    public static final double PULL_RADIUS = 11.0, CORE = 1.8;
    public static final int TORNADO_LIFE = 110, BURN_EVERY = 10, FLING = 84;
    public static final float BURN_DAMAGE = 3.0F, FLING_DAMAGE = 7.0F;

    private final int level;

    public LunarianJudgmentAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "lunarian_celestial_storm";
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

    /** Place libre au-dessus du joueur (blocs), jusqu'à MAX_HEIGHT. */
    private static double headroom(ServerLevel level, ServerPlayer player) {
        Vec3 from = player.position().add(0.0, 1.9, 0.0);
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(0.0, MAX_HEIGHT + 2.0, 0.0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? MAX_HEIGHT : Math.min(MAX_HEIGHT, hit.getLocation().y - from.y - 0.5);
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        if (Lunarian.refuseBusy(player)) {
            return false;
        }
        if (headroom((ServerLevel) player.level(), player) < MIN_HEIGHT) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.lunarian.no_sky"), true);
            return false;
        }
        return true;
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Lunarian.setAction(player, Lunarian.JUDGMENT);
        double gy = Human.ground(level, player.getX(), player.getZ(), player.getY());
        Vec3 center = new Vec3(player.getX(), gy, player.getZ());
        double height = Math.max(MIN_HEIGHT, headroom(level, player));
        Goro.start(new Goro.Session() {
            int t, phase, phaseStart, end = -1;
            double apexY;

            private void enter(int p) {
                phase = p;
                phaseStart = t;
            }

            private void motion(Vec3 v) {
                player.setDeltaMovement(v);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }

            @Override
            public boolean tick() {
                if (!Lunarian.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                player.fallDistance = 0.0;
                if (end >= 0) { // récupération après l'impact
                    if (t - end >= RECOVER) {
                        Lunarian.clearAction(player, Lunarian.JUDGMENT_END);
                        return false;
                    }
                    return true;
                }
                int k = t - phaseStart;
                switch (phase) {
                    case 0 -> { // préparation : il se ramasse, les flammes grandissent au poing
                        Human.halt(player);
                        if (t % 4 == 0) {
                            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 2 + t / 4, 0.3, 0.4, 0.3, 0.03);
                        }
                        if (t == PREP) { // puissant battement d'ailes : décollage
                            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.6F, 0.7F);
                            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.6F);
                            Human.groundBurst(level, center, 20, 0.8, 0.15);
                            level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.2, center.z, 16, 1.0, 0.1, 1.0, 0.08);
                            apexY = player.getY() + height;
                            player.setNoGravity(true);
                            enter(1);
                        }
                    }
                    case 1 -> { // ascension : réelle, verticale, battements d'ailes
                        double left = apexY - player.getY();
                        motion(new Vec3(0.0, Math.max(0.35, Math.min(1.5, left * 0.32)), 0.0));
                        if (k % 6 == 0) {
                            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.9F, 1.0F);
                        }
                        LunarianFx.spawn(level, LunarianFx.TRAIL, player.position().add(0.0, 0.6, 0.0), new Vec3(0, -1, 0), 0.8F, 0, 14);
                        if (left < 0.5 || k >= ASCENT_MAX) {
                            enter(2);
                        }
                    }
                    case 2 -> { // surplace : il bascule tête vers le sol
                        motion(Vec3.ZERO);
                        if (k == 1) {
                            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.6F);
                        }
                        if (k >= HOVER) {
                            enter(3);
                        }
                    }
                    case 3 -> { // plongée vers le point d'activation, de plus en plus vite
                        Vec3 to = center.subtract(player.position());
                        double dist = to.length(), speed = Math.min(3.8, 1.1 + 0.28 * k);
                        LunarianFx.spawn(level, LunarianFx.TRAIL, player.position().add(0.0, 1.0, 0.0), to.normalize(), 1.6F, 0, 16);
                        if (k % 5 == 0) {
                            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.5F + 0.04F * k);
                        }
                        if (dist <= speed + 0.4 || k >= DIVE_MAX) {
                            impact(level, player, center, apexY - center.y);
                            end = t;
                            return true;
                        }
                        motion(to.scale(speed / dist));
                    }
                    default -> {
                    }
                }
                return true;
            }

            @Override
            public void cancel() {
                player.setNoGravity(false);
                Lunarian.clearAction(player, Lunarian.JUDGMENT);
                Lunarian.clearAction(player, Lunarian.JUDGMENT_END);
            }
        });
    }

    /** Il s'écrase au centre : explosion de feu, dégâts autour, puis la tornade naît du sol. */
    private static void impact(ServerLevel level, ServerPlayer player, Vec3 center, double height) {
        player.teleportTo(level, center.x, center.y, center.z, Set.of(), player.getYRot(), player.getXRot(), false);
        player.setNoGravity(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.fallDistance = 0.0;
        Lunarian.setAction(player, Lunarian.JUDGMENT_END);
        LunarianFx.spawn(level, LunarianFx.IMPACT, center, new Vec3(0, 1, 0), (float) IMPACT_RADIUS, 0, 40);
        LunarianFx.spawn(level, LunarianFx.TORNADO, center, new Vec3(0, Math.max(MIN_HEIGHT, height), 0), TORNADO_RADIUS, 0, TORNADO_LIFE);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.6F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5F, 0.4F);
        Human.groundBurst(level, center, 40, 1.8, 0.3);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.3, center.z, 20, 1.5, 0.3, 1.5, 0.2);
        BuddhaHits.shake(level, center, 30.0, 1.3F, 16);
        Set<LivingEntity> hit = new HashSet<>();
        for (LivingEntity e : Goro.targets(level, player, new AABB(center, center).inflate(IMPACT_RADIUS + 1.0, 4.0, IMPACT_RADIUS + 1.0))) {
            double d = e.position().distanceTo(center);
            if (d > IMPACT_RADIUS) {
                continue;
            }
            float k = (float) (d / IMPACT_RADIUS);
            Vec3 out = new Vec3(e.getX() - center.x, 0.0, e.getZ() - center.z);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
            if (Human.strike(level, player, e, IMPACT_DAMAGE + (IMPACT_EDGE - IMPACT_DAMAGE) * k, out.scale(0.4).add(0.0, 0.5, 0.0), hit)) {
                e.igniteForTicks(60);
            }
        }
        tornado(level, player, center, Math.max(MIN_HEIGHT, height));
    }

    /**
     * La tornade, côté serveur : aspiration vers l'axe, rotation autour, montée progressive (les créatures sont réellement
     * emportées), brûlure toutes les {@link #BURN_EVERY} ticks (jamais à chaque tick), projection vers l'extérieur à
     * {@link #FLING}, synchronisée avec le début de la dissipation.
     */
    private static void tornado(ServerLevel level, ServerPlayer owner, Vec3 c, double height) {
        Map<LivingEntity, Integer> lastBurn = new HashMap<>();
        Goro.start(new Goro.Session() {
            int t;

            @Override
            public boolean tick() {
                t++;
                if (t > TORNADO_LIFE || owner.isRemoved() || owner.level() != level) {
                    return false;
                }
                if (t % 8 == 0 && t < FLING) {
                    level.playSound(null, c.x, c.y + 2.0, c.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 2.0F, 0.5F);
                    level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 1.0, c.z, 12, 2.5, 3.0, 2.5, 0.08);
                }
                if (t < 6 || t > FLING) {
                    return true;
                }
                double reach = PULL_RADIUS * Math.min(1.0, t / 16.0); // la tornade grandit : son aspiration aussi
                for (LivingEntity e : Goro.targets(level, owner, new AABB(c, c).inflate(reach, height + 2.0, reach).move(0.0, height * 0.5, 0.0))) {
                    double dx = e.getX() - c.x, dz = e.getZ() - c.z, d = Math.sqrt(dx * dx + dz * dz);
                    if (d > reach || e.getY() < c.y - 2.0 || e.getY() > c.y + height + 2.0) {
                        continue;
                    }
                    if (t == FLING) { // projection violente vers l'extérieur
                        Vec3 out = d < 1.0E-3 ? new Vec3(1, 0, 0) : new Vec3(dx / d, 0.0, dz / d);
                        Human.strike(level, owner, e, FLING_DAMAGE, out.scale(2.2).add(0.0, 0.9, 0.0), new HashSet<>());
                        e.igniteForTicks(80);
                        continue;
                    }
                    Vec3 radial = d < 1.0E-3 ? Vec3.ZERO : new Vec3(dx / d, 0.0, dz / d);
                    Vec3 tangent = new Vec3(-radial.z, 0.0, radial.x);
                    double lift = c.y + 1.5 + Math.min(height * 0.55, (t - 6) * 0.2);
                    double vy = Math.max(-0.4, Math.min(0.55, (lift - e.getY()) * 0.2));
                    Vec3 v = radial.scale(Math.max(-0.7, Math.min(0.5, (CORE - d) * 0.18))).add(tangent.scale(0.5)).add(0.0, vy, 0.0);
                    e.setDeltaMovement(v);
                    e.needsSync = true;
                    e.fallDistance = 0.0;
                    if (e instanceof ServerPlayer p) {
                        p.connection.send(new ClientboundSetEntityMotionPacket(p));
                    }
                    // brûlure à intervalle contrôlé
                    Integer last = lastBurn.get(e);
                    if (d < reach * 0.6 && (last == null || t - last >= BURN_EVERY)) {
                        lastBurn.put(e, t);
                        Human.strike(level, owner, e, BURN_DAMAGE, Vec3.ZERO, new HashSet<>());
                        e.igniteForTicks(60);
                    }
                }
                if (t == FLING) {
                    level.playSound(null, c.x, c.y + 3.0, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4F, 0.8F);
                }
                return true;
            }
        });
    }
}
