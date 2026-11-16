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
 * Statue de glace : le bloc de glace translucide (modèle IceStatueModel) qui emprisonne une entité gelée par une attaque du fruit
 * de la Glace. Purement visuelle : l'immobilisation est faite par IceFreeze, qui fait suivre la statue à sa cible et la retire à la
 * fin. Ses dimensions (largeur, hauteur de la cible) sont synchronisées pour que la statue enveloppe exactement la cible.
 */
public class IceStatue extends ModFxEntity {

    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(IceStatue.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(IceStatue.class, EntityDataSerializers.FLOAT);

    public IceStatue(EntityType<? extends IceStatue> type, Level level) {
        super(type, level);
    }

    /** Une statue autour de {@code target}, à sa taille. */
    public static IceStatue spawn(ServerLevel level, Entity target) {
        IceStatue statue = new IceStatue(ModEntities.ICE_STATUE, level);
        statue.managed = true;
        statue.entityData.set(WIDTH, target.getBbWidth());
        statue.entityData.set(HEIGHT, target.getBbHeight());
        statue.setPos(target.getX(), target.getY(), target.getZ());
        level.addFreshEntity(statue);
        return statue;
    }

    public float width() {
        return this.entityData.get(WIDTH);
    }

    public float height() {
        return this.entityData.get(HEIGHT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WIDTH, 0.6F);
        builder.define(HEIGHT, 1.8F);
    }
}
