package nolan.bloxpiece.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.fruit.DevilFruit;
import nolan.bloxpiece.registry.ModAttachments;

/** Un item "fruit du démon" : quand on le mange, le joueur obtient les pouvoirs ET la faiblesse du fruit. */
public class DevilFruitItem extends Item {
    private final DevilFruit fruit;

    public DevilFruitItem(Properties properties, DevilFruit fruit) {
        super(properties);
        this.fruit = fruit;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            // Pouvoirs, résistance et faiblesse découlent tous de ce fruit enregistré sur le joueur
            player.setAttached(ModAttachments.FRUIT, fruit.id());
            player.sendSystemMessage(Component.translatable("message.bloxpiece.ate_fruit"));
        }
        // Garde le comportement normal (retrait de l'item...)
        return super.finishUsingItem(stack, level, entity);
    }
}
