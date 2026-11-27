package nolan.bloxpiece.client.humandrill;

import java.util.ArrayList;
import java.util.List;

/**
 * Animations du Humandrill, en mathématiques pures (aucune classe de Minecraft : le même code sert au modèle et aux vérifications
 * hors jeu). Pour chaque image, une POSE décrit le corps de façon intuitive :
 * <ul>
 *   <li>bassin (lacet, accroupissement, fente), torse (torsion, penchement, roulis), tête et mâchoire ;</li>
 *   <li>pour chaque main : la POSITION de la poignée (repère « U » : torse redressé, tourné avec la torsion du torse ; px,
 *       origine au pivot du torse, y vers le bas, -z devant, -x à droite), la DIRECTION de la lame et celle du TRANCHANT.</li>
 * </ul>
 * Le solveur en tire les rotations de chaque pièce : cinématique inverse à deux os pour les bras (épaule, coude) et les jambes
 * (hanche, genou : les pieds restent posés au sol quand il s'accroupit, se fend ou marche), puis le poignet tourne pour que la
 * lame pointe exactement dans la direction voulue, le tranchant en avant dans le sens du coup. Les épées suivent donc une vraie
 * trajectoire (préparation, frappe, récupération) au lieu d'être simplement « collées » au bras.
 * <p>
 * Images clés des attaques : mêmes ticks que HumandrillAttackGoal (côté serveur, dégâts) ; une attaque de la main gauche est le
 * miroir de celle de la main droite. Entre deux clés : interpolation adoucie ; la première et la dernière clé sont la pose de
 * base du moment (garde, repos, course...), donc les transitions sont continues.
 */
public final class HumandrillAnimator {

    // ------------------------------------------------------------------ squelette (px) : mêmes valeurs que MakeHumandrill
    public static final float WAIST_Y = 9.0F, WAIST_Z = 1.0F;
    static final float[] TORSO_PIVOT = {0.0F, -1.5F, 0.0F};
    static final float[] HEAD_PIVOT = {0.0F, -11.0F, -2.5F};
    static final float[] SHOULDER_R = {-7.0F, -9.0F, -0.3F};
    static final float[] SHOULDER_L = {7.0F, -9.0F, -0.3F};
    static final float[] HIP_R = {-3.0F, 2.0F, 0.3F};
    static final float[] HIP_L = {3.0F, 2.0F, 0.3F};
    static final float UPPER = 7.5F, FORE = 7.7F, THIGH = 6.2F, SHIN = 5.5F, FOOT_H = 2.0F, GROUND = 24.0F;
    /** Point de la poignée dans le repère de la main (centre du poing) ; l'avant-bras « porte » la poignée à FORE + 1,3 px. */
    public static final float[] GRIP = {0.0F, 1.3F, 0.2F};
    static final float REACH2 = FORE + 1.3F;
    /** Penchement du torse au repos (dos voûté de babouin). */
    public static final float LEAN = 0.42F;
    /** Mâchoire entrouverte qui serre l'épée de la bouche. */
    public static final float JAW_BITE = 0.24F;
    /**
     * Lame et tranchant de l'épée dans le repère d'arrivée d'ItemInHandLayer (cadrage « thirdperson » d'Ace, que le sabre des
     * Humandrills reprend) : la lame part vers -z (un peu vers +y), le tranchant vers +y.
     */
    public static final float[] B0 = {0.0F, 0.17365F, -0.98481F};
    public static final float[] E0 = {0.0F, 0.98481F, 0.17365F};
    /**
     * Épée de la bouche (repère de la tête, px) : centre de la poignée serré entre les crocs, côté droit de la gueule (le pommeau
     * reste dans la gueule, la garde juste contre la joue droite) ; lame vers la droite, un peu en avant et vers le bas ;
     * tranchant vers l'avant.
     */
    public static final float[] MOUTH = {-1.6F, 0.42F, -8.0F};
    public static final float[] MOUTH_BLADE = {-0.95F, 0.06F, -0.3F};
    public static final float[] MOUTH_EDGE = {0.0F, 0.0F, -1.0F};

    // ------------------------------------------------------------------ entrées / sorties

    /** Ce qu'il faut savoir de l'entité pour une image. */
    public static final class Input {
        public float age;            // ticks (avec fraction)
        public float walkPos, walkSpeed;
        public float headYaw, headPitch;  // degrés, relatifs au corps
        public float guard;          // 0 repos (hors combat) .. 1 en garde (en combat : Humandrill.inCombat)
        public float attackT = -1.0F;     // ticks depuis le début de l'attaque (-1 : aucune)
        public int attackKind;       // 0..6 (HumandrillAttackGoal)
        public boolean left;         // attaque de la main gauche
        public int swords = 1;
        public float landT = 100.0F, landPower;
        public float air;            // 0 au sol .. 1 en l'air
        public float turn;           // degrés par tick (rotation du corps)
        public int durations[];      // durées des attaques (HumandrillAttackGoal.DURATION)
        public float tremble;        // 0..1 : tremblement de la préparation du coup puissant
        public float cadence = 1.0F; // rythme des pas : 1 / échelle du modèle (grand modèle = pas plus lents, sans glisser)
    }

    /** Rotations (x, y, z en radians) de chaque pièce, plus le décalage du bassin (px). */
    public static final class Rig {
        public final float[] waist = new float[3], torso = new float[3], head = new float[3], jaw = new float[3], cloth = new float[3],
                tail = new float[3], tailTip = new float[3];
        public final float[] rArm = new float[3], rFore = new float[3], rHand = new float[3];
        public final float[] lArm = new float[3], lFore = new float[3], lHand = new float[3];
        public final float[] rThigh = new float[3], rShin = new float[3], rFoot = new float[3];
        public final float[] lThigh = new float[3], lShin = new float[3], lFoot = new float[3];
        public float waistDy, waistDz;
    }

