package nolan.bloxpiece.mob;

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

/**
 * Base de tous les ennemis du mod : un monstre hostile classique (poursuite + corps-à-corps)
 * dont les stats viennent d'un BloxMobStats (l'XP à sa mort : MobXp, calculée automatiquement).
 *
 * Pour créer un nouvel ennemi : étendre cette classe, déclarer ses stats, l'enregistrer dans ModEntities,
 * lui donner un renderer (client) et son nom dans la lang. Rien d'autre à brancher : l'XP BloxPiece à sa mort est calculée
 * et donnée toute seule (progression/MobXp, d'après ses PV, ses dégâts et son rôle).
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

}
