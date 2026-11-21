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
 * Nuage d'orage du Goro Goro no Mi (rendu 3D : GoroCloudRenderer) : un amas de nuées gris-bleu sombre, traversé de lueurs
 * électriques bleues. Petit au-dessus du joueur (Mamaragan), immense au-dessus de la cible (El Thor). Purement visuel, jamais
 * sauvegardé. Il peut suivre une entité (au-dessus de sa tête). {@code size} : rayon (blocs) ; il apparaît, se charge (lueurs
 * de plus en plus vives) puis se dissipe en {@code life} ticks.
 */
public class GoroCloud extends ModFxEntity {

    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(GoroCloud.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(GoroCloud.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PEAK = SynchedEntityData.defineId(GoroCloud.class, EntityDataSerializers.INT);

    private Entity follow;
    private double followHeight;

    public GoroCloud(EntityType<? extends GoroCloud> type, Level level) {
        super(type, level);
    }

    public static GoroCloud spawn(ServerLevel level, Vec3 pos, float size, int life) {
        return spawn(level, pos, size, life, Math.max(1, (int) (life * 0.6F)));
    }

    /**
     * Nuage qui se forme, accumule de l'électricité jusqu'au tick {@code peak} (le moment où sa foudre jaillit : grand éclair
     * sous le nuage), reste chargé ensuite, puis se dissipe à la fin de {@code life}.
     */
    public static GoroCloud spawn(ServerLevel level, Vec3 pos, float size, int life, int peak) {
        GoroCloud cloud = new GoroCloud(ModEntities.GORO_CLOUD, level);
        cloud.managed = true;
        cloud.entityData.set(SIZE, size);
        cloud.entityData.set(LIFE, life);
        cloud.entityData.set(PEAK, Math.max(1, peak));
        cloud.setPos(pos.x, pos.y, pos.z);
        level.addFreshEntity(cloud);
        return cloud;
    }

    /** Le nuage reste {@code height} blocs au-dessus des pieds de {@code entity}. */
    public GoroCloud follow(Entity entity, double height) {
        this.follow = entity;
        this.followHeight = height;
        return this;
    }

    public float size() {
        return this.entityData.get(SIZE);
    }

    /** Tick de la décharge (charge maximale). */
    public int peak() {
        return this.entityData.get(PEAK);
    }

    public int life() {
        return this.entityData.get(LIFE);
    }

    /** Le nuage se dissipe dans {@code ticks} ticks (fin propre au lieu d'une disparition brutale). */
    public void fadeOut(int ticks) {
        this.entityData.set(LIFE, Math.min(this.life(), this.tickCount + ticks));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        if (this.tickCount >= this.life()) {
            this.discard();
            return;
        }
        if (this.follow != null && this.follow.isAlive()) {
            this.setPos(this.follow.getX(), this.follow.getY() + this.followHeight, this.follow.getZ());
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SIZE, 2.0F);
        builder.define(LIFE, 40);
        builder.define(PEAK, 24);
    }
}
