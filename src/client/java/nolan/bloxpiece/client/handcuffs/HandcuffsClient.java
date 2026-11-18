package nolan.bloxpiece.client.handcuffs;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.handcuffs.Handcuffs;

/**
 * Menottes, côté client du joueur menotté : son inventaire est verrouillé.
 * <ul>
 *   <li>Cadenas par-dessus chaque case de la hotbar et la main secondaire (sauf une case qui contient une Clé de Menottes).</li>
 *   <li>Il ne peut pas changer de case (molette, touches 1-9), sauf pour sélectionner une case qui contient une clé.</li>
 *   <li>Jeter (Q), échanger de main (F) : désactivés ; un conteneur ouvert (coffre...) est refermé. L'inventaire s'ouvre, mais seule
 *       la Clé de Menottes peut y être déplacée (AbstractContainerMenuMixin).</li>
 * </ul>
 * Utiliser un objet, frapper, casser un bloc... est refusé par le serveur (Handcuffs) et ici aussi (mêmes callbacks).
 */
public final class HandcuffsClient implements HudElement {

    private static final Identifier LOCK = BloxPiece.id("textures/gui/lock.png");
    private static final int VEIL = 0x99101018;   // voile sombre sur une case verrouillée

    /** Case sélectionnée au moment où le joueur a été menotté (ou dernière case de clé choisie) : il ne peut pas en sortir. */
    private static int lockedSlot = -1;

    private HandcuffsClient() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, BloxPiece.id("handcuffs_locks"), new HandcuffsClient());
    }

    /** Au début de chaque tick client (BloxPieceClient). */
    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !Handcuffs.isCuffed(player)) {
            lockedSlot = -1;
            return;
        }
        // Touches désactivées : jeter, échanger de main. Appelé au DÉBUT du tick : les appuis sont retirés avant que Minecraft ne
        // les traite. L'inventaire, lui, s'ouvre (seule la clé peut y être déplacée : AbstractContainerMenuMixin).
        while (client.options.keyDrop.consumeClick()) {
            // ignoré
        }
        while (client.options.keySwapOffhand.consumeClick()) {
            // ignoré
        }
        // Une interface de conteneur (coffre, four...) ouverte avant d'être menotté : refermée. L'inventaire du joueur reste ouvert.
        if (client.gui.screen() instanceof AbstractContainerScreen<?> && !(client.gui.screen() instanceof InventoryScreen)) {
            player.closeContainer();
        }
        // Sélection de la hotbar bloquée, sauf vers une case qui contient une clé
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (lockedSlot < 0) {
            lockedSlot = selected;
        }
        if (selected != lockedSlot) {
            if (Handcuffs.isKey(inventory.getItem(selected))) {
                lockedSlot = selected;
            } else {
                inventory.setSelectedSlot(lockedSlot);
                }
        }
    }

    /** Cadenas par-dessus les cases verrouillées (hotbar + main secondaire). */
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !Handcuffs.isCuffed(player) || mc.gui.screen() != null) {
            return;
        }
        Inventory inventory = player.getInventory();
        int center = g.guiWidth() / 2;
        int y = g.guiHeight() - 19;
        for (int i = 0; i < 9; i++) {
            if (!Handcuffs.isKey(inventory.getItem(i))) {
                lock(g, center - 88 + i * 20, y);
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            lock(g, center - 91 - 26, y); // case de la main secondaire, à gauche de la hotbar
        }
    }

    private static void lock(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 16, y + 16, VEIL);
        g.blit(RenderPipelines.GUI_TEXTURED, LOCK, x, y, 0.0F, 0.0F, 16, 16, 16, 16, 0xFFFFFFFF);
    }
}