    /** Pose intuitive (voir la classe). NaN / null : valeur de la pose de base. */
    static final class Pose {
        float ty, dl, tz, wy, crouch, lunge, headY, jaw;
        boolean lungeLeft;
        float[] rh, rd, re, lh, ld, le;

        Pose copy() {
            Pose p = new Pose();
            p.ty = this.ty;
            p.dl = this.dl;
            p.tz = this.tz;
            p.wy = this.wy;
            p.crouch = this.crouch;
            p.lunge = this.lunge;
            p.headY = this.headY;
            p.jaw = this.jaw;
            p.lungeLeft = this.lungeLeft;
            p.rh = this.rh.clone();
            p.rd = this.rd.clone();
            p.re = this.re.clone();
            p.lh = this.lh.clone();
            p.ld = this.ld.clone();
            p.le = this.le.clone();
            return p;
        }
    }

    /** Image clé d'une attaque (main droite) : NaN / null = pose de base. */
    static final class Key {
        final float t;
        float ty = Float.NaN, dl = Float.NaN, tz = Float.NaN, wy = Float.NaN, crouch = Float.NaN, lunge = Float.NaN,
                headY = Float.NaN, jaw = Float.NaN;
        float[] rh, rd, re, lh, ld, le;

        Key(float t) {
            this.t = t;
        }

        Key body(float ty, float dl, float wy, float crouch, float lunge) {
            this.ty = ty;
            this.dl = dl;
            this.wy = wy;
            this.crouch = crouch;
            this.lunge = lunge;
            return this;
        }

        Key head(float y) {
            this.headY = y;
            return this;
        }

        Key jaw(float j) {
            this.jaw = j;
            return this;
        }

        Key r(float hx, float hy, float hz, float dx, float dy, float dz, float ex, float ey, float ez) {
            this.rh = new float[] {hx, hy, hz};
            this.rd = new float[] {dx, dy, dz};
            this.re = new float[] {ex, ey, ez};
            return this;
        }

        Key l(float hx, float hy, float hz, float dx, float dy, float dz, float ex, float ey, float ez) {
            this.lh = new float[] {hx, hy, hz};
            this.ld = new float[] {dx, dy, dz};
            this.le = new float[] {ex, ey, ez};
            return this;
        }

        /** Même main gauche que la clé précédente donnée (position, lame, tranchant). */
        Key l(Key k) {
            this.lh = k.lh;
            this.ld = k.ld;
            this.le = k.le;
            return this;
        }

        /** Miroir gauche / droite (attaque de la main gauche). */
        Key mirror() {
            Key m = new Key(this.t);
            m.ty = -this.ty;
            m.dl = this.dl;
            m.tz = -this.tz;
            m.wy = -this.wy;
            m.crouch = this.crouch;
            m.lunge = this.lunge;
            m.headY = -this.headY;
            m.jaw = this.jaw;
            m.rh = mx(this.lh);
            m.rd = mx(this.ld);
            m.re = mx(this.le);
            m.lh = mx(this.rh);
            m.ld = mx(this.rd);
            m.le = mx(this.re);
            return m;
        }

        private static float[] mx(float[] v) {
            return v == null ? null : new float[] {-v[0], v[1], v[2]};
        }
    }

    // ------------------------------------------------------------------ poses de base

    private static Pose pose(float ty, float dl, float crouch, float[] rh, float[] rd, float[] re, float[] lh, float[] ld, float[] le) {
        Pose p = new Pose();
        p.ty = ty;
        p.dl = dl;
        p.crouch = crouch;
        p.rh = rh;
        p.rd = rd;
        p.re = re;
        p.lh = lh;
        p.ld = ld;
        p.le = le;
        return p;
    }

    private static float[] v(float x, float y, float z) {
        return new float[] {x, y, z};
    }

    /** Lame posée sur l'épaule droite (repos) : position du poing et direction de la lame (repère U). Avant IDLE (ordre d'init). */
    static final float[] SHOULDER_REST_HAND = {-8.3F, -11.3F, -10.0F};
    static final float[] SHOULDER_REST_BLADE = {-0.1F, -0.22F, 0.97F};

    /**
     * Repos (hors combat) : chaque lame est posée À PLAT sur son épaule, comme un soldat qui porte son arme : poing devant
     * l'épaule, lame qui passe au-dessus de l'épaule et part vers l'arrière en remontant un peu, plat de la lame vers le
     * haut (tranchant vers l'extérieur). Deux épées : une sur chaque épaule ; une seule : sur l'épaule droite.
     */
    static final Pose IDLE = pose(0.0F, 0.0F, 0.8F,
            SHOULDER_REST_HAND, SHOULDER_REST_BLADE, v(-1.0F, 0.0F, 0.0F),
            mirror(SHOULDER_REST_HAND), mirror(SHOULDER_REST_BLADE), v(1.0F, 0.0F, 0.0F));

    static float[] mirror(float[] a) {
        return new float[] {-a[0], a[1], a[2]};
    }

    /** Repos, main gauche vide (une seule épée) : le bras pend, poing vers l'avant. */
    static final float[] IDLE_EMPTY_L = {8.0F, 7.0F, -6.0F};
    /** Garde : penché, genoux fléchis, lames devant lui, pointes un peu relevées, tranchant vers le bas. */
    static final Pose GUARD = pose(0.0F, 0.12F, 1.6F,
            v(-8.5F, 3.0F, -11.0F), v(-0.2F, -0.25F, -1.0F), v(0.0F, 1.0F, -0.25F),
            v(8.5F, 3.0F, -11.0F), v(0.2F, -0.25F, -1.0F), v(0.0F, 1.0F, -0.25F));
    /** Course : corps très bas et penché (seuls dl et crouch servent : les lames gardent la pose de garde ou de repos). */
    static final Pose RUN = pose(0.0F, 0.38F, 1.2F,
            v(-9.0F, 3.0F, 4.0F), v(-0.3F, -0.25F, 1.0F), v(0.0F, 1.0F, 0.25F),
            v(9.0F, 3.0F, 4.0F), v(0.3F, -0.25F, 1.0F), v(0.0F, 1.0F, 0.25F));

