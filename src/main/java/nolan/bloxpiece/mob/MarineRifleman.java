package nolan.bloxpiece.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.mob.ai.GunnerAttackGoal;
import nolan.bloxpiece.registry.ModItems;

/**
 * Marine Fusilier : tient ses distances et tire au fusil réglementaire (mousquet long, à deux mains). Plus de portée et de dégâts
 * que le pistolet des pirates, mais une visée et un rechargement plus longs (voir GunnerAttackGoal.rifle).
 */
public class MarineRifleman extends ArmedMarine {

    public static final BloxMobStats STATS = new BloxMobStats(
            20.0,  // points de vie
            2.0,   // dégâts au corps-à-corps (il préfère tirer)
            0.27,  // vitesse
            44.0,  // détection : il repère de très loin
            15     // XP BloxPiece
    );

    public MarineRifleman(EntityType<? extends MarineRifleman> type, Level level) {
        super(type, level);
    }

    @Override
    public BloxMobStats stats() {
        return STATS;
    }

    /** Hors combat (patrouille, marche, repos) : fusil rangé en diagonale dans le dos ; il passe en main dès qu'il a une cible. */
    @Override
    public boolean stowsWeapon() {
        return true;
    }

    @Override
    public boolean twoHandedWeapon() {
        return true;
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(ModItems.MARINE_RIFLE);
    }

    @Override
    protected Goal combatGoal() {
        return GunnerAttackGoal.rifle(this);
    }
}
