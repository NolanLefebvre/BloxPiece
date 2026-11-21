package nolan.bloxpiece.fx;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModEntities;

/**
 * Impact du coup de hache au sol de Morgan : effet visuel qui lui est propre (rendu : MorganQuakeRenderer), purement visuel,
 * jamais sauvegardé. Posé au point où le fer frappe le sol, orienté comme Morgan (vers +z dans son repère après rotation).
 * <ul>
 *   <li>cratère : éclats de sol fendus tout autour du point d'impact ;</li>
 *   <li>fissures : grandes crevasses dentelées qui partent en éventail devant lui et se propagent avec l'onde, avec des
 *       ramifications, puis se referment en s'estompant ;</li>
 *   <li>onde de choc : un front de poussière (mur d'air et de terre soulevée) qui avance devant lui jusqu'au bout de la zone de
 *       dégâts, en s'élargissant comme elle, puis se dissipe ;</li>
 *   <li>débris : blocs de roche arrachés qui sautent et retombent.</li>
 * </ul>
 * Les dimensions sont celles de la zone de dégâts (MorganAxeGoal.SLAM_RANGE, demi-largeur) : on voit exactement ce qui touche.
 */
public class MorganQuake extends ModFxEntity {

    /** Durée de propagation de l'onde (ticks), puis de l'effacement. */
    public static final int SPREAD_TICKS = 8;
    public static final int LIFE = 36;
    /** Distance entre Morgan et le point d'impact (blocs). */
    public static final double IMPACT_AHEAD = 1.6;

    public MorganQuake(EntityType<? extends MorganQuake> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public static void spawn(ServerLevel level, Vec3 impact, float yaw) {
        MorganQuake fx = new MorganQuake(ModEntities.MORGAN_QUAKE, level);
        fx.managed = true;
        fx.setPos(impact.x, impact.y, impact.z);
        fx.setYRot(yaw);
        fx.yRotO = yaw;
        level.addFreshEntity(fx);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount >= LIFE) {
            this.discard();
        }
    }
}
