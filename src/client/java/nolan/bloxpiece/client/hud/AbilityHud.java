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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.ability.Ability;
import nolan.bloxpiece.ability.AbilityManager;
import nolan.bloxpiece.fruit.DevilFruit;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * HUD des attaques : 3 cases à droite de la hotbar.
 * - aucun fruit : les 3 cases restent vides
 * - un fruit : chaque case affiche l'icône de l'attaque (fond semi-transparent)
 * - attaque en recharge : icône assombrie + temps restant en secondes, qui disparaît à la fin
 *
 * Tout est LU, rien n'est recalculé : le fruit vient de l'attachment du joueur (synchronisé par le serveur),
 * les attaques viennent de DevilFruit, et les recharges de ClientCooldowns (alimenté par AbilityManager).
 *
 * Icônes : assets/bloxpiece/textures/gui/ability/<id du fruit>/<id de l'attaque>.png (16x16).
 */
public class AbilityHud implements HudElement {

    // ---- Dimensions (en pixels GUI) : modifie ici pour changer la taille / l'espacement
    private static final int SLOT_SIZE = 18;   // case = 16 px d'icône + 1 px de cadre de chaque côté
    private static final int ICON_SIZE = 16;
    private static final int GAP = 2;          // espace entre deux cases
    private static final int DISTANCE_FROM_HOTBAR = 4;

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

        int x = firstSlotX(mc, player, graphics.guiWidth());
        int y = graphics.guiHeight() - 20; // centré verticalement sur la hotbar (22 px de haut)

        for (int slot = 0; slot < DevilFruit.MAX_ABILITIES; slot++) {
            Ability ability = fruit == null ? null : fruit.getAbility(slot);
            drawSlot(graphics, mc.font, x, y, fruit, ability, now);
            x += SLOT_SIZE + GAP;
        }
    }

    /** Position X de la première case : à droite de la hotbar, en évitant la main secondaire et l'indicateur d'attaque. */
    private static int firstSlotX(Minecraft mc, LocalPlayer player, int guiWidth) {
        int x = guiWidth / 2 + HOTBAR_HALF_WIDTH + DISTANCE_FROM_HOTBAR;

        if (player.getMainArm() == HumanoidArm.LEFT) {
            x += OFFHAND_WIDTH; // gaucher : la case de la main secondaire passe à droite de la hotbar
        } else if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            x += ATTACK_INDICATOR_SPACE;
        }

        // Petit écran / grand GUI scale : on reste dans l'écran
        int totalWidth = DevilFruit.MAX_ABILITIES * SLOT_SIZE + (DevilFruit.MAX_ABILITIES - 1) * GAP;
        return Math.min(x, guiWidth - totalWidth - 2);
    }

    private static void drawSlot(GuiGraphicsExtractor g, Font font, int x, int y,
                                 DevilFruit fruit, Ability ability, long now) {
        g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, COLOR_SLOT_BACKGROUND);

        // Pas de fruit, ou pas d'attaque sur cette touche : case vide
        if (fruit == null || ability == null) {
            g.outline(x, y, SLOT_SIZE, SLOT_SIZE, COLOR_BORDER_EMPTY);
            return;
        }

        String key = AbilityManager.cooldownKey(fruit, ability);
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

        if (cooling) {
            // Secondes restantes (arrondi au supérieur, comme le message de recharge du serveur), par-dessus tout
            String text = Integer.toString((remaining + 19) / 20);
            int textX = x + (SLOT_SIZE - font.width(text)) / 2;
            int textY = y + (SLOT_SIZE - font.lineHeight) / 2 + 1;
            g.text(font, text, textX, textY, COLOR_TEXT, true);
        }
    }

    private static Identifier iconTexture(String key, DevilFruit fruit, Ability ability) {
        return ICONS.computeIfAbsent(key, k ->
                BloxPiece.id("textures/gui/ability/" + fruit.id() + "/" + ability.id() + ".png"));
    }
}
