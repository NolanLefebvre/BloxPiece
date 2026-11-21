package nolan.bloxpiece.fx;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModEntities;

/**
 * Onde de choc électrique du Goro Goro no Mi, posée au sol (rendu 3D : GoroWaveRenderer) : un anneau d'énergie bleue suivi
 * d'un anneau de poussière, qui s'élargit jusqu'à {@code radius} blocs en {@code life} ticks, avec des arcs qui fusent devant
 * le front. {@code dome} : en plus, un dôme de lumière qui gonfle puis s'éteint (explosions d'El Thor et du Raigo). Purement
 * visuelle (les dégâts sont appliqués par l'attaque, côté serveur), jamais sauvegardée.
 */
public class GoroWave extends ModFxEntity {

    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(GoroWave.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(GoroWave.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DOME = SynchedEntityData.defineId(GoroWave.class, EntityDataSerializers.BOOLEAN);

    public GoroWave(EntityType<? extends GoroWave> type, Level level) {
        super(type, level);
    }

    public static GoroWave spawn(ServerLevel level, Vec3 ground, float radius, int life, boolean dome) {
        GoroWave wave = new GoroWave(ModEntities.GORO_WAVE, level);
        wave.managed = true;
        wave.entityData.set(RADIUS, radius);
        wave.entityData.set(LIFE, life);
        wave.entityData.set(DOME, dome);
        wave.setPos(ground.x, ground.y, ground.z);
        level.addFreshEntity(wave);
        return wave;
    }

    public float radius() {
        return this.entityData.get(RADIUS);
    }

    public int life() {
        return this.entityData.get(LIFE);
    }

    public boolean dome() {
        return this.entityData.get(DOME);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel && this.tickCount >= this.life()) {
            this.discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 6.0F);
        builder.define(LIFE, 16);
        builder.define(DOME, false);
    }
}
