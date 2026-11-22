package nolan.bloxpiece.client.screen;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import nolan.bloxpiece.network.RaceRerollPayload;
import nolan.bloxpiece.race.Race;
import nolan.bloxpiece.race.RaceBonus;
import nolan.bloxpiece.race.RaceManager;
import nolan.bloxpiece.race.Races;
import nolan.bloxpiece.registry.ModItems;

/**
 * Menu Race (ligne « Race » du menu personnage, ou onglet de la race dans l'inventaire) : même style que les autres pages du
 * menu (BloxPanelScreen). Quatre boutons en haut :
 * <ul>
 *   <li><b>Informations</b> : icône, nom, description et chance d'obtention de sa race ;</li>
 *   <li><b>Bonus</b> : ses bonus passifs, écrits à partir des valeurs réellement appliquées (Races / RaceBonus) ;</li>
 *   <li><b>Attaques</b> : ouvre la page des attaques raciales (RaceAttacksScreen) ;</li>
 *   <li><b>Reroll</b> : le Race Reroll Token et le nombre possédé, l'index des 5 races (icône, nom, chance), la roulette et le
 *       bouton Reroll : le serveur tire la race et consomme le token (RaceManager.reroll), puis la roulette défile, ralentit et
 *       s'arrête sur la race qu'il a envoyée (startRoll) ; elle ne décide jamais rien.</li>
 * </ul>
 * Tout est relu à chaque image depuis la race synchronisée par le serveur : la page change toute seule après un reroll.
 */
public class RaceScreen extends BloxPanelScreen {

    private static final int TAB_Y = 30;
    private static final int TAB_H = 14;
    private static final int TAB_GAP = 4;
    private static final int TABS = 4;
    private static final int TAB_W = (PANEL_W - 2 * PAD - (TABS - 1) * TAB_GAP) / TABS;
    private static final int CONTENT_Y = TAB_Y + TAB_H + 8;
    private static final int BTN_H = 14;
    private static final int COLOR_GOOD = 0xFF6FD86F;
    private static final int COLOR_BAD = 0xFFE06060;
    /** Après un clic sur Reroll : bouton bloqué en attendant la réponse du serveur (pas de double envoi). */
    private static final long REROLL_LOCK_MS = 2500L;

    // ---- Roulette du reroll (pur affichage : la race est tirée par le serveur, RaceRollResultPayload)
    /** Durée du défilement (ms), puis de la révélation du résultat (ms). */
    private static final float SPIN_MS = 3400.0F;
    private static final float REVEAL_MS = 1600.0F;
    /** Tours complets de la roue avant d'arriver sur la race tirée. */
    private static final int SPIN_LAPS = 7;
    private static final int CELL_W = 52;
    /** Ordonnées de l'onglet Reroll. */
    private static final int INDEX_Y = CONTENT_Y + 30;
    private static final int CARD_H = 42;
    private static final int REEL_Y = INDEX_Y + 12 + CARD_H + 8;
    private static final int REEL_H = 46;
    private static final int ACTION_Y = REEL_Y + REEL_H + 8;

    private static final int INFO = 0, BONUS = 1, REROLL = 3;

    private final Screen parent;
    private int tab = INFO;
    private long rerollSentAt;
    private ItemStack tokenStack;
    /** Roulette en cours : race tirée par le serveur (null : aucune), début (ms), race d'avant (affichée jusqu'à la fin). */
    private Race rollTarget;
    private long rollStart;
    private Race rollPrevious;
    private int lastTickCell = -1;

    public RaceScreen(Screen parent, KeyMapping closeKey) {
        super(Component.translatable("screen.bloxpiece.race.title"), closeKey, parent == null);
        this.parent = parent;
    }

    private ItemStack token() {
        if (tokenStack == null) {
            tokenStack = new ItemStack(ModItems.RACE_REROLL_TOKEN);
        }
        return tokenStack;
    }

    private int backY() {
        return panelHeight() - BTN_H - 10;
    }

