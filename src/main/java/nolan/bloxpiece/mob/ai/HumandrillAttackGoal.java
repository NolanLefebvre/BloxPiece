package nolan.bloxpiece.mob.ai;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.mob.Humandrill;

/**
 * Le combat au sabre des Humandrills (côté serveur). Une attaque à la fois, chacune avec préparation (bras armé en arrière,
 * épaules et torse qui se tordent), frappe (dégâts aux ticks {@link #HITS}) et récupération. Les durées ci-dessous sont aussi
 * celles des animations (client : HumandrillAnimator) ; le début et le type de chaque attaque sont synchronisés
 * (Humandrill.startAttack), donc tous les joueurs voient la même animation au moment exact des dégâts.
 * <ul>
 *   <li>{@link #HORIZONTAL} — coup horizontal large (de droite à gauche, ou l'inverse de la main gauche) ;</li>
 *   <li>{@link #VERTICAL} — coup vertical : lame levée derrière la tête, puis abattue devant lui ;</li>
 *   <li>{@link #DIAGONAL} — coup diagonal, de l'épaule vers la hanche opposée ;</li>
 *   <li>{@link #QUICK} — estoc rapide, presque sans préparation ;</li>
 *   <li>{@link #POWER} — coup puissant : longue préparation très marquée (accroupi, torse tordu, lame(s) tremblante(s) au-dessus
 *       de l'épaule), puis une frappe diagonale qui projette ; à deux mains avec une seule épée ;</li>
 *   <li>{@link #COMBO} — 2 épées ou plus : enchaînement de 4 coups en alternant les mains (droite, gauche, diagonale droite,
 *       croix des deux lames) ;</li>
 *   <li>{@link #TRIPLE} — 3 épées : bras croisés devant lui, puis bond en avant et les deux lames s'ouvrent pendant que la tête
 *       balaie avec l'épée de la bouche.</li>
 * </ul>
 * Avec deux épées, les coups simples (horizontal, vertical, diagonal, rapide) alternent la main droite et la main gauche
 * (drapeau {@link #LEFT} dans le type synchronisé). Choix : tirage pondéré selon la distance, la configuration et la dernière
 * attaque (une répétition est moins probable) ; les attaques fortes ont une recharge.
 */
public class HumandrillAttackGoal extends Goal {

    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;
    public static final int DIAGONAL = 2;
    public static final int QUICK = 3;
    public static final int POWER = 4;
    public static final int COMBO = 5;
    public static final int TRIPLE = 6;
    public static final int KINDS = 7;
    /** Drapeau ajouté au type synchronisé : coup porté de la main gauche. */
    public static final int LEFT = 16;

    /** Durée totale de chaque attaque (ticks). */
    public static final int[] DURATION = {20, 22, 20, 11, 34, 38, 26};
    /** Ticks où la lame touche (dégâts). */
    public static final int[][] HITS = {{10}, {12}, {10}, {5}, {22}, {7, 13, 20, 27}, {14}};
    /** Multiplicateur des dégâts de base pour chaque coup. */
    private static final float[][] MULT = {{1.0F}, {1.3F}, {1.1F}, {0.7F}, {1.9F}, {0.55F, 0.55F, 0.65F, 1.0F}, {1.6F}};
    /**
     * Portée bord à bord (blocs) et demi-angle du cône devant lui (degrés) où le coup touche. Portées calées sur le modèle de
     * 4 blocs : distance réelle du bout de la lame au centre du Humandrill au moment de l'impact (mesurée sur les animations :
     * 4,4 à 5 blocs), moins sa demi-largeur (0,875), plus une petite marge.
     */
    private static final double[] REACH = {3.8, 4.0, 3.9, 4.4, 4.1, 3.9, 4.1};
    private static final double[] CONE = {85.0, 40.0, 65.0, 40.0, 75.0, 85.0, 110.0};
    /** Recul donné à la cible. */
    private static final double[] PUSH = {0.35, 0.3, 0.35, 0.12, 0.95, 0.25, 0.6};
    /** Temps mort après chaque attaque. */
    private static final int[] REST = {9, 11, 9, 5, 20, 22, 24};
    /** Poids de base pour le tirage. */
    private static final double[] WEIGHT = {1.0, 0.8, 0.9, 0.8, 0.45, 0.85, 0.75};
    private static final int POWER_COOLDOWN = 90;
    private static final int COMBO_COOLDOWN = 70;
    private static final int TRIPLE_COOLDOWN = 100;
    /** Coup des trois épées : bond en avant entre ces ticks. */
    public static final int TRIPLE_DASH_START = 11;
    public static final int TRIPLE_DASH_END = 14;
    /** Coup puissant : la préparation tremble entre ces ticks. */
    public static final int POWER_HOLD_START = 10;
    public static final int POWER_HOLD_END = 19;

