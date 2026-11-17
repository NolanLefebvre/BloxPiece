package nolan.bloxpiece.mob.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Combat du Pirate Sabreur : il RUSH sa cible (course plus rapide que la marche), puis une fois au contact il ne reste pas
 * planté : il tourne autour d'elle par petits pas de côté (strafe, sens qui change régulièrement) tout en gardant la pression,
 * et frappe vite ({@link #ATTACK_COOLDOWN} ticks entre deux coups).
 */
public class SwordsmanAttackGoal extends Goal {

    private static final double RUSH_SPEED = 1.35;
    private static final double ENGAGE_DISTANCE_SQR = 4.0 * 4.0;
    private static final int ATTACK_COOLDOWN = 12;

    private final Mob mob;
    private int cooldown;
    private int repath;
    private int sideTicks;
    private float side = 1.0F;

    public SwordsmanAttackGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.mob.setAggressive(true);
        this.repath = 0;
    }

    @Override
    public void stop() {
        this.mob.setAggressive(false);
        this.mob.getNavigation().stop();
        this.mob.getMoveControl().strafe(0.0F, 0.0F);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = this.mob.distanceToSqr(target);
        boolean visible = this.mob.getSensing().hasLineOfSight(target);

        if (distance > ENGAGE_DISTANCE_SQR || !visible) {
            // Poursuite : chemin recalculé souvent (la cible bouge), le pathfinding contourne les obstacles
            if (--this.repath <= 0) {
                this.mob.getNavigation().moveTo(target, RUSH_SPEED);
                this.repath = 4 + this.mob.getRandom().nextInt(4);
            }
        } else {
            // Au contact : pas de côté autour de la cible, en avançant
            this.mob.getNavigation().stop();
            if (--this.sideTicks <= 0) {
                this.side = -this.side;
                this.sideTicks = 15 + this.mob.getRandom().nextInt(25);
            }
            this.mob.getMoveControl().strafe(0.55F, this.side * 0.45F);
        }

        if (--this.cooldown <= 0 && visible && this.mob.isWithinMeleeAttackRange(target)) {
            this.mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            this.mob.doHurtTarget((ServerLevel) this.mob.level(), target);
            this.cooldown = ATTACK_COOLDOWN;
        }
    }
}
