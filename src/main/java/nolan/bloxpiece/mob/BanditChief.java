package nolan.bloxpiece.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Chef des bandits : le meneur d'un camp (un par camp). Bandana noir, barbe, cicatrice, manteau de fourrure (modèle 3D :
 * BanditModels.chief), un peu plus grand que ses hommes (échelle 1,1 : hitbox et rendu, attribut SCALE dans ModEntities).
 * Grand dao 3D du chef (garde en S, pommeau en anneau à gland rouge : ModItems.BANDIT_CHIEF_SWORD), robuste et dur à repousser ; comme tous les bandits, il ameute la bande quand il est attaqué.
 */
public class BanditChief extends ArmedBandit {

    public static final BloxMobStats STATS = new BloxMobStats(
            70.0,  // points de vie : un petit chef
            5.0,   // dégâts par coup (sans l'épée)
            0.28,  // vitesse
            32.0,  // détection / poursuite (blocs)
            50     // XP BloxPiece
    );

    public BanditChief(EntityType<? extends BanditChief> type, Level level) {
        super(type, level);
    }

    @Override
    public BloxMobStats stats() {
        return STATS;
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(nolan.bloxpiece.registry.ModItems.BANDIT_CHIEF_SWORD);
    }

    @Override
    protected Goal combatGoal() {
        return new MeleeAttackGoal(this, 1.25, true);
    }
}
