package nolan.bloxpiece.client.screen;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
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
 * Tout est dessiné dans un repère fixe PANEL_W x PANEL_H ; si l'écran est plus petit, le panneau est réduit.
 * Pour ajouter une information : voir CharacterInfoRows.
 */
public class CharacterScreen extends Screen {

    // ---- Mise en page (pixels GUI, repère du panneau)
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 176;
    private static final int PAD = 12;

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

    // ---- Couleurs (ARGB) : fond sombre, touches d'or
    private static final int COLOR_PANEL = 0xF2101018;
    private static final int COLOR_GOLD = 0xFFC8A24A;
    private static final int COLOR_GOLD_DIM = 0xFF6B5A2E;
    private static final int COLOR_GOLD_BRIGHT = 0xFFFFD25A;
    private static final int COLOR_INNER_LINE = 0xFF2A2418;
    private static final int COLOR_LABEL = 0xFF9A9AAA;
    private static final int COLOR_HINT = 0xFF6E6E7A;

    private static final float OPEN_DURATION_MS = 180.0F;

    private static final int ICON_SIZE = 16;

    private final KeyMapping closeKey;
    private final List<CharacterInfoRow> rows = CharacterInfoRows.create();

    // G ouvre le menu : on n'accepte "G pour fermer" qu'après le relâchement de la touche (sinon la répétition de touche refermerait le menu)
    private boolean closeArmed;

    private final long openedAtMs = System.currentTimeMillis();
    private long lastFrameNs = System.nanoTime();

    // Barre d'XP animée : valeur affichée qui rattrape la valeur réelle
    private float shownXpFraction = 0.0F;
    private int shownLevel = -1;

    public CharacterScreen(KeyMapping closeKey) {
        super(Component.translatable("screen.bloxpiece.character.title"));
        this.closeKey = closeKey;
        this.closeArmed = !closeKey.isDown();
    }

    // Le jeu continue de tourner derrière le menu (important en multijoueur)
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closeKey.matches(event) && closeArmed) {
            onClose();
            return true;
        }
        return super.keyPressed(event); // Échap est géré par Screen
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (closeKey.matches(event)) {
            closeArmed = true;
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        long nowNs = System.nanoTime();
        float dt = Mth.clamp((nowNs - lastFrameNs) / 1.0E9F, 0.0F, 0.1F);
        lastFrameNs = nowNs;

        float open = easeOut(Mth.clamp((System.currentTimeMillis() - openedAtMs) / OPEN_DURATION_MS, 0.0F, 1.0F));

        // Fond assombri (sans flou : on garde le jeu visible derrière)
        g.fill(0, 0, this.width, this.height, ((int) (0x99 * open)) << 24);

        // Panneau centré, réduit automatiquement si l'écran est petit, avec une légère montée à l'ouverture
        float scale = Math.min(1.0F, Math.min((this.width - 8) / (float) PANEL_W, (this.height - 8) / (float) PANEL_H));
        float originX = (this.width - PANEL_W * scale) / 2.0F;
        float originY = (this.height - PANEL_H * scale) / 2.0F + (1.0F - open) * 10.0F * scale;
        int mx = Math.round((mouseX - originX) / scale);
        int my = Math.round((mouseY - originY) / scale);

        g.pose().pushMatrix();
        g.pose().translate(originX, originY);
        g.pose().scale(scale);

        drawFrame(g, 0, 0, PANEL_W, PANEL_H);
        drawTitle(g, mc.font);
        drawPortrait(g, mc.font, player, mx, my);
        drawRows(g, mc.font, player, mx, my);
        drawExperience(g, mc.font, player, mx, my, mouseX, mouseY, dt);
        g.centeredText(mc.font, Component.translatable("screen.bloxpiece.character.hint", closeKey.getTranslatedKeyMessage()),
                PANEL_W / 2, PANEL_H - 14, COLOR_HINT);

        // Fondu à l'ouverture
        int fade = (int) ((1.0F - open) * 255.0F);
        if (fade > 0) {
            g.fill(0, 0, PANEL_W, PANEL_H, fade << 24);
        }

        g.pose().popMatrix();
    }

    // Fond personnalisé (dessiné dans extractRenderState) : on neutralise celui de Screen
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    // ------------------------------------------------------------------ éléments

    private void drawTitle(GuiGraphicsExtractor g, Font font) {
        Component title = getTitle().copy().withStyle(ChatFormatting.BOLD);
        int half = font.width(title) / 2;
        int cx = PANEL_W / 2;

        g.centeredText(font, title, cx, 9, COLOR_GOLD_BRIGHT);

        // Ornements de part et d'autre du titre + filet sous l'en-tête
        g.fill(PAD, 13, cx - half - 10, 14, COLOR_GOLD_DIM);
        g.fill(cx + half + 10, 13, PANEL_W - PAD, 14, COLOR_GOLD_DIM);
        g.fill(cx - half - 8, 12, cx - half - 5, 15, COLOR_GOLD);
        g.fill(cx + half + 5, 12, cx + half + 8, 15, COLOR_GOLD);
        g.fill(PAD, 24, PANEL_W - PAD, 25, COLOR_GOLD_DIM);
        g.fill(PANEL_W / 4, 24, PANEL_W * 3 / 4, 25, COLOR_GOLD);
    }

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

    private void drawRows(GuiGraphicsExtractor g, Font font, LocalPlayer player, int mx, int my) {
        int y = ROWS_Y;
        for (CharacterInfoRow row : rows) {
            row.extract(g, font, player, ROWS_X, y, ROWS_W, ROW_H, inside(mx, my, ROWS_X, y, ROWS_W, ROW_H));
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

    // ------------------------------------------------------------------ dessin générique

    private static void drawFrame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x88000000); // ombre portée
        g.fill(x, y, x + w, y + h, COLOR_PANEL);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0x1CFFFFFF, 0x00000000);
        g.outline(x, y, w, h, COLOR_GOLD_DIM);
        g.outline(x + 2, y + 2, w - 4, h - 4, COLOR_INNER_LINE);

        // Coins dorés
        int l = 8;
        g.fill(x - 1, y - 1, x - 1 + l, y + 1, COLOR_GOLD);
        g.fill(x - 1, y - 1, x + 1, y - 1 + l, COLOR_GOLD);
        g.fill(x + w + 1 - l, y - 1, x + w + 1, y + 1, COLOR_GOLD);
        g.fill(x + w - 1, y - 1, x + w + 1, y - 1 + l, COLOR_GOLD);
        g.fill(x - 1, y + h - 1, x - 1 + l, y + h + 1, COLOR_GOLD);
        g.fill(x - 1, y + h + 1 - l, x + 1, y + h + 1, COLOR_GOLD);
        g.fill(x + w + 1 - l, y + h - 1, x + w + 1, y + h + 1, COLOR_GOLD);
        g.fill(x + w - 1, y + h + 1 - l, x + w + 1, y + h + 1, COLOR_GOLD);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float easeOut(float t) {
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    private static Identifier icon(String name) {
        return BloxPiece.id("textures/gui/character/" + name + ".png");
    }
}
