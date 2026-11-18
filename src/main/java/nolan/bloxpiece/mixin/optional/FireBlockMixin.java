package nolan.bloxpiece.mixin.optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;

import nolan.bloxpiece.village.SafeZone;

/**
 * Zone sécurisée du village : un feu allumé JUSTE à l'extérieur ne peut ni consumer ni enflammer un bloc du village (le bloc
 * voisin n'est jamais brûlé ni détruit). Mixin facultatif : si le nom de la méthode change dans une version de Minecraft,
 * l'injection est ignorée (le feu reste de toute façon retiré dès qu'il apparaît dans la zone, voir BaseFireBlockMixin).
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true, require = 0)
    private void bloxpiece$noBurnInVillage(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo info) {
        if (SafeZone.isProtected(level, pos)) {
            info.cancel();
        }
    }
}
