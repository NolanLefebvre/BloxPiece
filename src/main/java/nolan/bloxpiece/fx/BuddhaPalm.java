package nolan.bloxpiece.fx;

import java.util.List;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import nolan.bloxpiece.registry.ModEntities;

/**
 * Le projectile de la Paume du Bouddha (deuxième attaque du fruit du Bouddha) : la gigantesque main ouverte dorée de Sengoku
 * (modèle 3D BuddhaPalmModel côté client : paume + doigts + manche, auréole qui tourne, traîne d'énergie).
 *
 * Construit comme HikenFist (même architecture, mêmes garanties), mais sans feu : c'est une onde de choc d'énergie divine.
 *
 * <h3>Hitbox = main visible</h3>
 * Une capsule orientée (elle suit la direction du vol, y compris vers le haut ou le bas) qui va du poignet au bout des doigts,
 * d'un rayon égal à la demi-largeur de la paume. Une entité est touchée dès que sa boîte entre dans la capsule (vérifié tous les
 * 0,6 bloc de trajet : aucune cible n'est "sautée"), sauf si elle est cachée derrière un mur. Les blocs sont détectés par une sonde
 * 1,2 x 1,2 au centre de la paume (le point de contact), pour que la main ne s'écrase pas sur le sol dès le lancer.
 *
 * <h3>Impact</h3>
 * Un seul rayon, {@link #BLAST_RADIUS}, règle les blocs détruits (puissance {@link #BLAST_POWER}), les dégâts de zone, le recul
 * et l'étendue de l'onde dorée (BuddhaEffects). Les dégâts sont appliqués UNE fois par entité ({@link #blastEntities}) : l'explosion
 * vanilla ne blesse personne. Un drapeau {@code impacted} garantit une seule explosion par paume. Le lanceur n'est jamais touché ni
 * repoussé par sa propre attaque.
 *
 * <h3>Chute</h3>
 * La main apparaît haut dans le ciel au-dessus de la cible (voir BuddhaPalmAbility), puis s'abat sur elle en accélérant
 * ({@link #START_SPEED} -> {@link #MAX_SPEED}) : sensation de poids. Elle file vers son point d'atterrissage, que BuddhaEffects
 * marque au sol d'un sceau doré pendant la chute. Après l'impact, elle reste plaquée au sol {@link #LINGER_TICKS} ticks (sans
 * collision ni dégâts) pendant que le client la dissout dans la lumière.
 *
 * <h3>Synchronisation</h3>
 * Position, orientation et vitesse sont envoyées par Minecraft à chaque tick (updateInterval 1 dans ModEntities) ; l'instant de
 * l'impact est une donnée synchronisée ({@link #impactAge()}), pour que l'animation de fin soit la même chez tous les joueurs.
 * Les particules sont envoyées par le serveur à tous les joueurs proches. Jamais sauvegardée.
 *
 * Repère : la position de l'entité = le CENTRE de la paume (pivot du modèle).
 */
public class BuddhaPalm extends Projectile {

    /** Échelle du modèle : 1 pixel de modèle = MODEL_SCALE / 16 bloc (la paume fait ~3,5 blocs de large, la main ~10 de haut). Lue par le renderer. */
    public static final double MODEL_SCALE = 3.5;

    private static final double PX = MODEL_SCALE / 16.0;

    // ------------------------------------------------------------------ hitbox (mesurée sur les cubes du modèle, en px)

    /** Bout des doigts (majeur) : 26 px au-dessus du centre de la paume. */
    private static final double UP_REACH = 26.0 * PX;
    /** Poignet : 12 px sous le centre de la paume (la manche, plus large, est exclue). */
    private static final double DOWN_REACH = 12.0 * PX;
    /** Rayon de la capsule : demi-largeur de la paume (8 px) + son biseau. */
    private static final double RADIUS = 9.0 * PX;
    /** Sonde de blocs : demi-côté, et distance devant le centre de la paume (face avant de la paume). */
    private static final double PROBE_HALF_SIZE = 0.6;
    private static final double PROBE_FORWARD = 2.5 * PX;
    /** Pas de balayage (blocs). */
    private static final double STEP = 0.6;

    // ------------------------------------------------------------------ explosion

    /** Puissance de l'explosion de blocs (4 = TNT, Hiken = 5) : la plus puissante des deuxièmes attaques. */
    private static final float BLAST_POWER = 7.0F;

