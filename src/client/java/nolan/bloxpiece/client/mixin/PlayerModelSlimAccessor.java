package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.model.player.PlayerModel;

/**
 * Lit si le modèle du joueur est « fin » (skin Alex, bras de 3 px) ou standard (Steve, 4 px), pour que le revêtement du Haki de
 * l'Armement épouse exactement les bras. Mixin facultatif (bloxpiece.client.optional.mixins.json) : si le champ n'existe pas
 * dans cette version de Minecraft, il n'est simplement pas appliqué et les bras sont traités comme standard.
 */
@Mixin(PlayerModel.class)
public interface PlayerModelSlimAccessor {

    @Accessor("slim")
    boolean bloxpiece$isSlim();
}
