package nolan.bloxpiece.mixin.optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;

import nolan.bloxpiece.ability.IceTerrain;

/**
 * Fruit de la Glace : la glace posée par ses attaques (IceTerrain) ne fond JAMAIS d'elle-même à la lumière : elle reste jusqu'à
 * ce que l'attaque rende le terrain d'origine (sinon elle devenait de l'eau avant, et le bloc d'origine n'était pas rendu).
 * Mixin facultatif (require = 0) : sans effet, mais sans plantage, si la méthode change.
 */
@Mixin(IceBlock.class)
public abstract class IceMeltMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void bloxpiece$noMelt(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo info) {
        if (IceTerrain.isFrozen(pos)) {
            info.cancel();
        }
    }
}
