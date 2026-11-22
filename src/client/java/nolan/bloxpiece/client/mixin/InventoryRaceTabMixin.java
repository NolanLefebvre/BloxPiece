package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import nolan.bloxpiece.race.Race;
import nolan.bloxpiece.race.Races;

/**
 * Inventaire du joueur (InventoryScreen, l'inventaire normal : pas de nouvel inventaire) : un onglet avec l'icône de SA race
 * au-dessus du coin haut droit de la fenêtre. Survol : nom de la race ; clic : ouvre le menu Race (RaceScreen), qui revient à
 * l'inventaire quand on le ferme. Il suit la fenêtre (livre de recettes ouvert compris : leftPos). Mixin facultatif
 * (bloxpiece.client.optional.mixins.json) : sans effet, mais sans plantage, si ces méthodes changent de nom.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class InventoryRaceTabMixin {

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    protected int imageWidth;

    private static final int BLOXPIECE$TAB = 22;

    private int bloxpiece$tabX() {
        return this.leftPos + this.imageWidth - BLOXPIECE$TAB - 4;
    }

    private int bloxpiece$tabY() {
        return this.topPos - BLOXPIECE$TAB + 2;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void bloxpiece$drawRaceTab(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!((Object) this instanceof InventoryScreen) || player == null) {
            return;
        }
        Race race = Races.of(player);
        int x = bloxpiece$tabX(), y = bloxpiece$tabY();
        boolean hovered = mouseX >= x && mouseX < x + BLOXPIECE$TAB && mouseY >= y && mouseY < y + BLOXPIECE$TAB;
        // onglet dans le style des menus BloxPiece : fond sombre, filet doré, icône de la race
        g.fill(x, y, x + BLOXPIECE$TAB, y + BLOXPIECE$TAB, 0xF2101018);
        g.outline(x, y, BLOXPIECE$TAB, BLOXPIECE$TAB, hovered ? 0xFFFFD25A : 0xFFC8A24A);
        g.blit(RenderPipelines.GUI_TEXTURED, race.icon(), x + 3, y + 3, 0.0F, 0.0F, 16, 16, 16, 16, 0xFFFFFFFF);
        if (hovered) {
            g.setTooltipForNextFrame(Minecraft.getInstance().font, Component.translatable("screen.bloxpiece.race.inventory_tab",
                    Component.translatable(race.translationKey())), mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void bloxpiece$clickRaceTab(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> info) {
        if (!((Object) this instanceof InventoryScreen inventory) || event.button() != 1) {
            return; // clic gauche seulement (SDL : bouton 1, comme BloxPanelScreen)
        }
        int x = bloxpiece$tabX(), y = bloxpiece$tabY();
        if (event.x() >= x && event.x() < x + BLOXPIECE$TAB && event.y() >= y && event.y() < y + BLOXPIECE$TAB) {
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            mc.gui.setScreen(new nolan.bloxpiece.client.screen.RaceScreen(inventory,
                    nolan.bloxpiece.client.BloxPieceClient.characterKey()));
            info.setReturnValue(true);
        }
    }
}
