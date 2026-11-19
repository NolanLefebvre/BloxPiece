package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Haki de l'Armement sur l'ICÔNE de l'objet en main (barre d'objets, inventaire, coffres...) : après le dessin de l'objet, on
 * peint son revêtement pixel par pixel (HakiItemCoat.renderIcon), avec la même encre et la même transition que dans le monde
 * (comme le voile de recharge de Minecraft, dessiné lui aussi juste après l'objet, par-dessus).
 *
 * La barre d'objets et les cases d'inventaire n'utilisent pas la même variante de la méthode de dessin des objets (avec ou sans
 * entité, avec ou sans graine...) : chaque variante connue est visée (noms « item », « renderItem », « fakeItem »...). Une
 * variante absente est ignorée (require = 0) ; si une variante en appelle une autre, l'icône n'est peinte qu'une fois
 * (HakiItemCoat évite le double dessin). Mixin facultatif (bloxpiece.client.optional.mixins.json).
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiItemHakiMixin {

    private void bloxpiece$paint(ItemStack stack, int x, int y) {
        nolan.bloxpiece.client.haki.HakiItemCoat.renderIcon((GuiGraphicsExtractor) (Object) this, stack, x, y);
    }

    // objet seul : (objet, x, y)
    @Inject(method = {"item(Lnet/minecraft/world/item/ItemStack;II)V", "renderItem(Lnet/minecraft/world/item/ItemStack;II)V",
            "fakeItem(Lnet/minecraft/world/item/ItemStack;II)V", "renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V"},
            at = @At("TAIL"), require = 0)
    private void bloxpiece$icon(ItemStack stack, int x, int y, CallbackInfo info) {
        bloxpiece$paint(stack, x, y);
    }

    // objet avec graine : (objet, x, y, graine) — cases d'inventaire
    @Inject(method = {"item(Lnet/minecraft/world/item/ItemStack;III)V", "renderItem(Lnet/minecraft/world/item/ItemStack;III)V",
            "fakeItem(Lnet/minecraft/world/item/ItemStack;III)V", "renderFakeItem(Lnet/minecraft/world/item/ItemStack;III)V"},
            at = @At("TAIL"), require = 0)
    private void bloxpiece$iconSeed(ItemStack stack, int x, int y, int seed, CallbackInfo info) {
        bloxpiece$paint(stack, x, y);
    }

    // objet tenu par une entité : (entité, objet, x, y, graine) — barre d'objets
    @Inject(method = {"item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V",
            "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V"},
            at = @At("TAIL"), require = 0)
    private void bloxpiece$iconEntity(@Coerce Object entity, ItemStack stack, int x, int y, int seed, CallbackInfo info) {
        bloxpiece$paint(stack, x, y);
    }

    // variante interne : (entité, monde, objet, x, y, graine)
    @Inject(method = {
            "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
            "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V"},
            at = @At("TAIL"), require = 0)
    private void bloxpiece$iconFull(@Coerce Object entity, @Coerce Object level, ItemStack stack, int x, int y, int seed,
                                    CallbackInfo info) {
        bloxpiece$paint(stack, x, y);
    }
}
