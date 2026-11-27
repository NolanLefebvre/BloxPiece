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
 * Effets 3D des attaques de la race Lunarian, purement VISUELS (dégâts, collisions, déplacements et projections sont calculés
 * par les attaques, côté serveur). Une entité par effet ; son type ({@link #kind}) choisit le rendu (client : LunarianFxRenderer) :
 * <ul>
 *   <li>{@link #FEATHER} : une plume incandescente qui vole sur {@link #reach} en oscillant, avec sa traînée de feu ;</li>
 *   <li>{@link #BURST} : petite explosion de feu (plume qui touche) ;</li>
 *   <li>{@link #FIST_BOOM} : explosion du Poing embrasé au contact, onde de choc (direction = {@link #reach}) ;</li>
 *   <li>{@link #TRAIL} : morceau de traînée de feu laissé par le joueur (dash, envol, plongée ; taille = intensité) ;</li>
 *   <li>{@link #IMPACT} : impact au sol de la Tempête du jugement céleste (explosion, onde au sol, débris) ;</li>
 *   <li>{@link #TORNADO} : la tornade de feu (rayon de base = taille, hauteur = y de {@link #reach}) qui monte depuis le sol.</li>
 * </ul>
 * La durée d'un trajet est {@link #travel} ticks. Jamais sauvegardée.
 */
public class LunarianFx extends ModFxEntity {

    public static final int FEATHER = 1, BURST = 2, FIST_BOOM = 3, TRAIL = 4, IMPACT = 5, TORNADO = 6;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(LunarianFx.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DX = SynchedEntityData.defineId(LunarianFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY = SynchedEntityData.defineId(LunarianFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ = SynchedEntityData.defineId(LunarianFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(LunarianFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(LunarianFx.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRAVEL = SynchedEntityData.defineId(LunarianFx.class, EntityDataSerializers.INT);

    public LunarianFx(EntityType<? extends LunarianFx> type, Level level) {
        super(type, level);
    }

    /** Pose un effet en {@code at} : {@code vector} = direction ou trajet ; {@code size} ; {@code travel} = ticks du trajet ; {@code life}. */
    public static LunarianFx spawn(ServerLevel level, int kind, Vec3 at, Vec3 vector, float size, int travel, int life) {
        LunarianFx fx = new LunarianFx(ModEntities.LUNARIAN_FX, level);
        fx.managed = true;
        fx.entityData.set(KIND, kind);
        fx.entityData.set(DX, (float) vector.x);
        fx.entityData.set(DY, (float) vector.y);
        fx.entityData.set(DZ, (float) vector.z);
        fx.entityData.set(SIZE, size);
        fx.entityData.set(TRAVEL, travel);
        fx.entityData.set(LIFE, life);
        fx.setPos(at.x, at.y, at.z);
        level.addFreshEntity(fx);
        return fx;
    }

    /** Le trajet s'arrête plus tôt (cible, obstacle) : nouveau trajet, nouvelle durée. */
    public void stopAt(Vec3 vector, int travel, int life) {
        this.entityData.set(DX, (float) vector.x);
        this.entityData.set(DY, (float) vector.y);
        this.entityData.set(DZ, (float) vector.z);
        this.entityData.set(TRAVEL, travel);
        this.entityData.set(LIFE, life);
    }

    public int kind() {
        return this.entityData.get(KIND);
    }

    public float[] reach() {
        return new float[] {this.entityData.get(DX), this.entityData.get(DY), this.entityData.get(DZ)};
    }

    public float size() {
        return this.entityData.get(SIZE);
    }

    public int travel() {
        return this.entityData.get(TRAVEL);
    }

    public int life() {
        return this.entityData.get(LIFE);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel && this.tickCount >= life()) {
            this.discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0 * 256.0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, FEATHER);
        builder.define(DX, 0.0F);
        builder.define(DY, 0.0F);
        builder.define(DZ, 1.0F);
        builder.define(SIZE, 1.0F);
        builder.define(TRAVEL, 0);
        builder.define(LIFE, 20);
    }
}
