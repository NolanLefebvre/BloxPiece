package nolan.bloxpiece.client.screen;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.network.PartyActionPayload;
import nolan.bloxpiece.network.PartySyncPayload;

/**
 * Page « Party » du menu du personnage (bouton Party, à côté de Quêtes et Haki) : même style que les pages Quêtes et Haki
 * (cadre, boutons dorés, lignes sombres). Elle relit ClientParty à chaque image : tout changement envoyé par le serveur
 * s'affiche aussitôt.
 * <ul>
 *   <li>Sans Party : message, bouton « Créer une Party », et les invitations reçues avec [Accepter] / [Refuser] (c'est ainsi
 *       qu'on rejoint une Party).</li>
 *   <li>Avec une Party : nom, chef, nombre de membres / limite ; un membre par ligne (tête si le joueur est visible, pseudo,
 *       « Chef », connecté / déconnecté). Le chef a, sur chaque autre membre, [Chef] (lui passer le rôle) et [Exclure] ; en bas
 *       [Inviter un joueur] (liste des joueurs connectés sans Party, avec [Inviter]) et [Dissoudre] (deux clics) ; tout membre a
 *       [Quitter].</li>
 * </ul>
 * Chaque bouton n'envoie qu'une DEMANDE (PartyActionPayload) : le serveur vérifie tout.
 */
public class PartyScreen extends BloxPanelScreen {

    private static final int X = PAD;
    private static final int W = PANEL_W - 2 * PAD;
    private static final int TOP = 32;
    private static final int ROW_H = 20;
    private static final int ROW_GAP = 2;
    private static final int BTN_H = 14;
    private static final int SMALL_W = 44;
    private static final int FACE = 14;
    private static final int CANDIDATES_PER_PAGE = 5;
    private static final int COLOR_ONLINE = 0xFF6FD86F;
    private static final int COLOR_OFFLINE = 0xFF6E6E7A;
    private static final int COLOR_DANGER = 0xFFE06060;
    private static final long CONFIRM_MS = 3000L;

    private final CharacterScreen parent;
    private boolean inviting;
    private int page;
    private long disbandArmedAt;

    public PartyScreen(CharacterScreen parent, KeyMapping closeKey) {
        super(Component.translatable("screen.bloxpiece.party.title"), closeKey, false);
        this.parent = parent;
    }

    // ------------------------------------------------------------------ mise en page (calculée à chaque image)

    private static PartySyncPayload state() {
        return ClientParty.get();
    }

    private boolean isLeader() {
        PartySyncPayload s = state();
        return s.inParty() && this.minecraft.player != null && s.leader().equals(this.minecraft.player.getUUID().toString());
    }

    /** Ordonnée du bas du contenu principal (avant le bouton Retour). */
    private int contentBottom() {
        PartySyncPayload s = state();
        if (!s.inParty()) {
            int y = TOP + 44;
            int n = Math.max(1, s.invites().size());
            return y + 14 + n * (ROW_H + ROW_GAP);
        }
        int y = membersTop() + s.members().size() * (ROW_H + ROW_GAP) + 4;   // boutons d'action
        y += BTN_H;
        if (inviting && isLeader()) {
            y += 8 + 12 + Math.max(1, Math.min(CANDIDATES_PER_PAGE, s.candidates().size())) * (ROW_H + ROW_GAP) + 12;
        }
        return y;
    }

    private static int membersTop() {
        return TOP + 16;
    }

    private int actionsY() {
        return membersTop() + state().members().size() * (ROW_H + ROW_GAP) + 4;
    }

    private int backY() {
        return contentBottom() + 8;
    }

    @Override
    protected int panelHeight() {
        return Math.max(PANEL_H, backY() + BTN_H + 10);
    }

