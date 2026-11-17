package nolan.bloxpiece.fx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModParticles;

/**
 * Effets de particules du fruit de la Glace (toutes personnalisées, voir ModParticles) : éclats de cristal, flocons de neige qui
 * tourbillonnent, brume glacée, lueur de givre. Tout est envoyé par le serveur à tous les joueurs proches (limite de distance
 * levée pour les grands effets). Aucun dégât ici.
 */
public final class IceEffects {

    private IceEffects() {
    }

    private static void send(ServerLevel level, ParticleOptions particle, Vec3 p, int count, double dx, double dy, double dz, double speed) {
        level.sendParticles(particle, true, true, p.x, p.y, p.z, count, dx, dy, dz, speed);
    }

    /** Givre qui éclate sur une cible touchée. */
    public static void frostBurst(ServerLevel level, Vec3 center, double width) {
        send(level, ModParticles.ICE_SHARD, center, 10, width * 0.4, width * 0.6, width * 0.4, 0.12);
        send(level, ModParticles.FROST_MIST, center, 4, width * 0.3, width * 0.4, width * 0.3, 0.02);
        send(level, ModParticles.SNOWFLAKE, center, 6, width * 0.5, width * 0.6, width * 0.5, 0.03);
    }

    /** Glace qui se brise : pic ou statue qui éclate en éclats et poussière de givre. */
    public static void shatter(ServerLevel level, Vec3 center, float size) {
        send(level, ModParticles.ICE_SHARD, center, (int) (14 * size), 0.4 * size, 0.7 * size, 0.4 * size, 0.18);
        send(level, ModParticles.FROST_MIST, center, (int) (5 * size), 0.4 * size, 0.5 * size, 0.4 * size, 0.02);
        send(level, ModParticles.SNOWFLAKE, center, (int) (6 * size), 0.5 * size, 0.6 * size, 0.5 * size, 0.04);
    }

    /** Aura du trident de glace tenu en main : cristaux et brume froide autour de la main. */
    public static void saberAura(ServerLevel level, Vec3 hand, int age) {
        send(level, ModParticles.ICE_SHARD, hand, 1, 0.25, 0.5, 0.25, 0.01);
        if (age % 2 == 0) {
            send(level, ModParticles.FROST_MIST, hand.add(0.0, -0.3, 0.0), 1, 0.2, 0.2, 0.2, 0.005);
        }
        if (age % 3 == 0) {
            send(level, ModParticles.SNOWFLAKE, hand, 1, 0.4, 0.6, 0.4, 0.01);
        }
    }

