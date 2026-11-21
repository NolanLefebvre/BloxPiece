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
 * La gigantesque sphère électrique du Raigo (rendu 3D : GoroOrbRenderer : noyau éblouissant, couches d'énergie bleue qui
 * tournent, arcs électriques qui courent sans cesse sur sa surface et ramifications qui s'en détachent). Purement visuelle :
 * position, taille et trajectoire sont pilotées par l'attaque (GoroRaigoAbility), côté serveur. Jamais sauvegardée.
 */
public class GoroOrb extends ModFxEntity {

    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(GoroOrb.class, EntityDataSerializers.FLOAT);

    public GoroOrb(EntityType<? extends GoroOrb> type, Level level) {
        super(type, level);
    }

    public static GoroOrb spawn(ServerLevel level, Vec3 pos, float radius) {
        GoroOrb orb = new GoroOrb(ModEntities.GORO_ORB, level);
        orb.managed = true;
        orb.entityData.set(RADIUS, radius);
        orb.setPos(pos.x, pos.y, pos.z);
        level.addFreshEntity(orb);
        return orb;
    }

    public float radius() {
        return this.entityData.get(RADIUS);
    }

    public void setRadius(float radius) {
        this.entityData.set(RADIUS, radius);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 0.5F);
    }
}
