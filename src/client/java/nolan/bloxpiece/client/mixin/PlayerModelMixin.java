package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.registry.ModAttachments;

/**
 * Animation de Dai Enkai: Entei : pendant la charge, le bras droit du joueur se lève vers le ciel.
 *
 * Minecraft calcule la pose du modèle du joueur dans PlayerModel.setupAnim ; on laisse faire l'animation normale puis,
 * à la toute fin, on remplace la rotation du bras droit si le joueur est en train de charger l'attaque.
 * Le serveur indique la charge avec l'attachment ENTEI_CHARGING (synchronisé avec tous les joueurs proches), donc
 * chacun voit le bras levé des autres joueurs, pas seulement le sien.
 */
@Mixin(PlayerModel.class)
public class PlayerModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void bloxpiece$raiseArmWhileCharging(AvatarRenderState state, CallbackInfo info) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        // L'état de rendu contient l'identifiant de l'entité : on retrouve le joueur concerné
        Entity player = level.getEntity(state.id);
        if (player == null || player.getAttached(ModAttachments.ENTEI_CHARGING) == null) {
            return;
        }

        ModelPart arm = ((PlayerModel) (Object) this).rightArm;
        arm.xRot = -3.0F + (float) Math.sin(state.ageInTicks * 0.9F) * 0.05F; // presque à la verticale, léger tremblement
        arm.yRot = 0.0F;
        arm.zRot = 0.25F;                                                      // un peu écarté du corps
    }
}
