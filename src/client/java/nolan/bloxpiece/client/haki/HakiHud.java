package nolan.bloxpiece.client.haki;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.client.BloxPieceClient;
import nolan.bloxpiece.client.hud.ClientCooldowns;
import nolan.bloxpiece.client.hud.HudLabel;
import nolan.bloxpiece.haki.ArmamentHaki;
import nolan.bloxpiece.haki.HakiEnergy;
import nolan.bloxpiece.haki.HakiManager;
import nolan.bloxpiece.haki.HakiType;
import nolan.bloxpiece.haki.Unconscious;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * HUD des Hakis : 3 cases à GAUCHE de la hotbar (Armement, Observation, Rois), même style et même logique que les cases des
 * attaques de fruit à droite : icône, touche en bas à droite, voile et secondes pendant la recharge. Un Haki actif est éclairé
 * (cadre doré qui pulse, voile de sa couleur). Si la place manque, les cases passent sur plusieurs rangées vers le haut.
 * Aussi : voile sombre et « Inconscient » quand le joueur est évanoui par un Haki des Rois.
 * Icônes : assets/bloxpiece/textures/gui/haki/&lt;id&gt;.png (carrées, toute taille : affichées dans la case de 16 px).
 */
public final class HakiHud implements HudElement {

    private static final int SLOT_SIZE = 18;
    private static final int ICON_SIZE = 16;
    private static final int GAP = 2;
    private static final int DISTANCE_FROM_HOTBAR = 4;
    private static final int SCREEN_MARGIN = 2;
    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int OFFHAND_WIDTH = 29;
    private static final int ATTACK_INDICATOR_SPACE = 24;

    private static final int COLOR_SLOT_BACKGROUND = 0x90000000;
    private static final int COLOR_BORDER_READY = 0xFFB4B4B4;
    private static final int COLOR_BORDER_COOLDOWN = 0xFF5A5A5A;
    private static final int COLOR_COOLDOWN_VEIL = 0xA0000000;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    /** Teinte d'un Haki actif : Armement violet sombre, Observation cyan, Rois rouge. */
    /** Titre « Haki » : ivoire chaud, traits dorés discrets (comme le nom du fruit de l'autre côté). */
    private static final int COLOR_LABEL = 0xFFF3E6C4;
    private static final int COLOR_LABEL_ACCENT = 0xB0D9A441;

    // jauge : 3 px de haut, 3 px au-dessus des cases (sous le titre)
    private static final int BAR_HEIGHT = 3;
    private static final int BAR_OFFSET = 6;
    private static final float LOW = 0.2F;
    private static final int COLOR_BAR_FRAME = 0xC0000000;
    private static final int COLOR_BAR_EMPTY = 0x8026222E;
    private static final int COLOR_BAR = 0xFF7B4FD6;        // violet profond du Haki
    private static final int COLOR_BAR_LIGHT = 0xFFB794FF;
    private static final int COLOR_BAR_GHOST = 0xB0E8DDFF;
    private static final int COLOR_BAR_LOW = 0xFFB8323C;
    private static final int COLOR_BAR_LOW_LIGHT = 0xFFFF7A80;
    private static final int COLOR_BAR_TICK = 0x60000000;

    private static final int[] ACTIVE_TINT = {0x553A1660, 0x4430C8E0, 0x55D01818};

