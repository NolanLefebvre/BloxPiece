package nolan.bloxpiece.client.screen;

import java.util.List;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.progression.LevelCurve;
import nolan.bloxpiece.progression.PlayerProgress;
import nolan.bloxpiece.progression.Progression;

/**
 * Menu du personnage (touche G) : portrait, informations et barre d'expérience.
 * Il ne met jamais le jeu en pause. Tout est LU à chaque image depuis les vraies données du joueur
 * (attachments synchronisés par le serveur), donc il se met à jour tout seul.
 *
 * Le cadre, le titre, l'animation et les touches sont dans BloxPanelScreen (partagés avec les autres pages du menu).
 * Pour ajouter une information : voir CharacterInfoRows. Une ligne peut être cliquable (ex : le fruit ouvre la page des attaques).
 */
public class CharacterScreen extends BloxPanelScreen {

    // ---- Mise en page (pixels GUI, repère du panneau)
    private static final int PORTRAIT_X = PAD;
    private static final int PORTRAIT_Y = 34;
    private static final int PORTRAIT_SIZE = 80;
    private static final int FACE_SIZE = 64;

    private static final int ROWS_X = 104;
    private static final int ROWS_Y = 34;
    private static final int ROWS_W = PANEL_W - ROWS_X - PAD;
    private static final int ROW_H = 20;
    private static final int ROW_GAP = 2;

    private static final int XP_Y = 130;
    private static final int XP_BAR_H = 10;

    private static final int ICON_SIZE = 16;

    private final List<CharacterInfoRow> rows = CharacterInfoRows.create();

    // Barre d'XP animée : valeur affichée qui rattrape la valeur réelle
    private float shownXpFraction = 0.0F;
    private int shownLevel = -1;

    public CharacterScreen(KeyMapping closeKey) {
        super(Component.translatable("screen.bloxpiece.character.title"), closeKey, true);
    }

