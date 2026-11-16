package nolan.bloxpiece.client.hud;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.ability.Ability;
import nolan.bloxpiece.ability.AbilityManager;
import nolan.bloxpiece.client.BloxPieceClient;
import nolan.bloxpiece.fruit.DevilFruit;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * HUD des attaques : 4 cases à droite de la hotbar (Z, X, C, V par défaut).
 * - aucun fruit : les 4 cases restent vides
 * - un fruit : chaque case affiche l'icône de l'attaque (fond semi-transparent) et sa touche en bas à droite
 * - attaque en recharge : icône assombrie + temps restant en secondes (à la place de la touche), qui disparaît à la fin
 * - attaque verrouillée (niveau du joueur trop bas) : un cadenas remplace l'affichage habituel ; il disparaît tout seul
 *   dès que le joueur atteint le niveau requis
 *
 * Tout est LU, rien n'est recalculé : le fruit vient de l'attachment du joueur (synchronisé par le serveur),
 * les attaques viennent de DevilFruit, et les recharges de ClientCooldowns (alimenté par AbilityManager).
 *
 * Disposition : en ligne à droite de la hotbar. Si la place manque (grand GUI scale, petite fenêtre, main secondaire
 * ou indicateur d'attaque affichés), les cases passent sur plusieurs rangées au-dessus les unes des autres
 * au lieu de se superposer à la hotbar.
 *
 * Icônes : assets/bloxpiece/textures/gui/ability/<id du fruit>/<id de l'attaque>.png (16x16).
 */
public class AbilityHud implements HudElement {

    // ---- Dimensions (en pixels GUI) : modifie ici pour changer la taille / l'espacement
    private static final int SLOT_SIZE = 18;   // case = 16 px d'icône + 1 px de cadre de chaque côté
    private static final int ICON_SIZE = 16;
    private static final int GAP = 2;          // espace entre deux cases
    private static final int DISTANCE_FROM_HOTBAR = 4;
    private static final int SCREEN_MARGIN = 2;   // marge minimale avec le bord droit de l'écran

    // Valeurs de la hotbar vanilla, pour ne pas chevaucher ses éléments annexes
    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int OFFHAND_WIDTH = 29;            // case de la main secondaire (côté droit si gaucher)
    private static final int ATTACK_INDICATOR_SPACE = 24;   // indicateur d'attaque affiché dans la hotbar

    // ---- Couleurs (ARGB)
    private static final int COLOR_SLOT_BACKGROUND = 0x90000000;
    private static final int COLOR_BORDER_READY = 0xFFB4B4B4;
    private static final int COLOR_BORDER_COOLDOWN = 0xFF5A5A5A;
    private static final int COLOR_BORDER_EMPTY = 0xFF4A4A4A;
    private static final int COLOR_COOLDOWN_VEIL = 0xA0000000;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    // Transparence de l'icône (0-255) : l'icône sert de fond, le chiffre reste lisible par-dessus
    private static final int ICON_ALPHA_READY = 0xB4;
    private static final int ICON_ALPHA_COOLDOWN = 0x70;
    private static final int ICON_ALPHA_LOCKED = 0x50;   // icône d'une attaque verrouillée, derrière le cadenas

    /** Goutte d'eau barrée : attaques bloquées parce que le joueur est dans l'eau. */
    private static final Identifier WATER_ICON = BloxPiece.id("textures/gui/water_blocked.png");
    private static final int COLOR_WATER_VEIL = 0x803A7BD5;   // voile bleu sur l'icône
    private static final int COLOR_BORDER_WATER = 0xFF3A7BD5;

    /** Cadenas des attaques verrouillées (aussi utilisé par la page des attaques du menu personnage). */
    public static final Identifier LOCK_ICON = BloxPiece.id("textures/gui/lock.png");

    private static final Map<String, Identifier> ICONS = new HashMap<>();

    /** À appeler une fois au démarrage du client : le HUD est dessiné juste après la hotbar. */
    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, BloxPiece.id("ability_slots"), new AbilityHud());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        // Visible uniquement en jeu : jamais par-dessus un menu ou une interface ouverte.
        // (F1 et le mode spectateur sont déjà gérés par la hotbar vanilla, dont on dépend.)
        if (player == null || level == null || mc.gui.screen() != null) {
            return;
        }

        DevilFruit fruit = Fruits.get(player.getAttached(ModAttachments.FRUIT));
        long now = level.getGameTime();

        // Disposition : une seule rangée si elle tient à droite de la hotbar, sinon plusieurs rangées vers le haut
        int guiWidth = graphics.guiWidth();
        int idealX = firstSlotX(mc, player, guiWidth);
        int fit = Mth.clamp((guiWidth - idealX - SCREEN_MARGIN + GAP) / (SLOT_SIZE + GAP), 1, DevilFruit.MAX_ABILITIES);
        int rows = (DevilFruit.MAX_ABILITIES + fit - 1) / fit;
        int columns = (DevilFruit.MAX_ABILITIES + rows - 1) / rows;   // répartit équitablement (4 -> 2 x 2 plutôt que 3 + 1)
        int totalWidth = columns * SLOT_SIZE + (columns - 1) * GAP;
        int x0 = Math.min(idealX, guiWidth - totalWidth - SCREEN_MARGIN);
        int bottomY = graphics.guiHeight() - 20; // centré verticalement sur la hotbar (22 px de haut)

        for (int slot = 0; slot < DevilFruit.MAX_ABILITIES; slot++) {
            Ability ability = fruit == null ? null : fruit.getAbility(slot);
            int x = x0 + (slot % columns) * (SLOT_SIZE + GAP);
            int y = bottomY - (slot / columns) * (SLOT_SIZE + GAP);
            drawSlot(graphics, mc.font, player, x, y, fruit, ability, now, keyLabel(mc.font, slot));
        }
    }

    /** Nom de la touche de l'attaque de ce slot (ex : "Z"), raccourci pour tenir dans la case. */
    private static String keyLabel(Font font, int slot) {
        KeyMapping key = BloxPieceClient.abilityKey(slot);
        if (key == null) {
            return "";
        }
        return font.plainSubstrByWidth(key.getTranslatedKeyMessage().getString(), SLOT_SIZE - 4);
    }

    /** Position X idéale de la première case : à droite de la hotbar, en évitant la main secondaire et l'indicateur d'attaque. */
    private static int firstSlotX(Minecraft mc, LocalPlayer player, int guiWidth) {
        int x = guiWidth / 2 + HOTBAR_HALF_WIDTH + DISTANCE_FROM_HOTBAR;

        if (player.getMainArm() == HumanoidArm.LEFT) {
            x += OFFHAND_WIDTH; // gaucher : la case de la main secondaire passe à droite de la hotbar
        } else if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            x += ATTACK_INDICATOR_SPACE;
        }

        return x;
    }

    private static void drawSlot(GuiGraphicsExtractor g, Font font, LocalPlayer player, int x, int y,
                                 DevilFruit fruit, Ability ability, long now, String keyLabel) {
        g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, COLOR_SLOT_BACKGROUND);

        // Pas de fruit, ou pas d'attaque sur cette touche : case vide
        if (fruit == null || ability == null) {
            g.outline(x, y, SLOT_SIZE, SLOT_SIZE, COLOR_BORDER_EMPTY);
            return;
        }

        String key = AbilityManager.cooldownKey(fruit, ability);

        // Attaque verrouillée : icône très atténuée en fond, cadenas par-dessus (comme dans la page des attaques).
        // Le serveur refuse de toute façon de la lancer.
        if (!ability.isUnlockedFor(player)) {
            g.blit(RenderPipelines.GUI_TEXTURED, iconTexture(key, fruit, ability), x + 1, y + 1,
                    0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, (ICON_ALPHA_LOCKED << 24) | 0xFFFFFF);
            g.blit(RenderPipelines.GUI_TEXTURED, LOCK_ICON, x + 1, y + 1,
                    0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, 0xFFFFFFFF);
            g.outline(x, y, SLOT_SIZE, SLOT_SIZE, COLOR_BORDER_COOLDOWN);
            return;
        }

        // Dans l'eau (pieds compris) : tous les pouvoirs sont bloqués par le serveur. Icône atténuée, voile bleu, goutte barrée.
        if (player.isInWater()) {
            g.blit(RenderPipelines.GUI_TEXTURED, iconTexture(key, fruit, ability), x + 1, y + 1,
                    0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, (ICON_ALPHA_LOCKED << 24) | 0xFFFFFF);
            g.fill(x + 1, y + 1, x + 1 + ICON_SIZE, y + 1 + ICON_SIZE, COLOR_WATER_VEIL);
            g.blit(RenderPipelines.GUI_TEXTURED, WATER_ICON, x + 1, y + 1,
                    0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, 0xFFFFFFFF);
            g.outline(x, y, SLOT_SIZE, SLOT_SIZE, COLOR_BORDER_WATER);
            return;
        }

        int remaining = ClientCooldowns.remainingTicks(key, now);
        boolean cooling = remaining > 0;

        // Icône de l'attaque en arrière-plan, avec transparence
        int alpha = cooling ? ICON_ALPHA_COOLDOWN : ICON_ALPHA_READY;
        g.blit(RenderPipelines.GUI_TEXTURED, iconTexture(key, fruit, ability), x + 1, y + 1,
                0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, (alpha << 24) | 0xFFFFFF);

        if (cooling) {
            // Voile sombre qui descend à mesure que la recharge avance (comme le cooldown des items vanilla)
            int total = Math.max(ability.cooldownTicks(), 1);
            int veilHeight = Math.min(ICON_SIZE, (int) Math.ceil(ICON_SIZE * (double) remaining / total));
            g.fill(x + 1, y + 1 + ICON_SIZE - veilHeight, x + 1 + ICON_SIZE, y + 1 + ICON_SIZE, COLOR_COOLDOWN_VEIL);
        }

        g.outline(x, y, SLOT_SIZE, SLOT_SIZE, cooling ? COLOR_BORDER_COOLDOWN : COLOR_BORDER_READY);

        if (!cooling && !keyLabel.isEmpty()) {
            // Attaque prête : sa touche en bas à droite de la case (comme le nombre d'items vanilla)
            g.text(font, keyLabel, x + SLOT_SIZE - 1 - font.width(keyLabel), y + SLOT_SIZE - font.lineHeight, COLOR_TEXT, true);
        }

        if (cooling) {
            // Secondes restantes (arrondi au supérieur, comme le message de recharge du serveur), par-dessus tout
            String text = Integer.toString((remaining + 19) / 20);
            int textX = x + (SLOT_SIZE - font.width(text)) / 2;
            int textY = y + (SLOT_SIZE - font.lineHeight) / 2 + 1;
            g.text(font, text, textX, textY, COLOR_TEXT, true);
        }
    }

    /** Icône d'une attaque (aussi utilisée par la page des attaques du menu personnage). */
    public static Identifier iconTexture(String key, DevilFruit fruit, Ability ability) {
        return ICONS.computeIfAbsent(key, k ->
                BloxPiece.id("textures/gui/ability/" + fruit.id() + "/" + ability.id() + ".png"));
    }
}
