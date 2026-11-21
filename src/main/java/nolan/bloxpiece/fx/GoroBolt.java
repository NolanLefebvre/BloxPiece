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
 * Un éclair BLEU du Goro Goro no Mi (rendu : GoroBoltRenderer, même construction que les éclairs du Haki des Rois mais en bleu,
 * et qui relie exactement deux points). Purement visuel : ni collision ni dégâts (les attaques les appliquent elles-mêmes),
 * jamais sauvegardé. Il part de sa position, va jusqu'au point {@code to}, crépite et s'éteint en {@code life} ticks.
 * {@code width} : épaisseur (1 = normale ; 3 à 6 = foudre géante d'El Thor ou de la pluie de foudre).
 */
public class GoroBolt extends ModFxEntity {

    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(GoroBolt.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(GoroBolt.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(GoroBolt.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DX = SynchedEntityData.defineId(GoroBolt.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY = SynchedEntityData.defineId(GoroBolt.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ = SynchedEntityData.defineId(GoroBolt.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DESCEND = SynchedEntityData.defineId(GoroBolt.class, EntityDataSerializers.INT);

    public GoroBolt(EntityType<? extends GoroBolt> type, Level level) {
        super(type, level);
    }

    /** Un éclair de {@code from} à {@code to}, qui frappe d'un coup. */
    public static GoroBolt strike(ServerLevel level, Vec3 from, Vec3 to, float width, int life) {
        return strike(level, from, to, width, life, 0);
    }

    /**
     * Un éclair de {@code from} à {@code to} dont la pointe DESCEND en {@code descend} ticks (traceur lumineux qui fonce vers
     * le point visé, avec ses ramifications), puis qui reste allumé jusqu'à {@code life} ticks (décharge principale).
     */
    public static GoroBolt strike(ServerLevel level, Vec3 from, Vec3 to, float width, int life, int descend) {
        GoroBolt bolt = new GoroBolt(ModEntities.GORO_BOLT, level);
        bolt.managed = true;
        bolt.entityData.set(SEED, level.getRandom().nextInt(1 << 20));
        bolt.entityData.set(LIFE, life);
        bolt.entityData.set(WIDTH, width);
        bolt.entityData.set(DX, (float) (to.x - from.x));
        bolt.entityData.set(DY, (float) (to.y - from.y));
        bolt.entityData.set(DZ, (float) (to.z - from.z));
        bolt.entityData.set(DESCEND, Math.max(0, descend));
        bolt.setPos(from.x, from.y, from.z);
        level.addFreshEntity(bolt);
        return bolt;
    }

    public int seed() {
        return this.entityData.get(SEED);
    }

    public int life() {
        return this.entityData.get(LIFE);
    }

    /** Durée de la descente de la pointe (0 : l'éclair frappe d'un coup). */
    public int descend() {
        return this.entityData.get(DESCEND);
    }

    public float width() {
        return this.entityData.get(WIDTH);
    }

    /** Vecteur de la position de départ jusqu'au point d'arrivée (blocs). */
    public float[] reach() {
        return new float[] {this.entityData.get(DX), this.entityData.get(DY), this.entityData.get(DZ)};
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
        builder.define(LIFE, 6);
        builder.define(WIDTH, 1.0F);
        builder.define(DX, 0.0F);
        builder.define(DY, -1.0F);
        builder.define(DZ, 0.0F);
        builder.define(DESCEND, 0);
    }
}
