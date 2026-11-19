package nolan.bloxpiece.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.registry.ModItems;

/**
 * Gol D. Roger, le Roi des Pirates (modèle 3D : RogerModels). Première version : un pirate armé (même IA que les autres, voir
 * ArmedPirate : il attaque joueurs et Marines à vue) mais bien plus fort, un peu plus grand (échelle 1,1 : hitbox et rendu,
 * attribut SCALE dans ModEntities), qui ne se fait pas repousser et ne disparaît jamais au loin. Son sabre Ace au fourreau hors
 * combat, en main dès qu'il a une cible. Résiste au Haki des Rois. Invocation : /summon bloxpiece:gol_d_roger.
 */
public class GolDRoger extends ArmedPirate {

    public static final BloxMobStats STATS = new BloxMobStats(
            400.0, // points de vie
            18.0,  // dégâts par coup (sans le sabre)
            0.30,  // vitesse
            40.0,  // détection / poursuite (blocs)
            500    // XP BloxPiece
    );

    public GolDRoger(EntityType<? extends GolDRoger> type, Level level) {
        super(type, level);
    }

    @Override
    public BloxMobStats stats() {
        return STATS;
    }

    /** Sabre au fourreau hors combat (la poignée dorée « sheathed_hilt » du modèle n'est affichée qu'à ce moment-là). */
    @Override
    public boolean stowsWeapon() {
        return true;
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(ModItems.ACE); // son sabre légendaire
    }

    @Override
    protected Goal combatGoal() {
        return new MeleeAttackGoal(this, 1.25, true);
    }

    /** Un personnage unique : jamais retiré du monde quand les joueurs s'éloignent. */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
