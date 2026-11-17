package nolan.bloxpiece.client;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import nolan.bloxpiece.item.BloxXpItem;

/** Infobulle des objets d'XP BloxPiece : montant exact d'XP du mod, et comment l'utiliser. */
public final class BloxXpTooltip {

    private BloxXpTooltip() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (stack.getItem() instanceof BloxXpItem item) {
                lines.add(Component.translatable("tooltip.bloxpiece.xp_item.amount", String.format("%,d", item.xp()).replace(',', ' '))
                        .withStyle(ChatFormatting.GREEN));
                lines.add(Component.translatable("tooltip.bloxpiece.xp_item.use").withStyle(ChatFormatting.GRAY));
            }
        });
    }
}
