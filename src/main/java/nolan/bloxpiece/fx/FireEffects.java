package nolan.bloxpiece.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tous les effets visuels (et les dégâts de zone bonus) des attaques de feu.
 * Les projectiles sont "suivis" : à chaque tick on dessine leur traînée, et quand ils disparaissent
 * (= impact) on déclenche une grosse explosion visuelle.
 */
public class FireEffects {

    /** Les 4 tailles de projectiles. Modifie ces valeurs pour régler l'intensité de chaque attaque. */
    public enum Kind {
        //     rayon traînée, flammes coeur, fumée, rayon impact, dégâts, feu (s), particules impact, gerbes d'explosion, points onde, vitesse onde, hauteur colonne, rayon de la sphère de feu (0 = aucune)
        SMALL (0.25, 3,  0, 2.5, 3.0f, 3, 25,  0, 24, 0.25, 0,  0.0),
        GREAT (0.60, 8,  3, 7.0, 8.0f, 6, 120, 2, 48, 0.55, 6,  0.0),
        METEOR(0.90, 12, 4, 7.5, 10.0f, 8, 160, 3, 56, 0.65, 8, 0.0),
        // Dai Enkai: Entei : un petit soleil. Dégâts et rayon au-dessus du météore, mais un seul projectile.
        // (sa boule visible est le modèle Blockbench de EnteiSun : pas de sphère de particules)
        ENTEI (3.00, 3, 0, 12.0, 16.0f, 10, 300, 6, 72, 0.80, 12, 0.0);

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
        final Entity visual; // corps visible qui suit le projectile (peut être null), supprimé à l'impact
        double x, y, z;
        int age;

