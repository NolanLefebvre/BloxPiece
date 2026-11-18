package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

import nolan.bloxpiece.client.entity.CuffPose;

/**
 * Menottes : remet les bras dans le dos À LA FIN de la pose des modèles qui les repositionnent après le modèle humanoïde de base
 * (zombies : bras tendus devant ; squelettes : visée ; piglins). Les classes ont pu changer de nom ou de package selon la version
 * de Minecraft : plusieurs noms sont essayés et ce mixin est OPTIONNEL (bloxpiece.client.optional.mixins.json, required = false) :
 * un nom introuvable est simplement ignoré, sans plantage.
 */
@Mixin(targets = {
        "net.minecraft.client.model.AbstractZombieModel",
        "net.minecraft.client.model.DrownedModel",
        "net.minecraft.client.model.ZombieVillagerModel",
        "net.minecraft.client.model.SkeletonModel",
        "net.minecraft.client.model.PiglinModel",
        "net.minecraft.client.model.monster.zombie.AbstractZombieModel",
        "net.minecraft.client.model.monster.zombie.DrownedModel",
        "net.minecraft.client.model.monster.zombie.ZombieVillagerModel",
        "net.minecraft.client.model.monster.skeleton.SkeletonModel",
        "net.minecraft.client.model.monster.piglin.PiglinModel"
}, remap = false)
public abstract class CuffedMobModelMixin {

    @Inject(method = "setupAnim", at = @At("TAIL"), require = 0)
    private void bloxpiece$cuffedArms(@Coerce EntityRenderState state, CallbackInfo info) {
        if ((Object) this instanceof HumanoidModel<?> model) {
            CuffPose.applyIfCuffed(model, state);
        }
    }
}
