package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Race Lunarian : inclinaison du CORPS ENTIER (LunarianPose.rotate) après les rotations de Minecraft : le joueur fonce couché,
 * poing en avant (Poing embrasé), et plonge tête vers le sol (Tempête du jugement céleste). Ailes, flammes et effets suivent
 * (dessinés dans le même repère). Mixin facultatif (require = 0) : sans effet, mais sans plantage, si la méthode change.
 */
@Mixin(AvatarRenderer.class)
public abstract class LunarianRotationMixin {

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL"), require = 0)
    private void bloxpiece$lunarianTilt(AvatarRenderState state, PoseStack pose, float bodyRot, float scale, CallbackInfo info) {
        nolan.bloxpiece.client.lunarian.LunarianPose.rotate(state, pose);
    }
}
