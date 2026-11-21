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
 * que le joueur : AceSlashGoal). À 50 % de vie, il libère son Haki des Rois, en bien plus puissant que le joueur
 * (RogerConquerorGoal). Résiste au Haki des Rois. Invocation : /summon bloxpiece:gol_d_roger.
 */
public class GolDRoger extends ArmedPirate {

    public static final BloxMobStats STATS = new BloxMobStats(
            400.0, // points de vie
            18.0,  // dégâts par coup (sans le sabre)
            0.30,  // vitesse
            40.0,  // détection / poursuite (blocs)
            500    // XP BloxPiece
    );

    /** Libération du Haki des Rois à 50 % de vie : déjà faite (une seule fois), et à lancer (RogerConquerorGoal). */
    private boolean conquerorUsed;
    private boolean conquerorPending;

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
        this.goalSelector.addGoal(0, new nolan.bloxpiece.mob.ai.RogerConquerorGoal(this)); // libération du Haki des Rois
    }

    /**
     * Passage sous 50 % de vie PENDANT LE COMBAT (vie avant le coup au-dessus du seuil, après le coup en dessous, toujours en
     * vie) : il libère son Haki des Rois (RogerConquerorGoal), une seule fois. Plusieurs coups autour du seuil : une seule
     * libération. Apparu déjà sous 50 % : jamais (le seuil n'est pas franchi).
     */
    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source,
                              float amount) {
        float half = this.getMaxHealth() * 0.5F;
        float before = this.getHealth();
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && !this.conquerorUsed && before > half && this.getHealth() <= half && this.isAlive()) {
            this.conquerorUsed = true;
            this.conquerorPending = true;
        }
        return hurt;
    }

    public boolean conquerorPending() {
        return this.conquerorPending;
    }

    public void clearConquerorPending() {
        this.conquerorPending = false;
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
