package nolan.bloxpiece.ability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.LunarianFx;

/**
 * Race Lunarian, niveau 20 : PLUMES INCANDESCENTES. Le joueur déploie ses ailes ({@link #DEPLOY}), elles s'arment vers
 * l'arrière ; {@link #COUNT} plumes de feu se forment autour des ailes (client : LunarianLayer, jusqu'à {@link #FORMED}), puis
 * sont projetées une à une, en alternant aile droite et aile gauche, à partir de {@link #FIRST} et toutes les {@link #EVERY}
 * ticks. Chaque plume vise une créature devant le joueur (les cibles sont réparties entre les plumes ; sans cible, éventail
 * dans l'axe du regard). Chaque plume a SA collision : elle s'arrête sur la première créature touchée (dégâts modérés, mise
 * à feu, petite explosion de feu) ou contre le premier bloc. Dégâts réduits en mode Défense (Lunarian).
 */
public class LunarianFeathersAbility implements Ability {

    public static final int DEPLOY = 10, FORMED = 20, FIRST = 22, EVERY = 2, COUNT = 12, DURATION = 56;
    public static final int COOLDOWN = 20 * 10;
    /** Dégâts par plume : le total possible reste celui de l'ancienne version (8 plumes x 4 = 32), réparti sur les {@link #COUNT} plumes. */
    public static final float DAMAGE = 32.0F / COUNT;
    public static final double SPEED = 1.5, RANGE = 26.0, RADIUS = 0.45, AIM_CONE = 0.8;

    private final int level;

    public LunarianFeathersAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "lunarian_burning_feathers";
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
        return !Lunarian.refuseBusy(player);
    }

    /**
     * Point de départ de la plume {@code i} autour des ailes (commun au rendu : LunarianLayer) : côté (1 = droite, -1 = gauche),
     * rang j (0 près du dos) ; {@code {côté (blocs, vers la droite), hauteur au-dessus des pieds, recul derrière le dos}}.
     */
    public static double[] slot(int i) {
        // six places par aile, en ARC autour de l'aile (du haut vers l'extérieur), alternance droite / gauche : chaque plume reste visible
        int side = i % 2 == 0 ? 1 : -1, j = i / 2;
        double a = Math.toRadians(15.0 + j * 21.0);
        return new double[] {side * (0.45 + 1.05 * Math.sin(a)), 1.25 + 0.95 * Math.cos(a), 0.4 + 0.05 * j};
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Lunarian.setAction(player, Lunarian.FEATHERS);
        Goro.start(new Goro.Session() {
            int t;
            List<LivingEntity> targets;

            @Override
            public boolean tick() {
                if (!Lunarian.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                if (t == DEPLOY) { // les ailes s'ouvrent d'un coup
                    level.playSound(null, player.getX(), player.getY() + 1.4, player.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8F, 1.3F);
                }
                if (t > DEPLOY && t < FIRST && t % 3 == 0) { // les plumes s'embrasent une à une
                    level.playSound(null, player.getX(), player.getY() + 1.4, player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.4F,
                            1.2F + t * 0.03F);
                }
                int k = t - FIRST;
                if (k >= 0 && k % EVERY == 0 && k / EVERY < COUNT) {
                    if (targets == null) {
                        targets = targets(level, player);
                    }
                    int i = k / EVERY;
                    shoot(level, player, i, targets.isEmpty() ? null : targets.get(i % targets.size()));
                }
                if (t >= DURATION) {
                    Lunarian.clearAction(player, Lunarian.FEATHERS);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Lunarian.clearAction(player, Lunarian.FEATHERS);
            }
        });
    }

    /** Créatures devant le joueur (dans un cône autour du regard, en vue directe), de la plus proche à la plus lointaine. */
    private static List<LivingEntity> targets(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity e : Goro.targets(level, player, new AABB(eye, eye).inflate(RANGE))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d < 0.5 || d > RANGE || to.normalize().dot(look) < AIM_CONE || !player.hasLineOfSight(e)) {
                continue;
            }
            list.add(e);
        }
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        return list;
    }

    private static void shoot(ServerLevel level, ServerPlayer player, int i, LivingEntity target) {
        double[] s = slot(i);
        Vec3 right = Human.right(player), back = Human.flatLook(player).scale(-1.0);
        Vec3 from = player.position().add(right.scale(s[0])).add(0.0, s[1], 0.0).add(back.scale(s[2]));
        Vec3 aim;
        if (target != null) {
            aim = target.getBoundingBox().getCenter().subtract(from).normalize();
        } else { // éventail dans l'axe du regard
            double spread = (i / 2 - 2.5) * 0.05 * (i % 2 == 0 ? 1 : -1);
            aim = player.getLookAngle().add(right.scale(spread)).normalize();
        }
        // léger écart propre à chaque plume (jamais deux trajectoires identiques, même sur une même cible)
        Vec3 up = right.cross(aim).normalize();
        Vec3 dir = aim.add(right.scale(Math.sin(i * 2.4) * 0.025)).add(up.scale(Math.cos(i * 1.7) * 0.02)).normalize();
        Vec3 end = Fishman.clip(level, player, from, from.add(dir.scale(RANGE)));
        double dist = end.distanceTo(from);
        int travel = Math.max(1, (int) Math.ceil(dist / SPEED));
        LunarianFx fx = LunarianFx.spawn(level, LunarianFx.FEATHER, from, end.subtract(from), 1.0F, travel, travel + 4);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5F, 1.4F + 0.05F * i);
        Set<LivingEntity> hit = new HashSet<>();
        Goro.start(new Goro.Session() {
            int k;

            @Override
            public boolean tick() {
                if (player.isRemoved() || player.level() != level) {
                    return false;
                }
                double a = Math.min(dist, k * SPEED), b = Math.min(dist, (k + 1) * SPEED);
                Vec3 pa = from.add(dir.scale(a)), pb = from.add(dir.scale(b));
                k++;
                // la première créature sur le trajet de ce tick
                LivingEntity first = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity e : Goro.targets(level, player, new AABB(pa, pb).inflate(RADIUS + 1.0))) {
                    var clip = e.getBoundingBox().inflate(RADIUS).clip(pa, pb);
                    double d = clip.isPresent() ? clip.get().distanceToSqr(pa) : e.getBoundingBox().inflate(RADIUS).contains(pa) ? 0.0 : -1.0;
                    if (d >= 0.0 && d < best) {
                        best = d;
                        first = e;
                    }
                }
                if (first != null) {
                    Vec3 at = pa.add(dir.scale(Math.sqrt(best)));
                    fx.stopAt(at.subtract(from), k, k + 2);
                    if (Human.strike(level, player, first, DAMAGE, dir.scale(0.35).add(0.0, 0.15, 0.0), hit)) {
                        first.igniteForTicks(60);
                    }
                    LunarianFx.spawn(level, LunarianFx.BURST, at, dir, 1.0F, 0, 14);
                    level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.06);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F, 1.6F);
                    return false;
                }
                if (k * SPEED >= dist) {
                    if (dist < RANGE - 0.1) { // contre un bloc
                        LunarianFx.spawn(level, LunarianFx.BURST, end, dir, 0.7F, 0, 12);
                    }
                    return false;
                }
                return true;
            }
        });
    }
}
