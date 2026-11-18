package nolan.bloxpiece.mob;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Les statistiques d'un mob du mod, regroupées au même endroit pour être faciles à modifier.
 * Chaque mob déclare les siennes (voir PirateSwordsman.STATS) : c'est la seule chose à changer pour l'équilibrer.
 *
 * @param maxHealth     points de vie (20 = 10 coeurs)
 * @param attackDamage  dégâts par attaque (2 = 1 coeur)
 * @param movementSpeed vitesse : 0.23 = zombie, 0.1 = vitesse de marche de base du joueur (voir PirateSwordsman)
 * @param followRange   distance (en blocs) à laquelle le mob repère et poursuit un joueur
 * @param xpReward      XP BloxPiece donnée au joueur qui tue le mob (PAS l'XP vanilla)
 */
public record BloxMobStats(double maxHealth, double attackDamage, double movementSpeed, double followRange, int xpReward) {

    /** Transforme les stats en attributs Minecraft (à enregistrer avec le type d'entité). */
    public AttributeSupplier.Builder toAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.FOLLOW_RANGE, followRange)
                .add(Attributes.STEP_HEIGHT, 1.0); // monte dalles, escaliers et rebords d'un bloc sans sauter
    }
}