    /** Apparition du trident dans la main : éclats et givre qui se rassemblent. */
    public static void saberForm(ServerLevel level, Vec3 hand) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 24; i++) {
            Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (d.lengthSqr() < 1.0E-4) {
                continue;
            }
            d = d.normalize();
            send(level, i % 3 == 0 ? ModParticles.SNOWFLAKE : ModParticles.ICE_SHARD, hand.add(d.scale(1.6)), 0, -d.x, -d.y, -d.z, 0.16);
        }
        send(level, ModParticles.FROST_GLOW, hand, 6, 0.2, 0.4, 0.2, 0.0);
    }

    /** Traînée du trident lancé, le long du trajet du tick : neige, éclats et lueur de givre. */
    public static void tridentTrail(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 path = to.subtract(from);
        int samples = Math.max(1, (int) Math.ceil(path.length() / 0.8));
        for (int i = 0; i < samples; i++) {
            Vec3 p = from.add(path.scale((double) i / samples));
            send(level, ModParticles.SNOWFLAKE, p, 1, 0.15, 0.15, 0.15, 0.02);
            send(level, ModParticles.FROST_GLOW, p, 1, 0.05, 0.05, 0.05, 0.0);
            if (i % 2 == 0) {
                send(level, ModParticles.ICE_SHARD, p, 1, 0.1, 0.1, 0.1, 0.03);
            }
        }
    }

    /** Impact du trident : explosion de glace, brume et anneau d'éclats au sol. */
    public static void tridentImpact(ServerLevel level, Vec3 point, double radius) {
        send(level, ModParticles.FROST_GLOW, point, 12, 0.4, 0.4, 0.4, 0.0);
        send(level, ModParticles.ICE_SHARD, point, 60, radius * 0.25, radius * 0.2, radius * 0.25, 0.35);
        send(level, ModParticles.SNOWFLAKE, point, 40, radius * 0.4, radius * 0.3, radius * 0.4, 0.06);
        send(level, ModParticles.FROST_MIST, point, 20, radius * 0.3, 0.3, radius * 0.3, 0.03);
        for (int i = 0; i < 36; i++) {
            double a = 2.0 * Math.PI * i / 36;
            send(level, ModParticles.FROST_MIST, point.add(0.0, 0.2, 0.0), 0, Math.cos(a), 0.02, Math.sin(a), 0.35);
        }
    }

    /** Apparition de l'aigle de glace : cristaux qui se rassemblent devant le joueur. */
    public static void eagleForm(ServerLevel level, Vec3 center) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 60; i++) {
            Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5, random.nextGaussian());
            if (d.lengthSqr() < 1.0E-4) {
                continue;
            }
            d = d.normalize();
            double r = 2.5 + random.nextDouble() * 2.5;
            send(level, i % 3 == 0 ? ModParticles.SNOWFLAKE : ModParticles.ICE_SHARD, center.add(d.scale(r)), 0, -d.x, -d.y, -d.z, 0.12 * r);
        }
        send(level, ModParticles.FROST_GLOW, center, 20, 1.2, 0.5, 1.2, 0.0);
        send(level, ModParticles.FROST_MIST, center, 12, 1.5, 0.5, 1.5, 0.02);
    }

    /**
     * Lancement de l'aigle : explosion de givre, onde d'air glacé (deux anneaux de brume et de flocons qui s'écartent autour de
     * l'aigle, perpendiculaires à son vol) et gerbe d'éclats projetée vers l'avant.
     */
    public static void eagleLaunch(ServerLevel level, Vec3 at, Vec3 dir) {
        send(level, ModParticles.FROST_GLOW, at, 30, 1.0, 0.6, 1.0, 0.0);
        send(level, ModParticles.ICE_SHARD, at, 90, 1.2, 0.8, 1.2, 0.45);
        send(level, ModParticles.SNOWFLAKE, at, 70, 1.5, 1.0, 1.5, 0.12);
        Vec3 ref = Math.abs(dir.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 u = dir.cross(ref).normalize();
        Vec3 v = dir.cross(u).normalize();
        for (int ring = 0; ring < 2; ring++) {
            Vec3 c = at.subtract(dir.scale(ring * 1.5));
            for (int i = 0; i < 56; i++) {
                double a = 2.0 * Math.PI * i / 56;
                Vec3 out = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                send(level, ModParticles.FROST_MIST, c.add(out.scale(1.5)), 0, out.x, out.y, out.z, 0.55 - ring * 0.15);
                if (i % 2 == 0) {
                    send(level, ModParticles.SNOWFLAKE, c.add(out.scale(1.2)), 0, out.x, out.y, out.z, 0.8 - ring * 0.2);
                }
            }
        }
        for (int i = 0; i < 40; i++) {
            send(level, ModParticles.ICE_SHARD, at, 0, dir.x + (level.getRandom().nextDouble() - 0.5) * 0.6,
                    dir.y + (level.getRandom().nextDouble() - 0.5) * 0.6, dir.z + (level.getRandom().nextDouble() - 0.5) * 0.6, 1.2);
        }
    }

    /** Traînée de l'aigle : large sillage de givre et flocons qui tourbillonnent, laissés sur place derrière lui. */
    public static void eagleTrail(ServerLevel level, Vec3 from, Vec3 to, Vec3 dir, double span, int age) {
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        Vec3 path = to.subtract(from);
        int samples = Math.max(1, (int) Math.ceil(path.length() / 0.5));
        RandomSource random = level.getRandom();
        for (int i = 0; i < samples; i++) {
            Vec3 p = from.add(path.scale((double) i / samples));
            send(level, ModParticles.FROST_GLOW, p, 1, 0.3, 0.2, 0.3, 0.0);
            for (int k = 0; k < 7; k++) {
                double offset = (random.nextDouble() * 2.0 - 1.0) * span;
                Vec3 q = p.add(side.scale(offset)).add(0.0, (random.nextDouble() - 0.5) * 0.8, 0.0);
                // tourbillon : vitesse tangentielle autour de l'axe du vol
                double a = age * 0.7 + offset;
                send(level, ModParticles.SNOWFLAKE, q, 0, side.x * Math.cos(a), Math.sin(a), side.z * Math.cos(a), 0.08);
            }
            send(level, ModParticles.FROST_MIST, p.add(side.scale((random.nextDouble() * 2.0 - 1.0) * span * 0.6)), 2, 0.3, 0.2, 0.3, 0.01);
            send(level, ModParticles.ICE_SHARD, p, 2, span * 0.3, 0.3, span * 0.3, 0.04);
            // bouts des ailes : deux filets de givre bien nets
            send(level, ModParticles.SNOWFLAKE, p.add(side.scale(span)), 1, 0.05, 0.05, 0.05, 0.0);
            send(level, ModParticles.SNOWFLAKE, p.subtract(side.scale(span)), 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /** Charge de l'Ère glaciaire : le froid converge vers la main posée au sol, brume qui rampe au sol. */
    public static void iceAgeCharge(ServerLevel level, Vec3 hand, double progress) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 10 + (int) (progress * 20); i++) {
            double a = random.nextDouble() * 2.0 * Math.PI;
            double r = 3.0 + random.nextDouble() * 6.0;
            Vec3 p = hand.add(Math.cos(a) * r, 0.2 + random.nextDouble() * 1.5, Math.sin(a) * r);
            Vec3 d = hand.subtract(p).normalize();
            send(level, i % 2 == 0 ? ModParticles.SNOWFLAKE : ModParticles.ICE_SHARD, p, 0, d.x, d.y, d.z, 0.08 * r);
        }
        send(level, ModParticles.FROST_GLOW, hand, 2 + (int) (progress * 6), 0.3, 0.1, 0.3, 0.0);
        send(level, ModParticles.FROST_MIST, hand, 3, 1.5 * progress, 0.1, 1.5 * progress, 0.02);
    }

    /** Front de l'Ère glaciaire au rayon {@code r} : mur de blizzard, brouillard glacé dense qui roule vers l'extérieur. */
    /**
     * Charge d'Ice Age : cercle de givre au sol qui montre en temps réel la portée qu'aura la vague (il s'élargit avec la charge),
     * et un second cercle intérieur qui tourne en sens inverse.
     */
    public static void iceAgeChargeRing(ServerLevel level, Vec3 center, double radius, int age) {
        int points = Math.min(160, 32 + (int) (radius * 2));
        for (int i = 0; i < points; i++) {
            double a = 2.0 * Math.PI * i / points + age * 0.03;
            send(level, i % 4 == 0 ? ModParticles.SNOWFLAKE : ModParticles.FROST_GLOW,
                    center.add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius), 1, 0.05, 0.05, 0.05, 0.0);
        }
        double inner = radius * 0.5;
        for (int i = 0; i < points / 2; i++) {
            double a = 2.0 * Math.PI * i / (points / 2) - age * 0.05;
            send(level, ModParticles.FROST_MIST, center.add(Math.cos(a) * inner, 0.15, Math.sin(a) * inner), 1, 0.1, 0.05, 0.1, 0.0);
        }
    }

    /** Front de la vague d'Ice Age ; {@code ground} donne la hauteur du sol en (x, z) : le blizzard épouse le relief. */
    public static void iceAgeFront(ServerLevel level, Vec3 center, double r, int age, java.util.function.DoubleBinaryOperator ground) {
        int points = Math.min(220, 24 + (int) (r * 7));
        RandomSource random = level.getRandom();
        for (int i = 0; i < points; i++) {
            double a = 2.0 * Math.PI * i / points + age * 0.02;
            double cos = Math.cos(a), sin = Math.sin(a);
            double px = center.x + cos * r, pz = center.z + sin * r;
            Vec3 base = new Vec3(px, ground.applyAsDouble(px, pz), pz);
            Vec3 p = base.add(0.0, 0.3 + random.nextDouble() * 2.5, 0.0);
            send(level, ModParticles.SNOWFLAKE, p, 0, cos + (random.nextDouble() - 0.5), 0.1, sin + (random.nextDouble() - 0.5), 0.3);
            if (i % 2 == 0) {
                send(level, ModParticles.FROST_MIST, base.add(0.0, 0.4, 0.0), 0, cos, 0.02, sin, 0.25);
            }
            if (i % 5 == 0) {
                send(level, ModParticles.ICE_SHARD, base.add(0.0, 0.6, 0.0), 0, cos, 0.6, sin, 0.25);
            }
        }
        // brouillard glacé qui reste à l'intérieur du cercle
        send(level, ModParticles.FROST_MIST, center, 12, r * 0.5, 0.6, r * 0.5, 0.01);
        send(level, ModParticles.SNOWFLAKE, center.add(0.0, 4.0, 0.0), 16, r * 0.5, 2.0, r * 0.5, 0.02);
    }
}
