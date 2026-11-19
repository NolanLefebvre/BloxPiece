package nolan.bloxpiece.client.screen;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.client.hud.AbilityHud;
import nolan.bloxpiece.job.Job;
import nolan.bloxpiece.job.Jobs;
import nolan.bloxpiece.network.ChooseJobPayload;
import nolan.bloxpiece.progression.Progression;

/**
 * Choix du job (ligne « Job » du menu du personnage) : même style que la page des attaques du fruit. Une ligne par job (icône,
 * nom, description, état) ; dès le niveau {@link Jobs#REQUIRED_LEVEL}, cliquer sur un job le sélectionne, puis le bouton
 * « Confirmer » en bas l'attribue (le serveur vérifie) et ramène au menu du personnage. Le choix est DÉFINITIF (seul un
 * administrateur peut le changer). Avant le niveau requis : cadenas et textes grisés.
 *
 * Retour au menu personnage : bouton « Retour » ou Échap. La touche du menu (G) ferme tout.
 */
public class JobScreen extends BloxPanelScreen {

    private static final int ROWS_X = PAD;
    private static final int ROWS_Y = 30;
    private static final int ROWS_W = PANEL_W - 2 * PAD;
    private static final int ROW_GAP = 3;
    private static final int ICON_FRAME = 26;
    private static final int ICON_SIZE = 16;
    private static final int TEXT_X = 38;
    private static final int RIGHT_PAD = 7;
    private static final int DESCRIPTION_Y = 16;
    private static final int MIN_ROW_H = 36;

    private static final int BACK_W = 62;
    private static final int BACK_H = 14;
    private static final int BACK_X = PAD;
    private static final int BACK_MARGIN = 6;
    private static final int BOTTOM_SPACE = 10;

    private static final int COLOR_NAME = 0xFFFFFFFF;
    private static final int COLOR_LOCKED_TEXT = 0xFF8A8A96;
    private static final int COLOR_DESCRIPTION = 0xFFB8B8C4;
    private static final int COLOR_DESCRIPTION_LOCKED = 0xFF6E6E7A;
    private static final int COLOR_CURRENT = 0xFFFFD25A;
    private static final int COLOR_CHOOSE = 0xFF6BE08A;
    private static final int COLOR_REQUIRED = 0xFFE06B6B;
    private static final int COLOR_FRAME = 0xFF07070C;

    private final CharacterScreen parent;
    /** Job cliqué, en attente de confirmation (bouton « Confirmer » en bas). */
    private Job pending;

    private static final int CONFIRM_W = 90;

    public JobScreen(CharacterScreen parent, KeyMapping closeKey) {
        super(Component.translatable("screen.bloxpiece.job.title"), closeKey, false);
        this.parent = parent;
    }

    private static Identifier icon(Job job) {
        return BloxPiece.id("textures/gui/job/" + job.id + ".png");
    }

    private List<FormattedCharSequence> lines(Font font, Job job) {
        return font.split(Component.translatable(job.descriptionKey()), ROWS_W - TEXT_X - RIGHT_PAD);
    }

    private int rowHeight(Font font, Job job) {
        return Math.max(MIN_ROW_H, DESCRIPTION_Y + Math.min(5, lines(font, job).size()) * font.lineHeight + 5);
    }

