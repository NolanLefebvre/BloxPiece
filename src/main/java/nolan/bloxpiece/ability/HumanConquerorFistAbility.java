package nolan.bloxpiece.ability;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
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

import nolan.bloxpiece.fx.HumanFx;

/**
 * Race Humaine, niveau 50 : POING DU CONQUÉRANT, l'attaque ultime. Deux phases bien distinctes :
 * <ol>
 *   <li>PRÉPARATION ({@link #CHARGE} ticks) : le joueur, planté au sol, ramène son poing droit en arrière et concentre toute sa
 *       puissance ; l'aura du poing grandit couche après couche (HumanFistLayer), le sol tremble sous lui ;</li>
 *   <li>FRAPPE ({@link #CHARGE} - {@link #RELEASE}) : le poing part droit devant et libère une énorme onde de choc
 *       (HumanFx.WAVE : un poing d'air géant, anneaux de compression, air déformé sur tout le trajet) qui file à
 *       {@link #WAVE_SPEED} blocs/tick jusqu'à {@link #RANGE} blocs.</li>
 * </ol>
 * Dégâts appliqués quand l'onde ATTEINT les cibles (une fois chacune). L'onde explose sur sa première cible, sur le premier
 * bloc, ou au bout de sa portée : explosion massive (HumanFx.BLAST, dégâts dégressifs sur {@link #BLAST_RADIUS} blocs), puis
 * une onde secondaire qui repousse tout jusqu'à {@link #SECOND_RADIUS} blocs. Cratère modeste, sans fissures (GoroTerrain : règle
 * mobGriefing, village protégé). Recharge importante : {@link #COOLDOWN}.
 */
public class HumanConquerorFistAbility implements Ability {

    public static final int CHARGE = 40, RELEASE = 44, DURATION = 72;
    public static final int COOLDOWN = 20 * 75;
    public static final double RANGE = 30.0, WAVE_SPEED = 1.7;
    /** Rayon de l'onde au départ et à pleine portée (blocs). */
    public static final double WAVE_RADIUS_START = 1.2, WAVE_RADIUS_END = 3.0;
    public static final float WAVE_DAMAGE = 28.0F, BLAST_DAMAGE = 44.0F, SECOND_DAMAGE = 6.0F;
    public static final double BLAST_RADIUS = 6.5, SECOND_RADIUS = 12.0;
    public static final int SECOND_DELAY = 8;
    public static final double WAVE_KNOCKBACK = 1.6, BLAST_KNOCKBACK = 2.2, SECOND_KNOCKBACK = 1.0;
    /** Durée de l'onde après son impact (le temps que ses anneaux s'effacent) et de l'explosion. */
    public static final int WAVE_FADE = 14, BLAST_LIFE = 54;

    private final int level;

    public HumanConquerorFistAbility(int level) {
        this.level = level;
    }

    /** Rayon de l'onde à {@code d} blocs de son départ. Partagé avec le rendu. */
    public static double waveRadius(double d) {
        return WAVE_RADIUS_START + (WAVE_RADIUS_END - WAVE_RADIUS_START) * Math.min(1.0, d / RANGE);
    }

    @Override
    public String id() {
        return "human_conqueror_fist";
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
        return !Human.refuseBusy(player);
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Human.setAction(player, Human.CONQUEROR);
        Goro.start(new Goro.Session() {
            int t;

            @Override
            public boolean tick() {
                if (!Human.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                if (t <= RELEASE) {
                    Human.halt(player); // planté au sol pendant toute la préparation et la frappe
                }
                if (t < CHARGE) {
                    gather(level, player, t);
                }
                if (t == CHARGE) {
                    Vec3 f = Human.rightFist(player, 0.4);
                    level.playSound(null, f.x, f.y, f.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5F, 0.4F);
                }
                if (t == RELEASE) {
                    release(level, player);
                }
                if (t >= DURATION) {
                    Human.clearAction(player, Human.CONQUEROR);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Human.clearAction(player, Human.CONQUEROR);
            }
        });
    }

    /** Préparation : le sol tremble de plus en plus, des graviers se soulèvent, la tension monte (son). */
    private static void gather(ServerLevel level, ServerPlayer player, int t) {
        float k = t / (float) CHARGE;
        Vec3 feet = player.position();
        if (t % 3 == 0) {
            double r = 1.2 + 1.5 * k;
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            Vec3 p = feet.add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
            Human.groundBurst(level, new Vec3(p.x, Human.ground(level, p.x, p.z, feet.y), p.z), 2 + (int) (4 * k), 0.4, 0.05 + 0.1 * k);
        }
        if (t % 5 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, feet.x, feet.y + 0.1, feet.z, 1 + (int) (3 * k), 0.8 + k, 0.02, 0.8 + k, 0.03);
        }
        if (t % 8 == 0) {
            level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F + k, 0.7F + 0.8F * k);
        }
        if (t % 10 == 0) {
            BuddhaHits.shake(level, feet, 8.0, 0.1F + 0.3F * k, 6);
        }
    }

