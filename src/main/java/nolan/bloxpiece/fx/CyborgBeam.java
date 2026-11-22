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
 * Radical Beam (race Cyborg) : le rayon laser jaune géant et son explosion, purement VISUELS (rendu : CyborgBeamRenderer ; les
 * dégâts et le cratère sont faits par CyborgRadicalBeamAbility). Part de sa position jusqu'au point d'impact {@code reach}
 * (DX/DY/DZ), avec un rayon de {@code radius} blocs, pendant {@code life} ticks : le front du rayon atteint l'impact en
 * {@link #TRAVEL} ticks, l'explosion (boule de lumière, onde de choc au sol) dure jusqu'à la fin. Jamais sauvegardé.
 */
public class CyborgBeam extends ModFxEntity {

    private static final EntityDataAccessor<Float> DX = SynchedEntityData.defineId(CyborgBeam.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY = SynchedEntityData.defineId(CyborgBeam.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ = SynchedEntityData.defineId(CyborgBeam.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(CyborgBeam.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(CyborgBeam.class, EntityDataSerializers.INT);
    /** Identifiant (réseau) du lanceur : en vue à la première personne, son rendu est adapté à sa caméra (CyborgBeamRenderer). */
    private static final EntityDataAccessor<Integer> SHOOTER = SynchedEntityData.defineId(CyborgBeam.class, EntityDataSerializers.INT);
    public static final int TRAVEL = 3;

    public CyborgBeam(EntityType<? extends CyborgBeam> type, Level level) {
        super(type, level);
    }

    public static CyborgBeam fire(ServerLevel level, net.minecraft.world.entity.Entity shooter, Vec3 from, Vec3 to, float radius, int life) {
        CyborgBeam beam = new CyborgBeam(ModEntities.CYBORG_BEAM, level);
        beam.managed = true;
        beam.entityData.set(SHOOTER, shooter.getId());
        beam.entityData.set(DX, (float) (to.x - from.x));
        beam.entityData.set(DY, (float) (to.y - from.y));
        beam.entityData.set(DZ, (float) (to.z - from.z));
        beam.entityData.set(RADIUS, radius);
        beam.entityData.set(LIFE, life);
        beam.setPos(from.x, from.y, from.z);
        level.addFreshEntity(beam);
        return beam;
    }

    public float[] reach() {
        return new float[] {this.entityData.get(DX), this.entityData.get(DY), this.entityData.get(DZ)};
    }

    public int shooter() {
        return this.entityData.get(SHOOTER);
    }

    public float radius() {
        return this.entityData.get(RADIUS);
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
        return distance < 256.0 * 256.0; // visible de très loin
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DX, 0.0F);
        builder.define(DY, 0.0F);
        builder.define(DZ, 1.0F);
        builder.define(RADIUS, 1.5F);
        builder.define(LIFE, 26);
        builder.define(SHOOTER, -1);
    }
}
