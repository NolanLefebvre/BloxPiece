package nolan.bloxpiece.fx;

import java.util.UUID;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModEntities;

/**
 * Carapace vide du Kame Kame no Mi, posée dans le monde pendant que son propriétaire est dans sa dimension de poche
 * (KamePocketAbility) : elle tombe au sol et y reste, sauvegardée avec le monde (propriétaire : attachment KAME_SHELL_OWNER).
 * Indestructible, immobile (sauf la chute), jamais poussée. Quand son propriétaire ressort (Kame.exitPocket), il revient caché
 * dedans, elle se redresse jusqu'à son torse puis quitte le sol pour se remettre sur son dos (c'est seulement là qu'elle
 * disparaît). Par sécurité, si son propriétaire est connecté, hors de sa poche et ne la rejoint pas (mort dans sa poche...),
 * elle se retire d'elle-même : jamais de carapace en double.
 */
public class KameShellEntity extends Projectile {

    /** Âge (tickCount) où la carapace commence à se redresser (sortie de la poche), -1 sinon. Synchronisé pour le rendu. */
    private static final EntityDataAccessor<Integer> RISE = SynchedEntityData.defineId(KameShellEntity.class,
            EntityDataSerializers.INT);

    public KameShellEntity(EntityType<? extends KameShellEntity> type, Level level) {
        super(type, level);
    }

    /** La carapace se détache du joueur et tombe à ses pieds, tournée comme lui. */
    public static KameShellEntity drop(ServerLevel level, ServerPlayer owner) {
        KameShellEntity shell = new KameShellEntity(ModEntities.KAME_SHELL, level);
        shell.setAttached(ModAttachments.KAME_SHELL_OWNER, owner.getUUID().toString());
        shell.setPos(owner.getX(), owner.getY() + 0.6, owner.getZ());
        shell.setYRot(owner.yBodyRot);
        shell.yRotO = owner.yBodyRot;
        level.addFreshEntity(shell);
        return shell;
    }

    /** Sortie de la poche : la carapace se redresse jusqu'au torse de son propriétaire (Kame.RISE_TICKS). */
    public void startRise() {
        this.entityData.set(RISE, this.tickCount);
    }

    public int riseStart() {
        return this.entityData.get(RISE);
    }

    @Override
    public void tick() {
        super.tick();
        // chute jusqu'au sol, puis immobile
        Vec3 v = this.getDeltaMovement();
        v = this.onGround() || this.riseStart() >= 0 ? Vec3.ZERO : new Vec3(0.0, Math.max(-1.0, v.y - 0.06), 0.0);
        this.setDeltaMovement(v);
        this.move(MoverType.SELF, v);
        if (this.level() instanceof ServerLevel level && this.tickCount % 20 == 0) {
            String owner = this.getAttached(ModAttachments.KAME_SHELL_OWNER);
            if (owner == null) {
                this.discard();
                return;
            }
            // elle reste au sol tout le temps que son propriétaire est dans sa poche (même déconnecté) ; elle ne se retire d'elle-même
            // que s'il est connecté, hors de sa poche, et ne la rejoint pas (mort dans sa poche, autre carapace) : jamais de doublon
            UUID ownerId = UUID.fromString(owner);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerId);
            String wait = player == null ? null : player.getAttached(ModAttachments.KAME_RETURN);
            boolean waited = wait != null && wait.endsWith(this.getUUID().toString());
            if (player != null && !waited && !nolan.bloxpiece.ability.Kame.exiting(ownerId, this.getUUID())) {
                this.discard();
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RISE, -1);
    }

    /** Visée par le curseur : un autre joueur peut faire clic droit dessus (demande d'entrée : KameVisits, UseEntityCallback). */
    @Override
    public boolean isPickable() {
        return true;
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
        return distance < 96.0 * 96.0;
    }
}
