package nolan.bloxpiece.client.screen;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import nolan.bloxpiece.ability.Ability;
import nolan.bloxpiece.fruit.DevilFruit;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.progression.Progression;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Page des attaques du fruit du démon du joueur (on y arrive en cliquant sur le fruit dans le menu personnage).
 * Une ligne par attaque du fruit (nom, description, niveau requis) ; les attaques verrouillées portent un cadenas.
 *
 * Tout est LU à chaque image : le fruit vient de l'attachment du joueur, les attaques viennent de DevilFruit
 * et le déblocage du niveau du joueur (Ability.isUnlockedFor). Rien à mettre à jour à la main.
 *
 * Retour au menu personnage : bouton "Retour" ou Échap. La touche du menu (G) ferme tout.
 */
public class AbilitiesScreen extends BloxPanelScreen {

    // La hauteur du panneau s'adapte au contenu (chaque ligne s'adapte à sa description, voir AbilityRow.heightFor) :
    // rien ne déborde jamais sur le bouton Retour
    private static final int MIN_PANEL_HEIGHT = PANEL_H;

    private static final int ROWS_X = PAD;
    private static final int ROWS_Y = 30;
    private static final int ROWS_W = PANEL_W - 2 * PAD;
    private static final int ROW_GAP = 3;

    private static final int BACK_W = 62;
    private static final int BACK_H = 14;
    private static final int BACK_X = PAD;
    /** Espace entre la dernière attaque et le bouton Retour, puis sous le bouton. */
    private static final int BACK_MARGIN = 6;
    private static final int BOTTOM_SPACE = 10;

    private final CharacterScreen parent;

    public AbilitiesScreen(CharacterScreen parent, KeyMapping closeKey) {
        // Pas d'animation d'ouverture : on change de page, on ne rouvre pas le menu
        super(Component.translatable("screen.bloxpiece.abilities.title"), closeKey, false);
        this.parent = parent;
    }

    @Override
    protected int panelHeight() {
        return Math.max(MIN_PANEL_HEIGHT, backY() + BACK_H + BOTTOM_SPACE);
    }

    /** Bas de la liste des attaques (ou de la zone de message s'il n'y en a pas). */
    private int contentBottom() {
        LocalPlayer player = this.minecraft.player;
        DevilFruit fruit = player == null ? null : currentFruit(player);
        if (fruit == null || !fruit.hasAbilities()) {
            return ROWS_Y + 40;
        }
        Font font = this.minecraft.font;
        int y = ROWS_Y;
        for (int slot = 0; slot < DevilFruit.MAX_ABILITIES; slot++) {
            Ability ability = fruit.getAbility(slot);
            if (ability != null) {
                y += AbilityRow.heightFor(font, ability, ROWS_W) + ROW_GAP;
            }
        }
        return y - ROW_GAP;
    }

    /** Le bouton Retour, juste sous la dernière attaque, sur sa propre ligne. */
    private int backY() {
        return contentBottom() + BACK_MARGIN;
    }

    @Override
    protected Component titleFor(LocalPlayer player) {
        DevilFruit fruit = currentFruit(player);
        if (fruit == null) {
            return getTitle();
        }
        return Component.translatable("screen.bloxpiece.abilities.title_fruit", Component.translatable(fruit.translationKey()).withColor(0xC060FF));
    }

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, Font font, LocalPlayer player,
                                int mx, int my, int mouseX, int mouseY, float dt) {
        DevilFruit fruit = currentFruit(player);

        if (fruit == null) {
            // Le fruit a disparu pendant que la page était ouverte (ex : /removedf)
            g.centeredText(font, Component.translatable("screen.bloxpiece.character.no_fruit"),
                    PANEL_W / 2, ROWS_Y + 16, COLOR_LABEL);
        } else if (!fruit.hasAbilities()) {
            // Fruit sans attaque (pas encore développées) : on l'indique plutôt que d'afficher une page vide
            g.centeredText(font, Component.translatable("screen.bloxpiece.abilities.none"),
                    PANEL_W / 2, ROWS_Y + 16, COLOR_LABEL);
        } else {
            Component tooltip = null;
            int y = ROWS_Y;
            for (int slot = 0; slot < DevilFruit.MAX_ABILITIES; slot++) {
                Ability ability = fruit.getAbility(slot);
                if (ability == null) {
                    continue; // le fruit a moins de 4 attaques : pas de ligne vide
                }
                int rowH = AbilityRow.heightFor(font, ability, ROWS_W);
                boolean hovered = inside(mx, my, ROWS_X, y, ROWS_W, rowH);
                AbilityRow.extract(g, font, player, fruit, ability, ROWS_X, y, ROWS_W, rowH, hovered);
                if (hovered && !ability.isUnlockedFor(player)) {
                    tooltip = Component.translatable("screen.bloxpiece.abilities.locked_tooltip",
                            ability.requiredLevel(), Progression.get(player).level());
                }
                y += rowH + ROW_GAP;
            }
            if (tooltip != null) {
                g.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
            }
        }

        drawBackButton(g, font, mx, my);
    }

    private void drawBackButton(GuiGraphicsExtractor g, Font font, int mx, int my) {
        int backY = backY();
        boolean hovered = inside(mx, my, BACK_X, backY, BACK_W, BACK_H);
        g.fill(BACK_X, backY, BACK_X + BACK_W, backY + BACK_H,
                hovered ? CharacterInfoRow.COLOR_BACKGROUND_HOVER : CharacterInfoRow.COLOR_BACKGROUND);
        g.outline(BACK_X, backY, BACK_W, BACK_H, hovered ? COLOR_GOLD_BRIGHT : COLOR_GOLD_DIM);
        g.centeredText(font, Component.translatable("screen.bloxpiece.abilities.back"),
                BACK_X + BACK_W / 2, backY + (BACK_H - font.lineHeight) / 2 + 1,
                hovered ? COLOR_GOLD_BRIGHT : COLOR_LABEL);
    }

    @Override
    protected boolean onPanelClick(int mx, int my) {
        if (inside(mx, my, BACK_X, backY(), BACK_W, BACK_H)) {
            playClickSound();
            onClose();
            return true;
        }
        return false;
    }

    /** Échap (et le bouton Retour) : retour au menu personnage, qui reprend là où il en était. */
    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private static @Nullable DevilFruit currentFruit(LocalPlayer player) {
        return Fruits.get(player.getAttached(ModAttachments.FRUIT));
    }
}
