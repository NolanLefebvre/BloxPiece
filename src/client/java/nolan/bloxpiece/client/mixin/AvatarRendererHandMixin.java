package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.client.entity.BubbleCoralLayer;
import nolan.bloxpiece.client.entity.BuddhaGoldLayer;

/**
 * Couche dorée du Bouddha sur la main vue à la première personne.
 *
 * La main n'est pas dessinée par les couches du joueur (BuddhaGoldLayer) mais par AvatarRenderer.renderHand : on laisse Minecraft dessiner
 * la main normalement (forme, pose, animations de swing inchangées) puis, à la fin, on redessine la même partie du modèle avec exactement
 * les mêmes passes dorées que le corps (BuddhaGoldLayer.submitHand). La main de la vue à la première personne est toujours celle du joueur local.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererHandMixin {

    @Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Lnet/minecraft/client/model/geom/ModelPart;Z)V",
            at = @At("TAIL"))
    private void bloxpiece$goldHand(PoseStack pose, SubmitNodeCollector collector, int light, Identifier skin, ModelPart arm, boolean sleeve, CallbackInfo info) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isInvisible()) {
            return; // comme pour le corps : pas de couche sur un joueur invisible
        }
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        BuddhaGoldLayer.submitHand(pose, collector, arm, skin, player.getScale(), player.tickCount + partialTick);
        // Corail à Bulles : la bulle d'air recouvre aussi la main
        BubbleCoralLayer.submitHand(pose, collector, arm, player, partialTick);
        // Haki de l'Armement : la main se couvre aussi de Haki à la première personne
        net.minecraft.client.model.player.PlayerModel model =
                (net.minecraft.client.model.player.PlayerModel) ((net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
        boolean rightArm = arm == model.rightArm;
        boolean mainArm = rightArm == (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
        nolan.bloxpiece.client.haki.HakiArmamentLayer.submitHand(pose, collector, arm, skin, player, mainArm, rightArm, model,
                player.tickCount + partialTick);
        // Race Humaine : aura du poing aussi sur la main vue à la première personne
        nolan.bloxpiece.client.human.HumanFistLayer.submitHand(pose, collector, arm, model);
        // Race Minks : griffes électriques sur la main vue à la première personne
        nolan.bloxpiece.client.minks.MinksLayer.submitHand(pose, collector, arm, model);
        // Race Fishman : aileron du bras et eau des attaques sur la main vue à la première personne
        nolan.bloxpiece.client.fishman.FishmanLayer.submitHand(pose, collector, arm, model, light);
        // Race Lunarian : feu du poing sur la main vue à la première personne
        nolan.bloxpiece.client.lunarian.LunarianLayer.submitHand(pose, collector, arm, model);
        // Race Cyborg, Surcharge moteur : conduits d'énergie sur la main droite vue à la première personne
        if (rightArm) {
            nolan.bloxpiece.client.cyborg.CyborgOverdriveLayer.submitHand(pose, collector, arm, false);
        }
    }
}