    // ------------------------------------------------------------------ attaques (main droite ; voir HumandrillAttackGoal)

    static final Key[][] KEYS = new Key[7][];

    static {
        // 0 — horizontal : lame tirée en arrière à droite, torse tordu ; balayage devant lui jusqu'à gauche ; récupération
        Key lAway = new Key(0).l(9.0F, -9.0F, 3.0F, 0.3F, -0.7F, 0.6F, 1.0F, 0.0F, 0.0F); // bras gauche écarté du chemin de la lame
        KEYS[0] = new Key[] {
                new Key(7).body(0.8F, 0.0F, 0.3F, 2.0F, 0.0F).r(-12, -4, 1, -0.55F, -0.05F, 0.83F, -0.83F, 0, -0.55F)
                        .l(7, 0, -10, 0.3F, -0.2F, -1, 0, 1, -0.2F),
                new Key(9).body(0.3F, 0.0F, 0.1F, 2.0F, 0.4F).r(-11, -3.5F, -9, -1, 0, -0.2F, 0.2F, 0, -1),
                new Key(11).body(-0.4F, 0.1F, -0.2F, 2.2F, 1.0F).r(-2, -3, -15, 0.5F, 0, -0.87F, 0.87F, 0, 0.5F).l(lAway),
                new Key(14).body(-0.9F, 0.1F, -0.35F, 2.2F, 1.0F).r(4, 0, -12, 0.85F, 0.25F, -0.35F, 0.3F, 0.2F, 0.9F).l(lAway),
        };
        // 1 — vertical : lame levée devant puis derrière la tête (torse cambré), abattue devant lui, fente
        Key lLow = new Key(0).l(8, 2, -10, 0.3F, -0.4F, -1, 0, 1, -0.4F);
        KEYS[1] = new Key[] {
                new Key(5).body(0.1F, -0.15F, 0.0F, 1.2F, 0.0F).r(-10, -10, -12, -0.15F, -1, 0.1F, 0, 0, -1),
                new Key(9).body(0.15F, -0.35F, 0.0F, 0.8F, 0.0F).r(-5, -19, -1, 0, -0.2F, 1, 0, -1, -0.2F)
                        .l(7, -4, -10, 0.2F, -0.4F, -1, 0, 1, -0.4F).jaw(0.3F),
                new Key(11).body(0.05F, 0.2F, 0.0F, 1.5F, 0.3F).r(-5.5F, -14, -14, 0, -1, -0.15F, 0, 0.15F, -1).l(lLow),
                new Key(13).body(0.0F, 0.55F, 0.0F, 2.6F, 1.0F).r(-3, 1.5F, -15, 0.05F, 0.5F, -0.86F, 0, 0.86F, 0.5F).l(lLow),
                new Key(16).body(0.0F, 0.5F, 0.0F, 2.6F, 1.0F).r(-3, 2, -14.5F, 0.05F, 0.52F, -0.85F, 0, 0.85F, 0.52F).l(lLow),
        };
        // 2 — diagonal : de l'épaule droite (haut) vers la hanche gauche (bas)
        Key lAway2 = new Key(0).l(9, -7, 1, 0.3F, -0.6F, 0.7F, 1, 0, 0);
        KEYS[2] = new Key[] {
                new Key(7).body(0.6F, -0.1F, 0.25F, 1.6F, 0.0F).r(-12, -14, -2, -0.45F, -0.75F, 0.48F, 0.6F, -0.3F, -0.75F)
                        .l(7, 0, -10, 0.3F, -0.2F, -1, 0, 1, -0.2F),
                new Key(9).body(0.1F, 0.0F, 0.1F, 1.8F, 0.4F).r(-8, -8, -14, -0.5F, -0.55F, -0.67F, 0.6F, 0.6F, -0.5F),
                new Key(11).body(-0.45F, 0.15F, -0.15F, 2.2F, 1.0F).r(1, 1, -14, 0.55F, 0.5F, -0.67F, 0.6F, 0.5F, 0.6F).l(lAway2),
                new Key(14).body(-0.75F, 0.15F, -0.3F, 2.2F, 1.0F).r(6, 4, -9, 0.75F, 0.45F, -0.1F, 0.3F, 0.5F, 0.8F).l(lAway2),
        };
        // 3 — rapide : bras ramené une fraction de seconde, estoc tendu
        KEYS[3] = new Key[] {
                new Key(3).body(0.35F, 0.05F, 0.1F, 1.8F, 0.0F).r(-9, -2, -4, -0.1F, -0.1F, -1, 0, 1, -0.1F),
                new Key(5).body(-0.25F, 0.15F, -0.1F, 2.0F, 1.2F).r(-4, -2, -18, 0.05F, -0.02F, -1, 0, 1, 0),
                new Key(7).body(-0.2F, 0.12F, -0.1F, 2.0F, 1.2F).r(-4.5F, -1.5F, -17, 0.05F, 0, -1, 0, 1, 0),
        };
        // 4 — puissant : longue préparation (accroupi, torse tordu, lame au-dessus de l'épaule, main gauche qui vise),
        // tremblement, frappe diagonale très ample, grande fente
        KEYS[4] = new Key[] {
                new Key(5).body(0.5F, -0.1F, 0.2F, 2.2F, 0.0F).r(-11, -9, -11, -0.2F, -1, 0.1F, 0, 0, -1)
                        .l(6, -2, -12, 0.15F, -0.25F, -1, 0, 1, -0.25F),
                new Key(10).body(1.0F, -0.2F, 0.45F, 3.0F, 0.0F).r(-11, -13, 4, -0.35F, -0.55F, 0.75F, 0.5F, -0.6F, -0.6F)
                        .l(6, -3, -13, 0.15F, -0.25F, -1, 0, 1, -0.25F).jaw(0.35F),
                new Key(18).body(1.1F, -0.25F, 0.5F, 3.4F, 0.0F).r(-12, -14, 5, -0.35F, -0.55F, 0.75F, 0.5F, -0.6F, -0.6F)
                        .l(6, -3.5F, -13.5F, 0.15F, -0.25F, -1, 0, 1, -0.25F).jaw(0.35F),
                new Key(21).body(0.2F, 0.1F, 0.1F, 3.0F, 0.6F).r(-9, -9, -14, -0.7F, -0.4F, -0.6F, 0.6F, 0.3F, -0.75F)
                        .l(9, -2, -6, 0.3F, -0.3F, -1, 0, 1, -0.3F),
                new Key(23).body(-0.6F, 0.45F, -0.3F, 3.0F, 1.4F).r(0, 0, -16, 0.6F, 0.3F, -0.74F, 0.6F, 0.6F, 0.5F)
                        .l(10, -8, 2, 0.3F, -0.7F, 0.6F, 1, 0, 0),
                new Key(27).body(-1.0F, 0.4F, -0.45F, 3.0F, 1.4F).r(6, 1, -11, 0.85F, 0.2F, -0.3F, 0.3F, 0.3F, 0.9F)
                        .l(10, -8, 2, 0.3F, -0.7F, 0.6F, 1, 0, 0),
                new Key(30).body(-0.5F, 0.3F, -0.2F, 2.4F, 0.8F).l(11, -4, -8, 0.35F, -0.75F, -0.55F, 1, 0, 0),
        };
        // 5 — combo (2 épées) : droite horizontale, gauche horizontale en revers, diagonale droite, croix des deux lames
        KEYS[5] = new Key[] {
                new Key(5).body(0.6F, 0.0F, 0.25F, 1.8F, 0.0F).r(-12, -4, 0, -0.55F, -0.05F, 0.83F, -0.83F, 0, -0.55F)
                        .l(7, -1, -9, 0.3F, -0.3F, -1, 0, 1, -0.3F),
                new Key(6.5F).body(0.2F, 0.05F, 0.1F, 1.9F, 0.4F).r(-11, -3.5F, -9, -1, 0, -0.2F, 0.2F, 0, -1),
                new Key(8).body(-0.35F, 0.1F, -0.15F, 2.0F, 1.0F).r(-2, -3, -15, 0.5F, 0, -0.87F, 0.87F, 0, 0.5F)
                        .l(11, -5, -6, 0.6F, -0.4F, 0.6F, 0.8F, 0, -0.5F),
                new Key(10).body(-0.55F, 0.1F, -0.25F, 2.0F, 0.8F).r(4, 1, -12, 0.85F, 0.25F, -0.35F, 0.3F, 0.2F, 0.9F)
                        .l(12, -8, 1, 0.55F, -0.2F, 0.8F, 0.83F, 0, -0.55F),
                new Key(11.5F).body(-0.2F, 0.05F, -0.1F, 1.9F, 0.4F).r(-4, 2, -14, 0, 0.2F, -1, 0, 1, 0.2F)
                        .l(11, -3.5F, -9, 1, 0, -0.2F, -0.2F, 0, -1),
                new Key(13).body(0.35F, 0.1F, 0.15F, 2.0F, 1.0F).r(-9, -6, -10, -0.3F, -0.8F, 0.3F, 0, 0, -1)
                        .l(2, -3, -15, -0.5F, 0, -0.87F, -0.87F, 0, 0.5F),
                new Key(15).body(0.55F, 0.05F, 0.2F, 2.0F, 0.6F).r(-11, -14, -2, -0.45F, -0.75F, 0.48F, 0.6F, -0.3F, -0.75F)
                        .l(-4, 1, -12, -0.85F, 0.25F, -0.35F, -0.3F, 0.2F, 0.9F),
                new Key(17).body(0.55F, 0.0F, 0.2F, 1.8F, 0.5F).r(-10, -13, -3, -0.45F, -0.75F, 0.48F, 0.6F, -0.3F, -0.75F)
                        .l(-5, 2, -11, -0.85F, 0.25F, -0.35F, -0.3F, 0.2F, 0.9F),
                new Key(19).body(0.1F, 0.0F, 0.1F, 2.0F, 0.6F).r(-8, -8, -14, -0.5F, -0.55F, -0.67F, 0.6F, 0.6F, -0.5F)
                        .l(8, 1, -10, 0.3F, -0.3F, -1, 0, 1, -0.3F),
                new Key(21).body(-0.4F, 0.2F, -0.15F, 2.2F, 1.0F).r(1, 1, -14, 0.55F, 0.45F, -0.7F, 0.6F, 0.5F, 0.6F)
                        .l(9, 0, -9, 0.3F, -0.3F, -1, 0, 1, -0.3F),
                new Key(23).body(-0.1F, 0.0F, 0.0F, 1.6F, 0.4F).r(-9, -5, -12, -0.4F, -0.8F, -0.3F, 0, 0, -1)
                        .l(9, -5, -12, 0.4F, -0.8F, -0.3F, 0, 0, -1),
                new Key(25).body(0.0F, -0.3F, 0.0F, 1.2F, 0.3F).r(-8, -18, -4, -0.5F, -0.6F, 0.6F, 0.5F, 0.3F, -0.8F)
                        .l(8, -18, -4, 0.5F, -0.6F, 0.6F, -0.5F, 0.3F, -0.8F).jaw(0.3F),
                new Key(26).body(0.0F, 0.1F, 0.0F, 1.8F, 0.5F).r(-8, -8, -15, -0.3F, -0.6F, -0.75F, 0.3F, 0.4F, -0.85F)
                        .l(8, -8, -15, 0.3F, -0.6F, -0.75F, -0.3F, 0.4F, -0.85F),
                new Key(27.5F).body(0.0F, 0.5F, 0.0F, 2.6F, 1.0F).r(2.5F, 2, -14, 0.5F, 0.35F, -0.79F, 0.5F, 0.6F, 0.6F)
                        .l(-2.5F, 5, -12, -0.5F, 0.35F, -0.79F, -0.5F, 0.6F, 0.6F),
                new Key(31).body(0.0F, 0.4F, 0.0F, 2.4F, 1.0F).r(6, 2, -11, 0.85F, 0.2F, -0.3F, 0.2F, 0.4F, 0.9F)
                        .l(-6, 5, -9, -0.85F, 0.2F, -0.3F, -0.2F, 0.4F, 0.9F),
                new Key(34).body(0.0F, 0.25F, 0.0F, 2.0F, 0.5F).r(-3, -3, -16, -0.2F, -0.3F, -1, 0, 1, -0.3F)
                        .l(3, 6, -12, 0.2F, 0.1F, -1, 0, 1, 0.1F),
        };
        // 6 — trois épées : bras croisés devant lui (lames en X), tête tournée (lame de la bouche en arrière), puis bond, les
        // deux lames s'ouvrent vers l'extérieur et la tête balaie avec la lame de la bouche
        KEYS[6] = new Key[] {
                new Key(9).body(0.0F, -0.15F, 0.0F, 2.6F, 0.0F).head(0.7F).r(4, -2, -15, -0.5F, -0.25F, -0.83F, 0, 1, -0.3F)
                        .l(-4, 3, -10, 0.5F, -0.25F, -0.83F, 0, 1, -0.3F),
                new Key(13).body(0.0F, 0.3F, 0.0F, 2.4F, 1.5F).head(0.1F).r(-6, -2, -15, -0.75F, -0.1F, -0.65F, -0.6F, 0, 0.75F)
                        .l(6, -1, -15, 0.75F, -0.1F, -0.65F, 0.6F, 0, 0.75F),
                new Key(15).body(0.0F, 0.35F, 0.0F, 2.4F, 1.5F).head(-0.55F).r(-12, -2, -9, -0.85F, 0, -0.5F, -0.5F, 0, 0.85F)
                        .l(12, -1, -9, 0.85F, 0, -0.5F, 0.5F, 0, 0.85F),
                new Key(19).body(0.0F, 0.3F, 0.0F, 2.2F, 1.5F).head(-0.4F).r(-12, 0, 0, -0.6F, 0.1F, 0.8F, 0.8F, 0, 0.6F)
                        .l(12, 0, 0, 0.6F, 0.1F, 0.8F, -0.8F, 0, 0.6F),
        };
    }

