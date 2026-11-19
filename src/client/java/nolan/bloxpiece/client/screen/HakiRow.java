package nolan.bloxpiece.client.screen;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.client.hud.AbilityHud;
import nolan.bloxpiece.haki.HakiManager;
import nolan.bloxpiece.haki.HakiMastery;
import nolan.bloxpiece.haki.HakiType;

/**
 * Une ligne de la page « Haki » : icône, nom, statut (débloqué / verrouillé), description, maîtrise (base ou maîtrisé) et
 * l'attaque avancée de ce Haki (débloquée par la maîtrise).
 * Même style que les lignes de la page des attaques du fruit (AbilityRow). Tout est lu à chaque image (attachments du
 * joueur synchronisés par le serveur).
 *
 * La hauteur de la ligne s'adapte toute seule à la description et à celle de l'attaque avancée (heightFor).
 */
final class HakiRow {

    private static final int ICON_FRAME = 26;
    private static final int ICON_SIZE = 16;
    private static final int TEXT_X = 38;
    private static final int RIGHT_PAD = 7;
    private static final int DESCRIPTION_Y = 16;
    private static final int MAX_DESCRIPTION_LINES = 6;
    private static final int MASTERY_H = 12;      // ligne « Maîtrise : ... »
    private static final int ATTACK_GAP = 3;      // espace avant le bloc de l'attaque avancée
    private static final int ATTACK_INDENT = 6;
    private static final int BOTTOM_PAD = 5;

    private static final int COLOR_ACCENT = 0xFF9B5CFF;          // violet du Haki
    private static final int COLOR_ACCENT_LOCKED = 0xFF55555F;
    private static final int COLOR_BACKGROUND_LOCKED = 0x60000000;
    private static final int COLOR_FRAME = 0xFF07070C;
    private static final int COLOR_FRAME_BORDER = 0xFFC8A24A;
    private static final int COLOR_FRAME_BORDER_LOCKED = 0xFF6B5A2E;
    private static final int COLOR_NAME = 0xFFFFFFFF;
    private static final int COLOR_NAME_LOCKED = 0xFF8A8A96;
    private static final int COLOR_DESCRIPTION = 0xFFB8B8C4;
    private static final int COLOR_DESCRIPTION_LOCKED = 0xFF6E6E7A;
    private static final int COLOR_UNLOCKED = 0xFF6BE08A;
    private static final int COLOR_LOCKED = 0xFFE06B6B;
    private static final int COLOR_BASE = 0xFFB8B8C4;
    private static final int COLOR_ATTACK_BG = 0x40000000;
    private static final int COLOR_ATTACK_NAME = 0xFFD9C8FF;

    private HakiRow() {
    }

    private static Identifier icon(HakiType type) {
        return BloxPiece.id("textures/gui/haki/" + type.id + ".png");
    }

    private static List<FormattedCharSequence> descriptionLines(Font font, HakiType type, int w) {
        return font.split(Component.translatable(type.descriptionKey()), w - RIGHT_PAD - TEXT_X);
    }

    private static List<FormattedCharSequence> attackLines(Font font, HakiType type, int w) {
        return font.split(Component.translatable(HakiMastery.advancedAttack(type).descriptionKey()),
                w - RIGHT_PAD - TEXT_X - ATTACK_INDENT * 2);
    }

    static int heightFor(Font font, HakiType type, int w) {
        int lines = Math.min(MAX_DESCRIPTION_LINES, descriptionLines(font, type, w).size());
        int attack = 4 + font.lineHeight + Math.min(4, attackLines(font, type, w).size()) * font.lineHeight + 3;
        return DESCRIPTION_Y + lines * font.lineHeight + 3 + MASTERY_H + ATTACK_GAP + attack + BOTTOM_PAD;
    }