        Tracked(Entity projectile, ServerLevel level, ServerPlayer owner, Kind kind, Entity visual) {
            this.projectile = projectile;
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

    /** À appeler juste après avoir fait apparaître un projectile : il aura traînée + impact spectaculaire. */
    public static void track(Entity projectile, ServerPlayer owner, Kind kind) {
        track(projectile, owner, kind, null);
    }

    /** Pareil, avec un corps visible (ex : le soleil de Dai Enkai: Entei) qui suit le projectile puis disparaît à l'impact. */
    public static void track(Entity projectile, ServerPlayer owner, Kind kind, Entity visual) {
        if (projectile.level() instanceof ServerLevel level) {
            TRACKED.add(new Tracked(projectile, level, owner, kind, visual));
        }
    }

    public static void clear() {
        for (Tracked t : TRACKED) {
            if (t.visual != null) {
                t.visual.discard();
            }
        }
        TRACKED.clear();
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

    /** Appelé à chaque tick serveur. */
    public static void tick() {
        Iterator<Tracked> it = TRACKED.iterator();
        while (it.hasNext()) {
            Tracked t = it.next();
            Entity e = t.projectile;

            if (e.isRemoved()) {
                if (t.visual != null) {
                    t.visual.discard(); // le soleil disparaît dans l'explosion
                }
                impact(t); // le projectile a disparu = il a touché quelque chose
                it.remove();
                continue;
            }

            t.x = e.getX();
            t.y = e.getY();
            t.z = e.getZ();
            if (t.visual != null) {
                t.visual.setPos(t.x, t.y, t.z); // le soleil reste centré sur la boule de feu
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
            level.sendParticles(ParticleTypes.FLAME,
                    t.x + u.x * cos + v.x * sin,
                    t.y + u.y * cos + v.y * sin,
                    t.z + u.z * cos + v.z * sin,
                    1, 0, 0, 0, 0.0);
        }

        // Coeur incandescent
        level.sendParticles(ParticleTypes.FLAME, t.x, t.y, t.z,
                k.coreCount, k.trailRadius * 0.4, k.trailRadius * 0.4, k.trailRadius * 0.4, 0.03);

        // Fumée derrière le projectile
        if (k.smokeCount > 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    t.x - dir.x * 0.8, t.y - dir.y * 0.8, t.z - dir.z * 0.8,
                    k.smokeCount, k.trailRadius * 0.5, k.trailRadius * 0.5, k.trailRadius * 0.5, 0.01);
        }

        // Grosse boule de feu (Entei) : nuage de flammes + enveloppe qui tourne, centrés sur le projectile
        if (k.sphereRadius > 0) {
            level.sendParticles(ParticleTypes.FLAME, t.x, t.y, t.z,
                    40, k.sphereRadius * 0.4, k.sphereRadius * 0.4, k.sphereRadius * 0.4, 0.02);
            fireSphere(level, t.x, t.y, t.z, k.sphereRadius, 28, t.age * 0.35);
        }

        // Gouttes de lave de temps en temps
        if (k != Kind.SMALL && k != Kind.ENTEI && t.age % 2 == 0) {
            level.sendParticles(ParticleTypes.LAVA, t.x, t.y, t.z, 2, k.trailRadius, k.trailRadius, k.trailRadius, 0.0);
        }
    }

    // ---------------------------------------------------------------- impact

    private static void impact(Tracked t) {
        ServerLevel level = t.level;
        Kind k = t.kind;
        double x = t.x, y = t.y, z = t.z;
        double r = k.impactRadius;

        // Grosses explosions visuelles
        if (k.emitters > 0) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 0.5, z, k.emitters, r * 0.25, 0.3, r * 0.25, 0.0);
        }

        // Boule de feu qui jaillit dans toutes les directions
        level.sendParticles(ParticleTypes.FLAME, x, y + 0.5, z, k.burst, r * 0.35, r * 0.3, r * 0.35, 0.3);
        level.sendParticles(ParticleTypes.LAVA, x, y + 0.5, z, k.burst / 3, r * 0.4, r * 0.2, r * 0.4, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.0, z, k.burst / 2, r * 0.3, r * 0.3, r * 0.3, 0.08);

        // Onde de choc : anneau de flammes (puis de fumée) qui s'étend vers l'extérieur.
        // Avec count = 0, les "offsets" servent de direction de la particule.
        shockwave(level, ParticleTypes.FLAME, x, y + 0.2, z, k.shockPoints, k.shockSpeed);
        shockwave(level, ParticleTypes.LARGE_SMOKE, x, y + 0.3, z, k.shockPoints, k.shockSpeed * 0.6);

        // Colonne de feu qui monte vers le ciel
        for (int h = 0; h < k.columnHeight * 2; h++) {
            level.sendParticles(ParticleTypes.FLAME, x, y + h * 0.5, z, 4, 0.35, 0.15, 0.35, 0.06);
            if (h % 3 == 0) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + h * 0.5, z, 1, 0.3, 0.1, 0.3, 0.02);
            }
        }

        // Dégâts bonus en zone + mise à feu (en plus de l'explosion normale)
        AABB box = new AABB(x - r, y - r, z - r, x + r, y + r, z + r);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != t.owner && e.isAlive() && !(e instanceof EnteiSun));
        for (LivingEntity target : targets) {
            if (target.distanceToSqr(x, y, z) <= r * r) {
                target.igniteForSeconds(k.igniteSeconds);
                if (t.owner != null) {
                    // Le joueur devient responsable de ces dégâts (récompenses d'XP, riposte des mobs)
                    target.setLastHurtByMob(t.owner);
                }
                target.hurtServer(level, level.damageSources().inFire(), k.damage);
            }
        }
    }

    // ---------------------------------------------------------------- effets de lancement

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
            level.sendParticles(ParticleTypes.FLAME,
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
            level.sendParticles(ParticleTypes.FLAME,
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
            level.sendParticles(ParticleTypes.FLAME,
                    x + Math.cos(angle) * rr, y + progress * height, z + Math.sin(angle) * rr,
                    2, 0.05, 0.05, 0.05, 0.02);
        }
    }
}
