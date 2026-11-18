package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import nolan.bloxpiece.client.entity.CuffPose;

/**
 * Menottes : tout modèle humanoïde (pirates, et base des zombies, squelettes, piglins...) place les bras dans le dos si l'entité
 * est menottée. Les modèles qui repositionnent les bras APRÈS (zombie : bras tendus, squelette : visée) sont corrigés en plus par
 * CuffedMobModelMixin.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void bloxpiece$cuffedArms(HumanoidRenderState state, CallbackInfo info) {
        CuffPose.applyIfCuffed((HumanoidModel<?>) (Object) this, state);
    }
}