    /** Rayon de l'explosion (blocs) : portée moyenne en espace libre d'une explosion vanilla de cette puissance (~9,3 blocs). */
    public static final double BLAST_RADIUS = BLAST_POWER * (0.3 / 0.225);

    /** Dégâts au centre de l'impact ; décroissance linéaire jusqu'à 0 au bord du rayon. */
    private static final float BLAST_MAX_DAMAGE = 38.0F;

    /** Sécurité : au bout de 3 s de vol sans rien toucher, la paume explose là où elle est. */
    private static final int MAX_AGE = 60;

    // ------------------------------------------------------------------ chute

    /** Vitesse au départ (blocs/tick) : la main se détache lentement du sceau... */
    public static final double START_SPEED = 0.5;
    /** ...puis accélère à chaque tick... */
    private static final double ACCELERATION = 0.16;
    /** ...jusqu'à cette vitesse (blocs/tick, balayée par pas de 0,6 bloc : aucune cible n'est sautée). */
    private static final double MAX_SPEED = 3.6;

    /** Durée (ticks) pendant laquelle la main reste au sol après l'impact (animation de dissolution côté client). Lue par le renderer. */
    public static final int LINGER_TICKS = 10;

    /** Âge de l'entité (tickCount) au moment de l'impact, -1 tant qu'elle vole. Synchronisé : le renderer anime la fin à partir de là. */
    private static final EntityDataAccessor<Integer> IMPACT_AGE =
            SynchedEntityData.defineId(BuddhaPalm.class, EntityDataSerializers.INT);

    private boolean managed;
    private boolean impacted;
    private int age;
    private int linger;
    private Vec3 landing = Vec3.ZERO;
    /** Le sceau doré au sol (peut être null) : il reçoit l'impact pour lancer son onde de choc. */
    private BuddhaSeal seal;
    private double speed;
    private Vec3 heading = new Vec3(0.0, 0.0, 1.0);
    private Vec3 velocity = Vec3.ZERO;

