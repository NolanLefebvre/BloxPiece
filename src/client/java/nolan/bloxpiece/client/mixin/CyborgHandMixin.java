package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;

/**
 * Race Cyborg, PREMIÈRE PERSONNE : quand Minecraft dessine la main GAUCHE du joueur local (AvatarRenderer.renderHand), le bras
 * mécanique est dessiné à sa place, sur la même pose (CyborgArmLayer.renderFirstPerson). Autres races, main droite : inchangé.
 * Mixin facultatif (bloxpiece.client.optional.mixins.json) : sans effet, mais sans plantage, si la méthode change.
 */
@Mixin(AvatarRenderer.class)
public abstract class CyborgHandMixin {

    @Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Lnet/minecraft/client/model/geom/ModelPart;Z)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void bloxpiece$cyborgHand(PoseStack pose, SubmitNodeCollector collector, int light, Identifier skin, ModelPart arm,
                                      boolean sleeve, CallbackInfo info) {
        PlayerModel model = (PlayerModel) ((net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
        if (arm != model.leftArm) {
            return;
        }
        arm.xRot = 0.0F; // main au repos (comme Minecraft avant de la dessiner)
        arm.yRot = 0.0F;
        arm.zRot = 0.0F;
        if (nolan.bloxpiece.client.cyborg.CyborgArmLayer.renderFirstPerson(pose, collector, light, arm)) {
            // Surcharge moteur : conduits d'énergie et arcs sur le bras mécanique vu à la première personne
            nolan.bloxpiece.client.cyborg.CyborgOverdriveLayer.submitHand(pose, collector, arm, true);
            info.cancel();
        }
    }
}
