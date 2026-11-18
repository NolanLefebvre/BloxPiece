package nolan.bloxpiece.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.network.StartRoulettePayload;
import nolan.bloxpiece.treasure.FruitRarity;
import nolan.bloxpiece.treasure.TreasureChests;

/**
 * Roulette du Coffre au Trésor, style "chasse au trésor" : panneau en bois clouté cerclé de fer forgé et d'ornements dorés, bannière
 * pirate rouge, bande de fruits dans un creux sombre, viseur doré lumineux au centre, parchemin du résultat et bouton texturé.
 * Textures : textures/gui/roulette/ (générées par make_roulette_gui.py, hors du dépôt). 1 pixel de texture = 1 pixel GUI.
 *
 * Déroulement :
 * <ol>
 *   <li>PRÊT : la bande de fruits est déjà affichée, immobile ; le joueur clique sur OUVRIR (StartRoulettePayload envoyé au serveur).</li>
 *   <li>ROULETTE : la bande défile de droite à gauche pendant {@link #SPIN_MS} ms en ralentissant (flou de mouvement, icônes qui
 *       s'estompent sur les bords, cliquetis), puis s'arrête pile sur le fruit gagné, au centre du viseur.</li>
 *   <li>TERMINÉ : carillon, gerbe d'éclats de la couleur de la rareté, parchemin avec le nom du fruit et sa rareté ; bouton
 *       RÉCUPÉRER (ou Échap) pour fermer.</li>
 * </ol>
 * Le fruit est tiré (au clic sur OUVRIR) et donné par le serveur (TreasureChests) : cet écran ne fait que l'animation.
 * Avant OUVRIR, Échap referme la fenêtre et le joueur garde son coffre (rien n'est consommé). Une fois lancée, la roulette ne peut
 * plus être fermée avant la fin ; l'écran n'a pas d'inventaire : aucun objet ne peut être déplacé pendant la roulette.
 */
public class RouletteScreen extends Screen {

    private enum State { READY, SPINNING, DONE }

    // ---- Animation
    public static final long SPIN_MS = 4200;               // durée du défilement (le serveur donne le fruit juste après)
    private static final int STRIP_LENGTH = 56;            // nombre d'icônes de la bande
    private static final int WINNER_INDEX = 50;            // position du fruit gagné dans la bande
    private static final float START_POS = 5.0F;           // icône au centre au départ (la bande est déjà pleine à gauche)
    private static final long MIN_CLICK_GAP_MS = 30;       // au plus un cliquetis toutes les 30 ms (début très rapide)
    private static final float FADE_WIDTH = 64.0F;         // largeur (px) du dégradé de transparence sur les bords de la bande

    // ---- Mise en page (repère du panneau, pixels GUI)
    private static final int PANEL_W = 400;
    private static final int PANEL_H = 250;
    private static final int REEL_X = 24, REEL_Y = 52, REEL_W = 352, REEL_H = 76;
    private static final int REEL_CX = REEL_X + REEL_W / 2;
    private static final int REEL_CY = REEL_Y + REEL_H / 2;
    private static final int SLOT = 48;                    // espacement entre deux icônes
    private static final float ICON_SCALE = 0.95F;         // icônes 32 px -> ~30 px (x1,3 au centre)
    private static final int BANNER_W = 240, BANNER_H = 34, BANNER_X = (PANEL_W - BANNER_W) / 2, BANNER_Y = -14;
    private static final int SELECTOR_W = 56, SELECTOR_H = 88;
    private static final int PLAQUE_W = 260, PLAQUE_H = 58, PLAQUE_X = (PANEL_W - PLAQUE_W) / 2, PLAQUE_Y = 144;
    private static final int BUTTON_W = 132, BUTTON_H = 26, BUTTON_X = (PANEL_W - BUTTON_W) / 2, BUTTON_Y = 212;

    // ---- Textures
    private static final Identifier PANEL = BloxPiece.id("textures/gui/roulette/panel.png");
    private static final Identifier BANNER = BloxPiece.id("textures/gui/roulette/banner.png");
    private static final Identifier SELECTOR = BloxPiece.id("textures/gui/roulette/selector.png");
    private static final Identifier PLAQUE = BloxPiece.id("textures/gui/roulette/plaque.png");
    private static final Identifier BUTTON = BloxPiece.id("textures/gui/roulette/button.png");

