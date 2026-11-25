import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Générateur des textures de l'explosion du Radical Beam (race Cyborg), pixel art reproductible, blanc à teinter en jeu :
 * <ul>
 *   <li>textures/entity/cyborg_hex.png : plaques de l'anneau hexagonal (CyborgFxModels.HEX) : filet de circuit, encoches,
 *       rebord ;</li>
 *   <li>textures/entity/cyborg_plasma.png : cellule de plasma (CyborgFxModels.PLASMA) : coeur clair, grille de circuit.</li>
 * </ul>
 * Lancement (JDK 25, depuis la racine du projet) :
 * java src/blockbench/cyborg/MakeCyborgBlastTextures.java src/main/resources/assets/bloxpiece
 */
public class MakeCyborgBlastTextures {

    public static void main(String[] args) throws Exception {
        File entity = new File(new File(args.length > 0 ? args[0] : "src/main/resources/assets/bloxpiece"), "textures/entity");
        entity.mkdirs();
        ImageIO.write(hex(), "png", new File(entity, "cyborg_hex.png"));
        ImageIO.write(plasma(), "png", new File(entity, "cyborg_plasma.png"));
        System.out.println("Textures de l'explosion Cyborg écrites dans " + entity);
    }

    static int grey(int v) {
        v = Math.max(0, Math.min(255, v));
        return (255 << 24) | (v << 16) | (v << 8) | v;
    }

    interface FacePainter {
        int color(int face, int x, int y, int w, int h);
    }

    /** Six faces d'un cube {@code w x h x d} dont le dépliage UV (Minecraft) commence en (u, v) ; face 0 dessus, 1 dessous. */
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

    /** Plaques : métal clair, filet de circuit brillant au milieu, encoches aux jonctions, dessous sombre. */
    static BufferedImage hex() {
        BufferedImage img = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(41);
        paintBox(img, 0, 0, 7, 2, 2, (face, x, y, w, h) -> {
            int v = 214 + (r.nextInt(3) - 1) * 5 + (face == 0 ? 14 : face == 1 ? -30 : 0);
            if (x == 0 || x == w - 1) {
                v -= 40; // jonction entre deux plaques
            } else if (face >= 2 && x % 3 == 1) {
                v += 30; // points du circuit
            }
            return grey(v);
        });
        paintBox(img, 0, 8, 7, 1, 1, (face, x, y, w, h) -> grey(178 + ((x == 0 || x == w - 1) ? -34 : (x % 2 == 0 ? 14 : 0))));
        return img;
    }

    /** Cellule de plasma : coeur clair, bords plus sombres, grille de circuit tous les 3 pixels, étincelles. */
    static BufferedImage plasma() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(43);
        FacePainter p = (face, x, y, w, h) -> {
            double cx = (x + 0.5) / w - 0.5, cy = (y + 0.5) / h - 0.5;
            double edge = Math.max(Math.abs(cx), Math.abs(cy)) * 2.0;
            int v = (int) (240 - 50 * edge) + (r.nextInt(3) - 1) * 6 + (face == 0 ? 8 : face == 1 ? -20 : 0);
            if (w >= 6 && (x % 3 == 0 || y % 3 == 0)) {
                v -= 22; // grille de circuit
            }
            if (r.nextInt(10) == 0) {
                v += 30; // étincelle
            }
            return grey(v);
        };
        paintBox(img, 0, 0, 6, 6, 6, p);
        paintBox(img, 0, 12, 3, 3, 3, p);
        return img;
    }
}
