package nolan.bloxpiece.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.mob.ai.GunnerAttackGoal;
import nolan.bloxpiece.registry.ModItems;

/** Pirate Tireur : garde ses distances et tire au pistolet à silex (voir GunnerAttackGoal). */
public class PirateGunner extends ArmedPirate {

    public static final BloxMobStats STATS = new BloxMobStats(
            18.0,  // points de vie : plus fragile que le sabreur
            2.0,   // dégâts au corps-à-corps (il préfère tirer)
            0.28,  // vitesse
            40.0   // détection : il repère de loin
    );

    public PirateGunner(EntityType<? extends PirateGunner> type, Level level) {
        super(type, level);
    }

    @Override
    public BloxMobStats stats() {
        return STATS;
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(ModItems.FLINTLOCK);
    }

    @Override
    protected Goal combatGoal() {
        return new GunnerAttackGoal(this);
    }
}
