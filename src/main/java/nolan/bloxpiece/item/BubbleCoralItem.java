package nolan.bloxpiece.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.fruit.BubbleCoral;

/**
 * Corail à Bulles : un petit corail renflé que le joueur écrase dans sa main (clic droit) : une bulle d'air se forme autour de lui
 * (voir BubbleCoral). Il sert {@link #USES} fois (barre d'usure, comme un outil), puis il est épuisé et disparaît ; rien n'est
 * consommé en créatif. Inutilisable si une bulle entoure déjà le joueur.
 */
public class BubbleCoralItem extends Item {

    /** Nombre d'utilisations d'un corail. */
    public static final int USES = 5;

    public BubbleCoralItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS; // côté client : geste de la main (le corail est écrasé)
        }
        if (BubbleCoral.isActive(serverPlayer)) {
            serverPlayer.sendSystemMessage(Component.translatable("message.bloxpiece.bubble_coral.already"), true);
            return InteractionResult.FAIL;
        }
        if (!player.isCreative()) {
            // Une utilisation de moins ; à la dernière, le corail s'effrite et disparaît
            ItemStack stack = player.getItemInHand(hand);
            int used = stack.getDamageValue() + 1;
            if (used >= stack.getMaxDamage()) {
                stack.shrink(1);
                ((ServerLevel) level).playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
            } else {
                stack.setDamageValue(used);
            }
        }
        BubbleCoral.form(serverPlayer);
        return InteractionResult.SUCCESS;
    }
}
