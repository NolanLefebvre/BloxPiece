package nolan.bloxpiece.fx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModEntities;

/**
 * Un météore de la Pluie de météores (fruit du Feu). C'est toujours une boule de feu vanilla (LargeFireball : même hitbox 1x1, même
 * vitesse, mêmes dégâts directs, peut toujours être renvoyée d'un coup), mais avec son PROPRE type d'entité : le client l'affiche
 * avec le modèle 3D MeteorModel (roche volcanique + enveloppe de flammes) au lieu de l'item boule de feu en 2D.
 *
 * La boule de feu vanilla garde sa puissance d'explosion dans un champ privé, rempli seulement par son autre constructeur (qui impose
 * le type vanilla). Ce météore garde donc sa propre puissance et refait à l'identique ce que fait LargeFireball à l'impact : dégâts
 * directs, réaction du bloc touché, puis explosion de même puissance, qui casse les blocs si la règle mobGriefing est active.
 * Les effets en plus (traînée, onde de choc, dégâts de zone, mise à feu) restent ceux de FireEffects (Kind.METEOR), inchangés.
 */
public class Meteor extends LargeFireball {

    private int blastPower = 1;

    public Meteor(EntityType<? extends Meteor> type, Level level) {
        super(type, level);
    }

    /** Fait apparaître un météore à {@code pos}, lancé à la vitesse {@code velocity}, avec une explosion de puissance {@code power}. */
    public static Meteor spawn(ServerLevel level, LivingEntity owner, Vec3 pos, Vec3 velocity, int power) {
        Meteor meteor = new Meteor(ModEntities.METEOR, level);
        meteor.setOwner(owner);
        meteor.blastPower = power;
        meteor.setPos(pos.x, pos.y, pos.z);
        meteor.setDeltaMovement(velocity);
        level.addFreshEntity(meteor);
        return meteor;
    }

    @Override
    protected void onHit(HitResult hit) {
        // Comme LargeFireball : dégâts directs (ou réaction du bloc touché), puis explosion
        if (hit instanceof EntityHitResult entityHit) {
            this.onHitEntity(entityHit);
        } else if (hit instanceof BlockHitResult blockHit) {
            this.onHitBlock(blockHit);
        }
        if (this.level() instanceof ServerLevel level) {
            boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
            level.explode(this, this.getX(), this.getY(), this.getZ(), (float) this.blastPower, griefing, Level.ExplosionInteraction.MOB);
            this.discard();
        }
    }
}
