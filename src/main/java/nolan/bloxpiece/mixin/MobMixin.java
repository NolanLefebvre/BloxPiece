package nolan.bloxpiece.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import nolan.bloxpiece.handcuffs.Handcuffs;

/**
 * Menottes : un mob menotté ne peut plus prendre de cible, quelle que soit son IA (vanilla ou du mod : pirates). Toutes les
 * attaques (corps à corps, tir, visée) ont besoin d'une cible : aucune ne peut donc démarrer, et aucune animation d'attaque ou de
 * visée ne se déclenche. Effacer la cible reste permis. Un Marine ne cible pas non plus un mob menotté (prisonnier).
 */
@Mixin(Mob.class)
public abstract class MobMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void bloxpiece$noTargetWhenCuffed(LivingEntity target, CallbackInfo info) {
        if (target != null && (Handcuffs.isCuffed((Mob) (Object) this) || nolan.bloxpiece.haki.Unconscious.is((Mob) (Object) this))) {
            info.cancel();
        }
        // Un Marine n'attaque pas un pirate menotté (prisonnier) : il ne le prend jamais pour cible
        if (target != null && (Object) this instanceof nolan.bloxpiece.mob.ArmedMarine && Handcuffs.isCuffed(target)) {
            info.cancel();
        }
    }
}