    @Override
    protected Component hint() {
        return Component.translatable("screen.bloxpiece.character.hint", closeKey.getTranslatedKeyMessage());
    }

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, Font font, LocalPlayer player,
                                int mx, int my, int mouseX, int mouseY, float dt) {
        drawPortrait(g, font, player, mx, my);
        drawRows(g, font, player, mx, my, mouseX, mouseY);
        drawExperience(g, font, player, mx, my, mouseX, mouseY, dt);
    }

    /** Clic sur une ligne cliquable (ex : le fruit) : elle fait son action. */
    @Override
    protected boolean onPanelClick(int mx, int my) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return false;
        }
        int y = ROWS_Y;
        for (CharacterInfoRow row : rows) {
            if (inside(mx, my, ROWS_X, y, ROWS_W, ROW_H)) {
                if (row.isClickable(player)) {
                    row.click(this);
                    return true;
                }
                return false;
            }
            y += ROW_H + ROW_GAP;
        }
        return false;
    }

    /** Ouvre la page des attaques du fruit (appelé par la ligne "fruit"). */
    void openAbilities() {
        playClickSound();
        this.minecraft.gui.setScreen(new AbilitiesScreen(this, closeKey));
    }

    // ------------------------------------------------------------------ éléments

    private void drawPortrait(GuiGraphicsExtractor g, Font font, LocalPlayer player, int mx, int my) {
        int x = PORTRAIT_X;
        int y = PORTRAIT_Y;
        int s = PORTRAIT_SIZE;
        boolean hovered = inside(mx, my, x, y, s, s);

        if (hovered) {
            g.outline(x - 2, y - 2, s + 4, s + 4, 0x66FFD25A); // halo au survol
        }

        g.fill(x, y, x + s, y + s, 0xFF07070C);
        g.fillGradient(x + 1, y + 1, x + s - 1, y + s - 1, 0xFF272C3D, 0xFF0D0E15);

        // Tête du joueur avec son skin actuel (suit automatiquement le joueur connecté)
        int faceX = x + (s - FACE_SIZE) / 2;
        int faceY = y + (s - FACE_SIZE) / 2 - 3;
        PlayerFaceExtractor.extractRenderState(g, player.getSkin(), faceX, faceY, FACE_SIZE);
        g.outline(faceX - 1, faceY - 1, FACE_SIZE + 2, FACE_SIZE + 2, 0xFF000000);

        g.outline(x, y, s, s, hovered ? COLOR_GOLD_BRIGHT : COLOR_GOLD);
        g.outline(x + 2, y + 2, s - 4, s - 4, COLOR_INNER_LINE);
        for (int[] corner : new int[][] {{x, y}, {x + s - 3, y}, {x, y + s - 3}, {x + s - 3, y + s - 3}}) {
            g.fill(corner[0], corner[1], corner[0] + 3, corner[1] + 3, COLOR_GOLD_BRIGHT);
        }

        // Pastille de niveau sur le bord bas du cadre
        PlayerProgress progress = Progression.get(player);
        Component badge = Component.translatable("screen.bloxpiece.character.level_badge", progress.level());
        int badgeW = font.width(badge) + 14;
        int badgeX = x + (s - badgeW) / 2;
        int badgeY = y + s - 7;
        g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 14, 0xFF0B0B12);
        g.outline(badgeX, badgeY, badgeW, 14, hovered ? COLOR_GOLD_BRIGHT : COLOR_GOLD);
        g.centeredText(font, badge, badgeX + badgeW / 2, badgeY + 3, COLOR_GOLD_BRIGHT);
    }

    private void drawRows(GuiGraphicsExtractor g, Font font, LocalPlayer player, int mx, int my,
                          int screenMouseX, int screenMouseY) {
        int y = ROWS_Y;
        for (CharacterInfoRow row : rows) {
            boolean hovered = inside(mx, my, ROWS_X, y, ROWS_W, ROW_H);
            row.extract(g, font, player, ROWS_X, y, ROWS_W, ROW_H, hovered);
            if (hovered && row.isClickable(player)) {
                g.setTooltipForNextFrame(font, row.clickTooltip(), screenMouseX, screenMouseY);
            }
            y += ROW_H + ROW_GAP;
        }
    }

    private void drawExperience(GuiGraphicsExtractor g, Font font, LocalPlayer player, int mx, int my,
                                int screenMouseX, int screenMouseY, float dt) {
        PlayerProgress progress = Progression.get(player);
        boolean max = LevelCurve.isMaxLevel(progress.level());
        long needed = LevelCurve.xpForNextLevel(progress.level());

        // Valeur réelle de la barre, et valeur affichée qui la rattrape en douceur
        float target = max ? 1.0F : (needed <= 0 ? 0.0F : Mth.clamp((float) progress.xp() / needed, 0.0F, 1.0F));
        if (progress.level() > shownLevel) {
            shownXpFraction = 0.0F; // nouveau niveau : la barre repart de zéro
            shownLevel = progress.level();
        }
        shownXpFraction += (target - shownXpFraction) * (1.0F - (float) Math.exp(-dt * 8.0F));

        // En-tête : icône + "Expérience" à gauche, valeurs à droite
        g.blit(RenderPipelines.GUI_TEXTURED, icon("xp"), PAD, XP_Y - 1, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, 0xFFFFFFFF);
        g.text(font, Component.translatable("screen.bloxpiece.character.xp"), PAD + 20, XP_Y + 4, COLOR_LABEL, true);
        Component values = max
                ? Component.translatable("screen.bloxpiece.character.xp_max")
                : Component.translatable("screen.bloxpiece.character.xp_values", progress.xp(), needed);
        g.text(font, values, PANEL_W - PAD - font.width(values), XP_Y + 4, max ? COLOR_GOLD_BRIGHT : 0xFFFFFFFF, true);

        // Barre
        int bx = PAD;
        int by = XP_Y + 18;
        int bw = PANEL_W - 2 * PAD;
        boolean hovered = inside(mx, my, bx, by, bw, XP_BAR_H);

        g.fill(bx, by, bx + bw, by + XP_BAR_H, 0xFF07070C);
        int fillW = Math.round((bw - 2) * shownXpFraction);
        if (fillW > 0) {
            int top = max ? 0xFFFFE27A : 0xFF55C7FF;
            int bottom = max ? 0xFFD99A1E : 0xFF1F6BE0;
            g.fillGradient(bx + 1, by + 1, bx + 1 + fillW, by + XP_BAR_H - 1, top, bottom);
            g.fill(bx + 1, by + 1, bx + 1 + fillW, by + 2, 0x70FFFFFF); // reflet

            // Pointe lumineuse qui pulse doucement
            float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 250.0);
            int glow = ((int) (0x60 + 0x9F * pulse)) << 24 | 0xFFFFFF;
            g.fill(bx + fillW, by + 1, bx + fillW + 1, by + XP_BAR_H - 1, glow);
        }
        for (int i = 1; i < 4; i++) { // graduations 25 / 50 / 75 %
            int tx = bx + bw * i / 4;
            g.fill(tx, by + 1, tx + 1, by + XP_BAR_H - 1, 0x55000000);
        }
        g.outline(bx, by, bw, XP_BAR_H, hovered ? COLOR_GOLD_BRIGHT : COLOR_GOLD_DIM);

        if (hovered) {
            Component tip = max
                    ? Component.translatable("screen.bloxpiece.character.xp_tooltip_max")
                    : Component.translatable("screen.bloxpiece.character.xp_tooltip", needed - progress.xp());
            g.setTooltipForNextFrame(font, tip, screenMouseX, screenMouseY);
        }
    }

    private static Identifier icon(String name) {
        return BloxPiece.id("textures/gui/character/" + name + ".png");
    }
}
