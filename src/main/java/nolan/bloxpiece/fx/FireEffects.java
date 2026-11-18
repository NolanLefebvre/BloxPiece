package nolan.bloxpiece.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModParticles;

/**
 * Tous les effets visuels (et les dégâts de zone bonus) des attaques de feu.
 * Les projectiles sont "suivis" : à chaque tick on dessine leur traînée, et quand ils disparaissent
 * (= impact) on déclenche une grosse explosion visuelle.
 */
public class FireEffects {

    /** Les tailles de projectiles (une par attaque de feu). Modifie ces valeurs pour régler l'intensité de chaque attaque. */
    public enum Kind {
        //     rayon traînée, flammes coeur, fumée, rayon impact, dégâts, feu (s), particules impact, gerbes d'explosion, points onde, vitesse onde, hauteur colonne, rayon de la sphère de feu (0 = aucune)
        SMALL (0.25, 3,  0, 2.5, 3.0f, 3, 25,  0, 24, 0.25, 0,  0.0),
        // Hiken : un poing de feu géant (le projectile est le modèle 3D HikenFist lui-même, pas de sphère de particules). Rayon de traînée = taille du poing ;
        // peu de flammes au coeur pour ne pas cacher le modèle. Le rayon d'impact est celui de l'explosion (HikenFist.BLAST_RADIUS) : particules, vague de feu
        // et explosion de blocs ont la même taille. Dégâts et mise à feu : 0 ici, c'est HikenFist qui les applique (une seule fois, avec décroissance).
        // Pas d'EXPLOSION_EMITTER (emitters = 0) : l'explosion vanilla en émet déjà un, deux se superposeraient en une explosion qui semble double.
        HIKEN (1.10, 2,  0, HikenFist.BLAST_RADIUS, 0.0f, 0, 220, 0, 56, 0.6, 6,  0.0),
        // Météore : le projectile est affiché avec le modèle 3D MeteorModel (voir Meteor). Peu de flammes au coeur (3, avant 12) pour ne pas
        // cacher le modèle ; la traînée dense de flammes et de fumée est dessinée DERRIÈRE lui (voir meteorTrail). Impact inchangé.
        METEOR(0.90, 3, 4, 7.5, 10.0f, 8, 160, 3, 56, 0.65, 8, 0.0),
        // Dai Enkai: Entei : un petit soleil. Dégâts et rayon au-dessus du météore, mais un seul projectile.
        // (sa boule visible est le modèle Blockbench de EnteiSun : pas de sphère de particules)
        // Traînée +50 % (4.5) pour suivre la taille de la boule. Impact COLOSSAL, taille DOUBLÉE par rapport à la version précédente :
        // rayon 64 blocs (avant : 32), 2 800 particules, 60 explosions, onde de choc deux fois plus large et rapide,
        // colonne de feu de 80 blocs. Les dégâts (20) et la mise à feu (14 s) ne changent pas.
        // En plus, une vague de feu en dôme s'étend jusqu'au rayon de l'impact (voir tickBlasts).
        ENTEI (4.50, 4, 0, 64.0, 20.0f, 14, 2800, 60, 320, 3.6, 80, 0.0);

        final double trailRadius;
        final int coreCount;
        final int smokeCount;
        final double impactRadius;
        final float damage;
        final float igniteSeconds;
        final int burst;
        final int emitters;
        final int shockPoints;
        final double shockSpeed;
        final int columnHeight;
        final double sphereRadius;

        Kind(double trailRadius, int coreCount, int smokeCount, double impactRadius, float damage,
             float igniteSeconds, int burst, int emitters, int shockPoints, double shockSpeed, int columnHeight,
             double sphereRadius) {
            this.trailRadius = trailRadius;
            this.coreCount = coreCount;
            this.smokeCount = smokeCount;
            this.impactRadius = impactRadius;
            this.damage = damage;
            this.igniteSeconds = igniteSeconds;
            this.burst = burst;
            this.emitters = emitters;
            this.shockPoints = shockPoints;
            this.shockSpeed = shockSpeed;
            this.columnHeight = columnHeight;
            this.sphereRadius = sphereRadius;
        }
    }

