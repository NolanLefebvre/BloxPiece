package nolan.bloxpiece.mob;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Base des combattants humanoïdes armés du mod (pirates, Marines) : chaque FACTION se bat ensemble.
 *
 * <ul>
 *   <li>Arme toujours en main (remise automatiquement, même pour un mob posé par la génération d'une structure).</li>
 *   <li>Synergie de groupe : dès qu'un combattant est blessé, tous ses ALLIÉS ({@link #isAlly}) à moins de {@link #ALERT_RADIUS}
 *       blocs prennent l'attaquant pour cible.</li>
 *   <li>Poursuite au pathfinding vanilla (contourne les murs, saute les marches, évite lave, vide et feu). Chaque variante a
 *       son comportement de combat ({@link #combatGoal()}) ; chaque faction ses cibles ({@link #registerTargets()}).</li>
 *   <li>Visée (bras levé) et coup de feu (recul de l'arme) synchronisés pour les animations.</li>
 * </ul>
 * Tout le reste (Haki des Rois, menottes, pouvoirs des fruits...) s'applique comme à n'importe quel mob.
 */
public abstract class ArmedHumanoid extends BloxMob {

    private static final double ALERT_RADIUS = 24.0;

    /** Le combattant vise (bras levé vers la cible), synchronisé serveur -> client pour l'animation. */
    private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(ArmedHumanoid.class, EntityDataSerializers.BOOLEAN);
    /** Âge (tickCount) du dernier coup de feu : animation de recul. */
    private static final EntityDataAccessor<Integer> SHOT_AT = SynchedEntityData.defineId(ArmedHumanoid.class, EntityDataSerializers.INT);

    protected ArmedHumanoid(EntityType<? extends ArmedHumanoid> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AIMING, false);
        builder.define(SHOT_AT, -1000);
    }

    public boolean isAiming() {
        return this.entityData.get(AIMING);
    }

    public void setAiming(boolean aiming) {
        if (this.isAiming() != aiming) {
            this.entityData.set(AIMING, aiming);
        }
    }

    /** Coup de feu : le recul de l'arme est joué par le client. */
    public void markShot() {
        this.entityData.set(SHOT_AT, this.tickCount);
    }

    /** Ticks écoulés depuis le dernier coup de feu (grand si aucun). */
    public int ticksSinceShot() {
        return this.tickCount - this.entityData.get(SHOT_AT);
    }

    /**
     * L'arme se range hors combat (fusil dans le dos) : elle n'est en main que pendant un combat ({@link #wantsWeaponInHand}).
     */
    public boolean stowsWeapon() {
        return false;
    }

    /** Arme en main en ce moment ? Toujours, sauf pour une arme qui se range : seulement avec une cible. */
    protected boolean wantsWeaponInHand() {
        return !this.stowsWeapon() || this.getTarget() != null || this.isAiming();
    }

    /** Arme tenue à deux mains (fusil) : la pose de visée lève les deux bras. */
    public boolean twoHandedWeapon() {
        return false;
    }

    /**
     * Toujours droitiers. Minecraft rend gaucher ~5 % des mobs au hasard : l'arme passait alors dans la main gauche, alors que la
     * pose de visée lève le bras droit et que la balle part du côté droit. Forcé ici pour corriger aussi les mobs déjà présents.
     */
    @Override
    public boolean isLeftHanded() {
        return false;
    }

    /** Même faction que ce combattant (ils s'entraident et ne se tirent pas dessus). */
    public abstract boolean isAlly(LivingEntity other);

    /** L'arme de cette variante. */
    protected abstract ItemStack weapon();

    /** Le comportement de combat de cette variante. */
    protected abstract Goal combatGoal();

    /** Les cibles de cette faction (targetSelector). */
    protected abstract void registerTargets();

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, this.combatGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.registerTargets();
    }

    /** Le pathfinding ne descend jamais plus d'UN bloc d'un coup (escaliers et échelles plutôt que sauter dans le vide). */
    @Override
    public int getMaxFallDistance() {
        return 1;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            ItemStack held = this.getMainHandItem();
            if (this.wantsWeaponInHand()) {
                if (held.isEmpty()) {
                    this.setItemSlot(EquipmentSlot.MAINHAND, this.weapon()); // sort l'arme (ou la remet si elle manque)
                }
            } else if (!held.isEmpty() && held.is(this.weapon().getItem())) {
                this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); // hors combat : l'arme se range (dans le dos)
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && source.getEntity() instanceof LivingEntity attacker && !this.isAlly(attacker)
                && !(attacker instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            // Tous les alliés proches se retournent contre l'attaquant
            for (ArmedHumanoid mate : level.getEntitiesOfClass(ArmedHumanoid.class, this.getBoundingBox().inflate(ALERT_RADIUS),
                    p -> p != this && p.isAlive() && this.isAlly(p))) {
                mate.setTarget(attacker);
            }
        }
        return hurt;
    }
}
