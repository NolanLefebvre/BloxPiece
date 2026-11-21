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
 * Un éclair 3D du Haki des Rois : ruban brisé noir au coeur et rouge vif autour (HakiBoltRenderer), qui crépite quelques ticks
 * autour de l'utilisateur. Purement visuel : ni collision ni dégâts, jamais sauvegardé. {@code seed} choisit la forme.
 */
public class HakiBolt extends ModFxEntity {

    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(HakiBolt.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(HakiBolt.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(HakiBolt.class, EntityDataSerializers.INT);
    /** Distance parcourue pendant sa vie (1 = normale ; petit = l'éclair reste presque sur place, ex : sur la lame d'Ace). */
    private static final EntityDataAccessor<Float> DRIFT = SynchedEntityData.defineId(HakiBolt.class, EntityDataSerializers.FLOAT);
    /** Épaisseur (1 = normale, celle du joueur ; plus grand = éclairs géants de Gol D. Roger). */
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(HakiBolt.class, EntityDataSerializers.FLOAT);

    public HakiBolt(EntityType<? extends HakiBolt> type, Level level) {
        super(type, level);
    }

    public static void spawn(ServerLevel level, Vec3 pos, float yaw, float pitch, float size, int life) {
        spawn(level, pos, yaw, pitch, size, life, 1.0F);
    }

    public static void spawn(ServerLevel level, Vec3 pos, float yaw, float pitch, float size, int life, float drift) {
        spawn(level, pos, yaw, pitch, size, life, drift, 1.0F);
    }

    /** Éclair amplifié : {@code width} multiplie son épaisseur (même rendu, mêmes couleurs). */
    public static void spawn(ServerLevel level, Vec3 pos, float yaw, float pitch, float size, int life, float drift, float width) {
        HakiBolt bolt = new HakiBolt(ModEntities.HAKI_BOLT, level);
        bolt.entityData.set(DRIFT, drift);
        bolt.entityData.set(WIDTH, width);
        bolt.managed = true;
        bolt.entityData.set(SEED, level.getRandom().nextInt(1 << 20));
        bolt.entityData.set(SIZE, size);
        bolt.entityData.set(LIFE, life);
        bolt.setPos(pos.x, pos.y, pos.z);
        bolt.setYRot(yaw);
        bolt.setXRot(pitch);
        bolt.yRotO = yaw;
        bolt.xRotO = pitch;
        level.addFreshEntity(bolt);
    }

    public int seed() {
        return this.entityData.get(SEED);
    }

    public float size() {
        return this.entityData.get(SIZE);
    }

    public float drift() {
        return this.entityData.get(DRIFT);
    }

    public float width() {
        return this.entityData.get(WIDTH);
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
        builder.define(SEED, 0);
        builder.define(SIZE, 1.0F);
        builder.define(LIFE, 6);
        builder.define(DRIFT, 1.0F);
        builder.define(WIDTH, 1.0F);
    }
}
