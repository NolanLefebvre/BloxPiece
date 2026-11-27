package nolan.bloxpiece.fx;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModEntities;

/**
 * Effets 3D des attaques de la race Fishman, purement VISUELS (dégâts, hitbox et recul sont calculés par les attaques, côté
 * serveur). Une entité par effet, posée à l'instant exact où il se produit ; son type ({@link #kind}) choisit le rendu (client :
 * FishmanFxRenderer) :
 * <ul>
 *   <li>{@link #WAVE} : Onde aquatique : une onde d'eau comprimée qui part du poing et avance sur {@link #reach} ;</li>
 *   <li>{@link #DROP} : une goutte de la Lance aquatique, qui file sur {@link #reach} et éclabousse au bout ;</li>
 *   <li>{@link #SPLASH} : petite éclaboussure (goutte qui touche une cible) ;</li>
 *   <li>{@link #TIDE} : Vague dévastatrice : vagues successives qui se propagent autour du point de frappe (rayon = taille) ;</li>
 *   <li>{@link #SPIRAL} : Perforation des profondeurs : la spirale d'eau projetée, sur {@link #reach} ;</li>
 *   <li>{@link #BLAST} : son explosion d'eau et l'onde de choc secondaire.</li>
 * </ul>
 * La durée de chaque trajet se déduit de la vitesse de l'attaque (constantes communes au client et au serveur). Jamais sauvegardée.
 */
public class FishmanFx extends ModFxEntity {

    public static final int WAVE = 1, DROP = 2, SPLASH = 3, TIDE = 4, SPIRAL = 5, BLAST = 6;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(FishmanFx.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DX = SynchedEntityData.defineId(FishmanFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY = SynchedEntityData.defineId(FishmanFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ = SynchedEntityData.defineId(FishmanFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(FishmanFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(FishmanFx.class, EntityDataSerializers.INT);
    /** Trajet : ticks pour aller au bout de {@link #reach} (0 : effet immobile). */
    private static final EntityDataAccessor<Integer> TRAVEL = SynchedEntityData.defineId(FishmanFx.class, EntityDataSerializers.INT);

    public FishmanFx(EntityType<? extends FishmanFx> type, Level level) {
        super(type, level);
    }

    /**
     * Pose un effet en {@code at} : {@code vector} = direction, ou trajet complet ; {@code size} = taille ; {@code travel} =
     * ticks du trajet ; {@code life} = durée totale (ticks).
     */
    public static FishmanFx spawn(ServerLevel level, int kind, Vec3 at, Vec3 vector, float size, int travel, int life) {
        FishmanFx fx = new FishmanFx(ModEntities.FISHMAN_FX, level);
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

    /** Le trajet s'arrête plus tôt (obstacle, cible) : nouveau trajet, nouvelle durée ; les clients voient le front s'arrêter là. */
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
        return distance < 192.0 * 192.0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, WAVE);
        builder.define(DX, 0.0F);
        builder.define(DY, 0.0F);
        builder.define(DZ, 1.0F);
        builder.define(SIZE, 1.0F);
        builder.define(TRAVEL, 0);
        builder.define(LIFE, 20);
    }
}
