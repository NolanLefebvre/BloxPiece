package nolan.bloxpiece.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.CyborgCannonball;

/**
 * Race Cyborg, niveau 10 : CANNON ARM. Le bras mécanique gauche se lève, ses plaques s'ouvrent et se réorganisent en canon
 * (animation : CyborgArmLayer), un gros boulet part dans l'axe du regard (CyborgCannonball), puis le canon se replie.
 * Déroulement (ticks) : lever 0-{@link #RAISE}, transformation jusqu'à {@link #ARMED}, tir à {@link #FIRE}, repli à partir de
 * {@link #RETRACT}, fin à {@link #DURATION}. Impact : explosion de rayon {@link #RADIUS}, petit cratère (règle mobGriefing, village
 * protégé : GoroTerrain), fumée, poussière, débris.
 */
public class CyborgCannonArmAbility implements Ability {

    public static final int RAISE = 8, ARMED = 22, FIRE = 25, RETRACT = 34, DURATION = 52;
    public static final int COOLDOWN = 20 * 10;
    public static final float DAMAGE = 18.0F;
    public static final double RADIUS = 3.5, SPEED = 2.2;

    private final int level;

    public CyborgCannonArmAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "cyborg_cannon_arm";
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
        return !Cyborg.refuseBusy(player);
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Cyborg.setAction(player, Cyborg.CANNON);
        Goro.start(new Goro.Session() {
            int t;

            @Override
            public boolean tick() {
                if (!Cyborg.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                Vec3 m = Cyborg.leftMuzzle(player, 0.9);
                if (t == RAISE || t == RAISE + 6) { // plaques qui s'ouvrent, pièces qui se verrouillent
                    level.playSound(null, m.x, m.y, m.z, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.8F, 1.4F);
                }
                if (t == ARMED - 2) {
                    level.playSound(null, m.x, m.y, m.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.9F, 0.7F);
                }
                if (t == FIRE) {
                    fire(level, player);
                }
                if (t == RETRACT) {
                    level.playSound(null, m.x, m.y, m.z, SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.8F, 1.2F);
                }
                if (t > FIRE && t < RETRACT && t % 2 == 0) { // fumée qui sort de la bouche du canon
                    Vec3 b = Cyborg.leftMuzzle(player, 1.5);
                    level.sendParticles(ParticleTypes.SMOKE, b.x, b.y, b.z, 2, 0.05, 0.05, 0.05, 0.01);
                }
                if (t >= DURATION) {
                    Cyborg.clearAction(player, Cyborg.CANNON);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Cyborg.clearAction(player, Cyborg.CANNON);
            }
        });
    }

    private static void fire(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 muzzle = Cyborg.leftMuzzle(player, 1.5);
        CyborgCannonball.spawn(level, player, muzzle, look.scale(SPEED));
        // départ du coup : flash, souffle, fumée ; recul du tireur
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 12, 0.15, 0.15, 0.15, 0.08);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 14, 0.2, 0.2, 0.2, 0.06);
        level.sendParticles(ParticleTypes.POOF, muzzle.x, muzzle.y, muzzle.z, 10, 0.1, 0.1, 0.1, 0.15);
        level.sendParticles(ParticleTypes.EXPLOSION, muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4F, 1.5F);
        player.push(-look.x * 0.35, 0.05, -look.z * 0.35);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
    }

    /** Une créature que le boulet de {@code owner} peut toucher (pas lui, pas sa Party, pas un joueur en créatif). */
    public static boolean canHit(LivingEntity entity, ServerPlayer owner) {
        return BuddhaHits.canHit(entity, owner);
    }

    /** Explosion du boulet (appelée par CyborgCannonball) : dégâts dégressifs, souffle, cratère, fumée, poussière, débris. */
    public static void explode(ServerLevel level, ServerPlayer owner, Vec3 at) {
        if (owner != null) {
            for (LivingEntity target : Goro.targets(level, owner, new AABB(at, at).inflate(RADIUS))) {
                double d = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).distanceTo(at);
                if (d > RADIUS + target.getBbWidth() * 0.5) {
                    continue;
                }
                float damage = (float) (DAMAGE * (1.0 - 0.5 * Math.min(1.0, d / RADIUS)));
                Vec3 dir = target.position().subtract(at);
                Vec3 push = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.4, 0.0) : dir.normalize().scale(0.9).add(0.0, 0.35, 0.0);
                BuddhaHits.strike(level, owner, target, damage, push);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 40, 1.2, 0.8, 1.2, 0.05);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.3, at.z, 10, 1.0, 0.4, 1.0, 0.02);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 20, 0.8, 0.5, 0.8, 0.08);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.2F, 0.85F);
        BuddhaHits.shake(level, at, 14.0, 0.8F, 8);
        // petit cratère, fissures et débris (mêmes règles que les autres attaques : mobGriefing, village protégé...)
        GoroTerrain.scar(level, at, 2.0, 1.3, 4.5, 5, 8);
    }
}
