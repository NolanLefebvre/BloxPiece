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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.handcuffs.Handcuffs;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModItems;

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
        }        drawTimer(g, mc, player);
    }

    /**
     * Compte à rebours des menottes, en haut au centre : icône des menottes, « Menottes : 4:32 » et une barre qui se vide jusqu'à
     * ce qu'elles s'ouvrent d'elles-mêmes (5 minutes). Le temps vient du serveur (attachment HANDCUFF_SECONDS).
     */
    private static void drawTimer(GuiGraphicsExtractor g, Minecraft mc, LocalPlayer player) {
        Integer left = player.getAttached(ModAttachments.HANDCUFF_SECONDS);
        if (left == null) {
            return;
        }
        int seconds = Math.max(0, left);
        Component text = Component.translatable("hud.bloxpiece.handcuffs.timer", String.format("%d:%02d", seconds / 60, seconds % 60));
        int textW = mc.font.width(text);
        int w = Math.max(110, 24 + textW + 8);
        int x = g.guiWidth() / 2 - w / 2;
        int y = 4;
        g.fill(x, y, x + w, y + 26, 0xB0101018);
        g.outline(x, y, w, 26, 0xFF6B5A2E);
        g.item(new ItemStack(ModItems.SEASTONE_HANDCUFFS), x + 4, y + 5);
        g.text(mc.font, text, x + 24, y + 4, 0xFFFFD25A, true);
        // barre du temps restant (se vide), du doré vers le rouge à la fin
        int bx = x + 24, by = y + 16, bw = w - 24 - 6;
        float frac = Math.min(1.0F, seconds / (float) Handcuffs.DURATION_SECONDS);
        g.fill(bx, by, bx + bw, by + 5, 0xFF07070C);
        int color = frac > 0.25F ? 0xFFC8A24A : 0xFFE06B6B;
        int fill = Math.round((bw - 2) * frac);
        if (fill > 0) {
            g.fill(bx + 1, by + 1, bx + 1 + fill, by + 4, color);
        }
    }

    private static void lock(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 16, y + 16, VEIL);
        g.blit(RenderPipelines.GUI_TEXTURED, LOCK, x, y, 0.0F, 0.0F, 16, 16, 16, 16, 0xFFFFFFFF);
    }
}
