package nolan.bloxpiece.fx;

import net.minecraft.world.phys.Vec3;

/**
 * Forme de la Lame dévastatrice du Haki des Rois, partagée par les collisions (AceSlash, serveur) et le rendu (AceSlashRenderer) :
 * le même croissant que le modèle 3D (constantes identiques à src/blockbench/ace_slash/make_ace_slash.py, en pixels de modèle,
 * 16 px = 1 bloc). Le modèle 3D (affichage) et cette forme de collision sont séparés : seule la forme compte pour les dégâts.
 * <p>
 * Repère local : X = envergure, Y = épaisseur, Z = sens du vol. Le croissant est couché dans le plan X-Z : sa bosse courbée (le
 * tranchant) mène vers l'avant, ses pointes sont rejetées vers l'arrière. Passage au monde : roulis (Z, la diagonale du coup),
 * puis inclinaison (X, pitch), puis direction (Y, -yaw), exactement comme le PoseStack du renderer.
 */
public final class AceSlashShape {

    /** Demi-envergure, flèches des bords avant (tranchant) et arrière (dos), recentrage, épaisseur : pixels, voir le générateur. */
    public static final double HS = 64.0;
    public static final double BOW_O = 32.0;
    public static final double BOW_I = 7.0;
    public static final double CENTER_Z = 14.0;
    public static final double THICK = 9.0;
    /** Inclinaison de la lame (degrés) autour de l'axe du vol : du haut à droite vers le bas à gauche, vu par le lanceur. */
    public static final float ROLL = -35.0F;
    /** Deuxième diagonale (miroir) : du haut à gauche vers le bas à droite. Le croissant est symétrique : même forme, juste
     *  incliné dans l'autre sens autour de l'axe du vol (bosse toujours vers l'avant). */
    public static final float ROLL_REVERSE = 35.0F;

    /** Inclinaison de la diagonale choisie. */
    public static float roll(boolean reverse) {
        return reverse ? ROLL_REVERSE : ROLL;
    }
    /** Points de collision le long de l'envergure. */
    public static final int SAMPLES = 17;

    private AceSlashShape() {
    }

    /** Un point de collision : position locale (blocs) et rayon (blocs) = demi-largeur de la lame à cet endroit. */
    public record Sample(double x, double y, double z, double radius) {
    }

    /** Point local (blocs) de la lame : abscisse s (-1..1), fraction f de la largeur (0 = tranchant avant, 1 = dos). */
    public static Vec3 point(double s, double f) {
        double z = (BOW_O - f * (BOW_O - BOW_I)) * (1.0 - s * s) - CENTER_Z;
        return new Vec3(s * HS / 16.0, 0.0, z / 16.0);
    }

    /** Points de collision pour une lame de taille {@code scale} (1 = normale ; plus grande après une charge). */
    public static Sample[] samples(double scale) {
        Sample[] out = new Sample[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            double s = -1.0 + 2.0 * i / (SAMPLES - 1);
            double k = 1.0 - s * s;
            double width = (BOW_O - BOW_I) * k;
            double radius = Math.max(0.35 * 16.0, Math.max(width, THICK * k) / 2.0 + 2.0); // pointes déchiquetées comprises
            Vec3 p = point(s, 0.5);
            out[i] = new Sample(p.x * scale, p.y * scale, p.z * scale, radius / 16.0 * scale);
        }
        return out;
    }

    /** Point local (blocs) vers le monde, relatif à la position de la lame. */
    public static Vec3 toWorld(double x, double y, double z, float yaw, float pitch, float roll) {
        double r = Math.toRadians(roll), p = Math.toRadians(pitch), w = Math.toRadians(-yaw);
        double x1 = x * Math.cos(r) - y * Math.sin(r), y1 = x * Math.sin(r) + y * Math.cos(r), z1 = z;           // roulis (Z)
        double y2 = y1 * Math.cos(p) - z1 * Math.sin(p), z2 = y1 * Math.sin(p) + z1 * Math.cos(p);               // pitch (X)
        double x3 = x1 * Math.cos(w) + z2 * Math.sin(w), z3 = -x1 * Math.sin(w) + z2 * Math.cos(w);              // direction (Y)
        return new Vec3(x3, y2, z3);
    }
}
