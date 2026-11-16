package nolan.bloxpiece.fx;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModEntities;

/**
 * Le sceau du Bouddha : le cercle doré qui marque au sol la zone d'impact de la Paume du Bouddha (modèle plat BuddhaSealModel côté
 * client). Entité purement VISUELLE : aucune collision, aucun dégât, jamais sauvegardée.
 *
 * Cycle de vie (serveur) : créé au début de la charge (BuddhaPalmAbility), il suit le point visé pendant la charge, puis reste fixe
 * pendant la chute de la main. À l'impact (BuddhaPalm), {@link #impact()} déclenche l'onde de choc : le client l'élargit et le
 * dissout, puis il disparaît. Sécurité : il disparaît aussi tout seul si l'attaque est annulée ou au bout de {@link #MAX_LIFE}.
 *
 * Synchronisation : position (updateInterval 1) et instant de l'impact (donnée synchronisée) : même animation chez tous les joueurs.
 * Étend Projectile, comme BuddhaPalm, pour profiter de sa gestion des sauvegardes et du suivi réseau sans rien réécrire.
 */
public class BuddhaSeal extends Projectile {

    /** Rayon du sceau au sol (blocs) : un peu moins que la zone de dégâts, qu'il annonce. Lu par le renderer. */
    public static final double RADIUS = BuddhaPalm.BLAST_RADIUS * 0.9;
    /** À l'impact, le sceau s'élargit jusqu'à ce multiple de son rayon (onde de choc). Lu par le renderer. */
    public static final float SHOCK_EXPAND = 2.2F;
    /** Durée de l'onde de choc / de la dissolution (ticks). Lu par le renderer. */
    public static final float IMPACT_TICKS = 16.0F;
    /** Durée de vie maximale (ticks) : un sceau dont l'attaque a été interrompue ne reste jamais. */
    private static final int MAX_LIFE = 20 * 6;

    private static final EntityDataAccessor<Integer> IMPACT_AGE =
            SynchedEntityData.defineId(BuddhaSeal.class, EntityDataSerializers.INT);

    /** Taille du sceau (multiple de RADIUS), synchronisée : la Déflagration le fait grandir pendant sa charge. */
    private static final EntityDataAccessor<Float> SIZE =
            SynchedEntityData.defineId(BuddhaSeal.class, EntityDataSerializers.FLOAT);

    private boolean managed;

    public BuddhaSeal(EntityType<? extends BuddhaSeal> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Fait apparaître un sceau posé au sol en {@code ground}. */
    public static BuddhaSeal spawn(ServerLevel level, Vec3 ground) {
        BuddhaSeal seal = new BuddhaSeal(ModEntities.BUDDHA_SEAL, level);
        seal.managed = true;
        seal.place(ground);
        level.addFreshEntity(seal);
        return seal;
    }

    /** Déplace le sceau (pendant la charge, il suit le point visé). Légèrement au-dessus du sol : jamais caché dans le bloc. */
    public void place(Vec3 ground) {
        this.setPos(ground.x, ground.y + 0.06, ground.z);
    }

    /** L'impact a eu lieu : l'onde de choc commence (une seule fois). */
    public void impact() {
        if (this.impactAge() < 0) {
            this.entityData.set(IMPACT_AGE, this.tickCount);
        }
    }

    /** Change la taille du sceau (1 = RADIUS). */
    public void setSize(float size) {
        this.entityData.set(SIZE, size);
    }

    /** Taille du sceau (1 = RADIUS). Lue par le renderer. */
    public float size() {
        return this.entityData.get(SIZE);
    }

    /** Âge (tickCount) au moment de l'impact, ou -1. Lu par le renderer, des deux côtés. */
    public int impactAge() {
        return this.entityData.get(IMPACT_AGE);
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(Vec3.ZERO);
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        int impactAge = this.impactAge();
        if (!this.managed || this.tickCount > MAX_LIFE
                || (impactAge >= 0 && this.tickCount - impactAge >= IMPACT_TICKS)) {
            this.discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(IMPACT_AGE, -1);
        builder.define(SIZE, 1.0F);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0 * 256.0;
    }
}
