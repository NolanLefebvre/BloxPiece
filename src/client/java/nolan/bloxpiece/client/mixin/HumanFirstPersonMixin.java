package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Race Humaine, PREMIÈRE PERSONNE : la main principale suit les phases des attaques Humaines (HumanFirstPerson). Même point
 * d'insertion que l'épée Ace (juste après l'ouverture de la pose de la main) : le bras et l'objet tenu bougent ensemble.
 * Mixin facultatif (require = 0) : sans effet, mais sans plantage, si la méthode change.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HumanFirstPersonMixin {

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
            shift = At.Shift.AFTER, ordinal = 0), require = 0)
    private void bloxpiece$humanAttack(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks,
                                       float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight,
                                       PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo info) {
        if (hand == InteractionHand.MAIN_HAND) {
            nolan.bloxpiece.client.human.HumanFirstPerson.apply(pose, partialTicks);
        }
    }
}
