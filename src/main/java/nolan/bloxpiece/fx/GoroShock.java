package nolan.bloxpiece.fx;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.registry.ModEntities;

/**
 * Électrisation d'une cible par le Mamaragan (rendu 3D : GoroShockRenderer) : la cible est enveloppée d'électricité (éclairs
 * bleus qui parcourent son corps, aura lumineuse, arcs qui crépitent à ses pieds) et un éclair épais la relie en permanence
 * au nuage d'orage au-dessus. Purement visuelle : les dégâts et la paralysie sont appliqués par l'attaque, côté serveur.
 * <ul>
 *   <li>Elle SUIT sa cible : côté serveur, collée à elle à chaque tick ; côté client, le rendu lit directement la position
 *       interpolée de la cible (aucun retard quand elle bouge).</li>
 *   <li>Elle disparaît d'elle-même si la cible meurt, disparaît ou change de dimension, si le nuage disparaît, ou à la fin de
 *       sa durée. Jamais sauvegardée.</li>
 *   <li>{@link #pulse} : la décharge s'intensifie un instant (chaque coup de dégâts périodique).</li>
 * </ul>
 */
public class GoroShock extends ModFxEntity {

    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(GoroShock.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLOUD = SynchedEntityData.defineId(GoroShock.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(GoroShock.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PULSE = SynchedEntityData.defineId(GoroShock.class, EntityDataSerializers.INT);

    private Entity target;
    private Entity cloud;

    public GoroShock(EntityType<? extends GoroShock> type, Level level) {
        super(type, level);
    }

    public static GoroShock spawn(ServerLevel level, Entity target, Entity cloud, int life) {
        GoroShock shock = new GoroShock(ModEntities.GORO_SHOCK, level);
        shock.managed = true;
        shock.target = target;
        shock.cloud = cloud;
        shock.entityData.set(TARGET, target.getId());
        shock.entityData.set(CLOUD, cloud.getId());
        shock.entityData.set(LIFE, life);
        shock.entityData.set(PULSE, -100);
        shock.setPos(target.getX(), target.getY(), target.getZ());
        level.addFreshEntity(shock);
        return shock;
    }

    public int targetId() {
        return this.entityData.get(TARGET);
    }

    public int cloudId() {
        return this.entityData.get(CLOUD);
    }

    public int life() {
        return this.entityData.get(LIFE);
    }

    /** Tick du dernier coup de dégâts (flash). */
    public int pulseTick() {
        return this.entityData.get(PULSE);
    }

    /** La décharge s'intensifie un instant (coup de dégâts). */
    public void pulse() {
        this.entityData.set(PULSE, this.tickCount);
    }

    /** Fin de l'électrisation dans {@code ticks} ticks (le temps de s'éteindre). */
    public void fadeOut(int ticks) {
        this.entityData.set(LIFE, Math.min(this.life(), this.tickCount + ticks));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        if (this.tickCount >= this.life() || this.target == null || !this.target.isAlive() || this.target.isRemoved()
                || this.target.level() != this.level() || this.cloud == null || this.cloud.isRemoved()) {
            this.discard();
            return;
        }
        this.setPos(this.target.getX(), this.target.getY(), this.target.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET, -1);
        builder.define(CLOUD, -1);
        builder.define(LIFE, 100);
        builder.define(PULSE, -100);
    }
}
