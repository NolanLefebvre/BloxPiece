package nolan.bloxpiece.client.screen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import nolan.bloxpiece.haki.HakiType;

/**
 * Page « Haki » du menu du personnage (bouton Haki) : même style que la page des attaques du fruit (AbilitiesScreen).
 * Une ligne par type de Haki (HakiRow) : statut de déblocage, description, maîtrise (base / maîtrisé) et attaque avancée
 * (débloquée par la maîtrise). La page grandit toute seule avec le contenu.
 *
 * Retour au menu personnage : bouton « Retour » ou Échap. La touche du menu (G) ferme tout.
 */
public class HakiScreen extends BloxPanelScreen {

    private static final int ROWS_X = PAD;
    private static final int ROWS_Y = 30;
    private static final int ROWS_W = PANEL_W - 2 * PAD;
    private static final int ROW_GAP = 3;

    private static final int BACK_W = 62;
    private static final int BACK_H = 14;
    private static final int BACK_X = PAD;
    private static final int BACK_MARGIN = 6;
    private static final int BOTTOM_SPACE = 10;

    private final CharacterScreen parent;

    public HakiScreen(CharacterScreen parent, KeyMapping closeKey) {
        super(Component.translatable("screen.bloxpiece.haki.title"), closeKey, false);
        this.parent = parent;
    }

    private int contentBottom() {
        Font font = this.minecraft.font;
        int y = ROWS_Y;
        for (HakiType type : HakiType.values()) {
            y += HakiRow.heightFor(font, type, ROWS_W) + ROW_GAP;
        }
        return y - ROW_GAP;
    }

    private int backY() {
        return contentBottom() + BACK_MARGIN;
    }

    @Override
    protected int panelHeight() {
        return Math.max(PANEL_H, backY() + BACK_H + BOTTOM_SPACE);
    }

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, Font font, LocalPlayer player,
                                int mx, int my, int mouseX, int mouseY, float dt) {
        int y = ROWS_Y;
        for (HakiType type : HakiType.values()) {
            int h = HakiRow.heightFor(font, type, ROWS_W);
            boolean hovered = inside(mx, my, ROWS_X, y, ROWS_W, h);
            HakiRow.extract(g, font, player, type, ROWS_X, y, ROWS_W, h, hovered);
            y += h + ROW_GAP;
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
                BACK_X + BACK_W / 2, backY + (BACK_H - font.lineHeight) / 2 + 1, hovered ? COLOR_GOLD_BRIGHT : COLOR_LABEL);
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

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