    // ---- Couleurs (ARGB)
    private static final int COLOR_GOLD = 0xFFC8A24A;
    private static final int COLOR_GOLD_BRIGHT = 0xFFFFD25A;
    private static final int COLOR_GOLD_PALE = 0xFFFFF0B0;
    private static final int COLOR_INK = 0xFF4A2E16;
    private static final int COLOR_HINT = 0xFFD8C8A0;

    // Numéro du clic gauche depuis le passage à SDL (voir BloxPanelScreen)
    private static final int LEFT_MOUSE_BUTTON = 1;

    /** Icône 32x32 de la roulette pour chaque fruit (textures/gui/roulette/fruit/<id>.png), ou null : on dessine alors l'objet. */
    private static final Map<Item, Optional<Identifier>> ICONS = new HashMap<>();

    private ItemStack winner = ItemStack.EMPTY;      // reçu du serveur après le clic sur OUVRIR
    private FruitRarity rarity = FruitRarity.RARE;
    private boolean hasWinner;
    private final List<Item> strip = new ArrayList<>();
    private final List<Sparkle> sparkles = new ArrayList<>();
    private final Random random = new Random();

    private State state = State.READY;
    private final long openedMs = System.currentTimeMillis();
    private long spinStartMs;
    private long doneMs;
    private long lastFrameNs = System.nanoTime();
    private int lastSlot = Math.round(START_POS);
    private long lastClickMs;
    private float hover;              // 0 -> 1 : animation de survol du bouton

    // Position et échelle du panneau à la dernière image (pour convertir les clics)
    private float originX;
    private float originY;
    private float scale = 1.0F;

    public RouletteScreen() {
        super(Component.translatable("screen.bloxpiece.roulette.title"));

        // Bande aléatoire de fruits (jamais deux fois le même d'affilée) ; le fruit gagné remplacera celui de WINNER_INDEX
        List<Item> fruits = TreasureChests.allFruits();
        Item previous = null;
        for (int i = 0; i < STRIP_LENGTH; i++) {
            Item item;
            do {
                item = fruits.get(this.random.nextInt(fruits.size()));
            } while (fruits.size() > 1 && item == previous);
            this.strip.add(item);
            previous = item;
        }
    }

    /** Réponse du serveur au clic sur OUVRIR : la roulette s'arrêtera sur ce fruit (bien avant qu'il n'arrive au centre). */
    public void setWinner(Item fruit) {
        this.winner = new ItemStack(fruit);
        this.rarity = FruitRarity.of(fruit);
        this.strip.set(WINNER_INDEX, fruit);
        this.hasWinner = true;
    }

    /** Le serveur n'a pas trouvé le coffre (jeté, cassé, trop loin) : la fenêtre se ferme. */
    public void fail() {
        super.onClose();
    }

    // ------------------------------------------------------------------ sécurité : pas de fermeture avant la fin

    @Override
    public boolean shouldCloseOnEsc() {
        return this.state != State.SPINNING;
    }

