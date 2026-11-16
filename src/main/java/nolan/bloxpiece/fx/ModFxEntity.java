package nolan.bloxpiece.fx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

/**
 * Base des entités d'attaque du fruit de la Glace (trident lancé, aigle, pics, statues) : comme BuddhaPalm et BuddhaSeal, une
 * entité Projectile (sauvegarde et suivi réseau déjà gérés par Minecraft) qui ne subit rien : ni dégâts, ni explosions, ni
 * poussée, ni portails, jamais visée. Les collisions et les dégâts sont faits à la main par chaque attaque. Une entité qui n'a pas
 * été créée par une attaque (rechargée d'une sauvegarde) disparaît aussitôt.
 */
public abstract class ModFxEntity extends Projectile {

    /** Vrai seulement pour une entité créée par une attaque. */
    protected boolean managed;

    protected ModFxEntity(EntityType<? extends ModFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.managed && this.level() instanceof ServerLevel) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160.0 * 160.0;
    }
}
