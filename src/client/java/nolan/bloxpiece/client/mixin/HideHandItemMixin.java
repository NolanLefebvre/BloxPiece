package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import nolan.bloxpiece.handcuffs.Handcuffs;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Menottes (troisième personne, vu par tous les joueurs) : un joueur ou un mob menotté n'affiche aucun objet dans ses mains
 * (principale et secondaire), les bras étant liés dans le dos.
 * <p>
 * Gura Gura no Mi (troisième personne) : pendant une attaque qui porte une boule sismique dans la main (attachment
 * HIDE_HAND_ITEM), les objets tenus ne sont pas dessinés, pour ne pas traverser la boule. Seul le rendu change : l'objet est
 * toujours dans la main du joueur. Si cette méthode change dans une future version de Minecraft, l'injection est simplement
 * ignorée (require = 0) : le jeu ne plante pas, l'objet reste visible.
 */
@Mixin(ArmedEntityRenderState.class)
public abstract class HideHandItemMixin {

    @ModifyExpressionValue(method = "extractArmedEntityRenderState", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getItemHeldByArm(Lnet/minecraft/world/entity/HumanoidArm;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack bloxpiece$hideHeldItem(ItemStack original, @Local(argsOnly = true) LivingEntity entity) {
        boolean hidden = entity.getAttached(ModAttachments.HIDE_HAND_ITEM) != null || Handcuffs.isCuffed(entity);
        return hidden ? ItemStack.EMPTY : original;
    }
}
