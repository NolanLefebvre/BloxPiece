package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.MinksFx;

/**
 * Race Minks, niveau 20 : BARRAGE DE GRIFFES ÉLECTRIQUES. Les griffes sortent, garde de combat ({@link #GUARD} ticks), puis une
 * rafale de coups de poing alternés (un tous les {@link #INTERVAL} ticks, {@link #PUNCHES} coups ; Sulong :
 * {@link #PUNCHES_SULONG}), et un dernier coup plus puissant. Chaque coup touche ce qui est devant le joueur
 * ({@link #RANGE} blocs) ; une même cible prend au plus {@link #MAX_HITS} coups, et la paralysie électrique reste limitée
 * (Minks.tryStun). Dégâts répartis sur les coups ; Sulong : x Minks.SULONG_POWER.
 */
public class MinksBarrageAbility implements Ability {

    public static final int GUARD = 5, INTERVAL = 2, PUNCHES = 14, PUNCHES_SULONG = 18, FINAL_WINDUP = 3, RECOVER = 9;
    public static final int COOLDOWN = 20 * 14;
    public static final float DAMAGE = 2.5F, FINAL_DAMAGE = 9.0F;
    public static final double RANGE = 3.2, HALF_WIDTH = 1.3;
    public static final int MAX_HITS = 16, STUN = 6;

    private final int level;

    public MinksBarrageAbility(int level) {
        this.level = level;
    }

    /** Instant (ticks) du coup final, et fin de l'attaque, pour {@code punches} coups. Partagé avec l'animation. */
    public static int finalTick(int punches) {
        return GUARD + punches * INTERVAL + FINAL_WINDUP;
    }

    public static int duration(int punches) {
        return finalTick(punches) + RECOVER;
    }

    @Override
    public String id() {
        return "minks_claw_barrage";
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
        return !Minks.refuseBusy(player);
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        boolean sulong = Minks.sulong(player);
        int punches = sulong ? PUNCHES_SULONG : PUNCHES;
        Minks.setAction(player, sulong ? Minks.BARRAGE_S : Minks.BARRAGE);
        Minks.showClaws(player, duration(punches) + 10);
        Map<LivingEntity, Integer> hits = new HashMap<>();
        Goro.start(new Goro.Session() {
            int t;

            @Override
            public boolean tick() {
                if (!Minks.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                float p = Minks.power(player);
                int k = t - GUARD;
                if (k > 0 && k <= punches * INTERVAL && k % INTERVAL == 0) {
                    punch(level, player, hits, (k / INTERVAL) % 2 == 0, DAMAGE * p, false);
                }
                if (t == finalTick(punches)) {
                    punch(level, player, hits, false, FINAL_DAMAGE * p, true);
                }
                if (t >= duration(punches)) {
                    Minks.clearAction(player);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Minks.clearAction(player);
            }
        });
    }

    /** Un coup : tout ce qui est dans la zone devant le joueur (au plus MAX_HITS coups par cible), impact devant le poing. */
    private static void punch(ServerLevel level, ServerPlayer player, Map<LivingEntity, Integer> hits, boolean left, float damage,
                              boolean last) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 eye = player.getEyePosition();
        // point d'impact CENTRÉ devant le joueur (sur l'axe du regard, sans décalage gauche / droite selon la main)
        Vec3 fist = eye.add(0.0, -0.35, 0.0).add(look.scale(1.4));
        Vec3 far = eye.add(look.scale(RANGE));
        boolean touched = false;
        for (LivingEntity e : Goro.targets(level, player, new AABB(eye, far).inflate(HALF_WIDTH))) {
            Vec3 c = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
            Vec3 rel = c.subtract(eye);
            double along = rel.dot(look);
            if (along < 0.0 || along > RANGE + e.getBbWidth() * 0.5 || rel.subtract(look.scale(along)).length() > HALF_WIDTH + e.getBbWidth() * 0.5) {
                continue;
            }
            int n = hits.getOrDefault(e, 0);
            if (n >= MAX_HITS && !last) {
                continue;
            }
            hits.put(e, n + 1);
            Minks.claw(level, player, e, damage, flat.scale(last ? 1.1 : 0.05).add(0.0, last ? 0.35 : 0.0, 0.0),
                    (int) ((last ? STUN * 2 : STUN) * Minks.power(player)));
            touched = true;
            fist = c.subtract(flat.scale(e.getBbWidth() * 0.5));
        }
        Minks.slash(level, player, last ? MinksFx.FINAL : MinksFx.BARRAGE_HIT, fist, look, 1.4F * Minks.power(player), // tous les coups : même taille que le dernier
                last ? 22 : 8);
        if (last) {
            level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
            level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.7F);
            BuddhaHits.shake(level, fist, 10.0, 0.5F, 6);
        } else {
            level.playSound(null, fist.x, fist.y, fist.z, touched ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
                    SoundSource.PLAYERS, 0.5F, 1.4F + level.getRandom().nextFloat() * 0.3F);
            if (touched) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, fist.x, fist.y, fist.z, 4, 0.1, 0.1, 0.1, 0.15);
            }
        }
    }
}
