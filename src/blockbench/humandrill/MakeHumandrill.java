import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Générateur du Humandrill (babouin sabreur de Kuraigana) : une seule source pour la géométrie et la texture.
 * <ul>
 *   <li>écrit src/client/java/nolan/bloxpiece/client/humandrill/HumandrillModels.java (pièces, cubes, UV) ;</li>
 *   <li>écrit textures/entity/humandrill.png (pixel art, 1 texel par pixel de modèle).</li>
 * </ul>
 * Silhouette de babouin / mandrill (pas un gorille) : long museau de chien à arête rouge et joues bleutées côtelées, sourcils
 * lourds, petits yeux ambre, crocs, favoris clairs ; corps voûté, poitrine profonde, crinière sur les épaules et le dos ; longs
 * bras (épaule, coude, poignet, poing) ; jambes courtes arquées (hanche, genou, cheville, long pied) ; queue « cassée » de
 * babouin (deux segments et une touffe). Équipement récupéré sur les champs de bataille : pagne, ceinture de corde, bandages
 * aux poignets, spalière de fer cabossée sur l'épaule gauche.
 * <p>
 * Lancement (JDK 25, depuis la racine du projet) : java src/blockbench/humandrill/MakeHumandrill.java
 */
public class MakeHumandrill {

    static final int TW = 128, TH = 64;

    enum Mat { FUR, FUR_LIGHT, FUR_DARK, MANE, SKIN, RIDGE, NOSE, HAND, TOOTH, CLOTH, WRAP, ROPE, IRON, SKULL, MUZZLE, CHEST }

    static final class Box {
        final double x, y, z;
        final int w, h, d;
        final Mat mat;
        int u, v;
        boolean mirror;
        Box shared;   // réutilise les UV d'un autre cube (miroir)

