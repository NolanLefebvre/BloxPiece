package nolan.bloxpiece.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.registry.ModItems;

/**
 * Capitaine pirate : le chef de l'équipage, dans sa cabine du navire. Grand chapeau, grande cape et grand sabre (3D, voir
 * PirateGearModels.captain). Très résistant et frappe fort, mais plus lent que ses sabreurs. Comme tout pirate armé, il ameute
 * l'équipage proche quand il est attaqué.
 */
public class PirateCaptain extends ArmedPirate {

    public static final BloxMobStats STATS = new BloxMobStats(
            90.0,  // points de vie : un mini-boss
            8.0,   // dégâts par coup (sans le sabre)
            0.27,  // vitesse
            32.0,  // détection / poursuite (blocs)
            80     // XP BloxPiece
    );

    public PirateCaptain(EntityType<? extends PirateCaptain> type, Level level) {
        super(type, level);
    }

    @Override
    public BloxMobStats stats() {
        return STATS;
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(ModItems.CAPTAIN_SABER);
    }

    @Override
    protected Goal combatGoal() {
        return new MeleeAttackGoal(this, 1.2, true);
    }
}
