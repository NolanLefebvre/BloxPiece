package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * Haki de l'Armement à la PREMIÈRE PERSONNE. Dans 26.3, les mains et les objets vus à la première personne sont dessinés par
 * FirstPersonHandsAndItemsRenderer.submitArmWithItem : après le placement de l'objet (position du bras, hauteur pendant
 * l'équipement, balancement du coup...), l'objet est envoyé au rendu, puis la pose est refermée (popPose). On s'insère JUSTE
 * AVANT cette fermeture : la pose est alors exactement celle de l'objet en main, et le revêtement noir métallique
 * (HakiItemCoat) y est ajouté avec la même transformation d'affichage, donc collé à l'arme et animé comme elle.
 * Mixin facultatif (bloxpiece.client.optional.mixins.json, require = 0) : sans effet, mais sans plantage, si la méthode change.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandHakiMixin {

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"),
            require = 0)
    private void bloxpiece$hakiCoat(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks,
                                    float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight,
                                    PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo info) {
        LocalPlayer player = Minecraft.getInstance().player;
        // (les cartes sont dessinées à plat devant l'écran avec leur propre placement : pas de revêtement)
        if (player == null || hand != InteractionHand.MAIN_HAND || itemStack.isEmpty()
                || itemStack.has(net.minecraft.core.component.DataComponents.MAP_ID)) {
            return;
        }
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        nolan.bloxpiece.client.haki.HakiItemCoat.renderFirstPerson(pose, collector, player, right, light);
    }
}
