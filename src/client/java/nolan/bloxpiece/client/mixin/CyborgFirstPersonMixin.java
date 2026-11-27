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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import nolan.bloxpiece.client.cyborg.CyborgFirstPerson;

/**
 * Race Cyborg, PREMIÈRE PERSONNE (CyborgFirstPerson) :
 * <ul>
 *   <li>la main principale suit les phases des attaques (même point d'insertion que l'épée Ace et la race Humaine) ;</li>
 *   <li>à la fin du dessin des mains, l'AUTRE bras (souvent le bras mécanique gauche, que Minecraft ne dessine pas main vide)
 *       est dessiné en plus pendant les attaques qui l'animent ; le bras mécanique remplace alors le bras vanilla
 *       (CyborgHandMixin), avec sa transformation en canon.</li>
 * </ul>
 * Sert aussi à la race Minks (MinksFirstPerson) et à la race Fishman (FishmanFirstPerson), avec le même mécanisme.
 * Mixin facultatif (require = 0) : sans effet, mais sans plantage, si les méthodes changent.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class CyborgFirstPersonMixin {

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
            shift = At.Shift.AFTER, ordinal = 0), require = 0)
    private void bloxpiece$cyborgMainArm(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks,
                                         float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight,
                                         PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo info) {
        if (hand == InteractionHand.MAIN_HAND) {
            CyborgFirstPerson.applyMain(pose, partialTicks);
            nolan.bloxpiece.client.minks.MinksFirstPerson.applyMain(pose, partialTicks); // race Minks (même principe)
            nolan.bloxpiece.client.fishman.FishmanFirstPerson.applyMain(pose, partialTicks); // race Fishman (même principe)
            nolan.bloxpiece.client.lunarian.LunarianFirstPerson.applyMain(pose, partialTicks); // race Lunarian (même principe)
        }
    }

    @Inject(method = "submitHandsWithItems", at = @At("TAIL"), require = 0)
    private void bloxpiece$cyborgOffArm(float partialTicks, PoseStack pose, SubmitNodeCollector collector, PlayerRenderState playerState,
                                        FirstPersonHandsAndItemsRenderState state, CallbackInfo info) {
        if (!playerState.hasPlayer || playerState.avatarRenderState == null) {
            return;
        }
        pose.pushPose();
        HumanoidArm arm = CyborgFirstPerson.offArm(pose, partialTicks);
        if (arm == null) { // race Minks : l'autre main pendant ses attaques (même principe)
            arm = nolan.bloxpiece.client.minks.MinksFirstPerson.offArm(pose, partialTicks);
        }
        if (arm == null) { // race Fishman : l'autre main pendant ses attaques (même principe)
            arm = nolan.bloxpiece.client.fishman.FishmanFirstPerson.offArm(pose, partialTicks);
        }
        if (arm == null) { // race Lunarian : l'autre main pendant ses capacités (même principe)
            arm = nolan.bloxpiece.client.lunarian.LunarianFirstPerson.offArm(pose, partialTicks);
        }
        if (arm != null) {
            ((FirstPersonArmInvoker) this).bloxpiece$renderPlayerArm(pose, collector, playerState.avatarRenderState.lightCoords, 0.0F, 0.0F,
                    arm, playerState);
        }
        pose.popPose();
        // race Cyborg : boule d'énergie du Radical Beam entre les mains, comme vue de l'extérieur
        pose.pushPose();
        CyborgFirstPerson.submitBeamCharge(pose, collector, partialTicks);
        pose.popPose();
        // race Lunarian : bout des ailes qui entre dans le champ, flammes de transition au bas de l'écran
        pose.pushPose();
        nolan.bloxpiece.client.lunarian.LunarianFirstPerson.submitView(pose, collector, partialTicks, playerState.avatarRenderState.lightCoords);
        pose.popPose();
    }
}
