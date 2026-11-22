package nolan.bloxpiece.mixin.optional;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.levelgen.structure.Structure;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.worldgen.MarineBaseExclusion;

/**
 * Choix des emplacements de structures (MarineBaseExclusion) : la Base de la Marine est refusée près du spawn / du village de
 * départ et sur un relief trop accidenté ; toute autre structure de surface est refusée trop près d'une base ; le Camp de
 * bandits et l'Arène de Roger sont en plus refusés sur un terrain en pente ou sous l'eau (BanditCampExclusion,
 * RogerArenaExclusion). Mixin facultatif :
 * si la méthode change de nom dans une version de Minecraft, l'injection est ignorée. En cas d'erreur pendant la vérification,
 * la structure est laissée telle quelle (jamais de plantage de la génération).
 */
@Mixin(Structure.class)
public abstract class StructureSpawnExclusionMixin {

    @Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true, require = 0)
    private void bloxpiece$marineBaseRules(Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> info) {
        try {
            Structure self = (Structure) (Object) this;
            boolean refuse = MarineBaseExclusion.isMarineBase(context, self)
                    ? MarineBaseExclusion.refuseBase(context)
                    : MarineBaseExclusion.refuseNearBase(context, self);
            if (!refuse && nolan.bloxpiece.worldgen.BanditCampExclusion.isCamp(context, self)) {
                refuse = nolan.bloxpiece.worldgen.BanditCampExclusion.refuse(context); // camp de bandits : relief, eau, spawn
            }
            if (!refuse && nolan.bloxpiece.worldgen.RogerArenaExclusion.isArena(context, self)) {
                refuse = nolan.bloxpiece.worldgen.RogerArenaExclusion.refuse(context); // arène de Roger : relief, eau, spawn
            }
            if (refuse) {
                info.setReturnValue(Optional.empty());
            }
        } catch (RuntimeException e) {
            BloxPiece.LOGGER.debug("[BloxPiece] Vérification d'emplacement de structure ignorée", e);
        }
    }
}
