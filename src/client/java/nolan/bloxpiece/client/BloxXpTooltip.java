package nolan.bloxpiece.client;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import nolan.bloxpiece.item.BloxXpItem;

/** Infobulles : objets d'XP BloxPiece (montant exact d'XP du mod, utilisation) et Race Reroll Token (utilisation). */
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
            if (stack.is(nolan.bloxpiece.registry.ModItems.RACE_REROLL_TOKEN)) {
                // Race Reroll Token : à quoi il sert et comment l'utiliser
                lines.add(Component.translatable("tooltip.bloxpiece.race_reroll_token.desc").withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("tooltip.bloxpiece.race_reroll_token.use").withStyle(ChatFormatting.GRAY));
            }
        });
    }
}
