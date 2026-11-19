package nolan.bloxpiece.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.job.Jobs;
import nolan.bloxpiece.progression.Progression;

/**
 * Bannière en haut de l'écran (style « toast » de Minecraft) quand le joueur atteint le niveau {@link Jobs#REQUIRED_LEVEL} sans
 * job : elle glisse depuis le haut, reste quelques secondes puis remonte, avec le son des défis réussis. Détection côté client,
 * au passage du niveau (pas à la connexion d'un joueur déjà au-dessus).
 */
public final class JobUnlockBanner implements HudElement {

    private static final Identifier ICON = BloxPiece.id("textures/gui/character/job.png");
    private static final long SHOW_MS = 6000L;
    private static final long SLIDE_MS = 350L;
    private static final int H = 32;

    private static int lastLevel = -1;
    private static long shownAt = -1L;

    private JobUnlockBanner() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, BloxPiece.id("job_unlock_banner"), new JobUnlockBanner());
    }

    /** Chaque tick client : déclenche la bannière au passage du niveau requis. */
    public static void tick(Minecraft client) {
        if (client.player == null) {
            lastLevel = -1;
            return;
        }
        int level = Progression.get(client.player).level();
        if (lastLevel >= 0 && lastLevel < Jobs.REQUIRED_LEVEL && level >= Jobs.REQUIRED_LEVEL && Jobs.get(client.player) == null) {
            shownAt = System.currentTimeMillis();
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
        }
        lastLevel = level;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        if (shownAt < 0L) {
            return;
        }
        long age = System.currentTimeMillis() - shownAt;
        if (age > SHOW_MS) {
            shownAt = -1L;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float in = Math.min(1.0F, age / (float) SLIDE_MS);
        float out = Math.min(1.0F, (SHOW_MS - age) / (float) SLIDE_MS);
        float slide = Math.min(easeOut(in), easeOut(out));
        Component title = Component.translatable("toast.bloxpiece.job.title");
        Component text = Component.translatable("toast.bloxpiece.job.text");
        // largeur adaptée au texte (jamais de débordement), sans dépasser l'écran
        int W = Math.min(g.guiWidth() - 8, 32 + Math.max(font.width(title), font.width(text)) + 10);
        int x = (g.guiWidth() - W) / 2;
        int y = Math.round(-H + (H + 6) * slide);

        // fond sombre, cadre doré, coins plus clairs (même charte que les menus du mod)
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0x88000000);
        g.fill(x, y, x + W, y + H, 0xF2101018);
        g.outline(x, y, W, H, 0xFFC8A24A);
        g.fill(x, y, x + 6, y + 1, 0xFFFFD25A);
        g.fill(x + W - 6, y, x + W, y + 1, 0xFFFFD25A);
        g.fill(x + 6, y + 6, x + 26, y + 26, 0xFF07070C);
        g.outline(x + 6, y + 6, 20, 20, 0xFFC8A24A);
        g.blit(RenderPipelines.GUI_TEXTURED, ICON, x + 8, y + 8, 0.0F, 0.0F, 16, 16, 16, 16, 0xFFFFFFFF);
        int textW = W - 32 - 6;
        g.text(font, nolan.bloxpiece.client.hud.JobUnlockBanner.fit(font, title, textW), x + 32, y + 7, 0xFFFFD25A, true);
        g.text(font, nolan.bloxpiece.client.hud.JobUnlockBanner.fit(font, text, textW), x + 32, y + 18, 0xFFD8D8E0, false);
    }

    /** Texte raccourci avec « … » s'il ne tient pas (très petit écran). */
    static Component fit(Font font, Component text, int max) {
        if (font.width(text) <= max) {
            return text;
        }
        return Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(0, max - font.width("..."))) + "...");
    }

    private static float easeOut(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t);
    }
}
