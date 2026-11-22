package nolan.bloxpiece.mob.ai;

import java.util.EnumSet;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Garde d'un lieu (même règle que le retour au camp des bandits, ArmedBandit.ReturnToCampGoal) : sans cible, le mob n'erre pas
 * à plus de {@code idleRadius} blocs de son poste et y revient s'il s'en écarte ; en combat, il ne poursuit pas au-delà de
 * {@code chaseRadius} blocs : il abandonne sa cible et rentre. Poste null : le but ne fait rien (mob ordinaire).
 * Utilisé par Gol D. Roger quand il est le boss d'une arène (GolDRoger.arenaHome).
 */
public final class ArenaHomeGoal extends Goal {

    /** Arrivé : assez près du poste pour s'arrêter. */
    private static final double HOME_REACHED = 3.0;

    private final PathfinderMob mob;
    private final Supplier<BlockPos> home;
    private final double idleRadius;
    private final double chaseRadius;

    public ArenaHomeGoal(PathfinderMob mob, Supplier<BlockPos> home, double idleRadius, double chaseRadius) {
        this.mob = mob;
        this.home = home;
        this.idleRadius = idleRadius;
        this.chaseRadius = chaseRadius;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    private double homeDistanceSqr() {
        BlockPos pos = this.home.get();
        return pos == null ? 0.0 : this.mob.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    @Override
    public boolean canUse() {
        if (this.home.get() == null) {
            return false;
        }
        double d = this.homeDistanceSqr();
        if (this.mob.getTarget() != null) {
            return d > this.chaseRadius * this.chaseRadius;
        }
        return d > this.idleRadius * this.idleRadius;
    }

    @Override
    public boolean canContinueToUse() {
        return this.home.get() != null && this.homeDistanceSqr() > HOME_REACHED * HOME_REACHED && !this.mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        this.mob.setTarget(null); // trop loin : il abandonne
        this.mob.setAggressive(false);
        BlockPos pos = this.home.get();
        this.mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.1);
    }

    @Override
    public void tick() {
        this.mob.setTarget(null); // il rentre sans se laisser reprendre en chemin (pas d'aller-retour à la limite)
    }
}
