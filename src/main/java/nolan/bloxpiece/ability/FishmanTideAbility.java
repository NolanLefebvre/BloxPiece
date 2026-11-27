package nolan.bloxpiece.ability;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.FishmanFx;

/**
 * Race Fishman, niveau 30 : VAGUE DÉVASTATRICE. Le joueur se baisse, place ses deux mains devant lui, se penche vers le sol et le
 * frappe violemment des deux mains ({@link #SLAM}) : une énorme vague d'eau se forme et se propage tout autour de lui jusqu'à
 * {@link #RADIUS} blocs en {@link #SPREAD} ticks, suivie de vagues plus petites (rendu : FishmanFxRenderer). La hitbox suit le
 * front réel de la première vague : chaque créature est touchée UNE fois quand le front l'atteint (dégâts élevés, projetée vers
 * l'extérieur et en l'air).
 */
public class FishmanTideAbility implements Ability {

    public static final int CROUCH = 8, SLAM = 15, DURATION = 36, SPREAD = 16;
    public static final int COOLDOWN = 20 * 18;
    public static final float DAMAGE = 11.0F;
    public static final double RADIUS = 8.0, BAND = 1.4, HEIGHT = 3.0, PUSH = 1.3, UP = 0.5;
    public static final int FX_LIFE = 46;

    private final int level;

    public FishmanTideAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "fishman_devastating_wave";
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
        return !Fishman.refuseBusy(player);
    }

    /** Rayon du front de la première vague, {@code k} ticks après la frappe (commun au rendu). */
    public static double front(float k) {
        float x = Math.max(0.0F, Math.min(1.0F, k / SPREAD));
        return 0.6 + (RADIUS - 0.6) * (1.0 - (1.0 - x) * (1.0 - x));
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Fishman.setAction(player, Fishman.TIDE);
        Set<LivingEntity> hit = new HashSet<>();
        Goro.start(new Goro.Session() {
            int t;
            Vec3 center;

            @Override
            public boolean tick() {
                if (!Fishman.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                if (t <= SLAM) {
                    Human.halt(player);
                }
                if (t == SLAM) {
                    double gy = Human.ground(level, player.getX(), player.getZ(), player.getY());
                    center = new Vec3(player.getX(), gy, player.getZ());
                    FishmanFx.spawn(level, FishmanFx.TIDE, center, Human.flatLook(player), (float) RADIUS, SPREAD, FX_LIFE);
                    level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
                    level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.6F, 0.6F);
                    level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.2F, 0.7F);
                    Human.groundBurst(level, center, 18, 0.6, 0.12);
                    Fishman.splash(level, center.add(0.0, 0.4, 0.0), 30, 0.8);
                    BuddhaHits.shake(level, center, 16.0, 0.8F, 10);
                }
                if (center != null && t > SLAM && t <= SLAM + SPREAD + 1) {
                    double r = front(t - SLAM);
                    for (LivingEntity e : Goro.targets(level, player, new AABB(center, center).inflate(r + 1.0, HEIGHT, r + 1.0))) {
                        double dx = e.getX() - center.x, dz = e.getZ() - center.z;
                        double d = Math.sqrt(dx * dx + dz * dz);
                        if (hit.contains(e) || d > r + e.getBbWidth() * 0.5 || e.getY() > center.y + HEIGHT || e.getY() + e.getBbHeight() < center.y - 1.0) {
                            continue;
                        }
                        Vec3 out = d < 1.0E-3 ? Human.flatLook(player) : new Vec3(dx / d, 0.0, dz / d);
                        if (Human.strike(level, player, e, DAMAGE, out.scale(PUSH).add(0.0, UP, 0.0), hit)) {
                            Human.knockTrail(level, e, 8);
                            Fishman.splash(level, e.getBoundingBox().getCenter(), 10, 0.4);
                        }
                    }
                    if ((t - SLAM) % 3 == 0) { // le grondement de l'eau qui déferle
                        Fishman.splash(level, center.add(0.0, 0.3, 0.0), 16, r * 0.6);
                        level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.8F, 0.7F);
                    }
                }
                if (t >= DURATION) {
                    Fishman.clearAction(player, Fishman.TIDE);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Fishman.clearAction(player, Fishman.TIDE);
            }
        });
    }
}
