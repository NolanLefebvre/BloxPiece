package nolan.bloxpiece.mob.ai;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.mob.ArmedMarine;
import nolan.bloxpiece.mob.ArmedPirate;
import nolan.bloxpiece.mob.Morgan;

/**
 * Le combat de Morgan : trois attaques à la hache, une seule à la fois, choisies selon la situation. Côté serveur ; les durées
 * ci-dessous sont aussi celles des animations (MorganModel), et le début et le type de chaque attaque sont synchronisés
 * (Morgan.startAxeSwing).
 * <ul>
 *   <li>{@link #CLASSIC} — coup classique, cible proche : préparation (hache levée derrière lui), frappe devant lui ; dégâts à
 *       l'impact ({@link #HIT_TICK}) si la cible est encore à portée et devant lui ; récupération.</li>
 *   <li>{@link #SPIN} — rotation, quand plusieurs ennemis l'entourent : il tend la hache sur le côté, tourne 4 fois sur
 *       lui-même (accélère, puis ralentit), la hache balaie tout autour de lui. Une cible est touchée quand la lame passe sur elle
 *       (angle de la lame calculé comme l'animation, {@link #spinDegrees}) ; au plus {@link #SPIN_MAX_HITS} fois par rotation,
 *       à {@link #SPIN_HIT_GAP} ticks d'écart au moins.</li>
 *   <li>{@link #SLAM} — coup vertical au sol, frappe très puissante devant lui : hache levée au-dessus de la tête, posture de
 *       puissance, il l'abat dans le sol ; au moment où le fer touche le sol ({@link #SLAM_HIT}), une onde de choc part devant
 *       lui en éventail jusqu'à {@link #SLAM_RANGE} blocs : tous les ennemis de cette zone (celle que dessine l'onde) sont
 *       touchés et projetés. Effet propre à Morgan (MorganQuake : cratère, crevasses qui se propagent, front de poussière,
 *       blocs de roche projetés), poussière et débris, secousse ; aucun bloc détruit, pas d'explosion.</li>
 * </ul>
 * <b>Choix de l'attaque</b> ({@link #choose}) : à chaque occasion (temps mort fini), chaque attaque disponible (à portée, recharge
 * finie) reçoit un poids selon la situation (distance, nombre d'ennemis autour de lui), réduit si c'est la même que la
 * précédente ; une attaque est tirée au sort selon ces poids. Les trois sont donc réellement utilisées et varient au cours d'un
 * combat, sans être purement aléatoires : de près surtout le coup classique (mais aussi la rotation et le coup au sol), entouré
 * surtout la rotation, de loin seulement le coup au sol (sinon il se rapproche). Une attaque en recharge laisse la place aux
 * autres ; une attaque va toujours jusqu'au bout avant la suivante ; jamais deux à la fois.
 * <p>
 * Anciennes causes corrigées : la rotation exigeait 2 ennemis (jamais contre un joueur seul) et une cible à 1,8 bloc alors qu'il
 * s'arrêtait à 2,2 ; le coup au sol n'avait qu'une chance sur 4 par tick, et le coup classique partait dans le même tick sinon.
 */
public class MorganAxeGoal extends Goal {

    public static final int CLASSIC = 0;
    public static final int SPIN = 1;
    public static final int SLAM = 2;

    // coup classique
    public static final int PREP_TICKS = 12;
    public static final int HIT_TICK = 16;
    public static final int END_TICK = 28;
    // rotation : tendue sur le côté (préparation), 4 tours (1440°) entre SPIN_START et SPIN_STOP, retour sur l'épaule
    public static final int SPIN_PREP = 10;
    public static final int SPIN_START = 10;
    public static final int SPIN_STOP = 44;
    public static final int SPIN_END = 54;
    public static final float SPIN_TURNS = 4.0F;
    // coup au sol : levée, temps d'arrêt, chute (impact au sol), récupération
    public static final int SLAM_RAISE = 14;
    public static final int SLAM_HOLD = 22;
    public static final int SLAM_HIT = 26;
    public static final int SLAM_END = 44;