    // ------------------------------------------------------------------ dessin

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, Font font, LocalPlayer player,
                                int mx, int my, int mouseX, int mouseY, float dt) {
        PartySyncPayload s = state();
        if (s.inParty()) {
            drawParty(g, font, player, s, mx, my);
        } else {
            drawNoParty(g, font, s, mx, my);
        }
        button(g, font, mx, my, X, backY(), 62, Component.translatable("screen.bloxpiece.abilities.back"), COLOR_GOLD_DIM);
    }

    private void drawNoParty(GuiGraphicsExtractor g, Font font, PartySyncPayload s, int mx, int my) {
        g.centeredText(font, Component.translatable("screen.bloxpiece.party.none"), PANEL_W / 2, TOP + 4, COLOR_LABEL);
        int cw = 120;
        button(g, font, mx, my, (PANEL_W - cw) / 2, TOP + 20, cw, Component.translatable("screen.bloxpiece.party.create"), COLOR_GOLD);

        int y = TOP + 44;
        g.text(font, Component.translatable("screen.bloxpiece.party.invites"), X, y, COLOR_GOLD, true);
        y += 14;
        if (s.invites().isEmpty()) {
            rowBackground(g, X, y, W, false);
            g.text(font, fit(font, Component.translatable("screen.bloxpiece.party.no_invites"), W - 12), X + 6,
                    y + (ROW_H - font.lineHeight) / 2 + 1, COLOR_HINT, false);
            return;
        }
        for (PartySyncPayload.Invite invite : s.invites()) {
            boolean hovered = inside(mx, my, X, y, W, ROW_H);
            rowBackground(g, X, y, W, hovered);
            Component text = Component.translatable("screen.bloxpiece.party.invite_row", invite.inviter(),
                    Component.translatable("party.bloxpiece.default_name", invite.partyName()), invite.secondsLeft());
            g.text(font, fit(font, text, W - 2 * SMALL_W - 18), X + 6, y + (ROW_H - font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);
            int bx = X + W - 2 * SMALL_W - 6;
            button(g, font, mx, my, bx, y + 3, SMALL_W, Component.translatable("screen.bloxpiece.party.accept"), COLOR_ONLINE);
            button(g, font, mx, my, bx + SMALL_W + 2, y + 3, SMALL_W, Component.translatable("screen.bloxpiece.party.refuse"), COLOR_DANGER);
            y += ROW_H + ROW_GAP;
        }
    }

    private void drawParty(GuiGraphicsExtractor g, Font font, LocalPlayer player, PartySyncPayload s, int mx, int my) {
        // en-tête : nom de la Party, chef, nombre de membres
        String leaderName = "";
        for (PartySyncPayload.Member m : s.members()) {
            if (m.uuid().equals(s.leader())) {
                leaderName = m.name();
            }
        }
        Component name = Component.translatable("party.bloxpiece.default_name", s.name());
        Component info = Component.translatable("screen.bloxpiece.party.info", leaderName, s.members().size(), s.maxMembers());
        g.text(font, fit(font, name, W - font.width(info) - 10), X, TOP + 2, COLOR_GOLD_BRIGHT, true);
        g.text(font, info, X + W - font.width(info), TOP + 2, COLOR_LABEL, true);

        boolean leader = isLeader();
        int y = membersTop();
        for (PartySyncPayload.Member m : s.members()) {
            boolean hovered = inside(mx, my, X, y, W, ROW_H);
            rowBackground(g, X, y, W, hovered);
            drawFace(g, font, m, X + 4, y + 3);
            int tx = X + 4 + FACE + 6;
            int ty = y + (ROW_H - font.lineHeight) / 2 + 1;
            boolean isLeaderRow = m.uuid().equals(s.leader());
            g.text(font, Component.literal(m.name()), tx, ty, m.online() ? 0xFFFFFFFF : COLOR_OFFLINE, false);
            int after = tx + font.width(m.name()) + 6;
            if (isLeaderRow) {
                g.text(font, Component.translatable("screen.bloxpiece.party.leader_tag"), after, ty, COLOR_GOLD_BRIGHT, false);
            }
            Component status = Component.translatable(m.online() ? "screen.bloxpiece.party.online" : "screen.bloxpiece.party.offline");
            int statusRight = X + W - 6 - (leader && !m.uuid().equals(player.getUUID().toString()) ? 2 * SMALL_W + 6 : 0);
            g.fill(statusRight - font.width(status) - 8, y + ROW_H / 2 - 2, statusRight - font.width(status) - 4, y + ROW_H / 2 + 2,
                    m.online() ? COLOR_ONLINE : COLOR_OFFLINE);
            g.text(font, status, statusRight - font.width(status), ty, m.online() ? COLOR_ONLINE : COLOR_OFFLINE, false);
            if (leader && !m.uuid().equals(player.getUUID().toString())) {
                int bx = X + W - 2 * SMALL_W - 4;
                button(g, font, mx, my, bx, y + 3, SMALL_W, Component.translatable("screen.bloxpiece.party.promote"), COLOR_GOLD);
                button(g, font, mx, my, bx + SMALL_W + 2, y + 3, SMALL_W, Component.translatable("screen.bloxpiece.party.kick"), COLOR_DANGER);
            }
            y += ROW_H + ROW_GAP;
        }

        // actions
        int ay = actionsY();
        if (leader) {
            boolean full = s.members().size() >= s.maxMembers();
            button(g, font, mx, my, X, ay, 110, Component.translatable(full ? "screen.bloxpiece.party.full" : "screen.bloxpiece.party.invite"),
                    full ? COLOR_GOLD_DIM : COLOR_GOLD);
            boolean armed = System.currentTimeMillis() - disbandArmedAt < CONFIRM_MS;
            button(g, font, mx, my, X + 116, ay, 90, Component.translatable(armed ? "screen.bloxpiece.party.confirm"
                    : "screen.bloxpiece.party.disband"), COLOR_DANGER);
        }
        button(g, font, mx, my, X + W - 70, ay, 70, Component.translatable("screen.bloxpiece.party.leave"), COLOR_DANGER);

        if (inviting && leader) {
            drawCandidates(g, font, s, mx, my, ay + BTN_H + 8);
        }
    }

    private void drawCandidates(GuiGraphicsExtractor g, Font font, PartySyncPayload s, int mx, int my, int y) {
        g.text(font, Component.translatable("screen.bloxpiece.party.candidates"), X, y, COLOR_GOLD, true);
        Component hint = Component.translatable("screen.bloxpiece.party.invite_hint");
        g.text(font, fit(font, hint, W - 120), X + W - Math.min(font.width(hint), W - 120), y, COLOR_HINT, false);
        y += 12;
        List<PartySyncPayload.Candidate> list = s.candidates();
        int pages = Math.max(1, (list.size() + CANDIDATES_PER_PAGE - 1) / CANDIDATES_PER_PAGE);
        page = Math.min(page, pages - 1);
        if (list.isEmpty()) {
            rowBackground(g, X, y, W, false);
            g.text(font, Component.translatable("screen.bloxpiece.party.no_candidates"), X + 6, y + (ROW_H - font.lineHeight) / 2 + 1,
                    COLOR_HINT, false);
            return;
        }
        for (int i = page * CANDIDATES_PER_PAGE; i < Math.min(list.size(), (page + 1) * CANDIDATES_PER_PAGE); i++) {
            PartySyncPayload.Candidate c = list.get(i);
            boolean hovered = inside(mx, my, X, y, W, ROW_H);
            rowBackground(g, X, y, W, hovered);
            drawFace(g, font, new PartySyncPayload.Member(c.uuid(), c.name(), true), X + 4, y + 3);
            g.text(font, Component.literal(c.name()), X + 4 + FACE + 6, y + (ROW_H - font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);
            button(g, font, mx, my, X + W - SMALL_W - 4, y + 3, SMALL_W, Component.translatable("screen.bloxpiece.party.invite_one"), COLOR_GOLD);
            y += ROW_H + ROW_GAP;
        }
        if (pages > 1) {
            Component label = Component.literal((page + 1) + " / " + pages);
            g.centeredText(font, label, PANEL_W / 2, y + 2, COLOR_LABEL);
            button(g, font, mx, my, PANEL_W / 2 - 50, y, 20, Component.literal("<"), COLOR_GOLD);
            button(g, font, mx, my, PANEL_W / 2 + 30, y, 20, Component.literal(">"), COLOR_GOLD);
        }
    }

    /** Tête du joueur s'il est visible dans le monde (son skin est connu), sinon une pastille avec son initiale. */
    private void drawFace(GuiGraphicsExtractor g, Font font, PartySyncPayload.Member m, int x, int y) {
        Player seen = null;
        if (this.minecraft.level != null) {
            for (Player p : this.minecraft.level.players()) { // joueurs visibles autour (skin connu)
                if (p.getUUID().toString().equals(m.uuid())) {
                    seen = p;
                    break;
                }
            }
        }
        g.fill(x - 1, y - 1, x + FACE + 1, y + FACE + 1, 0xFF000000);
        if (seen instanceof AbstractClientPlayer client) {
            PlayerFaceExtractor.extractRenderState(g, client.getSkin(), x, y, FACE);
        } else {
            g.fillGradient(x, y, x + FACE, y + FACE, 0xFF272C3D, 0xFF0D0E15);
            String initial = m.name().isEmpty() ? "?" : m.name().substring(0, 1).toUpperCase();
            g.centeredText(font, Component.literal(initial), x + FACE / 2, y + (FACE - font.lineHeight) / 2 + 1,
                    m.online() ? COLOR_GOLD : COLOR_OFFLINE);
        }
    }

    private static void rowBackground(GuiGraphicsExtractor g, int x, int y, int w, boolean hovered) {
        g.fill(x, y, x + w, y + ROW_H, hovered ? CharacterInfoRow.COLOR_BACKGROUND_HOVER : CharacterInfoRow.COLOR_BACKGROUND);
        g.fill(x, y, x + 2, y + ROW_H, COLOR_GOLD_DIM);
    }

    /** Bouton dans le style du menu (fond sombre, filet doré, texte clair au survol). */
    private static void button(GuiGraphicsExtractor g, Font font, int mx, int my, int x, int y, int w, Component label, int accent) {
        boolean hovered = inside(mx, my, x, y, w, BTN_H);
        g.fill(x, y, x + w, y + BTN_H, hovered ? CharacterInfoRow.COLOR_BACKGROUND_HOVER : CharacterInfoRow.COLOR_BACKGROUND);
        g.outline(x, y, w, BTN_H, hovered ? COLOR_GOLD_BRIGHT : accent);
        g.centeredText(font, fit(font, label, w - 4), x + w / 2, y + (BTN_H - font.lineHeight) / 2 + 1,
                hovered ? COLOR_GOLD_BRIGHT : COLOR_LABEL);
    }

    // ------------------------------------------------------------------ clics

    private void send(int action, String arg) {
        playClickSound();
        ClientPlayNetworking.send(new PartyActionPayload(action, arg));
    }

    @Override
    protected boolean onPanelClick(int mx, int my) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return false;
        }
        if (inside(mx, my, X, backY(), 62, BTN_H)) {
            playClickSound();
            onClose();
            return true;
        }
        PartySyncPayload s = state();
        if (!s.inParty()) {
            if (inside(mx, my, (PANEL_W - 120) / 2, TOP + 20, 120, BTN_H)) {
                send(PartyActionPayload.CREATE, "");
                return true;
            }
            int y = TOP + 58;
            for (PartySyncPayload.Invite invite : s.invites()) {
                int bx = X + W - 2 * SMALL_W - 6;
                if (inside(mx, my, bx, y + 3, SMALL_W, BTN_H)) {
                    send(PartyActionPayload.ACCEPT, invite.partyId());
                    return true;
                }
                if (inside(mx, my, bx + SMALL_W + 2, y + 3, SMALL_W, BTN_H)) {
                    send(PartyActionPayload.REFUSE, invite.partyId());
                    return true;
                }
                y += ROW_H + ROW_GAP;
            }
            return false;
        }
        boolean leader = isLeader();
        int y = membersTop();
        for (PartySyncPayload.Member m : s.members()) {
            if (leader && !m.uuid().equals(player.getUUID().toString())) {
                int bx = X + W - 2 * SMALL_W - 4;
                if (inside(mx, my, bx, y + 3, SMALL_W, BTN_H)) {
                    send(PartyActionPayload.TRANSFER, m.uuid());
                    return true;
                }
                if (inside(mx, my, bx + SMALL_W + 2, y + 3, SMALL_W, BTN_H)) {
                    send(PartyActionPayload.KICK, m.uuid());
                    return true;
                }
            }
            y += ROW_H + ROW_GAP;
        }
        int ay = actionsY();
        if (leader && inside(mx, my, X, ay, 110, BTN_H)) {
            playClickSound();
            inviting = !inviting;
            page = 0;
            return true;
        }
        if (leader && inside(mx, my, X + 116, ay, 90, BTN_H)) {
            if (System.currentTimeMillis() - disbandArmedAt < CONFIRM_MS) {
                disbandArmedAt = 0L;
                send(PartyActionPayload.DISBAND, "");
            } else {
                playClickSound();
                disbandArmedAt = System.currentTimeMillis(); // premier clic : « Confirmer ? »
            }
            return true;
        }
        if (inside(mx, my, X + W - 70, ay, 70, BTN_H)) {
            send(PartyActionPayload.LEAVE, "");
            return true;
        }
        if (inviting && leader) {
            int cy = ay + BTN_H + 8 + 12;
            List<PartySyncPayload.Candidate> list = s.candidates();
            int pages = Math.max(1, (list.size() + CANDIDATES_PER_PAGE - 1) / CANDIDATES_PER_PAGE);
            int shown = 0;
            for (int i = page * CANDIDATES_PER_PAGE; i < Math.min(list.size(), (page + 1) * CANDIDATES_PER_PAGE); i++) {
                if (inside(mx, my, X + W - SMALL_W - 4, cy + 3, SMALL_W, BTN_H)) {
                    send(PartyActionPayload.INVITE, list.get(i).uuid());
                    return true;
                }
                cy += ROW_H + ROW_GAP;
                shown++;
            }
            if (pages > 1 && shown > 0) {
                if (inside(mx, my, PANEL_W / 2 - 50, cy, 20, BTN_H)) {
                    playClickSound();
                    page = (page + pages - 1) % pages;
                    return true;
                }
                if (inside(mx, my, PANEL_W / 2 + 30, cy, 20, BTN_H)) {
                    playClickSound();
                    page = (page + 1) % pages;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
