package nolan.bloxpiece.mixin.optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;

import nolan.bloxpiece.village.SafeZone;

/**
 * Zone sécurisée du village : aucune flamme ne peut y exister. Toute apparition de feu (propagation, lave, éclair, briquet,
 * boule de feu de ghast ou de blaze, explosion, attaques de fruits...) passe par la pose d'un bloc de feu : il est retiré
 * immédiatement, avant d'avoir pu brûler quoi que ce soit. Mixin facultatif (bloxpiece.optional.mixins.json).
 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {

    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true, require = 0)
    private void bloxpiece$noFireInVillage(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston,
            CallbackInfo info) {
        if (level instanceof ServerLevel && SafeZone.isProtected(level, pos)) {
            level.removeBlock(pos, false);
            info.cancel();
        }
    }
}