    @Override
    public void onClose() {
        if (this.state == State.READY) {
            // Refermée sans lancer : le serveur oublie la roulette, le coffre n'est pas consommé
            ClientPlayNetworking.send(new StartRoulettePayload(StartRoulettePayload.CANCEL));
            super.onClose();
        } else if (this.state == State.DONE) {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false; // le serveur continue de tourner (il donne le fruit à la fin)
    }

    // ------------------------------------------------------------------ bouton

    private boolean overButton(int mx, int my) {
        return mx >= BUTTON_X && mx < BUTTON_X + BUTTON_W && my >= BUTTON_Y && my < BUTTON_Y + BUTTON_H;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == LEFT_MOUSE_BUTTON) {
            int mx = Math.round((float) ((event.x() - this.originX) / this.scale));
            int my = Math.round((float) ((event.y() - this.originY) / this.scale));
            if (overButton(mx, my)) {
                if (this.state == State.READY) {
                    launch();
                    return true;
                }
                if (this.state == State.DONE) {
                    playUi(1.0F);
                    this.onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** OUVRIR : prévient le serveur et lance l'animation. */
    private void launch() {
        this.state = State.SPINNING;
        this.spinStartMs = System.currentTimeMillis();
        ClientPlayNetworking.send(new StartRoulettePayload(StartRoulettePayload.START));
        playUi(1.0F);
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHEST_OPEN, 1.0F));
        burst(REEL_CX, REEL_CY, 24, COLOR_GOLD_BRIGHT);
    }

    private void playUi(float pitch) {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    // ------------------------------------------------------------------ animation

    /** Avancement 0..1 du défilement. */
    private float spinProgress(long now) {
        return Mth.clamp((now - this.spinStartMs) / (float) SPIN_MS, 0.0F, 1.0F);
    }

    /** Position de la bande (en icônes) : l'icône d'indice "position" est au centre. Ralentit jusqu'à WINNER_INDEX exactement. */
    private float position(long now) {
        if (this.state == State.READY) {
            return START_POS;
        }
        float inv = 1.0F - spinProgress(now);
        float eased = 1.0F - inv * inv * inv * inv; // décélération douce (easeOutQuart)
        return START_POS + (WINNER_INDEX - START_POS) * eased;
    }

    /** Vitesse de la bande, en icônes par seconde (dérivée de position) : sert au flou de mouvement. */
    private float speed(long now) {
        if (this.state != State.SPINNING) {
            return 0.0F;
        }
        float inv = 1.0F - spinProgress(now);
        return (WINNER_INDEX - START_POS) * 4.0F * inv * inv * inv / (SPIN_MS / 1000.0F);
    }

    private void update(long now, float pos, float dt) {
        if (this.state == State.SPINNING) {
            int slot = Math.round(pos);
            if (slot != this.lastSlot && now - this.lastClickMs >= MIN_CLICK_GAP_MS) {
                this.lastSlot = slot;
                this.lastClickMs = now;
                playUi(1.0F + 0.5F * (slot - START_POS) / (WINNER_INDEX - START_POS)); // monte un peu vers la fin
            }
            // Poussière d'or autour du viseur pendant le défilement
            if (this.random.nextFloat() < 0.5F) {
                spawnAroundSelector(COLOR_GOLD_BRIGHT, 1);
            }
            if (now - this.spinStartMs >= SPIN_MS && this.hasWinner) {
                this.state = State.DONE;
                this.doneMs = now;
                // Carillon de victoire : orbe d'XP + accord de notes (+ fanfare pour Légendaire et Mythique)
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, 1.0F));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, 1.5F));
                if (this.rarity == FruitRarity.LEGENDARY || this.rarity == FruitRarity.MYTHIC) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
                }
                burst(REEL_CX, REEL_CY, 60, this.rarity.color);
            }
        } else if (this.state == State.DONE && this.random.nextFloat() < 0.6F) {
            spawnAroundSelector(this.random.nextBoolean() ? this.rarity.color : COLOR_GOLD_BRIGHT, 1);
        } else if (this.state == State.READY && this.random.nextFloat() < 0.15F) {
            spawnAroundSelector(COLOR_GOLD, 1);
        }

        // Particules
        Iterator<Sparkle> it = this.sparkles.iterator();
        while (it.hasNext()) {
            Sparkle s = it.next();
            s.life -= dt;
            if (s.life <= 0.0F) {
                it.remove();
                continue;
            }
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.vy += 30.0F * dt; // légère retombée
        }

        // Survol du bouton : transition fluide
        float target = this.state != State.SPINNING && this.buttonHovered ? 1.0F : 0.0F;
        this.hover += (target - this.hover) * Math.min(1.0F, dt * 12.0F);
    }

    private boolean buttonHovered;

    private void spawnAroundSelector(int color, int count) {
        for (int i = 0; i < count; i++) {
            float x = REEL_CX + (this.random.nextFloat() - 0.5F) * SELECTOR_W;
            float y = REEL_CY + (this.random.nextBoolean() ? -1 : 1) * (SELECTOR_H / 2.0F - 6.0F) + (this.random.nextFloat() - 0.5F) * 10.0F;
            this.sparkles.add(new Sparkle(x, y, (this.random.nextFloat() - 0.5F) * 20.0F, -10.0F - this.random.nextFloat() * 20.0F,
                    0.6F + this.random.nextFloat() * 0.6F, color));
        }
    }

    private void burst(float x, float y, int count, int color) {
        for (int i = 0; i < count; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            float v = 40.0F + this.random.nextFloat() * 90.0F;
            this.sparkles.add(new Sparkle(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v * 0.6F,
                    0.7F + this.random.nextFloat() * 0.8F, this.random.nextInt(3) == 0 ? COLOR_GOLD_PALE : color));
        }
    }

    // ------------------------------------------------------------------ dessin

    // Fond personnalisé (dessiné dans extractRenderState) : on neutralise celui de Screen
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        long nowNs = System.nanoTime();
        float dt = Mth.clamp((nowNs - this.lastFrameNs) / 1.0E9F, 0.0F, 0.1F);
        this.lastFrameNs = nowNs;
        float pos = position(now);
        float seconds = (now - this.openedMs) / 1000.0F;
        float open = easeOut(Mth.clamp((now - this.openedMs) / 300.0F, 0.0F, 1.0F));
        float reveal = this.state == State.DONE ? easeOut(Mth.clamp((now - this.doneMs) / 450.0F, 0.0F, 1.0F)) : 0.0F;

        // Fond assombri plein écran, vignette chaude
        g.fillGradient(0, 0, this.width, this.height, (int) (0xC0 * open) << 24 | 0x05050C, (int) (0xE0 * open) << 24 | 0x120A04);

        // Panneau centré, réduit si l'écran est petit, avec une légère montée à l'ouverture
        this.scale = Math.min(1.0F, Math.min((this.width - 12) / (float) PANEL_W, (this.height - 30) / (float) (PANEL_H + 20)));
        this.originX = (this.width - PANEL_W * this.scale) / 2.0F;
        this.originY = (this.height - PANEL_H * this.scale) / 2.0F + 8.0F * this.scale + (1.0F - open) * 14.0F * this.scale;
        int mx = Math.round((mouseX - this.originX) / this.scale);
        int my = Math.round((mouseY - this.originY) / this.scale);
        this.buttonHovered = overButton(mx, my);
        update(now, pos, dt);

        g.pose().pushMatrix();
        g.pose().translate(this.originX, this.originY);
        g.pose().scale(this.scale);

        int white = alpha(open) | 0xFFFFFF;
        blit(g, PANEL, 0, 0, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H, white);

        drawGlow(g, seconds, reveal);
        drawStrip(g, now, pos, seconds, reveal); // la bande de fruits est visible dès l'ouverture, immobile avant OUVRIR
        blit(g, SELECTOR, REEL_CX - SELECTOR_W / 2, REEL_CY - SELECTOR_H / 2, 0, 0, SELECTOR_W, SELECTOR_H, SELECTOR_W, SELECTOR_H, white);
        drawSparkles(g);
        drawBanner(g, open);
        drawInfo(g, seconds, reveal, open);
        drawButton(g, seconds, open);

        g.pose().popMatrix();
    }

    /** Lueur magique derrière le viseur : dorée qui pulse doucement, puis de la couleur de la rareté une fois le fruit trouvé. */
    private void drawGlow(GuiGraphicsExtractor g, float seconds, float reveal) {
        int color = this.state == State.DONE ? this.rarity.color : COLOR_GOLD_BRIGHT;
        float pulse = 0.5F + 0.5F * (float) Math.sin(seconds * (this.state == State.DONE ? 5.0F : 3.0F));
        float strength = this.state == State.DONE ? 0.55F + 0.25F * pulse : 0.22F + 0.12F * pulse;
        for (int r = 6; r >= 0; r--) {
            int a = (int) (strength * 255.0F * (1.0F - r / 7.0F) * 0.35F);
            int half = 14 + r * 3;
            int halfH = 18 + r * 3;
            g.fill(REEL_CX - half, Math.max(REEL_Y, REEL_CY - halfH), REEL_CX + half, Math.min(REEL_Y + REEL_H, REEL_CY + halfH),
                    (a << 24) | (color & 0xFFFFFF));
        }
        // Rayons verticaux et horizontaux (éclat) à la révélation
        if (reveal > 0.0F) {
            int a = (int) (0x90 * (1.0F - reveal * 0.5F) * (0.6F + 0.4F * pulse));
            g.fill(REEL_CX - 1, REEL_Y + 2, REEL_CX + 1, REEL_Y + REEL_H - 2, (a << 24) | 0xFFFFFF);
            g.fill(REEL_X + 2, REEL_CY - 1, REEL_X + REEL_W - 2, REEL_CY + 1, ((a / 3) << 24) | (color & 0xFFFFFF));
        }
    }

    /** La bande de fruits : ombre portée, flou de mouvement, dégradé de transparence sur les bords, pierre de rareté sous chaque fruit. */
    private void drawStrip(GuiGraphicsExtractor g, long now, float pos, float seconds, float reveal) {
        float speed = speed(now);                                   // icônes par seconde
        int ghosts = Math.min(4, (int) (speed / 5.0F));             // copies du flou de mouvement
        float ghostStep = Math.min(9.0F, speed * 0.55F);            // écart entre deux copies (px)
        float halfW = REEL_W / 2.0F - 4.0F;
        for (int i = 0; i < this.strip.size(); i++) {
            float dx = (i - pos) * SLOT;
            float alpha = Mth.clamp((halfW - Math.abs(dx)) / FADE_WIDTH, 0.0F, 1.0F);
            if (alpha <= 0.02F) {
                continue;
            }
            boolean isWinner = i == WINNER_INDEX && this.state == State.DONE;
            float center = Math.max(0.0F, 1.0F - Math.abs(dx) / SLOT);
            float s = ICON_SCALE * (1.0F + 0.3F * center);
            float bob = this.state == State.DONE && !isWinner ? 0.0F : (float) Math.sin(seconds * 4.0F + i * 1.3F) * 1.5F;
            if (isWinner) {
                s *= 1.0F + 0.15F * reveal + 0.05F * (float) Math.sin(seconds * 7.0F);
                bob = 0.0F;
            }
            if (this.state == State.DONE && !isWinner) {
                alpha *= 1.0F - 0.6F * reveal; // les autres fruits s'effacent un peu pour mettre le gagnant en valeur
            }
            float x = REEL_CX + dx;
            float y = REEL_CY + bob - 2.0F;
            Item item = this.strip.get(i);

            // Ombre portée sous le fruit
            int shadowA = (int) (0x70 * alpha);
            g.fill(Math.round(x - 10 * s), Math.round(REEL_CY + 17), Math.round(x + 10 * s), Math.round(REEL_CY + 19), shadowA << 24);
            // Pierre de rareté
            int gem = FruitRarity.of(item).color;
            g.fill(Math.round(x - 4), REEL_Y + REEL_H - 9, Math.round(x + 4), REEL_Y + REEL_H - 7, (int) (0xE0 * alpha) << 24 | (gem & 0xFFFFFF));

            // Flou de mouvement : copies de plus en plus transparentes, en traînée à droite (la bande va vers la gauche)
            for (int k = ghosts; k >= 1; k--) {
                drawIcon(g, item, x + k * ghostStep, y, s, alpha * 0.32F / k);
            }
            drawIcon(g, item, x, y, s, alpha);
        }
    }

    private void drawIcon(GuiGraphicsExtractor g, Item item, float x, float y, float s, float alpha) {
        Identifier icon = icon(item);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s);
        if (icon != null) {
            blit(g, icon, -16, -16, 0, 0, 32, 32, 32, 32, alpha(alpha) | 0xFFFFFF);
        } else if (alpha > 0.5F) {
            g.pose().scale(2.0F);
            g.item(new ItemStack(item), -8, -8); // fruit sans icône de roulette : objet normal (sans transparence)
        }
        g.pose().popMatrix();
    }

    private void drawSparkles(GuiGraphicsExtractor g) {
        for (Sparkle s : this.sparkles) {
            float a = Mth.clamp(s.life / 0.4F, 0.0F, 1.0F);
            int c = alpha(a) | (s.color & 0xFFFFFF);
            int x = Math.round(s.x);
            int y = Math.round(s.y);
            g.fill(x - 1, y, x + 2, y + 1, c); // petite croix scintillante
            g.fill(x, y - 1, x + 1, y + 2, c);
        }
    }

    /** Bannière pirate rouge et titre : "OUVERTURE DE COFFRE", puis "TRÉSOR <RARETÉ>" une fois le fruit trouvé. */
    private void drawBanner(GuiGraphicsExtractor g, float open) {
        blit(g, BANNER, BANNER_X, BANNER_Y, 0, 0, BANNER_W, BANNER_H, BANNER_W, BANNER_H, alpha(open) | 0xFFFFFF);
        Component title;
        int color;
        if (this.state == State.DONE) {
            title = Component.translatable("screen.bloxpiece.roulette.title_won", Component.translatable(this.rarity.translationKey()));
            color = this.rarity == FruitRarity.COMMON ? COLOR_GOLD_PALE : this.rarity.color;
        } else {
            title = this.getTitle();
            color = COLOR_GOLD_BRIGHT;
        }
        title = title.copy().withStyle(ChatFormatting.BOLD);
        g.pose().pushMatrix();
        g.pose().translate(PANEL_W / 2.0F, BANNER_Y + 10.0F);
        g.pose().scale(1.25F);
        g.centeredText(this.font, title, 0, 0, color);
        g.pose().popMatrix();
        g.centeredText(this.font, Component.translatable("screen.bloxpiece.roulette.subtitle"), PANEL_W / 2, 32, COLOR_GOLD);
    }

    /** Sous la bande : consigne, puis parchemin du résultat (rareté, nom du fruit dans sa couleur, ajout à l'inventaire). */
    private void drawInfo(GuiGraphicsExtractor g, float seconds, float reveal, float open) {
        if (this.state == State.READY) {
            float pulse = 0.65F + 0.35F * (float) Math.sin(seconds * 3.0F);
            g.centeredText(this.font, Component.translatable("screen.bloxpiece.roulette.ready"), PANEL_W / 2, 160,
                    alpha(pulse * open) | (COLOR_HINT & 0xFFFFFF));
            return;
        }
        if (this.state == State.SPINNING) {
            int dots = (int) (seconds * 3.0F) % 4;
            g.centeredText(this.font, Component.translatable("screen.bloxpiece.roulette.spinning").append(".".repeat(dots)),
                    PANEL_W / 2, 160, COLOR_HINT);
            return;
        }
        // Parchemin qui se déroule (glisse vers le haut et apparaît)
        int a = alpha(reveal);
        int y = PLAQUE_Y + Math.round((1.0F - reveal) * 10.0F);
        blit(g, PLAQUE, PLAQUE_X, y, 0, 0, PLAQUE_W, PLAQUE_H, PLAQUE_W, PLAQUE_H, a | 0xFFFFFF);
        if (reveal < 0.15F) {
            return; // texte seulement quand le parchemin est visible (un texte presque transparent n'est pas dessiné)
        }
        Component rarityLine = Component.literal("★ ").append(Component.translatable(this.rarity.translationKey())).append(" ★")
                .withStyle(ChatFormatting.BOLD);
        g.centeredText(this.font, rarityLine, PANEL_W / 2, y + 8, a | (this.rarity.color & 0xFFFFFF));
        Component name = this.winner.getHoverName().copy().withStyle(ChatFormatting.BOLD);
        g.pose().pushMatrix();
        g.pose().translate(PANEL_W / 2.0F, y + 22.0F);
        g.pose().scale(1.2F);
        g.centeredText(this.font, name, 0, 0, a | (this.rarity.color & 0xFFFFFF));
        g.pose().popMatrix();
        Component added = Component.translatable("screen.bloxpiece.roulette.added");
        g.text(this.font, added, PANEL_W / 2 - this.font.width(added) / 2, y + 40, a | (COLOR_INK & 0xFFFFFF), false);
    }

    /** Bouton texturé OUVRIR / RÉCUPÉRER : grossit et s'illumine au survol (transition fluide), grisé pendant la roulette. */
    private void drawButton(GuiGraphicsExtractor g, float seconds, float open) {
        boolean disabled = this.state == State.SPINNING;
        float grow = 1.0F + 0.05F * this.hover;
        g.pose().pushMatrix();
        g.pose().translate(BUTTON_X + BUTTON_W / 2.0F, BUTTON_Y + BUTTON_H / 2.0F);
        g.pose().scale(grow);
        g.pose().translate(-BUTTON_W / 2.0F, -BUTTON_H / 2.0F);

        // Halo qui pulse autour du bouton OUVRIR pour attirer l'oeil
        if (this.state == State.READY) {
            float pulse = 0.5F + 0.5F * (float) Math.sin(seconds * 4.0F);
            for (int r = 3; r >= 1; r--) {
                g.fill(-r * 2, -r * 2, BUTTON_W + r * 2, BUTTON_H + r * 2, alpha((0.10F + 0.10F * pulse) / r * open) | 0xFFC040);
            }
        }
        int base = disabled ? 0x80FFFFFF : alpha(open) | 0xFFFFFF;
        blit(g, BUTTON, 0, 0, 0, 0, BUTTON_W, BUTTON_H, BUTTON_W, BUTTON_H * 2, base);
        if (!disabled && this.hover > 0.01F) {
            blit(g, BUTTON, 0, 0, 0, BUTTON_H, BUTTON_W, BUTTON_H, BUTTON_W, BUTTON_H * 2, alpha(this.hover * open) | 0xFFFFFF);
        }
        Component label = switch (this.state) {
            case READY -> Component.translatable("screen.bloxpiece.roulette.open");
            case SPINNING -> Component.literal("...");
            case DONE -> Component.translatable("screen.bloxpiece.roulette.claim");
        };
        int textColor = disabled ? 0xFF9A8A70 : (this.hover > 0.5F ? 0xFFFFFFFF : COLOR_GOLD_PALE);
        g.centeredText(this.font, label.copy().withStyle(ChatFormatting.BOLD), BUTTON_W / 2, (BUTTON_H - 8) / 2, textColor);
        g.pose().popMatrix();

        if (this.state == State.DONE) {
            g.centeredText(this.font, Component.translatable("screen.bloxpiece.roulette.close"), PANEL_W / 2, PANEL_H + 6, COLOR_HINT);
        } else if (this.state == State.READY) {
            g.centeredText(this.font, Component.translatable("screen.bloxpiece.roulette.later"), PANEL_W / 2, PANEL_H + 6, COLOR_HINT);
        }
    }

    // ------------------------------------------------------------------ outils

    private static void blit(GuiGraphicsExtractor g, Identifier texture, int x, int y, int u, int v, int w, int h, int texW, int texH, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) u, (float) v, w, h, texW, texH, color);
    }

    /** Icône de roulette du fruit (vérifiée une seule fois dans les ressources). */
    private static Identifier icon(Item item) {
        return ICONS.computeIfAbsent(item, it -> {
            Identifier key = BuiltInRegistries.ITEM.getKey(it);
            Identifier id = Identifier.fromNamespaceAndPath(key.getNamespace(), "textures/gui/roulette/fruit/" + key.getPath() + ".png");
            return Minecraft.getInstance().getResourceManager().getResource(id).isPresent() ? Optional.of(id) : Optional.empty();
        }).orElse(null);
    }

    /** Alpha 0..1 -> octet de poids fort d'une couleur ARGB. */
    private static int alpha(float a) {
        return ((int) (Mth.clamp(a, 0.0F, 1.0F) * 255.0F)) << 24;
    }

    private static float easeOut(float t) {
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    /** Un éclat scintillant (poussière d'or), en coordonnées du panneau. */
    private static final class Sparkle {
        float x, y, vx, vy, life;
        final int color;

        Sparkle(float x, float y, float vx, float vy, float life, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.color = color;
        }
    }
}
