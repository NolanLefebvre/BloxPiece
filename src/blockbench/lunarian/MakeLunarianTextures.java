import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Générateur des textures de la race Lunarian (pixel art reproductible) :
 * <ul>
 *   <li>textures/entity/lunarian_wing.png : atlas des plumes NOIRES détourées des ailes (LunarianModels.WING) : couvertures,
 *       rémiges, grandes rémiges primaires, os du bord d'attaque ;</li>
 *   <li>textures/entity/lunarian_flame_{core,body,edge}_{0,1}.png : volumes de feu en trois couches, deux variantes (LunarianModels.FLAME) ;</li>
 *   <li>textures/entity/lunarian_feather.png : plume INCANDESCENTE (projectile) : coeur jaune pâle, rouge et orange vers les
 *       bords, rachis blanc-jaune ;</li>
 *   <li>textures/gui/race/attack/lunarian_*.png : icônes 16x16 des quatre capacités (contour sombre, comme les autres races).</li>
 * </ul>
 * Lancement (JDK 25, depuis la racine du projet) : java src/blockbench/lunarian/MakeLunarianTextures.java src/main/resources/assets/bloxpiece
 */
public class MakeLunarianTextures {

    static final int OUTLINE = argb(255, 18, 14, 16);

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : "src/main/resources/assets/bloxpiece");
        File entity = new File(root, "textures/entity");
        File icons = new File(root, "textures/gui/race/attack");
        entity.mkdirs();
        icons.mkdirs();
        ImageIO.write(wing(5), "png", new File(entity, "lunarian_wing.png"));
        ImageIO.write(feather(6), "png", new File(entity, "lunarian_feather.png"));
        String[] layers = {"core", "body", "edge"};
        for (int l = 0; l < 3; l++) {
            for (int v = 0; v < 2; v++) {
                ImageIO.write(fireCube(l, v), "png", new File(entity, "lunarian_flame_" + layers[l] + "_" + v + ".png"));
            }
        }
        ImageIO.write(iconFlames(), "png", new File(icons, "lunarian_flame_mode.png"));
        ImageIO.write(iconFeathers(), "png", new File(icons, "lunarian_burning_feathers.png"));
        ImageIO.write(iconFist(), "png", new File(icons, "lunarian_blazing_fist.png"));
        ImageIO.write(iconStorm(), "png", new File(icons, "lunarian_celestial_storm.png"));
        System.out.println("Textures Lunarian écrites dans " + root);
    }

    static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // ------------------------------------------------------------------ ailes : atlas des plumes VOXEL

    /**
     * Étages de plumes (LunarianModels.WING) : {u, v, largeur, longueur} ; chaque plume est une bande de 1 px d'épaisseur, son
     * dépliage occupe (2 x (1 + largeur)) x (1 + longueur) pixels. Les longueurs sont celles AFFICHÉES en jeu (aucun étirement) :
     * A petites couvertures, B couvertures, C moyennes, D et D2 longues (courtes / longues), E et E2 grandes rémiges.
     */
    static final int[][] TIERS = {{0, 0, 3, 4}, {10, 0, 3, 6}, {20, 0, 4, 9}, {32, 0, 4, 11}, {44, 0, 4, 14}, {56, 0, 4, 14}, {68, 0, 4, 17}};

    /**
     * Atlas des ailes (références : ailes voxel noires) : pour chaque étage, la bande de plume dépliée : face avant et face
     * arrière en dégradé du noir violacé (attache) vers un gris violet plus clair (bout), reflet clair sur un bord, ombre sur
     * l'autre, fines barbes en diagonale ; tranches sombres. En bas : l'os du bord d'attaque (petites plumes serrées) et le
     * collier d'attache (métal sombre).
     */
    static BufferedImage wing(long seed) {
        Random r = new Random(seed);
        BufferedImage img = new BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB);
        for (int[] t : TIERS) {
            int u = t[0], v = t[1], w = t[2], len = t[3];
            for (int y = 0; y <= len; y++) {
                for (int x = 0; x < 2 * (1 + w); x++) {
                    int col;
                    double k = Math.max(0, y - 1) / (double) Math.max(1, len - 1);
                    int base = 26 + (int) (34 * k);
                    boolean front = x >= 1 && x < 1 + w, back = x >= 2 + w;
                    boolean tip = y >= len - 3; // rangées des marches du bout : toutes les colonnes couleur de plume (leurs faces tombent dessus)
                    if (y == 0) {
                        col = shadeP(base - 8, r); // dessus et dessous de la bande
                    } else if (front || back || tip) {
                        int fx = front ? x - 1 : back ? (2 * (1 + w) - 1 - x) : 1; // même dessin des deux côtés (miroir)
                        int d = fx == 0 ? 16 : fx == w - 1 ? -9 : ((fx + y) % 3 == 0 ? -4 : 0);
                        if (w >= 4 && fx == 1) {
                            d += 7; // rachis clair
                        }
                        col = shadeP(base + d, r);
                    } else {
                        col = shadeP(base - 12, r); // tranches
                    }
                    img.setRGB(u + x, v + y, col);
                }
            }
        }
        // os du bord d'attaque (pièce de 14 x 2,6 x 1,5 px) : petites plumes serrées, noir profond
        for (int y = 40; y < 46; y++) {
            for (int x = 0; x < 40; x++) {
                int s = ((x + (y % 2) * 2) % 4 == 0 || y % 3 == 0) ? -7 : 0;
                img.setRGB(x, y, shadeP(32 + s, r));
            }
        }
        // collier d'attache (cube de 3 px) : métal sombre, liseré clair
        for (int y = 48; y < 54; y++) {
            for (int x = 40; x < 52; x++) {
                int d = (y == 48 || y == 53) ? 18 : (x % 3 == 0 ? -6 : 4);
                img.setRGB(x, y, argb(255, 52 + d, 50 + d, 60 + d));
            }
        }
        return img;
    }

    /** Noir violacé de la valeur {@code v} (léger grain). */
    static int shadeP(int v, Random r) {
        v += r.nextInt(3) - 1;
        return argb(255, v - 1, v - 4, v + 16);
    }

    // ------------------------------------------------------------------ feu : volumes (cube de 16 px, trois couches, deux variantes)

    /**
     * Texture d'un VOLUME DE FEU (cube de 16 px : dépliage 64 x 32 de Minecraft) : {@code layer} 0 = COEUR (blanc-jaune vif, grain
     * fin), 1 = CORPS (jaune, orange et rouge mêlés en volutes), 2 = BORD (rouge et braise sombre, TROUÉ : des pixels
     * transparents découpent un contour de feu irrégulier) ; {@code v} : variante (les volumes alternent pour scintiller).
     */
    static BufferedImage fireCube(int layer, int v) {
        Random r = new Random(500 + layer * 31L + v * 977L);
        BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        int[][] core = {{255, 252, 228}, {255, 240, 160}, {255, 222, 110}};
        int[][] body = {{255, 230, 110}, {255, 186, 52}, {255, 140, 32}, {236, 92, 22}};
        int[][] edge = {{226, 70, 20}, {184, 42, 14}, {130, 24, 10}};
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                double w = Math.sin(x * 0.7 + y * 0.45 + v * 2.1) + Math.sin(x * 0.23 - y * 0.9 + v) + (r.nextDouble() - 0.5) * 0.9;
                int[] c;
                int a = 255;
                if (layer == 0) {
                    c = core[w > 0.6 ? 0 : w > -0.6 ? 1 : 2];
                } else if (layer == 1) {
                    c = body[w > 1.0 ? 0 : w > 0.0 ? 1 : w > -0.9 ? 2 : 3];
                } else {
                    c = edge[w > 0.5 ? 0 : w > -0.6 ? 1 : 2];
                    if (r.nextInt(100) < 30 + (int) (w * 12)) {
                        a = 0; // trou : contour irrégulier
                    }
                }
                img.setRGB(x, y, argb(a, c[0], c[1], c[2]));
            }
        }
        return img;
    }

    /**
     * Plume de PHÉNIX (LunarianModels.FEATHER, 64 x 32) : zones lues par les pièces du modèle :
     * <ul>
     *   <li>(0, 0) 16 x 8 : barbe intérieure (le relief du milieu de la plume) : jaune vif mêlé de blanc chaud, barbes en diagonale ;</li>
     *   <li>(0, 8) 16 x 8 : barbe extérieure : orange qui vire au rouge vers le bord, barbes plus sombres ;</li>
     *   <li>(16, 0) 40 x 20 : rachis : blanc-jaune incandescent avec de légères pulsations ;</li>
     *   <li>(0, 16) 16 x 16 : bout et pointes : rouge et braise sombre.</li>
     * </ul>
     */
    static BufferedImage feather(long seed) {
        Random r = new Random(seed);
        BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        int[][] inner = {{255, 252, 214}, {255, 232, 120}, {255, 206, 70}};
        int[][] outer = {{255, 170, 40}, {250, 124, 28}, {226, 74, 20}, {176, 40, 14}};
        int[][] core = {{255, 255, 236}, {255, 244, 190}};
        int[][] tip = {{226, 70, 20}, {170, 36, 12}, {120, 22, 10}};
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                int[] c;
                int n = r.nextInt(3);
                if (x < 16 && y < 8) {
                    c = inner[(x + y) % 4 == 0 ? 2 : n == 0 ? 0 : 1];
                } else if (x < 16 && y < 16) {
                    int k = Math.min(3, (y - 8) / 2 + ((x + y) % 3 == 0 ? 1 : 0));
                    c = outer[k];
                } else if (x < 16) {
                    c = tip[(x * 3 + y) % 5 == 0 ? 2 : n == 0 ? 0 : 1];
                } else if (y < 20) {
                    c = core[(x + y) % 7 == 0 ? 1 : 0];
                } else {
                    c = outer[1];
                }
                int d = r.nextInt(11) - 5;
                img.setRGB(x, y, argb(255, c[0] + d, c[1] + d, c[2] + d / 2));
            }
        }
        return img;
    }


    // ------------------------------------------------------------------ icônes

    static final int BLACK = argb(255, 30, 28, 32), GREY = argb(255, 74, 72, 78), RED = argb(255, 222, 58, 22),
            ORANGE = argb(255, 255, 140, 30), YELLOW = argb(255, 255, 214, 70), WHITE = argb(255, 255, 248, 210),
            SKIN = argb(255, 226, 190, 150);

    static BufferedImage canvas() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    static void px(BufferedImage img, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < 16 && y < 16) {
            img.setRGB(x, y, c);
        }
    }

    static BufferedImage outline(BufferedImage img) {
        BufferedImage out = canvas();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    out.setRGB(x, y, img.getRGB(x, y));
                    continue;
                }
                int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] d : n) {
                    int nx = x + d[0], ny = y + d[1];
                    if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && (img.getRGB(nx, ny) >>> 24) != 0) {
                        out.setRGB(x, y, OUTLINE);
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** Une langue de flamme de hauteur h posée en (x, base). */
    static void flame(BufferedImage img, int x, int base, int h) {
        for (int i = 0; i < h; i++) {
            int w = Math.max(0, (h - i) / 3);
            for (int dx = -w; dx <= w; dx++) {
                px(img, x + dx, base - i, i > h * 2 / 3 ? RED : Math.abs(dx) == w ? ORANGE : i < h / 3 ? WHITE : YELLOW);
            }
        }
    }

    /** Deux ailes noires et une flamme entre elles. */
    static BufferedImage iconFlames() {
        BufferedImage img = canvas();
        for (int i = 0; i < 6; i++) {
            for (int y = 4 + i; y < 13; y++) {
                px(img, 1 + i, y, i % 2 == 0 ? BLACK : GREY);
                px(img, 14 - i, y, i % 2 == 0 ? BLACK : GREY);
            }
        }
        flame(img, 8, 13, 11);
        return outline(img);
    }

    /** Trois plumes de feu en diagonale. */
    static BufferedImage iconFeathers() {
        BufferedImage img = canvas();
        int[][] f = {{3, 12}, {7, 9}, {11, 6}};
        for (int[] p : f) {
            for (int i = 0; i < 5; i++) {
                px(img, p[0] + i, p[1] - i, i == 4 ? WHITE : YELLOW);
                px(img, p[0] + i - 1, p[1] - i, ORANGE);
                px(img, p[0] + i, p[1] - i + 1, RED);
            }
        }
        return outline(img);
    }

    /** Poing en feu. */
    static BufferedImage iconFist() {
        BufferedImage img = canvas();
        flame(img, 4, 13, 9);
        flame(img, 7, 13, 11);
        for (int y = 7; y <= 12; y++) {
            for (int x = 8; x <= 13; x++) {
                px(img, x, y, y == 12 || x == 13 ? argb(255, 180, 140, 104) : SKIN);
            }
        }
        return outline(img);
    }

    /** Tornade de feu. */
    static BufferedImage iconStorm() {
        BufferedImage img = canvas();
        for (int y = 1; y < 15; y++) {
            int w = 1 + (14 - y) / 3 + (y > 12 ? 2 : 0);
            int sh = (int) Math.round(Math.sin(y * 0.8) * 1.2);
            for (int dx = -w; dx <= w; dx++) {
                px(img, 8 + dx + sh, y, (dx + y) % 3 == 0 ? YELLOW : Math.abs(dx) == w ? RED : ORANGE);
            }
        }
        px(img, 3, 4, ORANGE);
        px(img, 13, 6, YELLOW);
        return outline(img);
    }
}
