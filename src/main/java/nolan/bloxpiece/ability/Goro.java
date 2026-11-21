package nolan.bloxpiece.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fruit.DevilFruit;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.fruit.WaterCurse;
import nolan.bloxpiece.fx.GoroBolt;
import nolan.bloxpiece.fx.GoroWave;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Goro Goro no Mi (fruit de la foudre d'Ener) : ce qui est commun à ses quatre attaques, côté serveur.
 * <ul>
 *   <li>Déroulement dans le temps : chaque attaque en cours est une {@link Session} avancée à chaque tick serveur ; la liste
 *       est vidée à l'arrêt du serveur (les entités visuelles ne sont jamais sauvegardées).</li>
 *   <li>Paralysie ({@link #stun}) : la cible ne peut plus bouger ni attaquer (vitesse annulée, déplacement et cible effacés pour
 *       un mob, lenteur et faiblesse extrêmes pour un joueur), avec des étincelles bleues sur elle.</li>
 *   <li>Coups : ceux du Bouddha (BuddhaHits : jamais le lanceur, ni un joueur en créatif ; dégâts côté serveur, bonus de niveau
 *       compris, recul réduit par la résistance au recul).</li>
 *   <li>Effets : éclairs bleus (GoroBolt), étincelles électriques, éclats du sol, sons de foudre, tremblement de caméra.</li>
 * </ul>
 */
public final class Goro {

    /** Une attaque en cours : renvoie false quand elle est terminée. */
    interface Session {
        boolean tick();

        default void cancel() {
        }
    }

    private static final List<Session> SESSIONS = new ArrayList<>();
    private static final Map<LivingEntity, Long> STUNNED = new HashMap<>();

    private Goro() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(Goro::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    static void start(Session session) {
        SESSIONS.add(session);
    }

    private static void tick(MinecraftServer server) {
        GoroTerrain.tick(); // cratères en cours de creusement
        if (!SESSIONS.isEmpty()) {
            List<Session> copy = new ArrayList<>(SESSIONS); // une attaque peut en lancer une autre pendant son tick
            for (Session s : copy) {
                boolean alive;
                try {
                    alive = s.tick();
                } catch (RuntimeException e) {
                    alive = false; // jamais de plantage du serveur pour un effet
                    s.cancel();
                }
                if (!alive) {
                    SESSIONS.remove(s);
                }
            }
        }
        if (!STUNNED.isEmpty()) {
            Iterator<Map.Entry<LivingEntity, Long>> it = STUNNED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<LivingEntity, Long> e = it.next();
                LivingEntity target = e.getKey();
                if (!target.isAlive() || target.isRemoved() || target.level().getGameTime() >= e.getValue()) {
                    it.remove();
                    continue;
                }
                Vec3 v = target.getDeltaMovement();
                target.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
                if (target instanceof Mob mob) {
                    mob.getNavigation().stop();
                    mob.setTarget(null); // n'attaque plus
                }
                if (target.level() instanceof ServerLevel level && target.tickCount % 3 == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.6,
                            target.getZ(), 4, target.getBbWidth() * 0.5, target.getBbHeight() * 0.4, target.getBbWidth() * 0.5, 0.05);
                }
            }
        }
    }

    private static void clear() {
        for (Session s : SESSIONS) {
            s.cancel();
        }
        SESSIONS.clear();
        STUNNED.clear();
        GoroTerrain.clear();
    }

    /** Lance {@code action} dans {@code delay} ticks (impact au moment où la pointe d'un éclair touche le sol). */
    static void later(int delay, Runnable action) {
        start(new Session() {
            int t;

            @Override
            public boolean tick() {
                if (++this.t >= delay) {
                    action.run();
                    return false;
                }
                return true;
            }
        });
    }

    /** Paralyse {@code target} pendant {@code ticks} ticks (ne bouge plus, n'attaque plus). */
    static void stun(LivingEntity target, int ticks) {
        long until = target.level().getGameTime() + ticks;
        STUNNED.merge(target, until, Math::max);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 9, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 9, false, false));
    }

    /** Le joueur a-t-il toujours le Goro Goro no Mi et cette attaque (pas mort, pas déconnecté, pas dans l'eau...) ? */
    static boolean stillValid(ServerPlayer player, ServerLevel level, Ability ability) {
        if (player.isRemoved() || !player.isAlive() || player.level() != level || WaterCurse.touchesWater(player)) {
            return false;
        }
        DevilFruit fruit = Fruits.get(player.getAttached(ModAttachments.FRUIT));
        if (fruit == null || !ability.isUnlockedFor(player)) {
            return false;
        }
        for (int slot = 0; slot < DevilFruit.MAX_ABILITIES; slot++) {
            if (fruit.getAbility(slot) == ability) {
                return true;
            }
        }
        return false;
    }

    /** Hauteur du sol (premier bloc solide) en (x, z), au plus près de {@code nearY}. */
    static double ground(ServerLevel level, double x, double z, double nearY) {
        BlockPos.MutableBlockPos p = BlockPos.containing(x, nearY + 6, z).mutable();
        for (int i = 0; i < 24; i++, p.move(0, -1, 0)) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.getCollisionShape(level, p).isEmpty()) {
                return p.getY() + 1.0;
            }
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
    }

    /** Toutes les entités que l'attaque de {@code owner} peut toucher dans la boîte. */
    static List<LivingEntity> targets(ServerLevel level, ServerPlayer owner, AABB box) {
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> BuddhaHits.canHit(e, owner));
    }

    /** Coup de foudre : dégâts (une fois), recul vers l'extérieur depuis {@code from}, étincelles sur la cible. */
    static void hit(ServerLevel level, ServerPlayer owner, LivingEntity target, float damage, Vec3 from, double push, double up) {
        Vec3 d = target.position().subtract(from);
        Vec3 h = new Vec3(d.x, 0.0, d.z);
        Vec3 dir = h.lengthSqr() < 1.0E-4 ? Vec3.ZERO : h.normalize();
        BuddhaHits.strike(level, owner, target, damage, new Vec3(dir.x * push, up, dir.z * push));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 18,
                target.getBbWidth() * 0.5, target.getBbHeight() * 0.4, target.getBbWidth() * 0.5, 0.25);
    }

    /** Impact de foudre au sol : étincelles, éclats du sol, petit souffle, son. {@code size} : 1 = petit, 3+ = gigantesque. */
    static void impact(ServerLevel level, Vec3 at, double size) {
        int n = (int) (20 * size);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, n * 2, size * 0.8, 0.3 * size, size * 0.8, 0.6);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, n / 2, size * 0.6, 0.4 * size, size * 0.6, 0.15);
        level.sendParticles(size >= 2.5 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, n / 2, size, 0.1, size, 0.08);
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2, at.z, n, size * 0.7, 0.2, size * 0.7,
                    0.3);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, (float) (1.0 + size), 0.8F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, (float) (0.6 + size * 0.8),
                (float) (1.2 - Math.min(0.6, size * 0.12)));
    }

    /** Éclairs qui fusent au ras du sol, tout autour d'un impact. */
    static void groundArcs(ServerLevel level, Vec3 at, int count, double length, float width) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count + level.getRandom().nextDouble() * 0.4;
            double l = length * (0.6 + 0.4 * level.getRandom().nextDouble());
            Vec3 end = at.add(Math.cos(a) * l, 0.0, Math.sin(a) * l);
            end = new Vec3(end.x, ground(level, end.x, end.z, at.y) + 0.15, end.z);
            GoroBolt.strike(level, at.add(0, 0.2, 0), end, width, 8, 2); // décharges qui courent au ras du sol
        }
    }

    /**
     * Onde de choc au sol : anneau d'énergie et de poussière qui s'élargit jusqu'à {@code radius} (GoroWave, vu par tous), plus un
     * cercle de nuages de poussière projetés vers l'extérieur. {@code dome} : dôme de lumière en plus (grosses explosions).
     */
    static void wave(ServerLevel level, Vec3 at, float radius, int life, boolean dome) {
        GoroWave.spawn(level, at.add(0, 0.15, 0), radius, life, dome);
        int n = Math.min(64, 12 + (int) (radius * 2.0F));
        double speed = 0.25 + radius * 0.035;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            // nombre 0 : (dx, dy, dz) devient la direction de la particule et le dernier paramètre sa vitesse
            level.sendParticles(ParticleTypes.CLOUD, at.x + Math.cos(a), at.y + 0.3, at.z + Math.sin(a), 0, Math.cos(a), 0.04,
                    Math.sin(a), speed);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x + Math.cos(a) * 1.5, at.y + 0.4, at.z + Math.sin(a) * 1.5, 0,
                        Math.cos(a), 0.1, Math.sin(a), speed * 1.6);
            }
        }
    }
}
