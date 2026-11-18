package nolan.bloxpiece.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.progression.LevelScaling;
import nolan.bloxpiece.progression.Progression;

/**
 * Progression : les dégâts des attaques de fruit du démon sont multipliés selon le niveau du lanceur (LevelScaling), en un seul
 * endroit pour toutes les attaques de tous les fruits (Feu, Bouddha, Glace...), y compris celles ajoutées plus tard.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float bloxpiece$scaleFruitDamage(float amount, @Local(argsOnly = true) DamageSource source) {
        // Haki de l'Armement sur tout le corps : dégâts subis réduits
        amount *= nolan.bloxpiece.haki.HakiManager.incomingDamageMultiplier((LivingEntity) (Object) this, source);
        Player attacker = LevelScaling.fruitAttacker(source);
        if (attacker == null || attacker == (Object) this) {
            return amount;
        }
        return amount * LevelScaling.damageMultiplier(Progression.get(attacker).level());
    }
}
