package nolan.bloxpiece.mob.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.PirateBullet;
import nolan.bloxpiece.handcuffs.Handcuffs;
import nolan.bloxpiece.mob.ArmedHumanoid;

/**
 * Combat du Pirate Tireur. Tous les déplacements passent par le PathNavigation (jamais de strafe ni de setDeltaMovement) :
 * <ul>
 *   <li>trop loin ou cible cachée : il se rapproche ;</li>
 *   <li>trop près (moins de {@link #MIN_DISTANCE} blocs) : il recule vers un point éloigné de la cible (DefaultRandomPos) ;</li>
 *   <li>à bonne portée, rechargé et cible visible : il s'arrête, se tourne (corps ET tête) vers la cible, lève le bras
 *       ({@link ArmedHumanoid#setAiming}) pendant le temps de visée de son arme, puis tire seulement si la ligne de vue est libre et
 *       que son regard est aligné sur la cible. Il garde le bras levé {@link #AFTER_SHOT_TICKS} ticks après le coup, puis le
 *       baisse pendant le rechargement.</li>
 * </ul>
 */
public class GunnerAttackGoal extends Goal {

    private static final double MIN_DISTANCE = 6.0;
    private static final int AFTER_SHOT_TICKS = 8;

    // Réglages de l'arme (pistolet des pirates par défaut ; fusil de la Marine : voir rifle)
    private final double maxDistance;
    private final double fireRange;
    private final int aimTicks;
    private final int reloadTicks;
    private final float bulletDamage;
    private final double bulletSpeed;
    private final double inaccuracy;
    /** Bout du canon devant le tireur (blocs) : plus loin pour un fusil long. */
    private final double muzzleForward;
    /** Écart maximal (cosinus, ~10°) entre le regard du pirate et la direction de la cible pour autoriser le tir. */
    private static final double MIN_ALIGNMENT = Math.cos(Math.toRadians(10.0));

    private final ArmedHumanoid mob;
    private int reload;
    private int aim;
    private int afterShot;
    private int repath;

    /** Pistolet à silex (Pirate Tireur). */
    public GunnerAttackGoal(ArmedHumanoid mob) {
        this(mob, 16.0, 24.0, 15, 40, 5.0F, 2.4, 0.02, 0.7);
    }

    /** Fusil de la Marine : plus de portée et de dégâts, visée et rechargement plus longs, très précis. */
    public static GunnerAttackGoal rifle(ArmedHumanoid mob) {
        return new GunnerAttackGoal(mob, 22.0, 34.0, 22, 60, 8.0F, 3.2, 0.01, 1.4);
    }

    private GunnerAttackGoal(ArmedHumanoid mob, double maxDistance, double fireRange, int aimTicks, int reloadTicks,
                             float bulletDamage, double bulletSpeed, double inaccuracy, double muzzleForward) {
        this.mob = mob;
        this.maxDistance = maxDistance;
        this.fireRange = fireRange;
        this.aimTicks = aimTicks;
        this.reloadTicks = reloadTicks;
        this.bulletDamage = bulletDamage;
        this.bulletSpeed = bulletSpeed;
        this.inaccuracy = inaccuracy;
        this.muzzleForward = muzzleForward;
        this.reload = reloadTicks / 2;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (Handcuffs.isCuffed(this.mob)) {
            return false; // menotté : ni visée ni tir
        }
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
        this.mob.setAiming(false);
        this.mob.getNavigation().stop();
        this.aim = 0;
        this.afterShot = 0;
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
        double distance = Math.sqrt(this.mob.distanceToSqr(target));
        boolean visible = this.mob.getSensing().hasLineOfSight(target);

        if (this.reload > 0) {
            this.reload--;
        }
        if (this.afterShot > 0) {
            // Juste après le coup de feu : il reste en joue un instant
            this.afterShot--;
            this.mob.getNavigation().stop();
            this.faceTarget(target);
            this.mob.setAiming(this.afterShot > 0);
            return;
        }

        boolean canAim = this.reload <= 0 && visible && distance <= this.fireRange && distance >= MIN_DISTANCE * 0.5;
        if (canAim) {
            // Visée : immobile, corps et tête tournés vers la cible, bras levé
            this.mob.getNavigation().stop();
            this.faceTarget(target);
            this.mob.setAiming(true);
            if (++this.aim >= this.aimTicks && this.mob.hasLineOfSight(target) && this.isAligned(target)) {
                this.shoot(target);
                this.reload = this.reloadTicks;
                this.aim = 0;
                this.afterShot = AFTER_SHOT_TICKS;
            }
            return;
        }

        // Rechargement ou déplacement : bras baissé
        this.aim = 0;
        this.mob.setAiming(false);
        this.move(target, distance, visible);
    }

    private void move(LivingEntity target, double distance, boolean visible) {
        if (--this.repath > 0) {
            return;
        }
        this.repath = 10;
        if (!visible || distance > this.maxDistance) {
            this.mob.getNavigation().moveTo(target, 1.1);
        } else if (distance < MIN_DISTANCE) {
            Vec3 away = DefaultRandomPos.getPosAway(this.mob, 10, 4, target.position());
            if (away != null) {
                this.mob.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
            }
        } else {
            this.mob.getNavigation().stop();
        }
    }

    /** Tourne immédiatement le corps, la tête et le regard du pirate vers le centre de la cible. */
    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - this.mob.getX();
        double dz = target.getZ() - this.mob.getZ();
        double dy = target.getY(0.5) - this.mob.getEyeY();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
        this.mob.setYRot(yaw);
        this.mob.setYBodyRot(yaw);
        this.mob.setYHeadRot(yaw);
        this.mob.setXRot(pitch);
    }

    /** Le regard du pirate pointe-t-il bien vers la cible (moins de ~10° d'écart) ? */
    private boolean isAligned(LivingEntity target) {
        Vec3 toTarget = new Vec3(target.getX(), target.getY(0.5), target.getZ()).subtract(this.mob.getEyePosition()).normalize();
        return this.mob.getViewVector(1.0F).dot(toTarget) >= MIN_ALIGNMENT;
    }

    /** Bout du canon : devant le pirate, côté main droite, un peu sous les yeux. */
    private Vec3 muzzle() {
        double yaw = Math.toRadians(this.mob.getYRot());
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        return this.mob.getEyePosition().add(0.0, -0.3, 0.0).add(right.scale(0.35)).add(forward.scale(this.muzzleForward));
    }

    private void shoot(LivingEntity target) {
        ServerLevel level = (ServerLevel) this.mob.level();
        Vec3 from = this.muzzle();
        Vec3 dir = new Vec3(target.getX() - from.x, target.getY(0.5) - from.y, target.getZ() - from.z).normalize().add(
                this.mob.getRandom().nextGaussian() * this.inaccuracy,
                this.mob.getRandom().nextGaussian() * this.inaccuracy,
                this.mob.getRandom().nextGaussian() * this.inaccuracy);
        PirateBullet.fire(level, this.mob, from, dir.normalize(), this.bulletSpeed, this.bulletDamage);
        this.mob.markShot(); // recul de l'arme (animation)
    }
}
