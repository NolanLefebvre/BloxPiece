package nolan.bloxpiece.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.PirateBullet;

/**
 * Pistolet à silex (arme du Pirate Tireur, lootable). Clic droit : tire une balle (PirateBullet) dans la direction du regard.
 * Chaque tir consomme 1 poudre à canon (sauf en créatif) et le pistolet doit ensuite être rechargé {@link #RELOAD_TICKS} ticks.
 * Sans poudre : un simple déclic.
 */
public class FlintlockItem extends Item {

    private static final int RELOAD_TICKS = 30;
    private static final float DAMAGE = 9.0F;
    private static final double SPEED = 3.0;

    public FlintlockItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.isCreative() && !consumePowder(player)) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 1.0F, 1.2F);
            return InteractionResult.FAIL;
        }
        Vec3 look = player.getLookAngle();
        PirateBullet.fire(serverLevel, player, player.getEyePosition().add(look.scale(0.5)).add(0.0, -0.1, 0.0), look, SPEED, DAMAGE);
        player.getCooldowns().addCooldown(stack, RELOAD_TICKS);
        return InteractionResult.SUCCESS;
    }

    /** Retire 1 poudre à canon de l'inventaire : faux s'il n'y en a pas. */
    private static boolean consumePowder(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(Items.GUNPOWDER)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }
}
