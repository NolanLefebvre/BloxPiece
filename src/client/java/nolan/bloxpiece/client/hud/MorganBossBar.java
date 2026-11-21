package nolan.bloxpiece.client.hud;

import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.mob.Morgan;

/**
 * Barre de boss de Morgan « main de hache », en haut de l'écran : même système et même style que celle de Gol D. Roger
 * (RogerBossBar : une rangée de {@link #CELLS} carrés de vie qui passe derrière un grand emblème central, décor aux deux bouts,
 * nom en lettres pixel), à son thème (textures/gui/morgan_bossbar*.png, générées par src/blockbench/ui/make_morgan_bossbar.py) :
 * tube d'acier riveté, portrait avec sa mâchoire d'acier à créneaux devant deux haches croisées, pans de son manteau bleu clair
 * doublé de magenta, épaulettes dorées, « Capitaine » / « Morgan » couleur acier, carrés magenta.
 * <ul>
 *   <li>Vie : la vraie vie de l'entité (getHealth / getMaxHealth), que Minecraft envoie déjà à chaque client : même valeur
 *       pour tous les joueurs, toujours à jour (dégâts et soins), aucun paquet en plus.</li>
 *   <li>Zone : la barre apparaît à moins de {@link #SHOW_RADIUS} blocs de Morgan, disparaît au-delà de {@link #HIDE_RADIUS}
 *       (marge : pas de clignotement) ; plusieurs Morgan : seulement le plus proche. À sa mort, elle se vide puis s'efface.</li>
 *   <li>Mêmes animations que celle de Roger (fondu, tremblement au coup reçu, palpitation sous 25 %, éclat qui parcourt la
 *       barre), traînée des dégâts récents bleu clair. Si la barre de Roger est affichée en même temps, celle de Morgan passe
 *       juste en dessous.</li>
 * </ul>
 */
public final class MorganBossBar implements HudElement {

    public static final double SHOW_RADIUS = 32.0;
    public static final double HIDE_RADIUS = 40.0;
    private static final float FADE_TICKS = 10.0F;
    /** Coup reçu : tremblement et éclat de la barre (ticks). Éclat doré : durée et période (ticks). */
    private static final int HURT_TICKS = 6;
    private static final float SHINE_TICKS = 22.0F, SHINE_PERIOD = 90.0F;

    /** Décor et carrés de vie (positions identiques au générateur make_morgan_bossbar.py). */
    private static final Identifier FRAME = BloxPiece.id("textures/gui/morgan_bossbar.png");
    private static final Identifier CELLS_TEX = BloxPiece.id("textures/gui/morgan_bossbar_cells.png");
    /** Le premier plan (crâne, tricorne, sabres, éclairs, capes, épaulettes), redessiné PAR-DESSUS les carrés. */
    private static final Identifier EMBLEM = BloxPiece.id("textures/gui/morgan_bossbar_emblem.png");
    private static final int FRAME_W = 360, FRAME_H = 64;
    /**
     * Barre de vie : UNE rangée continue de {@link #CELLS} carrés de {@link #CELL} px séparés de {@link #GAP} px, d'un bout à
     * l'autre ; elle passe derrière l'emblème et ses bouts sous les épaulettes (calque EMBLEM redessiné par-dessus).
     */
    private static final int CELLS = 39, CELL = 6, GAP = 1, CELL_X = 44, CELL_Y = 34;
    private static final int BAR_X0 = CELL_X - 2, BAR_X1 = CELL_X + CELLS * (CELL + GAP) + 1, BAR_Y0 = CELL_Y - 2,
            BAR_Y1 = CELL_Y + CELL + 2;

    private static Morgan boss;
    /** Fondu (0 = invisible, 1 = visible). */
    private static float fade, fadeOld;
    /** Vie affichée (0 à 1) et traînée des dégâts récents. */
    private static float health = 1.0F, trail = 1.0F, trailOld = 1.0F;
    /** Ticks restants de la réaction à un coup ; horloge des animations. */
    private static int hurt;
    private static int clock;

