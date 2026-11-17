package nolan.bloxpiece.fx;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import nolan.bloxpiece.registry.ModEntities;

/**
 * Le projectile de Hiken : le poing de feu LUI-MÊME (modèle 3D HikenFistModel côté client : poing + avant-bras + masse de flammes).
 *
 * C'est une vraie entité projectile du mod (elle étend {@link Projectile}, PAS une boule de feu) avec son propre déplacement,
 * ses propres collisions et sa propre explosion : plus aucune LargeFireball n'est créée par Hiken.
 *
 * <h3>Hitbox = poing visible</h3>
 * Le modèle fait 1 px = {@link #MODEL_SCALE}/16 bloc. La hitbox est une boîte ORIENTÉE (elle tourne avec le poing, y compris en
 * visant vers le haut ou le bas) qui épouse la partie principale du poing : dos de la main + phalanges + poignet, soit
 * 18 x 16 x 21 px = ~6,75 x 6,0 x 7,7 blocs. Le pouce qui dépasse sur le côté, l'avant-bras et la masse de flammes (traînée) n'en font PAS
 * partie. {@link #makeBoundingBox(Vec3)} la donne aussi à Minecraft (boîte alignée sur les axes qui l'englobe) : F3+B montre donc
 * une hitbox à la taille du poing, côté serveur comme côté client.
 * <ul>
 *   <li><b>Entités</b> : le poing explose dès que SA boîte orientée touche une entité (test exact boîte orientée / boîte de l'entité,
 *       par axe séparateur), vérifié tous les 0,6 bloc de trajet pour ne jamais "sauter" une cible. Une entité cachée derrière un mur
 *       (aucune ligne de vue depuis l'axe du poing) n'est pas touchée.</li>
 *   <li><b>Blocs</b> : le poing est bien plus haut qu'un joueur (6 blocs pour 1,8) et plus large qu'un couloir : si la boîte entière
 *       comptait pour les blocs, il exploserait sur le sol ou les murs dès le lancer. Les blocs sont donc détectés par une sonde 1 x 1
 *       placée sur les phalanges (le point de contact du coup de poing), balayée sur tout le trajet du tick.</li>
 *   <li><b>Lancer</b> : les entités déjà recouvertes par le poing au départ à côté ou derrière le lanceur (monture, familier, allié) sont
 *       ignorées tant qu'elles restent dans le poing ; une cible devant le joueur est touchée normalement.</li>
 * </ul>
 *
 * <h3>Explosion</h3>
 * Un seul rayon, {@link #BLAST_RADIUS} (~2 demi-largeurs de poing), règle à la fois l'explosion de blocs (puissance
 * {@link #BLAST_POWER}), les dégâts de zone, la mise à feu, le recul et les particules de FireEffects. L'explosion vanilla ne blesse
 * personne (calculateur personnalisé) : tous les dégâts passent par {@link #blastEntities}, appliqués UNE fois par entité, avec une
 * décroissance linéaire jusqu'au bord du rayon. Un drapeau {@code impacted} garantit une seule explosion par poing.
 *
 * <h3>Synchronisation</h3>
 * Position, orientation et vitesse sont envoyées par Minecraft à chaque tick (updateInterval 1 dans ModEntities) ; le client ne
 * simule rien. L'explosion (blocs, recul, particules, son) est envoyée par le serveur aux clients. Jamais sauvegardée (noSave).
 *
 * Repère : la position de l'entité = le CENTRE du poing (pivot du modèle).
 */
public class HikenFist extends Projectile {

    /** Échelle du modèle : 1 pixel de modèle = MODEL_SCALE / 16 bloc (le poing fait ~6,75 blocs de large). Lue aussi par le renderer client. */
    public static final double MODEL_SCALE = 6.0;

    private static final double PX = MODEL_SCALE / 16.0;

    /** Distance (en blocs) entre le centre du poing et le bout du pouce, à l'échelle MODEL_SCALE (13 px de modèle) : repère de départ du poing. */
    public static final double FRONT_OFFSET = 13.0 * PX;

    // ------------------------------------------------------------------ hitbox (mesurée sur les cubes du modèle, en px)