    // ------------------------------------------------------------------ résolution

    private HumandrillAnimator() {
    }

    static float smooth(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }

    static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    static float[] lerp(float t, float[] a, float[] b) {
        return new float[] {lerp(t, a[0], b[0]), lerp(t, a[1], b[1]), lerp(t, a[2], b[2])};
    }

    static Pose lerp(float t, Pose a, Pose b) {
        Pose p = new Pose();
        p.ty = lerp(t, a.ty, b.ty);
        p.dl = lerp(t, a.dl, b.dl);
        p.tz = lerp(t, a.tz, b.tz);
        p.wy = lerp(t, a.wy, b.wy);
        p.crouch = lerp(t, a.crouch, b.crouch);
        p.lunge = lerp(t, a.lunge, b.lunge);
        p.headY = lerp(t, a.headY, b.headY);
        p.jaw = lerp(t, a.jaw, b.jaw);
        p.lungeLeft = t < 0.5F ? a.lungeLeft : b.lungeLeft;
        p.rh = lerp(t, a.rh, b.rh);
        p.rd = lerp(t, a.rd, b.rd);
        p.re = lerp(t, a.re, b.re);
        p.lh = lerp(t, a.lh, b.lh);
        p.ld = lerp(t, a.ld, b.ld);
        p.le = lerp(t, a.le, b.le);
        return p;
    }

