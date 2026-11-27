package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.FishmanFx;

/**
 * Race Fishman, niveau 20 : LANCE AQUATIQUE. Le joueur avance les mains devant lui ; de nombreuses gouttes d'eau apparaissent,
 * se rassemblent et tournent autour de ses mains (client : FishmanLayer, de 0 à {@link #GATHER}). D'un mouvement brusque des
 * bras, il projette TROIS SALVES successives ({@link #THROWS}) de {@link #DROPS} gouttes à très haute vitesse dans un cône serré
 * devant lui ; entre deux salves, les bras se réarment (même mouvement : FishmanPose). Chaque goutte est un projectile (hitbox
 * {@link #RADIUS}) qui traverse les créatures sur sa trajectoire et éclate contre le premier bloc. Dégâts répartis :
 * {@link #DAMAGE} par goutte, au plus {@link #MAX_PER_SALVO} gouttes d'une même salve par cible (le total possible reste
 * proche de l'ancienne projection unique).
 */
public class FishmanSpearAbility implements Ability {

    /** Rassemblement des gouttes jusqu'à GATHER ; les trois salves (ticks) ; THROW : la première. */
    public static final int GATHER = 22, THROW = 25, DURATION = 52;
    public static final int[] THROWS = {25, 33, 41};
    public static final int COOLDOWN = 20 * 12;
    /** Gouttes PAR SALVE (en deux jets rapprochés) et gouttes d'une même salve qui peuvent toucher une même cible. */
    public static final int DROPS = 10, MAX_PER_SALVO = 2;
    public static final float DAMAGE = 1.7F;
    public static final double SPEED = 2.4, RANGE = 24.0, RADIUS = 0.4, SPREAD = 0.075, PUSH = 0.12, UP = 0.05;

    private final int level;

    public FishmanSpearAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "fishman_water_spear";
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
        Fishman.setAction(player, Fishman.SPEAR);
        Random random = new Random(player.getId() * 31L + level.getGameTime());
        Goro.start(new Goro.Session() {
            int t;
            @SuppressWarnings("unchecked")
            final Map<LivingEntity, Integer>[] counts = new Map[THROWS.length];

            @Override
            public boolean tick() {
                if (!Fishman.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                if (t <= THROWS[THROWS.length - 1] + 1) {
                    Human.halt(player); // ancré jusqu'à la dernière salve
                }
                if (t < THROW) {
                    if (t % 5 == 1) { // l'eau se rassemble en clapotant
                        Vec3 c = Fishman.hand(player, 0, 0.7);
                        level.playSound(null, c.x, c.y, c.z, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.PLAYERS, 0.6F,
                                1.2F + t * 0.03F);
                    }
                }
                // les trois salves, chacune en deux jets très rapprochés ; chaque salve a son propre plafond par cible
                int salvo = salvo(t);
                if (salvo >= 0) {
                    if (counts[salvo] == null) {
                        counts[salvo] = new HashMap<>();
                    }
                    Vec3 c = Fishman.hand(player, 0, 0.8);
                    level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.5F);
                    for (int i = 0; i < DROPS / 2; i++) {
                        shoot(level, player, random, counts[salvo]);
                    }
                }
                if (t >= DURATION) {
                    Fishman.clearAction(player, Fishman.SPEAR);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Fishman.clearAction(player, Fishman.SPEAR);
            }
        });
    }

    /** Indice de la salve lancée à ce tick (jets à THROWS[i] et THROWS[i] + 1), ou -1. */
    private static int salvo(int t) {
        for (int i = 0; i < THROWS.length; i++) {
            if (t == THROWS[i] || t == THROWS[i] + 1) {
                return i;
            }
        }
        return -1;
    }

    /** Une goutte : part d'un point autour des mains, dans un cône serré autour du regard, et vole jusqu'au premier bloc. */
    private static void shoot(ServerLevel level, ServerPlayer player, Random random, Map<LivingEntity, Integer> count) {
        Vec3 look = player.getLookAngle();
        Vec3 right = Human.right(player), up = right.cross(look).normalize();
        double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble());
        Vec3 dir = look.add(right.scale(Math.cos(a) * r * SPREAD)).add(up.scale(Math.sin(a) * r * SPREAD)).normalize();
        Vec3 from = Fishman.hand(player, 0, 0.8).add(right.scale((random.nextDouble() - 0.5) * 0.5)).add(up.scale((random.nextDouble() - 0.5) * 0.35));
        Vec3 end = Fishman.clip(level, player, from, from.add(dir.scale(RANGE)));
        double dist = end.distanceTo(from);
        int travel = Math.max(1, (int) Math.ceil(dist / SPEED));
        FishmanFx.spawn(level, FishmanFx.DROP, from, end.subtract(from), 1.0F, travel, travel + 6);
        Set<LivingEntity> hit = new java.util.LinkedHashSet<>(); // ordre d'entrée : les nouvelles cibles sont à la fin
        Goro.start(new Goro.Session() {
            int k;

            @Override
            public boolean tick() {
                if (player.isRemoved() || player.level() != level) {
                    return false;
                }
                double s = Math.min(dist, k * SPEED), e = Math.min(dist, (k + 1) * SPEED);
                Vec3 pa = from.add(dir.scale(s)), pb = from.add(dir.scale(e));
                int before = hit.size();
                if (Fishman.sweep(level, player, pa, pb, RADIUS, DAMAGE, PUSH, UP, hit, count, MAX_PER_SALVO) > 0) {
                    for (LivingEntity target : hit) {
                        if (before-- > 0) {
                            continue;
                        }
                        Vec3 at = target.getBoundingBox().getCenter();
                        FishmanFx.spawn(level, FishmanFx.SPLASH, at, dir, 0.7F, 0, 10);
                        Fishman.splash(level, at, 5, 0.2);
                    }
                    level.playSound(null, pb.x, pb.y, pb.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.4F, 1.6F);
                }
                k++;
                if (k * SPEED >= dist) {
                    if (dist < RANGE - 0.1) { // éclate contre le bloc
                        Fishman.splash(level, end, 4, 0.15);
                    }
                    return false;
                }
                return true;
            }
        });
    }
}
