package nolan.bloxpiece.client.screen;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import nolan.bloxpiece.ability.Ability;
import nolan.bloxpiece.ability.AbilityManager;
import nolan.bloxpiece.client.hud.AbilityHud;
import nolan.bloxpiece.fruit.DevilFruit;

/**
 * Une ligne de la page des attaques : icône, nom, niveau requis et description d'une attaque.
 * Même style que CharacterInfoRow. Tout est calculé à chaque image à partir du niveau réel du joueur :
 * une attaque verrouillée (cadenas sur l'icône, textes grisés) se débloque d'elle-même quand le niveau est atteint.
 */
final class AbilityRow {

    private static final int ICON_FRAME = 26;
    private static final int ICON_SIZE = 16;
    private static final int TEXT_X = 38;        // début des textes, à droite de l'icône
    private static final int MAX_DESCRIPTION_LINES = 8; // la description s'affiche en entier (la ligne s'agrandit)
    private static final int MIN_HEIGHT = 36;     // hauteur d'une ligne dont la description tient sur 1 ou 2 lignes
    private static final int DESCRIPTION_Y = 16;  // début de la description sous le nom
    private static final int BOTTOM_PAD = 4;
    private static final int RIGHT_PAD = 7;

    private static final int COLOR_ACCENT = 0xFFFF8A3D;
    private static final int COLOR_ACCENT_LOCKED = 0xFF55555F;
    private static final int COLOR_BACKGROUND_LOCKED = 0x60000000;
    private static final int COLOR_FRAME = 0xFF07070C;
    private static final int COLOR_FRAME_BORDER = 0xFFC8A24A;
    private static final int COLOR_FRAME_BORDER_LOCKED = 0xFF6B5A2E;

    private static final int COLOR_NAME = 0xFFFFFFFF;
    private static final int COLOR_NAME_LOCKED = 0xFF8A8A96;
    private static final int COLOR_DESCRIPTION = 0xFFB8B8C4;
    private static final int COLOR_DESCRIPTION_LOCKED = 0xFF6E6E7A;
    private static final int COLOR_LEVEL_OK = 0xFF6BE08A;
    private static final int COLOR_LEVEL_LOCKED = 0xFFE06B6B;

    private static final int ICON_ALPHA_LOCKED = 0x50;

    private AbilityRow() {
    }

    /** Description de l'attaque, découpée à la largeur disponible dans une ligne de largeur {@code w}. */
    private static List<FormattedCharSequence> descriptionLines(Font font, Ability ability, int w) {
        return font.split(Component.translatable(ability.descriptionKey()), w - RIGHT_PAD - TEXT_X);
    }

    /**
     * Hauteur de la ligne de cette attaque : 36 px, plus si la description a besoin de plus de 2 lignes
     * (ex : Dai Enkai: Entei), pour qu'elle s'affiche en entier.
     */
    static int heightFor(Font font, Ability ability, int w) {
        int lines = Math.min(MAX_DESCRIPTION_LINES, descriptionLines(font, ability, w).size());
        return Math.max(MIN_HEIGHT, DESCRIPTION_Y + lines * font.lineHeight + BOTTOM_PAD);
    }

    static void extract(GuiGraphicsExtractor g, Font font, LocalPlayer player, DevilFruit fruit, Ability ability,
                        int x, int y, int w, int h, boolean hovered) {
        boolean unlocked = ability.isUnlockedFor(player);

        // Fond + barre d'accent à gauche (même principe que CharacterInfoRow)
        int background = hovered ? CharacterInfoRow.COLOR_BACKGROUND_HOVER
                : unlocked ? CharacterInfoRow.COLOR_BACKGROUND : COLOR_BACKGROUND_LOCKED;
        g.fill(x, y, x + w, y + h, background);
        g.fill(x, y, x + (hovered ? 2 : 1), y + h, unlocked ? COLOR_ACCENT : COLOR_ACCENT_LOCKED);

        // Icône dans son cadre. Verrouillée : icône très atténuée, avec le cadenas par-dessus
        int frameX = x + 6;
        int frameY = y + (h - ICON_FRAME) / 2;
        int iconX = frameX + (ICON_FRAME - ICON_SIZE) / 2;
        int iconY = frameY + (ICON_FRAME - ICON_SIZE) / 2;
        g.fill(frameX, frameY, frameX + ICON_FRAME, frameY + ICON_FRAME, COLOR_FRAME);

        int alpha = unlocked ? 0xFF : ICON_ALPHA_LOCKED;
        g.blit(RenderPipelines.GUI_TEXTURED,
                AbilityHud.iconTexture(AbilityManager.cooldownKey(fruit, ability), fruit, ability),
                iconX, iconY, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, (alpha << 24) | 0xFFFFFF);
        if (!unlocked) {
            g.blit(RenderPipelines.GUI_TEXTURED, AbilityHud.LOCK_ICON, iconX, iconY,
                    0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, 0xFFFFFFFF);
        }
        g.outline(frameX, frameY, ICON_FRAME, ICON_FRAME, unlocked ? COLOR_FRAME_BORDER : COLOR_FRAME_BORDER_LOCKED);

        // Ligne 1 : nom à gauche, niveau requis à droite (vert = atteint, rouge = pas encore)
        int textX = x + TEXT_X;
        int right = x + w - RIGHT_PAD;
        g.text(font, Component.translatable(ability.nameKey()), textX, y + 5,
                unlocked ? COLOR_NAME : COLOR_NAME_LOCKED, true);
        Component level = Component.translatable("screen.bloxpiece.abilities.required_level", ability.requiredLevel());
        g.text(font, level, right - font.width(level), y + 5, unlocked ? COLOR_LEVEL_OK : COLOR_LEVEL_LOCKED, true);

        // Lignes suivantes : description, à la ligne automatiquement (la hauteur de la ligne s'adapte : voir heightFor)
        List<FormattedCharSequence> lines = descriptionLines(font, ability, w);
        int lineY = y + DESCRIPTION_Y;
        for (int i = 0; i < Math.min(MAX_DESCRIPTION_LINES, lines.size()); i++) {
            g.text(font, lines.get(i), textX, lineY, unlocked ? COLOR_DESCRIPTION : COLOR_DESCRIPTION_LOCKED, false);
            lineY += font.lineHeight;
        }
    }
}
