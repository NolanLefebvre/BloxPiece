package nolan.bloxpiece.client.screen;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.progression.Progression;
import nolan.bloxpiece.race.Race;
import nolan.bloxpiece.race.RaceAttack;
import nolan.bloxpiece.race.Races;

/**
 * Page « Attaques » du menu Race : une fiche par attaque de SA race (liste lue dans Races : ajouter une attaque n'oblige pas à
 * toucher à cette page). Fiche : icône (celle de l'attaque si elle existe, sinon celle de la race), nom, description, niveau
 * requis et état :
 * <ul>
 *   <li><b>À venir</b> (gris) : pas encore développée, QUEL QUE SOIT le niveau du joueur (jamais présentée comme utilisable) ;</li>
 *   <li><b>Verrouillée</b> (rouge, cadenas) : développée, niveau BloxPiece (Progression) insuffisant ;</li>
 *   <li><b>Débloquée</b> (vert) : développée et niveau atteint.</li>
 * </ul>
 */
public class RaceAttacksScreen extends BloxPanelScreen {

    private static final int TOP = 30;
    private static final int CARD_H = 30;
    private static final int CARD_GAP = 3;
    private static final int BTN_H = 14;
    private static final int COLOR_GOOD = 0xFF6FD86F;
    private static final int COLOR_BAD = 0xFFE06060;
    private static final int COLOR_SOON = 0xFF8A8A9A;
    /** Icônes d'attaque présentes dans les ressources (sinon l'icône de la race). */
    private static final Map<Identifier, Boolean> ICON_EXISTS = new HashMap<>();

    private final RaceScreen parent;

    public RaceAttacksScreen(RaceScreen parent, KeyMapping closeKey) {
        super(Component.translatable("screen.bloxpiece.race.attacks_title"), closeKey, false);
        this.parent = parent;
    }

    private int backY() {
        LocalPlayer player = Minecraft.getInstance().player;
        int n = player == null ? 0 : Races.of(player).attacks().size();
        return TOP + 14 + n * (CARD_H + CARD_GAP) + 4;
    }

    @Override
    protected int panelHeight() {
        return Math.max(PANEL_H, backY() + BTN_H + 10);
    }

    private static Identifier iconOf(RaceAttack attack, Race race) {
        boolean exists = ICON_EXISTS.computeIfAbsent(attack.icon(),
                id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent());
        return exists ? attack.icon() : race.icon();
    }

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, Font font, LocalPlayer player,
                                int mx, int my, int mouseX, int mouseY, float dt) {
        Race race = Races.of(player);
        int level = Progression.get(player).level();
        g.text(font, Component.translatable("screen.bloxpiece.race.attacks_of", Component.translatable(race.translationKey()), level),
                PAD, TOP, race.color(), true);
        int y = TOP + 14;
        int w = PANEL_W - 2 * PAD;
        for (RaceAttack attack : race.attacks()) {
            boolean reached = level >= attack.requiredLevel();
            int stateColor;
            Component state;
            if (!attack.implemented()) {
                stateColor = COLOR_SOON;
                state = Component.translatable("screen.bloxpiece.race.state.soon");
            } else if (!reached) {
                stateColor = COLOR_BAD;
                state = Component.translatable("screen.bloxpiece.race.state.locked");
            } else {
                stateColor = COLOR_GOOD;
                state = Component.translatable("screen.bloxpiece.race.state.unlocked");
            }
            boolean hovered = inside(mx, my, PAD, y, w, CARD_H);
            g.fill(PAD, y, PAD + w, y + CARD_H, hovered ? CharacterInfoRow.COLOR_BACKGROUND_HOVER : CharacterInfoRow.COLOR_BACKGROUND);
            g.fill(PAD, y, PAD + 2, y + CARD_H, stateColor);
            // icône
            int ix = PAD + 6, iy = y + (CARD_H - 16) / 2;
            boolean dim = !attack.implemented() || !reached;
            g.blit(RenderPipelines.GUI_TEXTURED, iconOf(attack, race), ix, iy, 0.0F, 0.0F, 16, 16, 16, 16, dim ? 0x80FFFFFF : 0xFFFFFFFF);
            if (attack.implemented() && !reached) {
                g.blit(RenderPipelines.GUI_TEXTURED, nolan.bloxpiece.client.hud.AbilityHud.LOCK_ICON, ix, iy, 0.0F, 0.0F, 16, 16, 16, 16,
                        0xFFFFFFFF);
            }
            // état et niveau à droite
            Component levelText = Component.translatable("screen.bloxpiece.race.level_required", attack.requiredLevel());
            int right = PAD + w - 6;
            int stateW = font.width(state) + 8;
            g.fill(right - stateW, y + 4, right, y + 15, 0x60000000);
            g.outline(right - stateW, y + 4, stateW, 11, stateColor);
            g.centeredText(font, state, right - stateW / 2, y + 6, stateColor);
            g.text(font, levelText, right - font.width(levelText), y + 18, reached ? COLOR_GOLD_BRIGHT : COLOR_LABEL, false);
            // nom et description
            int tx = ix + 22;
            int textW = right - stateW - 8 - tx;
            g.text(font, fit(font, Component.translatable(attack.nameKey()), textW), tx, y + 5, dim ? 0xFFB8B8C4 : 0xFFFFFFFF, true);
            Component desc = Component.translatable(attack.descriptionKey());
            g.text(font, fit(font, desc, right - font.width(levelText) - 8 - tx), tx, y + 18, COLOR_LABEL, false);
            if (hovered && font.width(desc) > right - font.width(levelText) - 8 - tx) {
                g.setTooltipForNextFrame(font, desc, mouseX, mouseY); // description complète au survol
            }
            y += CARD_H + CARD_GAP;
        }
        int by = backY();
        boolean hovered = inside(mx, my, PAD, by, 62, BTN_H);
        g.fill(PAD, by, PAD + 62, by + BTN_H, hovered ? CharacterInfoRow.COLOR_BACKGROUND_HOVER : CharacterInfoRow.COLOR_BACKGROUND);
        g.outline(PAD, by, 62, BTN_H, hovered ? COLOR_GOLD_BRIGHT : COLOR_GOLD_DIM);
        g.centeredText(font, Component.translatable("screen.bloxpiece.abilities.back"), PAD + 31, by + (BTN_H - font.lineHeight) / 2 + 1,
                hovered ? COLOR_GOLD_BRIGHT : COLOR_LABEL);
        g.text(font, fit(font, Component.translatable("screen.bloxpiece.race.attacks_note"), PANEL_W - 2 * PAD - 70), PAD + 70,
                by + 3, COLOR_HINT, false);
    }

    @Override
    protected boolean onPanelClick(int mx, int my) {
        if (inside(mx, my, PAD, backY(), 62, BTN_H)) {
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
