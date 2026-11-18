package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import nolan.bloxpiece.client.haki.UnconsciousRenderState;

/**
 * Haki des Rois : une entité (joueur ou mob) évanouie est couchée sur le côté, au ras du sol : après son orientation habituelle,
 * le modèle entier est remonté d'une demi-largeur (l'épaule touche le sol) puis basculé de 90°. Mixin facultatif
 * (bloxpiece.client.optional.mixins.json) : si la méthode change dans une version de Minecraft, l'entité reste simplement debout,
 * mais toujours paralysée.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class UnconsciousPoseMixin {

    @Inject(method = "setupRotations", at = @At("TAIL"), require = 0)
    private void bloxpiece$lieDown(LivingEntityRenderState state, PoseStack pose, float bodyRot, float scale, CallbackInfo info) {
        if (state instanceof UnconsciousRenderState u && u.bloxpiece$isUnconscious()) {
            pose.translate(0.0F, 0.3F, 0.0F);
            pose.rotateDegrees(Axis.ZP, 90.0F);
        }
    }
}