    private int contentBottom() {
        Font font = this.minecraft.font;
        int y = ROWS_Y;
        for (Job job : Job.values()) {
            y += rowHeight(font, job) + ROW_GAP;
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

    private static boolean levelReached(LocalPlayer player) {
        return Progression.get(player).level() >= Jobs.REQUIRED_LEVEL;
    }

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, Font font, LocalPlayer player,
                                int mx, int my, int mouseX, int mouseY, float dt) {
        boolean open = levelReached(player);
        Job current = Jobs.get(player);
        Component tooltip = null;
        int y = ROWS_Y;
        for (Job job : Job.values()) {
            int h = rowHeight(font, job);
            boolean hovered = inside(mx, my, ROWS_X, y, ROWS_W, h);
            boolean selected = job == current || (current == null && job == pending);
            drawRow(g, font, job, ROWS_X, y, ROWS_W, h, hovered && open && current == null, open, selected);
            if (hovered) {
                tooltip = !open ? Component.translatable("screen.bloxpiece.job.locked_tooltip", Jobs.REQUIRED_LEVEL,
                        Progression.get(player).level())
                        : job == current ? Component.translatable("screen.bloxpiece.job.current_tooltip")
                        : job == pending ? Component.translatable("screen.bloxpiece.job.pending_tooltip")
                        : current != null ? Component.translatable("screen.bloxpiece.job.final_tooltip")
                        : Component.translatable("screen.bloxpiece.job.choose_tooltip", Component.translatable(job.nameKey()));
            }
            y += h + ROW_GAP;
        }
        if (tooltip != null) {
            g.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
        drawBackButton(g, font, mx, my);
        if (current == null && pending != null) {
            drawConfirmButton(g, font, mx, my);
        }
    }

    /** Bouton « Confirmer » (à droite du bouton Retour), visible quand un job est sélectionné. */
    private void drawConfirmButton(GuiGraphicsExtractor g, Font font, int mx, int my) {
        int x = PANEL_W - PAD - CONFIRM_W;
        int y = backY();
        boolean hovered = inside(mx, my, x, y, CONFIRM_W, BACK_H);
        g.fill(x, y, x + CONFIRM_W, y + BACK_H, hovered ? 0x5A6BE08A : 0x3A6BE08A);
        g.outline(x, y, CONFIRM_W, BACK_H, hovered ? 0xFF9BFFB0 : COLOR_CHOOSE);
        g.centeredText(font, fit(font, Component.translatable("screen.bloxpiece.job.confirm"),
                CONFIRM_W - 6), x + CONFIRM_W / 2, y + (BACK_H - font.lineHeight) / 2 + 1, hovered ? 0xFFFFFFFF : COLOR_CHOOSE);
    }

    private void drawRow(GuiGraphicsExtractor g, Font font, Job job, int x, int y, int w, int h, boolean hovered, boolean open,
                         boolean selected) {
        g.fill(x, y, x + w, y + h, hovered ? CharacterInfoRow.COLOR_BACKGROUND_HOVER
                : open ? CharacterInfoRow.COLOR_BACKGROUND : 0x60000000);
        g.fill(x, y, x + (hovered || selected ? 2 : 1), y + h, open ? job.color : 0xFF55555F);
        if (selected) {
            g.outline(x, y, w, h, COLOR_CURRENT); // job actuel : encadré doré
        }
        int frameX = x + 6;
        int frameY = y + (Math.min(h, MIN_ROW_H) - ICON_FRAME) / 2;
        int iconX = frameX + (ICON_FRAME - ICON_SIZE) / 2;
        int iconY = frameY + (ICON_FRAME - ICON_SIZE) / 2;
        g.fill(frameX, frameY, frameX + ICON_FRAME, frameY + ICON_FRAME, COLOR_FRAME);
        g.blit(RenderPipelines.GUI_TEXTURED, icon(job), iconX, iconY, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE,
                ((open ? 0xFF : 0x50) << 24) | 0xFFFFFF);
        if (!open) {
            g.blit(RenderPipelines.GUI_TEXTURED, AbilityHud.LOCK_ICON, iconX, iconY, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE,
                    ICON_SIZE, 0xFFFFFFFF);
        }
        g.outline(frameX, frameY, ICON_FRAME, ICON_FRAME, selected ? COLOR_CURRENT : open ? COLOR_GOLD : COLOR_GOLD_DIM);

        int textX = x + TEXT_X;
        int right = x + w - RIGHT_PAD;
        Component state = !open ? Component.translatable("screen.bloxpiece.job.required", Jobs.REQUIRED_LEVEL)
                : selected && job == pending ? Component.translatable("screen.bloxpiece.job.selected")
                : selected ? Component.translatable("screen.bloxpiece.job.current")
                : Component.translatable("screen.bloxpiece.job.choose");
        int stateX = right - font.width(state);
        g.text(font, fit(font, Component.translatable(job.nameKey()), stateX - 8 - textX), textX, y + 5,
                open ? (selected ? COLOR_CURRENT : COLOR_NAME) : COLOR_LOCKED_TEXT, true);
        g.text(font, state, stateX, y + 5, !open ? COLOR_REQUIRED : selected ? COLOR_CURRENT : COLOR_CHOOSE, true);
        int ly = y + DESCRIPTION_Y;
        List<FormattedCharSequence> lines = lines(font, job);
        for (int i = 0; i < Math.min(5, lines.size()); i++) {
            g.text(font, lines.get(i), textX, ly, open ? COLOR_DESCRIPTION : COLOR_DESCRIPTION_LOCKED, false);
            ly += font.lineHeight;
        }
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
        LocalPlayer player = this.minecraft.player;
        if (player == null || !levelReached(player) || Jobs.get(player) != null) {
            return false; // niveau trop bas, ou job déjà choisi (définitif)
        }
        // confirmation : le choix est envoyé au serveur, puis retour au menu du personnage
        if (pending != null && inside(mx, my, PANEL_W - PAD - CONFIRM_W, backY(), CONFIRM_W, BACK_H)) {
            playClickSound();
            ClientPlayNetworking.send(new ChooseJobPayload(pending.id));
            onClose();
            return true;
        }
        Font font = this.minecraft.font;
        int y = ROWS_Y;
        for (Job job : Job.values()) {
            int h = rowHeight(font, job);
            if (inside(mx, my, ROWS_X, y, ROWS_W, h)) {
                // un clic sélectionne le job (il reste à le confirmer en bas) ; re-cliquer le désélectionne
                playClickSound();
                pending = pending == job ? null : job;
                return true;
            }
            y += h + ROW_GAP;
        }
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
