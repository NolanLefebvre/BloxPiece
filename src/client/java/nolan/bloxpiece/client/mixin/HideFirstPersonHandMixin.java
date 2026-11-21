package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;

import nolan.bloxpiece.handcuffs.Handcuffs;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Menottes (première personne) : menotté, le joueur ne voit plus ni ses mains ni les objets tenus (mains liées dans le dos).
 * <p>
 * Gura Gura no Mi (première personne) : pendant une attaque qui porte une boule sismique (attachment HIDE_HAND_ITEM), la main et
 * l'objet tenu ne sont pas dessinés devant l'écran : la boule sismique (placée en bas de l'écran, côté main) les remplace, et rien
 * ne la traverse. Injection facultative (require = 0) : si la méthode change dans une future version, la main reste simplement
 * visible, sans plantage.
 */
@Mixin(GameRenderer.class)
public abstract class HideFirstPersonHandMixin {

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true, require = 0)
    private void bloxpiece$hideHand(CallbackInfo info) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && (player.getAttached(ModAttachments.HIDE_HAND_ITEM) != null || Handcuffs.isCuffed(player)
                || player.getAttached(ModAttachments.KAME_RETRACT) != null)) { // Kame Kame no Mi : bras rentrés dans la carapace
            info.cancel();
        }
    }
}