    private static class Tracked {
        final Entity projectile;
        final ServerLevel level;
        final ServerPlayer owner;
        final Kind kind;
        final double scale;  // puissance de charge (Dai Enkai: Entei chargé) : taille de la traînée, de l'impact et dégâts x scale
        final Entity visual; // corps visible qui suit le projectile (peut être null : Hiken n'en a pas, son poing est le projectile), supprimé à l'impact
        double x, y, z;
        int age;

        Tracked(Entity projectile, ServerLevel level, ServerPlayer owner, Kind kind, Entity visual, double scale) {
            this.projectile = projectile;
            this.scale = scale;
            this.visual = visual;
            this.level = level;
            this.owner = owner;
            this.kind = kind;
            this.x = projectile.getX();
            this.y = projectile.getY();
            this.z = projectile.getZ();
        }
    }

    private static final List<Tracked> TRACKED = new ArrayList<>();

    /** Vague de feu en dôme qui s'étend après l'impact de Dai Enkai: Entei (dessinée tick après tick). */
    private static final class Blast {
        final ServerLevel level;
        final double x, y, z;
        final double maxRadius;
        final int ticks;      // durée de l'expansion
        final int domePoints; // densité du dôme de flammes
        final boolean sphere; // true : vraie boule 3D (Dai Enkai: Entei, voir BlastSphere) ; false : anneau au sol + dôme (Hiken)
        int age;

