package nolan.bloxpiece.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Fenêtre de dialogue d'un PNJ (pour l'instant : le Tavernier), en bas de l'écran : nom du PNJ, sa réplique (qui s'écrit
 * lettre par lettre ; un clic l'affiche en entier), puis les questions du joueur en boutons sur deux colonnes. Le dernier
 * bouton, « Au revoir », ferme la fenêtre (Échap aussi). Le jeu n'est pas mis en pause.
 * <p>
 * Toute la mise en page est calculée à chaque image à partir de la taille des textes : rien ne se chevauche, quelle que soit
 * la langue ou la taille de l'écran. Textes : clés « npc.bloxpiece.&lt;pnj&gt;.* » (fr_fr.json / en_us.json).
 */
public class DialogueScreen extends Screen {

    /** Un bouton du joueur : son texte et ce qu'il fait (réponse du PNJ, quête...). Le bouton « Au revoir » est ajouté à part. */
    public record Option(Component label, Runnable action) {
    }

    private static final int MAX_W = 380;
    private static final int PAD = 10;
    private static final int BUTTON_H = 18;
    private static final int GAP = 5;
    private static final int LEFT_MOUSE_BUTTON = 1; // SDL : clic gauche = 1 (comme BloxPanelScreen)
    private static final float CHARS_PER_SECOND = 70.0F;

    private static final int COLOR_PANEL = 0xF2101018;
    private static final int COLOR_GOLD = 0xFFC8A24A;
    private static final int COLOR_GOLD_DIM = 0xFF6B5A2E;
    private static final int COLOR_GOLD_BRIGHT = 0xFFFFD25A;
    private static final int COLOR_TEXT = 0xFFE8E4D8;
    private static final int COLOR_BUTTON = 0x40000000;
    private static final int COLOR_BUTTON_HOVER = 0x40FFD25A;

    private final String npc;
    private final int entityId;
    private List<Option> options;
    private Component speech;
    private long speechStartMs = System.currentTimeMillis();
    private boolean speechComplete;

    // Mise en page de la dernière image (pour les clics)
    private int panelX;
    private int panelY;
    private int panelW;
    private int buttonsY;
    private int buttonW;

    public DialogueScreen(String npc) {
        this(npc, "", -1);
    }

    public DialogueScreen(String npc, String speech) {
        this(npc, speech, -1);
    }

    /**
     * {@code speech} : première réplique (suffixe de clé), vide pour l'accueil habituel ; {@code entityId} : le PNJ (Silvers
     * Rayleigh s'en sert pour les quêtes de Haki : le serveur vérifie que le joueur lui parle bien).
     */
    public DialogueScreen(String npc, String speech, int entityId) {
        super(Component.translatable("npc.bloxpiece." + npc + ".name"));
        this.npc = npc;
        this.entityId = entityId;
        this.options = mainOptions();
        this.speech = Component.translatable(key(speech == null || speech.isEmpty() ? "greeting" : speech));
    }

    private String key(String suffix) {
        return "npc.bloxpiece." + this.npc + "." + suffix;
    }

    /** Sujet de conversation : la question du joueur, et la réponse du PNJ quand il clique. */
    private Option topic(String id) {
        return new Option(Component.translatable(key(id + ".question")), () -> say(Component.translatable(key(id + ".answer"))));
    }

    /**
     * Les boutons de départ, propres à chaque PNJ. Silvers Rayleigh : « quests » ouvre les quêtes de Haki
     * (RayleighQuestDialogue) ; les autres sujets sont de simples réponses.
     */
    public List<Option> mainOptions() {
        if (nolan.bloxpiece.mob.Rayleigh.NPC_ID.equals(this.npc)) {
            return List.of(new Option(Component.translatable(key("quests.question")), () -> RayleighQuestDialogue.open(this)),
                    topic("haki"), topic("roger"), topic("who"));
        }
        if (nolan.bloxpiece.mob.TavernKeeper.NPC_ID.equals(this.npc)) {
            // Tavernier : « missions » ouvre les quêtes répétables (TavernQuestDialogue) ; les autres sujets sont des réponses
            return List.of(new Option(Component.translatable(key("missions.question")), () -> TavernQuestDialogue.open(this)),
                    topic("fruit"), topic("mastery"), topic("village"), topic("start"));
        }
        return List.of(topic("fruit"), topic("mastery"), topic("village"), topic("start"));
    }

    /** Remplace les boutons (le bouton « Au revoir » reste toujours en dernier). */
    public void setOptions(List<Option> options) {
        this.options = new ArrayList<>(options);
    }

    /** Retour aux sujets de départ, avec une nouvelle réplique du PNJ. */
    public void showMain(Component speech) {
        this.setOptions(mainOptions());
        this.say(speech);
    }

    public int entityId() {
        return this.entityId;
    }

