package nolan.bloxpiece.ability;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.LunarianFx;

/**
 * Race Lunarian, niveau 30 : POING EMBRASÉ. Le joueur déploie ses ailes et arme son bras ; les flammes enveloppent son poing
 * ({@link #WINDUP} ticks, posture offensive, poing en avant). Puis DASH très rapide dans la direction du regard fixée au
 * lancement (au sol comme en l'air, sans gravité pendant le dash) : {@link #SPEED} blocs par tick pendant au plus
 * {@link #DASH} ticks, traînée de feu derrière lui. Il s'arrête contre un bloc (aucun passage à travers) ou au contact de la
 * PREMIÈRE créature : explosion de feu au poing (dégâts {@link #DAMAGE} à la cible, {@link #SPLASH} autour, une seule fois
 * chacune), recul, mise à feu, onde de choc. Récupération ensuite ({@link #RECOVER}). Dégâts réduits en mode Défense.
 */
public class LunarianFistAbility implements Ability {

    public static final int WINDUP = 10, DASH = 10, RECOVER = 14;
    public static final int COOLDOWN = 20 * 12;
    public static final float DAMAGE = 11.0F, SPLASH = 5.0F;
    public static final double SPEED = 1.6, REACH = 0.9, SPLASH_RADIUS = 2.8, PUSH = 1.4, UP = 0.4;

    private final int level;

    public LunarianFistAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "lunarian_blazing_fist";
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

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Lunarian.setAction(player, Lunarian.FIST);
        Set<LivingEntity> hit = new HashSet<>();
        Goro.start(new Goro.Session() {
            int t, end = -1;
            Vec3 dir;

            @Override
            public boolean tick() {
                if (!Lunarian.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                player.fallDistance = 0.0;
                if (end >= 0) { // récupération
                    if (t - end >= RECOVER) {
                        Lunarian.clearAction(player, Lunarian.FIST_END);
                        return false;
                    }
                    return true;
                }
                if (t < WINDUP) {
                    Human.halt(player);
                    if (t % 3 == 0) {
                        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.2, player.getZ(), 3, 0.2, 0.2, 0.2, 0.03);
                    }
                    if (t == 2) {
                        level.playSound(null, player.getX(), player.getY() + 1.2, player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9F, 0.8F);
                    }
                    return true;
                }
                if (t == WINDUP) { // départ : battement d'ailes
                    dir = player.getLookAngle();
                    player.setNoGravity(true);
                    level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 1.4F);
                    level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9F, 0.8F);
                }
                int k = t - WINDUP;
                Vec3 c = player.getBoundingBox().getCenter();
                // obstacle : la boîte du joueur doit passer. Contre le sol ou un plafond (visée vers le bas ou le haut), il GLISSE
                // à l'horizontale (et monte une marche) au lieu de s'arrêter ; il ne s'arrête que contre un vrai mur.
                Vec3 move = slide(level, player, dir.scale(SPEED));
                boolean blocked = move == null;
                // contact : la première créature devant le poing sur ce tick
                LivingEntity first = null;
                double best = Double.MAX_VALUE;
                Vec3 a = c, b = c.add(dir.scale(SPEED + REACH));
                for (LivingEntity e : Goro.targets(level, player, new AABB(a, b).inflate(1.2))) {
                    var clip = e.getBoundingBox().inflate(0.4).clip(a, b);
                    double d = clip.isPresent() ? clip.get().distanceToSqr(a) : e.getBoundingBox().inflate(0.4).contains(a) ? 0.0 : -1.0;
                    if (d >= 0.0 && d < best) {
                        best = d;
                        first = e;
                    }
                }
                LunarianFx.spawn(level, LunarianFx.TRAIL, c, dir, 1.0F, 0, 12);
                if (first != null) {
                    Vec3 at = a.add(dir.scale(Math.sqrt(best)));
                    explode(level, player, first, at, dir, hit);
                    stop();
                    return true;
                }
                if (blocked || k >= DASH) {
                    stop();
                    return true;
                }
                player.setDeltaMovement(move);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                return true;
            }

            /** Fin du dash : arrêt net, gravité rendue, récupération. */
            private void stop() {
                player.setNoGravity(false);
                player.setDeltaMovement(Vec3.ZERO);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                end = t;
                Lunarian.setAction(player, Lunarian.FIST_END);
            }

            @Override
            public void cancel() {
                player.setNoGravity(false);
                Lunarian.clearAction(player, Lunarian.FIST);
                Lunarian.clearAction(player, Lunarian.FIST_END);
            }
        });
    }

    /**
     * Le déplacement de ce tick : dans l'axe visé s'il est libre ; sinon sa part horizontale (glisse sur le sol ou sous le
     * plafond), au besoin en montant une marche ; null si un mur l'arrête.
     */
    private static Vec3 slide(ServerLevel level, ServerPlayer player, Vec3 full) {
        AABB box = player.getBoundingBox();
        if (level.noCollision(player, box.move(full))) {
            return full;
        }
        Vec3 flat = new Vec3(full.x, 0.0, full.z);
        if (flat.lengthSqr() < 0.04) {
            return null; // visée presque verticale contre le sol ou le plafond
        }
        if (level.noCollision(player, box.move(flat))) {
            return flat;
        }
        Vec3 step = flat.add(0.0, 0.6, 0.0);
        return level.noCollision(player, box.move(step)) ? step : null;
    }

    /** L'explosion du poing : la cible, puis les créatures proches (une seule fois chacune). */
    private static void explode(ServerLevel level, ServerPlayer player, LivingEntity target, Vec3 at, Vec3 dir, Set<LivingEntity> hit) {
        Vec3 push = new Vec3(dir.x, 0.0, dir.z);
        push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize();
        if (Human.strike(level, player, target, DAMAGE, push.scale(PUSH).add(0.0, UP, 0.0), hit)) {
            target.igniteForTicks(80);
            Human.knockTrail(level, target, 8);
        }
        for (LivingEntity e : Goro.targets(level, player, new AABB(at, at).inflate(SPLASH_RADIUS))) {
            if (e.getBoundingBox().getCenter().distanceTo(at) > SPLASH_RADIUS) {
                continue;
            }
            Vec3 out = e.position().subtract(at);
            out = new Vec3(out.x, 0.0, out.z);
            out = out.lengthSqr() < 1.0E-4 ? push : out.normalize();
            if (Human.strike(level, player, e, SPLASH, out.scale(0.8).add(0.0, 0.3, 0.0), hit)) {
                e.igniteForTicks(40);
            }
        }
        LunarianFx.spawn(level, LunarianFx.FIST_BOOM, at, dir, 1.0F, 0, 24);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.3F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2F, 0.7F);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 24, 0.4, 0.4, 0.4, 0.12);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.1);
        BuddhaHits.shake(level, at, 10.0, 0.45F, 6);
    }
}
