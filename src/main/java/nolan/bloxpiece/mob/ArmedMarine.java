package nolan.bloxpiece.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Base des Marines (Épéiste, Fusilier) : la Marine attaque à vue les PIRATES et les JOUEURS (même priorité), et toute l'escouade
 * proche riposte quand l'un d'eux est blessé (voir ArmedHumanoid). Un mob menotté n'est jamais pris pour cible (MobMixin).
 * Sensible comme tout mob au Haki des Rois, aux menottes et aux pouvoirs des fruits.
 */
public abstract class ArmedMarine extends ArmedHumanoid {

    protected ArmedMarine(EntityType<? extends ArmedMarine> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isAlly(LivingEntity other) {
        return other instanceof ArmedMarine;
    }

    @Override
    protected void registerTargets() {
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ArmedMarine.class));
        // joueurs et pirates (Sabreur, Tireur, Capitaine : tous des ArmedPirate) attaqués à vue, dans le rayon de détection du Marine
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, ArmedPirate.class, true));
    }
}