    /** Une clé complétée par la pose de base. */
    static Pose resolve(Key k, Pose base, boolean lungeLeft) {
        Pose p = base.copy();
        if (k == null) {
            return p;
        }
        p.ty = Float.isNaN(k.ty) ? p.ty : k.ty;
        p.dl = Float.isNaN(k.dl) ? p.dl : k.dl;
        p.tz = Float.isNaN(k.tz) ? p.tz : k.tz;
        p.wy = Float.isNaN(k.wy) ? p.wy : k.wy;
        p.crouch = Float.isNaN(k.crouch) ? p.crouch : k.crouch;
        p.lunge = Float.isNaN(k.lunge) ? p.lunge : k.lunge;
        p.headY = Float.isNaN(k.headY) ? p.headY : k.headY;
        p.jaw = Float.isNaN(k.jaw) ? p.jaw : k.jaw;
        p.lungeLeft = lungeLeft;
        if (k.rh != null) {
            p.rh = k.rh.clone();
            p.rd = k.rd.clone();
            p.re = k.re.clone();
        }
        if (k.lh != null) {
            p.lh = k.lh.clone();
            p.ld = k.ld.clone();
            p.le = k.le.clone();
        }
        return p;
    }

    /** Pose d'une attaque au tick t (clés de début et de fin = pose de base). */
    static Pose attackPose(int kind, boolean left, float t, int duration, Pose base) {
        Key[] src = KEYS[kind];
        List<Key> keys = new ArrayList<>();
        keys.add(null);
        for (Key k : src) {
            keys.add(left ? k.mirror() : k);
        }
        keys.add(null);
        float[] times = new float[keys.size()];
        times[0] = 0.0F;
        for (int i = 0; i < src.length; i++) {
            times[i + 1] = src[i].t;
        }
        times[times.length - 1] = duration;
        int i = 0;
        while (i < times.length - 2 && t > times[i + 1]) {
            i++;
        }
        float u = (t - times[i]) / Math.max(0.001F, times[i + 1] - times[i]);
        Pose a = resolve(keys.get(i), base, left);
        Pose b = resolve(keys.get(i + 1), base, left);
        return lerp(smooth(u), a, b);
    }