        Blast(ServerLevel level, double x, double y, double z, double maxRadius, int ticks, int domePoints, boolean sphere) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.maxRadius = maxRadius;
            this.ticks = ticks;
            this.domePoints = domePoints;
            this.sphere = sphere;
        }
    }

    private static final int BLAST_TICKS = 20; // durée de l'expansion de Dai Enkai: Entei (20 ticks = 1 seconde)
    private static final int HIKEN_BLAST_TICKS = 8; // Hiken : un choc plus bref et plus sec
    private static final List<Blast> BLASTS = new ArrayList<>();

    /** À appeler juste après avoir fait apparaître un projectile : il aura traînée + impact spectaculaire. */
    public static void track(Entity projectile, ServerPlayer owner, Kind kind) {
        track(projectile, owner, kind, null);
    }

    /** Pareil, avec un corps visible (ex : le soleil de Dai Enkai: Entei) qui suit le projectile puis disparaît à l'impact. */
    public static void track(Entity projectile, ServerPlayer owner, Kind kind, Entity visual) {
        track(projectile, owner, kind, visual, 1.0);
    }

    /** Pareil, avec une puissance de charge : rayon de l'impact, dégâts, mise à feu, traînée et particules x scale. */
    public static void track(Entity projectile, ServerPlayer owner, Kind kind, Entity visual, double scale) {
        if (projectile.level() instanceof ServerLevel level) {
            TRACKED.add(new Tracked(projectile, level, owner, kind, visual, scale));
        }
    }

    public static void clear() {
        for (Tracked t : TRACKED) {
            if (t.visual != null) {
                t.visual.discard();
            }
        }
        TRACKED.clear();
        BLASTS.clear();
    }

    /**
     * Vrai si ces dégâts viennent d'une attaque de feu lancée par la victime elle-même (explosion de sa boule de feu,
     * de ses météores...). Le propriétaire de chaque projectile est déjà enregistré (Tracked.owner, et Projectile.getOwner()
     * côté vanilla) : on compare l'auteur des dégâts, la victime et le projectile à l'origine de l'explosion.
     */
    public static boolean isOwnAttack(LivingEntity victim, DamageSource source) {
        if (source.getEntity() != victim) {
            return false; // l'auteur des dégâts n'est pas la victime : ce n'est pas sa propre attaque
        }
        Entity direct = source.getDirectEntity();
        for (Tracked t : TRACKED) {
            if (t.owner == victim && (direct == null || direct == t.projectile)) {
                return true;
            }
        }
        return false;
    }

    /** Vrai si cette entité est un projectile d'une attaque de feu en vol (utile pour reconnaître les attaques de fruit). */
    public static boolean isTracked(Entity entity) {
        for (Tracked t : TRACKED) {
            if (t.projectile == entity || t.visual == entity) {
                return true;
            }
        }
        return false;
    }

    /** Appelé à chaque tick serveur. */
    public static void tick() {
        tickBlasts();

        Iterator<Tracked> it = TRACKED.iterator();
        while (it.hasNext()) {
            Tracked t = it.next();
            Entity e = t.projectile;

            if (e.isRemoved()) {
                if (t.visual != null) {
                    t.visual.discard(); // le soleil disparaît dans l'explosion
                }
                if (e instanceof HikenFist fist) {
                    if (!fist.hasImpacted()) {
                        it.remove(); // disparu sans exploser (monde déchargé, /kill...) : pas d'impact fantôme
                        continue;
                    }
                    Vec3 point = fist.impactPoint(); // le point exact de l'explosion
                    t.x = point.x;
                    t.y = point.y;
                    t.z = point.z;
                }
                impact(t); // le projectile a disparu = il a touché quelque chose
                it.remove();
                continue;
            }

            if (e instanceof HikenFist fist) {
                // Hiken : le poing EST le projectile ; traînée et impact se calent sur le devant de ses phalanges
                Vec3 front = fist.front();
                t.x = front.x;
                t.y = front.y;
                t.z = front.z;
            } else {
                t.x = e.getX();
                t.y = e.getY();
                t.z = e.getZ();
                if (t.visual != null) {
                    t.visual.setPos(t.x, t.y, t.z); // le soleil reste centré sur la boule de feu
                }
            }
            trail(t);
            t.age++;
        }
    }

    // ---------------------------------------------------------------- traînée

    private static void trail(Tracked t) {
        ServerLevel level = t.level;
        Kind k = t.kind;

        Vec3 dir = t.projectile.getDeltaMovement();
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, -1, 0) : dir.normalize();

        // Deux vecteurs perpendiculaires à la direction, pour dessiner une spirale autour du projectile
        Vec3 ref = Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(ref).normalize();
        Vec3 v = dir.cross(u);

        // Double hélice de flammes
        for (int i = 0; i < 2; i++) {
            double angle = t.age * 0.7 + i * Math.PI;
            double cos = Math.cos(angle) * k.trailRadius;
            double sin = Math.sin(angle) * k.trailRadius;
            level.sendParticles(ModParticles.FIRE_FLAME,
                    t.x + u.x * cos + v.x * sin,
                    t.y + u.y * cos + v.y * sin,
                    t.z + u.z * cos + v.z * sin,
                    1, 0, 0, 0, 0.0);
        }

        // Coeur incandescent
        level.sendParticles(ModParticles.FIRE_FLAME, t.x, t.y, t.z,
                k.coreCount, k.trailRadius * 0.4, k.trailRadius * 0.4, k.trailRadius * 0.4, 0.03);

        // Fumée derrière le projectile
        if (k.smokeCount > 0) {
            level.sendParticles(ModParticles.FIRE_SMOKE,
                    t.x - dir.x * 0.8, t.y - dir.y * 0.8, t.z - dir.z * 0.8,
                    k.smokeCount, k.trailRadius * 0.5, k.trailRadius * 0.5, k.trailRadius * 0.5, 0.01);
        }

        // Grosse boule de feu (Entei) : nuage de flammes + enveloppe qui tourne, centrés sur le projectile
        if (k.sphereRadius > 0) {
            level.sendParticles(ModParticles.FIRE_FLAME, t.x, t.y, t.z,
                    40, k.sphereRadius * 0.4, k.sphereRadius * 0.4, k.sphereRadius * 0.4, 0.02);
            fireSphere(level, t.x, t.y, t.z, k.sphereRadius, 28, t.age * 0.35);
        }

        // Dai Enkai: Entei : anneau de flammes qui tourne autour de la boule + traîne de comète
        if (k == Kind.ENTEI) {
            enteiAura(t, dir, u, v);
        }

        // Météore : longue traînée de flammes et de fumée derrière le modèle 3D
        if (k == Kind.METEOR) {
            meteorTrail(t, dir);
        }

        // Hiken : étincelles et braises autour de la masse de flammes (le poing et sa masse de flammes sont le modèle 3D)
        if (k == Kind.HIKEN) {
            hikenSparks(t, dir);
        }

        // Légère traînée de fumée noire et braises qui se détachent et flottent derrière le projectile
        if (t.age % 2 == 0) {
            double back = 1.0 + k.trailRadius;
            level.sendParticles(ModParticles.FIRE_SMOKE, t.x - dir.x * back, t.y - dir.y * back, t.z - dir.z * back,
                    1, k.trailRadius * 0.4, k.trailRadius * 0.4, k.trailRadius * 0.4, 0.01);
            level.sendParticles(ModParticles.FIRE_EMBER, t.x - dir.x * back, t.y - dir.y * back, t.z - dir.z * back,
                    k == Kind.SMALL ? 1 : 3, k.trailRadius * 0.8, k.trailRadius * 0.8, k.trailRadius * 0.8, 0.02);
        }

        // Braises plus grosses de temps en temps
        if (k != Kind.SMALL && k != Kind.ENTEI && t.age % 2 == 0) {
            level.sendParticles(ModParticles.FIRE_EMBER, t.x, t.y, t.z, 2, k.trailRadius, k.trailRadius, k.trailRadius, 0.0);
        }
    }

    /** Effets en vol du soleil : deux anneaux de flammes (perpendiculaires au vol) qui tournent, et une traîne de braises. */
    private static void enteiAura(Tracked t, Vec3 dir, Vec3 u, Vec3 v) {
        ServerLevel level = t.level;
        double ringRadius = t.kind.trailRadius * 1.1 * t.scale;
        int points = (int) (24 * t.scale);

        for (int ring = 0; ring < 2; ring++) {
            double spin = t.age * (ring == 0 ? 0.4 : -0.3);
            for (int i = 0; i < points; i++) {
                double a = 2 * Math.PI * i / points + spin;
                double cos = Math.cos(a) * ringRadius * (ring == 0 ? 1.0 : 0.8);
                double sin = Math.sin(a) * ringRadius * (ring == 0 ? 1.0 : 0.8);
                level.sendParticles(ModParticles.FIRE_FLAME,
                        t.x + u.x * cos + v.x * sin,
                        t.y + u.y * cos + v.y * sin,
                        t.z + u.z * cos + v.z * sin,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        // Traîne de braises derrière le soleil, comme une comète
        for (int i = 0; i < 8; i++) {
            double back = (2.0 + level.getRandom().nextDouble() * 8.0) * t.scale;
            level.sendParticles(ModParticles.FIRE_FLAME,
                    t.x - dir.x * back, t.y - dir.y * back, t.z - dir.z * back,
                    2, 1.2 * t.scale, 1.2 * t.scale, 1.2 * t.scale, 0.02);
        }
        if (t.age % 2 == 0) {
            level.sendParticles(ModParticles.FIRE_EMBER, t.x - dir.x * 2.0, t.y - dir.y * 2.0, t.z - dir.z * 2.0,
                    3, 2.0, 2.0, 2.0, 0.0);
        }
    }

    /**
     * Météore : traînée dense derrière le modèle (qui fait ~1,8 bloc de rayon) : flammes qui s'élargissent en s'éloignant, puis
     * une colonne de fumée noire plus longue, et des braises qui se détachent.
     */
    private static void meteorTrail(Tracked t, Vec3 dir) {
        ServerLevel level = t.level;
        for (int i = 0; i < 6; i++) {
            double back = 1.5 + level.getRandom().nextDouble() * 5.0;
            double spread = 0.35 + back * 0.12;
            level.sendParticles(ModParticles.FIRE_FLAME,
                    t.x - dir.x * back, t.y + 0.5 - dir.y * back, t.z - dir.z * back,
                    1, spread, spread, spread, 0.02);
        }
        for (int i = 0; i < 3; i++) {
            double back = 4.0 + level.getRandom().nextDouble() * 8.0;
            double spread = 0.4 + back * 0.08;
            level.sendParticles(ModParticles.FIRE_SMOKE,
                    t.x - dir.x * back, t.y + 0.5 - dir.y * back, t.z - dir.z * back,
                    1, spread, spread, spread, 0.01);
        }
        level.sendParticles(ModParticles.FIRE_EMBER, t.x - dir.x * 2.0, t.y + 0.5 - dir.y * 2.0, t.z - dir.z * 2.0,
                2, 1.0, 1.0, 1.0, 0.03);
    }

    /** Hiken : quelques braises irrégulières qui se détachent de la masse de flammes, derrière le poing (le poing lui-même est le modèle 3D). */
    private static void hikenSparks(Tracked t, Vec3 dir) {
        ServerLevel level = t.level;
        for (int i = 0; i < 5; i++) {
            double back = 4.0 + level.getRandom().nextDouble() * 18.0;
            double spread = 0.4 + back * 0.06;
            level.sendParticles(ModParticles.FIRE_FLAME,
                    t.x - dir.x * back, t.y - dir.y * back, t.z - dir.z * back,
                    1, spread, spread, spread, 0.02);
        }
    }

    // ---------------------------------------------------------------- impact

    private static void impact(Tracked t) {
        ServerLevel level = t.level;
        Kind k = t.kind;
        double x = t.x, y = t.y, z = t.z;
        double r = k.impactRadius * t.scale;
        int burst = (int) Math.min(k.burst * t.scale, 6000); // plus de particules quand c'est plus gros (plafonné)

        // Pluie de braises qui retombent doucement en flottant après l'explosion
        level.sendParticles(ModParticles.FIRE_EMBER, x, y + 1.0, z, Math.max(10, burst / 4), r * 0.5, r * 0.4, r * 0.5, 0.08);

        // Grosses explosions visuelles
        if (k.emitters > 0) {
            // Entei : les gerbes sont réparties en volume (même étalement en hauteur qu'en largeur), pas en nappe plate
            double emitterHeight = k == Kind.ENTEI ? r * 0.25 : 0.3;
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 0.5, z, k.emitters, r * 0.25, emitterHeight, r * 0.25, 0.0);
        }

        // Boule de feu qui jaillit dans toutes les directions
        level.sendParticles(ModParticles.FIRE_FLAME, x, y + 0.5, z, burst, r * 0.35, r * 0.3, r * 0.35, 0.3);
        level.sendParticles(ModParticles.FIRE_EMBER, x, y + 0.5, z, burst / 3, r * 0.4, r * 0.2, r * 0.4, 0.0);
        level.sendParticles(ModParticles.FIRE_SMOKE, x, y + 1.0, z, burst / 2, r * 0.3, r * 0.3, r * 0.3, 0.08);

        // Onde de choc : anneau de flammes (puis de fumée) qui s'étend vers l'extérieur.
        // Avec count = 0, les "offsets" servent de direction de la particule.
        // Dai Enkai: Entei : l'onde part dans TOUTES les directions (sphère) au lieu d'un anneau horizontal plat
        if (k == Kind.ENTEI) {
            int shockPoints = (int) Math.min(k.shockPoints * t.scale, 900);
            sphericalShock(level, ModParticles.FIRE_FLAME, x, y + 0.5, z, shockPoints, k.shockSpeed * t.scale);
            sphericalShock(level, ModParticles.FIRE_SMOKE, x, y + 0.5, z, shockPoints, k.shockSpeed * 0.6 * t.scale);
        } else {
            shockwave(level, ModParticles.FIRE_FLAME, x, y + 0.2, z, k.shockPoints, k.shockSpeed);
            shockwave(level, ModParticles.FIRE_SMOKE, x, y + 0.3, z, k.shockPoints, k.shockSpeed * 0.6);
        }

        // Colonne de feu qui monte vers le ciel
        for (int h = 0; h < (int) (k.columnHeight * 2 * t.scale); h++) {
            level.sendParticles(ModParticles.FIRE_FLAME, x, y + h * 0.5, z, 4, 0.35, 0.15, 0.35, 0.06);
            if (h % 3 == 0) {
                level.sendParticles(ModParticles.FIRE_SMOKE, x, y + h * 0.5, z, 1, 0.3, 0.1, 0.3, 0.02);
            }
        }

        // Dai Enkai: Entei : en plus, une vague de feu en dôme qui s'étend jusqu'au rayon de l'impact
        if (k == Kind.ENTEI) {
            BLASTS.add(new Blast(level, x, y, z, r, BLAST_TICKS, 200, true));
        }

        // Hiken : anneau de flammes au sol + petit dôme, jusqu'au bord de l'explosion. Les dégâts, la mise à feu et le recul
        // sont déjà gérés par HikenFist (une seule fois par entité) : rien à ajouter ici.
        if (k == Kind.HIKEN) {
            BLASTS.add(new Blast(level, x, y, z, r, HIKEN_BLAST_TICKS, 70, false));
            return;
        }

        // Dégâts bonus en zone + mise à feu (en plus de l'explosion normale)
        AABB box = new AABB(x - r, y - r, z - r, x + r, y + r, z + r);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != t.owner && e.isAlive() && !(e instanceof EnteiSun));
        for (LivingEntity target : targets) {
            if (target.distanceToSqr(x, y, z) <= r * r) {
                target.igniteForSeconds(k.igniteSeconds * (float) t.scale);
                if (t.owner != null) {
                    // Le joueur devient responsable de ces dégâts (récompenses d'XP, riposte des mobs)
                    target.setLastHurtByMob(t.owner);
                }
                target.hurtServer(level, t.owner != null
                        ? level.damageSources().source(DamageTypes.IN_FIRE, t.owner)
                        : level.damageSources().inFire(), k.damage * (float) t.scale);
            }
        }
    }

    /** Fait avancer les vagues de feu : Entei = une boule 3D qui grossit (BlastSphere) ; Hiken = un anneau au sol + un dôme de flammes. */
    private static void tickBlasts() {
        Iterator<Blast> it = BLASTS.iterator();
        while (it.hasNext()) {
            Blast b = it.next();
            b.age++;
            double progress = Math.min(1.0, (double) b.age / b.ticks);
            double eased = 1.0 - (1.0 - progress) * (1.0 - progress); // part vite, ralentit à la fin
            double radius = b.maxRadius * eased;

            if (b.sphere) {
                // Dai Enkai: Entei : une vraie boule 3D (voir BlastSphere), visible de tous les côtés, avec ses cercles posés sur la surface
                emitSphere(b, radius);
            } else {
                // Anneau de flammes au sol (plus il est grand, plus il a de points)
                int ringPoints = Math.max(24, Math.min(480, (int) (radius * 7)));
                for (int i = 0; i < ringPoints; i++) {
                    double a = 2 * Math.PI * i / ringPoints;
                    b.level.sendParticles(ModParticles.FIRE_FLAME,
                            b.x + Math.cos(a) * radius, b.y + 0.3, b.z + Math.sin(a) * radius,
                            1, 0.0, 0.1, 0.0, 0.0);
                }

                // Dôme : demi-sphère de flammes (points répartis régulièrement, hémisphère du haut)
                double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
                int domePoints = b.domePoints; // Entei : 200 (dôme final de 64 blocs de rayon) ; Hiken : 70
                for (int i = 0; i < domePoints; i++) {
                    double yy = (i + 0.5) / domePoints;             // 0..1 : du sol au sommet
                    double ring = Math.sqrt(1.0 - yy * yy);
                    double a = i * goldenAngle;
                    b.level.sendParticles(ModParticles.FIRE_FLAME,
                            b.x + Math.cos(a) * ring * radius, b.y + yy * radius * 0.8, b.z + Math.sin(a) * ring * radius,
                            1, 0.0, 0.0, 0.0, 0.0);
                }

                // Fumée à l'arrière du front de feu
                if (b.age % 2 == 0) {
                    shockwave(b.level, ModParticles.FIRE_SMOKE, b.x, b.y + 0.5, b.z, 48, 0.2 + 0.15 * progress);
                }
            }

            if (b.age >= b.ticks) {
                it.remove();
            }
        }
    }

    /**
     * Envoie les particules de la boule de Dai Enkai: Entei pour ce tick. La boule fait jusqu'à 64 blocs de rayon : les particules
     * sont envoyées avec la limite de distance levée (sinon elles n'apparaissent qu'à moins de 32 blocs du joueur, et on ne verrait que
     * le morceau de sphère le plus proche, pas la boule entière sous tous les angles).
     */
    private static void emitSphere(Blast b, double radius) {
        BlastSphere.emit(b.x, b.y + 0.5, b.z, radius, b.age, (px, py, pz, kind) -> {
            var particle = kind == BlastSphere.Kind.SMOKE ? ModParticles.FIRE_SMOKE : ModParticles.FIRE_FLAME;
            for (ServerPlayer player : b.level.players()) {
                b.level.sendParticles(player, particle, true, true, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        });
    }

    /** Onde de choc sphérique : des particules qui partent du centre dans toutes les directions (points de Fibonacci sur la sphère). */
    private static void sphericalShock(ServerLevel level, net.minecraft.core.particles.SimpleParticleType particle,
                                       double x, double y, double z, int points, double speed) {
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < points; i++) {
            double dy = 1.0 - 2.0 * (i + 0.5) / points;
            double ring = Math.sqrt(1.0 - dy * dy);
            double a = i * goldenAngle;
            // count = 0 : les "offsets" servent de direction de la particule
            level.sendParticles(particle, x, y, z, 0, Math.cos(a) * ring, dy, Math.sin(a) * ring, speed);
        }
    }

    // ---------------------------------------------------------------- effets de lancement

    /** Anneau de flammes qui tourne autour d'un point, dans un plan incliné (tiltX / tiltZ en radians). */
    public static void orbitRing(ServerLevel level, Vec3 center, double radius, int points, double spin, double tiltX, double tiltZ) {
        double cosX = Math.cos(tiltX), sinX = Math.sin(tiltX);
        double cosZ = Math.cos(tiltZ), sinZ = Math.sin(tiltZ);
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points + spin;
            double px = Math.cos(a) * radius;
            double py = 0.0;
            double pz = Math.sin(a) * radius;
            // inclinaison autour de X, puis autour de Z
            double y1 = py * cosX - pz * sinX;
            double z1 = py * sinX + pz * cosX;
            double x2 = px * cosZ - y1 * sinZ;
            double y2 = px * sinZ + y1 * cosZ;
            level.sendParticles(ModParticles.FIRE_FLAME, center.x + x2, center.y + y2, center.z + z1, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Anneau de particules qui part de (x,y,z) vers l'extérieur. */
    public static void shockwave(ServerLevel level, net.minecraft.core.particles.SimpleParticleType particle,
                                 double x, double y, double z, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            level.sendParticles(particle, x, y, z, 0, Math.cos(angle), 0.02, Math.sin(angle), speed);
        }
    }

    /** Cercle fixe de flammes posé au sol (sert à "marquer" une zone). */
    public static void groundRing(ServerLevel level, double cx, double y, double cz, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            level.sendParticles(ModParticles.FIRE_FLAME,
                    cx + Math.cos(angle) * radius, y + 0.1, cz + Math.sin(angle) * radius,
                    2, 0.05, 0.15, 0.05, 0.01);
        }
    }

    /** Sphère de flammes : points répartis régulièrement sur la surface ; {@code spin} la fait tourner (en radians). */
    public static void fireSphere(ServerLevel level, double cx, double cy, double cz, double radius, int points, double spin) {
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < points; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / points;
            double ring = Math.sqrt(1.0 - y * y);
            double angle = i * goldenAngle + spin;
            level.sendParticles(ModParticles.FIRE_FLAME,
                    cx + Math.cos(angle) * ring * radius, cy + y * radius, cz + Math.sin(angle) * ring * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Tourbillon de flammes autour d'un point, qui monte en spirale. */
    public static void fireTornado(ServerLevel level, double x, double y, double z, double radius, double height, int turns) {
        int steps = (int) (height * 8);
        for (int i = 0; i < steps; i++) {
            double progress = (double) i / steps;
            double angle = progress * turns * 2 * Math.PI;
            double rr = radius * (1.0 - progress * 0.4);
            level.sendParticles(ModParticles.FIRE_FLAME,
                    x + Math.cos(angle) * rr, y + progress * height, z + Math.sin(angle) * rr,
                    2, 0.05, 0.05, 0.05, 0.02);
        }
    }
}
