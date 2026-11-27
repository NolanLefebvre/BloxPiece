import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Générateur des textures de la race Fishman (pixel art reproductible) :
 * <ul>
 *   <li>textures/entity/fishman_fin.png : membrane des ailerons (FishmanModels) : bandes de bleus du plus profond (base) au plus
 *       clair (bord), rayons de nageoire plus sombres, liseré clair, petites écailles ;</li>
 *   <li>textures/entity/fishman_water.png : eau des effets (cube de 16 px) : bleus mêlés, reflets en vagues, translucide ;</li>
 *   <li>textures/entity/fishman_foam.png : écume (crêtes des vagues, éclaboussures) ;</li>
 *   <li>textures/gui/race/attack/fishman_*.png : icônes 16x16 des quatre attaques (contour sombre, comme les autres races).</li>
 * </ul>
 * Lancement (JDK 25, depuis la racine du projet) : java src/blockbench/fishman/MakeFishmanTextures.java src/main/resources/assets/bloxpiece
 */
public class MakeFishmanTextures {

    static final int OUTLINE = argb(255, 16, 22, 34);

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : "src/main/resources/assets/bloxpiece");
        File entity = new File(root, "textures/entity");
        File icons = new File(root, "textures/gui/race/attack");
        entity.mkdirs();
        icons.mkdirs();
        ImageIO.write(fin(7), "png", new File(entity, "fishman_fin.png"));
        ImageIO.write(water(11), "png", new File(entity, "fishman_water.png"));
        ImageIO.write(foam(12), "png", new File(entity, "fishman_foam.png"));
        ImageIO.write(iconWave(), "png", new File(icons, "fishman_aqua_wave.png"));
        ImageIO.write(iconSpear(), "png", new File(icons, "fishman_water_spear.png"));
        ImageIO.write(iconTide(), "png", new File(icons, "fishman_devastating_wave.png"));
        ImageIO.write(iconSpiral(), "png", new File(icons, "fishman_abyss_piercer.png"));
        System.out.println("Textures Fishman écrites dans " + root);
    }

    static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static int[] mix(int[] a, int[] b, double k) {
        return new int[] {(int) (a[0] + (b[0] - a[0]) * k), (int) (a[1] + (b[1] - a[1]) * k), (int) (a[2] + (b[2] - a[2]) * k)};
    }

    /**
     * Membrane : bandes horizontales de 4 px (chaque rangée d'aileron a sa bande) qui vont du bleu profond (v = 0 : base) au
     * bleu-cyan clair (bas de l'image : bords) ; rayons verticaux plus sombres tous les 3 px ; liseré clair en haut de chaque
     * bande ; grain d'écailles.
     */
    static BufferedImage fin(long seed) {
        Random r = new Random(seed);
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int[] deep = {20, 58, 138}, mid = {38, 118, 206}, light = {104, 196, 238};
        for (int y = 0; y < 64; y++) {
            int band = y / 4;
            double k = band / 15.0;
            int[] c = k < 0.5 ? mix(deep, mid, k * 2) : mix(mid, light, (k - 0.5) * 2);
            for (int x = 0; x < 64; x++) {
                int d = (r.nextInt(9) - 4);
                if (x % 3 == 0) {
                    d -= 26; // rayon de nageoire
                } else if (x % 3 == 1) {
                    d += 6;
                }
                if (y % 4 == 0) {
                    d += 18; // liseré
                }
                if (r.nextInt(11) == 0) {
                    d += 14; // reflet d'écaille
                }
                img.setRGB(x, y, argb(255, c[0] + d, c[1] + d, c[2] + d));
            }
        }
        return img;
    }

    /** Eau : bleus mêlés en vagues, reflets clairs, quelques bulles ; alpha partiel (translucide). */
    static BufferedImage water(long seed) {
        Random r = new Random(seed);
        BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        int[] dark = {22, 84, 176}, base = {44, 132, 222}, hi = {150, 214, 250};
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                double w = Math.sin(x * 0.55 + y * 0.9) + 0.6 * Math.sin(x * 0.23 - y * 1.7) + (r.nextDouble() - 0.5) * 0.5;
                int[] c = w > 1.05 ? hi : w > -0.4 ? mix(base, hi, Math.max(0, (w - 0.3) * 0.4)) : mix(dark, base, (w + 1.6) / 1.2);
                int a = w > 1.05 ? 225 : 185;
                if (r.nextInt(40) == 0) { // petite bulle
                    c = new int[] {205, 238, 255};
                    a = 235;
                }
                img.setRGB(x, y, argb(a, c[0], c[1], c[2]));
            }
        }
        return img;
    }

    /** Écume : blanc bleuté, grain, quelques trous plus transparents. */
    static BufferedImage foam(long seed) {
        Random r = new Random(seed);
        BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                int d = r.nextInt(22) - 11;
                boolean hole = r.nextInt(7) == 0;
                int[] c = hole ? new int[] {170, 214, 244} : new int[] {236 + d / 2, 246 + d / 3, 255};
                img.setRGB(x, y, argb(hole ? 150 : 240, c[0], c[1], c[2]));
            }
        }
        return img;
    }

    // ------------------------------------------------------------------ icônes 16x16

    static final int W1 = argb(255, 30, 96, 196), W2 = argb(255, 64, 156, 236), W3 = argb(255, 170, 224, 252), FOAM = argb(255, 240, 250, 255);
    static final int SKIN = argb(255, 226, 196, 120), SKIN_D = argb(255, 176, 140, 78);

    static BufferedImage canvas() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    static void px(BufferedImage img, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < 16 && y < 16) {
            img.setRGB(x, y, c);
        }
    }

    /** Contour sombre autour de tous les pixels opaques. */
    static BufferedImage outline(BufferedImage img) {
        BufferedImage out = canvas();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    out.setRGB(x, y, img.getRGB(x, y));
                    continue;
                }
                boolean near = false;
                for (int dy = -1; dy <= 1 && !near; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && (img.getRGB(nx, ny) >>> 24) != 0
                                && Math.abs(dx) + Math.abs(dy) == 1) {
                            near = true;
                            break;
                        }
                    }
                }
                if (near) {
                    out.setRGB(x, y, OUTLINE);
                }
            }
        }
        return out;
    }

    /** Onde aquatique : poing à gauche, arcs d'eau qui partent vers la droite. */
    static BufferedImage iconWave() {
        BufferedImage img = canvas();
        for (int y = 6; y <= 9; y++) {
            for (int x = 2; x <= 5; x++) {
                px(img, x, y, y == 9 || x == 2 ? SKIN_D : SKIN);
            }
        }
        for (int i = 0; i < 3; i++) {
            int cx = 7 + i * 2, h = 2 + i;
            for (int y = 8 - h; y <= 7 + h; y++) {
                px(img, cx + (Math.abs(y - 7) > h - 1 ? -1 : 0), y, i == 2 ? W3 : i == 1 ? W2 : W1);
            }
        }
        px(img, 13, 3, FOAM);
        px(img, 13, 12, FOAM);
        px(img, 14, 7, FOAM);
        return outline(img);
    }

    /** Lance aquatique : gouttes en diagonale avec traînées. */
    static BufferedImage iconSpear() {
        BufferedImage img = canvas();
        int[][] drops = {{11, 3}, {12, 8}, {7, 6}, {9, 11}, {4, 10}};
        for (int[] d : drops) {
            px(img, d[0], d[1], W3);
            px(img, d[0] + 1, d[1] - 1, W2);
            px(img, d[0] - 1, d[1] + 1, W2);
            px(img, d[0] - 2, d[1] + 2, W1);
        }
        return outline(img);
    }

    /** Vague dévastatrice : grande vague qui s'enroule, écume en crête. */
    static BufferedImage iconTide() {
        BufferedImage img = canvas();
        for (int x = 1; x <= 14; x++) {
            int top = (int) Math.round(12 - 7 * Math.sin(Math.PI * Math.min(1.0, x / 11.0)));
            for (int y = top; y <= 13; y++) {
                px(img, x, y, y < top + 2 ? W2 : y > 11 ? W1 : (x + y) % 4 == 0 ? W3 : W2);
            }
            px(img, x, top, FOAM);
        }
        px(img, 10, 4, FOAM);
        px(img, 9, 5, FOAM);
        px(img, 11, 6, W3);
        return outline(img);
    }

    /** Perforation des profondeurs : spirale d'eau. */
    static BufferedImage iconSpiral() {
        BufferedImage img = canvas();
        for (double a = 0; a < Math.PI * 5; a += 0.12) {
            double rr = 0.6 + a * 0.38;
            int x = (int) Math.round(8 + Math.cos(a) * rr), y = (int) Math.round(8 + Math.sin(a) * rr);
            px(img, x, y, a > 12 ? W1 : a > 6 ? W2 : W3);
        }
        px(img, 8, 8, FOAM);
        return outline(img);
    }
}
