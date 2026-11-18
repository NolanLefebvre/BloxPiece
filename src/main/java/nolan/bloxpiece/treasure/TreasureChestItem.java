package nolan.bloxpiece.treasure;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * L'objet Coffre au Trésor. Clic droit dans le vide : la roulette des fruits du démon s'ouvre ; le coffre n'est consommé qu'au clic
 * sur OUVRIR (on peut refermer la fenêtre et le garder).
 * Clic droit sur un bloc : il est posé (comportement normal d'un BlockItem) ; on l'ouvre ensuite d'un clic droit sur le coffre posé.
 */
public class TreasureChestItem extends BlockItem {

    public TreasureChestItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            TreasureChests.openFromHand(serverPlayer, hand); // consommé seulement au clic sur OUVRIR
        }
        return InteractionResult.SUCCESS;
    }
}