    @Override
    protected int panelHeight() {
        return ACTION_Y + BTN_H + 14 + BTN_H + 10;
    }

    /** Le serveur a tiré, payé et appliqué la race : la roulette démarre et s'arrêtera dessus (RaceRollResultPayload). */
    public void startRoll(Race race) {
        if (this.minecraft == null || this.minecraft.player == null || race == null) {
            return;
        }
        this.tab = REROLL;
        if (this.rollPrevious == null) {
            this.rollPrevious = Races.of(this.minecraft.player);
        }
        this.rollTarget = race;
        this.rollStart = System.currentTimeMillis();
        this.lastTickCell = -1;
    }

    /** Roulette en cours (défilement ou révélation) : le bouton Reroll est bloqué. */
    private boolean rolling() {
        return rollTarget != null && System.currentTimeMillis() - rollStart < SPIN_MS + REVEAL_MS;
    }

    /** Race à montrer dans les onglets : l'ancienne tant que la roulette n'a pas révélé la nouvelle (pas de spoiler). */
    private Race shownRace(LocalPlayer player) {
        if (rollPrevious != null && (rollTarget == null ? System.currentTimeMillis() - rerollSentAt < REROLL_LOCK_MS
                : System.currentTimeMillis() - rollStart < SPIN_MS)) {
            return rollPrevious;
        }
        return Races.of(player);
    }

