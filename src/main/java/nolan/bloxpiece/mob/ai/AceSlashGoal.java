package nolan.bloxpiece.mob.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import nolan.bloxpiece.fx.AceSlash;
import nolan.bloxpiece.item.AceItem;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Gol D. Roger lance la Lame dévastatrice du Haki des Rois avec son épée Ace : exactement la même attaque que le joueur (même
 * charge, même frappe et même croissant : AceItem.strike, AceSlash), décidée par son IA côté serveur.
 * <ol>
 *   <li>Conditions : Ace en main, une cible vivante qu'il voit, entre {@link #MIN_RANGE} et {@link #MAX_RANGE} blocs, recharge
 *       terminée ({@link #COOLDOWN_MIN} à {@link #COOLDOWN_MAX} ticks entre deux), et un peu de hasard (pas à chaque occasion).</li>
 *   <li>Charge : il s'arrête, se tourne vers sa cible et lève l'épée ({@link #CHARGE_MIN} à {@link #CHARGE_MAX} ticks : plus ou
 *       moins chargée), avec les éclairs et la brume du Haki des Rois ; la diagonale est tirée au hasard, comme pour le joueur.</li>
 *   <li>Frappe : comme un relâchement du joueur ; il reste tourné vers sa cible jusqu'à la fin du geste, puis reprend ses coups
 *       d'épée normaux (MeleeAttackGoal).</li>
 * </ol>
 * Cible perdue pendant la charge : l'attaque est annulée (rien ne part).
 */
public class AceSlashGoal extends Goal {

    private static final double MIN_RANGE = 5.0;
    private static final double MAX_RANGE = 32.0;
    private static final int CHARGE_MIN = 15;
    private static final int CHARGE_MAX = 45;
    private static final int COOLDOWN_MIN = 200;
    private static final int COOLDOWN_MAX = 320;
    /** Durée du geste après le relâchement (levée minimale, frappe, retour). */
    private static final int AFTER_TICKS = (int) (AceSlash.RAISE_TICKS + AceSlash.STRIKE_TICKS + AceSlash.RECOVER_TICKS);

    private final Mob mob;
    private long nextUse;
    private int age;
    private int chargeTicks;
    private boolean released;

    public AceSlashGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean holdsAce() {
        return this.mob.getMainHandItem().getItem() instanceof AceItem;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive() || !this.holdsAce() || this.mob.level().getGameTime() < this.nextUse) {
            return false;
        }
        double distance = this.mob.distanceTo(target);
        return distance >= MIN_RANGE && distance <= MAX_RANGE && this.mob.getSensing().hasLineOfSight(target)
                && this.mob.getRandom().nextInt(20) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return this.released ? this.age < this.chargeTicks + AFTER_TICKS
                : this.mob.getTarget() != null && this.mob.getTarget().isAlive() && this.holdsAce();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        ServerLevel level = (ServerLevel) this.mob.level();
        this.age = 0;
        this.released = false;
        this.chargeTicks = CHARGE_MIN + this.mob.getRandom().nextInt(CHARGE_MAX - CHARGE_MIN + 1);
        this.mob.getNavigation().stop();
        // début de la charge : mêmes attachments que le joueur (synchronisés) : geste, éclairs, diagonale au hasard
        this.mob.setAttached(ModAttachments.ACE_CHARGE, (int) level.getGameTime());
        this.mob.setAttached(ModAttachments.ACE_REVERSE, level.getRandom().nextBoolean());
        level.playSound(null, this.mob.getX(), this.mob.getY() + 1.0, this.mob.getZ(),
                net.minecraft.sounds.SoundEvents.WARDEN_SONIC_CHARGE, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) this.mob.level();
        LivingEntity target = this.mob.getTarget();
        this.mob.getNavigation().stop();
        if (target != null) {
            this.mob.getLookControl().setLookAt(target, 60.0F, 60.0F);
            // tout le corps face à la cible (le geste et le croissant partent dans cette direction)
            this.mob.setYRot(this.mob.getYHeadRot());
            this.mob.yBodyRot = this.mob.getYHeadRot();
        }
        this.age++;
        if (!this.released) {
            AceItem.chargeTick(level, this.mob, this.age);
            if (this.age >= this.chargeTicks) {
                this.released = true;
                AceItem.strike(level, this.mob, this.mob.getMainHandItem(), this.age);
            }
        }
    }

    @Override
    public void stop() {
        if (!this.released) {
            this.mob.removeAttached(ModAttachments.ACE_CHARGE); // cible perdue pendant la charge : annulée
        }
        this.nextUse = this.mob.level().getGameTime() + COOLDOWN_MIN
                + this.mob.getRandom().nextInt(COOLDOWN_MAX - COOLDOWN_MIN + 1);
    }
}
