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
 * Haki des Rois : une entité évanouie est couchée au ras du sol, après son orientation habituelle : les bipèdes (joueurs,
 * zombies, villageois...) tombent SUR LE DOS (bascule de 90° en arrière autour des pieds), les autres (quadrupèdes, araignées...)
 * tombent SUR LE CÔTÉ (bascule de 90° sur le flanc), remontés selon leur largeur pour reposer sur le sol. Mixin facultatif
 * (bloxpiece.client.optional.mixins.json) : si la méthode change dans une version de Minecraft, l'entité reste simplement debout,
 * mais toujours paralysée.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class UnconsciousPoseMixin {

    @Inject(method = "setupRotations", at = @At("TAIL"), require = 0)
    private void bloxpiece$lieDown(LivingEntityRenderState state, PoseStack pose, float bodyRot, float scale, CallbackInfo info) {
        if (!(state instanceof UnconsciousRenderState u) || !u.bloxpiece$isUnconscious()) {
            return;
        }
        float width = u.bloxpiece$lieWidth() > 0.0F ? u.bloxpiece$lieWidth() : 0.6F;
        if (u.bloxpiece$lieMode() == UnconsciousRenderState.ON_BACK) {
            // bipède : bascule EN ARRIÈRE autour des pieds, visage vers le ciel, le dos posé au sol (épaisseur ~ moitié de la largeur)
            pose.translate(0.0F, width * 0.28F, 0.0F);
            pose.rotateDegrees(Axis.XP, 90.0F);
        } else {
            // quadrupède et autres : couché sur le flanc, remonté d'une demi-largeur
            pose.translate(0.0F, width * 0.5F, 0.0F);
            pose.rotateDegrees(Axis.ZP, 90.0F);
        }
    }
}
