import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Générateur des textures de la race Minks (pixel art reproductible) :
 * <ul>
 *   <li>textures/entity/minks_ears.png, minks_ears_sulong.png : oreilles de lapin (fourrure blanche ; Sulong : modèles et textures propres,
 *       intérieur rose) (MinksModels.EAR) ;</li>
 *   <li>textures/entity/minks_tail.png, minks_tail_sulong.png : queue duveteuse (MinksModels.TAIL) ;</li>
 *   <li>textures/entity/minks_glove_a.png, minks_glove_b.png : veines électriques des gants (MinksModels.GLOVE_*), alternées en jeu ;</li>
 *   <li>textures/gui/race/attack/minks_*.png : icônes 16x16 des quatre attaques (contour sombre, comme les autres races).</li>
 * </ul>
 * Lancement (JDK 25, depuis la racine du projet) : java src/blockbench/minks/MakeMinksTextures.java src/main/resources/assets/bloxpiece
 */
public class MakeMinksTextures {

    static final int OUTLINE = argb(255, 20, 18, 26);

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : "src/main/resources/assets/bloxpiece");
        File entity = new File(root, "textures/entity");
        File icons = new File(root, "textures/gui/race/attack");
        entity.mkdirs();
        icons.mkdirs();
        int[] white = {232, 232, 238}, snow = {244, 244, 250};
        ImageIO.write(ears(white, 11), "png", new File(entity, "minks_ears.png"));
        ImageIO.write(earsSulong(snow, 12), "png", new File(entity, "minks_ears_sulong.png"));
        ImageIO.write(tail(white, 13), "png", new File(entity, "minks_tail.png"));
        ImageIO.write(tailSulong(snow, 14), "png", new File(entity, "minks_tail_sulong.png"));
        ImageIO.write(glove(31), "png", new File(entity, "minks_glove_a.png"));
        ImageIO.write(glove(32), "png", new File(entity, "minks_glove_b.png"));
        ImageIO.write(icon(CLAWS), "png", new File(icons, "minks_electric_claws.png"));
        ImageIO.write(icon(BARRAGE), "png", new File(icons, "minks_claw_barrage.png"));
        ImageIO.write(icon(DANCE), "png", new File(icons, "minks_lightning_dance.png"));
        ImageIO.write(icon(SULONG), "png", new File(icons, "minks_sulong.png"));
        System.out.println("Textures Minks écrites dans " + root);
    }

    static int argb(int a, int r, int g, int b) {
        return (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static int shade(int[] c, int d) {
        return argb(255, c[0] + d, c[1] + d, c[2] + d);
    }

    interface FacePainter {
        int color(int face, int x, int y, int w, int h);
    }

    /** Six faces d'un cube {@code w x h x d} (dépliage UV de Minecraft en (u, v)) ; face 0 dessus, 1 dessous, 3 avant. */
    static void paintBox(BufferedImage img, int u, int v, int w, int h, int d, FacePainter p) {
        face(img, u + d, v, w, d, 0, p);
        face(img, u + d + w, v, w, d, 1, p);
        face(img, u, v + d, d, h, 2, p);
        face(img, u + d, v + d, w, h, 3, p);
        face(img, u + d + w, v + d, d, h, 4, p);
        face(img, u + 2 * d + w, v + d, w, h, 5, p);
    }

    static void face(BufferedImage img, int x0, int y0, int w, int h, int face, FacePainter p) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x0 + x < img.getWidth() && y0 + y < img.getHeight()) {
                    img.setRGB(x0 + x, y0 + y, p.color(face, x, y, w, h));
                }
            }
        }
    }

    /** Fourrure : grain de poils (pixels plus clairs et plus sombres), dessus éclairé, bords un peu plus sombres. */
    static FacePainter fur(int[] base, Random r) {
        return (face, x, y, w, h) -> {
            int d = (r.nextInt(5) - 2) * 7 + (face == 0 ? 14 : face == 1 ? -22 : face == 4 || face == 5 ? -8 : 0);
            if ((x == 0 || x == w - 1) && w > 1) {
                d -= 14;
            }
            if (r.nextInt(7) == 0) {
                d += 18; // poil clair
            }
            return shade(base, d);
        };
    }

    static BufferedImage ears(int[] fur, long seed) {
        BufferedImage img = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(seed);
        paintBox(img, 0, 0, 2, 5, 1, fur(fur, r));
        paintBox(img, 8, 0, 2, 4, 1, fur(fur, r));
        paintBox(img, 16, 0, 1, 1, 1, fur(fur, r));
        int[] pink = {240, 162, 178};
        paintBox(img, 0, 8, 1, 7, 1, (face, x, y, w, h) -> shade(pink, (r.nextInt(3) - 1) * 6 + (y == 0 || y == h - 1 ? -16 : 0)));
        return img;
    }

    static BufferedImage tail(int[] fur, long seed) {
        BufferedImage img = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(seed);
        int[] light = {Math.min(255, fur[0] + 30), Math.min(255, fur[1] + 30), Math.min(255, fur[2] + 30)};
        paintBox(img, 0, 0, 4, 4, 3, fur(light, r));
        paintBox(img, 0, 8, 3, 3, 3, fur(light, r));
        return img;
    }

    /** Oreille de Sulong (MinksModels.earSulong, 32x32) : fourrure blanche très fournie, intérieur rose, touffes. */
    static BufferedImage earsSulong(int[] fur, long seed) {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(seed);
        FacePainter shaggy = shaggy(fur, r);
        paintBox(img, 0, 0, 3, 7, 2, shaggy);
        paintBox(img, 12, 0, 3, 6, 2, shaggy);
        paintBox(img, 24, 0, 2, 2, 2, shaggy);
        int[] pink = {244, 170, 186};
        paintBox(img, 0, 10, 2, 11, 1, (face, x, y, w, h) -> shade(pink, (r.nextInt(3) - 1) * 6 + (y == 0 || y == h - 1 ? -14 : 0)
                + (x == 0 ? 8 : 0)));
        paintBox(img, 8, 10, 4, 2, 3, shaggy);
        paintBox(img, 24, 10, 2, 2, 2, shaggy);
        return img;
    }

    /** Queue de Sulong (MinksModels.tailSulong, 32x32) : pompon blanc très fourni. */
    static BufferedImage tailSulong(int[] fur, long seed) {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(seed);
        FacePainter shaggy = shaggy(fur, r);
        paintBox(img, 0, 0, 6, 6, 5, shaggy);
        paintBox(img, 0, 12, 4, 4, 4, shaggy);
        paintBox(img, 16, 12, 2, 3, 2, shaggy);
        return img;
    }

    /** Fourrure de Sulong : mèches (colonnes plus claires et plus sombres), reflets bleutés de l'électricité. */
    static FacePainter shaggy(int[] base, Random r) {
        return (face, x, y, w, h) -> {
            int d = (r.nextInt(5) - 2) * 6 + (face == 0 ? 10 : face == 1 ? -20 : face == 4 || face == 5 ? -6 : 0);
            d += (x % 2 == 0 ? 6 : -6);          // mèches
            if (r.nextInt(6) == 0) {
                d -= 22;                         // creux entre les mèches
            }
            if (r.nextInt(14) == 0) {
                return argb(255, base[0] - 30, base[1] - 4, 255); // reflet bleuté
            }
            return shade(base, d);
        };
    }

    /**
     * Gant électrique (32x32, deux images alternées en jeu) : fond translucide, réseau de veines électriques brisées opaques et
     * brillantes, noeuds plus lumineux. Blanc : teinté en bleu en jeu.
     */
    static BufferedImage glove(long seed) {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(seed);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int v = 150 + r.nextInt(30);
                img.setRGB(x, y, argb(95 + r.nextInt(40), v, v, v)); // champ électrique diffus
            }
        }
        // veines : chemins brisés qui parcourent la texture dans tous les sens
        for (int k = 0; k < 26; k++) {
            int x = r.nextInt(32), y = r.nextInt(32);
            int dx = r.nextBoolean() ? 1 : -1, dy = r.nextBoolean() ? 1 : -1;
            for (int s = 0; s < 10 + r.nextInt(10); s++) {
                img.setRGB(Math.floorMod(x, 32), Math.floorMod(y, 32), argb(255, 255, 255, 255));
                if (r.nextInt(3) == 0) {
                    int nx = Math.floorMod(x + (r.nextBoolean() ? 1 : -1), 32);
                    img.setRGB(nx, Math.floorMod(y, 32), argb(235, 225, 225, 225)); // épaisseur irrégulière
                }
                if (r.nextBoolean()) {
                    x += dx;
                } else {
                    y += dy;
                }
                if (r.nextInt(5) == 0) {
                    dx = -dx; // zigzag
                }
            }
        }
        return img;
    }

    // ------------------------------------------------------------------ icônes 16x16

    static final String[] CLAWS = {
            "................",
            "...Y....Y....Y..",
            "...WY...WY...WY.",
            "....W....W....W.",
            "....W....W....W.",
            "....WY...WY...WY",
            "...WWY..WWY..WWY",
            "..W.WY.W.WY.W.WY",
            "....GGGGGGGGGG..",
            "...GMMMMMMMMMMG.",
            "...GMYMMYMMYMMG.",
            "...GMMMMMMMMMMG.",
            "....GGGGGGGGGG..",
            ".....SSSSSSSS...",
            ".....SSSSSSSS...",
            "................"};

    static final String[] BARRAGE = {
            "................",
            "..W.....Y.......",
            ".W..LLL...Y.....",
            "...LSSSL..YY....",
            "W.LSSSSSWWWWYW..",
            "..LSSSSSWWWWW.Y.",
            "W.LSSSSSWWWYW...",
            "...LSSSL..Y.....",
            ".W..LLL..Y..LLL.",
            "..W.....Y..LSSSL",
            "...........LSSSL",
            "..........WLSSSL",
            ".........W.LSSSL",
            "............LLL.",
            "................",
            "................"};

    static final String[] DANCE = {
            "................",
            ".........YYW....",
            "........YYW.....",
            ".......YYW......",
            "..B...YYWWWWW...",
            ".BB..YYYYYYWW...",
            "BBB.....YYWW..O.",
            ".BB....YYWW..OOO",
            "..B...YYWW...OOO",
            ".....YYWW....OO.",
            "....YYW.....O..O",
            "...YYW.....O....",
            "..YW............",
            ".YW.............",
            "................",
            "................"};

    static final String[] SULONG = {
            "..W..........W..",
            ".WPW........WPW.",
            ".WPW........WPW.",
            ".WPW..YYYY..WPW.",
            ".WPW.Y....Y.WPW.",
            "..WWWWWWWWWWWW..",
            "..WWWWWWWWWWWW..",
            ".WWYYWWWWWWYYWW.",
            ".WWCYWWWWWWCYWW.",
            ".WWWWWWPPWWWWWW.",
            ".WWWWWWWWWWWWWW.",
            "..WWWWWWWWWWWW..",
            "Y..WWWWWWWWWW..Y",
            ".Y..WWWWWWWW..Y.",
            "..Y..........Y..",
            "................"};

    static int palette(char c) {
        return switch (c) {
            case 'Y' -> argb(255, 70, 180, 255);   // électricité bleue
            case 'W' -> argb(255, 250, 250, 236);  // éclat / fourrure blanche
            case 'C' -> argb(255, 140, 220, 255);  // yeux lumineux
            case 'G' -> argb(255, 40, 110, 255);   // gant : bleu intense
            case 'M' -> argb(255, 120, 200, 255);  // gant : bleu électrique
            case 'S' -> argb(255, 232, 232, 238);  // fourrure blanche
            case 'L' -> argb(255, 250, 250, 255);  // fourrure claire
            case 'P' -> argb(255, 240, 160, 178);  // rose
            case 'B' -> argb(255, 40, 110, 255);   // traînée bleu intense
            case 'O' -> argb(255, 232, 232, 238);  // silhouette
            default -> 0;
        };
    }

    static BufferedImage icon(String[] rows) {
        BufferedImage ic = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                ic.setRGB(x, y, palette(rows[y].charAt(x)));
            }
        }
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = ic.getRGB(x, y);
                if ((c >>> 24) != 0) {
                    out.setRGB(x, y, c);
                    continue;
                }
                boolean near = false;
                for (int dy = -1; dy <= 1 && !near; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if ((dx == 0 || dy == 0) && nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && (ic.getRGB(nx, ny) >>> 24) != 0) {
                            near = true;
                            break;
                        }
                    }
                }
                out.setRGB(x, y, near ? OUTLINE : 0);
            }
        }
        return out;
    }
}