    private static final double START_REACH = 3.2;  // portée bord à bord pour lancer un coup (un peu moins que la lame : la cible peut reculer)
    private static final double APPROACH = 2.4;     // il s'approche jusque-là entre deux coups
    private static final double VERTICAL_REACH = 3.5; // écart de hauteur max (ses lames montent jusqu'à ~4 blocs)
    private static final double RUN_GAP = 5.5;      // plus loin : il court
    private static final int REPATH = 8;
    private static final double REPEAT_FACTOR = 0.35;

    private final Humandrill mob;
    private int kind = -1;       // attaque en cours (sans drapeau ; -1 : aucune)
    private boolean left;        // main gauche
    private int age;
    private int rest;
    private int repath;
    private int lastKind = -1;
    private boolean lastLeft = true;
    private long powerReady, comboReady, tripleReady;

    public HumandrillAttackGoal(Humandrill mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Durée d'une attaque d'après son type synchronisé (drapeau LEFT ignoré). */
    public static int duration(int syncedKind) {
        int k = syncedKind & 15;
        return k >= 0 && k < KINDS ? DURATION[k] : 0;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.kind >= 0 || this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.repath = 0;
        this.mob.setAggressive(true);
    }

    @Override
    public void stop() {
        this.kind = -1;
        this.mob.setAggressive(false);
        this.mob.getNavigation().stop();
    }

    private double gap(LivingEntity other) {
        double dx = other.getX() - this.mob.getX(), dz = other.getZ() - this.mob.getZ();
        return Math.sqrt(dx * dx + dz * dz) - (this.mob.getBbWidth() + other.getBbWidth()) * 0.5;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (this.rest > 0) {
            this.rest--;
        }
        if (this.kind >= 0) {
            this.attackTick(target);
            return;
        }
        if (target == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double gap = this.gap(target);
        boolean canHit = Math.abs(target.getY() - this.mob.getY()) < VERTICAL_REACH && this.mob.getSensing().hasLineOfSight(target);
        if (canHit && this.rest <= 0 && gap <= START_REACH) {
            int k = this.choose(gap);
            if (k >= 0) {
                this.begin(k);
                return;
            }
        }
        if (canHit && gap <= APPROACH) {
            this.mob.getNavigation().stop();
        } else if (--this.repath <= 0) {
            this.repath = REPATH;
            this.mob.getNavigation().moveTo(target, gap > RUN_GAP ? 1.35 : 1.05);
        }
    }

    /** Tirage pondéré de la prochaine attaque (-1 : aucune). */
    private int choose(double gap) {
        long now = this.mob.level().getGameTime();
        int swords = this.mob.swords();
        double[] w = WEIGHT.clone();
        if (gap < 1.6) {
            w[QUICK] *= 1.8;   // collé à lui : l'estoc rapide
            w[VERTICAL] *= 0.7;
        }
        if (now < this.powerReady) {
            w[POWER] = 0.0;
        }
        if (swords < 2 || now < this.comboReady) {
            w[COMBO] = 0.0;
        }
        if (swords < 3 || now < this.tripleReady) {
            w[TRIPLE] = 0.0;
        }
        if (this.lastKind >= 0) {
            w[this.lastKind] *= REPEAT_FACTOR;
        }
        double sum = 0.0;
        for (double v : w) {
            sum += v;
        }
        if (sum <= 0.0) {
            return -1;
        }
        double r = this.mob.getRandom().nextDouble() * sum;
        for (int k = 0; k < KINDS; k++) {
            r -= w[k];
            if (r < 0.0 && w[k] > 0.0) {
                return k;
            }
        }
        return HORIZONTAL;
    }

    private void begin(int k) {
        this.kind = k;
        this.lastKind = k;
        this.age = 0;
        // deux épées : les coups simples alternent les mains
        boolean single = k == HORIZONTAL || k == VERTICAL || k == DIAGONAL || k == QUICK;
        this.left = single && this.mob.swords() >= 2 && !this.lastLeft;
        if (single && this.mob.swords() >= 2) {
            this.lastLeft = this.left;
        }
        this.mob.getNavigation().stop();
        this.mob.yBodyRot = this.mob.getYHeadRot();
        this.mob.setYRot(this.mob.getYHeadRot());
        this.mob.startAttack(k | (this.left ? LEFT : 0));
        if (k == POWER || k == TRIPLE) {
            this.mob.playSound(SoundEvents.PIGLIN_BRUTE_ANGRY, 1.0F, 1.5F); // cri avant le gros coup
        } else {
            this.mob.level().playSound(null, this.mob.getX(), this.mob.getY() + 1.2, this.mob.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, 0.5F, 1.3F + this.mob.getRandom().nextFloat() * 0.2F);
        }
    }

    private void attackTick(LivingEntity target) {
        this.mob.getNavigation().stop();
        this.age++;
        int[] hits = HITS[this.kind];
        int last = hits[hits.length - 1];
        if (target != null && this.age < last) {
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            this.mob.setYRot(this.mob.getYHeadRot()); // face à la cible jusqu'au dernier coup
            this.mob.yBodyRot = this.mob.getYHeadRot();
        }
        if (this.kind == TRIPLE && this.age >= TRIPLE_DASH_START && this.age < TRIPLE_DASH_END && this.mob.onGround()) {
            Vec3 look = Vec3.directionFromRotation(0.0F, this.mob.getYRot());
            double speed = target != null && this.gap(target) < 0.6 ? 0.15 : 0.45;
            this.mob.setDeltaMovement(look.x * speed, this.mob.getDeltaMovement().y, look.z * speed); // bond en avant
        }
        for (int i = 0; i < hits.length; i++) {
            if (this.age == hits[i]) {
                this.impact(target, MULT[this.kind][i]);
            }
        }
        if (this.age >= DURATION[this.kind]) {
            long now = this.mob.level().getGameTime();
            if (this.kind == POWER) {
                this.powerReady = now + POWER_COOLDOWN;
            } else if (this.kind == COMBO) {
                this.comboReady = now + COMBO_COOLDOWN;
            } else if (this.kind == TRIPLE) {
                this.tripleReady = now + TRIPLE_COOLDOWN;
            }
            this.rest = REST[this.kind];
            this.kind = -1;
        }
    }

    /** Un ennemi que la lame peut toucher : la cible, les joueurs (pas en créatif ni spectateur) ; jamais un autre Humandrill. */
    private boolean isFoe(LivingEntity e) {
        if (e == this.mob || !e.isAlive() || e instanceof Humandrill) {
            return false;
        }
        if (e instanceof Player p) {
            return !p.isCreative() && !p.isSpectator();
        }
        return e == this.mob.getTarget();
    }

    private void impact(LivingEntity target, float mult) {
        if (!(this.mob.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 look = Vec3.directionFromRotation(0.0F, this.mob.getYRot());
        // effet visuel à la hauteur où passent les lames (proportionnel à la taille du mob)
        Vec3 front = this.mob.position().add(look.scale(this.mob.getBbWidth() * 0.5 + 1.6));
        double bladeY = this.mob.getY() + this.mob.getBbHeight() * 0.4;
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, bladeY, front.z, 1, 0.0, 0.0, 0.0, 0.0);
        boolean heavy = this.kind == POWER || this.kind == TRIPLE || mult >= 1.0F && this.kind == COMBO;
        level.playSound(null, front.x, bladeY, front.z,
                heavy ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE,
                heavy ? 1.1F : 0.8F, heavy ? 0.75F : 1.0F + this.mob.getRandom().nextFloat() * 0.25F);
        double reach = REACH[this.kind];
        double cone = Math.cos(Math.toRadians(CONE[this.kind]));
        float damage = (float) (this.mob.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) * mult);
        List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, this.mob.getBoundingBox().inflate(reach + 1.0, 1.5, reach + 1.0),
                this::isFoe);
        for (LivingEntity foe : foes) {
            if (this.gap(foe) > reach || Math.abs(foe.getY() - this.mob.getY()) > VERTICAL_REACH) {
                continue;
            }
            Vec3 to = new Vec3(foe.getX() - this.mob.getX(), 0.0, foe.getZ() - this.mob.getZ());
            if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(look) < cone) {
                continue; // sur le côté ou derrière lui : esquivé
            }
            if (foe.hurtServer(level, this.mob.damageSources().mobAttack(this.mob), damage)) {
                push(foe, to.x, to.z, PUSH[this.kind], this.kind == POWER ? 0.3 : 0.1);
            }
        }
    }

    /** Recul : poussée horizontale dans la direction (dx, dz), plus un peu de hauteur (résistance au recul prise en compte). */
    private static void push(LivingEntity foe, double dx, double dz, double strength, double up) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) {
            return;
        }
        double resist = 1.0 - foe.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0.0) {
            return;
        }
        foe.push(dx / len * strength * resist, up * resist, dz / len * strength * resist);
    }

    /** Pour le rendu : 0..1 de la phase de préparation tremblante du coup puissant (sinon 0). */
    public static float powerTremble(float t) {
        return t >= POWER_HOLD_START && t <= POWER_HOLD_END ? Mth.sin((t - POWER_HOLD_START) / (POWER_HOLD_END - POWER_HOLD_START) * Mth.PI) : 0.0F;
    }
}
