package nolan.bloxpiece.handcuffs;

import java.util.Set;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import nolan.bloxpiece.registry.ModItems;

/**
 * Granite Marin (Seastone) : il neutralise les pouvoirs des fruits du démon. Un porteur de fruit est "neutralisé" s'il est menotté
 * OU s'il a sur lui (n'importe quelle case : inventaire, hotbar, main secondaire) un objet en Granite Marin : minerai, fragment
 * brut, lingot, pépite ou menottes ({@link #ITEMS}). La Clé de Menottes n'en fait pas partie.
 *
 * Effets (porteur de fruit) : vitesse -15 % et transformation annulée (Handcuffs.tick), attaques bloquées (AbilityManager),
 * passif Logia annulé (LogiaIntangibility).
 */
public final class Seastone {

    /** Les objets en Granite Marin. Pour en ajouter un : l'ajouter ici. */
    private static Set<Item> items;

    private Seastone() {
    }

    private static Set<Item> items() {
        if (items == null) {
            items = Set.of(ModItems.SEASTONE_ORE, ModItems.RAW_SEASTONE, ModItems.SEASTONE_INGOT, ModItems.SEASTONE_NUGGET,
                    ModItems.SEASTONE_HANDCUFFS);
        }
        return items;
    }

    public static boolean isSeastone(ItemStack stack) {
        return !stack.isEmpty() && items().contains(stack.getItem());
    }

    /** Le joueur a-t-il au moins un objet en Granite Marin sur lui ? */
    public static boolean carries(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isSeastone(inventory.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /** Pouvoirs neutralisés par le Granite Marin : menotté, ou un objet en Granite Marin sur soi. */
    public static boolean suppresses(Entity entity) {
        return entity instanceof Player player && (Handcuffs.isCuffed(player) || carries(player));
    }
}
