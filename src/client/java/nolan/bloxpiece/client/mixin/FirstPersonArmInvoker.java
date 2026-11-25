package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Accès à FirstPersonHandsAndItemsRenderer.renderPlayerArm (privée) : dessine un bras vide à la première personne, à sa place
 * normale. Sert à montrer l'AUTRE bras pendant les attaques Cyborg (CyborgFirstPersonMixin). Mixin facultatif.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public interface FirstPersonArmInvoker {

    @Invoker("renderPlayerArm")
    void bloxpiece$renderPlayerArm(PoseStack pose, SubmitNodeCollector collector, int light, float inverseArmHeight, float attack,
                                   HumanoidArm arm, PlayerRenderState playerState);
}