    public String npcKey(String suffix) {
        return key(suffix);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ dessin

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        this.panelW = Math.min(MAX_W, this.width - 16);
        int textW = this.panelW - 2 * PAD;

        // réplique du PNJ, découpée à la largeur, révélée lettre par lettre
        String full = this.speech.getString();
        int shown = this.speechComplete ? full.length()
                : Mth.clamp((int) ((System.currentTimeMillis() - this.speechStartMs) / 1000.0F * CHARS_PER_SECOND), 0, full.length());
        if (shown >= full.length()) {
            this.speechComplete = true;
        }
        List<FormattedCharSequence> lines = font.split(Component.literal(full.substring(0, shown)), textW);
        int fullLines = Math.max(1, font.split(this.speech, textW).size()); // hauteur fixe : la fenêtre ne saute pas

        int buttons = this.options.size() + 1; // + Au revoir
        int rows = (buttons + 1) / 2;
        int nameH = font.lineHeight + 6;
        int textH = fullLines * (font.lineHeight + 1);
        int panelH = PAD + nameH + textH + 8 + rows * (BUTTON_H + GAP) - GAP + PAD;
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = Math.max(4, this.height - panelH - 12);
        int x = this.panelX, y = this.panelY;

        // cadre
        g.fill(x - 1, y - 1, x + this.panelW + 1, y + panelH + 1, 0x88000000);
        g.fill(x, y, x + this.panelW, y + panelH, COLOR_PANEL);
        g.outline(x, y, this.panelW, panelH, COLOR_GOLD_DIM);
        int l = 8;
        for (int[] c : new int[][] {{x - 1, y - 1, 1, 1}, {x + this.panelW + 1, y - 1, -1, 1},
                {x - 1, y + panelH + 1, 1, -1}, {x + this.panelW + 1, y + panelH + 1, -1, -1}}) {
            g.fill(Math.min(c[0], c[0] + c[2] * l), Math.min(c[1], c[1] + c[3] * 2), Math.max(c[0], c[0] + c[2] * l),
                    Math.max(c[1], c[1] + c[3] * 2), COLOR_GOLD);
            g.fill(Math.min(c[0], c[0] + c[2] * 2), Math.min(c[1], c[1] + c[3] * l), Math.max(c[0], c[0] + c[2] * 2),
                    Math.max(c[1], c[1] + c[3] * l), COLOR_GOLD);
        }

        // nom du PNJ (onglet doré au-dessus du texte)
        Component name = BloxPanelScreen.fit(font, this.getTitle().copy().withStyle(ChatFormatting.BOLD), textW);
        g.text(font, name, x + PAD, y + PAD, COLOR_GOLD_BRIGHT, true);
        g.fill(x + PAD, y + PAD + font.lineHeight + 2, x + PAD + font.width(name), y + PAD + font.lineHeight + 3, COLOR_GOLD_DIM);

        // réplique
        int ty = y + PAD + nameH;
        for (FormattedCharSequence line : lines) {
            g.text(font, line, x + PAD, ty, COLOR_TEXT, false);
            ty += font.lineHeight + 1;
        }

        // boutons de réponse, sur deux colonnes
        this.buttonsY = y + PAD + nameH + textH + 8;
        this.buttonW = (textW - GAP) / 2;
        for (int i = 0; i < buttons; i++) {
            int bx = x + PAD + (i % 2) * (this.buttonW + GAP);
            int by = this.buttonsY + (i / 2) * (BUTTON_H + GAP);
            int bw = this.buttonW;
            if (i == buttons - 1 && buttons % 2 == 1) { // dernier bouton seul sur sa ligne : centré
                bx = x + PAD + (textW - bw) / 2;
            }
            boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + BUTTON_H;
            boolean bye = i == buttons - 1;
            g.fill(bx, by, bx + bw, by + BUTTON_H, hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
            g.outline(bx, by, bw, BUTTON_H, hovered ? COLOR_GOLD_BRIGHT : COLOR_GOLD_DIM);
            Component label = bye ? Component.translatable(key("bye")) : this.options.get(i).label();
            label = BloxPanelScreen.fit(font, label, bw - 10);
            g.centeredText(font, label, bx + bw / 2, by + (BUTTON_H - font.lineHeight) / 2 + 1,
                    hovered ? COLOR_GOLD_BRIGHT : (bye ? 0xFFB8B8C4 : COLOR_TEXT));
        }
    }

    // Pas de flou ni d'assombrissement : le PNJ et la taverne restent bien visibles derrière la fenêtre
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    // ------------------------------------------------------------------ clics

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == LEFT_MOUSE_BUTTON) {
            double mx = event.x(), my = event.y();
            int buttons = this.options.size() + 1;
            int textW = this.panelW - 2 * PAD;
            for (int i = 0; i < buttons; i++) {
                int bx = this.panelX + PAD + (i % 2) * (this.buttonW + GAP);
                int by = this.buttonsY + (i / 2) * (BUTTON_H + GAP);
                if (i == buttons - 1 && buttons % 2 == 1) {
                    bx = this.panelX + PAD + (textW - this.buttonW) / 2;
                }
                if (mx >= bx && mx < bx + this.buttonW && my >= by && my < by + BUTTON_H) {
                    click();
                    if (i == buttons - 1) {
                        this.onClose();
                    } else {
                        this.options.get(i).action().run();
                    }
                    return true;
                }
            }
            // clic ailleurs sur la fenêtre : la réplique s'affiche en entier tout de suite
            if (!this.speechComplete && mx >= this.panelX && mx < this.panelX + this.panelW && my >= this.panelY && my < this.buttonsY) {
                this.speechComplete = true;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    public void say(Component text) {
        this.speech = text;
        this.speechStartMs = System.currentTimeMillis();
        this.speechComplete = false;
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
