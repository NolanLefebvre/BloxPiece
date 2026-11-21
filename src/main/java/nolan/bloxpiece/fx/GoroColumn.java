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
 * Colonne de foudre géante d'El Thor (rendu 3D : GoroColumnRenderer) : un pilier d'énergie bleue de {@code radius} blocs de
 * rayon qui descend du nuage (position de l'entité = bas du nuage) jusqu'au sol ({@code height} blocs plus bas), entouré
 * d'éclairs épais qui s'enroulent autour de lui et se ramifient. Elle s'abat en quelques ticks, rugit, puis s'amincit et
 * s'éteint en {@code life} ticks. Purement visuelle, jamais sauvegardée.
 */
public class GoroColumn extends ModFxEntity {

    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(GoroColumn.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(GoroColumn.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(GoroColumn.class, EntityDataSerializers.INT);

    public GoroColumn(EntityType<? extends GoroColumn> type, Level level) {
        super(type, level);
    }

    public static GoroColumn spawn(ServerLevel level, Vec3 top, float radius, float height, int life) {
        GoroColumn column = new GoroColumn(ModEntities.GORO_COLUMN, level);
        column.managed = true;
        column.entityData.set(RADIUS, radius);
        column.entityData.set(HEIGHT, height);
        column.entityData.set(LIFE, life);
        column.setPos(top.x, top.y, top.z);
        level.addFreshEntity(column);
        return column;
    }

    public float radius() {
        return this.entityData.get(RADIUS);
    }

    public float height() {
        return this.entityData.get(HEIGHT);
    }

    public int life() {
        return this.entityData.get(LIFE);
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
        builder.define(RADIUS, 3.0F);
        builder.define(HEIGHT, 24.0F);
        builder.define(LIFE, 20);
    }
}
