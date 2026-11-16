package nolan.bloxpiece.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

import nolan.bloxpiece.fx.IceEffects;
import nolan.bloxpiece.registry.ModItems;

/**
 * Le trident de glace (attaque 1 du fruit de la Glace) ne peut pas exister au sol : dès qu'il est jeté (touche de lâcher, glissé
 * hors de l'inventaire, mort du joueur...), il fond en éclats de glace et disparaît.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void bloxpiece$meltIceSaber(CallbackInfo info) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getItem().is(ModItems.ICE_SABER) && self.level() instanceof ServerLevel level) {
            IceEffects.shatter(level, self.position().add(0.0, 0.3, 0.0), 0.8F);
            self.discard();
            info.cancel();
        }
    }
}
