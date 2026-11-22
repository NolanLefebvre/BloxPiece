package nolan.bloxpiece.fx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.ability.CyborgCannonArmAbility;
import nolan.bloxpiece.registry.ModEntities;

/**
 * Boulet du Cannon Arm (race Cyborg) : gros boulet de fer (rendu : CyborgCannonballRenderer) tiré dans l'axe du regard. Il
 * avance à chaque tick côté serveur (légère retombée), laisse une traînée de fumée, et explose au premier bloc ou à la première
 * créature touchable sur son trajet (jamais son tireur ni sa Party), ou au bout de {@link #MAX_LIFE} ticks
 * (CyborgCannonArmAbility.explode). Jamais sauvegardé.
 */
public class CyborgCannonball extends ModFxEntity {

    private static final int MAX_LIFE = 60;
    private static final double GRAVITY = 0.025;
    private ServerPlayer shooter;

    public CyborgCannonball(EntityType<? extends CyborgCannonball> type, Level level) {
        super(type, level);
    }

    public static CyborgCannonball spawn(ServerLevel level, ServerPlayer owner, Vec3 at, Vec3 velocity) {
        CyborgCannonball ball = new CyborgCannonball(ModEntities.CYBORG_CANNONBALL, level);
        ball.managed = true;
        ball.shooter = owner;
        ball.setOwner(owner);
        ball.setPos(at.x, at.y, at.z);
        ball.setDeltaMovement(velocity);
        level.addFreshEntity(ball);
        return ball;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }
        Vec3 pos = this.position();
        Vec3 v = this.getDeltaMovement();
        Vec3 next = pos.add(v);
        Vec3 hit = null;
        BlockHitResult block = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS) {
            hit = block.getLocation();
        }
        double best = hit == null ? Double.MAX_VALUE : hit.distanceToSqr(pos);
        if (this.shooter != null) {
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, next).inflate(1.0),
                    e -> CyborgCannonArmAbility.canHit(e, this.shooter))) {
                var clip = target.getBoundingBox().inflate(0.45).clip(pos, next);
                if (clip.isPresent() && clip.get().distanceToSqr(pos) < best) {
                    best = clip.get().distanceToSqr(pos);
                    hit = clip.get();
                }
            }
        }
        if (hit != null || this.tickCount >= MAX_LIFE) {
            CyborgCannonArmAbility.explode(level, this.shooter, hit != null ? hit : pos);
            this.discard();
            return;
        }
        this.setPos(next.x, next.y, next.z);
        this.setDeltaMovement(v.x * 0.995, v.y - GRAVITY, v.z * 0.995);
        level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 3, 0.08, 0.08, 0.08, 0.01);
        if (this.tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }
}
