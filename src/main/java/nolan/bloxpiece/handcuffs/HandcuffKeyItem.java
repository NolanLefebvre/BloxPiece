package nolan.bloxpiece.handcuffs;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Clé de Menottes : clic droit sur un joueur menotté pour le libérer, ou clic droit dans le vide pour se libérer soi-même
 * (un joueur menotté peut toujours sélectionner et utiliser une clé de sa hotbar). Les menottes retirées sont détruites. La clé
 * n'est pas consommée.
 */
public class HandcuffKeyItem extends Item {

    public HandcuffKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!Handcuffs.isCuffed(target)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer holder) {
            if (target instanceof ServerPlayer cuffed) {
                Handcuffs.release(cuffed, holder);
            } else if (target instanceof Mob mob) {
                Handcuffs.releaseMob(mob, holder); // le mob reprend son arme
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!Handcuffs.isCuffed(player)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer self) {
            Handcuffs.release(self, self); // se libère soi-même
        }
        return InteractionResult.SUCCESS;
    }
}