    private static final int REPATH = 10;
    private static final double SPEED = 1.0;
    // coup classique : portée bord à bord pour lancer le coup / pour qu'il touche ; cône devant lui (70°)
    private static final double START_REACH = 2.2;   // portée bord à bord pour lancer le coup classique ou la rotation
    private static final double APPROACH = 1.6;      // il s'approche jusque-là entre deux attaques
    private static final double HIT_REACH = 2.9;
    private static final double HIT_CONE = Math.cos(Math.toRadians(70.0));
    private static final int CLASSIC_REST = 14;
    // rotation : portée de la hache tendue (centre à centre, blocs), dégâts, recul, limites par cible, recharges
    private static final double SPIN_REACH = 3.3;    // centre à centre : la hache tendue (fer à ~2,6 blocs) + la cible
    private static final float SPIN_DAMAGE = 10.0F;
    private static final double SPIN_PUSH = 0.7;
    private static final int SPIN_MAX_HITS = 2;
    private static final int SPIN_HIT_GAP = 10;
    private static final int SPIN_COOLDOWN = 120;
    // coup au sol : onde de choc en éventail devant lui, de SLAM_NEAR à SLAM_RANGE blocs (centre de Morgan), demi-largeur
    // SLAM_HALF_WIDTH + SLAM_SPREAD x distance ; dégâts, recul, recharge
    public static final double SLAM_RANGE = 8.0;
    private static final double SLAM_NEAR = 0.3;
    public static final double SLAM_HALF_WIDTH = 1.2;
    public static final double SLAM_SPREAD = 0.45;
    private static final double SLAM_START = 6.5;     // portée bord à bord pour le lancer
    private static final float SLAM_DAMAGE = 20.0F;
    private static final int SLAM_COOLDOWN = 140;
    private static final int SLAM_WAVE_TICKS = 8;     // l'onde de poussière avance pendant 8 ticks après l'impact
    private static final int BIG_REST = 24;           // temps mort après une rotation ou un coup au sol
    // choix de l'attaque : poids de base, réduction pour la même attaque que la précédente, chance de lancer le coup au sol de loin
    private static final double REPEAT_FACTOR = 0.3;
    private static final float FAR_SLAM_CHANCE = 0.35F;

    private final Morgan morgan;
    private int kind = -1;      // attaque en cours (-1 : aucune)
    private int age;            // ticks depuis le début de l'attaque en cours
    private float startYaw;     // rotation : orientation du corps au départ (verrouillée pendant l'attaque)
    private int rest;           // temps mort commun
    private long spinReady;
    private long slamReady;
    private int repath;
    private int lastKind = -1;  // dernière attaque lancée (pour éviter les répétitions)
    private int streak;         // nombre de fois de suite
    private int decide;         // de loin : prochaine décision de coup au sol dans N ticks
    private Vec3 slamOrigin;    // coup au sol : point de départ et direction de l'onde (pour les effets après l'impact)
    private Vec3 slamDir;
    private int slamWave = -1;
    private final Map<UUID, int[]> spinHits = new HashMap<>(); // cible -> {nombre de coups, tick du dernier}

    public MorganAxeGoal(Morgan morgan) {
        this.morgan = morgan;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Angle parcouru par la rotation (degrés) au tick t : départ lent, très rapide au milieu, arrêt en douceur. */
    public static float spinDegrees(float t) {
        float u = Mth.clamp((t - SPIN_START) / (float) (SPIN_STOP - SPIN_START), 0.0F, 1.0F);
        return 360.0F * SPIN_TURNS * u * u * (3.0F - 2.0F * u);
    }

    /** Durée totale d'une attaque. */
    public static int duration(int kind) {
        return kind == SPIN ? SPIN_END : kind == SLAM ? SLAM_END : END_TICK;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.morgan.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.kind >= 0 || this.slamWave >= 0 || this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.repath = 0;
        this.morgan.setAggressive(true);
    }

    @Override
    public void stop() {
        this.kind = -1;
        this.morgan.setAggressive(false);
        this.morgan.getNavigation().stop();
    }

    /** Distance horizontale bord à bord entre Morgan et une entité (blocs). */
    private double gap(LivingEntity other) {
        double dx = other.getX() - this.morgan.getX(), dz = other.getZ() - this.morgan.getZ();
        return Math.sqrt(dx * dx + dz * dz) - (this.morgan.getBbWidth() + other.getBbWidth()) * 0.5;
    }

    /** Un ennemi que ses attaques de zone peuvent toucher : joueurs (pas en créatif ni spectateur) et pirates, pas les Marines. */
    private boolean isFoe(LivingEntity e) {
        if (e == this.morgan || !e.isAlive() || e instanceof ArmedMarine) {
            return false;
        }
        if (e instanceof Player p) {
            return !p.isCreative() && !p.isSpectator();
        }
        return e instanceof ArmedPirate || e == this.morgan.getTarget();
    }

    private List<LivingEntity> foesAround(double radius) {
        return this.morgan.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(this.morgan.blockPosition()).inflate(radius + 1.0, 3.0, radius + 1.0),
                e -> this.isFoe(e) && Math.abs(e.getY() - this.morgan.getY()) < 3.0
                        && e.distanceToSqr(this.morgan.getX(), e.getY(), this.morgan.getZ()) <= radius * radius);
    }

