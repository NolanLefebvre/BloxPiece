package nolan.bloxpiece.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Base des pirates armés (Sabreur, Tireur, Capitaine) : un équipage HOSTILE qui se bat ensemble (voir ArmedHumanoid). Il attaque
 * les joueurs à vue, et les Marines qu'il croise.
 */
public abstract class ArmedPirate extends ArmedHumanoid {

    protected ArmedPirate(EntityType<? extends ArmedPirate> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isAlly(LivingEntity other) {
        return other instanceof ArmedPirate;
    }

    @Override
    protected void registerTargets() {
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ArmedPirate.class));
        // joueurs ET Marines à vue, même priorité : le pirate s'en prend à l'ennemi qu'il repère (vrais affrontements entre factions)
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, ArmedMarine.class, true));
    }
}
