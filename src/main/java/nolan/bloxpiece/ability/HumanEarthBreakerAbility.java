package nolan.bloxpiece.ability;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.HumanFx;

/**
 * Race Humaine, niveau 30 : BRISEUR TERRESTRE. Le joueur lève haut son poing droit en concentrant sa force
 * (0-{@link #RAISE}), tient la pose une seconde de tension ({@link #HOLD}), puis l'abat de tout son poids sur le sol devant lui.
 * Au contact ({@link #IMPACT}) : explosion de poussière, fissures qui s'ouvrent, puis une grande ONDE DE CHOC CIRCULAIRE qui se
 * propage jusqu'à {@link #RADIUS} blocs en {@link #WAVE_TICKS} ticks (HumanFx.QUAKE : plusieurs couches 3D animées). Chaque
 * créature est touchée UNE fois, à l'instant où le front de l'onde l'atteint ({@link #front}, même courbe que le rendu) :
 * dégâts élevés (plus forts près du centre) et recul vers l'extérieur.
 * <p>
 * Terrain : AUCUN bloc n'est cassé (les fissures, éclats et débris sont des effets visuels, plus des particules du vrai sol) :
 * l'attaque reste maîtrisée, même en zone construite.
 */
public class HumanEarthBreakerAbility implements Ability {

    public static final int RAISE = 12, HOLD = 17, IMPACT = 21, DURATION = 44;
    public static final int WAVE_TICKS = 14;
    public static final int COOLDOWN = 20 * 25;
    public static final double RADIUS = 8.0;
    /** Dégâts au centre et au bord de l'onde ; recul vers l'extérieur et vers le haut. */
    public static final float DAMAGE_CENTER = 24.0F, DAMAGE_EDGE = 14.0F;
    public static final double KNOCKBACK = 1.2, LIFT = 0.55;
    public static final int FX_LIFE = WAVE_TICKS + 34;

    private final int level;

    public HumanEarthBreakerAbility(int level) {
        this.level = level;
    }

    /** Rayon du front de l'onde {@code k} ticks après l'impact (départ violent, puis ralentit). Partagé avec le rendu. */
    public static double front(double k) {
        double x = Math.max(0.0, Math.min(1.0, k / WAVE_TICKS));
        return RADIUS * (1.0 - (1.0 - x) * (1.0 - x) * 0.85 - 0.15 * (1.0 - x));
    }

    @Override
    public String id() {
        return "human_earth_breaker";
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
        Human.setAction(player, Human.BREAKER);
        Goro.start(new Goro.Session() {
            int t;
            Vec3 center;
            final Set<LivingEntity> hit = new HashSet<>();

            @Override
            public boolean tick() {
                if (!Human.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                if (t <= IMPACT) {
                    Human.halt(player); // de tout son poids : le joueur reste planté au sol
                }
                if (t == 2) {
                    level.playSound(null, player.getX(), player.getY() + 1.5, player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE,
                            SoundSource.PLAYERS, 1.0F, 0.45F);
                }
                if (t > RAISE && t < HOLD && t % 2 == 0) { // tension : petits graviers qui tremblent autour des pieds
                    Human.groundBurst(level, player.position(), 2, 0.6, 0.02);
                }
                if (t == HOLD) {
                    level.playSound(null, player.getX(), player.getY() + 1.5, player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                            SoundSource.PLAYERS, 1.2F, 0.5F);
                }
                if (t == IMPACT) {
                    center = impact(level, player);
                    damage(level, player, center, 0.0, hit);
                }
                if (center != null && t > IMPACT && t <= IMPACT + WAVE_TICKS + 1) {
                    int k = t - IMPACT;
                    damage(level, player, center, k, hit);
                    ring(level, center, front(k));
                    if (k == WAVE_TICKS) { // l'onde atteint sa portée : dernière gerbe de poussière sur tout le bord
                        for (int i = 0; i < 16; i++) {
                            double a = i * Math.PI * 2 / 16;
                            Vec3 p = center.add(Math.cos(a) * RADIUS, 0.0, Math.sin(a) * RADIUS);
                            double gy = Human.ground(level, p.x, p.z, center.y);
                            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, gy + 0.3, p.z, 1, 0.3, 0.1, 0.3, 0.01);
                        }
                        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 0.5F);
                    }
                }
                if (t >= DURATION) {
                    Human.clearAction(player, Human.BREAKER);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Human.clearAction(player, Human.BREAKER);
            }
        });
    }

    /** Le poing touche le sol : point d'impact (sur le sol, juste devant le joueur), son, souffle, effet 3D. */
    private static Vec3 impact(ServerLevel level, ServerPlayer player) {
        Vec3 f = Human.flatLook(player);
        Vec3 p = player.position().add(f.scale(0.9)).add(Human.right(player).scale(0.25));
        Vec3 at = new Vec3(p.x, Human.ground(level, p.x, p.z, player.getY()), p.z);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.4F, 0.5F);
        Human.groundBurst(level, at, 50, 0.9, 0.3);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.4, 0.1, 0.4, 0.0);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 20, 0.8, 0.1, 0.8, 0.1);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.2, at.z, 6, 0.6, 0.1, 0.6, 0.02);
        HumanFx.spawn(level, player, HumanFx.QUAKE, at, Human.flatLook(player), (float) RADIUS, FX_LIFE);
        BuddhaHits.shake(level, at, 22.0, 1.5F, 14);
        return at;
    }

    /** Toutes les créatures que le front de l'onde vient d'atteindre ({@code k} ticks après l'impact) : touchées une fois. */
    private static void damage(ServerLevel level, ServerPlayer player, Vec3 c, double k, Set<LivingEntity> hit) {
        double r = Math.max(1.5, front(k));
        for (LivingEntity e : Human.targets(level, player, new AABB(c, c).inflate(r + 1.0, 3.0, r + 1.0))) {
            if (hit.contains(e) || Math.abs(e.getY() - c.y) > 3.0) {
                continue; // (en l'air, très au-dessus de l'onde au sol : épargné)
            }
            Vec3 d = new Vec3(e.getX() - c.x, 0.0, e.getZ() - c.z);
            double dist = d.length();
            if (dist > r + e.getBbWidth() * 0.5) {
                continue;
            }
            float damage = (float) (DAMAGE_CENTER + (DAMAGE_EDGE - DAMAGE_CENTER) * Math.min(1.0, dist / RADIUS));
            Vec3 dir = dist < 1.0E-3 ? Human.flatLook(player) : d.scale(1.0 / dist);
            if (Human.strike(level, player, e, damage, dir.scale(KNOCKBACK).add(0.0, LIFT, 0.0), hit)) {
                Human.knockTrail(level, e, 8);
            }
        }
    }

    /** Éclats du sol et poussière sur le front de l'onde (le rendu 3D fait le reste). */
    private static void ring(ServerLevel level, Vec3 c, double r) {
        int n = Math.max(8, (int) (r * 2.5));
        for (int i = 0; i < n; i++) {
            double a = (i + level.getRandom().nextDouble() * 0.5) * Math.PI * 2 / n;
            double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
            double gy = Human.ground(level, x, z, c.y);
            Human.groundBurst(level, new Vec3(x, gy, z), 2, 0.2, 0.12);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, x, gy + 0.2, z, 1, 0.1, 0.05, 0.1, 0.02);
            }
        }
    }
}
