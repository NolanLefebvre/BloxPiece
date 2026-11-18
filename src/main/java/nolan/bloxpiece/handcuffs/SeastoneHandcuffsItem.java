package nolan.bloxpiece.handcuffs;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Menottes en Granite Marin : clic droit sur un autre joueur pour le menotter (seulement s'il est à 10 % de ses PV ou moins et
 * qu'on est dans son dos, voir Handcuffs). Accroupi + clic droit (dans le vide ou sur un bloc) : on se menotte soi-même.
 * L'objet est consommé (sauf en créatif).
 */
public class SeastoneHandcuffsItem extends Item {

    public SeastoneHandcuffsItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player) && !(target instanceof Mob)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer by) {
            boolean cuffed = target instanceof ServerPlayer victim ? Handcuffs.cuff(victim, by)
                    : target instanceof Mob mob && Handcuffs.cuffMob(mob, by); // mob : il faut aussi qu'il ait des bras
            if (cuffed && !by.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Accroupi + clic droit dans le vide : auto-menottage. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return cuffSelf(player, player.getItemInHand(hand));
    }

    /** Accroupi + clic droit sur un bloc : auto-menottage aussi. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        return player == null ? InteractionResult.PASS : cuffSelf(player, context.getItemInHand());
    }

    private static InteractionResult cuffSelf(Player player, ItemStack stack) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer self && Handcuffs.cuffSelf(self) && !self.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