    @Override
    public void tick() {
        LivingEntity target = this.morgan.getTarget();
        if (this.rest > 0) {
            this.rest--;
        }
        if (this.slamWave >= 0) {
            this.slamWaveTick();
        }
        if (this.kind >= 0) {
            this.attackTick(target);
            return;
        }
        if (target == null) {
            return;
        }
        this.morgan.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double gap = this.gap(target);
        boolean canHit = Math.abs(target.getY() - this.morgan.getY()) < 3.0 && this.morgan.getSensing().hasLineOfSight(target);
        if (canHit && this.rest <= 0) {
            int kind = this.choose(gap);
            if (kind >= 0) {
                this.begin(kind);
                return;
            }
        }
        if (canHit && gap <= APPROACH) {
            this.morgan.getNavigation().stop(); // assez près : il attend la fin de son temps mort
        } else if (--this.repath <= 0) {
            this.repath = REPATH;
            this.morgan.getNavigation().moveTo(target, SPEED);
        }
    }

    /**
     * Choisit l'attaque à lancer maintenant (-1 : aucune, il continue d'approcher). Poids selon la situation, réduits pour la
     * même attaque que la précédente (et encore plus au-delà de 2 fois de suite), puis tirage selon les poids.
     */
    private int choose(double gap) {
        long now = this.morgan.level().getGameTime();
        double close = gap <= START_REACH ? 1.0 : 0.0;
        double wClassic = close * 1.0;
        double wSpin = 0.0, wSlam = 0.0;
        if (now >= this.spinReady && gap <= START_REACH) {
            int foes = this.foesAround(SPIN_REACH + 0.5).size();
            wSpin = 0.55 + 0.9 * Math.max(0, foes - 1);   // seul contre un joueur aussi ; surtout quand il est entouré
            wClassic *= foes >= 2 ? 0.6 : 1.0;
        }
        if (now >= this.slamReady && gap <= SLAM_START) {
            if (gap <= START_REACH) {
                wSlam = 0.6;
            } else if (--this.decide <= 0) {               // de loin : il ne frappe pas toujours dès qu'il le peut
                this.decide = 5;
                wSlam = this.morgan.getRandom().nextFloat() < FAR_SLAM_CHANCE ? 1.0 : 0.0;
            }
            int foesAhead = this.foesInSlamZone(this.morgan.getYHeadRot()).size();
            wSlam *= foesAhead >= 2 ? 1.6 : 1.0;           // plusieurs ennemis devant lui : l'onde de choc est faite pour ça
        }
        double[] w = {wClassic, wSpin, wSlam};
        if (this.lastKind >= 0) {
            w[this.lastKind] *= this.streak >= 2 ? REPEAT_FACTOR * 0.5 : REPEAT_FACTOR;
        }
        double sum = w[0] + w[1] + w[2];
        if (sum <= 0.0) {
            return -1;
        }
        double r = this.morgan.getRandom().nextDouble() * sum;
        for (int k = 0; k < 3; k++) {
            r -= w[k];
            if (r < 0.0 && w[k] > 0.0) {
                return k;
            }
        }
        return w[2] > 0 ? SLAM : w[1] > 0 ? SPIN : CLASSIC;
    }

    private void begin(int kind) {
        this.streak = kind == this.lastKind ? this.streak + 1 : 1;
        this.lastKind = kind;
        this.kind = kind;
        this.age = 0;
        this.startYaw = this.morgan.getYHeadRot();
        this.spinHits.clear();
        this.morgan.getNavigation().stop();
        this.morgan.setYRot(this.startYaw);
        this.morgan.yBodyRot = this.startYaw;
        this.morgan.startAxeSwing(kind);
        float pitch = kind == SLAM ? 0.5F : 0.6F;
        this.morgan.level().playSound(null, this.morgan.getX(), this.morgan.getY() + 2.0, this.morgan.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.8F, pitch);
    }