    /** Demi-largeur : le dos de la main fait 18 px de large (le pouce, qui dépasse jusqu'à 12 px, est exclu). */
    private static final double HALF_WIDTH = 9.0 * PX;
    /** Hauteur : de -7,7 px (bout des doigts) à +8,4 px (phalanges). */
    private static final double HALF_UP = (8.4 + 7.7) / 2.0 * PX;
    private static final double CENTER_UP = (8.4 - 7.7) / 2.0 * PX;
    /** Devant du poing = bout des doigts (10,6 px) : c'est le point de contact d'un coup de poing. */
    public static final double CONTACT_OFFSET = 10.6 * PX;
    /** Arrière du poing = poignet (10 px derrière le centre). */
    private static final double REAR_OFFSET = 10.0 * PX;
    private static final double HALF_LENGTH = (CONTACT_OFFSET + REAR_OFFSET) / 2.0;
    private static final double CENTER_FORWARD = (CONTACT_OFFSET - REAR_OFFSET) / 2.0;

    /** Sonde de blocs : demi-côté (1 x 1 bloc, comme une boule de feu) et profondeur (>= au pas de balayage, donc aucun trou). */
    private static final double PROBE_HALF_SIZE = 0.5;
    private static final double PROBE_DEPTH = 0.7;
    /** Pas de balayage (blocs) : la vitesse (1,8 bloc/tick) est découpée en pas de 0,6 bloc max. */
    private static final double STEP = 0.6;

    /** Une entité à moins d'1 bloc devant le lanceur (ou derrière / à côté) quand le poing apparaît est ignorée tant qu'elle reste dedans. */
    private static final double LAUNCH_GRACE_AHEAD = 1.0;

    // ------------------------------------------------------------------ explosion

    /** Puissance de l'explosion de blocs (4 = TNT, 6 = creeper chargé). */
    private static final float BLAST_POWER = 5.0F;

    /**
     * Rayon de l'explosion (blocs) : la portée moyenne en espace libre des rayons d'une explosion vanilla de cette puissance
     * (puissance x 0,3 / 0,225, soit ~6,7 blocs), ~2 demi-largeurs du poing. Utilisé pour les dégâts de zone, la mise à feu, le recul
     * et l'étendue des particules.
     */
    public static final double BLAST_RADIUS = BLAST_POWER * (0.3 / 0.225);

    /** Dégâts au centre de l'impact (une cible touchée en plein poing) ; décroissance linéaire jusqu'à 0 au bord du rayon. */
    private static final float BLAST_MAX_DAMAGE = 30.0F;

    /** Durée de mise à feu des entités touchées par l'explosion. */
    private static final float BLAST_IGNITE_SECONDS = 8.0F;

    /** Sécurité : au bout de 10 s de vol sans rien toucher, le poing explose là où il est (évite un projectile éternel). */
    private static final int MAX_AGE = 20 * 10;

    /** Vrai seulement pour un poing créé par l'attaque : un poing orphelin (ancienne sauvegarde) est supprimé. */
    private boolean managed;

    /** Vrai dès que l'explosion a eu lieu : garantit une seule explosion / un seul lot de dégâts par poing. */
    private boolean impacted;

    /** Le point de l'explosion (serveur) : FireEffects y centre l'impact visuel. */
    private Vec3 impactPoint = Vec3.ZERO;

    private int age;

    /** Sens du vol (serveur uniquement : le client reçoit l'orientation via la rotation de l'entité). */
    private Vec3 heading = new Vec3(0.0, 0.0, 1.0);

    /** Vitesse de vol constante (serveur) : on ne lit pas getDeltaMovement(), que Minecraft peut modifier (courants d'eau...). */
    private Vec3 velocity = Vec3.ZERO;

    /** Entités déjà recouvertes par le poing au lancer (côté / derrière le lanceur) : ignorées tant qu'elles restent dedans. */
    private final Set<Integer> launchIgnored = new HashSet<>();

