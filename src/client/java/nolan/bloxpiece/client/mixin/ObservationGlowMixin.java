package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import nolan.bloxpiece.registry.ModAttachments;

/**
 * Haki de l'Observation : tant qu'il est actif (attachment HAKI_OBSERVATION, synchronisé), le joueur voit les créatures vivantes
 * autour de lui (à moins de {@link #RANGE} blocs) en SURBRILLANCE, même à travers les murs (le contour lumineux de Minecraft).
 * Seulement sur SON écran : les autres joueurs ne voient rien de spécial. Mixin facultatif (require = 0).
 */
@Mixin(Minecraft.class)
public abstract class ObservationGlowMixin {

    private static final double RANGE = 40.0;

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true, require = 0)
    private void bloxpiece$observationGlow(Entity entity, CallbackInfoReturnable<Boolean> info) {
        LocalPlayer player = ((Minecraft) (Object) this).player;
        if (player != null && entity != player && entity instanceof LivingEntity && !entity.isInvisible()
                && Boolean.TRUE.equals(player.getAttached(ModAttachments.HAKI_OBSERVATION))
                && entity.distanceToSqr(player) < RANGE * RANGE) {
            info.setReturnValue(true);
        }
    }
}
