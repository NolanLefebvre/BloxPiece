package nolan.bloxpiece.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Titre d'un groupe de cases du HUD (« Haki » au-dessus des Hakis, nom du fruit au-dessus des attaques) : texte centré sur le
 * groupe, ombré, avec deux fins traits dégradés de chaque côté quand il reste de la place. Le texte ne sort jamais de
 * [minX, maxX] : il est décalé, puis raccourci avec « … » si besoin. Même hauteur des deux côtés de la hotbar.
 */
public final class HudLabel {

    /** Distance entre le haut des cases et le haut du texte (laisse la place de la jauge de Haki entre les deux). */
    public static final int OFFSET_ABOVE_SLOTS = 17;

    private static final int LINE_GAP = 3;
    private static final int MIN_LINE = 4;

    private HudLabel() {
    }

    /**
     * @param groupX  bord gauche du groupe de cases
     * @param groupW  largeur du groupe
     * @param slotsTop haut de la rangée de cases la plus haute
     */
    public static void draw(GuiGraphicsExtractor g, Font font, String text, int groupX, int groupW, int slotsTop,
                            int minX, int maxX, int color, int accent) {
        int available = Math.max(0, maxX - minX);
        if (font.width(text) > available) {
            text = font.plainSubstrByWidth(text, Math.max(0, available - font.width("…"))) + "…";
        }
        int w = font.width(text);
        int center = groupX + groupW / 2;
        int x = Math.max(minX, Math.min(maxX - w, center - w / 2));
        int y = slotsTop - OFFSET_ABOVE_SLOTS;
        g.text(font, text, x, y, color, true);

        // traits décoratifs, qui s'estompent vers l'extérieur, s'ils tiennent dans la largeur du groupe
        int lineY = y + 4;
        int leftEnd = x - LINE_GAP;
        int rightStart = x + w + LINE_GAP;
        int leftLen = leftEnd - groupX;
        int rightLen = groupX + groupW - rightStart;
        int len = Math.min(leftLen, rightLen);
        if (len >= MIN_LINE) {
            fadeLine(g, leftEnd - len, lineY, len, accent, true);
            fadeLine(g, rightStart, lineY, len, accent, false);
        }
    }

    /** Ligne de 1 px dont l'opacité diminue vers l'extérieur (fadeLeft : plus transparente à gauche). */
    private static void fadeLine(GuiGraphicsExtractor g, int x, int y, int len, int color, boolean fadeLeft) {
        int baseAlpha = color >>> 24;
        int rgb = color & 0xFFFFFF;
        for (int i = 0; i < len; i++) {
            float t = (i + 1) / (float) len;          // 0 -> 1 de gauche à droite
            float k = fadeLeft ? t : 1.0F - t + 1.0F / len;
            int a = (int) (baseAlpha * Math.min(1.0F, k));
            if (a > 0) {
                g.fill(x + i, y, x + i + 1, y + 1, (a << 24) | rgb);
            }
        }
        // petit losange à l'extrémité côté texte
        int tip = fadeLeft ? x + len - 1 : x;
        g.fill(tip, y - 1, tip + 1, y + 2, (baseAlpha << 24) | rgb);
    }
}