    private void attackTick(LivingEntity target) {
        this.morgan.getNavigation().stop();
        this.age++;
        if (this.kind == SPIN) {
            // le corps reste orienté comme au départ : c'est le modèle qui tourne (même angle que les dégâts)
            this.morgan.setYRot(this.startYaw);
            this.morgan.yBodyRot = this.startYaw;
            this.spinTick();
        } else {
            int hitTick = this.kind == SLAM ? SLAM_HIT : HIT_TICK;
            if (target != null && this.age < hitTick) {
                this.morgan.getLookControl().setLookAt(target, 30.0F, 30.0F);
                this.morgan.setYRot(this.morgan.getYHeadRot()); // face à la cible jusqu'à l'impact
                this.morgan.yBodyRot = this.morgan.getYHeadRot();
            }
            if (this.age == hitTick) {
                if (this.kind == SLAM) {
                    this.slamImpact();
                } else {
                    this.classicImpact(target);
                }
            }
        }
        if (this.age >= duration(this.kind)) {
            long now = this.morgan.level().getGameTime();
            if (this.kind == SPIN) {
                this.spinReady = now + SPIN_COOLDOWN;
                this.rest = BIG_REST;
            } else if (this.kind == SLAM) {
                this.slamReady = now + SLAM_COOLDOWN;
                this.rest = BIG_REST;
            } else {
                this.rest = CLASSIC_REST;
            }
            this.kind = -1;
        }
    }

    // ------------------------------------------------------------------ coup classique

    private void classicImpact(LivingEntity target) {
        if (!(this.morgan.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 look = Vec3.directionFromRotation(0.0F, this.morgan.getYRot());
        Vec3 front = this.morgan.position().add(look.scale(2.0));
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, this.morgan.getY() + 1.2, front.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, front.x, this.morgan.getY() + 1.0, front.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE,
                1.2F, 0.6F);
        if (target == null || !target.isAlive() || this.gap(target) > HIT_REACH
                || Math.abs(target.getY() - this.morgan.getY()) > 3.0) {
            return;
        }
        Vec3 to = new Vec3(target.getX() - this.morgan.getX(), 0.0, target.getZ() - this.morgan.getZ());
        if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(look) < HIT_CONE) {
            return; // passé sur le côté ou derrière lui : esquivé
        }
        this.morgan.doHurtTarget(level, target);
    }

    // ------------------------------------------------------------------ rotation

    /**
     * Direction de la lame (orientation façon « yaw », degrés) : au départ, la hache est tendue du côté droit ; le modèle tourne
     * de -spinDegrees (le fil de la lame mène), donc la lame passe par startYaw - spinDegrees.
     */
    private float bladeYaw(int t) {
        return this.startYaw - spinDegrees(t);
    }

    private void spinTick() {
        if (!(this.morgan.level() instanceof ServerLevel level)) {
            return;
        }
        int t = this.age;
        this.spinEffects(level, t);
        if (t <= SPIN_START + 1 || t > SPIN_STOP - 1) {
            return; // la hache ne frappe que quand elle tourne vraiment
        }
        float from = this.bladeYaw(t - 1), to = this.bladeYaw(t);
        float sweep = from - to;                               // degrés parcourus ce tick (> 0)
        for (LivingEntity foe : this.foesAround(SPIN_REACH)) {
            double dx = foe.getX() - this.morgan.getX(), dz = foe.getZ() - this.morgan.getZ();
            double dist = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
            // orientation « yaw » Y du côté droit pointé vers la cible : côté droit(Y) = (-cos Y, -sin Y)
            float bearing = (float) Math.toDegrees(Math.atan2(-dz, -dx));
            float slack = (float) Math.toDegrees(Math.atan2(foe.getBbWidth() * 0.5 + 0.2, dist));
            float behind = Mth.wrapDegrees(from - bearing);       // de combien la lame doit encore tourner pour l'atteindre
            if (behind < 0.0F) {
                behind += 360.0F;
            }
            if (behind > sweep + slack && behind < 360.0F - slack) {
                continue; // la lame n'est pas passée sur elle ce tick
            }
            int[] hits = this.spinHits.computeIfAbsent(foe.getUUID(), k -> new int[] {0, -100});
            if (hits[0] >= SPIN_MAX_HITS || t - hits[1] < SPIN_HIT_GAP) {
                continue;
            }
            hits[0]++;
            hits[1] = t;
            if (foe.hurtServer(level, this.morgan.damageSources().mobAttack(this.morgan), SPIN_DAMAGE)) {
                push(foe, dx, dz, SPIN_PUSH, 0.15); // repoussée vers l'extérieur
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, foe.getX(), foe.getY() + foe.getBbHeight() * 0.6, foe.getZ(), 1,
                    0.0, 0.0, 0.0, 0.0);
            level.playSound(null, foe.getX(), foe.getY() + 1.0, foe.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE,
                    1.0F, 0.7F);
        }
    }

