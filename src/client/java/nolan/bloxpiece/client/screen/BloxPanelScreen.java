package nolan.bloxpiece.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Le style commun de toutes les pages du menu personnage : fond assombri, panneau doré centré, titre orné,
 * animation d'ouverture, touche pour fermer. Une page (CharacterScreen, AbilitiesScreen...) n'écrit que son contenu.
 *
 * Tout est dessiné dans un repère fixe PANEL_W x PANEL_H ; si l'écran est plus petit, le panneau est réduit.
 * Les coordonnées reçues par une page (mx, my) sont déjà dans ce repère. Le menu ne met jamais le jeu en pause.
 */
public abstract class BloxPanelScreen extends Screen {

    // ---- Mise en page (pixels GUI, repère du panneau)
    protected static final int PANEL_W = 300;
    protected static final int PANEL_H = 176;
    protected static final int PAD = 12;

    // ---- Couleurs (ARGB) : fond sombre, touches d'or
    protected static final int COLOR_PANEL = 0xF2101018;
    protected static final int COLOR_GOLD = 0xFFC8A24A;
    protected static final int COLOR_GOLD_DIM = 0xFF6B5A2E;
    protected static final int COLOR_GOLD_BRIGHT = 0xFFFFD25A;
    protected static final int COLOR_INNER_LINE = 0xFF2A2418;
    protected static final int COLOR_LABEL = 0xFF9A9AAA;
    protected static final int COLOR_HINT = 0xFF6E6E7A;

    private static final float OPEN_DURATION_MS = 180.0F;

    // Numéro du clic gauche : depuis le passage à SDL, c'est 1 (et non plus 0 comme avec GLFW).
    // C'est la même valeur que celle testée par les boutons de Minecraft (AbstractWidget.isValidClickButton).
    private static final int LEFT_MOUSE_BUTTON = 1;

    protected final KeyMapping closeKey;

    // G ouvre le menu : on n'accepte "G pour fermer" qu'après le relâchement de la touche (sinon la répétition de touche refermerait le menu)
    private boolean closeArmed;

    private final long openedAtMs = System.currentTimeMillis();
    private final boolean animated;
    private long lastFrameNs = System.nanoTime();

    // Position et échelle du panneau à la dernière image : sert à convertir un clic de souris en coordonnées du panneau
    private float originX;
    private float originY;
    private float scale = 1.0F;

    /**
     * @param animated true = animation d'ouverture ; false = la page apparaît directement
     *                 (quand on passe d'une page du menu à une autre, pour ne pas rejouer l'ouverture)
     */
    protected BloxPanelScreen(Component title, KeyMapping closeKey, boolean animated) {
        super(title);
        this.closeKey = closeKey;
        this.animated = animated;
        this.closeArmed = !closeKey.isDown();
    }

    // ------------------------------------------------------------------ à écrire dans chaque page

    /** Dessine le contenu de la page (le cadre, le titre, l'indication du bas et le fondu sont déjà gérés). */
    protected abstract void extractPanel(GuiGraphicsExtractor g, Font font, LocalPlayer player,
                                         int mx, int my, int screenMouseX, int screenMouseY, float dt);

    /** Texte d'aide affiché en bas du panneau. */
    protected abstract Component hint();

    /** Titre affiché en haut du panneau (par défaut : le titre de la page ; une page peut l'adapter au joueur). */
    protected Component titleFor(LocalPlayer player) {
        return getTitle();
    }

    /** Clic gauche aux coordonnées (mx, my) du panneau. Renvoie true si la page l'a utilisé. */
    protected boolean onPanelClick(int mx, int my) {
        return false;
    }

    /** Ferme tout le menu (touche du menu). Échap, lui, appelle onClose() : une page peut y revenir à la page précédente. */
    protected void closeAll() {
        super.onClose();
    }

    // ------------------------------------------------------------------ comportement commun

    // Le jeu continue de tourner derrière le menu (important en multijoueur)
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closeKey.matches(event) && closeArmed) {
            closeAll();
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == LEFT_MOUSE_BUTTON) {
            int mx = Math.round((float) ((event.x() - originX) / scale));
            int my = Math.round((float) ((event.y() - originY) / scale));
            if (onPanelClick(mx, my)) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
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

        float open = animated
                ? easeOut(Mth.clamp((System.currentTimeMillis() - openedAtMs) / OPEN_DURATION_MS, 0.0F, 1.0F))
                : 1.0F;

        // Fond assombri (sans flou : on garde le jeu visible derrière)
        g.fill(0, 0, this.width, this.height, ((int) (0x99 * open)) << 24);

        // Panneau centré, réduit automatiquement si l'écran est petit, avec une légère montée à l'ouverture
        scale = Math.min(1.0F, Math.min((this.width - 8) / (float) PANEL_W, (this.height - 8) / (float) PANEL_H));
        originX = (this.width - PANEL_W * scale) / 2.0F;
        originY = (this.height - PANEL_H * scale) / 2.0F + (1.0F - open) * 10.0F * scale;
        int mx = Math.round((mouseX - originX) / scale);
        int my = Math.round((mouseY - originY) / scale);

        g.pose().pushMatrix();
        g.pose().translate(originX, originY);
        g.pose().scale(scale);

        drawFrame(g, 0, 0, PANEL_W, PANEL_H);
        drawTitle(g, mc.font, titleFor(player));
        extractPanel(g, mc.font, player, mx, my, mouseX, mouseY, dt);
        g.centeredText(mc.font, hint(), PANEL_W / 2, PANEL_H - 14, COLOR_HINT);

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

    /** Petit "clic" d'interface, à jouer quand le joueur clique sur un élément du menu. */
    protected void playClickSound() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ------------------------------------------------------------------ dessin générique

    protected static void drawTitle(GuiGraphicsExtractor g, Font font, Component rawTitle) {
        Component title = rawTitle.copy().withStyle(ChatFormatting.BOLD);
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

    protected static void drawFrame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
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

    protected static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected static float easeOut(float t) {
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }
}
