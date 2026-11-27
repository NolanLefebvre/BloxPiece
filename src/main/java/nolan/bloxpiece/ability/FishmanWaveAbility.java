package nolan.bloxpiece.ability;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.FishmanFx;

/**
 * Race Fishman, niveau 10 : KARATÉ DES HOMMES-POISSONS — ONDE AQUATIQUE. Le joueur prend la garde du karaté des hommes-poissons
 * (jambes fléchies très écartées, main avant ouverte en lame, poing arrière serré à la hanche), concentre sa force dans le poing,
 * puis le pousse violemment devant lui : au contact de l'air ({@link #STRIKE}), une onde d'eau comprimée part du poing et
 * avance de {@link #RANGE} blocs ({@link #SPEED} blocs par tick), traverse les créatures (chacune touchée une fois : dégâts
 * moyens, recul modéré) et s'arrête au premier mur.
 * Déroulement (ticks) : garde 0-{@link #STANCE}, armé jusqu'à {@link #STRIKE}, frappe, tenue puis retour jusqu'à {@link #DURATION}.
 */
public class FishmanWaveAbility implements Ability {

    public static final int STANCE = 9, STRIKE = 16, DURATION = 30;
    public static final int COOLDOWN = 20 * 8;
    public static final float DAMAGE = 7.0F;
    public static final double RANGE = 10.0, SPEED = 0.9, RADIUS = 1.1, PUSH = 0.9, UP = 0.3;

    private final int level;

    public FishmanWaveAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "fishman_aqua_wave";
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

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Fishman.setAction(player, Fishman.WAVE);
        Goro.start(new Goro.Session() {
            int t;

            @Override
            public boolean tick() {
                if (!Fishman.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                if (t < STRIKE) {
                    Human.halt(player); // la garde est ancrée au sol
                }
                if (t == STANCE) { // le poing se serre : l'eau se rassemble autour
                    Vec3 f = Fishman.hand(player, 1, 0.2);
                    level.playSound(null, f.x, f.y, f.z, SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.5F, 1.6F);
                }
                if (t == STRIKE) {
                    fire(level, player);
                }
                if (t >= DURATION) {
                    Fishman.clearAction(player, Fishman.WAVE);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Fishman.clearAction(player, Fishman.WAVE);
            }
        });
    }

    /** Le coup : l'onde part du poing tendu et avance, touchant ce qu'elle traverse (hitbox = volume de l'onde). */
    private static void fire(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 from = Fishman.hand(player, 1, 0.9);
        Vec3 end = Fishman.clip(level, player, from, from.add(look.scale(RANGE)));
        double dist = end.distanceTo(from);
        int travel = Math.max(1, (int) Math.ceil(dist / SPEED));
        FishmanFx.spawn(level, FishmanFx.WAVE, from, end.subtract(from), (float) RADIUS, travel, travel + 12);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.0F, 0.8F);
        Fishman.splash(level, from, 12, 0.3);
        BuddhaHits.shake(level, from, 6.0, 0.25F, 4);
        Set<LivingEntity> hit = new HashSet<>();
        Goro.start(new Goro.Session() {
            int k;

            @Override
            public boolean tick() {
                if (player.isRemoved() || player.level() != level) {
                    return false;
                }
                double a = Math.min(dist, k * SPEED), b = Math.min(dist, (k + 1) * SPEED);
                Vec3 dir = dist < 1.0E-4 ? look : end.subtract(from).normalize();
                Vec3 pa = from.add(dir.scale(a)), pb = from.add(dir.scale(b));
                if (Fishman.sweep(level, player, pa, pb, RADIUS, DAMAGE, PUSH, UP, hit, null, 1) > 0) {
                    level.playSound(null, pb.x, pb.y, pb.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8F, 1.2F);
                    Fishman.splash(level, pb, 14, 0.5);
                }
                k++;
                if (k * SPEED >= dist) { // au bout (ou contre le mur) : l'onde éclate
                    Fishman.splash(level, end, 20, 0.6);
                    level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.7F, 0.9F);
                    return false;
                }
                return true;
            }
        });
    }
}
