package nolan.bloxpiece.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.registry.ModItems;

/** Pirate Sabreur : rapide, au corps-à-corps avec un sabre pirate (MeleeAttackGoal vanilla : poursuite au pathfinding, course x1,3). */
public class PirateSwordsman extends ArmedPirate {

    public static final BloxMobStats STATS = new BloxMobStats(
            24.0,  // points de vie
            5.0,   // dégâts par coup (sans le sabre)
            0.30,  // vitesse : bien plus vif qu'un zombie (0,23)
            32.0   // détection / poursuite (blocs)
    );

    public PirateSwordsman(EntityType<? extends PirateSwordsman> type, Level level) {
        super(type, level);
    }

    @Override
    public BloxMobStats stats() {
        return STATS;
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(ModItems.PIRATE_SABER);
    }

    @Override
    protected Goal combatGoal() {
        return new MeleeAttackGoal(this, 1.3, true);
    }
}