    /** Pose de base : repos / garde / course, marche, saut, réception, virage, respiration. */
    static Pose basePose(Input in) {
        float run = clamp01((in.walkSpeed - 0.55F) / 0.35F);
        Pose idle = IDLE.copy();
        if (in.swords < 2) {
            idle.lh = IDLE_EMPTY_L.clone();
            idle.ld = v(0.2F, 0.3F, -1.0F);
            idle.le = v(0.0F, 1.0F, 0.3F);
        }
        Pose p = lerp(smooth(in.guard), idle, GUARD);
        // course : seulement le CORPS (plus bas, plus penché) ; les lames gardent la pose de base : devant lui, prêtes à
        // frapper, tant qu'il est en combat (il poursuit sa cible lames en avant), ou posées sur les épaules hors combat
        float r = smooth(run);
        p.dl = lerp(r, p.dl, RUN.dl);
        p.crouch = lerp(r, p.crouch, RUN.crouch);
        float speed = Math.min(1.0F, in.walkSpeed);
        float phase = in.walkPos * 0.6662F * 0.9F * in.cadence;
        float swing = (float) Math.sin(phase) * speed;
        // marche : contre-rotation du torse, balancement des mains (moins en garde, plus en course)
        p.ty += 0.09F * swing;
        p.wy -= 0.07F * swing;
        // balancement des mains : faible au repos (les lames restent posées sur les épaules), plus marqué en garde et en course
        float arm = 0.4F + 0.4F * in.guard + 0.6F * run;
        p.rh[2] += arm * swing;
        p.lh[2] -= arm * swing;
        // respiration
        float breath = (float) Math.sin(in.age * 0.08F);
        p.dl += 0.025F * breath;
        p.rh[1] += 0.3F * breath;
        p.lh[1] += 0.3F * breath;
        // virage : roulis et torsion dans le sens du virage, la tête devance
        float turn = Math.max(-12.0F, Math.min(12.0F, in.turn));
        p.tz -= turn * 0.018F;
        p.ty += turn * 0.012F;
        p.headY += turn * 0.02F;
        // en l'air : bras écartés et relevés, corps redressé
        if (in.air > 0.0F) {
            p.rh = lerp(in.air * 0.5F, p.rh, v(-12.0F, -6.0F, -4.0F));
            p.lh = lerp(in.air * 0.5F, p.lh, v(12.0F, -6.0F, -4.0F));
            p.dl -= 0.12F * in.air;
        }
        // réception : écrasement rapide puis remontée (selon la hauteur de la chute)
        if (in.landT >= 0.0F && in.landT < 12.0F && in.landPower > 0.0F) {
            float k = in.landT < 2.0F ? in.landT / 2.0F : 1.0F - (in.landT - 2.0F) / 10.0F;
            k = smooth(k) * in.landPower;
            p.crouch += 4.0F * k;
            p.dl += 0.35F * k;
            p.rh = lerp(k * 0.5F, p.rh, v(-13.0F, 4.0F, -6.0F));
            p.lh = lerp(k * 0.5F, p.lh, v(13.0F, 4.0F, -6.0F));
        }
        p.jaw = in.swords >= 3 ? JAW_BITE : 0.05F + 0.04F * (float) Math.max(0.0, Math.sin(in.age * 0.05F));
        return p;
    }

    static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    /** Calcule toutes les rotations de l'image. */
    public static void solve(Input in, Rig rig) {
        Pose base = basePose(in);
        Pose p = base;
        if (in.attackT >= 0.0F && in.attackKind >= 0 && in.attackKind < KEYS.length) {
            int duration = in.durations != null ? in.durations[in.attackKind] : 30;
            if (in.attackT < duration) {
                boolean left = in.left && in.swords >= 2 && in.attackKind <= 3;
                p = attackPose(in.attackKind, left, in.attackT, duration, base);
                if (in.swords >= 3) {
                    p.jaw = JAW_BITE; // l'épée de la bouche reste serrée
                }
            }
        }
        if (in.tremble > 0.0F) {
            float j = 0.45F * in.tremble;
            p.rh[0] += j * (float) Math.sin(in.age * 2.9F);
            p.rh[1] += j * (float) Math.sin(in.age * 3.7F + 1.0F);
            p.ty += 0.02F * in.tremble * (float) Math.sin(in.age * 3.3F);
        }
        apply(p, in, rig);
    }

    // ------------------------------------------------------------------ pose -> rotations