    /** Recul : poussée horizontale de force {@code strength} dans la direction (dx, dz), plus un peu de hauteur. */
    private static void push(LivingEntity foe, double dx, double dz, double strength, double up) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) {
            return;
        }
        double resist = 1.0 - foe.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0.0) {
            return;
        }
        foe.push(dx / len * strength * resist, up * resist, dz / len * strength * resist);
    }

    /**
     * Effets de la rotation, qui suivent la lame (même angle que l'animation et les dégâts) : sifflement à chaque tour, éclats de
     * balayage au fer, poussière soulevée au sol sous la lame, et un tourbillon d'air autour de lui qui marque la zone dangereuse.
     */
    private void spinEffects(ServerLevel level, int t) {
        if (t < SPIN_START || t > SPIN_STOP) {
            return;
        }
        float speed = spinDegrees(t) - spinDegrees(t - 1);
        if ((int) (spinDegrees(t) / 360.0F) != (int) (spinDegrees(t - 1) / 360.0F)) {
            level.playSound(null, this.morgan.getX(), this.morgan.getY() + 2.0, this.morgan.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, 1.2F, 0.45F); // sifflement : un tour de plus
        }
        if (speed < 12.0F) {
            return;
        }
        double ox = this.morgan.getX(), oy = this.morgan.getY(), oz = this.morgan.getZ();
        float yaw = this.bladeYaw(t) * Mth.DEG_TO_RAD;
        double bx = -Math.cos(yaw), bz = -Math.sin(yaw);                  // direction de la hache tendue
        double tx = -Math.sin(yaw), tz = Math.cos(yaw);                   // sens du mouvement (le fil mène : angle qui décroît)
        // éclat de balayage au fer, poussière soulevée sous la lame (projetée dans le sens du mouvement)
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, ox + bx * 2.4, oy + 2.0, oz + bz * 2.4, 1, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 3; i++) {
            double r = 1.6 + this.morgan.getRandom().nextDouble() * 1.4;
            level.sendParticles(ParticleTypes.CLOUD, ox + bx * r, oy + 0.15, oz + bz * r, 0, tx, 0.08, tz, 0.18);
        }
        // tourbillon d'air autour de lui (zone de danger) : petites bouffées qui tournent
        for (int i = 0; i < 4; i++) {
            double a = this.morgan.getRandom().nextDouble() * Math.PI * 2.0;
            double r = SPIN_REACH - 0.3;
            level.sendParticles(ParticleTypes.POOF, ox + Math.cos(a) * r, oy + 0.1 + this.morgan.getRandom().nextDouble() * 1.6,
                    oz + Math.sin(a) * r, 0, Math.sin(a), 0.02, -Math.cos(a), 0.12);
        }
        BlockState ground = level.getBlockState(this.morgan.blockPosition().below());
        if (!ground.isAir() && t % 2 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), ox + bx * 2.2, oy + 0.1, oz + bz * 2.2, 4,
                    0.3, 0.05, 0.3, 0.1);
        }
    }

    // ------------------------------------------------------------------ coup au sol

    /** Ennemis dans l'éventail de l'onde de choc, pour un coup au sol porté dans l'orientation yaw (degrés). */
    private List<LivingEntity> foesInSlamZone(float yaw) {
        Vec3 dir = Vec3.directionFromRotation(0.0F, yaw);
        Vec3 o = this.morgan.position();
        double far = SLAM_RANGE + SLAM_HALF_WIDTH + SLAM_SPREAD * SLAM_RANGE + 1.0;
        return this.morgan.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(o.x - far, o.y - 1.5, o.z - far, o.x + far, o.y + 3.0, o.z + far),
                e -> {
                    if (!this.isFoe(e) || e.getY() < o.y - 1.5 || e.getY() > o.y + 2.5) {
                        return false;
                    }
                    double dx = e.getX() - o.x, dz = e.getZ() - o.z;
                    double along = dx * dir.x + dz * dir.z;               // distance devant lui
                    double side = Math.abs(-dx * dir.z + dz * dir.x);      // écart sur le côté
                    double reach = e.getBbWidth() * 0.5;
                    return along >= SLAM_NEAR - reach && along <= SLAM_RANGE + reach
                            && side <= SLAM_HALF_WIDTH + SLAM_SPREAD * Math.max(0.0, along) + reach;
                });
    }

    private void slamImpact() {
        if (!(this.morgan.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 dir = Vec3.directionFromRotation(0.0F, this.morgan.getYRot());
        Vec3 o = this.morgan.position();
        Vec3 hit = o.add(dir.scale(nolan.bloxpiece.fx.MorganQuake.IMPACT_AHEAD)); // là où le fer mord le sol
        // impact : effet propre à Morgan (cratère, crevasses, front de poussière, débris : MorganQuake), nuage, bruit, secousse
        nolan.bloxpiece.fx.MorganQuake.spawn(level, new Vec3(hit.x, o.y + 0.02, hit.z), this.morgan.getYRot());
        level.sendParticles(ParticleTypes.EXPLOSION, hit.x, o.y + 0.3, hit.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, hit.x, o.y + 0.2, hit.z, 6, 0.6, 0.1, 0.6, 0.01);
        this.groundBurst(level, hit, 1.2, 50, 0.35);
        level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2F, 0.5F);
        level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 1.0F, 0.5F);
        level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.4F, 0.5F);
        nolan.bloxpiece.ability.BuddhaHits.shake(level, hit, 18.0, 1.0F, 10);
        // dégâts et recul : toute la zone de l'onde, au moment de l'impact ; recul plus fort près de l'impact
        for (LivingEntity foe : this.foesInSlamZone(this.morgan.getYRot())) {
            if (foe.hurtServer(level, this.morgan.damageSources().mobAttack(this.morgan), SLAM_DAMAGE)) {
                double px = foe.getX() - o.x, pz = foe.getZ() - o.z;
                double along = Math.max(0.0, px * dir.x + pz * dir.z);
                double strength = 1.5 - 0.7 * Math.min(1.0, along / SLAM_RANGE);
                push(foe, px + dir.x * 2.0, pz + dir.z * 2.0, strength, 0.5); // projetée vers l'avant de l'onde, et soulevée
            }
        }
        // l'onde de poussière et de débris avance ensuite (slamWaveTick)
        this.slamOrigin = o;
        this.slamDir = dir;
        this.slamWave = 0;
    }

    /** Une bouffée de poussière, d'éclats du sol et de débris projetés, autour de p (rayon r). */
    private void groundBurst(ServerLevel level, Vec3 p, double r, int count, double up) {
        BlockState state = level.getBlockState(BlockPos.containing(p.x, this.morgan.getY() - 0.5, p.z));
        if (!state.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), p.x, this.morgan.getY() + 0.15, p.z, count,
                    r, 0.1, r, up);
        }
        level.sendParticles(ParticleTypes.POOF, p.x, this.morgan.getY() + 0.2, p.z, Math.max(2, count / 8), r, 0.05, r, 0.03);
    }

    /**
     * Onde de choc qui avance devant lui après l'impact (purement visuelle : les dégâts sont déjà faits) : à chaque tick, un arc
     * de poussière et de débris un peu plus loin et un peu plus large, jusqu'à SLAM_RANGE, exactement sur la zone touchée.
     */
    private void slamWaveTick() {
        if (!(this.morgan.level() instanceof ServerLevel level) || this.slamOrigin == null) {
            this.slamWave = -1;
            return;
        }
        int k = ++this.slamWave;
        double along = SLAM_RANGE * k / SLAM_WAVE_TICKS;
        double half = SLAM_HALF_WIDTH + SLAM_SPREAD * along;
        Vec3 side = new Vec3(-this.slamDir.z, 0.0, this.slamDir.x);
        int points = 3 + (int) (half * 1.5);
        for (int i = 0; i <= points; i++) {
            double l = -half + 2.0 * half * i / points;
            Vec3 p = this.slamOrigin.add(this.slamDir.scale(along)).add(side.scale(l));
            this.groundBurst(level, p, 0.25, 6, 0.22);
        }
        if (k % 3 == 0) {
            Vec3 c = this.slamOrigin.add(this.slamDir.scale(along));
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
        if (k >= SLAM_WAVE_TICKS) {
            this.slamWave = -1;
        }
    }
}
