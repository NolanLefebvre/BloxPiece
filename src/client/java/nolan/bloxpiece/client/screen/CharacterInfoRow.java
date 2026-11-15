package nolan.bloxpiece.client.screen;

import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Une ligne du menu personnage : icône + nom de l'information à gauche, valeur à droite.
 * La valeur est calculée à chaque image à partir des vraies données du joueur : elle est donc toujours à jour.
 */
public final class CharacterInfoRow {

    /** Ce qui s'affiche à droite de la ligne. */
    public record Value(Component text, int color) {
    }

    private static final int COLOR_BACKGROUND = 0x40000000;
    private static final int COLOR_BACKGROUND_HOVER = 0x40FFD25A;
    private static final int COLOR_LABEL = 0xFF9A9AAA;
    private static final int ICON_SIZE = 16;

    private final Identifier icon;
    private final Component label;
    private final int accent;
    private final Function<LocalPlayer, Value> value;
    private @Nullable Function<LocalPlayer, ItemStack> itemIcon;

    /**
     * @param icon   texture 16x16 (ex : textures/gui/character/level.png)
     * @param accent couleur de la barre à gauche de la ligne
     * @param value  calcule la valeur à afficher pour ce joueur
     */
    public CharacterInfoRow(Identifier icon, Component label, int accent, Function<LocalPlayer, Value> value) {
        this.icon = icon;
        this.label = label;
        this.accent = accent;
        this.value = value;
    }

    /** Affiche un item à la place de l'icône quand il y en a un (sinon l'icône normale reste affichée). */
    public CharacterInfoRow withItemIcon(Function<LocalPlayer, ItemStack> itemIcon) {
        this.itemIcon = itemIcon;
        return this;
    }

    public void extract(GuiGraphicsExtractor g, Font font, LocalPlayer player, int x, int y, int w, int h, boolean hovered) {
        g.fill(x, y, x + w, y + h, hovered ? COLOR_BACKGROUND_HOVER : COLOR_BACKGROUND);
        g.fill(x, y, x + (hovered ? 2 : 1), y + h, accent); // barre d'accent : s'épaissit au survol

        int iconX = x + 5;
        int iconY = y + (h - ICON_SIZE) / 2;
        ItemStack stack = itemIcon != null ? itemIcon.apply(player) : ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            g.item(stack, iconX, iconY);
        } else {
            g.blit(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0.0F, 0.0F,
                    ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, 0xFFFFFFFF);
        }

        int textY = y + (h - font.lineHeight) / 2 + 1;
        g.text(font, label, x + 26, textY, COLOR_LABEL, true);

        Value current = value.apply(player);
        g.text(font, current.text(), x + w - 7 - font.width(current.text()), textY, current.color(), true);
    }
}
