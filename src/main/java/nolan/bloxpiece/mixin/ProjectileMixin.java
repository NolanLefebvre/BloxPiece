package nolan.bloxpiece.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

import nolan.bloxpiece.fruit.LogiaIntangibility;

/**
 * Passif Logia du fruit du Feu : les projectiles (flèches, tridents, boules de neige...) TRAVERSENT le corps du joueur au lieu de
 * le toucher. Projectile.canHitEntity décide si un projectile peut entrer en collision avec une entité (les flèches et les autres
 * projectiles vanilla passent par lui) : on répond "non" pour un joueur Logia, donc ni dégâts, ni rebond, ni flèche plantée.
 * Côté serveur uniquement (c'est lui qui décide des collisions). Les projectiles des attaques du mod ne sont pas concernés.
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    @Inject(method = "canHitEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void bloxpiece$logiaPassThrough(Entity target, CallbackInfoReturnable<Boolean> info) {
        if (target instanceof ServerPlayer player && LogiaIntangibility.letsThrough(player, (Projectile) (Object) this)) {
            info.setReturnValue(false);
        }
    }
}
