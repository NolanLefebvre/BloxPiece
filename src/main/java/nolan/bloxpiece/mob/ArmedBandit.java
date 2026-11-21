package nolan.bloxpiece.mob;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.registry.ModAttachments;

/**
 * Base des bandits (camp de bandits) : une bande HOSTILE qui se bat ensemble (même système que les pirates et les Marines, voir
 * ArmedHumanoid : arme toujours en main, poursuite, toute la bande riposte quand l'un d'eux est blessé).
 * <ul>
 *   <li>Cibles : les joueurs ET tous les mobs du mod qu'il voit (pirates, Marines, Morgan, Gol D. Roger... tout BloxMob), sauf
 *       les autres bandits ({@link BanditHuntGoal}).</li>
 *   <li>Territoire : il garde son camp (l'endroit où il est apparu, enregistré avec lui : ModAttachments.BANDIT_HOME). Sans
 *       cible, il n'erre pas à plus de {@link #IDLE_RADIUS} blocs et revient s'il s'en écarte ; en combat, il ne poursuit pas
 *       au-delà de {@link #CHASE_RADIUS} blocs du camp : il abandonne sa cible et rentre ({@link ReturnToCampGoal}).</li>
 * </ul>
 */
public abstract class ArmedBandit extends ArmedHumanoid {

    public static final double IDLE_RADIUS = 10.0;
    public static final double CHASE_RADIUS = 24.0;
    /** Arrivé : assez près du camp pour s'arrêter. */
    private static final double HOME_REACHED = 4.0;

    protected ArmedBandit(EntityType<? extends ArmedBandit> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isAlly(LivingEntity other) {
        return other instanceof ArmedBandit;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new ReturnToCampGoal(this)); // avant le combat : il lâche une poursuite trop lointaine
    }

    @Override
    protected void registerTargets() {
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ArmedBandit.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(2, new BanditHuntGoal(this));
    }

    /** Le camp de ce bandit (enregistré à sa première mise à jour). */
    public BlockPos home() {
        BlockPos home = this.getAttached(ModAttachments.BANDIT_HOME);
        if (home == null && !this.level().isClientSide()) {
            home = this.blockPosition();
            this.setAttached(ModAttachments.BANDIT_HOME, home);
        }
        return home;
    }

    private double homeDistanceSqr() {
        BlockPos home = this.home();
        return home == null ? 0.0 : this.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount == 1) {
            this.home(); // enregistre le camp dès l'apparition
        }
    }

    /** Retour au camp : sans cible au-delà de IDLE_RADIUS, ou en poursuite au-delà de CHASE_RADIUS (cible abandonnée). */
    static final class ReturnToCampGoal extends Goal {

        private final ArmedBandit bandit;

        ReturnToCampGoal(ArmedBandit bandit) {
            this.bandit = bandit;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            double d = this.bandit.homeDistanceSqr();
            if (this.bandit.getTarget() != null) {
                return d > CHASE_RADIUS * CHASE_RADIUS;
            }
            return d > IDLE_RADIUS * IDLE_RADIUS;
        }

        @Override
        public boolean canContinueToUse() {
            return this.bandit.homeDistanceSqr() > HOME_REACHED * HOME_REACHED && !this.bandit.getNavigation().isDone();
        }

        @Override
        public void start() {
            this.bandit.setTarget(null); // trop loin du camp : il abandonne
            this.bandit.setAggressive(false);
            BlockPos home = this.bandit.home();
            this.bandit.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.1);
        }

        @Override
        public void tick() {
            this.bandit.setTarget(null); // il rentre au camp sans se laisser reprendre en chemin (pas d'aller-retour à la limite)
        }
    }

    /**
     * Chasse : la cible la plus proche parmi les mobs du mod (tout BloxMob vivant, sauf les bandits) qu'il voit, dans son rayon
     * de détection et pas trop loin du camp. Vérifié toutes les {@link #INTERVAL} ticks.
     */
    static final class BanditHuntGoal extends Goal {

        private static final int INTERVAL = 10;
        private final ArmedBandit bandit;
        private LivingEntity found;

        BanditHuntGoal(ArmedBandit bandit) {
            this.bandit = bandit;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (this.bandit.getTarget() != null || this.bandit.getRandom().nextInt(INTERVAL) != 0) {
                return false;
            }
            double range = this.bandit.getAttributeValue(Attributes.FOLLOW_RANGE);
            BlockPos home = this.bandit.home();
            List<BloxMob> near = this.bandit.level().getEntitiesOfClass(BloxMob.class, this.bandit.getBoundingBox().inflate(range, 6.0, range),
                    m -> m.isAlive() && !(m instanceof ArmedBandit) && !m.isInvisible()
                            && (home == null || m.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5)
                                    < CHASE_RADIUS * CHASE_RADIUS)
                            && this.bandit.getSensing().hasLineOfSight(m));
            this.found = null;
            double best = Double.MAX_VALUE;
            for (BloxMob m : near) {
                double d = m.distanceToSqr(this.bandit);
                if (d < best && d <= range * range) {
                    best = d;
                    this.found = m;
                }
            }
            return this.found != null;
        }

        @Override
        public boolean canContinueToUse() {
            return false; // une fois la cible choisie, le combat (combatGoal) s'en occupe
        }

        @Override
        public void start() {
            this.bandit.setTarget(this.found);
        }
    }
}
