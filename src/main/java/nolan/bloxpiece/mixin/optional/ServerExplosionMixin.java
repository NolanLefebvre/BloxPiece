package nolan.bloxpiece.mixin.optional;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;

import nolan.bloxpiece.village.SafeZone;

/**
 * Zone sécurisée du village : TOUTE explosion (creeper, TNT, boule de feu de ghast, cristal, attaques explosives des fruits du
 * mod...) ignore les blocs du village. Les blocs à détruire sont filtrés à la source ; les flammes de l'explosion, posées sur ces
 * mêmes positions, n'y apparaissent donc pas non plus. Les dégâts aux joueurs du village sont déjà annulés par SafeZone.
 * Mixin facultatif (configuration bloxpiece.optional.mixins.json, required = false) : s'il ne s'applique pas dans une version
 * de Minecraft, le jeu démarre quand même (seule cette protection manque, un avertissement est écrit dans la console).
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
    private void bloxpiece$protectVillage(CallbackInfoReturnable<List<BlockPos>> cir) {
        List<BlockPos> positions = cir.getReturnValue();
        if (positions == null || positions.isEmpty()) {
            return;
        }
        List<BlockPos> kept = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            if (!SafeZone.isProtected(this.level, pos)) {
                kept.add(pos);
            }
        }
        if (kept.size() != positions.size()) {
            cir.setReturnValue(kept);
        }
    }
}
