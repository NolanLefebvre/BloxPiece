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
 * Effets 3D des attaques de la race Humaine, purement VISUELS (les dégâts sont faits par les attaques, côté serveur). Une entité
 * par effet, posée à l'instant exact de l'impact ; son type ({@link #kind}) choisit le rendu (client : HumanFxRenderer) :
 * <ul>
 *   <li>{@link #PUNCH} : Impact Fulgurant, au point de contact du poing ;</li>
 *   <li>{@link #RUSH} : Ruée Destructrice, au point de collision de l'épaule ;</li>
 *   <li>{@link #QUAKE} : Briseur Terrestre, au point d'impact au sol ;</li>
 *   <li>{@link #WAVE} : Poing du Conquérant, l'onde de choc qui part du poing jusqu'à son point d'impact ({@link #reach}) ;</li>
 *   <li>{@link #BLAST} : Poing du Conquérant, l'explosion à l'impact et l'onde secondaire.</li>
 *   <li>{@link #TRAIL} : Ruée Destructrice, la traînée de vitesse qui suit le trajet RÉEL du lanceur (ses positions sont
 *       relevées côté client, à chaque image, tant qu'il fonce).</li>
 * </ul>
 * Données synchronisées : direction ou trajet (DX/DY/DZ), taille (rayon en blocs), durée de vie (ticks), lanceur (vue à la
 * première personne adaptée). Jamais sauvegardée.
 */
public class HumanFx extends ModFxEntity {

    public static final int PUNCH = 1, RUSH = 2, QUAKE = 3, WAVE = 4, BLAST = 5, TRAIL = 6;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(HumanFx.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DX = SynchedEntityData.defineId(HumanFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY = SynchedEntityData.defineId(HumanFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ = SynchedEntityData.defineId(HumanFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(HumanFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(HumanFx.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHOOTER = SynchedEntityData.defineId(HumanFx.class, EntityDataSerializers.INT);

    public HumanFx(EntityType<? extends HumanFx> type, Level level) {
        super(type, level);
    }

    /**
     * Pose un effet en {@code at} : {@code vector} = direction de la frappe (PUNCH, RUSH, BLAST), trajet complet jusqu'à
     * l'impact (WAVE) ; {@code size} = rayon (blocs) ; {@code life} = durée (ticks).
     */
    public static HumanFx spawn(ServerLevel level, Entity shooter, int kind, Vec3 at, Vec3 vector, float size, int life) {
        HumanFx fx = new HumanFx(ModEntities.HUMAN_FX, level);
        fx.managed = true;
        fx.entityData.set(KIND, kind);
        fx.entityData.set(DX, (float) vector.x);
        fx.entityData.set(DY, (float) vector.y);
        fx.entityData.set(DZ, (float) vector.z);
        fx.entityData.set(SIZE, size);
        fx.entityData.set(LIFE, life);
        fx.entityData.set(SHOOTER, shooter == null ? -1 : shooter.getId());
        fx.setPos(at.x, at.y, at.z);
        level.addFreshEntity(fx);
        return fx;
    }

    /**
     * L'onde s'arrête plus tôt que prévu (elle a atteint sa cible) : nouveau trajet {@code vector} (jusqu'au point d'arrêt) et
     * nouvelle durée de vie ; les clients voient le front s'arrêter là et s'effacer.
     */
    public void stopAt(Vec3 vector, int life) {
        this.entityData.set(DX, (float) vector.x);
        this.entityData.set(DY, (float) vector.y);
        this.entityData.set(DZ, (float) vector.z);
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

    public int life() {
        return this.entityData.get(LIFE);
    }

    public int shooter() {
        return this.entityData.get(SHOOTER);
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
        builder.define(KIND, PUNCH);
        builder.define(DX, 0.0F);
        builder.define(DY, 0.0F);
        builder.define(DZ, 1.0F);
        builder.define(SIZE, 1.0F);
        builder.define(LIFE, 20);
        builder.define(SHOOTER, -1);
    }
}
