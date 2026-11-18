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
import nolan.bloxpiece.quest.DailyQuest;
import nolan.bloxpiece.quest.QuestData;
import nolan.bloxpiece.quest.QuestType;
import nolan.bloxpiece.quest.Quests;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModItems;

/**
 * Page Quêtes du menu personnage (bouton "Quêtes"). Onglets de catégories en haut (QuestCategory : "Quêtes journalières" et
 * "Quêtes uniques", ces dernières encore vides), temps restant avant les nouvelles quêtes à droite, puis une carte par quête : titre, objectif,
 * barre de progression "5 / 10", récompense (icône + nombre) et bouton "Récupérer" (actif quand la quête est terminée,
 * "Terminée" une fois récupérée). Tout est LU à chaque image depuis l'attachment QUESTS (synchronisé par le serveur) ; le bouton
 * envoie ClaimQuestPayload et le serveur vérifie tout.
 */
public class QuestsScreen extends BloxPanelScreen {

    private static final int TABS_Y = 30;
    private static final int TAB_H = 14;
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
            drawUniqueQuests(g, font);
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
            int w = font.width(label) + 14;
            boolean selected = cat == this.category;
            boolean hovered = inside(mx, my, x, TABS_Y, w, TAB_H);
            g.fill(x, TABS_Y, x + w, TABS_Y + TAB_H, selected ? 0xFF2A2418 : CharacterInfoRow.COLOR_BACKGROUND);
            g.outline(x, TABS_Y, w, TAB_H, selected || hovered ? COLOR_GOLD_BRIGHT : COLOR_GOLD_DIM);
            g.centeredText(font, label, x + w / 2, TABS_Y + 3, selected ? COLOR_GOLD_BRIGHT : COLOR_LABEL);
            x += w + 4;
        }
    }

    /**
     * Onglet Quêtes uniques : encore aucune quête (le contenu viendra plus tard). Un cadre en pointillés dorés avec une icône,
     * un titre et une explication, centré dans la zone des cartes.
     */
    private void drawUniqueQuests(GuiGraphicsExtractor g, Font font) {
        int x = CARD_X, y = CARDS_Y, w = CARD_W, h = Quests.DAILY_COUNT * (CARD_H + CARD_GAP) - CARD_GAP;
        g.fill(x, y, x + w, y + h, COLOR_CARD);
        // bordure en pointillés
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
        g.centeredText(font, Component.translatable("screen.bloxpiece.quests.unique.empty"), x + w / 2, cy - 10, COLOR_GOLD_BRIGHT);
        g.centeredText(font, Component.translatable("screen.bloxpiece.quests.unique.hint"), x + w / 2, cy + 4, COLOR_LABEL);
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
            int w = this.minecraft.font.width(Component.translatable(cat.titleKey())) + 14;
            if (inside(mx, my, tx, TABS_Y, w, TAB_H)) {
                if (cat != this.category) {
                    playClickSound();
                    this.category = cat;
                }
                return true;
            }
            tx += w + 4;
        }
        if (this.category != QuestCategory.DAILY) {
            return false; // pas encore de bouton dans l'onglet des quêtes uniques
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
