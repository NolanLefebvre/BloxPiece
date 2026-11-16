package nolan.bloxpiece.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Particule personnalisée du fruit du Bouddha : un sprite dessiné pour le mod (lueur, étoile, pétale, éclair, nuage), en pleine
 * luminosité (l'énergie éclaire d'elle-même), semi-transparent, avec une courbe de taille, une couleur qui évolue (or -> orange...)
 * et une apparition / disparition en fondu. Les sprites animés (étoile, pétale, nuage) changent d'image selon l'âge.
 *
 * Une seule classe pour toutes les particules du Bouddha : chaque type a son {@link Style}. La vitesse reçue est celle envoyée
 * par le serveur (sendParticles), sans le petit bruit aléatoire que Minecraft ajoute par défaut : les anneaux restent réguliers.
 */
public class BuddhaParticle extends SingleQuadParticle {

    /** Réglages d'un type de particule. Couleurs en 0xRRGGBB (les sprites sont en niveaux de gris, teintés ici). */
    public enum Style {
        //     vie min, vie max, taille, taille finale (x), frottement, gravité, couleur début, couleur fin, opacité, animé
        GLOW        (10, 16, 0.30F, 0.35F, 0.90F, 0.0F,    0xFFD45A, 0xFFB030, 0.90F, false),
        GLOW_PALE   (10, 16, 0.34F, 0.40F, 0.90F, 0.0F,    0xFFF6C8, 0xFFDC78, 0.90F, false),
        GLOW_ORANGE (10, 16, 0.26F, 0.35F, 0.90F, 0.0F,    0xFFA43A, 0xF06A1E, 0.85F, false),
        SPARK       (16, 28, 0.16F, 0.30F, 0.96F, 0.0F,    0xFFFFF0, 0xFFC040, 1.00F, true),
        PETAL       (24, 44, 0.13F, 0.60F, 0.97F, -0.006F, 0xFFE08A, 0xFF9A30, 1.00F, true),
        FLASH       (6,  6,  3.20F, 1.80F, 1.00F, 0.0F,    0xFFF4C0, 0xFFC850, 0.85F, false),
        MIST        (30, 50, 0.70F, 2.80F, 0.92F, -0.004F, 0xF6E2A8, 0xD8B070, 0.55F, true);

        final int minLife, maxLife;
        final float size, endSize, friction, gravity, alpha;
        final int startColor, endColor;
        final boolean animated;

        Style(int minLife, int maxLife, float size, float endSize, float friction, float gravity,
              int startColor, int endColor, float alpha, boolean animated) {
            this.minLife = minLife;
            this.maxLife = maxLife;
            this.size = size;
            this.endSize = endSize;
            this.friction = friction;
            this.gravity = gravity;
            this.startColor = startColor;
            this.endColor = endColor;
            this.alpha = alpha;
            this.animated = animated;
        }
    }

    private final SpriteSet sprites;
    private final Style style;
    private final float baseSize;

    BuddhaParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                   SpriteSet sprites, Style style, RandomSource random) {
        super(level, x, y, z, vx, vy, vz, sprites.get(random));
        this.sprites = sprites;
        this.style = style;
        // La vitesse exacte demandée par le serveur (pas de bruit aléatoire) : cercles et ondes restent réguliers
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = style.minLife + (style.maxLife > style.minLife ? random.nextInt(style.maxLife - style.minLife + 1) : 0);
        this.baseSize = style.size * (0.85F + random.nextFloat() * 0.3F);
        this.quadSize = this.baseSize;
        this.friction = style.friction;
        this.gravity = style.gravity;
        this.hasPhysics = false; // l'énergie traverse les blocs : jamais bloquée ni écrasée contre un mur
        this.applyColor(0.0F);
        if (style.animated) {
            this.setSpriteFromAge(sprites);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        float t = Math.min(1.0F, (float) this.age / this.lifetime);
        // Taille : grossit (ou rétrécit) en douceur vers sa taille finale
        float eased = 1.0F - (1.0F - t) * (1.0F - t);
        this.quadSize = this.baseSize * (1.0F + (this.style.endSize - 1.0F) * eased);
        this.applyColor(t);
        if (this.style.animated) {
            this.setSpriteFromAge(this.sprites);
        }
    }

    /** Couleur et opacité à l'instant {@code t} (0 = naissance, 1 = fin) : fondu d'entrée rapide, fondu de sortie sur le dernier tiers. */
    private void applyColor(float t) {
        int a = this.style.startColor, b = this.style.endColor;
        float r = lerp(t, (a >> 16 & 0xFF), (b >> 16 & 0xFF)) / 255.0F;
        float g = lerp(t, (a >> 8 & 0xFF), (b >> 8 & 0xFF)) / 255.0F;
        float bl = lerp(t, (a & 0xFF), (b & 0xFF)) / 255.0F;
        this.setColor(r, g, bl);
        float fadeIn = Math.min(1.0F, t / 0.12F);
        float fadeOut = t < 0.66F ? 1.0F : Math.max(0.0F, (1.0F - t) / 0.34F);
        this.setAlpha(this.style.alpha * fadeIn * fadeOut);
    }

    private static float lerp(float t, float from, float to) {
        return from + (to - from) * t;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    // Pleine luminosité : l'énergie dorée brille même la nuit et dans les grottes. Les deux noms sont déclarés (sans @Override)
    // pour couvrir le nom de la méthode de lumière de cette version de Minecraft ; celui qui n'existe pas est simplement ignoré.
    public int getLightCoords(float partialTick) {
        return 0xF000F0;
    }

    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    /** Fabrique enregistrée côté client pour chaque type (voir BloxPieceClient). */
    public record Provider(SpriteSet sprites, Style style) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new BuddhaParticle(level, x, y, z, vx, vy, vz, this.sprites, this.style, random);
        }
    }
}