    private MorganBossBar() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, BloxPiece.id("morgan_boss_bar"), new MorganBossBar());
    }

    /** Chaque tick client : choix du boss suivi (zone), vie, fondu. */
    public static void tick(Minecraft client) {
        fadeOld = fade;
        trailOld = trail;
        if (client.player == null || client.level == null) {
            boss = null;
            fade = fadeOld = 0.0F;
            return;
        }
        // le boss suivi reste suivi jusqu'à HIDE_RADIUS (marge) ; sinon, le plus proche à moins de SHOW_RADIUS
        if (boss != null && (boss.isRemoved() || boss.level() != client.level || boss.distanceTo(client.player) > HIDE_RADIUS)) {
            boss = boss.isDeadOrDying() ? boss : null; // mort : on le garde le temps que la barre se vide et s'efface
        }
        if (boss == null || boss.isRemoved()) {
            List<Morgan> near = client.level.getEntitiesOfClass(Morgan.class,
                    client.player.getBoundingBox().inflate(SHOW_RADIUS), r -> r.isAlive() && r.distanceTo(client.player) <= SHOW_RADIUS);
            Morgan nearest = null;
            for (Morgan r : near) {
                if (nearest == null || r.distanceToSqr(client.player) < nearest.distanceToSqr(client.player)) {
                    nearest = r;
                }
            }
            if (nearest != null) {
                boss = nearest;
                health = trail = trailOld = nearest.getHealth() / Math.max(1.0F, nearest.getMaxHealth());
            } else if (boss != null && boss.isRemoved()) {
                boss = null;
            }
        }
        boolean visible = boss != null && boss.isAlive() && !boss.isRemoved();
        clock++;
        if (hurt > 0) {
            hurt--;
        }
        if (boss != null) {
            float now = boss.isAlive() ? boss.getHealth() / Math.max(1.0F, boss.getMaxHealth()) : 0.0F;
            if (now < health - 1.0E-4F) {
                hurt = HURT_TICKS; // coup reçu : la barre tremble et s'éclaire un instant
            }
            health = now;
        }
        fade = Math.max(0.0F, Math.min(1.0F, fade + (visible ? 1.0F : -1.0F) / FADE_TICKS));
        // traînée dorée : rattrape la vie (vite vers le bas, tout de suite vers le haut quand il se soigne)
        trail = health >= trail ? health : Math.max(health, trail - 0.02F - (trail - health) * 0.08F);
        if (fade <= 0.0F && !visible) {
            boss = null;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        float pt = deltaTracker.getGameTimeDeltaPartialTick(false);
        float a = fadeOld + (fade - fadeOld) * pt;
        if (a <= 0.01F) {
            return;
        }
        float shown = trailOld + (trail - trailOld) * pt;
        float time = clock + pt;
        // taille : jamais plus large que l'écran ; apparition : léger grossissement (92 % -> 100 %) et glissade depuis le haut
        float appear = 0.92F + 0.08F * a;
        float scale = Math.min(1.0F, (g.guiWidth() - 12.0F) / FRAME_W) * appear;
        float hurtK = hurt > 0 ? Math.max(0.0F, (hurt - pt) / HURT_TICKS) : 0.0F;
        float shake = hurtK > 0.0F ? (float) Math.sin(time * 9.0F) * 1.2F * hurtK : 0.0F;
        g.pose().pushMatrix();
        float top = RogerBossBar.showing() ? 3.0F + FRAME_H * scale + 2.0F : 3.0F; // sous celle de Roger s'il est là aussi
        g.pose().translate(g.guiWidth() / 2.0F + shake, top + (1.0F - a) * -6.0F);
        g.pose().scale(scale);
        g.pose().translate(-FRAME_W / 2.0F, 0.0F);
        int white = alpha(0xFFFFFFFF, a);
        // décor complet : emblème (crâne de Roger, tricorne, sabres Ace), pans de cape, plumes, nom en lettres pixel
        g.blit(RenderPipelines.GUI_TEXTURED, FRAME, 0, 0, 0.0F, 0.0F, FRAME_W, FRAME_H, FRAME_W, FRAME_H, white);
        // carrés de vie : une rangée continue, de gauche à droite sous l'emblème (chacun = 1/CELLS de la vie) ; le carré entamé se vide
        // progressivement ; les dégâts récents restent dorés un instant ; sous 25 % de vie, les carrés restants palpitent
        float lifeCells = CELLS * Math.max(0.0F, Math.min(1.0F, health));
        float trailCells = CELLS * Math.max(0.0F, Math.min(1.0F, shown));
        float pulse = health < 0.25F ? 0.5F + 0.5F * (float) Math.sin(time * 0.5F) : 0.0F;
        for (int i = 0; i < CELLS; i++) {
            int x = CELL_X + i * (CELL + GAP);
            g.blit(RenderPipelines.GUI_TEXTURED, CELLS_TEX, x, CELL_Y, 7.0F, 0.0F, CELL, CELL, 13, 6, white); // vide
            float lifeK = Math.max(0.0F, Math.min(1.0F, lifeCells - i));
            float trailK = Math.max(0.0F, Math.min(1.0F, trailCells - i));
            int lifePx = Math.round(CELL * lifeK), trailPx = Math.round(CELL * trailK);
            if (trailPx > lifePx) {
                g.fill(x + lifePx, CELL_Y + 1, x + Math.max(lifePx + 1, trailPx - 1), CELL_Y + CELL - 1, alpha(0xFF9ED8F0, a));
            }
            if (lifePx > 0) {
                g.blit(RenderPipelines.GUI_TEXTURED, CELLS_TEX, x, CELL_Y, 0.0F, 0.0F, lifePx, CELL, 13, 6, white);
                if (hurtK > 0.0F) {
                    g.fill(x, CELL_Y, x + lifePx, CELL_Y + CELL, alpha(0xFFFFF0E0, a * 0.5F * hurtK)); // coup reçu
                }
                if (pulse > 0.0F) {
                    g.fill(x, CELL_Y, x + lifePx, CELL_Y + CELL, alpha(0xFFFF60B0, a * 0.3F * pulse));
                }
            }
        }
        // premier plan par-dessus les carrés : la rangée passe derrière le crâne et sous les épaulettes
        g.blit(RenderPipelines.GUI_TEXTURED, EMBLEM, 0, 0, 0.0F, 0.0F, FRAME_W, FRAME_H, FRAME_W, FRAME_H, white);
        // éclat doré qui parcourt la barre de temps en temps (bande oblique)
        float phase = (time % SHINE_PERIOD) / SHINE_TICKS;
        if (phase < 1.0F) {
            int cx = Math.round(BAR_X0 - 20 + (BAR_X1 - BAR_X0 + 40) * phase);
            for (int y = BAR_Y0; y < BAR_Y1; y++) {
                int x0 = cx + (BAR_Y1 - y) / 2;
                int lo = Math.max(BAR_X0, x0), hi = Math.min(BAR_X1, x0 + 4);
                if (hi > lo) {
                    g.fill(lo, y, hi, y + 1, alpha(0xFFE8F6FF, a * 0.25F));
                }
            }
        }
        // nom : grand, or clair, contour sombre (le nom de l'entité : traduit, ou son étiquette de nom)
        g.pose().popMatrix();
    }

    private static int alpha(int argb, float a) {
        int base = (argb >>> 24) & 0xFF;
        return ((int) (base * Math.max(0.0F, Math.min(1.0F, a))) << 24) | (argb & 0xFFFFFF);
    }
}
