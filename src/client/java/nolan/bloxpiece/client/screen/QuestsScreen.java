package nolan.bloxpiece.client.screen;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import nolan.bloxpiece.network.ClaimQuestPayload;
import nolan.bloxpiece.quest.UniqueQuest;
import nolan.bloxpiece.quest.UniqueQuestData;
import nolan.bloxpiece.quest.DailyQuest;
import nolan.bloxpiece.quest.QuestData;
import nolan.bloxpiece.quest.QuestType;
import nolan.bloxpiece.quest.Quests;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModItems;

/**
 * Page Quêtes du menu personnage (bouton "Quêtes"). Onglets de catégories en haut (QuestCategory : "Quêtes journalières" et
 * "Quêtes uniques" : les quêtes de Haki de Silvers Rayleigh, liste et détail), temps restant avant les nouvelles quêtes à droite, puis une carte par quête : titre, objectif,
 * barre de progression "5 / 10", récompense (icône + nombre) et bouton "Récupérer" (actif quand la quête est terminée,
 * "Terminée" une fois récupérée). Tout est LU à chaque image depuis l'attachment QUESTS (synchronisé par le serveur) ; le bouton
 * envoie ClaimQuestPayload et le serveur vérifie tout.
 */
public class QuestsScreen extends BloxPanelScreen {

    private static final int TABS_Y = 30;
    private static final int TAB_H = 14;
    /** Marge intérieure des onglets. */
    private static final int TAB_PAD = 14;
    private static final int CARDS_Y = 50;
    private static final int CARD_H = 40;
    private static final int CARD_GAP = 4;
    private static final int CARD_X = PAD;
    private static final int CARD_W = PANEL_W - 2 * PAD;
    /** Largeur réservée aux deux récompenses (icône + nombre chacune), entre les textes et le bouton Récupérer. */
    private static final int REWARDS_W = 76;
    private static final int CLAIM_W = 70;
    private static final int CLAIM_H = 16;

    private static final int BACK_W = 62;
    private static final int BACK_H = 14;
    private static final int BACK_X = PAD;

    private static final int COLOR_CARD = 0xFF15151F;
    private static final int COLOR_CARD_DONE = 0xFF142016;
    private static final int COLOR_GREEN = 0xFF5BE36B;

    private final CharacterScreen parent;
    private QuestCategory category = QuestCategory.DAILY;

    public QuestsScreen(CharacterScreen parent, KeyMapping closeKey) {
        super(Component.translatable("screen.bloxpiece.quests.title"), closeKey, false);
        this.parent = parent;
    }

    @Override
    protected int panelHeight() {
        return backY() + BACK_H + 10;
    }

