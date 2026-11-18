package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.handcuffs.Handcuffs;

/**
 * Menottes en Granite Marin : inventaire ouvert par un joueur menotté, un cadenas par-dessus CHAQUE case, sauf celles qui
 * contiennent une Clé de Menottes (la seule chose qu'il peut déplacer, voir AbstractContainerMenuMixin côté commun).
 * require = 0 : si la méthode de dessin change de nom dans une version de Minecraft, le jeu se lance quand même (sans cadenas).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    private static final Identifier BLOXPIECE$LOCK = BloxPiece.id("textures/gui/lock.png");
    private static final int BLOXPIECE$VEIL = 0x99101018;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    @Final
    protected AbstractContainerMenu menu;

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void bloxpiece$lockSlots(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !Handcuffs.isCuffed(player) || player.isCreative()) {
            return;
        }
        for (Slot slot : this.menu.slots) {
            if (Handcuffs.isKey(slot.getItem())) {
                continue; // la clé reste libre
            }
            int x = this.leftPos + slot.x;
            int y = this.topPos + slot.y;
            g.fill(x, y, x + 16, y + 16, BLOXPIECE$VEIL);
            g.blit(RenderPipelines.GUI_TEXTURED, BLOXPIECE$LOCK, x, y, 0.0F, 0.0F, 16, 16, 16, 16, 0xFFFFFFFF);
        }
    }
}
