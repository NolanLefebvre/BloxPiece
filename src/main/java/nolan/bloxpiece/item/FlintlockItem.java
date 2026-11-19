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
 * Chaque tir consomme 1 poudre à canon (sauf en créatif) et l'arme doit ensuite être rechargée. Sert aussi au fusil de la Marine.
 * Sans poudre : un simple déclic.
 */
public class FlintlockItem extends Item {

    private final int reloadTicks;
    private final float damage;
    private final double speed;

    /** Pistolet à silex des pirates. */
    public FlintlockItem(Properties properties) {
        this(properties, 30, 9.0F, 3.0);
    }

    /** Arme à feu sur mesure (ex : fusil de la Marine : rechargement long, gros dégâts, balle rapide). */
    public FlintlockItem(Properties properties, int reloadTicks, float damage, double speed) {
        super(properties);
        this.reloadTicks = reloadTicks;
        this.damage = damage;
        this.speed = speed;
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
        PirateBullet.fire(serverLevel, player, player.getEyePosition().add(look.scale(0.5)).add(0.0, -0.1, 0.0), look, this.speed,
                this.damage);
        player.getCooldowns().addCooldown(stack, this.reloadTicks);
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
