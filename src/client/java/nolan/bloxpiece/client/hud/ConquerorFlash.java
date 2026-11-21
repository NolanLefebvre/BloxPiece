package nolan.bloxpiece.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.ConquerorShockwave;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Activation du Haki des Rois, à l'écran : au moment de la décharge (ConquerorShockwave.BUILD_TICKS après l'activation), un flash
 * rouge sombre très bref, puis un vignettage rouge discret sur les bords qui s'efface. Pour tout joueur proche de celui qui
 * l'active (attachment CONQUEROR_RELEASE, synchronisé), plus faible avec la distance ; aussi pour la libération de Gol D.
 * Roger, visible de bien plus loin.
 */
public final class ConquerorFlash implements HudElement {

    private static final float FLASH_TICKS = 4.0F;
    private static final float VIGNETTE_TICKS = 14.0F;
    private static final double RANGE = 24.0;
    private static final double BOSS_RANGE = 48.0;

    private ConquerorFlash() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, BloxPiece.id("conqueror_flash"), new ConquerorFlash());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        float now = mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
        float strength = 0.0F, age = 0.0F;
        // joueurs, et Gol D. Roger (sa libération se voit bien plus loin : BOSS_RANGE)
        java.util.List<net.minecraft.world.entity.LivingEntity> sources = new java.util.ArrayList<>(mc.level.players());
        sources.addAll(mc.level.getEntitiesOfClass(nolan.bloxpiece.mob.GolDRoger.class,
                mc.player.getBoundingBox().inflate(BOSS_RANGE)));
        for (net.minecraft.world.entity.LivingEntity p : sources) {
            Integer at = p.getAttached(ModAttachments.CONQUEROR_RELEASE);
            if (at == null) {
                continue;
            }
            float a = now - at - ConquerorShockwave.BUILD_TICKS;
            double distance = p.distanceTo(mc.player);
            double range = p instanceof Player ? RANGE : BOSS_RANGE;
            if (a < 0.0F || a > VIGNETTE_TICKS || distance > range) {
                continue;
            }
            float s = (float) (1.0 - distance / range);
            if (s > strength) {
                strength = s;
                age = a;
            }
        }
        if (strength <= 0.0F) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        // flash : plein écran, rouge sombre, très bref
        if (age < FLASH_TICKS) {
            float f = (1.0F - age / FLASH_TICKS) * 0.35F * strength;
            g.fill(0, 0, w, h, argb(f, 0x6A0008));
        }
        // vignettage : bandes de plus en plus transparentes vers le centre
        float v = (1.0F - age / VIGNETTE_TICKS) * 0.45F * strength;
        int bands = 8;
        for (int i = 0; i < bands; i++) {
            float alpha = v * (1.0F - i / (float) bands);
            int bx = w * (i + 1) / (bands * 6), by = h * (i + 1) / (bands * 6);
            int px = w * i / (bands * 6), py = h * i / (bands * 6);
            int c = argb(alpha / bands * 2.0F, 0x200002);
            g.fill(px, py, w - px, by, c);          // haut
            g.fill(px, h - by, w - px, h - py, c);  // bas
            g.fill(px, by, bx, h - by, c);          // gauche
            g.fill(w - bx, by, w - px, h - by, c);  // droite
        }
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
