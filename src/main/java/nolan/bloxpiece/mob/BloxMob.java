package nolan.bloxpiece.mob;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.progression.Progression;

/**
 * Base de tous les ennemis du mod : un monstre hostile classique (poursuite + corps-à-corps)
 * dont les stats et la récompense d'XP viennent d'un BloxMobStats.
 *
 * Pour créer un nouvel ennemi : étendre cette classe, déclarer ses stats, l'enregistrer dans ModEntities,
 * lui donner un renderer (client) et son nom dans la lang. Rien d'autre à brancher : l'XP est donnée toute seule.
 */
public abstract class BloxMob extends Monster {

    protected BloxMob(EntityType<? extends BloxMob> type, Level level) {
        super(type, level);
        // L'XP des mobs du mod passe par Progression : pas d'orbes d'expérience vanilla.
        this.xpReward = 0;
    }

    /** Les stats de ce mob (aussi utilisées pour enregistrer ses attributs). */
    public abstract BloxMobStats stats();

    /** Comportement hostile classique : nage, poursuit et frappe le joueur, se venge, se balade et regarde autour. */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /**
     * Donne l'XP BloxPiece au joueur responsable de la mort (appelé par ModEvents à la mort du mob).
     * Responsable = le joueur qui porte le coup fatal (y compris par projectile / explosion),
     * ou à défaut le joueur qui l'a blessé dans les dernières secondes. Aucun joueur = aucune XP.
     */
    public void rewardKiller(DamageSource source) {
        ServerPlayer killer = findKiller(source);
        if (killer != null) {
            Progression.addXp(killer, stats().xpReward());
        }
    }

    private ServerPlayer findKiller(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        if (this.getLastHurtByMob() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }
}
