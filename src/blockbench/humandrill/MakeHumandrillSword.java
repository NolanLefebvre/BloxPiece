import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * Sabre des Humandrills (modèle 3D original, même qualité de construction que l'épée Ace mais dessin différent) :
 * <ul>
 *   <li>longue lame de cimeterre nettement courbe, qui S'ÉLARGIT vers le dernier tiers (ventre de lame) avant une pointe
 *       effilée qui suit la courbe ; acier usé : dos sombre piqué de rouille, gorge (rainure) le long du dos, ligne de trempe
 *       ondulée, tranchant clair ébréché ;</li>
 *   <li>garde ronde (anneau doré plat en deux étages, rebord clair, creux sombre, moyeu) encadrée de deux rondelles ; collet
 *       doré à la base de la lame ;</li>
 *   <li>longue poignée tressée (cordon sombre croisé en losanges sur peau claire) avec quatre bourrelets de tresse ; pommeau
 *       de fer à bague dorée. Pas de branche de garde (contrairement à Ace).</li>
 * </ul>
 * Mêmes conventions que models/item/ace.json (axe de l'épée en x = z = 8 le long de y, diagonale d'icône à -45° autour de z,
 * poignée au même endroit) : mêmes cadrages en main, validés en jeu pour Ace. Sorties : models/item/humandrill_sword.json,
 * items/humandrill_sword.json, textures/item/humandrill_sword_{blade,gold,guard,grip,iron}.png, et un aperçu (silhouette
 * de face) dans le dossier donné en 2e argument.
 * <p>
 * Lancement (JDK 25, depuis la racine du projet) :
 * java src/blockbench/humandrill/MakeHumandrillSword.java src/main/resources/assets/bloxpiece [dossier_apercu]
 */
public class MakeHumandrillSword {

    static final String ID = "humandrill_sword";

    record El(double[] f, double[] t, String tx, String c, double angle, double[] origin, double[] uv, String[] faces) {
    }

    static final List<El> E = new ArrayList<>();
    static final double AX = 8.0, AZ = 8.0;

    static void el(double[] f, double[] t, String tx, String c) {
        E.add(new El(f, t, tx, c, -45.0, new double[] {8, 8, 8}, null, null));
    }

    static void el(double[] f, double[] t, String tx, String c, double angle, double[] origin, double[] uv, String... faces) {
        E.add(new El(f, t, tx, c, angle, origin, uv, faces.length == 0 ? null : faces));
    }

    static double[] v(double x, double y, double z) {
        return new double[] {r4(x), r4(y), r4(z)};
    }

    static double r4(double x) {
        return Math.round(x * 10000.0) / 10000.0;
    }

    static double r3(double x) {
        return Math.round(x * 1000.0) / 1000.0;
    }

    static double[] rot2(double x, double y, double deg) {
        double a = Math.toRadians(deg);
        return new double[] {x * Math.cos(a) - y * Math.sin(a), x * Math.sin(a) + y * Math.cos(a)};
    }

    /** Une seule rotation (angle, centre) = alpha autour de joint, puis -45° autour de (8, 8) (diagonale d'icône). Comme Ace. */
    static Object[] combined(double alpha, double[] joint) {
        double theta = -45.0 + alpha;
        double px = joint[0], py = joint[1];
        double[] r = rot2(px, py, alpha);
        double[] c = rot2(px - r[0] - 8, py - r[1] - 8, -45.0);
        double cx = c[0] + 8, cy = c[1] + 8;
        double a = Math.toRadians(theta);
        double m00 = 1 - Math.cos(a), m01 = Math.sin(a), m10 = -Math.sin(a), m11 = 1 - Math.cos(a);
        double det = m00 * m11 - m01 * m10;
        return new Object[] {r3(theta), new double[] {r4((cx * m11 - m01 * cy) / det), r4((m00 * cy - m10 * cx) / det), 8.0}};
    }

    /** Disque octogonal (deux pavés croisés) centré sur l'axe. */
    static void disc(double y0, double y1, double d, String c, String tx) {
        double r = d / 2, k = d / 2 * 0.62;
        el(v(AX - r, y0, AZ - k), v(AX + r, y1, AZ + k), tx, c);
        el(v(AX - k, y0, AZ - r), v(AX + k, y1, AZ + r), tx, c);
    }

    // ---------------------------------------------------------------- géométrie
    static final double W0 = 1.35, HALF = W0 / 2;   // largeur de la lame à la base ; le dos reste à -HALF de l'axe
    static final double BAND = 7.0 / 16.0 * W0;     // moitié sombre (dos)

    static double widthAt(double u) {                // u = 0 (base) .. 1 (fin du corps) : la lame s'élargit vers le bout
        return W0 * (1.0 + 0.24 * Math.pow(u, 1.6));
    }

    static void build() {
        // pommeau : bouton et chapeau de fer, bague dorée
        disc(-3.6, -3.4, 0.75, "pommeau : bouton", "iron");
        disc(-3.4, -2.97, 1.35, "pommeau : chapeau", "iron");
        disc(-2.97, -2.8, 1.5, "pommeau : bague", "gold");
        // poignée tressée et ses bourrelets de tresse
        el(v(AX - 0.48, -2.8, AZ - 0.48), v(AX + 0.48, 1.25, AZ + 0.48), "grip", "poignée");
        for (double y : new double[] {-2.4, -1.45, -0.5, 0.45}) {
            el(v(AX - 0.55, y, AZ - 0.55), v(AX + 0.55, y + 0.24, AZ + 0.55), "grip", "tresse");
        }
        // garde ronde : rondelle, anneau plat en deux étages (le plus large côté main), rondelle, collet
        disc(1.25, 1.4, 1.7, "rondelle", "gold");
        disc(1.4, 1.64, 4.0, "garde ronde", "guard");
        disc(1.64, 1.8, 3.3, "garde ronde : étage", "guard");
        disc(1.8, 1.94, 1.75, "rondelle", "gold");
        el(v(AX - HALF - 0.12, 1.94, AZ - 0.3), v(AX - HALF + W0 + 0.12, 2.75, AZ + 0.3), "gold", "collet");

        // lame : pans courbés progressivement, de plus en plus larges vers le bout (dos aligné)
        double bodyLen = 19.0, curve = 24.0, tipLen = 5.2, tipCurve = 3.2;
        int nseg = 14;
        double seg = bodyLen / nseg;
        double[] joint = {AX, 2.6};
        for (int k = 0; k < nseg; k++) {
            double alpha = curve * Math.pow((double) k / nseg, 2);
            Object[] ao = combined(alpha, joint);
            double w = widthAt((k + 0.5) / nseg);
            double z = 0.17 + (k % 2 == 1 ? 0.002 : 0.0);
            double lo = k == 0 ? 0.0 : -0.06;
            double v0 = r3((k * 5.3) % 6);
            el(v(joint[0] - HALF, joint[1] + lo, AZ - z), v(joint[0] - HALF + w, joint[1] + seg, AZ + z), "blade", "lame",
                    (double) ao[0], (double[]) ao[1], new double[] {0, 16, v0, v0 + 10}, "north", "south", "east", "west");
            double[] d = rot2(0.0, seg, alpha);
            joint = new double[] {joint[0] + d[0], joint[1] + d[1]};
        }
        // pointe : la largeur se referme (le tranchant remonte vers le dos), en suivant la courbe
        double alpha = curve * Math.pow((nseg - 1.0) / nseg, 2);
        double wEnd = widthAt(1.0);
        List<Double> cuts = new ArrayList<>();
        cuts.add(0.0);
        double t = 0.0;
        double tb = Math.sqrt(1 - BAND / wEnd);
        while (t < 1.0 - 1e-6) {
            double dt = 0.2;
            while (dt > 0.01 && tipW(wEnd, t) - tipW(wEnd, Math.min(1.0, t + dt)) > tipW(wEnd, Math.min(1.0, t + dt))) {
                dt *= 0.8;
            }
            if (t < tb && tb < t + dt) {
                dt = tb - t;
            }
            t = Math.min(1.0, t + dt);
            cuts.add(t);
        }
        for (int i = 0; i + 1 < cuts.size(); i++) {
            double t0 = cuts.get(i), t1 = cuts.get(i + 1);
            double L = (t1 - t0) * tipLen;
            alpha += tipCurve * L;
            double jx = joint[0], jy = joint[1];
            double w0 = tipW(wEnd, t0), w1 = tipW(wEnd, t1);
            double sx = jx - HALF;
            double band = Math.min(w1, BAND);
            double zs = 0.17 + 0.002 * (i % 2);
            Object[] ao = combined(alpha, joint);
            el(v(sx, jy - 0.06, AZ - zs), v(sx + band, jy + L, AZ + zs), "blade", "pointe : dos", (double) ao[0], (double[]) ao[1],
                    new double[] {0, toU(band), 3, 9}, "north", "south", "west");
            double dw = w0 - w1;
            double tilt = Math.toDegrees(Math.atan2(dw, L));
            double sw = Math.min(w0 - band + 0.05, w1);
            double[] p = rot2(sx + w0 - jx, 0.0, alpha);
            double[] pivot = {jx + p[0], jy + p[1]};
            Object[] ao2 = combined(alpha + tilt, pivot);
            double ls = Math.hypot(dw, L);
            double[] ur = w1 >= BAND - 1e-6 ? new double[] {r3(16 - toU(sw)), 16} : new double[] {r3(toU(BAND) - toU(sw)), toU(BAND)};
            el(v(pivot[0] - sw, pivot[1] - 0.12, AZ - (zs - 0.006)), v(pivot[0], pivot[1] + ls, AZ + (zs - 0.006)), "blade",
                    "pointe : fil", (double) ao2[0], (double[]) ao2[1], new double[] {ur[0], ur[1], 6, 12}, "north", "south", "east");
            double[] d = rot2(0.0, L, alpha);
            joint = new double[] {jx + d[0], jy + d[1]};
        }
    }

    static double tipW(double wEnd, double t) {
        return Math.max(0.04, wEnd * (1 - t * t));
    }

    static double toU(double d) {
        return r3(d / W0 * 16);
    }

    // ---------------------------------------------------------------- textures 16 x 16

    static double noise(int x, int y, int seed) {
        long n = (x * 374761393L + y * 668265263L + seed * 982451653L) & 0xFFFFFFFFL;
        n = ((n ^ (n >> 13)) * 1274126177L) & 0xFFFFFFFFL;
        return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0;
    }

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | (cl(r) << 16) | (cl(g) << 8) | cl(b);
    }

    static int cl(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Colonnes = largeur (0 = dos, 15 = tranchant) : dos sombre rouillé, gorge, acier gris, ligne de trempe ondulée, fil clair ébréché. */
    static int bladePx(int x, int y) {
        double r = noise(x, y, 3);
        int wave = (int) Math.round(Math.sin(y * 0.9) * 0.8 + noise(0, y, 9) * 0.6); // ligne de trempe ondulée
        if (x == 0) {
            return rgb(30, 28, 28);
        }
        if (x <= 5) {
            if (x == 2) {
                return rgb(44 + (int) (10 * r), 42 + (int) (10 * r), 44);            // gorge (rainure)
            }
            if (r > 0.9) {
                return rgb(122, 70, 38);                                           // rouille
            }
            if (r > 0.82) {
                return rgb(96, 62, 42);
            }
            int g = (int) (66 + 30 * r + 4 * x);
            return rgb(g, g, g + 4);
        }
        if (x <= 8 + wave) {
            int g = (int) (118 + 26 * r + 6 * (x - 6));
            return rgb(g, g, g + 5);
        }
        if (x == 9 + wave) {
            return rgb(226, 228, 232);                                             // ligne de trempe claire
        }
        if (x == 15) {
            return noise(x, y, 5) > 0.84 ? rgb(92, 92, 96) : rgb(214, 216, 220);   // fil, quelques ébréchures
        }
        int g = (int) (192 + 40 * r);
        return rgb(g, g, Math.min(255, g + 6));
    }

    /** Laiton doré vieilli : reflet en diagonale, bords sombres, taches de patine. */
    static int goldPx(int x, int y) {
        int d = Math.abs((x + y) - 15);
        double r = noise(x, y, 11);
        int[] c = d <= 1 ? new int[] {244, 208, 104} : d <= 6 ? new int[] {206, 158, 54} : new int[] {158, 112, 34};
        if (r > 0.9) {
            c = new int[] {112, 84, 40};
        }
        if (x == 0 || x == 15 || y == 0 || y == 15) {
            c = new int[] {(int) (c[0] * 0.78), (int) (c[1] * 0.78), (int) (c[2] * 0.78)};
        }
        return rgb(c[0] + (int) (10 * r), c[1] + (int) (8 * r), c[2]);
    }

    /** Garde ronde vue de dessus (projection plane) : rebord clair, creux sombre ciselé de points, anneau, moyeu. */
    static int guardPx(int x, int y) {
        double cx = x - 7.5, cy = y - 7.5;
        double rr = Math.sqrt(cx * cx + cy * cy) / 8.0;
        double r = noise(x, y, 17);
        if (rr > 0.86 || y == 0) {
            return rgb(238 + (int) (12 * r), 196 + (int) (10 * r), 92);           // rebord
        }
        if (rr > 0.74) {
            return rgb(150, 104, 30);
        }
        if (rr > 0.5) {
            boolean dot = ((x * 3 + y * 5) % 7 == 0);
            return dot ? rgb(232, 190, 86) : rgb(96 + (int) (16 * r), 70 + (int) (10 * r), 34);   // creux ciselé
        }
        if (rr > 0.38) {
            return rgb(222, 178, 70);                                              // anneau intérieur
        }
        return rgb(70 + (int) (14 * r), 60, 52);                                   // moyeu (fer)
    }

    /** Poignée : cordon brun sombre croisé en losanges sur peau claire (granuleuse). */
    static int gripPx(int x, int y) {
        int a = Math.floorMod(x + y, 8), b = Math.floorMod(x - y, 8);
        double r = noise(x, y, 23);
        if (a <= 1 || b <= 1) {
            boolean light = a == 0 || b == 0;
            return light ? rgb(84, 54, 38) : rgb(48, 30, 22);
        }
        int g = (int) (184 + 22 * r);
        return r > 0.85 ? rgb(150, 140, 118) : rgb(g, g - 12, g - 38);
    }

    /** Fer sombre du pommeau. */
    static int ironPx(int x, int y) {
        double r = noise(x, y, 29);
        int g = (int) (52 + 26 * r) + (y < 3 ? 18 : 0);
        return rgb(g, g, g + 6);
    }

    interface Px {
        int at(int x, int y);
    }

    static BufferedImage tex(Px p) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                img.setRGB(x, y, p.at(x, y));
            }
        }
        return img;
    }

    // ---------------------------------------------------------------- JSON

    static String n(double d) {
        if (d == Math.rint(d)) {
            return String.valueOf((long) d);
        }
        return String.format(Locale.ROOT, "%s", r4(d));
    }

    static String arr(double... a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            sb.append(i > 0 ? ", " : "").append(n(a[i]));
        }
        return sb.append("]").toString();
    }

    static double planar(double v, double lo, double hi) {
        return r3(Math.min(16, Math.max(0, (v - lo) / (hi - lo) * 16)));
    }

    static String faces(El e) {
        String[] keep = e.faces != null ? e.faces : new String[] {"north", "south", "east", "west", "up", "down"};
        String tx = "#" + e.tx;
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String k : keep) {
            double[] uv;
            if (e.tx.equals("blade")) {
                double u0 = e.uv[0], u1 = e.uv[1], v0 = e.uv[2], v1 = e.uv[3];
                uv = switch (k) {
                    case "north" -> new double[] {u1, v0, u0, v1};
                    case "south" -> new double[] {u0, v0, u1, v1};
                    case "west" -> new double[] {u0, v0, Math.min(16, u0 + 1), v1};
                    case "east" -> new double[] {Math.max(0, u1 - 1), v0, u1, v1};
                    default -> null;
                };
            } else if (e.tx.equals("grip")) {
                uv = e.c.equals("tresse") ? new double[] {0, 0, 16, 3} : new double[] {0, 0, 16, 16};
            } else {
                double[] f = e.f, t = e.t;
                double ux0 = planar(f[0], 5.8, 10.2), ux1 = planar(t[0], 5.8, 10.2);
                double uz0 = planar(f[2], 5.8, 10.2), uz1 = planar(t[2], 5.8, 10.2);
                double vy0 = planar(3.0 - t[1], 0, 6.8), vy1 = planar(3.0 - f[1], 0, 6.8);
                if (e.tx.equals("guard")) { // côtés : le rebord (rangée 0) ; dessus / dessous : la garde vue de dessus
                    vy0 = 0;
                    vy1 = 1;
                }
                if (vy1 - vy0 < 0.5) {
                    vy1 = Math.min(16, vy0 + 0.5);
                }
                uv = switch (k) {
                    case "north" -> new double[] {ux1, vy0, ux0, vy1};
                    case "south" -> new double[] {ux0, vy0, ux1, vy1};
                    case "east" -> new double[] {uz0, vy0, uz1, vy1};
                    case "west" -> new double[] {uz1, vy0, uz0, vy1};
                    case "up" -> new double[] {ux0, uz0, ux1, uz1};
                    case "down" -> new double[] {ux0, uz1, ux1, uz0};
                    default -> null;
                };
            }
            if (uv == null) {
                continue;
            }
            sb.append(first ? "" : ", ").append("\"").append(k).append("\": {\"uv\": ").append(arr(uv)).append(", \"texture\": \"")
                    .append(tx).append("\"}");
            first = false;
        }
        return sb.append("}").toString();
    }

    static double[] place(El e, double x, double y) {
        double ox = e.origin[0], oy = e.origin[1];
        double[] r = rot2(x - ox, y - oy, e.angle);
        return new double[] {r[0] + ox, r[1] + oy};
    }

    /** Cadrage (sol, cadre, inventaire) : l'épée entière centrée dans la case. */
    static String framing(double scale, double[] extra, boolean mirror) {
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        for (El e : E) {
            for (double x : new double[] {e.f[0], e.t[0]}) {
                for (double y : new double[] {e.f[1], e.t[1]}) {
                    double[] p = place(e, x, y);
                    minX = Math.min(minX, p[0]);
                    maxX = Math.max(maxX, p[0]);
                    minY = Math.min(minY, p[1]);
                    maxY = Math.max(maxY, p[1]);
                }
            }
        }
        double cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
        double tx = mirror ? (cx - 8) * scale : -(cx - 8) * scale;
        return "{\"rotation\": " + arr(0, mirror ? 180 : 0, 0) + ", \"translation\": " + arr(r3(tx + extra[0]), r3(-(cy - 8) * scale + extra[1]), extra[2])
                + ", \"scale\": " + arr(scale, scale, scale) + "}";
    }

    static String model() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"__comment\": \"Sabre des Humandrills : généré par src/blockbench/humandrill/MakeHumandrillSword.java\",\n");
        sb.append("  \"textures\": {\n");
        for (String k : new String[] {"blade", "gold", "guard", "grip", "iron"}) {
            sb.append("    \"").append(k).append("\": \"bloxpiece:item/").append(ID).append("_").append(k).append("\",\n");
        }
        sb.append("    \"particle\": \"bloxpiece:item/").append(ID).append("_blade\"\n  },\n");
        sb.append("  \"display\": {\n");
        // en main : exactement les cadrages d'Ace (même position de poignée, mêmes axes) ; validés en jeu
        sb.append("    \"thirdperson_righthand\": {\"rotation\": [-180, 90, -145], \"translation\": [0, 5.56, 0.05], \"scale\": [0.85, 0.85, 0.85]},\n");
        sb.append("    \"thirdperson_lefthand\": {\"rotation\": [-180, -90, 145], \"translation\": [0, 5.56, 0.05], \"scale\": [0.85, 0.85, 0.85]},\n");
        sb.append("    \"firstperson_righthand\": {\"rotation\": [-180, 90, -125], \"translation\": [1.13, 2.9, 0.98], \"scale\": [0.54, 0.54, 0.54]},\n");
        sb.append("    \"firstperson_lefthand\": {\"rotation\": [-180, -90, 125], \"translation\": [1.13, 2.9, 0.98], \"scale\": [0.54, 0.54, 0.54]},\n");
        sb.append("    \"ground\": ").append(framing(0.42, new double[] {0, 2, 0}, false)).append(",\n");
        sb.append("    \"fixed\": ").append(framing(0.7, new double[] {0, 0, 0}, true)).append(",\n");
        sb.append("    \"gui\": ").append(framing(0.7, new double[] {0, 0, 0}, false)).append("\n  },\n");
        sb.append("  \"elements\": [\n");
        for (int i = 0; i < E.size(); i++) {
            El e = E.get(i);
            sb.append("    {\"__comment\": \"").append(e.c).append("\", \"from\": ").append(arr(e.f)).append(", \"to\": ").append(arr(e.t))
                    .append(", \"rotation\": {\"angle\": ").append(n(e.angle)).append(", \"axis\": \"z\", \"origin\": ").append(arr(e.origin))
                    .append("}, \"faces\": ").append(faces(e)).append("}").append(i + 1 < E.size() ? ",\n" : "\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    /** Aperçu : silhouette de face (épée à l'horizontale, poignée à gauche), couleur par matière. */
    static void preview(File out) throws Exception {
        int W = 1400, H = 480;
        double sc = 40;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(14, 14, 18));
        g.fillRect(0, 0, W, H);
        for (El e : E) {
            double[][] c = {{e.f[0], e.f[1]}, {e.t[0], e.f[1]}, {e.t[0], e.t[1]}, {e.f[0], e.t[1]}};
            Path2D p = new Path2D.Double();
            for (int i = 0; i < 4; i++) {
                double[] q = place(e, c[i][0], c[i][1]);
                double[] u = rot2(q[0] - 8, q[1] - 8, 45.0);    // annule la diagonale : épée verticale
                double px = u[1], py = -u[0];                     // puis horizontale, poignée à gauche
                double sx = W / 2.0 + (px - 10) * sc, sy = H / 2.0 - py * sc;
                if (i == 0) {
                    p.moveTo(sx, sy);
                } else {
                    p.lineTo(sx, sy);
                }
            }
            p.closePath();
            g.setColor(switch (e.tx) {
                case "blade" -> e.c.contains("fil") ? new Color(220, 222, 228) : new Color(150, 152, 160);
                case "gold" -> new Color(210, 162, 58);
                case "guard" -> new Color(236, 192, 90);
                case "grip" -> new Color(90, 58, 40);
                default -> new Color(70, 70, 76);
            });
            g.fill(p);
        }
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : "src/main/resources/assets/bloxpiece");
        build();
        for (El e : E) {
            if (e.angle < -45.0 || e.angle > 45.0) {
                throw new IllegalStateException("angle hors limites : " + e.angle);
            }
            for (int i = 0; i < 3; i++) {
                if (e.f[i] < -16 || e.t[i] > 32) {
                    throw new IllegalStateException("élément hors limites : " + e.c);
                }
            }
        }
        File texDir = new File(root, "textures/item");
        ImageIO.write(tex(MakeHumandrillSword::bladePx), "png", new File(texDir, ID + "_blade.png"));
        ImageIO.write(tex(MakeHumandrillSword::goldPx), "png", new File(texDir, ID + "_gold.png"));
        ImageIO.write(tex(MakeHumandrillSword::guardPx), "png", new File(texDir, ID + "_guard.png"));
        ImageIO.write(tex(MakeHumandrillSword::gripPx), "png", new File(texDir, ID + "_grip.png"));
        ImageIO.write(tex(MakeHumandrillSword::ironPx), "png", new File(texDir, ID + "_iron.png"));
        Files.writeString(new File(root, "models/item/" + ID + ".json").toPath(), model(), StandardCharsets.UTF_8);
        Files.writeString(new File(root, "items/" + ID + ".json").toPath(),
                "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"bloxpiece:item/" + ID + "\"\n  }\n}\n", StandardCharsets.UTF_8);
        if (args.length > 1) {
            preview(new File(args[1], "humandrill_sword_preview.png"));
        }
        System.out.println(E.size() + " éléments ; modèle et textures écrits dans " + root);
    }
}