    private int backY() {
        return CARDS_Y + Quests.DAILY_COUNT * (CARD_H + CARD_GAP) + 2;
    }

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, Font font, LocalPlayer player,
                                int mx, int my, int mouseX, int mouseY, float dt) {
        drawTabs(g, font, mx, my);
        if (this.category == QuestCategory.UNIQUE) {
            drawUniqueQuests(g, font, player, mx, my);
            drawBackButton(g, font, mx, my);
            return;
        }
        if (this.category == QuestCategory.REPEATABLE) {
            drawRepeatable(g, font, player);
            drawBackButton(g, font, mx, my);
            return;
        }
        QuestData data = player.getAttached(ModAttachments.QUESTS);
        if (data != null) {
            drawResetTimer(g, font, player, data);
        }
        if (data == null || data.daily().isEmpty()) {
            g.centeredText(font, Component.translatable("screen.bloxpiece.quests.loading"), PANEL_W / 2, CARDS_Y + 20, COLOR_LABEL);
        } else {
            List<DailyQuest> quests = data.daily();
            for (int i = 0; i < quests.size(); i++) {
                drawCard(g, font, quests.get(i), CARDS_Y + i * (CARD_H + CARD_GAP), mx, my, mouseX, mouseY);
            }
        }
        drawBackButton(g, font, mx, my);
    }

    // ------------------------------------------------------------------ onglets et minuteur

    private void drawTabs(GuiGraphicsExtractor g, Font font, int mx, int my) {
        int x = PAD;
        for (QuestCategory cat : QuestCategory.values()) {
            Component label = Component.translatable(cat.titleKey());
            int w = font.width(label) + TAB_PAD;
            boolean selected = cat == this.category;
            boolean hovered = inside(mx, my, x, TABS_Y, w, TAB_H);
            g.fill(x, TABS_Y, x + w, TABS_Y + TAB_H, selected ? 0xFF2A2418 : CharacterInfoRow.COLOR_BACKGROUND);
            g.outline(x, TABS_Y, w, TAB_H, selected || hovered ? COLOR_GOLD_BRIGHT : COLOR_GOLD_DIM);
            g.centeredText(font, label, x + w / 2, TABS_Y + 3, selected ? COLOR_GOLD_BRIGHT : COLOR_LABEL);
            x += w + 4;
        }
    }

    // ------------------------------------------------------------------ quêtes uniques (quêtes de Haki de Silvers Rayleigh)

    private static final int UNIQUE_H = Quests.DAILY_COUNT * (CARD_H + CARD_GAP) - CARD_GAP;
    private static final int ROW_H = 19;
    private static final int ROW_GAP = 2;
    private static final int LIST_W = 54;
    private static final int LIST_H = 13;
    private static final int COLOR_BLUE = 0xFF55C7FF;
    private static final int COLOR_RED = 0xFFE05A5A;

    /** Quête unique ouverte en détail (null : la liste). */
    private UniqueQuest selected;

    private static int statusColor(UniqueQuest.Status status) {
        return switch (status) {
            case AVAILABLE -> COLOR_GOLD_BRIGHT;
            case ACTIVE -> COLOR_BLUE;
            case COMPLETED -> COLOR_GREEN;
            case LOCKED -> COLOR_LABEL;
            case UNRELEASED -> COLOR_HINT;
        };
    }

    /**
     * Onglet Quêtes uniques : SEULEMENT les quêtes de Haki que le joueur a en cours (acceptées auprès de Silvers Rayleigh, pas
     * encore terminées). Liste : une ligne par quête (pastille d'état, nom, niveau requis, état) ; un clic ouvre son détail (nom,
     * état, Haki concerné, niveau requis, description, chaque objectif avec sa barre de progression, récompense). Aucune quête
     * en cours : cadre vide qui renvoie vers Rayleigh. Tout est LU depuis les attachments synchronisés (UNIQUE_QUESTS, niveau,
     * Haki débloqués) : rien n'est décidé ici.
     */
    private void drawUniqueQuests(GuiGraphicsExtractor g, Font font, LocalPlayer player, int mx, int my) {
        if (this.selected != null && this.selected.status(player) != UniqueQuest.Status.ACTIVE) {
            this.selected = null; // quête terminée pendant qu'on la regardait : elle quitte la liste
        }
        if (this.selected != null) {
            drawUniqueDetail(g, font, player, this.selected, mx, my);
            return;
        }
        int level = nolan.bloxpiece.progression.Progression.get(player).level();
        List<UniqueQuest> quests = activeQuests(player);
        if (quests.isEmpty()) {
            drawNoActiveQuest(g, font);
            return;
        }
        for (int i = 0; i < quests.size(); i++) {
            UniqueQuest quest = quests.get(i);
            UniqueQuest.Status status = quest.status(player);
            int y = CARDS_Y + i * (ROW_H + ROW_GAP);
            boolean hovered = inside(mx, my, CARD_X, y, CARD_W, ROW_H);
            g.fill(CARD_X, y, CARD_X + CARD_W, y + ROW_H, status == UniqueQuest.Status.COMPLETED ? COLOR_CARD_DONE
                    : hovered ? CharacterInfoRow.COLOR_BACKGROUND_HOVER : COLOR_CARD);
            g.outline(CARD_X, y, CARD_W, ROW_H, hovered ? COLOR_GOLD_BRIGHT
                    : status == UniqueQuest.Status.AVAILABLE ? COLOR_GOLD : COLOR_GOLD_DIM);
            int color = statusColor(status);
            g.fill(CARD_X + 5, y + 6, CARD_X + 12, y + 13, color);
            // à droite : état, puis niveau requis
            Component state = Component.translatable(status.key());
            int stateX = CARD_X + CARD_W - 6 - font.width(state);
            g.text(font, state, stateX, y + 6, color, true);
            Component lvl = Component.translatable("screen.bloxpiece.quests.unique.level_short", quest.level);
            int lvlX = stateX - 10 - font.width(lvl);
            g.text(font, lvl, lvlX, y + 6, level >= quest.level ? COLOR_LABEL : COLOR_RED, false);
            Component title = fit(font, Component.translatable(quest.titleKey()), lvlX - 8 - (CARD_X + 17));
            g.text(font, title, CARD_X + 17, y + 6, status == UniqueQuest.Status.UNRELEASED ? COLOR_LABEL : 0xFFFFFFFF, true);
        }
    }

    private void drawUniqueDetail(GuiGraphicsExtractor g, Font font, LocalPlayer player, UniqueQuest quest, int mx, int my) {
        int x = CARD_X, y = CARDS_Y, w = CARD_W, h = UNIQUE_H;
        UniqueQuest.Status status = quest.status(player);
        int level = nolan.bloxpiece.progression.Progression.get(player).level();
        g.fill(x, y, x + w, y + h, status == UniqueQuest.Status.COMPLETED ? COLOR_CARD_DONE : COLOR_CARD);
        g.outline(x, y, w, h, COLOR_GOLD_DIM);
        int left = x + 6, right = x + w - 6;

        // nom et état
        Component state = Component.translatable(status.key());
        g.text(font, state, right - font.width(state), y + 5, statusColor(status), true);
        g.text(font, fit(font, Component.translatable(quest.titleKey()).copy().withStyle(net.minecraft.ChatFormatting.BOLD),
                right - font.width(state) - 8 - left), left, y + 5, COLOR_GOLD_BRIGHT, true);
        // Haki concerné et niveau requis
        Component lvl = Component.translatable("screen.bloxpiece.quests.unique.level", quest.level);
        g.text(font, lvl, right - font.width(lvl), y + 17, level >= quest.level ? COLOR_LABEL : COLOR_RED, false);
        Component haki = Component.translatable(quest.evolution ? "screen.bloxpiece.quests.unique.haki_evolved"
                : "screen.bloxpiece.quests.unique.haki", Component.translatable(quest.haki.nameKey()));
        g.text(font, fit(font, haki, right - font.width(lvl) - 8 - left), left, y + 17, COLOR_LABEL, false);
        // description (deux lignes au plus)
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable(quest.descriptionKey()), right - left);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(font, lines.get(i), left, y + 29 + i * (font.lineHeight + 1), 0xFFE8E4D8, false);
        }

        // objectifs et leur progression
        int oy = y + 51;
        if (!quest.isReleased()) {
            g.text(font, fit(font, Component.translatable("screen.bloxpiece.quests.unique.unreleased"), right - left), left, oy + 4,
                    COLOR_HINT, false);
        } else {
            UniqueQuestData data = player.getAttached(ModAttachments.UNIQUE_QUESTS);
            UniqueQuestData.Entry entry = data == null ? null : data.get(quest);
            for (int i = 0; i < quest.objectives.size(); i++) {
                UniqueQuest.Objective o = quest.objectives.get(i);
                int value = status == UniqueQuest.Status.COMPLETED ? o.target() : entry == null ? 0 : Math.min(o.target(), entry.progress(i));
                boolean done = value >= o.target();
                String counter = value + " / " + o.target();
                g.text(font, counter, right - font.width(counter), oy, done ? COLOR_GREEN : 0xFFFFFFFF, true);
                g.text(font, fit(font, nolan.bloxpiece.quest.UniqueQuests.objectiveText(o), right - font.width(counter) - 8 - left),
                        left, oy, COLOR_LABEL, false);
                int by = oy + 10;
                g.fill(left, by, right, by + 5, 0xFF07070C);
                int fill = Math.round((right - left - 2) * Math.min(1.0F, value / (float) Math.max(1, o.target())));
                if (fill > 0) {
                    g.fillGradient(left + 1, by + 1, left + 1 + fill, by + 4, done ? 0xFF8BF28F : 0xFF55C7FF, done ? 0xFF2E9A3A : 0xFF1F6BE0);
                }
                g.outline(left, by, right - left, 5, COLOR_GOLD_DIM);
                oy += 18;
            }
        }

        // indication selon l'état
        Component hint = switch (status) {
            case AVAILABLE -> Component.translatable("screen.bloxpiece.quests.unique.hint.available");
            case ACTIVE -> Component.translatable("screen.bloxpiece.quests.unique.hint.active");
            case COMPLETED -> Component.translatable("screen.bloxpiece.quests.unique.hint.completed");
            case LOCKED -> level < quest.level ? Component.translatable("screen.bloxpiece.quests.unique.hint.level", quest.level)
                    : Component.translatable("screen.bloxpiece.quests.unique.hint.requires",
                    Component.translatable(quest.requires == null ? quest.titleKey() : quest.requires.titleKey()));
            case UNRELEASED -> Component.translatable("screen.bloxpiece.quests.unique.hint.unreleased");
        };
        g.text(font, fit(font, hint, right - left), left, y + h - 30, statusColor(status), false);

        // récompense et bouton Liste
        int listX = right - LIST_W, listY = y + h - LIST_H - 4;
        Component reward = Component.translatable("screen.bloxpiece.quests.unique.reward",
                Component.translatable(quest.rewardKey(), Component.translatable(quest.haki.nameKey())));
        g.text(font, fit(font, reward, listX - 8 - left), left, listY + 3, COLOR_GOLD, false);
        boolean hovered = inside(mx, my, listX, listY, LIST_W, LIST_H);
        g.fill(listX, listY, listX + LIST_W, listY + LIST_H, hovered ? CharacterInfoRow.COLOR_BACKGROUND_HOVER : CharacterInfoRow.COLOR_BACKGROUND);
        g.outline(listX, listY, LIST_W, LIST_H, hovered ? COLOR_GOLD_BRIGHT : COLOR_GOLD_DIM);
        g.centeredText(font, Component.translatable("screen.bloxpiece.quests.unique.list"), listX + LIST_W / 2, listY + 3,
                hovered ? COLOR_GOLD_BRIGHT : COLOR_LABEL);
    }

    /** Clics dans l'onglet des quêtes uniques : ouvrir une quête, ou revenir à la liste. */
    private boolean onUniqueClick(int mx, int my) {
        if (this.selected != null) {
            int listX = CARD_X + CARD_W - 6 - LIST_W, listY = CARDS_Y + UNIQUE_H - LIST_H - 4;
            if (inside(mx, my, listX, listY, LIST_W, LIST_H)) {
                playClickSound();
                this.selected = null;
                return true;
            }
            return false;
        }
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return false;
        }
        List<UniqueQuest> quests = activeQuests(player);
        for (int i = 0; i < quests.size(); i++) {
            if (inside(mx, my, CARD_X, CARDS_Y + i * (ROW_H + ROW_GAP), CARD_W, ROW_H)) {
                playClickSound();
                this.selected = quests.get(i);
                return true;
            }
        }
        return false;
    }

    /** Les quêtes uniques que le joueur a en cours, dans l'ordre de UniqueQuest (mêmes positions au dessin et au clic). */
    private static List<UniqueQuest> activeQuests(LocalPlayer player) {
        List<UniqueQuest> list = new java.util.ArrayList<>();
        for (UniqueQuest quest : UniqueQuest.values()) {
            if (quest.status(player) == UniqueQuest.Status.ACTIVE) {
                list.add(quest);
            }
        }
        return list;
    }

    /** Aucune quête unique en cours : cadre en pointillés dorés, icône, titre et rappel « parle à Rayleigh ». */
    private void drawNoActiveQuest(GuiGraphicsExtractor g, Font font) {
        drawEmptyFrame(g, font, "screen.bloxpiece.quests.unique.empty", "screen.bloxpiece.quests.unique.hint");
    }

    // ------------------------------------------------------------------ quêtes répétables (missions du Tavernier)

    /**
     * Onglet Quêtes répétables : la mission du Tavernier en cours (une seule à la fois) : nom et état, difficulté (couleur) et
     * récompense en XP, description, chaque objectif séparément (ennemis tués / demandés, restants, barre), puis l'indication
     * « en cours » ou « terminée : retourne voir le Tavernier ». Aucune mission : cadre vide qui renvoie vers le Tavernier.
     * Tout est LU depuis l'attachment REPEATABLE_QUESTS (synchronisé) : la récompense se récupère auprès du Tavernier.
     */
    private void drawRepeatable(GuiGraphicsExtractor g, Font font, LocalPlayer player) {
        nolan.bloxpiece.quest.RepeatableQuest.Data data = player.getAttached(ModAttachments.REPEATABLE_QUESTS);
        if (data == null || data.active().isEmpty()) {
            drawEmptyFrame(g, font, "screen.bloxpiece.quests.repeatable.empty", "screen.bloxpiece.quests.repeatable.hint");
            return;
        }
        nolan.bloxpiece.quest.RepeatableQuest quest = data.active().get();
        int x = CARD_X, y = CARDS_Y, w = CARD_W, h = UNIQUE_H;
        boolean done = quest.completed();
        g.fill(x, y, x + w, y + h, done ? COLOR_CARD_DONE : COLOR_CARD);
        g.outline(x, y, w, h, done ? COLOR_GOLD_BRIGHT : COLOR_GOLD_DIM);
        int left = x + 6, right = x + w - 6;

        // nom et état
        Component state = Component.translatable(done ? "screen.bloxpiece.quests.unique.status.completed"
                : "screen.bloxpiece.quests.unique.status.active");
        g.text(font, state, right - font.width(state), y + 5, done ? COLOR_GREEN : COLOR_BLUE, true);
        g.text(font, fit(font, Component.translatable(quest.titleKey()).copy().withStyle(net.minecraft.ChatFormatting.BOLD),
                right - font.width(state) - 8 - left), left, y + 5, COLOR_GOLD_BRIGHT, true);
        // difficulté et récompense
        Component reward = Component.translatable("screen.bloxpiece.quests.repeatable.reward", quest.reward());
        g.text(font, reward, right - font.width(reward), y + 17, COLOR_GREEN, false);
        Component difficulty = Component.translatable("screen.bloxpiece.quests.repeatable.difficulty",
                Component.translatable(nolan.bloxpiece.quest.RepeatableQuest.difficultyKey(quest.difficulty()))
                        .withColor(nolan.bloxpiece.quest.RepeatableQuest.difficultyColor(quest.difficulty())));
        g.text(font, fit(font, difficulty, right - font.width(reward) - 8 - left), left, y + 17, COLOR_LABEL, false);
        // description (une ligne)
        g.text(font, fit(font, Component.translatable(quest.descriptionKey()), right - left), left, y + 29, 0xFFE8E4D8, false);

        // objectifs, séparément : nom de la cible, tués / demandés, restants, barre
        int oy = y + 43;
        for (nolan.bloxpiece.quest.RepeatableQuest.Objective o : quest.objectives()) {
            boolean ok = o.done();
            String counter = o.progress() + " / " + o.count();
            Component left2 = ok ? Component.translatable("screen.bloxpiece.quests.repeatable.objective_done")
                    : Component.translatable("screen.bloxpiece.quests.repeatable.remaining", o.count() - o.progress());
            int counterX = right - font.width(counter);
            g.text(font, counter, counterX, oy, ok ? COLOR_GREEN : 0xFFFFFFFF, true);
            int remainX = counterX - 8 - font.width(left2);
            g.text(font, left2, remainX, oy, ok ? COLOR_GREEN : COLOR_HINT, false);
            g.text(font, fit(font, Component.translatable("screen.bloxpiece.quests.repeatable.kill", TavernQuestDialogue.targetName(o)),
                    remainX - 8 - left), left, oy, COLOR_LABEL, false);
            int by = oy + 10;
            g.fill(left, by, right, by + 5, 0xFF07070C);
            int fill = Math.round((right - left - 2) * Math.min(1.0F, o.progress() / (float) Math.max(1, o.count())));
            if (fill > 0) {
                g.fillGradient(left + 1, by + 1, left + 1 + fill, by + 4, ok ? 0xFF8BF28F : 0xFF55C7FF, ok ? 0xFF2E9A3A : 0xFF1F6BE0);
            }
            g.outline(left, by, right - left, 5, COLOR_GOLD_DIM);
            oy += 18;
        }

        // état de la mission
        Component hint = Component.translatable(done ? "screen.bloxpiece.quests.repeatable.hint_done"
                : "screen.bloxpiece.quests.repeatable.hint_active");
        g.text(font, fit(font, hint, right - left), left, y + h - 13, done ? COLOR_GOLD_BRIGHT : COLOR_LABEL, false);
    }

    /** Cadre vide en pointillés dorés (icône, titre, indication). */
    private void drawEmptyFrame(GuiGraphicsExtractor g, Font font, String titleKey, String hintKey) {
        int x = CARD_X, y = CARDS_Y, w = CARD_W, h = UNIQUE_H;
        g.fill(x, y, x + w, y + h, COLOR_CARD);
        for (int i = 0; i < w; i += 6) {
            g.fill(x + i, y, x + Math.min(w, i + 3), y + 1, COLOR_GOLD_DIM);
            g.fill(x + i, y + h - 1, x + Math.min(w, i + 3), y + h, COLOR_GOLD_DIM);
        }
        for (int i = 0; i < h; i += 6) {
            g.fill(x, y + i, x + 1, y + Math.min(h, i + 3), COLOR_GOLD_DIM);
            g.fill(x + w - 1, y + i, x + w, y + Math.min(h, i + 3), COLOR_GOLD_DIM);
        }
        int cy = y + h / 2;
        g.item(new ItemStack(ModItems.TREASURE_CHEST), x + w / 2 - 8, cy - 34);
        g.centeredText(font, fit(font, Component.translatable(titleKey), w - 12), x + w / 2, cy - 10,
                COLOR_GOLD_BRIGHT);
        g.centeredText(font, fit(font, Component.translatable(hintKey), w - 12), x + w / 2, cy + 4,
                COLOR_LABEL);
    }

    /**
     * "Nouvelles quêtes dans 12:34" : temps réel restant avant le prochain jour Minecraft (20 ticks = 1 s). Affiché à droite de
     * la ligne du bouton Retour (la ligne des onglets reste libre pour les onglets : aucun chevauchement).
     */
    private void drawResetTimer(GuiGraphicsExtractor g, Font font, LocalPlayer player, QuestData data) {
        long ticksLeft = Math.max(0L, data.resetAt() - player.level().getGameTime()); // resetAt vient du serveur
        long seconds = ticksLeft / 20;
        Component text = fit(font, Component.translatable("screen.bloxpiece.quests.reset",
                String.format("%d:%02d", seconds / 60, seconds % 60)), PANEL_W - 2 * PAD - BACK_W - 10);
        g.text(font, text, PANEL_W - PAD - font.width(text), backY() + (BACK_H - font.lineHeight) / 2 + 1, COLOR_LABEL, true);
    }

    // ------------------------------------------------------------------ cartes de quêtes

    private void drawCard(GuiGraphicsExtractor g, Font font, DailyQuest quest, int y, int mx, int my, int mouseX, int mouseY) {
        QuestType type = quest.questType();
        boolean done = quest.isComplete();
        g.fill(CARD_X, y, CARD_X + CARD_W, y + CARD_H, done ? COLOR_CARD_DONE : COLOR_CARD);
        g.outline(CARD_X, y, CARD_W, CARD_H, done && !quest.claimed() ? COLOR_GOLD_BRIGHT : COLOR_GOLD_DIM);

        // Titre et objectif
        Component title = type == null ? Component.literal(quest.type()) : Component.translatable(type.titleKey());
        Component objective = type == null ? Component.empty() : Component.translatable(type.objectiveKey(), quest.target());
        // zone de texte : de la gauche de la carte jusqu'avant les icônes de récompense (rien ne passe dessous)
        int textRight = CARD_X + CARD_W - CLAIM_W - 6 - REWARDS_W - 6;
        String counter = quest.progress() + " / " + quest.target();
        int counterX = textRight - font.width(counter);
        g.text(font, fit(font, title, counterX - 6 - (CARD_X + 6)), CARD_X + 6, y + 5, COLOR_GOLD_BRIGHT, true);
        g.text(font, counter, counterX, y + 5, done ? COLOR_GREEN : 0xFFFFFFFF, true);
        g.text(font, fit(font, objective, textRight - (CARD_X + 6)), CARD_X + 6, y + 16, COLOR_LABEL, false);

        // Barre de progression (sur toute la largeur de la zone de texte)
        int bx = CARD_X + 6, by = y + 28;
        int barW = textRight - bx;
        g.fill(bx, by, bx + barW, by + 7, 0xFF07070C);
        int fill = Math.round((barW - 2) * Math.min(1.0F, quest.progress() / (float) Math.max(1, quest.target())));
        if (fill > 0) {
            g.fillGradient(bx + 1, by + 1, bx + 1 + fill, by + 6, done ? 0xFF8BF28F : 0xFF55C7FF, done ? 0xFF2E9A3A : 0xFF1F6BE0);
        }
        g.outline(bx, by, barW, 7, COLOR_GOLD_DIM);

        // Récompenses : cristaux d'XP garantis (selon la difficulté) + bonus tiré (survol : nom de la récompense)
        int claimX = CARD_X + CARD_W - CLAIM_W - 6;
        int ry = y + (CARD_H - 16) / 2;
        if (type != null) {
            drawReward(g, font, type.crystal(quest.target()), claimX - REWARDS_W, ry, mx, my, mouseX, mouseY);
        }
        drawReward(g, font, quest.reward(), claimX - REWARDS_W / 2, ry, mx, my, mouseX, mouseY);

        // Bouton Récupérer / Terminée
        int cy = y + (CARD_H - CLAIM_H) / 2;
        boolean claimable = done && !quest.claimed();
        boolean hovered = claimable && inside(mx, my, claimX, cy, CLAIM_W, CLAIM_H);
        g.fill(claimX, cy, claimX + CLAIM_W, cy + CLAIM_H, claimable ? (hovered ? 0xFF3A3010 : 0xFF2A2410) : 0xFF101016);
        g.outline(claimX, cy, CLAIM_W, CLAIM_H, claimable ? (hovered ? COLOR_GOLD_BRIGHT : COLOR_GOLD) : 0xFF33333F);
        Component label = Component.translatable(quest.claimed() ? "screen.bloxpiece.quests.claimed" : "screen.bloxpiece.quests.claim");
        int color = quest.claimed() ? COLOR_GREEN : claimable ? COLOR_GOLD_BRIGHT : COLOR_HINT;
        g.centeredText(font, label, claimX + CLAIM_W / 2, cy + (CLAIM_H - font.lineHeight) / 2 + 1, color);
    }

    private void drawReward(GuiGraphicsExtractor g, Font font, QuestType.Reward reward, int rx, int ry, int mx, int my,
            int mouseX, int mouseY) {
        ItemStack icon = reward.isXp() ? new ItemStack(ModItems.XP_CRYSTAL_CABIN_BOY)
                : new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(reward.id())), reward.count());
        g.fill(rx - 2, ry - 2, rx + 18, ry + 18, 0xFF07070C);
        g.outline(rx - 2, ry - 2, 20, 20, COLOR_GOLD_DIM);
        g.item(icon, rx, ry);
        String count = reward.isXp() ? "+" + reward.count() : (reward.count() > 1 ? "x" + reward.count() : "");
        g.text(font, count, rx + 20, ry + 8, reward.isXp() ? COLOR_GREEN : 0xFFFFFFFF, true);
        if (inside(mx, my, rx - 2, ry - 2, 20, 20)) {
            Component tip = reward.isXp()
                    ? Component.translatable("screen.bloxpiece.quests.reward_xp", reward.count())
                    : Component.translatable("screen.bloxpiece.quests.reward_item", reward.count(), icon.getHoverName());
            g.setTooltipForNextFrame(font, tip, mouseX, mouseY);
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

    // ------------------------------------------------------------------ clics

    @Override
    protected boolean onPanelClick(int mx, int my) {
        if (inside(mx, my, BACK_X, backY(), BACK_W, BACK_H)) {
            playClickSound();
            onClose();
            return true;
        }
        // onglets de catégories (mêmes positions que drawTabs)
        int tx = PAD;
        for (QuestCategory cat : QuestCategory.values()) {
            int w = this.minecraft.font.width(Component.translatable(cat.titleKey())) + TAB_PAD;
            if (inside(mx, my, tx, TABS_Y, w, TAB_H)) {
                if (cat != this.category) {
                    playClickSound();
                    this.category = cat;
                }
                return true;
            }
            tx += w + 4;
        }
        if (this.category == QuestCategory.UNIQUE) {
            return onUniqueClick(mx, my);
        }
        if (this.category == QuestCategory.REPEATABLE) {
            return false; // rien à cliquer : la récompense se récupère auprès du Tavernier
        }
        LocalPlayer player = this.minecraft.player;
        QuestData data = player == null ? null : player.getAttached(ModAttachments.QUESTS);
        if (data == null) {
            return false;
        }
        int claimX = CARD_X + CARD_W - CLAIM_W - 6;
        for (int i = 0; i < data.daily().size(); i++) {
            DailyQuest quest = data.daily().get(i);
            int cy = CARDS_Y + i * (CARD_H + CARD_GAP) + (CARD_H - CLAIM_H) / 2;
            if (inside(mx, my, claimX, cy, CLAIM_W, CLAIM_H) && quest.isComplete() && !quest.claimed()) {
                playClickSound();
                ClientPlayNetworking.send(new ClaimQuestPayload(i));
                return true;
            }
        }
        return false;
    }

    /** Échap et le bouton Retour : retour au menu personnage. */
    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
