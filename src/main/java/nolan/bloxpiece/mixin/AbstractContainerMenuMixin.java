package nolan.bloxpiece.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import nolan.bloxpiece.handcuffs.Handcuffs;

/**
 * Menottes en Granite Marin : un joueur menotté peut ouvrir son inventaire, mais seule la Clé de Menottes peut y être déplacée
 * (pour la mettre dans sa hotbar et se libérer). Tout autre clic dans un inventaire est annulé. AbstractContainerMenu.clicked est
 * appelée par le client (prédiction) ET par le serveur (paquet de clic) : les deux refusent de la même façon.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void bloxpiece$onlyKeyWhenCuffed(int slotId, int button, @Coerce Object clickType, Player player, CallbackInfo info) {
        if (!Handcuffs.isCuffed(player) || player.isCreative()) {
            return;
        }
        // Le type de clic (énumération renommée en 26.3) est lu par son nom : PICKUP, QUICK_MOVE, SWAP, THROW...
        String type = clickType instanceof Enum<?> e ? e.name() : String.valueOf(clickType);
        if (!bloxpiece$allowed(slotId, button, type, player)) {
            info.cancel();
        }
    }

    /**
     * Clics permis (uniquement autour de la Clé de Menottes) :
     * <ul>
     *   <li>prendre une clé, la poser sur une case vide ou sur une autre clé, l'envoyer ailleurs (Maj + clic) ;</li>
     *   <li>poser la clé tenue sur un objet de la HOTBAR : la clé prend sa place et l'objet passe dans la main de la souris, qui peut
     *       seulement le reposer sur une case vide (pour finir l'échange) ;</li>
     *   <li>touches 1-9 : échanger une clé avec n'importe quelle case de la hotbar (vide ou non), dans les deux sens.</li>
     * </ul>
     */
    private boolean bloxpiece$allowed(int slotId, int button, String clickType, Player player) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        ItemStack carried = menu.getCarried();
        Slot slot = slotId >= 0 && slotId < menu.slots.size() ? menu.slots.get(slotId) : null;
        ItemStack inSlot = slot == null ? ItemStack.EMPTY : slot.getItem();
        switch (clickType) {
            case "PICKUP":
                if (slot == null) {
                    return false; // clic hors de la fenêtre : ce serait jeter l'objet tenu
                }
                if (carried.isEmpty()) {
                    return Handcuffs.isKey(inSlot);                 // prendre une clé
                }
                if (Handcuffs.isKey(carried)) {
                    return inSlot.isEmpty() || Handcuffs.isKey(inSlot) || bloxpiece$isHotbar(slot); // poser / échanger dans la hotbar
                }
                return inSlot.isEmpty();                            // reposer l'objet échangé contre la clé
            case "QUICK_MOVE":
                return Handcuffs.isKey(inSlot);
            case "SWAP":
                if (button < 0 || button > 8) {
                    return false; // touche de la main secondaire : non
                }
                return Handcuffs.isKey(inSlot) || Handcuffs.isKey(player.getInventory().getItem(button));
            default:
                return false; // jeter (Q), tout ramasser, glisser-répartir, clic molette...
        }
    }

    /** Case de la hotbar du joueur (cases 0 à 8 de son inventaire). */
    private static boolean bloxpiece$isHotbar(Slot slot) {
        return slot.container instanceof Inventory && slot.getContainerSlot() < 9;
    }
}