    public BuddhaPalm(EntityType<? extends BuddhaPalm> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * Fait apparaître la main : son centre part de {@code center} (dans le ciel) et elle s'abat vers {@code landing} en accélérant.
     */
    public static BuddhaPalm spawn(ServerLevel level, ServerPlayer owner, Vec3 center, Vec3 landing, BuddhaSeal seal) {
        Vec3 direction = landing.subtract(center);
        BuddhaPalm palm = new BuddhaPalm(ModEntities.BUDDHA_PALM, level);
        palm.managed = true;
        palm.setOwner(owner);
        Vec3 dir = direction.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : direction.normalize();
        palm.heading = dir;
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1.0, 1.0)));
        palm.setYRot(yaw);
        palm.setXRot(pitch);
        palm.yRotO = yaw;
        palm.xRotO = pitch;
        palm.setPos(center.x, center.y, center.z);
        palm.landing = landing;
        palm.seal = seal;
        palm.speed = START_SPEED;
        palm.velocity = dir.scale(START_SPEED);
        palm.setDeltaMovement(palm.velocity);
        level.addFreshEntity(palm);
        return palm;
    }

    // ================================================================== géométrie

    /** Axe "haut" de la main (vers le bout des doigts), déduit de l'orientation : même formule que le repère de HikenFist. */
    private static Vec3 upAxis(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        return new Vec3(-Math.sin(yaw) * Math.sin(pitch), Math.cos(pitch), Math.cos(yaw) * Math.sin(pitch));
    }

    private static Vec3 forwardAxis(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    private static Vec3 closestPoint(AABB box, Vec3 p) {
        return new Vec3(Mth.clamp(p.x, box.minX, box.maxX), Mth.clamp(p.y, box.minY, box.maxY), Mth.clamp(p.z, box.minZ, box.maxZ));
    }

    /** La boîte de l'entité touche-t-elle la capsule (poignet -> bout des doigts) ? Échantillonnée tous les ~0,5 bloc le long de l'axe. */
    private static boolean capsuleHits(Vec3 center, Vec3 up, AABB box) {
        Vec3 bottom = center.subtract(up.scale(DOWN_REACH));
        double length = UP_REACH + DOWN_REACH;
        int samples = (int) Math.ceil(length / 0.5);
        for (int i = 0; i <= samples; i++) {
            Vec3 p = bottom.add(up.scale(length * i / samples));
            if (closestPoint(box, p).distanceToSqr(p) <= RADIUS * RADIUS) {
                return true;
            }
        }
        return false;
    }

    /** Pour Minecraft (F3+B, suivi des entités) : la boîte alignée qui englobe la capsule. Ne dépend que de la rotation et de constantes. */
    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        Vec3 up = upAxis(this.getYRot(), this.getXRot());
        Vec3 top = pos.add(up.scale(UP_REACH));
        Vec3 bottom = pos.subtract(up.scale(DOWN_REACH));
        return new AABB(top, bottom).inflate(RADIUS);
    }

    // ================================================================== vol et collisions (serveur)

    @Override
    public void tick() {
        super.tick();
        this.setBoundingBox(this.makeBoundingBox());
        if (!(this.level() instanceof ServerLevel level)) {
            return; // client : position et orientation arrivent du serveur
        }
        if (!this.managed) {
            this.discard(); // paume orpheline
            return;
        }
        if (this.impacted) {
            // Au sol : la main reste un instant (le client la dissout), puis disparaît
            this.setDeltaMovement(Vec3.ZERO);
            if (++this.linger >= LINGER_TICKS) {
                this.discard();
            }
            return;
        }

        // Chute de plus en plus rapide : sensation de poids
        this.speed = Math.min(MAX_SPEED, this.speed + ACCELERATION);
        this.velocity = this.heading.scale(this.speed);
        this.setDeltaMovement(this.velocity);
        Vec3 up = upAxis(this.getYRot(), this.getXRot());
        Vec3 forward = forwardAxis(this.getYRot(), this.getXRot());
        Vec3 start = this.position();
        Vec3 motion = this.velocity;

        AABB sweep = this.makeBoundingBox(start).minmax(this.makeBoundingBox(start.add(motion)));
        List<Entity> candidates = level.getEntities(this, sweep, this::canHitEntity);

        int steps = Math.max(1, (int) Math.ceil(motion.length() / STEP));
        Vec3 delta = motion.scale(1.0 / steps);
        for (int k = 1; k <= steps; k++) {
            Vec3 center = start.add(delta.scale(k));
            Vec3 front = center.add(forward.scale(PROBE_FORWARD));

            // 1) entité touchée par la main
            for (Entity entity : candidates) {
                AABB box = entity.getBoundingBox();
                if (capsuleHits(center, up, box) && ServerExplosion.getSeenPercent(center, entity) > 0.0F) {
                    this.detonate(level, center, front);
                    return;
                }
            }

            // 2) bloc touché par le centre de la paume
            if (this.probeHitsBlock(level, front)) {
                Vec3 previousFront = start.add(delta.scale(k - 1)).add(forward.scale(PROBE_FORWARD));
                BlockHitResult clip = level.clip(new ClipContext(previousFront, front.add(forward.scale(PROBE_HALF_SIZE)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                Vec3 point = clip.getType() == HitResult.Type.MISS ? front : clip.getLocation();
                // Léger recul : le centre de l'explosion ne doit pas être enfoui dans le bloc touché
                this.detonate(level, center, point.subtract(forward.scale(0.3)));
                return;
            }
        }

        this.age++;
        if (this.age >= MAX_AGE) {
            this.detonate(level, start, start.add(forward.scale(PROBE_FORWARD)));
            return;
        }
        this.setPos(start.add(motion));
        BuddhaEffects.palmTrail(level, start, this.position(), forward, this.age);
        BuddhaEffects.descent(level, this.position(), this.landing, this.age);
    }

    private boolean probeHitsBlock(ServerLevel level, Vec3 front) {
        AABB probe = new AABB(front, front).inflate(PROBE_HALF_SIZE);
        for (VoxelShape shape : level.getBlockCollisions(this, probe)) {
            if (!shape.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== impact

    /** Impact : dégâts (une fois par entité), effets dorés, explosion (blocs + recul) ; la main reste ensuite un instant au sol. Jamais deux fois. */
    private void detonate(ServerLevel level, Vec3 palmPosition, Vec3 point) {
        if (this.impacted) {
            return;
        }
        this.impacted = true;
        this.setPos(palmPosition);
        this.setDeltaMovement(Vec3.ZERO);
        this.entityData.set(IMPACT_AGE, this.tickCount);

        // Les dégâts d'abord : les blocs que l'explosion va détruire protègent encore les entités cachées derrière
        this.blastEntities(level, point, palmPosition, upAxis(this.getYRot(), this.getXRot()));

        // Effets d'impact AVANT l'explosion : les débris prennent la couleur du sol tant qu'il est encore là
        BuddhaEffects.palmImpact(level, point, this.heading, BLAST_RADIUS);
        if (this.seal != null) {
            this.seal.impact(); // le sceau au sol devient l'onde de choc
        }

        // Pas de feu (énergie divine, pas des flammes) ; ExplosionInteraction.MOB : les blocs ne sont détruits que si la règle
        // mobGriefing est active, comme pour Hiken
        level.explode(this, Explosion.getDefaultDamageSource(level, this), new BlastCalculator(point, this.getOwner()),
                point.x, point.y, point.z, BLAST_POWER, false, Level.ExplosionInteraction.MOB);
    }

    /**
     * Dégâts de zone : toute entité vivante à moins de {@link #BLAST_RADIUS} du point d'impact, décroissance linéaire, réduite par les
     * blocs qui la protègent (sauf si la main la recouvre). Une seule fois par entité ; jamais le lanceur.
     */
    private void blastEntities(ServerLevel level, Vec3 point, Vec3 palmCenter, Vec3 up) {
        Entity owner = this.getOwner();
        DamageSource source = level.damageSources().source(
                owner == null ? DamageTypes.EXPLOSION : DamageTypes.PLAYER_EXPLOSION, this, owner);
        AABB zone = new AABB(point, point).inflate(BLAST_RADIUS);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, zone,
                e -> e != owner && e.isAlive() && !e.isSpectator() && !(e instanceof EnteiSun)
                        && !nolan.bloxpiece.party.Parties.allies(e, owner)); // membres de la Party du lanceur épargnés
        for (LivingEntity target : targets) {
            AABB box = target.getBoundingBox();
            double distance = closestPoint(box, point).distanceTo(point);
            if (distance > BLAST_RADIUS) {
                continue;
            }
            float exposure = capsuleHits(palmCenter, up, box) ? 1.0F : ServerExplosion.getSeenPercent(point, target);
            float damage = (float) (BLAST_MAX_DAMAGE * (1.0 - distance / BLAST_RADIUS)) * exposure;
            if (damage < 0.5F) {
                continue;
            }
            if (owner instanceof LivingEntity ownerLiving) {
                target.setLastHurtByMob(ownerLiving); // XP et riposte des mobs attribuées au lanceur
            }
            if (target.hurtServer(level, source, damage)) {
                EnchantmentHelper.doPostAttackEffects(level, target, source);
            }
        }
    }

    /**
     * Explosion vanilla (blocs détruits, son, recul synchronisé avec les clients) SANS dégâts aux entités (déjà appliqués, jamais en
     * double), avec un recul limité au même rayon et jamais appliqué au lanceur.
     */
    private static final class BlastCalculator extends ExplosionDamageCalculator {
        private final Vec3 center;
        private final Entity owner;

        BlastCalculator(Vec3 center, Entity owner) {
            this.center = center;
            this.owner = owner;
        }

        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            if (entity == this.owner
                    || closestPoint(entity.getBoundingBox(), this.center).distanceTo(this.center) > BLAST_RADIUS) {
                return 0.0F;
            }
            return super.getKnockbackMultiplier(entity);
        }
    }

    // ================================================================== entité

    @Override
    protected boolean canHitEntity(Entity entity) {
        // Ni le lanceur, ni les autres projectiles, ni le soleil visuel d'Entei
        return entity != this.getOwner() && !(entity instanceof Projectile) && !(entity instanceof EnteiSun)
                && entity.canBeHitByProjectile();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // position, rotation et vitesse sont envoyées par Minecraft ; en plus : l'instant de l'impact (animation de fin)
        builder.define(IMPACT_AGE, -1);
    }

    /** Âge (tickCount) au moment de l'impact, ou -1 si la main vole encore. Lu par le renderer, des deux côtés. */
    public int impactAge() {
        return this.entityData.get(IMPACT_AGE);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0 * 256.0;
    }
}