    // ------------------------------------------------------------------ dessin

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, Font font, LocalPlayer player,
                                int mx, int my, int mouseX, int mouseY, float dt) {
        String[] tabs = {"info", "bonus", "attacks", "reroll"};
        for (int i = 0; i < TABS; i++) {
            int x = PAD + i * (TAB_W + TAB_GAP);
            boolean selected = i == tab;
            boolean hovered = inside(mx, my, x, TAB_Y, TAB_W, TAB_H);
            g.fill(x, TAB_Y, x + TAB_W, TAB_Y + TAB_H, selected || hovered ? CharacterInfoRow.COLOR_BACKGROUND_HOVER
                    : CharacterInfoRow.COLOR_BACKGROUND);
            g.outline(x, TAB_Y, TAB_W, TAB_H, selected || hovered ? COLOR_GOLD_BRIGHT : COLOR_GOLD);
            g.centeredText(font, Component.translatable("screen.bloxpiece.race.tab." + tabs[i]), x + TAB_W / 2,
                    TAB_Y + (TAB_H - font.lineHeight) / 2 + 1, selected || hovered ? COLOR_GOLD_BRIGHT : COLOR_LABEL);
        }
        Race race = shownRace(player);
        switch (tab) {
            case BONUS -> drawBonus(g, font, race);
            case REROLL -> drawReroll(g, font, player, race, mx, my, mouseX, mouseY);
            default -> drawInfo(g, font, race);
        }
        button(g, font, mx, my, PAD, backY(), 62, Component.translatable("screen.bloxpiece.abilities.back"), true);
    }

    private void drawHeader(GuiGraphicsExtractor g, Font font, Race race, int y) {
        // icône (16 x 16 agrandie x2) dans un cadre doré, nom de la race en couleur
        int s = 36;
        g.fill(PAD, y, PAD + s, y + s, 0xFF07070C);
        g.fillGradient(PAD + 1, y + 1, PAD + s - 1, y + s - 1, 0xFF272C3D, 0xFF0D0E15);
        g.pose().pushMatrix();
        g.pose().translate(PAD + 2, y + 2);
        g.pose().scale(2.0F);
        g.blit(RenderPipelines.GUI_TEXTURED, race.icon(), 0, 0, 0.0F, 0.0F, 16, 16, 16, 16, 0xFFFFFFFF);
        g.pose().popMatrix();
        g.outline(PAD, y, s, s, COLOR_GOLD);
        g.text(font, Component.translatable(race.translationKey()), PAD + s + 8, y + 4, race.color(), true);
    }

    private void drawInfo(GuiGraphicsExtractor g, Font font, Race race) {
        int y = CONTENT_Y;
        drawHeader(g, font, race, y);
        g.text(font, Component.translatable("screen.bloxpiece.race.chance", race.weight()), PAD + 44, y + 18, COLOR_LABEL, false);
        int ty = y + 44;
        for (FormattedCharSequence line : font.split(Component.translatable(race.descriptionKey()), PANEL_W - 2 * PAD)) {
            g.text(font, line, PAD, ty, 0xFFE8E8F0, false);
            ty += font.lineHeight + 2;
        }
        ty += 4;
        int first = race.attacks().isEmpty() ? 0 : race.attacks().get(0).requiredLevel();
        int last = race.attacks().isEmpty() ? 0 : race.attacks().get(race.attacks().size() - 1).requiredLevel();
        g.text(font, Component.translatable("screen.bloxpiece.race.attacks_summary", race.attacks().size(), first, last), PAD, ty,
                COLOR_HINT, false);
    }

    private void drawBonus(GuiGraphicsExtractor g, Font font, Race race) {
        int y = CONTENT_Y;
        g.text(font, Component.translatable("screen.bloxpiece.race.bonus_title", Component.translatable(race.translationKey())),
                PAD, y, COLOR_GOLD, true);
        y += 14;
        List<RaceBonus> bonuses = race.bonuses();
        for (RaceBonus bonus : bonuses) {
            g.fill(PAD, y, PANEL_W - PAD, y + 18, CharacterInfoRow.COLOR_BACKGROUND);
            g.fill(PAD, y, PAD + 2, y + 18, race.color());
            g.text(font, fit(font, bonus.describe(), PANEL_W - 2 * PAD - 12), PAD + 8, y + 5, COLOR_GOOD, false);
            y += 20;
        }
        g.text(font, fit(font, Component.translatable("screen.bloxpiece.race.bonus_note"), PANEL_W - 2 * PAD), PAD, y + 4,
                COLOR_HINT, false);
    }

    private void drawReroll(GuiGraphicsExtractor g, Font font, LocalPlayer player, Race race, int mx, int my, int mouseX, int mouseY) {
        int y = CONTENT_Y;
        int w = PANEL_W - 2 * PAD;
        // le jeton et le nombre possédé
        g.fill(PAD, y, PAD + 24, y + 24, 0xFF07070C);
        g.outline(PAD, y, 24, 24, COLOR_GOLD);
        g.item(token(), PAD + 4, y + 4);
        g.text(font, token().getHoverName(), PAD + 32, y + 2, COLOR_GOLD_BRIGHT, true);
        int count = RaceManager.tokens(player);
        g.text(font, Component.translatable("screen.bloxpiece.race.tokens", count), PAD + 32, y + 14,
                count > 0 ? COLOR_GOOD : COLOR_BAD, false);
        Component hint = Component.translatable("screen.bloxpiece.race.reroll_hint");
        g.text(font, fit(font, hint, w - 150), PAD + w - Math.min(font.width(hint), w - 150), y + 8, COLOR_HINT, false);

        // INDEX des races, façon sommaire (et non pas en cases comme la roulette) : deux colonnes de lignes fines, petite icône,
        // nom de la race, pointillés, chance à droite (lue dans Races : toujours la vraie valeur) ; ta race marquée d'un trait d'or
        List<Race> races = Races.all();
        g.text(font, Component.translatable("screen.bloxpiece.race.index"), PAD, INDEX_Y, COLOR_GOLD, true);
        Component chanceLabel = Component.translatable("screen.bloxpiece.race.index_chance");
        g.text(font, chanceLabel, PAD + w - font.width(chanceLabel), INDEX_Y, COLOR_HINT, false);
        g.fill(PAD, INDEX_Y + 10, PAD + w, INDEX_Y + 11, COLOR_GOLD_DIM);
        int rows = (races.size() + 1) / 2;
        int colW = (w - 12) / 2;
        int rowH = (CARD_H - 2) / Math.max(1, rows);
        g.fill(PAD + colW + 6, INDEX_Y + 14, PAD + colW + 7, INDEX_Y + 12 + rows * rowH, COLOR_INNER_LINE); // filet entre colonnes
        for (int i = 0; i < races.size(); i++) {
            Race r = races.get(i);
            int x = PAD + (i / rows) * (colW + 12) + 4, ry = INDEX_Y + 13 + (i % rows) * rowH;
            if (r == race) {
                g.fill(x - 4, ry + 1, x - 2, ry + rowH - 1, COLOR_GOLD_BRIGHT); // ta race actuelle
            }
            g.pose().pushMatrix();
            g.pose().translate(x, ry + 1);
            g.pose().scale(0.75F);
            g.blit(RenderPipelines.GUI_TEXTURED, r.icon(), 0, 0, 0.0F, 0.0F, 16, 16, 16, 16, 0xFFFFFFFF);
            g.pose().popMatrix();
            Component name = Component.translatable(r.translationKey());
            Component pct = Component.translatable("screen.bloxpiece.race.percent", r.weight());
            int tx = x + 16, ty = ry + (rowH - font.lineHeight) / 2 + 1;
            int end = x + colW - 6;
            g.text(font, name, tx, ty, r == race ? COLOR_GOLD_BRIGHT : 0xFFE8E8F0, false);
            g.text(font, pct, end - font.width(pct), ty, r.color(), false);
            for (int dx = tx + font.width(name) + 4; dx < end - font.width(pct) - 4; dx += 3) {
                g.fill(dx, ty + 6, dx + 1, ty + 7, 0x66FFFFFF); // pointillés de sommaire
            }
        }

        drawReel(g, font, races, w);

        // bouton Reroll (bloqué sans token, en attente du serveur ou pendant la roulette)
        boolean waiting = System.currentTimeMillis() - rerollSentAt < REROLL_LOCK_MS && rollTarget == null;
        boolean enabled = count > 0 && !waiting && !rolling();
        int bw = 110;
        button(g, font, mx, my, (PANEL_W - bw) / 2, ACTION_Y, bw, Component.translatable(waiting || rolling()
                ? "screen.bloxpiece.race.reroll_wait" : "screen.bloxpiece.race.reroll"), enabled);
        if (count <= 0 && !rolling()) {
            g.centeredText(font, Component.translatable("screen.bloxpiece.race.no_token"), PANEL_W / 2, ACTION_Y + BTN_H + 4, COLOR_BAD);
        }
    }

    /** Position de la roue (0..1) à l'instant u (0..1) : départ en douceur, vitesse maximale vers 15 %, long ralentissement. */
    static float wheel(float u) {
        u = Math.max(0.0F, Math.min(1.0F, u));
        float a = 0.15F;          // fin de l'accélération
        float pa = 3.0F * a / (2.0F + a); // part du trajet pendant l'accélération : vitesse continue au passage (pas d'à-coup)
        if (u < a) {
            float k = u / a;
            return pa * k * k;    // accélération (vitesse qui monte linéairement)
        }
        float k = (u - a) / (1.0F - a);
        // ralentissement : vitesse de départ égale à celle de la fin de l'accélération (2 pa / a), arrêt net à 1
        return pa + (1.0F - pa) * (1.0F - (float) Math.pow(1.0F - k, 3.0F));
    }

    /**
     * La roulette : une bande d'icônes de races qui défile sous un repère doré, accélère, ralentit et s'arrête EXACTEMENT sur la
     * race envoyée par le serveur ; puis révélation (cadre qui pulse, rayons, nom de la race). Au repos : la race actuelle.
     */
    private void drawReel(GuiGraphicsExtractor g, Font font, List<Race> races, int w) {
        int x0 = PAD, y0 = REEL_Y;
        int n = races.size();
        g.fill(x0, y0, x0 + w, y0 + REEL_H, 0xFF07070C);
        g.fillGradient(x0 + 1, y0 + 1, x0 + w - 1, y0 + REEL_H - 1, 0xFF1A1D2A, 0xFF0B0C12);
        long now = System.currentTimeMillis();
        float elapsed = rollTarget == null ? -1.0F : now - rollStart;
        float center; // position (en cases) sous le repère
        if (rollTarget == null) {
            center = Math.max(0, races.indexOf(Races.of(this.minecraft.player)));
        } else {
            float u = Math.min(1.0F, elapsed / SPIN_MS);
            float start = Math.max(0, races.indexOf(rollPrevious));
            float end = SPIN_LAPS * n + Math.max(0, races.indexOf(rollTarget));
            center = start + (end - start) * wheel(u);
            int cell = (int) Math.floor(center + 0.5F);
            if (cell != lastTickCell && u < 1.0F) {
                lastTickCell = cell; // petit clic à chaque race qui passe sous le repère
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.4F + 0.6F * (1.0F - u)));
            }
        }
        int mid = x0 + w / 2;
        int first = (int) Math.floor(center) - w / CELL_W / 2 - 1;
        for (int i = first; i <= first + w / CELL_W + 3; i++) {
            float cx = mid + (i - center) * CELL_W;
            int left = Math.round(cx - CELL_W / 2.0F) + 2, right = left + CELL_W - 4;
            if (left < x0 + 2 || right > x0 + w - 2) {
                continue; // seulement les cases entières dans la fenêtre
            }
            Race r = races.get(Math.floorMod(i, n));
            float dist = Math.abs(cx - mid) / (w / 2.0F);
            int alpha = (int) (255 * Math.max(0.25F, 1.0F - dist * 0.9F));
            g.fill(left, y0 + 4, right, y0 + REEL_H - 4, (Math.min(alpha, 160) << 24) | 0x20222E);
            g.fill(left, y0 + REEL_H - 6, right, y0 + REEL_H - 4, (alpha << 24) | (r.color() & 0xFFFFFF));
            g.blit(RenderPipelines.GUI_TEXTURED, r.icon(), (left + right) / 2 - 8, y0 + 8, 0.0F, 0.0F, 16, 16, 16, 16,
                    (alpha << 24) | 0xFFFFFF);
            g.centeredText(font, fit(font, Component.translatable(r.translationKey()), CELL_W - 6), (left + right) / 2, y0 + 28,
                    (alpha << 24) | (r.color() & 0xFFFFFF));
        }
        // bords assombris et repère doré au centre
        g.fillGradient(x0 + 1, y0 + 1, x0 + 30, y0 + REEL_H - 1, 0xE0000000, 0x00000000);
        g.fillGradient(x0 + w - 30, y0 + 1, x0 + w - 1, y0 + REEL_H - 1, 0x00000000, 0xE0000000);
        g.outline(x0, y0, w, REEL_H, COLOR_GOLD_DIM);
        g.outline(mid - CELL_W / 2, y0 + 2, CELL_W, REEL_H - 4, COLOR_GOLD);
        g.fill(mid - 3, y0 - 3, mid + 4, y0, COLOR_GOLD_BRIGHT);
        g.fill(mid - 1, y0, mid + 2, y0 + 2, COLOR_GOLD_BRIGHT);
        g.fill(mid - 3, y0 + REEL_H, mid + 4, y0 + REEL_H + 3, COLOR_GOLD_BRIGHT);

        // révélation : la race tirée pulse sous le repère, rayons dorés, puis son nom apparaît
        if (rollTarget != null && elapsed >= SPIN_MS) {
            float r = Math.min(1.0F, (elapsed - SPIN_MS) / REVEAL_MS);
            if (lastTickCell != -2) {
                lastTickCell = -2;
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.2F));
            }
            float pulse = 0.5F + 0.5F * (float) Math.sin(elapsed / 90.0);
            int glow = ((int) (120 + 135 * pulse) << 24) | (rollTarget.color() & 0xFFFFFF);
            int half = CELL_W / 2 + (int) (3 * pulse);
            g.outline(mid - half, y0 + 1 - (int) (2 * pulse), half * 2, REEL_H - 2 + (int) (4 * pulse), glow);
            g.outline(mid - half - 2, y0 - 1 - (int) (2 * pulse), half * 2 + 4, REEL_H + 2 + (int) (4 * pulse),
                    ((int) (60 + 80 * pulse) << 24) | 0xFFD25A);
            for (int k = 0; k < 8; k++) { // rayons dorés qui s'étirent autour de la case gagnante
                double a = k * Math.PI / 4 + elapsed / 900.0;
                int len = (int) (6 + 10 * r);
                int rx = (int) (mid + Math.cos(a) * (CELL_W / 2.0 + 4));
                int ry = (int) (y0 + REEL_H / 2 + Math.sin(a) * (REEL_H / 2.0 + 2));
                int ex = (int) (rx + Math.cos(a) * len), ey = (int) (ry + Math.sin(a) * len);
                for (int s = 0; s <= len; s += 2) {
                    int px = rx + (ex - rx) * s / Math.max(1, len), py = ry + (ey - ry) * s / Math.max(1, len);
                    g.fill(px, py, px + 2, py + 2, ((int) (200 * (1.0F - s / (float) Math.max(1, len))) << 24) | 0xFFE27A);
                }
            }
            int textAlpha = (int) (255 * Math.min(1.0F, r * 2.5F));
            if (textAlpha > 8) {
                Component result = Component.translatable("screen.bloxpiece.race.result",
                        Component.translatable(rollTarget.translationKey()));
                g.centeredText(font, result, PANEL_W / 2, ACTION_Y + BTN_H + 4, (textAlpha << 24) | (rollTarget.color() & 0xFFFFFF));
            }
        }
    }

    private static void button(GuiGraphicsExtractor g, Font font, int mx, int my, int x, int y, int w, Component label, boolean enabled) {
        boolean hovered = enabled && inside(mx, my, x, y, w, BTN_H);
        g.fill(x, y, x + w, y + BTN_H, hovered ? CharacterInfoRow.COLOR_BACKGROUND_HOVER : CharacterInfoRow.COLOR_BACKGROUND);
        g.outline(x, y, w, BTN_H, !enabled ? COLOR_GOLD_DIM : hovered ? COLOR_GOLD_BRIGHT : COLOR_GOLD);
        g.centeredText(font, label, x + w / 2, y + (BTN_H - font.lineHeight) / 2 + 1,
                !enabled ? COLOR_HINT : hovered ? COLOR_GOLD_BRIGHT : COLOR_LABEL);
    }

    // ------------------------------------------------------------------ clics

    @Override
    protected boolean onPanelClick(int mx, int my) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return false;
        }
        for (int i = 0; i < TABS; i++) {
            int x = PAD + i * (TAB_W + TAB_GAP);
            if (inside(mx, my, x, TAB_Y, TAB_W, TAB_H)) {
                playClickSound();
                if (i == 2) {
                    this.minecraft.gui.setScreen(new RaceAttacksScreen(this, closeKey)); // page dédiée aux attaques
                } else {
                    tab = i;
                }
                return true;
            }
        }
        if (inside(mx, my, PAD, backY(), 62, BTN_H)) {
            playClickSound();
            onClose();
            return true;
        }
        if (tab == REROLL) {
            int bw = 110;
            if (inside(mx, my, (PANEL_W - bw) / 2, ACTION_Y, bw, BTN_H)) {
                boolean waiting = System.currentTimeMillis() - rerollSentAt < REROLL_LOCK_MS && rollTarget == null;
                if (!waiting && !rolling() && RaceManager.tokens(player) > 0) {
                    playClickSound();
                    rerollSentAt = System.currentTimeMillis(); // un seul envoi par clic ; le serveur refuse aussi les doublons
                    rollPrevious = Races.of(player);    // race d'avant, montrée jusqu'à la révélation
                    rollTarget = null;
                    ClientPlayNetworking.send(RaceRerollPayload.INSTANCE);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