        Box(double x, double y, double z, int w, int h, int d, Mat mat) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
            this.h = h;
            this.d = d;
            this.mat = mat;
        }
    }

    static final class Part {
        final String name, parent;
        final double px, py, pz;
        final List<Box> boxes = new ArrayList<>();

        Part(String name, String parent, double px, double py, double pz) {
            this.name = name;
            this.parent = parent;
            this.px = px;
            this.py = py;
            this.pz = pz;
        }

        Part box(double x, double y, double z, int w, int h, int d, Mat m) {
            this.boxes.add(new Box(x, y, z, w, h, d, m));
            return this;
        }
    }

    static final Map<String, Part> PARTS = new LinkedHashMap<>();

    static Part part(String name, String parent, double px, double py, double pz) {
        Part p = new Part(name, parent, px, py, pz);
        PARTS.put(name, p);
        return p;
    }

    /** Copie miroir (x -> -x) d'une pièce droite : mêmes UV, cubes retournés. */
    static void mirrorOf(String src, String name, String parent) {
        Part s = PARTS.get(src);
        Part p = part(name, parent, -s.px, s.py, s.pz);
        for (Box b : s.boxes) {
            Box m = new Box(-(b.x + b.w), b.y, b.z, b.w, b.h, b.d, b.mat);
            m.mirror = true;
            m.shared = b;
            p.boxes.add(m);
        }
    }

    // ---------------------------------------------------------------- géométrie (px ; pieds à y = 24)
    static void build() {
        part("waist", "root", 0, 9.0, 1.0)
                .box(-5, -1.5, -3.5, 10, 4, 7, Mat.FUR)                 // bassin
                .box(-5.5, -1.0, -4.0, 11, 1, 8, Mat.ROPE);             // ceinture de corde
        part("cloth", "waist", 0, 0.0, -3.9)
                .box(-3.5, 0, -0.5, 7, 6, 1, Mat.CLOTH);                // pagne (flotte avec les jambes)
        part("tail", "waist", 0, -0.6, 3.3)
                .box(-1, -1, -0.3, 2, 2, 5, Mat.FUR);
        part("tail_tip", "tail", 0, 0, 4.6)
                .box(-1, -1, 0, 2, 2, 6, Mat.FUR)
                .box(-1.5, -1.5, 4.8, 3, 3, 2, Mat.MANE);              // touffe

        part("torso", "waist", 0, -1.5, 0)
                .box(-6.5, -11, -4.2, 13, 7, 8, Mat.CHEST)             // poitrine profonde
                .box(-5.5, -4.5, -3.6, 11, 5, 7, Mat.FUR)              // ventre
                .box(-4, -9.5, -4.8, 8, 7, 1, Mat.FUR_LIGHT)           // plastron de poils clairs
                .box(-7, -12.2, -1.8, 14, 5, 6, Mat.MANE)              // crinière des épaules
                .box(-6, -10, 3.2, 12, 8, 2, Mat.MANE);                // crinière du dos

        part("head", "torso", 0, -11, -2.5)
                .box(-4, -7, -4.5, 8, 7, 7, Mat.SKULL)                 // crâne (yeux, orbites)
                .box(-4.5, -6, -5.2, 9, 1, 1, Mat.FUR_DARK)            // arcade sourcilière lourde
                .box(-5.5, -5, -3.5, 11, 6, 4, Mat.FUR_LIGHT)          // favoris
                .box(-3, -8, -3.5, 6, 1, 6, Mat.MANE)                  // crête
                .box(-2.5, -4, -10.2, 5, 4, 6, Mat.MUZZLE)             // long museau (joues côtelées)
                .box(-1.5, -4.7, -9.6, 3, 1, 5, Mat.RIDGE)             // arête rouge
                .box(-1.5, -3.4, -10.9, 3, 2, 1, Mat.NOSE)             // truffe
                .box(-5.2, -6.8, -0.5, 1, 2, 2, Mat.SKIN)              // oreille droite
                .box(4.2, -6.8, -0.5, 1, 2, 2, Mat.SKIN)               // oreille gauche
                .box(-2.7, -0.4, -9.3, 1, 2, 1, Mat.TOOTH)             // croc
                .box(1.7, -0.4, -9.3, 1, 2, 1, Mat.TOOTH);             // croc
        part("jaw", "head", 0, 0, -4.2)
                .box(-2, 0, -5.8, 4, 2, 6, Mat.MUZZLE)
                .box(-1.5, -0.6, -5.7, 3, 1, 1, Mat.TOOTH)             // dents du bas
                .box(-2.5, 1.2, -4.6, 5, 1, 4, Mat.FUR_LIGHT);         // barbiche

        part("right_arm", "torso", -7, -9, -0.3)
                .box(-2.5, -1.5, -2.5, 5, 9, 5, Mat.FUR)
                .box(-3, -2.3, -3, 6, 3, 6, Mat.MANE);                 // touffe d'épaule
        part("right_forearm", "right_arm", 0, 7.5, 0)
                .box(-2, -0.3, -2, 4, 8, 4, Mat.FUR)
                .box(-2.5, 5, -2.5, 5, 2, 5, Mat.WRAP);                // bandage du poignet
        part("right_hand", "right_forearm", 0, 7.7, 0)
                .box(-2, -0.3, -2.5, 4, 3, 5, Mat.HAND)                // poing
                .box(1.6, 0.2, -2.0, 1, 2, 2, Mat.HAND);               // pouce
        mirrorOf("right_arm", "left_arm", "torso");
        // à gauche : spalière de fer cabossée à la place de la touffe
        Part la = PARTS.get("left_arm");
        la.boxes.remove(1);
        la.box(-2, -3, -3.5, 6, 4, 7, Mat.IRON).box(-1.5, 0.6, -3.2, 5, 2, 6, Mat.IRON);
        mirrorOf("right_forearm", "left_forearm", "left_arm");
        mirrorOf("right_hand", "left_hand", "left_forearm");

        part("right_thigh", "waist", -3, 2, 0.3)
                .box(-2.5, -1.2, -2.5, 5, 8, 5, Mat.FUR);
        part("right_shin", "right_thigh", 0, 6.2, 0)
                .box(-2, -0.3, -2, 4, 6, 4, Mat.FUR);
        part("right_foot", "right_shin", 0, 5.5, 0)
                .box(-2, 0, -4.5, 4, 2, 6, Mat.HAND)
                .box(-2, 0.9, -5.4, 4, 1, 1, Mat.HAND);                // orteils
        mirrorOf("right_thigh", "left_thigh", "waist");
        mirrorOf("right_shin", "left_shin", "left_thigh");
        mirrorOf("right_foot", "left_foot", "left_shin");
    }

    // ---------------------------------------------------------------- UV : rangement en étagères
    static void pack() {
        int x = 0, y = 0, rowH = 0;
        List<Box> all = new ArrayList<>();
        for (Part p : PARTS.values()) {
            for (Box b : p.boxes) {
                if (b.shared == null) {
                    all.add(b);
                }
            }
        }
        all.sort((a, b) -> Integer.compare(b.d + b.h, a.d + a.h));
        for (Box b : all) {
            int bw = 2 * b.d + 2 * b.w, bh = b.d + b.h;
            if (x + bw > TW) {
                x = 0;
                y += rowH;
                rowH = 0;
            }
            b.u = x;
            b.v = y;
            x += bw;
            rowH = Math.max(rowH, bh);
        }
        if (y + rowH > TH) {
            throw new IllegalStateException("texture trop petite : " + (y + rowH));
        }
        for (Part p : PARTS.values()) {
            for (Box b : p.boxes) {
                if (b.shared != null) {
                    b.u = b.shared.u;
                    b.v = b.shared.v;
                }
            }
        }
    }

    // ---------------------------------------------------------------- peinture
    static final int[][] IMG = new int[TW][TH];

    static double noise(int x, int y, int seed) {
        long n = (x * 374761393L + y * 668265263L + seed * 982451653L) & 0xFFFFFFFFL;
        n = ((n ^ (n >> 13)) * 1274126177L) & 0xFFFFFFFFL;
        return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0;
    }

    static int rgb(double r, double g, double b) {
        return 0xFF000000 | (cl(r) << 16) | (cl(g) << 8) | cl(b);
    }

    static int cl(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    static void set(int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < TW && y < TH) {
            IMG[x][y] = c;
        }
    }

    /**
     * Fourrure brune : plusieurs nuances de marron. Grain de base, mèches verticales qui tirent vers le brun-noir (ombre entre
     * les poils), pointes qui tirent vers le fauve doré, quelques plaques roussâtres ; k = luminosité (dessus clair, dessous
     * sombre).
     */
    static int fur(int x, int y, double[] base, double k, int strand) {
        double n = noise(x, y, 1);
        double s = noise(x, y / strand, 2);
        double patch = noise(x / 3, y / 3, 3);
        double m = 0.86 + 0.24 * n;
        double[] c = base.clone();
        if (patch > 0.7) {
            c = mix(c, RUSSET, 0.35);   // plaques roussâtres
        } else if (patch < 0.2) {
            c = mix(c, UMBER, 0.25);    // plaques plus sombres
        }
        if (s > 0.72) {
            c = mix(c, STRAND, 0.45);   // mèche sombre
        }
        if (n > 0.92) {
            c = mix(c, TIP, 0.5);       // pointe dorée
            m *= 1.08;
        }
        return rgb(c[0] * m * k, c[1] * m * k, c[2] * m * k);
    }

    static double[] mix(double[] a, double[] b, double t) {
        return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** Nuances de la fourrure (marrons d'après les images de référence). */
    static final double[] RUSSET = {146, 84, 44}, UMBER = {78, 50, 30}, STRAND = {52, 32, 20}, TIP = {196, 148, 90};
    static final double[] FUR = {122, 82, 50}, FUR_LIGHT = {184, 144, 98}, FUR_DARK = {70, 44, 28}, MANE = {94, 60, 36},
            SKIN = {96, 84, 92}, HAND = {66, 56, 56}, CHEST = {150, 108, 68};

    /** Face d'un cube : 0 dessus, 1 dessous, 2 côté droit (-x), 3 avant (-z), 4 côté gauche (+x), 5 arrière. */
    interface Painter {
        int at(Box b, int face, int fx, int fy, int fw, int fh, int gx, int gy);
    }

    static void paint(Box b, Painter p) {
        int u = b.u, v = b.v, w = b.w, h = b.h, d = b.d;
        int[][] rects = {{u + d, v, w, d}, {u + d + w, v, w, d}, {u, v + d, d, h}, {u + d, v + d, w, h}, {u + d + w, v + d, d, h},
                {u + 2 * d + w, v + d, w, h}};
        for (int f = 0; f < 6; f++) {
            int[] r = rects[f];
            for (int i = 0; i < r[2]; i++) {
                for (int j = 0; j < r[3]; j++) {
                    set(r[0] + i, r[1] + j, p.at(b, f, i, j, r[2], r[3], r[0] + i, r[1] + j));
                }
            }
        }
    }

    static double shade(int face) {
        return face == 0 ? 1.1 : face == 1 ? 0.7 : 1.0;
    }

    static int painter(Box b, int f, int fx, int fy, int fw, int fh, int gx, int gy) {
        double k = shade(f);
        switch (b.mat) {
            case FUR:
                return fur(gx, gy, FUR, k * (f >= 2 ? 1.0 - 0.12 * fy / Math.max(1, fh) : 1.0), 2);
            case FUR_LIGHT:
                return fur(gx, gy, FUR_LIGHT, k, 2);
            case FUR_DARK:
                return fur(gx, gy, FUR_DARK, k, 1);
            case MANE:
                return fur(gx, gy, MANE, k * (f >= 2 && fy == fh - 1 ? 0.8 : 1.0), 3);        // longues mèches
            case CHEST: {
                // poitrine : poils plus clairs au centre de la face avant
                double c = f == 3 ? 1.0 - Math.abs(fx - (fw - 1) / 2.0) / fw : 0.0;
                double[] base = {FUR[0] + (CHEST[0] - FUR[0]) * c * 2, FUR[1] + (CHEST[1] - FUR[1]) * c * 2, FUR[2] + (CHEST[2] - FUR[2]) * c * 2};
                return fur(gx, gy, base, k, 2);
            }
            case SKIN:
                return rgb(SKIN[0] * (0.92 + 0.12 * noise(gx, gy, 4)) * k, SKIN[1] * k, SKIN[2] * k);
            case HAND: {
                boolean knuckle = f == 1 || (f == 3 && fy == fh - 1);
                double m = (0.9 + 0.15 * noise(gx, gy, 5)) * (knuckle ? 0.8 : 1.0) * k;
                return rgb(HAND[0] * m, HAND[1] * m, HAND[2] * m);
            }
            case TOOTH:
                return fy == fh - 1 && f >= 2 ? rgb(244, 236, 214) : rgb(226, 214, 186);
            case RIDGE: {
                // arête du nez : rouge vif au milieu, plus sombre sur les bords
                boolean edge = (f == 0 || f == 3) && (fx == 0 || fx == fw - 1);
                double m = 0.9 + 0.15 * noise(gx, gy, 6);
                return edge ? rgb(132 * m, 40 * m, 42 * m) : rgb(186 * m * k, 50 * m * k, 46 * m * k);
            }
            case NOSE: {
                if (f == 3) {
                    boolean nostril = fy == fh - 1 && (fx == 0 || fx == fw - 1);
                    return nostril ? rgb(24, 14, 16) : rgb(176, 44, 44);
                }
                return rgb(150, 38, 40);
            }
            case MUZZLE: {
                // museau : côtés bleutés côtelés (sillons verticaux sombres), dessus gris-violet, dessous sombre
                double n = noise(gx, gy, 7);
                if (f == 2 || f == 4) {
                    boolean groove = fx % 2 == 1;
                    double[] blue = {86, 112, 160};
                    double m = (groove ? 0.62 : 1.0) * (0.9 + 0.15 * n) * (1.0 - 0.18 * fy / Math.max(1, fh));
                    return rgb(blue[0] * m, blue[1] * m, blue[2] * m);
                }
                if (f == 1) {
                    return rgb(58, 46, 52);
                }
                if (f == 3) {
                    return fy >= fh - 1 ? rgb(60, 44, 48) : rgb(SKIN[0] * (0.9 + 0.1 * n), SKIN[1] * 0.95, SKIN[2]);   // lèvre
                }
                return rgb(SKIN[0] * (0.95 + 0.12 * n) * k, SKIN[1] * k, SKIN[2] * k);
            }
            case SKULL: {
                if (f == 3) {
                    // face : front poilu, orbites sombres sous l'arcade (rangée 2), petits yeux ambre à pupille noire
                    int col = fx, row = fy;
                    if (row == 2 && (col == 1 || col == fw - 2)) {
                        return rgb(236, 168, 36);                                       // iris ambre
                    }
                    if (row == 2 && (col == 2 || col == fw - 3)) {
                        return rgb(20, 14, 12);                                         // pupille
                    }
                    if (row == 2 || (row == 3 && col >= 1 && col <= fw - 2)) {
                        return rgb(62, 50, 54);                                         // orbites, peau nue
                    }
                    if (row >= 3) {
                        return rgb(SKIN[0] * 0.95, SKIN[1] * 0.95, SKIN[2] * 0.95);    // joues nues autour du museau
                    }
                }
                return fur(gx, gy, FUR, k, 2);
            }
            case CLOTH: {
                // pagne rouge délavé : deux bandes sombres, bas effiloché
                double n = noise(gx, gy, 8);
                boolean band = fy == 1 || fy == fh - 2;
                boolean fray = fy == fh - 1 && n > 0.45;
                double m = (band ? 0.7 : 1.0) * (0.88 + 0.2 * n) * k;
                return fray ? rgb(78, 30, 28) : rgb(132 * m, 46 * m, 40 * m);
            }
            case WRAP: {
                // bandage : bandes enroulées en biais, sale
                boolean line = Math.floorMod(fx + fy, 3) == 0;
                double m = (line ? 0.78 : 1.0) * (0.9 + 0.14 * noise(gx, gy, 9)) * k;
                return rgb(192 * m, 174 * m, 140 * m);
            }
            case ROPE: {
                boolean twist = Math.floorMod(fx - fy, 2) == 0;
                double m = (twist ? 0.8 : 1.0) * (0.9 + 0.12 * noise(gx, gy, 10)) * k;
                return rgb(124 * m, 98 * m, 62 * m);
            }
            case IRON: {
                // fer cabossé : rivets dorés aux coins, rouille, bosses claires
                double n = noise(gx, gy, 11);
                boolean rivet = f >= 2 && (fx == 1 || fx == fw - 2) && fy == 1;
                if (rivet) {
                    return rgb(184, 148, 72);
                }
                if (n > 0.9) {
                    return rgb(118, 70, 40);
                }
                double m = (0.85 + 0.3 * n) * k * (fy == 0 && f >= 2 ? 1.2 : 1.0);
                return rgb(92 * m, 94 * m, 102 * m);
            }
            default:
                return rgb(255, 0, 255);
        }
    }

    // ---------------------------------------------------------------- source Java
    static String f(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", ".0");
        return s + "F";
    }

    static String javaSource() {
        StringBuilder sb = new StringBuilder();
        sb.append("package nolan.bloxpiece.client.humandrill;\n\n");
        sb.append("import net.minecraft.client.model.geom.ModelLayerLocation;\n");
        sb.append("import net.minecraft.client.model.geom.PartPose;\n");
        sb.append("import net.minecraft.client.model.geom.builders.CubeListBuilder;\n");
        sb.append("import net.minecraft.client.model.geom.builders.LayerDefinition;\n");
        sb.append("import net.minecraft.client.model.geom.builders.MeshDefinition;\n");
        sb.append("import net.minecraft.client.model.geom.builders.PartDefinition;\n\n");
        sb.append("import nolan.bloxpiece.BloxPiece;\n\n");
        sb.append("/**\n");
        sb.append(" * Géométrie du Humandrill (babouin sabreur de Kuraigana), environ 2 blocs, 1 texel par pixel, texture humandrill.png\n");
        sb.append(" * (").append(TW).append("x").append(TH).append(").\n");
        sb.append(" * FICHIER GÉNÉRÉ par src/blockbench/humandrill/MakeHumandrill.java (avec la texture) : modifier le générateur, pas ce fichier.\n");
        sb.append(" * Hiérarchie : waist (bassin) > torse > tête > mâchoire ; torse > bras > avant-bras > main ; waist > cuisse > tibia > pied ;\n");
        sb.append(" * waist > queue > bout de queue ; waist > pagne. Rotations : toutes calculées par HumandrillAnimator (repos à zéro).\n");
        sb.append(" */\n");
        sb.append("public final class HumandrillModels {\n\n");
        sb.append("    public static final ModelLayerLocation HUMANDRILL = new ModelLayerLocation(BloxPiece.id(\"humandrill\"), \"main\");\n\n");
        sb.append("    private HumandrillModels() {\n    }\n\n");
        sb.append("    public static LayerDefinition create() {\n");
        sb.append("        MeshDefinition mesh = new MeshDefinition();\n");
        sb.append("        PartDefinition root = mesh.getRoot();\n");
        for (Part p : PARTS.values()) {
            sb.append("        PartDefinition ").append(p.name).append(" = ").append(p.parent).append(".addOrReplaceChild(\"").append(p.name)
                    .append("\", CubeListBuilder.create()");
            boolean mirror = false;
            for (Box b : p.boxes) {
                sb.append("\n                .texOffs(").append(b.u).append(", ").append(b.v).append(")");
                if (b.mirror != mirror) {
                    sb.append(b.mirror ? ".mirror()" : ".mirror(false)");
                    mirror = b.mirror;
                }
                sb.append(".addBox(").append(f(b.x)).append(", ").append(f(b.y)).append(", ").append(f(b.z)).append(", ")
                        .append(f(b.w)).append(", ").append(f(b.h)).append(", ").append(f(b.d)).append(")");
            }
            sb.append(",\n                PartPose.offset(").append(f(p.px)).append(", ").append(f(p.py)).append(", ").append(f(p.pz)).append("));\n");
        }
        sb.append("        return LayerDefinition.create(mesh, ").append(TW).append(", ").append(TH).append(");\n");
        sb.append("    }\n}\n");
        // variables inutilisées tolérées (pièces feuilles) : javac ne s'en plaint pas
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        build();
        pack();
        for (Part p : PARTS.values()) {
            for (Box b : p.boxes) {
                if (b.shared == null) {
                    paint(b, MakeHumandrill::painter);
                }
            }
        }
        BufferedImage img = new BufferedImage(TW, TH, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < TW; x++) {
            for (int y = 0; y < TH; y++) {
                img.setRGB(x, y, IMG[x][y]);
            }
        }
        File tex = new File("src/main/resources/assets/bloxpiece/textures/entity/humandrill.png");
        ImageIO.write(img, "png", tex);
        File src = new File("src/client/java/nolan/bloxpiece/client/humandrill/HumandrillModels.java");
        src.getParentFile().mkdirs();
        Files.writeString(src.toPath(), javaSource(), StandardCharsets.UTF_8);
        System.out.println("Humandrill : " + PARTS.size() + " pièces ; " + tex + " ; " + src);
    }
}