    static void extract(GuiGraphicsExtractor g, Font font, LocalPlayer player, HakiType type, int x, int y, int w, int h,
                        boolean hovered) {
        boolean unlocked = HakiManager.isUnlocked(player, type);
        int background = hovered ? CharacterInfoRow.COLOR_BACKGROUND_HOVER
                : unlocked ? CharacterInfoRow.COLOR_BACKGROUND : COLOR_BACKGROUND_LOCKED;
        g.fill(x, y, x + w, y + h, background);
        g.fill(x, y, x + (hovered ? 2 : 1), y + h, unlocked ? COLOR_ACCENT : COLOR_ACCENT_LOCKED);

        // icône dans son cadre (cadenas par-dessus si verrouillé)
        int frameX = x + 6;
        int frameY = y + 6;
        int iconX = frameX + (ICON_FRAME - ICON_SIZE) / 2;
        int iconY = frameY + (ICON_FRAME - ICON_SIZE) / 2;
        g.fill(frameX, frameY, frameX + ICON_FRAME, frameY + ICON_FRAME, COLOR_FRAME);
        int alpha = unlocked ? 0xFF : 0x50;
        g.blit(RenderPipelines.GUI_TEXTURED, icon(type), iconX, iconY, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE,
                (alpha << 24) | 0xFFFFFF);
        if (!unlocked) {
            g.blit(RenderPipelines.GUI_TEXTURED, AbilityHud.LOCK_ICON, iconX, iconY, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE,
                    ICON_SIZE, 0xFFFFFFFF);
        }
        g.outline(frameX, frameY, ICON_FRAME, ICON_FRAME, unlocked ? COLOR_FRAME_BORDER : COLOR_FRAME_BORDER_LOCKED);

        // ligne 1 : nom à gauche, statut à droite
        int textX = x + TEXT_X;
        int right = x + w - RIGHT_PAD;
        Component status = Component.translatable(unlocked ? "screen.bloxpiece.haki.unlocked" : "screen.bloxpiece.haki.locked");
        int statusX = right - font.width(status);
        g.text(font, BloxPanelScreen.fit(font, Component.translatable(type.nameKey()), statusX - 8 - textX), textX, y + 5,
                unlocked ? COLOR_NAME : COLOR_NAME_LOCKED, true);
        g.text(font, status, statusX, y + 5, unlocked ? COLOR_UNLOCKED : COLOR_LOCKED, true);

        // description
        List<FormattedCharSequence> lines = descriptionLines(font, type, w);
        int lineY = y + DESCRIPTION_Y;
        for (int i = 0; i < Math.min(MAX_DESCRIPTION_LINES, lines.size()); i++) {
            g.text(font, lines.get(i), textX, lineY, unlocked ? COLOR_DESCRIPTION : COLOR_DESCRIPTION_LOCKED, false);
            lineY += font.lineHeight;
        }

        // maîtrise : niveau de base, ou maîtrisé (plus tard : par une quête spéciale)
        boolean mastered = HakiMastery.isMastered(player, type);
        int top = lineY + 3;
        Component mastery = Component.translatable("screen.bloxpiece.haki.mastery",
                Component.translatable(mastered ? "screen.bloxpiece.haki.mastered" : "screen.bloxpiece.haki.base"));
        g.text(font, mastery, textX, top, mastered ? BloxPanelScreen.COLOR_GOLD_BRIGHT : COLOR_BASE, true);

        // attaque avancée de ce Haki : nom, prérequis (la maîtrise), description
        HakiMastery.AdvancedAttack attack = HakiMastery.advancedAttack(type);
        int ax = textX;
        int ay = top + MASTERY_H + ATTACK_GAP;
        List<FormattedCharSequence> attackLines = attackLines(font, type, w);
        int shownLines = Math.min(4, attackLines.size());
        int ah = 4 + font.lineHeight + shownLines * font.lineHeight + 3;
        int aw = right - ax;
        g.fill(ax, ay, ax + aw, ay + ah, COLOR_ATTACK_BG);
        g.fill(ax, ay, ax + 1, ay + ah, mastered ? COLOR_ACCENT : COLOR_ACCENT_LOCKED);
        Component requirement = Component.translatable(mastered ? "screen.bloxpiece.haki.attack_available"
                : "screen.bloxpiece.haki.attack_requires");
        int reqX = ax + aw - ATTACK_INDENT - font.width(requirement);
        g.text(font, BloxPanelScreen.fit(font, Component.translatable(attack.nameKey()), reqX - 8 - ax - ATTACK_INDENT),
                ax + ATTACK_INDENT, ay + 4, mastered ? COLOR_ATTACK_NAME : COLOR_NAME_LOCKED, true);
        g.text(font, requirement, reqX, ay + 4, mastered ? COLOR_UNLOCKED : COLOR_LOCKED, true);
        int ly = ay + 4 + font.lineHeight;
        for (int i = 0; i < shownLines; i++) {
            g.text(font, attackLines.get(i), ax + ATTACK_INDENT, ly, mastered ? COLOR_DESCRIPTION : COLOR_DESCRIPTION_LOCKED, false);
            ly += font.lineHeight;
        }
    }
}
