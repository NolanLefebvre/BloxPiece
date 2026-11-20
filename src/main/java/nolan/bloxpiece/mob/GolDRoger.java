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
 * attribut SCALE dans ModEntities), qui ne se fait pas repousser et ne disparaît jamais au loin. Son épée Ace toujours en
 * main (fourreau vide au côté gauche) ; il la laisse tomber à sa mort (table de butin). En combat, il lance aussi la Lame dévastatrice du Haki des Rois avec Ace (même attaque
 * que le joueur : AceSlashGoal). Résiste au Haki des Rois. Invocation : /summon bloxpiece:gol_d_roger.
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
        // Ace tombe à sa mort par sa table de butin (loot_table/entities/gol_d_roger.json), toujours, une seule fois : l'épée
        // tenue en main ne tombe jamais en plus (sinon elle pourrait tomber deux fois)
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
    }

    @Override
    public BloxMobStats stats() {
        return STATS;
    }

    /** Ace toujours en main, même hors combat (le fourreau reste au côté, vide). */
    @Override
    public boolean stowsWeapon() {
        return false;
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(ModItems.ACE); // son sabre légendaire
    }

    /** En plus de ses coups d'épée : la Lame dévastatrice du Haki des Rois avec Ace (AceSlashGoal), prioritaire quand elle part. */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new nolan.bloxpiece.mob.ai.AceSlashGoal(this));
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
