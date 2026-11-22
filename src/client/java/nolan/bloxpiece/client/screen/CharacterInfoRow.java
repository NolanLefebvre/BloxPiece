package nolan.bloxpiece.client.screen;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

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

    // Visibles dans le package : les autres lignes du menu (ex : AbilityRow) reprennent le même style
    static final int COLOR_BACKGROUND = 0x40000000;
    static final int COLOR_BACKGROUND_HOVER = 0x40FFD25A;
    private static final int COLOR_LABEL = 0xFF9A9AAA;
    private static final int COLOR_LABEL_LOCKED = 0xFF5E5E6A;
    private static final int COLOR_BACKGROUND_LOCKED = 0x60000000;
    private static final int COLOR_ACCENT_LOCKED = 0xFF55555F;
    private static final int COLOR_CLICK_HINT = 0xFFC8A24A;
    private static final int COLOR_CLICK_HINT_HOVER = 0xFFFFD25A;
    private static final int ICON_SIZE = 16;

    private final Identifier icon;
    private final Component label;
    private final int accent;
    private final Function<LocalPlayer, Value> value;
    private @Nullable Function<LocalPlayer, ItemStack> itemIcon;
    private @Nullable Function<LocalPlayer, Identifier> iconTexture;
    private @Nullable Function<LocalPlayer, Component> dynamicTooltip;

    // Ligne cliquable (facultatif) : quand actif, un clic dessus déclenche l'action
    private @Nullable Predicate<LocalPlayer> clickEnabled;
    private @Nullable Consumer<CharacterScreen> onClick;
    private @Nullable Component clickTooltip;

    // Ligne verrouillée (facultatif) : grisée, cadenas sur l'icône, pas de clic, infobulle qui donne le prérequis
    private @Nullable Predicate<LocalPlayer> lockedWhen;
    private @Nullable Function<LocalPlayer, Component> lockTooltip;

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

    /** Icône qui dépend du joueur (ex : l'icône de SA race) à la place de l'icône fixe. */
    public CharacterInfoRow withIconTexture(Function<LocalPlayer, Identifier> iconTexture) {
        this.iconTexture = iconTexture;
        return this;
    }

    /** Infobulle qui dépend du joueur (ex : description de sa race), à la place de l'infobulle fixe du clic. */
    public CharacterInfoRow withTooltip(Function<LocalPlayer, Component> tooltip) {
        this.dynamicTooltip = tooltip;
        return this;
    }

    /**
     * Rend la ligne cliquable (icône, nom et valeur). Un ">" apparaît à côté du nom et une infobulle explique le clic.
     *
     * @param enabled dit si le clic est possible pour ce joueur (ex : il faut avoir un fruit)
     * @param action  exécutée au clic ; reçoit le menu pour pouvoir ouvrir une autre page
     */
    public CharacterInfoRow withClick(Component tooltip, Predicate<LocalPlayer> enabled, Consumer<CharacterScreen> action) {
        this.clickTooltip = tooltip;
        this.clickEnabled = enabled;
        this.onClick = action;
        return this;
    }

    /** Verrouille la ligne tant que {@code locked} est vrai (ex : niveau trop bas), avec une infobulle qui dit pourquoi. */
    public CharacterInfoRow withLock(Predicate<LocalPlayer> locked, Function<LocalPlayer, Component> tooltip) {
        this.lockedWhen = locked;
        this.lockTooltip = tooltip;
        return this;
    }

    public boolean isLocked(LocalPlayer player) {
        return lockedWhen != null && lockedWhen.test(player);
    }

    public Component lockTooltip(LocalPlayer player) {
        return lockTooltip != null ? lockTooltip.apply(player) : Component.empty();
    }

    /** Cette ligne réagit-elle au clic pour ce joueur ? (jamais quand elle est verrouillée) */
    public boolean isClickable(LocalPlayer player) {
        return onClick != null && clickEnabled != null && clickEnabled.test(player) && !isLocked(player);
    }

    public void click(CharacterScreen screen) {
        if (onClick != null) {
            onClick.accept(screen);
        }
    }

    /** Infobulle à afficher au survol d'une ligne cliquable. */
    public Component clickTooltip() {
        return clickTooltip != null ? clickTooltip : Component.empty();
    }

    /** Infobulle au survol pour ce joueur : l'infobulle propre au joueur si la ligne en a une, sinon celle du clic. */
    public Component clickTooltip(LocalPlayer player) {
        return dynamicTooltip != null ? dynamicTooltip.apply(player) : clickTooltip();
    }

    public void extract(GuiGraphicsExtractor g, Font font, LocalPlayer player, int x, int y, int w, int h, boolean hovered) {
        boolean locked = isLocked(player);
        if (locked) {
            hovered = false; // ligne verrouillée : pas de surbrillance (l'infobulle donne le prérequis)
        }
        g.fill(x, y, x + w, y + h, locked ? COLOR_BACKGROUND_LOCKED : hovered ? COLOR_BACKGROUND_HOVER : COLOR_BACKGROUND);
        g.fill(x, y, x + (hovered ? 2 : 1), y + h, locked ? COLOR_ACCENT_LOCKED : accent); // barre d'accent : s'épaissit au survol

        int iconX = x + 5;
        int iconY = y + (h - ICON_SIZE) / 2;
        ItemStack stack = itemIcon != null ? itemIcon.apply(player) : ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            g.item(stack, iconX, iconY);
        } else {
            g.blit(RenderPipelines.GUI_TEXTURED, iconTexture != null ? iconTexture.apply(player) : icon, iconX, iconY, 0.0F, 0.0F,
                    ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, locked ? 0x60FFFFFF : 0xFFFFFFFF);
        }
        if (locked) {
            g.blit(RenderPipelines.GUI_TEXTURED, nolan.bloxpiece.client.hud.AbilityHud.LOCK_ICON, iconX, iconY, 0.0F, 0.0F,
                    ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, 0xFFFFFFFF);
        }

        int textY = y + (h - font.lineHeight) / 2 + 1;
        g.text(font, label, x + 26, textY, locked ? COLOR_LABEL_LOCKED : COLOR_LABEL, true);
        int labelEnd = x + 26 + font.width(label);
        if (isClickable(player)) {
            g.text(font, ">", labelEnd + 4, textY, hovered ? COLOR_CLICK_HINT_HOVER : COLOR_CLICK_HINT, true);
            labelEnd += 4 + font.width(">");
        }

        // Valeur alignée à droite ; si elle est trop longue pour la place restante, elle est raccourcie ("...")
        // au lieu de passer par-dessus le libellé
        Value current = value.apply(player);
        int available = x + w - 7 - (labelEnd + 8);
        Component text = current.text();
        if (font.width(text) > available) {
            String ellipsis = "...";
            text = Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(0, available - font.width(ellipsis))) + ellipsis);
        }
        g.text(font, text, x + w - 7 - font.width(text), textY, current.color(), true);
    }
}
