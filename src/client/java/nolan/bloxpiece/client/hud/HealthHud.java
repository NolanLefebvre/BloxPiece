package nolan.bloxpiece.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import nolan.bloxpiece.BloxPiece;

/**
 * Barre de vie à cœurs colorés superposés (style "Colorful Hearts") : remplace la barre de vie et la barre d'armure vanilla.
 *
 * <ul>
 *   <li>Toujours UNE seule rangée de 10 cœurs, à l'emplacement normal : avec beaucoup de PV (niveaux), Minecraft n'empile plus
 *       des rangées de cœurs qui cachent l'écran.</li>
 *   <li>Chaque palier de 20 PV a sa couleur : rouge (0-20), orange (21-40), jaune (41-60), vert, cyan, bleu, violet, rose, blanc,
 *       gris, or, puis on recommence. Les cœurs du palier en cours sont dessinés PAR-DESSUS une rangée pleine de la couleur du
 *       palier précédent : on voit d'un coup d'œil le palier atteint et ce qu'il en reste (demi-cœurs compris).</li>
 *   <li>À gauche des cœurs, le numéro de la barre de vie en cours ("x1", "x2"...), de la même couleur que les cœurs actuels.</li>
 *   <li>Comme en vanilla : cœurs vides pour la vie manquante (sous 20 PV max), cœurs qui tremblent sous 4 PV, contour qui
 *       clignote quand le joueur est touché ; l'absorption s'affiche en "+N" doré à droite des cœurs.</li>
 *   <li>La barre d'armure est toujours juste au-dessus de la rangée de cœurs (vanilla la placerait au-dessus des rangées
 *       empilées, donc très haut).</li>
 * </ul>
 * Rien n'est affiché en créatif ou en spectateur, comme en vanilla.
 */
public final class HealthHud {

    private static final Identifier CONTAINER = BloxPiece.id("textures/gui/hud/heart_container.png");
    private static final Identifier FULL = BloxPiece.id("textures/gui/hud/heart_full.png");
    private static final Identifier HALF = BloxPiece.id("textures/gui/hud/heart_half.png");
    private static final Identifier ARMOR_FULL = BloxPiece.id("textures/gui/hud/armor_full.png");
    private static final Identifier ARMOR_HALF = BloxPiece.id("textures/gui/hud/armor_half.png");
    private static final Identifier ARMOR_EMPTY = BloxPiece.id("textures/gui/hud/armor_empty.png");

    /** Couleur de chaque palier de 20 PV (ARGB), dans l'ordre. Au-delà, on recommence. */
    private static final int[] TIER_COLORS = {
            0xFFFF1E1E, // rouge
            0xFFFF8C1A, // orange
            0xFFFFD21A, // jaune
            0xFF3DDC4A, // vert
            0xFF22D3E6, // cyan
            0xFF3A6CFF, // bleu
            0xFFA24BFF, // violet
            0xFFFF5FD0, // rose
            0xFFF2F2F2, // blanc
            0xFF5E5E5E, // gris
            0xFFFFD25A  // or
    };

    private static final int HP_PER_TIER = 20;
    private static final int HEARTS = 10;
    private static final int ICON = 9;
    private static final int STEP = 8;
    private static final int COLOR_ABSORPTION = 0xFFFFD25A;

    private HealthHud() {
    }

    /** À appeler une fois au démarrage du client. */
    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, vanilla -> HealthHud::drawHearts);
        HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, vanilla -> HealthHud::drawArmor);
    }

    private static LocalPlayer survivalPlayer() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isCreative() || player.isSpectator()) {
            return null;
        }
        return player;
    }

    private static int tierColor(int tier) {
        return TIER_COLORS[Math.floorMod(tier, TIER_COLORS.length)];
    }

    private static void icon(GuiGraphicsExtractor g, Identifier texture, int x, int y, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, ICON, ICON, ICON, ICON, color);
    }

    private static void drawHearts(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        LocalPlayer player = survivalPlayer();
        if (player == null) {
            return;
        }
        int x0 = g.guiWidth() / 2 - 91;
        int y0 = g.guiHeight() - 39;
        int hp = Mth.ceil(player.getHealth());
        int maxHp = Mth.ceil(player.getMaxHealth());
        int containers = Math.min(HEARTS, Math.max(1, (maxHp + 1) / 2));
        boolean shaking = hp <= 4;
        boolean flash = player.hurtTime > 0 && (player.hurtTime / 3) % 2 == 1;

        int[] dy = new int[HEARTS];
        for (int i = 0; i < HEARTS; i++) {
            dy[i] = shaking && ((player.tickCount * 7 + i * 13) % 5 == 0) ? -1 : 0; // petit tremblement à faible vie
        }

        // Cœurs vides (contour blanc quand le joueur vient d'être touché)
        for (int i = 0; i < containers; i++) {
            icon(g, CONTAINER, x0 + i * STEP, y0 + dy[i], 0xFFFFFFFF);
            if (flash) {
                g.outline(x0 + i * STEP, y0 + dy[i], ICON, ICON - 1, 0x80FFFFFF);
            }
        }

        if (hp > 0) {
            int tier = (hp - 1) / HP_PER_TIER;           // palier en cours (0 = 1 à 20 PV)
            int inTier = hp - tier * HP_PER_TIER;        // PV dans ce palier (1 à 20)
            if (tier > 0) {
                // Rangée pleine du palier précédent, en dessous
                for (int i = 0; i < HEARTS; i++) {
                    icon(g, FULL, x0 + i * STEP, y0 + dy[i], tierColor(tier - 1));
                }
            }
            int color = tierColor(tier);
            int full = inTier / 2;
            for (int i = 0; i < full; i++) {
                icon(g, FULL, x0 + i * STEP, y0 + dy[i], color);
            }
            if (inTier % 2 == 1) {
                icon(g, HALF, x0 + full * STEP, y0 + dy[full], color);
            }

            // Compteur de la barre de vie en cours ("x1" de 1 à 20 PV, "x2" de 21 à 40...), à gauche des cœurs, aligné sur la
            // rangée, de la couleur des cœurs actuels et avec une ombre portée. Jamais le maximum : seulement la barre active.
            String counter = "x" + (tier + 1);
            var font = Minecraft.getInstance().font;
            g.text(font, counter, x0 - 2 - font.width(counter), y0 + 1, color, true);
        }

        // Absorption (pommes dorées...) : "+N" doré à droite de la rangée
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        if (absorption > 0) {
            g.text(Minecraft.getInstance().font, "+" + absorption, x0 + HEARTS * STEP + 2, y0 + 1, COLOR_ABSORPTION, true);
        }
    }

    private static void drawArmor(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        LocalPlayer player = survivalPlayer();
        if (player == null) {
            return;
        }
        int armor = player.getArmorValue();
        if (armor <= 0) {
            return;
        }
        int x0 = g.guiWidth() / 2 - 91;
        int y0 = g.guiHeight() - 39 - 10; // toujours juste au-dessus de l'unique rangée de cœurs
        for (int i = 0; i < HEARTS; i++) {
            Identifier texture = i * 2 + 2 <= armor ? ARMOR_FULL : i * 2 + 1 == armor ? ARMOR_HALF : ARMOR_EMPTY;
            icon(g, texture, x0 + i * STEP, y0, 0xFFFFFFFF);
        }
    }
}
