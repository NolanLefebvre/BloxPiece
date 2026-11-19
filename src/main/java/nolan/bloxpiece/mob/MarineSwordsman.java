package nolan.bloxpiece.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.registry.ModItems;

/** Marine Épéiste : au corps-à-corps avec le sabre réglementaire de la Marine (comme le Pirate Sabreur, un peu plus robuste). */
public class MarineSwordsman extends ArmedMarine {

    public static final BloxMobStats STATS = new BloxMobStats(
            26.0,  // points de vie
            5.0,   // dégâts par coup (sans le sabre)
            0.30,  // vitesse
            32.0,  // détection / poursuite (blocs)
            13     // XP BloxPiece
    );

    public MarineSwordsman(EntityType<? extends MarineSwordsman> type, Level level) {
        super(type, level);
    }

    @Override
    public BloxMobStats stats() {
        return STATS;
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(ModItems.MARINE_SABER);
    }

    @Override
    protected Goal combatGoal() {
        return new MeleeAttackGoal(this, 1.3, true);
    }
}
