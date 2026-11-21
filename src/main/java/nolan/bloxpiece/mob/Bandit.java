package nolan.bloxpiece.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Bandit : membre d'un camp de bandits, à l'épée (fauchon 3D de bandit : ModItems.BANDIT_SWORD). Capuche, foulard sur le visage, gilet de cuir (modèle 3D :
 * BanditModels.bandit, 4 variantes de couleurs). Au corps-à-corps comme le Pirate Sabreur (MeleeAttackGoal vanilla).
 */
public class Bandit extends ArmedBandit {

    public static final BloxMobStats STATS = new BloxMobStats(
            22.0,  // points de vie
            3.0,   // dégâts par coup (sans l'épée)
            0.30,  // vitesse
            28.0,  // détection / poursuite (blocs)
            10     // XP BloxPiece
    );

    public Bandit(EntityType<? extends Bandit> type, Level level) {
        super(type, level);
    }

    @Override
    public BloxMobStats stats() {
        return STATS;
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(nolan.bloxpiece.registry.ModItems.BANDIT_SWORD);
    }

    @Override
    protected Goal combatGoal() {
        return new MeleeAttackGoal(this, 1.3, true);
    }
}