    static void apply(Pose p, Input in, Rig rig) {
        float tx = LEAN + p.dl;
        // bassin : lacet, s'abaisse en s'accroupissant, avance un peu dans la fente ; rebond de la marche
        float speed = Math.min(1.0F, in.walkSpeed);
        float phase = in.walkPos * 0.6662F * 0.9F * in.cadence;
        float run = clamp01((in.walkSpeed - 0.55F) / 0.35F);
        float bob = (0.5F + 0.7F * run) * Math.abs((float) Math.sin(phase)) * speed;
        rig.waist[0] = 0.0F;
        rig.waist[1] = p.wy;
        rig.waist[2] = 0.0F;
        rig.waistDy = p.crouch - bob;
        rig.waistDz = -1.2F * p.lunge;
        // torse
        rig.torso[0] = tx;
        rig.torso[1] = p.ty;
        rig.torso[2] = p.tz;
        float[] rTorso = rotZYX(tx, p.ty, p.tz);
        float[] u = rotY(p.ty);
        float[] m = mul(transpose(u), rTorso); // torse dans le repère U
        float[] mT = transpose(m);
        // tête : regarde la cible (lacet et tangage relatifs au corps), garde le visage droit malgré le dos voûté
        // avec une épée dans la bouche, la tête suit à moitié la torsion du torse (la lame ne balaie pas l'épaule droite)
        float follow = in.swords >= 3 ? 0.5F : 1.0F;
        float yawRel = (float) Math.toRadians(in.headYaw) - follow * p.ty - p.wy + p.headY;
        yawRel = Math.max(-1.3F, Math.min(1.3F, yawRel));
        float pitch = Math.max(-0.6F, Math.min(0.5F, (float) Math.toRadians(in.headPitch)));
        float[] headU = mul(rotY(yawRel), rotX(pitch));
        euler(mul(mT, headU), rig.head);
        rig.jaw[0] = p.jaw;
        rig.jaw[1] = 0.0F;
        rig.jaw[2] = 0.0F;
        // bras
        arm(m, mT, SHOULDER_R, p.rh, p.rd, p.re, v(-0.6F, 0.75F, 0.3F), rig.rArm, rig.rFore, rig.rHand);
        arm(m, mT, SHOULDER_L, p.lh, p.ld, p.le, v(0.6F, 0.75F, 0.3F), rig.lArm, rig.lFore, rig.lHand);
        // jambes : pieds au sol (sauf en l'air), pas de marche, fente
        float hipY = WAIST_Y + rig.waistDy;
        float ankleY = GROUND - FOOT_H - hipY;  // repère du bassin
        float stride = (2.8F + 1.8F * run) * speed;
        float lift = (1.4F + 1.2F * run) * speed;
        float wide = 0.4F * Math.min(3.0F, p.crouch);
        float frontR = p.lungeLeft ? -3.0F * p.lunge : 4.2F * p.lunge;
        float frontL = p.lungeLeft ? 4.2F * p.lunge : -3.0F * p.lunge;
        for (int side = 0; side < 2; side++) {
            boolean right = side == 0;
            float ph = phase + (right ? 0.0F : (float) Math.PI);
            float[] hip = right ? HIP_R : HIP_L;
            float tz = 0.3F - stride * (float) Math.sin(ph) - (right ? frontR : frontL) - rig.waistDz;
            float ty = ankleY - lift * Math.max(0.0F, (float) Math.cos(ph));
            float tX = hip[0] + (right ? -wide : wide);
            if (in.air > 0.0F) { // en l'air : jambes repliées sous lui
                ty = lerp(in.air, ty, hip[1] + 8.0F);
                tz = lerp(in.air, tz, hip[2] - 2.0F + (right ? -1.0F : 1.0F));
            }
            float toe = in.air > 0.0F ? 0.35F * in.air : 0.15F * speed * Math.max(0.0F, (float) Math.cos(ph));
            leg(hip, v(tX, ty + hip[1] * 0.0F, tz), toe, right ? rig.rThigh : rig.lThigh, right ? rig.rShin : rig.lShin,
                    right ? rig.rFoot : rig.lFoot);
        }
        // pagne : suit la cuisse la plus avancée
        rig.cloth[0] = Math.min(0.0F, Math.min(rig.rThigh[0], rig.lThigh[0])) * 0.8F + 0.05F;
        rig.cloth[1] = 0.0F;
        rig.cloth[2] = 0.0F;
        // queue de babouin : la base se dresse, le bout retombe ; elle se balance en marchant et en garde
        float sway = (float) Math.sin(in.age * 0.09F) * 0.12F + swingTail(phase, speed);
        rig.tail[0] = 0.75F + 0.15F * in.guard - 0.25F * run;
        rig.tail[1] = sway;
        rig.tail[2] = 0.0F;
        rig.tailTip[0] = -1.35F + 0.4F * run;
        rig.tailTip[1] = sway * 0.6F;
        rig.tailTip[2] = 0.0F;
    }

    private static float swingTail(float phase, float speed) {
        return (float) Math.sin(phase * 0.5F) * 0.25F * speed;
    }

    /** Bras : cinématique inverse (épaule, coude) vers la poignée, puis poignet orienté pour la lame. */
    static void arm(float[] m, float[] mT, float[] shoulderLocal, float[] target, float[] blade, float[] edge, float[] pole,
            float[] outArm, float[] outFore, float[] outHand) {
        float[] s = apply(m, shoulderLocal);
        float[][] chain = twoBone(s, target, pole, UPPER, REACH2);
        float[] y = chain[0], fdir = chain[1];
        float[] perp = sub(fdir, scale(y, dot(fdir, y)));
        float[] z = len(perp) < 1.0E-3F ? norm(perpTo(y, pole)) : scale(norm(perp), -1.0F); // l'avant-bras plie vers -z
        float[] x = cross(y, z);
        float[] armU = cols(x, y, z);
        float elbow = -(float) Math.acos(Math.max(-1.0F, Math.min(1.0F, dot(y, fdir))));
        euler(mul(mT, armU), outArm);
        outFore[0] = elbow;
        outFore[1] = 0.0F;
        outFore[2] = 0.0F;
        float[] foreU = mul(armU, rotX(elbow));
        float[] want = mul(basis(blade, naturalEdge(blade, edge, fdir)), transpose(basis(B0, E0)));
        euler(mul(transpose(foreU), want), outHand);
    }