    private HakiHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, BloxPiece.id("haki_slots"), new HakiHud());
    }

    private static Identifier icon(HakiType type) {
        return BloxPiece.id("textures/gui/haki/" + type.id + ".png");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.screen() != null) {
            return;
        }
        if (Unconscious.is(player)) {
            drawUnconscious(g, mc, player);
        }
        // rien du tout tant qu'aucun Haki n'est débloqué ; dès le premier, les trois cases s'affichent (cadenas sur les autres)
        boolean any = false;
        for (HakiType type : HakiType.values()) {
            any |= HakiManager.isUnlocked(player, type);
        }
        if (!any) {
            return;
        }
        HakiType[] types = HakiType.values();
        int rightEdge = lastSlotRight(mc, player, g.guiWidth());
        int fit = Mth.clamp((rightEdge - SCREEN_MARGIN + GAP) / (SLOT_SIZE + GAP), 1, types.length);
        int rows = (types.length + fit - 1) / fit;
        int columns = (types.length + rows - 1) / rows;
        int totalWidth = columns * SLOT_SIZE + (columns - 1) * GAP;
        int x0 = Math.max(SCREEN_MARGIN, rightEdge - totalWidth);
        int bottomY = g.guiHeight() - 20;
        long now = mc.level.getGameTime();
        for (int i = 0; i < types.length; i++) {
            // la case la plus proche de la hotbar est la dernière de la rangée : Armement le plus à gauche
            int x = x0 + (i % columns) * (SLOT_SIZE + GAP);
            int y = bottomY - (i / columns) * (SLOT_SIZE + GAP);
            drawSlot(g, mc.font, player, types[i], x, y, now);
        }

        // jauge de Haki juste au-dessus des cases, titre « Haki » au-dessus de la jauge
        int slotsTop = bottomY - (rows - 1) * (SLOT_SIZE + GAP);
        boolean anyActive = false;
        for (HakiType type : types) {
            anyActive |= isActive(player, type);
        }
        drawEnergyBar(g, player, x0, slotsTop - BAR_OFFSET, totalWidth, anyActive, now);
        HudLabel.draw(g, mc.font, Component.translatable("hud.bloxpiece.haki.label").getString(), x0, totalWidth, slotsTop,
                SCREEN_MARGIN, rightEdge, COLOR_LABEL, COLOR_LABEL_ACCENT);
    }

    // ---- Jauge de Haki

    /** Valeur affichée (suit la vraie valeur en douceur) et « fantôme » clair qui montre ce qui vient d'être consommé. */
    private static float shown = -1.0F;
    private static float ghost = -1.0F;
    private static long lastFrame;

    private static void drawEnergyBar(GuiGraphicsExtractor g, LocalPlayer player, int x, int y, int w, boolean draining, long now) {
        Integer value = player.getAttached(ModAttachments.HAKI_ENERGY);
        float target = (value == null ? HakiEnergy.MAX : value) / (float) HakiEnergy.MAX;
        target = Mth.clamp(target, 0.0F, 1.0F);
        long t = System.nanoTime();
        float dt = lastFrame == 0 ? 0.0F : Math.min(0.25F, (t - lastFrame) / 1.0E9F);
        lastFrame = t;
        if (shown < 0.0F) {
            shown = target;
            ghost = target;
        }
        shown += (target - shown) * Math.min(1.0F, dt * 14.0F);
        if (ghost < shown) {
            ghost = shown;
        } else {
            ghost = Math.max(shown, ghost - dt * 0.35F); // le fantôme rattrape la jauge avec un léger retard
        }

        // cadre : fond sombre, liseré
        g.fill(x - 1, y - 1, x + w + 1, y + BAR_HEIGHT + 1, COLOR_BAR_FRAME);
        g.fill(x, y, x + w, y + BAR_HEIGHT, COLOR_BAR_EMPTY);
        int fillW = Math.round(w * shown);
        int ghostW = Math.round(w * ghost);
        if (ghostW > fillW) {
            g.fill(x + fillW, y, x + ghostW, y + BAR_HEIGHT, COLOR_BAR_GHOST);
        }
        if (fillW > 0) {
            boolean low = shown < LOW;
            int main = COLOR_BAR;
            int light = COLOR_BAR_LIGHT;
            if (low) {
                // presque vide : la jauge rougit et pulse
                float pulse = 0.5F + 0.5F * Mth.sin((now % 16) / 16.0F * ((float) Math.PI * 2.0F));
                main = lerpColor(COLOR_BAR_LOW, COLOR_BAR_LOW_LIGHT, pulse * 0.6F);
                light = COLOR_BAR_LOW_LIGHT;
            }
            g.fill(x, y, x + fillW, y + BAR_HEIGHT, main);
            g.fill(x, y, x + fillW, y + 1, light); // reflet en haut
            if (draining && fillW > 2) {
                // en cours de consommation : un éclat qui parcourt la jauge
                int sweep = (int) ((now % 30) / 30.0F * (fillW + 6)) - 3;
                for (int i = 0; i < 3; i++) {
                    int sx = sweep + i;
                    if (sx >= 0 && sx < fillW) {
                        g.fill(x + sx, y, x + sx + 1, y + BAR_HEIGHT, 0x50FFFFFF);
                    }
                }
            }
        }
        // repères à 25 / 50 / 75 %
        for (int q = 1; q < 4; q++) {
            int mx = x + w * q / 4;
            g.fill(mx, y + 1, mx + 1, y + BAR_HEIGHT, COLOR_BAR_TICK);
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gr = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }

    /** Reprend la jauge à zéro (changement de monde). */
    public static void reset() {
        shown = -1.0F;
        ghost = -1.0F;
        lastFrame = 0L;
    }

    /** Bord droit de la dernière case : à gauche de la hotbar, en évitant la main secondaire et l'indicateur d'attaque. */
    private static int lastSlotRight(Minecraft mc, LocalPlayer player, int guiWidth) {
        int x = guiWidth / 2 - HOTBAR_HALF_WIDTH - DISTANCE_FROM_HOTBAR;
        if (player.getMainArm() == HumanoidArm.RIGHT) {
            if (!player.getOffhandItem().isEmpty()) {
                x -= OFFHAND_WIDTH; // droitier : la case de la main secondaire est à gauche de la hotbar
            }
        } else if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            x -= ATTACK_INDICATOR_SPACE;
        }
        return x;
    }

    private static boolean isActive(LocalPlayer player, HakiType type) {
        return switch (type) {
            case ARMAMENT -> ArmamentHaki.mode(player) != 0;
            case OBSERVATION -> Boolean.TRUE.equals(player.getAttached(ModAttachments.HAKI_OBSERVATION));
            case CONQUEROR -> Boolean.TRUE.equals(player.getAttached(ModAttachments.HAKI_CONQUEROR));
        };
    }

    private static void drawSlot(GuiGraphicsExtractor g, Font font, LocalPlayer player, HakiType type, int x, int y, long now) {
        g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, COLOR_SLOT_BACKGROUND);
        boolean unlocked = HakiManager.isUnlocked(player, type);
        boolean active = unlocked && isActive(player, type);
        int remaining = active ? 0 : ClientCooldowns.remainingTicks(type.cooldownKey(), now);
        boolean cooling = remaining > 0;
        int alpha = !unlocked ? 0x50 : cooling ? 0x70 : 0xD0;
        g.blit(RenderPipelines.GUI_TEXTURED, icon(type), x + 1, y + 1, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE,
                (alpha << 24) | 0xFFFFFF);
        if (!unlocked) {
            // Haki pas encore débloqué : cadenas par-dessus l'icône atténuée (comme les attaques verrouillées du fruit)
            g.blit(RenderPipelines.GUI_TEXTURED, nolan.bloxpiece.client.hud.AbilityHud.LOCK_ICON, x + 1, y + 1, 0.0F, 0.0F,
                    ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, 0xFFFFFFFF);
            g.outline(x, y, SLOT_SIZE, SLOT_SIZE, COLOR_BORDER_COOLDOWN);
            return;
        }
        if (active) {
            g.fill(x + 1, y + 1, x + 1 + ICON_SIZE, y + 1 + ICON_SIZE, ACTIVE_TINT[type.ordinal()]);
            float pulse = 0.5F + 0.5F * Mth.sin((now % 40) / 40.0F * ((float) Math.PI * 2.0F));
            int c = (int) (0xA0 + 0x5F * pulse);
            g.outline(x, y, SLOT_SIZE, SLOT_SIZE, 0xFF000000 | (c << 16) | ((int) (c * 0.82F) << 8) | 0x30);
            g.outline(x - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, ((int) (0x60 * pulse) << 24) | 0xFFD25A);
        } else if (cooling) {
            int total = ClientCooldowns.totalTicks(type.cooldownKey());
            int veil = Math.min(ICON_SIZE, (int) Math.ceil(ICON_SIZE * (double) remaining / total));
            g.fill(x + 1, y + 1 + ICON_SIZE - veil, x + 1 + ICON_SIZE, y + 1 + ICON_SIZE, COLOR_COOLDOWN_VEIL);
            g.outline(x, y, SLOT_SIZE, SLOT_SIZE, COLOR_BORDER_COOLDOWN);
            String text = Integer.toString((remaining + 19) / 20);
            g.text(font, text, x + (SLOT_SIZE - font.width(text)) / 2, y + (SLOT_SIZE - font.lineHeight) / 2 + 1, COLOR_TEXT, true);
            return;
        } else {
            g.outline(x, y, SLOT_SIZE, SLOT_SIZE, unlocked ? COLOR_BORDER_READY : COLOR_BORDER_COOLDOWN);
        }
        KeyMapping key = BloxPieceClient.hakiKey(type.slot());
        if (key != null && unlocked) {
            String label = font.plainSubstrByWidth(key.getTranslatedKeyMessage().getString(), SLOT_SIZE - 4);
            g.text(font, label, x + SLOT_SIZE - 1 - font.width(label), y + SLOT_SIZE - font.lineHeight, COLOR_TEXT, true);
        }
    }

    /** Évanoui : écran assombri sur les bords et « Inconscient (5 s) » au centre. */
    private static void drawUnconscious(GuiGraphicsExtractor g, Minecraft mc, LocalPlayer player) {
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, 0x70000000);
        Long until = player.getAttached(ModAttachments.UNCONSCIOUS_UNTIL);
        long left = until == null ? 0 : Math.max(0, until - mc.level.getGameTime());
        Component text = Component.translatable("hud.bloxpiece.haki.unconscious", (left + 19) / 20);
        g.centeredText(mc.font, text, w / 2, h / 2 - 20, 0xFFE06B6B);
    }
}
