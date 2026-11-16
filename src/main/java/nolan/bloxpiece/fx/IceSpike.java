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
 * Pic de glace qui jaillit du sol (modèle IceSpikeModel) : impact du trident lancé et Ère glaciaire. Purement visuel (les dégâts
 * sont faits par l'attaque). Il sort du sol en {@link #GROW_TICKS} ticks, reste dressé, puis se brise en éclats et disparaît au
 * bout de {@link #LIFE_TICKS} ticks. Sa taille et son inclinaison sont synchronisées (lues par le renderer).
 */
public class IceSpike extends ModFxEntity {

    /** Durée de vie par défaut (ticks) ; chaque pic peut avoir la sienne (voir {@link #life()}). */
    public static final int LIFE_TICKS = 70;
    /** Durée de la poussée hors du sol (ticks), lue aussi par le renderer. */
    public static final int GROW_TICKS = 4;

    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(IceSpike.class, EntityDataSerializers.FLOAT);
    /** Durée de vie de ce pic (ticks), synchronisée : le renderer anime sa disparition au bon moment. */
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(IceSpike.class, EntityDataSerializers.INT);

    public IceSpike(EntityType<? extends IceSpike> type, Level level) {
        super(type, level);
    }

    /**
     * Un pic posé au sol en {@code ground}, de taille {@code size} (1 = ~2,5 blocs de haut), incliné vers l'extérieur de
     * {@code tiltDeg} degrés dans la direction {@code yawDeg}.
     */
    public static IceSpike spawn(ServerLevel level, Vec3 ground, float size, float yawDeg, float tiltDeg) {
        return spawn(level, ground, size, yawDeg, tiltDeg, LIFE_TICKS);
    }

    /** Pareil, avec une durée de vie choisie (ticks). */
    public static IceSpike spawn(ServerLevel level, Vec3 ground, float size, float yawDeg, float tiltDeg, int life) {
        IceSpike spike = new IceSpike(ModEntities.ICE_SPIKE, level);
        spike.managed = true;
        spike.entityData.set(SIZE, size);
        spike.entityData.set(LIFE, life);
        spike.setYRot(yawDeg);
        spike.setXRot(tiltDeg);
        spike.yRotO = yawDeg;
        spike.xRotO = tiltDeg;
        spike.setPos(ground.x, ground.y, ground.z);
        level.addFreshEntity(spike);
        level.playSound(null, ground.x, ground.y, ground.z, net.minecraft.sounds.SoundEvents.GLASS_PLACE,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.6F + level.getRandom().nextFloat() * 0.4F);
        return spike;
    }

    public float size() {
        return this.entityData.get(SIZE);
    }

    public int life() {
        return this.entityData.get(LIFE);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && this.tickCount >= this.life()) {
            IceEffects.shatter(level, this.position().add(0.0, 1.0 * this.size(), 0.0), this.size());
            this.discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SIZE, 1.0F);
        builder.define(LIFE, LIFE_TICKS);
    }
}