    /**
     * Tranchant réellement donné à la lame : le tranchant « naturel » du poing (dans le prolongement de l'avant-bras, comme une
     * épée tenue normalement), tourné autour de la lame vers le tranchant voulu par l'image clé (sens du coup), de 70° au plus :
     * le poignet ne se tord jamais à l'envers.
     */
    static float[] naturalEdge(float[] blade, float[] wanted, float[] forearm) {
        float[] d = norm(blade);
        float[] nat = sub(forearm, scale(d, dot(forearm, d)));
        float[] w = norm(perpTo(d, wanted));
        if (len(nat) < 0.15F) {
            return w; // lame dans l'axe de l'avant-bras : le tranchant voulu
        }
        nat = norm(nat);
        float angle = (float) Math.atan2(dot(cross(nat, w), d), dot(nat, w));
        angle = Math.max(-MAX_EDGE_TURN, Math.min(MAX_EDGE_TURN, angle));
        return add(scale(nat, (float) Math.cos(angle)), scale(cross(d, nat), (float) Math.sin(angle)));
    }

    static final float MAX_EDGE_TURN = 1.22F;

    /** Jambe : cinématique inverse (hanche, genou vers l'avant) vers la cheville ; pied à plat (plus la pointe relevée). */
    static void leg(float[] hip, float[] ankle, float toe, float[] outThigh, float[] outShin, float[] outFoot) {
        float[][] chain = twoBone(hip, ankle, v(0.0F, 0.15F, -1.0F), THIGH, SHIN);
        float[] y = chain[0], fdir = chain[1];
        float[] perp = sub(fdir, scale(y, dot(fdir, y)));
        float[] z = len(perp) < 1.0E-3F ? v(0.0F, 0.0F, 1.0F) : norm(perp); // le tibia plie vers +z (genou devant)
        float[] x = cross(y, z);
        float[] thigh = cols(x, y, z);
        float knee = (float) Math.acos(Math.max(-1.0F, Math.min(1.0F, dot(y, fdir))));
        euler(thigh, outThigh);
        outShin[0] = knee;
        outShin[1] = 0.0F;
        outShin[2] = 0.0F;
        float[] shin = mul(thigh, rotX(knee));
        euler(mul(transpose(shin), rotX(-toe)), outFoot);
    }

    /** Deux os : renvoie {direction du 1er os, direction du 2e os} (coude du côté de pole). */
    static float[][] twoBone(float[] a, float[] target, float[] pole, float l1, float l2) {
        float[] d = sub(target, a);
        float dist = len(d);
        float[] h = dist < 1.0E-4F ? v(0.0F, 1.0F, 0.0F) : scale(d, 1.0F / dist);
        dist = Math.max(Math.abs(l1 - l2) + 0.5F, Math.min(l1 + l2 - 0.05F, dist));
        float c = (l1 * l1 - l2 * l2 + dist * dist) / (2.0F * dist);
        float hh = (float) Math.sqrt(Math.max(0.0F, l1 * l1 - c * c));
        float[] p = norm(perpTo(h, pole));
        float[] elbow = add(scale(h, c), scale(p, hh));
        float[] end = scale(h, dist);
        return new float[][] {norm(elbow), norm(sub(end, elbow))};
    }

    /** Composante de v perpendiculaire à n (n unitaire) ; un axe de secours si v est parallèle à n. */
    static float[] perpTo(float[] n, float[] v) {
        float[] p = sub(v, scale(n, dot(v, n)));
        if (len(p) < 1.0E-3F) {
            p = sub(v(0.0F, 1.0F, 0.0F), scale(n, n[1]));
            if (len(p) < 1.0E-3F) {
                p = sub(v(0.0F, 0.0F, 1.0F), scale(n, n[2]));
            }
        }
        return p;
    }

    // ------------------------------------------------------------------ petite algèbre (matrices 3x3 en lignes)

    static float[] rotX(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[] {1, 0, 0, 0, c, -s, 0, s, c};
    }

    static float[] rotY(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[] {c, 0, s, 0, 1, 0, -s, 0, c};
    }

    static float[] rotZ(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[] {c, -s, 0, s, c, 0, 0, 0, 1};
    }

    /** Ordre de ModelPart : Rz · Ry · Rx. */
    public static float[] rotZYX(float x, float y, float z) {
        return mul(rotZ(z), mul(rotY(y), rotX(x)));
    }

    public static float[] mul(float[] a, float[] b) {
        float[] r = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return r;
    }

    public static float[] transpose(float[] a) {
        return new float[] {a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8]};
    }

    public static float[] apply(float[] m, float[] v) {
        return new float[] {m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
                m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
    }

    /** Matrice dont les colonnes sont x, y, z. */
    static float[] cols(float[] x, float[] y, float[] z) {
        return new float[] {x[0], y[0], z[0], x[1], y[1], z[1], x[2], y[2], z[2]};
    }

    /** Base orthonormée {lame, tranchant, lame x tranchant} (le tranchant est redressé perpendiculairement à la lame). */
    public static float[] basis(float[] blade, float[] edge) {
        float[] d = norm(blade);
        float[] e = perpTo(d, edge);
        e = norm(e);
        return cols(d, e, cross(d, e));
    }

    /** Angles (x, y, z) tels que Rz · Ry · Rx = m. */
    public static void euler(float[] m, float[] out) {
        float sy = Math.max(-1.0F, Math.min(1.0F, -m[6]));
        out[1] = (float) Math.asin(sy);
        if (Math.abs(sy) < 0.9999F) {
            out[0] = (float) Math.atan2(m[7], m[8]);
            out[2] = (float) Math.atan2(m[3], m[0]);
        } else {
            out[0] = (float) Math.atan2(-m[5], m[4]);
            out[2] = 0.0F;
        }
    }

    static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static float[] add(float[] a, float[] b) {
        return new float[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    static float[] sub(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static float[] scale(float[] a, float k) {
        return new float[] {a[0] * k, a[1] * k, a[2] * k};
    }

    static float len(float[] a) {
        return (float) Math.sqrt(dot(a, a));
    }

    static float[] norm(float[] a) {
        float l = len(a);
        return l < 1.0E-6F ? v(0.0F, 1.0F, 0.0F) : scale(a, 1.0F / l);
    }
}
