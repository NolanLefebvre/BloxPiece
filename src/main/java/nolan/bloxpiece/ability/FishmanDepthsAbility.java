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
 * Race Fishman, niveau 50 (ultime) : PERFORATION DES PROFONDEURS. Le joueur prend appui et arme son bras droit ; une immense
 * quantité d'eau se concentre autour du bras et du poing et y tourne en spirale de plus en plus imposante pendant
 * {@link #CHARGE} ticks (client : FishmanLayer). Frappe surpuissante ({@link #STRIKE}) : une gigantesque spirale d'eau
 * comprimée part à {@link #SPEED} blocs par tick, jusqu'à {@link #RANGE} blocs. Elle s'arrête sur la PREMIÈRE créature ou le
 * premier bloc touché et EXPLOSE : dégâts très élevés dans {@link #BLAST_RADIUS} blocs (plus forts au centre), fort recul vers
 * l'extérieur, onde de choc. Sans rien toucher, elle explose au bout de sa course.
 */
public class FishmanDepthsAbility implements Ability {

    public static final int CHARGE = 40, STRIKE = 44, DURATION = 62;
    public static final int COOLDOWN = 20 * 60;
    public static final double SPEED = 1.6, RANGE = 34.0, RADIUS = 1.3;
    public static final double BLAST_RADIUS = 5.5, PUSH = 2.0, UP = 0.7;
    public static final float BLAST_DAMAGE = 24.0F, EDGE_DAMAGE = 12.0F;
    public static final int BLAST_LIFE = 44;
    /** Taille VISUELLE de la spirale et de l'explosion (rendu seulement : les hitbox restent {@link #RADIUS} et {@link #BLAST_RADIUS}). */
    public static final float VISUAL_RADIUS = 2.9F, VISUAL_BLAST = 9.5F;

    private final int level;

    public FishmanDepthsAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "fishman_abyss_piercer";
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
        Fishman.setAction(player, Fishman.DEPTHS);
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
                    Human.halt(player);
                    float k = t / (float) CHARGE;
                    if (t % 6 == 0) { // l'eau tourbillonne de plus en plus fort
                        Vec3 f = Fishman.hand(player, 1, 0.3);
                        level.playSound(null, f.x, f.y, f.z, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.PLAYERS, 0.6F + 0.6F * k,
                                0.6F + 0.8F * k);
                        Fishman.splash(level, f, 2 + (int) (6 * k), 0.3 + 0.3 * k);
                    }
                }
                if (t == STRIKE) {
                    fire(level, player);
                }
                if (t >= DURATION) {
                    Fishman.clearAction(player, Fishman.DEPTHS);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Fishman.clearAction(player, Fishman.DEPTHS);
            }
        });
    }

    private static void fire(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 from = Fishman.hand(player, 1, 1.0);
        Vec3 end = Fishman.clip(level, player, from, from.add(look.scale(RANGE)));
        double dist = end.distanceTo(from);
        int travel = Math.max(1, (int) Math.ceil(dist / SPEED));
        FishmanFx spiral = FishmanFx.spawn(level, FishmanFx.SPIRAL, from, end.subtract(from), VISUAL_RADIUS, travel, travel + 14);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 1.4F, 0.7F);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2F, 0.5F);
        Fishman.splash(level, from, 30, 0.6);
        BuddhaHits.shake(level, from, 10.0, 0.6F, 8);
        Goro.start(new Goro.Session() {
            int k;

            @Override
            public boolean tick() {
                if (player.isRemoved() || player.level() != level) {
                    return false;
                }
                double a = Math.min(dist, k * SPEED), b = Math.min(dist, (k + 1) * SPEED);
                Vec3 pa = from.add(look.scale(a)), pb = from.add(look.scale(b));
                if (k % 2 == 0) {
                    Fishman.splash(level, pb, 6, 0.5);
                }
                // première créature sur le trajet de ce tick : la spirale s'y arrête
                LivingEntity first = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity e : Goro.targets(level, player, new AABB(pa, pb).inflate(RADIUS + 1.0))) {
                    var clip = e.getBoundingBox().inflate(RADIUS).clip(pa, pb);
                    if (clip.isPresent() && clip.get().distanceToSqr(pa) < best) {
                        best = clip.get().distanceToSqr(pa);
                        first = e;
                    }
                    if (first == null && e.getBoundingBox().inflate(RADIUS).contains(pa)) {
                        first = e;
                        best = 0.0;
                    }
                }
                k++;
                if (first != null) {
                    Vec3 at = pa.add(look.scale(Math.sqrt(best)));
                    spiral.stopAt(at.subtract(from), k, k + 10);
                    blast(level, player, at, look);
                    return false;
                }
                if (k * SPEED >= dist) {
                    blast(level, player, end, look);
                    return false;
                }
                return true;
            }
        });
    }

    /** L'explosion d'eau : dégâts décroissants avec la distance, fort recul vers l'extérieur, onde de choc. */
    private static void blast(ServerLevel level, ServerPlayer player, Vec3 at, Vec3 look) {
        FishmanFx.spawn(level, FishmanFx.BLAST, at, look, VISUAL_BLAST, 0, BLAST_LIFE);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6F, 0.8F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 2.0F, 0.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
        Fishman.splash(level, at, 60, 1.6);
        BuddhaHits.shake(level, at, 24.0, 1.2F, 14);
        Set<LivingEntity> hit = new HashSet<>();
        for (LivingEntity e : Goro.targets(level, player, new AABB(at, at).inflate(BLAST_RADIUS + 1.0))) {
            Vec3 c = e.getBoundingBox().getCenter();
            double d = c.distanceTo(at);
            if (d > BLAST_RADIUS + e.getBbWidth() * 0.5) {
                continue;
            }
            float k = (float) Math.min(1.0, d / BLAST_RADIUS);
            Vec3 out = c.subtract(at);
            out = out.lengthSqr() < 1.0E-4 ? look : out.normalize();
            if (Human.strike(level, player, e, BLAST_DAMAGE + (EDGE_DAMAGE - BLAST_DAMAGE) * k,
                    out.scale(PUSH * (1.0 - 0.4 * k)).add(0.0, UP, 0.0), hit)) {
                Human.knockTrail(level, e, 12);
            }
        }
    }
}
