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
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Base des pirates armés (Sabreur, Tireur) : un équipage qui se bat ensemble.
 *
 * <ul>
 *   <li>Arme toujours en main (remise automatiquement, même pour un pirate posé par la génération d'un navire).</li>
 *   <li>Synergie de groupe : dès qu'un pirate est blessé, tous les pirates armés à moins de {@link #ALERT_RADIUS} blocs prennent
 *       l'attaquant pour cible.</li>
 *   <li>Poursuite : le pathfinding vanilla contourne les murs, saute les marches d'un bloc et évite la lave, le vide, le feu et
 *       les blocs dangereux. Chaque variante a son propre comportement de combat ({@link #combatGoal()}).</li>
 * </ul>
 */
public abstract class ArmedPirate extends BloxMob {

    private static final double ALERT_RADIUS = 24.0;

    /** Le pirate vise (bras droit levé vers la cible), synchronisé serveur → client pour l'animation. */
    private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(ArmedPirate.class, EntityDataSerializers.BOOLEAN);

    protected ArmedPirate(EntityType<? extends ArmedPirate> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AIMING, false);
    }

    public boolean isAiming() {
        return this.entityData.get(AIMING);
    }

    public void setAiming(boolean aiming) {
        if (this.isAiming() != aiming) {
            this.entityData.set(AIMING, aiming);
        }
    }

    /**
     * Les pirates armés sont toujours droitiers. Minecraft rend gaucher ~5 % des mobs au hasard : l'arme passait alors dans la main
     * gauche, alors que la pose de visée (ArmedPirateModel) lève le bras droit et que la balle part du côté droit (GunnerAttackGoal).
     * Forcé ici (et non à l'apparition) pour corriger aussi les pirates gauchers déjà présents dans les mondes existants.
     */
    @Override
    public boolean isLeftHanded() {
        return false;
    }

    /** L'arme de cette variante (sabre, pistolet). */
    protected abstract ItemStack weapon();

    /** Le comportement de combat de cette variante. */
    protected abstract Goal combatGoal();

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, this.combatGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ArmedPirate.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /**
     * Le pathfinding ne descend jamais plus d'UN bloc d'un coup : les pirates ne sautent plus par-dessus bord ni dans la cale,
     * ils prennent les escaliers et les échelles (vanilla : 3 blocs, plus en poursuite).
     */
    @Override
    public int getMaxFallDistance() {
        return 1;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.getMainHandItem().isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, this.weapon());
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof ArmedPirate)
                && !(attacker instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            // Tout l'équipage proche se retourne contre l'attaquant
            for (ArmedPirate mate : level.getEntitiesOfClass(ArmedPirate.class, this.getBoundingBox().inflate(ALERT_RADIUS),
                    p -> p != this && p.isAlive())) {
                mate.setTarget(attacker);
            }
        }
        return hurt;
    }
}
