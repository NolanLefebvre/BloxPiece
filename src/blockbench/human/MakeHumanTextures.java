import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Générateur des textures de la race Humaine (pixel art, généré pour être reproductible) :
 * <ul>
 *   <li>textures/entity/human_rock.png : éclats de pierre des impacts (HumanModels.ROCK) ;</li>
 *   <li>textures/entity/human_dust.png : nuages de poussière en volume (HumanModels.DUST) ;</li>
 *   <li>textures/entity/human_fist.png : poing d'air géant du Poing du Conquérant (HumanModels.FIST), blanc à teinter ;</li>
 *   <li>textures/entity/human_ring.png, human_chunk.png, human_flame.png : anneau en blocs, voxel d'explosion et flamme pixel du
 *       Poing du Conquérant (HumanModels.RING, CHUNK, FLAME), blancs à teinter ;</li>
 *   <li>textures/gui/race/attack/human_*.png : icônes 16x16 des quatre attaques (contour sombre, comme celles du Cyborg).</li>
 * </ul>
 * Lancement (JDK 25, depuis la racine du projet) : java src/blockbench/human/MakeHumanTextures.java src/main/resources/assets/bloxpiece
 */
public class MakeHumanTextures {

    static final int OUTLINE = argb(255, 20, 18, 26);

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : "src/main/resources/assets/bloxpiece");
        File entity = new File(root, "textures/entity");
        File icons = new File(root, "textures/gui/race/attack");
        entity.mkdirs();
        icons.mkdirs();
        ImageIO.write(rock(), "png", new File(entity, "human_rock.png"));
        ImageIO.write(dust(), "png", new File(entity, "human_dust.png"));
        ImageIO.write(fist(), "png", new File(entity, "human_fist.png"));
        ImageIO.write(ring(), "png", new File(entity, "human_ring.png"));
        ImageIO.write(chunk(), "png", new File(entity, "human_chunk.png"));
        ImageIO.write(flame(), "png", new File(entity, "human_flame.png"));
        ImageIO.write(icon(IMPACT), "png", new File(icons, "human_lightning_impact.png"));
        ImageIO.write(icon(RUSH), "png", new File(icons, "human_destructive_rush.png"));
        ImageIO.write(icon(BREAKER), "png", new File(icons, "human_earth_breaker.png"));
        ImageIO.write(icon(CONQUEROR), "png", new File(icons, "human_conqueror_fist.png"));
        System.out.println("Textures Humaines écrites dans " + root);
    }

    static int argb(int a, int r, int g, int b) {
        return (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Une teinte de base, assombrie ou éclaircie de {@code d}. */
    static int shade(int[] rgb, int d, int alpha) {
        return argb(alpha, rgb[0] + d, rgb[1] + d, rgb[2] + d);
    }

    // ------------------------------------------------------------------ peinture d'un cube (dépliage UV de Minecraft)

    interface FacePainter {
        /** Couleur du pixel (x, y) d'une face de w x h ; {@code face} : 0 dessus, 1 dessous, 2 à 5 côtés. */
        int color(int face, int x, int y, int w, int h);
    }

    /** Peint les six faces d'un cube {@code w x h x d} dont le dépliage commence en (u, v). */
    static void paintBox(BufferedImage img, int u, int v, int w, int h, int d, FacePainter p) {
        face(img, u + d, v, w, d, 0, p);             // dessus
        face(img, u + d + w, v, w, d, 1, p);         // dessous
        face(img, u, v + d, d, h, 2, p);             // côté
        face(img, u + d, v + d, w, h, 3, p);         // avant
        face(img, u + d + w, v + d, d, h, 4, p);     // côté
        face(img, u + 2 * d + w, v + d, w, h, 5, p); // arrière
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

    static boolean border(int x, int y, int w, int h) {
        return x == 0 || y == 0 || x == w - 1 || y == h - 1;
    }

    // ------------------------------------------------------------------ éclats de pierre

    static BufferedImage rock() {
        BufferedImage img = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(11);
        int[] stone = {128, 122, 116};
        FacePainter p = (face, x, y, w, h) -> {
            int d = (r.nextInt(5) - 2) * 9;                 // grain de la pierre
            if (r.nextInt(9) == 0) {
                d -= 28;                                    // petites cavités
            }
            if (face == 0) {
                d += 22;                                    // dessus éclairé
            } else if (face == 1) {
                d -= 30;
            } else if (face == 4 || face == 5) {
                d -= 12;
            }
            if (border(x, y, w, h)) {
                d -= 26;                                    // arêtes marquées
            }
            return shade(stone, d, 255);
        };
        paintBox(img, 0, 0, 4, 4, 4, p);   // éclat principal 4x4x4
        paintBox(img, 0, 8, 2, 3, 2, p);   // excroissance 2x3x2
        paintBox(img, 16, 8, 3, 2, 2, p);  // second éclat 3x2x2
        return img;
    }

    // ------------------------------------------------------------------ poussière

    static BufferedImage dust() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(23);
        int[] dust = {196, 178, 150};
        FacePainter p = (face, x, y, w, h) -> {
            int d = (r.nextInt(7) - 3) * 6 + (face == 0 ? 14 : face == 1 ? -18 : 0);
            // bords plus clairsemés (nuage doux), coeur plus dense
            double cx = (x + 0.5) / w - 0.5, cy = (y + 0.5) / h - 0.5;
            double edge = Math.sqrt(cx * cx + cy * cy) * 2.0;
            int alpha = (int) (235 - 120 * edge) - r.nextInt(30);
            if (r.nextInt(6) == 0) {
                alpha -= 60; // trous dans le voile
            }
            return shade(dust, d, Math.max(60, alpha));
        };
        paintBox(img, 0, 0, 8, 8, 8, p);   // boule principale 8x8x8
        paintBox(img, 0, 16, 6, 6, 6, p);  // boules secondaires 6x6x6
        return img;
    }

    // ------------------------------------------------------------------ Poing du Conquérant (textures blanches, teintées en jeu)

    /**
     * Peinture « énergie » d'une face : dessus éclairé, dessous sombre, arêtes marquées, reflet d'un pixel en haut à gauche,
     * ombre d'un pixel en bas : le volume se lit même teinté et translucide.
     */
    static int energy(Random r, int face, int x, int y, int w, int h, int base) {
        int d = (r.nextInt(3) - 1) * 5;
        d += face == 0 ? 14 : face == 1 ? -26 : face == 4 || face == 5 ? -10 : 0;
        if (border(x, y, w, h)) {
            d -= 38;
        } else if (x == 1 && y == 1) {
            d += 30; // reflet
        } else if (y == h - 2) {
            d -= 14; // ombre portée en bas de la face
        }
        return shade(new int[] {base, base, base}, d, 255);
    }

    static BufferedImage fist() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(5);
        // dos de la main : tendons (lignes plus sombres) sur le dessus
        paintBox(img, 0, 0, 8, 8, 5, (face, x, y, w, h) -> {
            int c = energy(r, face, x, y, w, h, 232);
            if (face == 0 && x % 2 == 1 && y > 0 && y < h - 1 && !border(x, y, w, h)) {
                c = shade(new int[] {232, 232, 232}, -24, 255);
            }
            return c;
        });
        paintBox(img, 0, 16, 2, 3, 3, (face, x, y, w, h) -> energy(r, face, x, y, w, h, 240));  // phalanges du dessus
        paintBox(img, 12, 16, 2, 3, 2, (face, x, y, w, h) -> {                                   // phalanges repliées : ongle
            int c = energy(r, face, x, y, w, h, 236);
            return face == 3 && y == 0 ? shade(new int[] {255, 255, 255}, 0, 255) : c;
        });
        paintBox(img, 22, 16, 1, 1, 1, (face, x, y, w, h) -> shade(new int[] {255, 255, 255}, 0, 255)); // jointures : brillantes
        paintBox(img, 0, 24, 2, 3, 3, (face, x, y, w, h) -> energy(r, face, x, y, w, h, 226));  // pouce
        paintBox(img, 12, 24, 4, 2, 2, (face, x, y, w, h) -> energy(r, face, x, y, w, h, 232));
        // manchette : bande plus sombre, rivets brillants aux coins, filet clair au milieu
        paintBox(img, 28, 0, 7, 7, 2, (face, x, y, w, h) -> {
            int c = energy(r, face, x, y, w, h, 196);
            boolean rivet = (x == 1 || x == w - 2) && (y == 1 || y == h - 2) && w > 2 && h > 2;
            if (rivet) {
                return shade(new int[] {255, 255, 255}, 0, 255);
            }
            if (face >= 2 && h > 2 && y == h / 2) {
                return shade(new int[] {228, 228, 228}, 0, 255);
            }
            return c;
        });
        // avant-bras : stries de vitesse en biais (l'air file vers l'arrière), de plus en plus pâle
        paintBox(img, 0, 32, 6, 6, 6, (face, x, y, w, h) -> {
            int c = energy(r, face, x, y, w, h, 220);
            return (face != 3 && face != 5 && (x + y) % 3 == 0 && !border(x, y, w, h)) ? shade(new int[] {220, 220, 220}, -30, 255) : c;
        });
        paintBox(img, 28, 16, 5, 5, 4, (face, x, y, w, h) -> {
            int c = energy(r, face, x, y, w, h, 200);
            return (face != 3 && face != 5 && (x + y) % 3 == 1 && !border(x, y, w, h)) ? shade(new int[] {200, 200, 200}, -30, 255) : c;
        });
        return img;
    }

    /** Anneau en blocs : bande avec un filet brillant au centre et des encoches, rebord plus sombre. */
    static BufferedImage ring() {
        BufferedImage img = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(9);
        paintBox(img, 0, 0, 5, 2, 2, (face, x, y, w, h) -> {
            int d = (r.nextInt(3) - 1) * 4 + (face == 0 ? 12 : face == 1 ? -24 : 0);
            if (h >= 2 && (face == 2 || face == 3 || face == 4 || face == 5)) {
                d += y == 0 ? 18 : -10; // filet brillant en haut de la bande
            }
            if (x == 0 || x == w - 1) {
                d -= 34; // encoches entre les pièces
            }
            return shade(new int[] {234, 234, 234}, d, 255);
        });
        paintBox(img, 0, 8, 5, 1, 1, (face, x, y, w, h) -> shade(new int[] {196, 196, 196}, (x == 0 || x == w - 1) ? -30 : (r.nextInt(3) - 1) * 4, 255));
        return img;
    }

    /** Voxel d'explosion : coeur plus clair, bords plus sombres, grain de feu (pixels plus chauds ou plus froids). */
    static BufferedImage chunk() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(13);
        FacePainter p = (face, x, y, w, h) -> {
            double cx = (x + 0.5) / w - 0.5, cy = (y + 0.5) / h - 0.5;
            double edge = Math.max(Math.abs(cx), Math.abs(cy)) * 2.0;
            int d = (int) (18 - 46 * edge) + (r.nextInt(5) - 2) * 7 + (face == 0 ? 10 : face == 1 ? -18 : 0);
            if (r.nextInt(8) == 0) {
                d += 24; // étincelle
            }
            return shade(new int[] {226, 226, 226}, d, 255);
        };
        paintBox(img, 0, 0, 8, 8, 8, p);
        paintBox(img, 0, 16, 4, 4, 4, p);
        return img;
    }

    /** Flamme pixel : base dense et claire, sommet plus pâle, coeur brillant, bords sombres. */
    static BufferedImage flame() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(17);
        int[][] boxes = {{0, 0, 4, 4, 4}, {0, 8, 3, 4, 3}, {0, 16, 2, 3, 2}, {16, 16, 1, 2, 1}};
        for (int b = 0; b < boxes.length; b++) {
            int level = b;
            int[] bx = boxes[b];
            paintBox(img, bx[0], bx[1], bx[2], bx[3], bx[4], (face, x, y, w, h) -> {
                int d = 16 - level * 14 + (r.nextInt(3) - 1) * 6;
                if (w > 2 && (x == 0 || x == w - 1)) {
                    d -= 30;
                } else if (w > 2 && x == w / 2) {
                    d += 16; // coeur de la flamme
                }
                return shade(new int[] {230, 230, 230}, d, 255);
            });
        }
        return img;
    }

    // ------------------------------------------------------------------ icônes 16x16

    static final String[] IMPACT = {
            "................",
            "................",
            "............C...",
            "..LLLLLL..C..C..",
            ".LSSSSSSL..W....",
            "LSSSSSSSSWW.C...",
            "LSSSSSDDSWWWWWC.",
            "LSSSSSSSSWWW....",
            "LSSSSSDDSWW.C...",
            "LTTTSSSSS..W....",
            "LDTTTTDDS.C..C..",
            ".DDTTTSSD....C..",
            "..DDDDDD........",
            "................",
            "................",
            "................"};

    static final String[] RUSH = {
            "................",
            "................",
            "..........SSS...",
            ".........SLLSS..",
            "CCCC.....SSSSS..",
            "........OOOOO...",
            ".CCCCC.OOOOOOOWW",
            "......OOOOOOOOWW",
            "CCCC.OOOOOOOO.W.",
            "....OOOOOOOO....",
            ".CCC.OOOOOO.....",
            "......bb.bb.....",
            ".....bb...bb....",
            "....bb.....bb...",
            "................",
            "................"};

    static final String[] BREAKER = {
            "................",
            "....LLLLLLL.....",
            "...LSSSSSSSL....",
            "...LSSSSSSSTT...",
            "...LSSSSSSTTD...",
            "...LSSSSSSSTD...",
            "...LSDSSDSSDD...",
            "...LSDSSDSSD....",
            "....DDDDDDDD....",
            "..Y.WWWWWWWW.Y..",
            "BBBBWWWWWWWWBBBB",
            "BBBKBBKBBKBBKBBB",
            "bbKbbbKbbbKbbKbb",
            "bKbbbKbbbbbKbbbK",
            "bbbbbbbbbbbbbbbb",
            "................"};

    static final String[] CONQUEROR = {
            "....Y.....Y.....",
            "..Y..OOOOO...Y..",
            "...OOYYYYYOO....",
            "..OYLLLLLLYO..W.",
            ".OYLSSSSSSLYOWW.",
            ".OYSSSSSSSSYWWWW",
            "OYLSSSSSDDSSWWWW",
            "OYLSSSSSSSSSWWWW",
            "OYLSSSSSDDSSWWWW",
            ".OYTTTSSSSSYWWW.",
            ".OYDTTTTDDDYOWW.",
            "..OYDDDDDDYO..W.",
            "...OOYYYYYOO....",
            "..Y..OOOOO...Y..",
            "....Y.....Y.....",
            "................"};

    static int palette(char c) {
        return switch (c) {
            case 'S' -> argb(255, 232, 190, 150); // peau
            case 'L' -> argb(255, 250, 222, 188); // peau éclairée
            case 'D' -> argb(255, 176, 126, 90);  // peau dans l'ombre
            case 'T' -> argb(255, 206, 156, 116); // pouce
            case 'W' -> argb(255, 255, 255, 255); // éclat de l'impact
            case 'C' -> argb(255, 196, 232, 255); // air comprimé
            case 'Y' -> argb(255, 255, 214, 74);  // aura dorée
            case 'O' -> argb(255, 240, 128, 40);  // aura orangée / vêtement
            case 'B' -> argb(255, 128, 90, 56);   // terre
            case 'b' -> argb(255, 86, 58, 36);    // terre sombre
            case 'K' -> argb(255, 36, 24, 18);    // fissures
            default -> 0;
        };
    }

    /** Icône depuis ses 16 lignes, puis contour sombre autour de tout ce qui est dessiné (comme les icônes du Cyborg). */
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
