package nolan.bloxpiece.progression;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Ce que le niveau du joueur (1 à {@link LevelCurve#MAX_LEVEL}) apporte : la puissance de ses attaques de fruit du démon et ses
 * points de vie maximum. Pour rééquilibrer, modifie uniquement les constantes de ce fichier.
 *
 * <h3>Dégâts des fruits</h3>
 * Les dégâts de base écrits dans chaque attaque (Hiken 30, Déflagration 60...) sont ceux d'un joueur de niveau ~28. Ils sont
 * multipliés par {@link #damageMultiplier} : x0,5 au niveau 1 (une attaque reste forte sans tuer d'un coup), x1 vers le niveau
 * 28, x2 au niveau 100. La courbe monte un peu plus vite au début (puissance 0,8) : chaque niveau compte, sans explosion à la fin.
 * Exemples pour Hiken (30) : niv. 1 = 15, niv. 10 = ~22, niv. 50 = ~41, niv. 100 = 60.
 *
 * <h3>Points de vie</h3>
 * 20 PV (10 cœurs) au niveau 1, +2 PV (1 cœur) par niveau : 38 PV au niveau 10, 118 au niveau 50, 218 au niveau 100.
 */
public final class LevelScaling {

    private static final float MIN_MULTIPLIER = 0.5F;
    private static final float MAX_MULTIPLIER = 2.0F;
    private static final double CURVE = 0.8;

    /** PV gagnés par niveau au-delà du niveau 1. */
    public static final double HEALTH_PER_LEVEL = 2.0;

    private LevelScaling() {
    }

    /** Multiplicateur des dégâts des attaques de fruit pour ce niveau. */
    public static float damageMultiplier(int level) {
        double t = Math.max(0.0, Math.min(1.0, (level - LevelCurve.START_LEVEL) / (double) (LevelCurve.MAX_LEVEL - LevelCurve.START_LEVEL)));
        return MIN_MULTIPLIER + (MAX_MULTIPLIER - MIN_MULTIPLIER) * (float) Math.pow(t, CURVE);
    }

    /** PV maximum ajoutés aux 20 PV de base pour ce niveau. */
    public static double bonusHealth(int level) {
        return Math.max(0, level - LevelCurve.START_LEVEL) * HEALTH_PER_LEVEL;
    }

    private static final Identifier HEALTH_MODIFIER = BloxPiece.id("level_health");

    /**
     * Met les PV maximum du joueur à jour d'après son niveau (appelé à chaque tick serveur, ne fait rien si c'est déjà bon).
     * Modificateur permanent : il est sauvegardé avec le joueur, donc sa vie n'est jamais rognée à la reconnexion. Le client voit
     * le nouveau maximum (attribut synchronisé par Minecraft). En perdant des niveaux, la vie est ramenée au nouveau maximum.
     */
    public static void applyHealth(ServerPlayer player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        double bonus = bonusHealth(Progression.get(player).level());
        AttributeModifier current = maxHealth.getModifier(HEALTH_MODIFIER);
        if (current != null && current.amount() == bonus) {
            return;
        }
        maxHealth.removeModifier(HEALTH_MODIFIER);
        if (bonus > 0) {
            maxHealth.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * Le joueur responsable de ces dégâts s'ils viennent d'une attaque de fruit du démon (null sinon) : l'attaquant est un joueur
     * qui a un fruit, et les dégâts sont d'un type utilisé par les attaques des fruits (feu, boules de feu, explosions, glace).
     * Les coups d'épée, de poing et les flèches d'un porteur de fruit ne sont PAS concernés.
     */
    public static Player fruitAttacker(DamageSource source) {
        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player player) || player.getAttached(ModAttachments.FRUIT) == null) {
            return null;
        }
        boolean fruitDamage = source.is(DamageTypes.FREEZE) || source.is(DamageTypes.FIREBALL)
                || source.is(DamageTypes.UNATTRIBUTED_FIREBALL) || source.is(DamageTypes.IN_FIRE)
                || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION);
        return fruitDamage ? player : null;
    }
}