    public HikenFist(EntityType<? extends HikenFist> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * Lance le poing : le bout de son pouce part de {@code front}, il fonce vers {@code direction} à {@code speed} blocs/tick,
     * puis explose à l'impact (voir {@link #BLAST_RADIUS}).
     */
    public static HikenFist spawn(ServerLevel level, ServerPlayer owner, Vec3 front, Vec3 direction, double speed) {
        HikenFist fist = new HikenFist(ModEntities.HIKEN_FIST, level);
        fist.managed = true;
        fist.setOwner(owner);
        fist.follow(front, direction);
        fist.velocity = fist.heading.scale(speed);
        fist.setDeltaMovement(fist.velocity);
        fist.collectLaunchOverlaps(level, owner);
        level.addFreshEntity(fist);
        return fist;
    }

    /** Place le poing de sorte que le bout de son pouce soit sur {@code front}, et l'oriente vers {@code direction}. */
    private void follow(Vec3 front, Vec3 direction) {
        Vec3 dir = direction.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : direction.normalize();
        this.heading = dir;
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, dir.y))));
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yRotO = yaw;
        this.xRotO = pitch;
        this.setPos(front.x - dir.x * FRONT_OFFSET, front.y - dir.y * FRONT_OFFSET, front.z - dir.z * FRONT_OFFSET);
    }

    /** Le bout du pouce : le point autour duquel FireEffects dessine la traînée en vol. */
    public Vec3 front() {
        return this.position().add(this.heading.scale(FRONT_OFFSET));
    }

    /**
     * Coup au corps-à-corps : le poing frappe IMMÉDIATEMENT {@code target} (ennemi collé devant le viseur), sans voler, pour qu'il
     * ne puisse pas passer à côté à cause du départ décalé sur la droite. L'explosion part du point de la cible le plus proche du
     * poing (même impact, mêmes dégâts qu'un coup normal).
     */
    public void strikeNow(Entity target) {
        if (this.level() instanceof ServerLevel level) {
            AABB box = target.getBoundingBox();
            Vec3 point = closestPoint(box, this.front()).add(box.getCenter()).scale(0.5); // entre la surface et le centre de la cible
            this.detonate(level, this.position(), point);
        }
    }

    /** Vrai si le poing a explosé (et pas s'il a simplement disparu : monde déchargé, /kill...). */
    public boolean hasImpacted() {
        return this.impacted;
    }

    /** Le point de l'explosion (valide si {@link #hasImpacted()}). */
    public Vec3 impactPoint() {
        return this.impactPoint;
    }

    // ================================================================== géométrie

    /** Les trois axes du poing, déduits de son orientation (yaw/pitch) : même formule côté serveur et côté client. */
    private record Frame(Vec3 side, Vec3 up, Vec3 forward) {
        static Frame of(float yawDeg, float pitchDeg) {
            double yaw = Math.toRadians(yawDeg);
            double pitch = Math.toRadians(pitchDeg);
            double sy = Math.sin(yaw), cy = Math.cos(yaw);
            double sp = Math.sin(pitch), cp = Math.cos(pitch);
            return new Frame(
                    new Vec3(cy, 0.0, sy),               // latéral (toujours horizontal)
                    new Vec3(-sy * sp, cp, cy * sp),     // dessus du poing (s'incline avec la hauteur du vol)
                    new Vec3(-sy * cp, -sp, cy * cp));   // sens du vol
        }
    }

    /** Une boîte orientée : centre, trois axes unitaires et demi-dimensions le long de chacun. */
    private record Obb(Vec3 center, Frame frame, double hSide, double hUp, double hForward) {

        /** La plus petite boîte alignée sur les axes qui contient cette boîte orientée. */
        AABB bounds() {
            double ex = Math.abs(frame.side().x) * hSide + Math.abs(frame.up().x) * hUp + Math.abs(frame.forward().x) * hForward;
            double ey = Math.abs(frame.side().y) * hSide + Math.abs(frame.up().y) * hUp + Math.abs(frame.forward().y) * hForward;
            double ez = Math.abs(frame.side().z) * hSide + Math.abs(frame.up().z) * hUp + Math.abs(frame.forward().z) * hForward;
            return new AABB(center.x - ex, center.y - ey, center.z - ez, center.x + ex, center.y + ey, center.z + ez);
        }

        /** Test exact boîte orientée / boîte alignée (théorème de l'axe séparateur : 15 axes). */
        boolean intersects(AABB box) {
            Vec3 t = box.getCenter().subtract(center);
            double bx = box.getXsize() * 0.5, by = box.getYsize() * 0.5, bz = box.getZsize() * 0.5;
            Vec3[] mine = {frame.side(), frame.up(), frame.forward()};
            Vec3[] world = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
            for (Vec3 axis : world) {
                if (separatedOn(axis, t, bx, by, bz)) return false;
            }
            for (Vec3 axis : mine) {
                if (separatedOn(axis, t, bx, by, bz)) return false;
            }
            for (Vec3 a : mine) {
                for (Vec3 b : world) {
                    if (separatedOn(a.cross(b), t, bx, by, bz)) return false;
                }
            }
            return true;
        }

        private boolean separatedOn(Vec3 axis, Vec3 t, double bx, double by, double bz) {
            if (axis.lengthSqr() < 1.0E-8) {
                return false; // axe dégénéré (vecteurs parallèles) : il ne sépare rien
            }
            double radiusBox = bx * Math.abs(axis.x) + by * Math.abs(axis.y) + bz * Math.abs(axis.z);
            double radiusObb = hSide * Math.abs(frame.side().dot(axis)) + hUp * Math.abs(frame.up().dot(axis))
                    + hForward * Math.abs(frame.forward().dot(axis));
            return Math.abs(t.dot(axis)) > radiusBox + radiusObb + 1.0E-9;
        }
    }

    /** La hitbox du poing (partie principale : dos de la main, phalanges, poignet) quand son centre est en {@code pos}. */
    private static Obb fistVolume(Vec3 pos, Frame f) {
        Vec3 center = pos.add(f.forward().scale(CENTER_FORWARD)).add(f.up().scale(CENTER_UP));
        return new Obb(center, f, HALF_WIDTH, HALF_UP, HALF_LENGTH);
    }

    /** La sonde de blocs : 1 x 1 bloc, collée au devant des phalanges. */
    private static Obb blockProbe(Vec3 pos, Frame f) {
        Vec3 center = pos.add(f.forward().scale(CONTACT_OFFSET - PROBE_DEPTH * 0.5));
        return new Obb(center, f, PROBE_HALF_SIZE, PROBE_HALF_SIZE, PROBE_DEPTH * 0.5);
    }

    private static Vec3 closestPoint(AABB box, Vec3 p) {
        return new Vec3(Mth.clamp(p.x, box.minX, box.maxX), Mth.clamp(p.y, box.minY, box.maxY), Mth.clamp(p.z, box.minZ, box.maxZ));
    }

    /**
     * Pour minecraft : la boîte alignée qui englobe la hitbox du poing (F3+B l'affiche). Appelée jusque dans le constructeur de
     * {@link Entity} : elle ne doit donc dépendre que de la rotation et de constantes.
     */
    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        return fistVolume(pos, Frame.of(this.getYRot(), this.getXRot())).bounds();
    }

    // ================================================================== vol et collisions (serveur)

    @Override
    public void tick() {
        super.tick();
        // La hitbox dépend de l'orientation, qui peut arriver juste après la position : on la recalcule (client et serveur)
        this.setBoundingBox(this.makeBoundingBox());
        if (!(this.level() instanceof ServerLevel level)) {
            return; // client : rien à simuler, la position et l'orientation arrivent du serveur
        }
        if (!this.managed) {
            this.discard(); // poing orphelin
            return;
        }
        if (this.impacted) {
            return;
        }

        this.setDeltaMovement(this.velocity);
        Frame frame = Frame.of(this.getYRot(), this.getXRot());
        Vec3 start = this.position();
        Vec3 motion = this.velocity;

        // Entités candidates : celles qui touchent le volume balayé par le poing pendant ce tick
        Obb startVolume = fistVolume(start, frame);
        AABB sweep = startVolume.bounds().minmax(fistVolume(start.add(motion), frame).bounds());
        List<Entity> candidates = level.getEntities(this, sweep, this::canHitEntity);

        // Les entités du lancer redeviennent touchables dès qu'elles ont quitté le poing
        this.launchIgnored.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !startVolume.intersects(e.getBoundingBox());
        });

        // Balayage par pas de 0,6 bloc : à chaque pas, entités puis blocs
        int steps = Math.max(1, (int) Math.ceil(motion.length() / STEP));
        Vec3 delta = motion.scale(1.0 / steps);
        for (int k = 1; k <= steps; k++) {
            Vec3 center = start.add(delta.scale(k));
            Vec3 front = center.add(frame.forward().scale(CONTACT_OFFSET));

            // 1) entité touchée par le poing (la plus proche du devant si plusieurs)
            Obb fist = fistVolume(center, frame);
            Vec3 entityPoint = null;
            double best = Double.MAX_VALUE;
            for (Entity entity : candidates) {
                if (this.launchIgnored.contains(entity.getId())) {
                    continue;
                }
                AABB box = entity.getBoundingBox();
                if (!fist.intersects(box)) {
                    continue;
                }
                Vec3 axisPoint = this.axisPointFor(center, frame, box);
                if (ServerExplosion.getSeenPercent(axisPoint, entity) <= 0.0F) {
                    continue; // aucune ligne de vue depuis l'axe du poing : l'entité est derrière un mur
                }
                double distance = closestPoint(box, front).distanceToSqr(front);
                if (distance < best) {
                    best = distance;
                    entityPoint = axisPoint;
                }
            }
            if (entityPoint != null) {
                this.detonate(level, center, entityPoint);
                return;
            }

            // 2) bloc touché par la sonde des phalanges
            if (this.probeHitsBlock(level, center, frame)) {
                Vec3 previousFront = start.add(delta.scale(k - 1)).add(frame.forward().scale(CONTACT_OFFSET));
                BlockHitResult clip = level.clip(new ClipContext(previousFront, front, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                Vec3 point = clip.getType() == HitResult.Type.MISS ? front : clip.getLocation();
                // Léger recul : le centre de l'explosion ne doit pas être enfoui dans le bloc touché (sinon il absorberait l'onde)
                this.detonate(level, center, point.subtract(frame.forward().scale(0.15)));
                return;
            }
        }

        this.age++;
        if (this.age >= MAX_AGE) {
            this.detonate(level, start, start.add(frame.forward().scale(CONTACT_OFFSET)));
            return;
        }
        this.setPos(start.add(motion));
    }

    /** Vrai si un bloc solide touche la sonde 1 x 1 des phalanges quand le poing est centré en {@code pos}. */
    private boolean probeHitsBlock(ServerLevel level, Vec3 pos, Frame frame) {
        Obb probe = blockProbe(pos, frame);
        for (VoxelShape shape : level.getBlockCollisions(this, probe.bounds())) {
            for (AABB box : shape.toAabbs()) {
                if (probe.intersects(box)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Le point de l'axe du poing (entre son centre et le bout des phalanges) le plus proche de l'entité touchée : l'explosion
     * part de là. Pour une cible devant le poing, c'est le bout des phalanges.
     */
    private Vec3 axisPointFor(Vec3 center, Frame frame, AABB box) {
        Vec3 front = center.add(frame.forward().scale(CONTACT_OFFSET));
        double along = closestPoint(box, front).subtract(center).dot(frame.forward());
        return center.add(frame.forward().scale(Mth.clamp(along, 0.0, CONTACT_OFFSET)));
    }

    /** Au lancer : repère les entités déjà dans le poing à côté ou derrière le lanceur (monture, familier, allié) pour les ignorer. */
    private void collectLaunchOverlaps(ServerLevel level, ServerPlayer owner) {
        Frame frame = Frame.of(this.getYRot(), this.getXRot());
        Obb volume = fistVolume(this.position(), frame);
        for (Entity entity : level.getEntities(this, volume.bounds(), this::canHitEntity)) {
            AABB box = entity.getBoundingBox();
            if (volume.intersects(box) && box.getCenter().subtract(owner.position()).dot(frame.forward()) < LAUNCH_GRACE_AHEAD) {
                this.launchIgnored.add(entity.getId());
            }
        }
    }

    // ================================================================== impact

    /**
     * Impact : dégâts de zone (une fois par entité), explosion (blocs + recul), puis le poing disparaît (FireEffects prend le relais
     * pour les flammes et les particules). Protégé contre le double déclenchement.
     */
    private void detonate(ServerLevel level, Vec3 fistPosition, Vec3 point) {
        if (this.impacted) {
            return;
        }
        this.impacted = true;
        this.impactPoint = point;
        this.setPos(fistPosition);

        // Les dégâts d'abord (comme l'explosion vanilla) : les blocs que l'explosion va détruire protègent encore les entités
        this.blastEntities(level, point, fistVolume(fistPosition, Frame.of(this.getYRot(), this.getXRot())));

        boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
        level.explode(this, Explosion.getDefaultDamageSource(level, this), new BlastCalculator(point),
                point.x, point.y, point.z, BLAST_POWER, griefing, Level.ExplosionInteraction.MOB);
        this.discard();
    }

    /**
     * Dégâts de zone : toute entité vivante dont la boîte est à moins de {@link #BLAST_RADIUS} du point d'impact, décroissance
     * linéaire (plein tarif au centre, 0 au bord), réduite par les blocs qui la protègent. Une entité recouverte par le poing
     * n'est jamais protégée. Un seul coup par entité : l'explosion vanilla ne blesse personne (voir {@link BlastCalculator}).
     */
    private void blastEntities(ServerLevel level, Vec3 point, Obb fist) {
        Entity owner = this.getOwner();
        DamageSource source = level.damageSources().source(
                owner == null ? DamageTypes.UNATTRIBUTED_FIREBALL : DamageTypes.FIREBALL, this, owner);
        AABB zone = new AABB(point, point).inflate(BLAST_RADIUS);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, zone,
                e -> e != owner && e.isAlive() && !e.isSpectator() && !(e instanceof EnteiSun));
        for (LivingEntity target : targets) {
            AABB box = target.getBoundingBox();
            double distance = closestPoint(box, point).distanceTo(point);
            if (distance > BLAST_RADIUS) {
                continue;
            }
            float exposure = fist.intersects(box) ? 1.0F : ServerExplosion.getSeenPercent(point, target);
            float damage = (float) (BLAST_MAX_DAMAGE * (1.0 - distance / BLAST_RADIUS)) * exposure;
            if (damage < 0.5F) {
                continue;
            }
            target.igniteForSeconds(BLAST_IGNITE_SECONDS);
            if (owner instanceof LivingEntity ownerLiving) {
                // Le joueur devient responsable de ces dégâts (récompenses d'XP, riposte des mobs)
                target.setLastHurtByMob(ownerLiving);
            }
            if (target.hurtServer(level, source, damage)) {
                EnchantmentHelper.doPostAttackEffects(level, target, source);
            }
        }
    }

    /**
     * Explosion vanilla (blocs détruits, feu, son, recul synchronisé avec les clients) SANS dégâts aux entités (déjà appliqués par
     * {@link #blastEntities}, jamais en double) et avec un recul limité au même rayon que les dégâts.
     */
    private static final class BlastCalculator extends ExplosionDamageCalculator {
        private final Vec3 center;

        BlastCalculator(Vec3 center) {
            this.center = center;
        }

        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            if (closestPoint(entity.getBoundingBox(), this.center).distanceTo(this.center) > BLAST_RADIUS) {
                return 0.0F;
            }
            return super.getKnockbackMultiplier(entity);
        }
    }

    // ================================================================== entité

    @Override
    protected boolean canHitEntity(Entity entity) {
        // Ni le lanceur, ni les autres projectiles (flèches, boules de feu, autres poings), ni le soleil visuel d'Entei
        return entity != this.getOwner() && !(entity instanceof Projectile) && !(entity instanceof EnteiSun)
                && entity.canBeHitByProjectile();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // rien à synchroniser en plus : position, rotation et vitesse sont envoyées par Minecraft
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false; // le poing est indestructible : l'explosion de sa propre attaque ne le touche jamais
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true; // ni poussé ni détruit par les explosions (la sienne, celles des autres attaques de feu...)
    }

    @Override
    public boolean isPickable() {
        return false; // ni cible de projectile, ni de curseur
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
        return false; // la hitbox est grande : un courant d'eau ne doit jamais dévier le poing
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false; // un poing de 7 blocs ne doit pas se téléporter en frôlant un portail
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // Le poing fait ~7 blocs de large : la distance d'affichage par défaut (~64 blocs) serait bien trop courte
        return distance < 256.0 * 256.0;
    }
}
