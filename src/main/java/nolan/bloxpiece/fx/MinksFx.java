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
 * Effets 3D des attaques de la race Minks, purement VISUELS (les dégâts sont faits par les attaques, côté serveur). Une entité
 * par effet, posée à l'instant exact où il se produit ; son type ({@link #kind}) choisit le rendu (client : MinksFxRenderer) :
 * <ul>
 *   <li>{@link #IMPACT} : coup de griffes électriques (marques de griffes, éclats, arcs) ;</li>
 *   <li>{@link #BARRAGE_HIT} : un coup du barrage (pression, arcs, petite marque) ;</li>
 *   <li>{@link #BLINK} : bond éclair de la Danse : éclair qui relie l'ancienne position à la nouvelle ({@link #reach}) et
 *       silhouette électrique qui reste un instant à l'ancienne place ;</li>
 *   <li>{@link #FINAL} : dernier coup (barrage, danse) : grande déchirure électrique et onde ;</li>
 *   <li>{@link #SULONG} : explosion électrique de la transformation Sulong ;</li>
 *   <li>{@link #ZONE} : zone INVISIBLE de la Danse des griffes éclair (centre, rayon = taille adapté à la place disponible) :
 *       rien n'est dessiné, elle sert à la caméra cinématique du lanceur ;</li>
 *   <li>{@link #CRASH} : explosion d'éclairs quand le joueur s'écrase au centre à la fin de la Danse.</li>
 * </ul>
 * Jamais sauvegardée.
 */
public class MinksFx extends ModFxEntity {

    public static final int IMPACT = 1, BARRAGE_HIT = 2, BLINK = 3, FINAL = 4, SULONG = 5, ZONE = 6, CRASH = 7;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(MinksFx.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DX = SynchedEntityData.defineId(MinksFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY = SynchedEntityData.defineId(MinksFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ = SynchedEntityData.defineId(MinksFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(MinksFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(MinksFx.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHOOTER = SynchedEntityData.defineId(MinksFx.class, EntityDataSerializers.INT);
    /** Diagonale des trois griffures : faux = diagonale droite, vrai = diagonale gauche (alternée à chaque frappe, Minks.slash). */
    private static final EntityDataAccessor<Boolean> FLIP = SynchedEntityData.defineId(MinksFx.class, EntityDataSerializers.BOOLEAN);

    public MinksFx(EntityType<? extends MinksFx> type, Level level) {
        super(type, level);
    }

    /**
     * Pose un effet en {@code at} : {@code vector} = direction de la frappe, ou trajet complet du bond (BLINK) jusqu'à
     * la nouvelle position ; {@code size} = taille ; {@code life} = durée (ticks).
     */
    public static MinksFx spawn(ServerLevel level, Entity shooter, int kind, Vec3 at, Vec3 vector, float size, int life) {
        MinksFx fx = new MinksFx(ModEntities.MINKS_FX, level);
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

    /** Prolonge la vie de l'effet (marque de griffes croisée par la frappe suivante). */
    public void extend(int life) {
        if (life > life()) {
            this.entityData.set(LIFE, life);
        }
    }

    /** Choisit la diagonale des griffures (synchronisée). */
    public MinksFx flip(boolean flip) {
        this.entityData.set(FLIP, flip);
        return this;
    }

    public boolean flipped() {
        return this.entityData.get(FLIP);
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
        builder.define(KIND, IMPACT);
        builder.define(DX, 0.0F);
        builder.define(DY, 0.0F);
        builder.define(DZ, 1.0F);
        builder.define(SIZE, 1.0F);
        builder.define(LIFE, 20);
        builder.define(SHOOTER, -1);
        builder.define(FLIP, false);
    }
}