    /** La frappe : l'onde part du poing ; elle avance tick par tick et touche les cibles quand elle les atteint. */
    private static void release(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 from = Human.rightFist(player, 1.1);
        Vec3 far = from.add(look.scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 to = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        double length = to.distanceTo(from);
        int travel = Math.max(1, (int) Math.ceil(length / WAVE_SPEED));
        HumanFx wave = HumanFx.spawn(level, player, HumanFx.WAVE, from, to.subtract(from), (float) WAVE_RADIUS_END, travel + WAVE_FADE);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.5F, 0.6F);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.8F);
        level.sendParticles(ParticleTypes.EXPLOSION, from.x, from.y, from.z, 1, 0.0, 0.0, 0.0, 0.0);
        BuddhaHits.shake(level, from, 24.0, 1.2F, 10);
        // recul du lanceur : il encaisse la force de son propre coup
        player.push(-look.x * 0.3, 0.0, -look.z * 0.3);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
        Set<LivingEntity> hit = new HashSet<>();
        Goro.start(new Goro.Session() {
            int k;
            double done;

            @Override
            public boolean tick() {
                if (wave.isRemoved()) {
                    return false;
                }
                k++;
                double front = Math.min(length, k * WAVE_SPEED);
                Vec3 a = from.add(look.scale(done)), b = from.add(look.scale(front));
                double r = waveRadius(front);
                // cibles que le front vient d'atteindre : touchées une fois ; l'onde explose sur la plus proche
                LivingEntity first = null;
                double best = Double.MAX_VALUE;
                if (player.isAlive() && !player.isRemoved()) {
                    for (LivingEntity e : Human.targets(level, player, new AABB(a, b).inflate(r + 1.0))) {
                        Vec3 c = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
                        double along = c.subtract(from).dot(look);
                        if (hit.contains(e) || along > front + e.getBbWidth() || distanceToSegment(c, from, b) > r + e.getBbWidth() * 0.5) {
                            continue;
                        }
                        Human.strike(level, player, e, WAVE_DAMAGE, look.scale(WAVE_KNOCKBACK).add(0.0, 0.5, 0.0), hit);
                        Human.knockTrail(level, e, 10);
                        if (along < best) {
                            best = along;
                            first = e;
                        }
                    }
                }
                // souffle sur le passage du front
                level.sendParticles(ParticleTypes.CLOUD, b.x, b.y, b.z, 4, r * 0.4, r * 0.4, r * 0.4, 0.05);
                double gy = Human.ground(level, b.x, b.z, b.y);
                if (b.y - gy < r + 0.5) { // l'onde rase le sol : elle le laboure
                    Human.groundBurst(level, new Vec3(b.x, gy, b.z), 8, r * 0.5, 0.2);
                }
                done = front;
                if (first != null || front >= length) {
                    Vec3 at = first != null ? from.add(look.scale(Math.max(0.5, Math.min(best, front)))) : to;
                    if (first != null) {
                        wave.stopAt(at.subtract(from), k + WAVE_FADE);
                    }
                    explode(level, player, at, look);
                    return false;
                }
                return true;
            }
        });
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double k = len2 < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / len2));
        return p.distanceTo(a.add(ab.scale(k)));
    }

    /** Explosion massive au point d'impact, puis l'onde secondaire. */
    private static void explode(ServerLevel level, ServerPlayer owner, Vec3 at, Vec3 look) {
        Set<LivingEntity> blast = new HashSet<>();
        if (owner.isAlive() && !owner.isRemoved()) {
            for (LivingEntity e : Human.targets(level, owner, new AABB(at, at).inflate(BLAST_RADIUS))) {
                double d = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0).distanceTo(at);
                if (d > BLAST_RADIUS + e.getBbWidth() * 0.5) {
                    continue;
                }
                float damage = (float) (BLAST_DAMAGE * (1.0 - 0.5 * Math.min(1.0, d / BLAST_RADIUS)));
                Vec3 dir = e.position().subtract(at);
                Vec3 push = dir.lengthSqr() < 1.0E-4 ? look.scale(BLAST_KNOCKBACK) : dir.normalize().scale(BLAST_KNOCKBACK);
                if (Human.strike(level, owner, e, damage, push.add(0.0, 0.8, 0.0), blast)) {
                    Human.knockTrail(level, e, 14);
                }
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 2, 1.5, 0.8, 1.5, 0.0);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 50, 2.5, 1.2, 2.5, 0.25);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.5, at.z, 20, 3.0, 1.0, 3.0, 0.03);
        Human.groundBurst(level, new Vec3(at.x, Human.ground(level, at.x, at.z, at.y), at.z), 60, 2.5, 0.4);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 5.0F, 0.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 2.0F, 0.4F);
        BuddhaHits.shake(level, at, 40.0, 2.2F, 20);
        HumanFx.spawn(level, owner, HumanFx.BLAST, at, look, (float) BLAST_RADIUS, BLAST_LIFE);
        // cratère modeste et débris, SANS fissures (0 fissure) ; mêmes règles de destruction que les autres grandes attaques
        GoroTerrain.scar(level, at, 2.5, 1.5, 0.0, 0, 10);
        // onde secondaire : repousse tout autour (une fois), sans gros dégâts
        Goro.later(SECOND_DELAY, () -> {
            if (!owner.isAlive() || owner.isRemoved() || owner.level() != level) {
                return;
            }
            Set<LivingEntity> second = new HashSet<>();
            for (LivingEntity e : Human.targets(level, owner, new AABB(at, at).inflate(SECOND_RADIUS, 4.0, SECOND_RADIUS))) {
                Vec3 d = new Vec3(e.getX() - at.x, 0.0, e.getZ() - at.z);
                if (d.length() > SECOND_RADIUS) {
                    continue;
                }
                Vec3 dir = d.lengthSqr() < 1.0E-4 ? look : d.normalize();
                Human.strike(level, owner, e, SECOND_DAMAGE, dir.scale(SECOND_KNOCKBACK).add(0.0, 0.4, 0.0), second);
            }
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.4F);
            BuddhaHits.shake(level, at, 30.0, 0.9F, 10);
        });
    }
}
