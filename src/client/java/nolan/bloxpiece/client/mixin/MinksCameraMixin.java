package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;

/**
 * Danse des griffes éclair (race Minks) : caméra cinématique du joueur qui lance l'attaque (MinksCamera). Après que Minecraft a
 * placé la caméra sur le joueur, elle est déplacée et orientée vers la zone d'attaque. Locale au client : les autres joueurs
 * gardent leur caméra. Mixin facultatif (require = 0) : sans effet, mais sans plantage, si la méthode change.
 */
@Mixin(Camera.class)
public abstract class MinksCameraMixin {

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "alignWithEntity", at = @At("TAIL"), require = 0)
    private void bloxpiece$minksCinematic(float partialTick, CallbackInfo info) {
        double[] cam = nolan.bloxpiece.client.minks.MinksCamera.cinematic(partialTick);
        if (cam != null) {
            this.setRotation((float) cam[3], (float) cam[4]);
            this.setPosition(cam[0], cam[1], cam[2]);
        }
    }
}
