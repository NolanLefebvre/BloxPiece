package nolan.bloxpiece.fx;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.mob.ArmedPirate;
import nolan.bloxpiece.registry.ModEntities;

/**
 * Balle de pistolet à silex (modèle 3D : bille de plomb), tirée par le Pirate Tireur ou par un joueur avec le pistolet.
 * Vol rapide avec une très légère chute, traînée de fumée ; la première entité touchée (ou le premier bloc) l'arrête. Une balle de
 * pirate ne touche jamais un autre pirate armé. Collisions faites à la main (pas à pas), position synchronisée chaque tick.
 */
public class PirateBullet extends ModFxEntity {

    private static final double STEP = 0.5;
    private static final double GRAVITY = 0.012;
    private static final int MAX_AGE = 50;

    private Vec3 velocity = Vec3.ZERO;
    private float damage;
    private int age;

    public PirateBullet(EntityType<? extends PirateBullet> type, Level level) {
        super(type, level);
    }

    /** Tire une balle : flash et fumée au canon, détonation. */
    public static PirateBullet fire(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 direction, double speed, float damage) {
        PirateBullet bullet = new PirateBullet(ModEntities.PIRATE_BULLET, level);
        bullet.managed = true;
        bullet.setOwner(shooter);
        bullet.damage = damage;
        bullet.velocity = direction.normalize().scale(speed);
        bullet.setDeltaMovement(bullet.velocity);
        bullet.setPos(from.x, from.y, from.z);
        level.addFreshEntity(bullet);
        Vec3 muzzle = from.add(direction.normalize().scale(0.4));
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 3, 0.05, 0.05, 0.05, 0.02);
        level.sendParticles(ParticleTypes.POOF, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.1, 0.1, 0.1, 0.01);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 2.0F, 0.55F);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.HOSTILE, 1.0F, 1.6F);
        return bullet;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }
        LivingEntity owner = this.getOwner() instanceof LivingEntity l ? l : null;
        this.velocity = this.velocity.add(0.0, -GRAVITY, 0.0);
        this.setDeltaMovement(this.velocity);
        Vec3 start = this.position();
        Vec3 end = start.add(this.velocity);

        BlockHitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double reach = block.getType() == HitResult.Type.MISS ? this.velocity.length() : block.getLocation().distanceTo(start);
        Vec3 dir = this.velocity.normalize();
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(1.0),
                e -> e != owner && e.isAlive() && !e.isSpectator() && !(owner instanceof ArmedPirate && e instanceof ArmedPirate));
        for (double d = 0.0; d <= reach; d += STEP) {
            Vec3 p = start.add(dir.scale(d));
            for (LivingEntity target : candidates) {
                if (target.getBoundingBox().inflate(0.15).contains(p)) {
                    DamageSource source = level.damageSources().mobProjectile(this, owner);
                    if (owner != null) {
                        target.setLastHurtByMob(owner);
                    }
                    target.hurtServer(level, source, this.damage);
                    this.impact(level, p);
                    return;
                }
            }
        }
        if (block.getType() != HitResult.Type.MISS || ++this.age >= MAX_AGE) {
            this.impact(level, block.getType() != HitResult.Type.MISS ? block.getLocation() : end);
            return;
        }
        level.sendParticles(ParticleTypes.SMOKE, start.x, start.y, start.z, 2, 0.02, 0.02, 0.02, 0.0);
        this.setYRot((float) Math.toDegrees(Mth.atan2(-dir.x, dir.z)));
        this.setPos(end);
    }

    private void impact(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.2);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ARROW_HIT, SoundSource.HOSTILE, 0.8F, 1.4F);
        this.discard();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // position et vitesse : envoyées par Minecraft
    }
}
