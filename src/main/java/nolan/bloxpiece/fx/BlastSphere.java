package nolan.bloxpiece.fx;

/**
 * Géométrie de l'explosion sphérique de Dai Enkai: Entei (aucune dépendance à Minecraft : ne fait que calculer des positions de
 * particules, que FireEffects envoie ensuite aux joueurs).
 *
 * À chaque tick, la boule de feu est une VRAIE sphère pleine, visible de tous les côtés (devant, derrière, dessus, dessous) :
 * <ul>
 *   <li>une coque extérieure de points répartis régulièrement sur TOUTE la surface (spirale de Fibonacci, pas un dôme), qui tourne
 *       lentement ;</li>
 *   <li>deux coques intérieures plus petites (78 % et 55 % du rayon) : elles donnent de la profondeur, on voit à travers la boule ;</li>
 *   <li>des cercles qui épousent la surface : trois grands cercles inclinés dans des plans différents (qui tournent) et deux anneaux de
 *       latitude qui balaient la sphère du bas vers le haut, comme une onde circulaire qui se forme autour de la boule. Ces cercles sont
 *       toujours posés SUR la sphère, jamais un disque plat.</li>
 * </ul>
 */
final class BlastSphere {

    private BlastSphere() {
    }

    enum Kind { SHELL, INNER, RING, SMOKE }

    interface Sink {
        void point(double x, double y, double z, Kind kind);
    }

    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    /** Émet tous les points de la boule pour ce tick ({@code age} : 1..totalTicks), centrée sur (cx, cy, cz), de rayon {@code radius}. */
    static void emit(double cx, double cy, double cz, double radius, int age, Sink sink) {
        int shell = clamp((int) (radius * 6.0), 40, 400);

        // 1) coque extérieure + coques intérieures : sphère pleine, tout autour
        fibonacci(cx, cy, cz, radius, shell, age * 0.35, Kind.SHELL, sink);
        fibonacci(cx, cy, cz, radius * 0.78, shell / 2, -age * 0.25, Kind.INNER, sink);
        fibonacci(cx, cy, cz, radius * 0.55, shell / 4, age * 0.15, Kind.INNER, sink);

        // 2) trois grands cercles sur la surface, dans trois plans différents (équateur + deux plans inclinés qui tournent)
        int ring = clamp((int) (radius * 2.5), 40, 160);
        circle(cx, cy, cz, radius, ring, normal(0.0, 0.0, 0.0), age * 0.12, sink);
        circle(cx, cy, cz, radius, ring, normal(Math.toRadians(60.0), 0.0, age * 0.06), age * 0.10, sink);
        circle(cx, cy, cz, radius, ring, normal(0.0, Math.toRadians(-60.0), -age * 0.05), -age * 0.09, sink);

        // 3) deux anneaux de latitude qui balaient la sphère du bas vers le haut (l'onde circulaire qui se forme autour de la boule)
        for (int i = 0; i < 2; i++) {
            double phase = (age * 0.07 + i * 0.5) % 1.0;
            double s = -0.92 + 1.84 * phase;                 // hauteur relative sur la sphère : -0.92 .. +0.92
            double rr = Math.sqrt(1.0 - s * s) * radius;     // rayon du cercle à cette hauteur : il suit la surface
            for (int j = 0; j < ring; j++) {
                double a = 2.0 * Math.PI * j / ring + age * 0.2;
                sink.point(cx + Math.cos(a) * rr, cy + s * radius, cz + Math.sin(a) * rr, Kind.RING);
            }
        }

        // 4) un peu de fumée à l'intérieur, un tick sur deux
        if (age % 2 == 0) {
            fibonacci(cx, cy, cz, radius * 0.65, shell / 8, age * 0.5, Kind.SMOKE, sink);
        }
    }

    /** {@code n} points répartis régulièrement sur toute la sphère (pôle à pôle), tournant de {@code spin} radians autour de l'axe vertical. */
    static void fibonacci(double cx, double cy, double cz, double radius, int n, double spin, Kind kind, Sink sink) {
        for (int i = 0; i < n; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / n;
            double ring = Math.sqrt(1.0 - y * y);
            double a = i * GOLDEN_ANGLE + spin;
            sink.point(cx + Math.cos(a) * ring * radius, cy + y * radius, cz + Math.sin(a) * ring * radius, kind);
        }
    }

    /** Normale d'un plan : l'axe vertical, penché de {@code tiltX} autour de X puis de {@code tiltZ} autour de Z, puis tourné de {@code spinY} autour de Y. */
    private static double[] normal(double tiltX, double tiltZ, double spinY) {
        double x = 0.0, y = 1.0, z = 0.0;
        double y1 = y * Math.cos(tiltX) - z * Math.sin(tiltX);
        double z1 = y * Math.sin(tiltX) + z * Math.cos(tiltX);
        double x2 = x * Math.cos(tiltZ) - y1 * Math.sin(tiltZ);
        double y2 = x * Math.sin(tiltZ) + y1 * Math.cos(tiltZ);
        double x3 = x2 * Math.cos(spinY) + z1 * Math.sin(spinY);
        double z3 = -x2 * Math.sin(spinY) + z1 * Math.cos(spinY);
        return new double[] {x3, y2, z3};
    }

    /** Grand cercle (de rayon {@code radius}, sur la sphère) dans le plan de normale {@code n}, tournant de {@code spin} dans son plan. */
    private static void circle(double cx, double cy, double cz, double radius, int points, double[] n, double spin, Sink sink) {
        // deux vecteurs unitaires du plan
        double[] ref = Math.abs(n[1]) > 0.9 ? new double[] {1, 0, 0} : new double[] {0, 1, 0};
        double[] u = cross(n, ref);
        normalize(u);
        double[] v = cross(n, u);
        for (int i = 0; i < points; i++) {
            double a = 2.0 * Math.PI * i / points + spin;
            double c = Math.cos(a) * radius, s = Math.sin(a) * radius;
            sink.point(cx + u[0] * c + v[0] * s, cy + u[1] * c + v[1] * s, cz + u[2] * c + v[2] * s, Kind.RING);
        }
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static void normalize(double[] a) {
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        a[0] /= l;
        a[1] /= l;
        a[2] /= l;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
