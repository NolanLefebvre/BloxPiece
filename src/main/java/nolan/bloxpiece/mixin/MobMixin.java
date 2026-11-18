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
 * visée ne se déclenche. Effacer la cible reste permis.
 */
@Mixin(Mob.class)
public abstract class MobMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void bloxpiece$noTargetWhenCuffed(LivingEntity target, CallbackInfo info) {
        if (target != null && Handcuffs.isCuffed((Mob) (Object) this)) {
            info.cancel();
        }
    }
}
