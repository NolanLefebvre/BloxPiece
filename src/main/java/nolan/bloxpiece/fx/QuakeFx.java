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
 * Effet 3D des attaques du Gura Gura no Mi (purement visuel, voir GuraModels côté client) :
 * <ul>
 *   <li>{@link #PANEL} : panneau de fissures dans l'air, face à la direction (Kaishin, fissures de la charge) ;</li>
 *   <li>{@link #ORB} : sphère d'énergie sismique (main pendant Gekishin, mains pendant la charge de Shima Yurashi) ;</li>
 *   <li>{@link #RING} : anneau de fissures couché au sol (Kabutowari) ;</li>
 *   <li>{@link #BLAST} : immense sphère fissurée qui s'étend (impact de Gekishin, déflagration de Shima Yurashi).</li>
 * </ul>
 * Type, taille (rayon en blocs) et durée de vie sont synchronisés ; l'orientation est la rotation de l'entité. Une attaque peut
 * déplacer l'effet chaque tick (sphère qui suit la main) et changer sa taille (charge).
 */
public class QuakeFx extends ModFxEntity {

    public static final int PANEL = 0;
    public static final int ORB = 1;
    public static final int RING = 2;
    public static final int BLAST = 3;
    /** Immense fissure d'air verticale (fin de charge de Shima Yurashi). */
    public static final int TEAR = 4;
    /** Tsunami géant soulevé par la secousse (Shima Yurashi) ; le modèle avance vers +Z. */
    public static final int WAVE = 5;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(QuakeFx.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(QuakeFx.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(QuakeFx.class, EntityDataSerializers.INT);
    /** Joueur dont la main porte l'effet (id d'entité, -1 = aucun) et quelle main (1 = droite, -1 = gauche, 0 = aucune). */
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(QuakeFx.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HAND = SynchedEntityData.defineId(QuakeFx.class, EntityDataSerializers.INT);

    public QuakeFx(EntityType<? extends QuakeFx> type, Level level) {
        super(type, level);
    }

    /** Un effet de type {@code kind}, de rayon {@code size} blocs, qui dure {@code life} ticks, orienté (yaw, pitch). */
    public static QuakeFx spawn(ServerLevel level, int kind, Vec3 pos, float yaw, float pitch, float size, int life) {
        QuakeFx fx = new QuakeFx(ModEntities.QUAKE_FX, level);
        fx.managed = true;
        fx.entityData.set(KIND, kind);
        fx.entityData.set(SIZE, size);
        fx.entityData.set(LIFE, life);
        fx.setPos(pos.x, pos.y, pos.z);
        fx.setYRot(yaw);
        fx.setXRot(pitch);
        fx.yRotO = yaw;
        fx.xRotO = pitch;
        level.addFreshEntity(fx);
        return fx;
    }

    /**
     * Ancre l'effet sur une main du joueur : le CLIENT le place exactement sur le poing (pose du bras comprise) en troisième
     * personne, et en bas de l'écran du côté de cette main en première personne (voir QuakeFxRenderer).
     */
    public QuakeFx anchorTo(net.minecraft.world.entity.Entity owner, int hand) {
        this.entityData.set(OWNER, owner.getId());
        this.entityData.set(HAND, hand);
        return this;
    }

    public int ownerId() {
        return this.entityData.get(OWNER);
    }

    public int hand() {
        return this.entityData.get(HAND);
    }

    public int kind() {
        return this.entityData.get(KIND);
    }

    public float size() {
        return this.entityData.get(SIZE);
    }

    public void setSize(float size) {
        this.entityData.set(SIZE, size);
    }

    public int life() {
        return this.entityData.get(LIFE);
    }

    /** Prolonge la vie de l'effet (ex : sphère tenue tant que la charge dure). */
    public void setLife(int life) {
        this.entityData.set(LIFE, life);
    }

    /** Déplace l'effet (suit une main) en gardant une interpolation fluide côté client. */
    public void follow(Vec3 pos) {
        this.setPos(pos.x, pos.y, pos.z);
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
        builder.define(KIND, PANEL);
        builder.define(SIZE, 1.0F);
        builder.define(LIFE, 20);
        builder.define(OWNER, -1);
        builder.define(HAND, 0);
    }
}
