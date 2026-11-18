package nolan.bloxpiece.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModParticles;

/**
 * Effets visuels dorés de la Paume du Bouddha (deuxième attaque du fruit du Bouddha) : charge (sceau dans le ciel), traînée et
 * sceau au sol pendant la chute, impact (débris, poussière, onde de choc, auréole et colonne de lumière). Même principe que FireEffects pour les attaques de feu : tout est calculé côté serveur et les particules sont envoyées
 * à tous les joueurs proches (synchronisation multijoueur), avec la limite de distance levée pour que les grands cercles restent
 * visibles en entier. Aucun dégât ici : ils sont appliqués une seule fois par BuddhaPalm.
 *
 * Toutes les particules sont celles du mod (ModParticles / ModParticle) : lueurs or, jaune pâle et orange, étoiles
 * scintillantes, pétales de lotus, éclair à rayons, poussière dorée. Seuls les débris de blocs restent vanilla (ils prennent la
 * texture du sol). Le cercle au sol et l'onde de choc au sol sont le modèle 3D du sceau (BuddhaSeal), pas des particules.
 */
public class BuddhaEffects {

    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    /** Durée de l'onde de choc d'impact (ticks). */
    private static final int SHOCK_TICKS = 16;
    /** Hauteur finale de la colonne de lumière qui jaillit de l'impact (blocs). */
    private static final double PILLAR_HEIGHT = 32.0;

    /** Onde de choc dorée en cours (état serveur), dessinée tick après tick. */
    private static final class Shock {
        final ServerLevel level;
        final Vec3 center;
        final Vec3 heading;
        final double radius;
        int age;

        Shock(ServerLevel level, Vec3 center, Vec3 heading, double radius) {
            this.level = level;
            this.center = center;
            this.heading = heading;
            this.radius = radius;
        }
    }

    private static final List<Shock> SHOCKS = new ArrayList<>();

    /** Sphère de la Déflagration en cours d'expansion (état serveur). */
    private static final class Nova {
        final ServerLevel level;
        final Vec3 center;
        final double radius;
        int age;

        Nova(ServerLevel level, Vec3 center, double radius) {
            this.level = level;
            this.center = center;
            this.radius = radius;
        }
    }

    /** Durée de l'expansion de la Déflagration (ticks). */
    private static final int NOVA_TICKS = 14;
    private static final List<Nova> NOVAS = new ArrayList<>();

    /** À appeler à chaque tick serveur (voir ModEvents). */
    public static void tick() {
        Iterator<Nova> novas = NOVAS.iterator();
        while (novas.hasNext()) {
            Nova nova = novas.next();
            nova.age++;
            drawNova(nova);
            if (nova.age >= NOVA_TICKS) {
                novas.remove();
            }
        }
        if (SHOCKS.isEmpty()) {
            return;
        }
        Iterator<Shock> it = SHOCKS.iterator();
        while (it.hasNext()) {
            Shock shock = it.next();
            shock.age++;
            drawShock(shock);
            if (shock.age >= SHOCK_TICKS) {
                it.remove();
            }
        }
    }

    public static void clear() {
        SHOCKS.clear();
        NOVAS.clear();
    }

    // ---------------------------------------------------------------- charge

    /**
     * Pendant la charge ({@code progress} de 0 à 1) :
     * <ul>
     *   <li>un faisceau de lumière monte de la main levée du joueur jusqu'au ciel (visible à la 1re personne : il part au-dessus
     *       de la caméra et file vers le haut de l'écran, et à la 3e personne) ;</li>
     *   <li>dans le ciel, au-dessus de la cible, un sceau doré se dessine et grandit (cercles qui tournent, rayons), face à la
     *       direction de la chute, et l'énergie converge vers lui : c'est de là que la main va jaillir ;</li>
     *   <li>une auréole s'allume derrière le joueur (visible des autres joueurs et en 3e personne, jamais devant la caméra).</li>
     * </ul>
     */
    public static void charge(ServerLevel level, Vec3 hand, Vec3 sky, Vec3 fall, Vec3 back, Vec3 look,
                              double size, double progress, int age) {
        RandomSource random = level.getRandom();

        // Faisceau main -> ciel (une particule tous les ~1,5 bloc, qui monte doucement)
        if (age % 2 == 0) {
            Vec3 beam = sky.subtract(hand);
            double length = beam.length();
            Vec3 step = beam.normalize();
            double offset = (age * 0.37) % 1.5;
            for (double d = offset; d < length * progress + 1.0 && d < length; d += 1.5) {
                Vec3 p = hand.add(step.scale(d));
                send(level, d % 3.0 < 1.5 ? ModParticles.BUDDHA_SPARK : ModParticles.BUDDHA_GLOW_PALE, p, 1, 0.08, 0.08, 0.08, 0.01);
            }
        }

        // Sceau dans le ciel : grandit avec la charge et tourne
        double sealR = 1.0 + 6.0 * progress;
        circle(level, ModParticles.BUDDHA_GLOW, sky, fall, sealR, 40, age * 0.2);
        circle(level, ModParticles.BUDDHA_GLOW_ORANGE, sky, fall, sealR * 0.6, 24, -age * 0.3);
        if (age % 2 == 0) {
            spokes(level, ModParticles.BUDDHA_GLOW_PALE, sky, fall, sealR * 0.6, sealR * 1.1, 8, age * 0.2);
        }

        // Énergie qui converge vers le sceau (count = 0 : les "offsets" sont la direction)
        int gather = 8 + (int) (progress * 14);
        for (int i = 0; i < gather; i++) {
            Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (d.lengthSqr() < 1.0E-4) {
                continue;
            }
            d = d.normalize();
            double r = 5.0 + random.nextDouble() * 5.0;
            send(level, i % 3 == 0 ? ModParticles.BUDDHA_PETAL : ModParticles.BUDDHA_SPARK, sky.add(d.scale(r)), 0, -d.x, -d.y, -d.z, 0.1 * r);
        }
        send(level, ModParticles.BUDDHA_GLOW_PALE, sky, 3 + (int) (progress * 8),
                0.6 * progress, 0.6 * progress, 0.6 * progress, 0.0);

        // Auréole derrière le joueur, perpendiculaire au regard
        double haloRadius = (0.8 + 1.6 * progress) * size;
        circle(level, ModParticles.BUDDHA_GLOW, back, look, haloRadius, 28, age * 0.25);
        // Étincelles à la main levée
        if (age % 3 == 0) {
            send(level, ModParticles.BUDDHA_SPARK, hand, 3, 0.3 * size, 0.3 * size, 0.3 * size, 0.1);
        }
    }

    /** Apparition de la main dans le ciel : éclair doré, explosion de lumière au sceau, et souffle au sol autour du joueur. */
    public static void launch(ServerLevel level, Vec3 sky, Vec3 feet, Vec3 fall) {
        send(level, ModParticles.BUDDHA_FLASH, sky, 1, 0.0, 0.0, 0.0, 0.0);
        sphere(level, ModParticles.BUDDHA_SPARK, sky, 90, 0.8);
        sphere(level, ModParticles.BUDDHA_PETAL, sky, 50, 0.6);
        circle(level, ModParticles.BUDDHA_GLOW, sky, fall, 7.5, 64, 0.0);
        circle(level, ModParticles.BUDDHA_SPARK, sky, fall, 9.0, 40, 0.0);
        // Souffle au sol autour du joueur (à hauteur des pieds : ne gêne jamais la vue à la 1re personne)
        for (int i = 0; i < 40; i++) {
            double a = 2.0 * Math.PI * i / 40;
            send(level, ModParticles.BUDDHA_SPARK, new Vec3(feet.x, feet.y + 0.15, feet.z), 0, Math.cos(a), 0.02, Math.sin(a), 0.6);
        }
        level.playSound(null, sky.x, sky.y, sky.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0F, 0.6F);
    }

    // ---------------------------------------------------------------- chute

    /**
     * Traînée de la main pendant la chute, émise le long du trajet parcouru ce tick ({@code from} -> {@code to}). Ce sont des
     * particules laissées DANS LE MONDE : elles restent sur place, dérivent et s'éteignent derrière la main au lieu de la suivre,
     * ce qui donne une traînée naturelle (rien n'est attaché au modèle). Tous les 3 ticks, un anneau de souffle se détache et
     * s'élargit lentement là où la main est passée.
     */
    public static void palmTrail(ServerLevel level, Vec3 from, Vec3 to, Vec3 forward, int age) {
        RandomSource random = level.getRandom();
        Vec3 path = to.subtract(from);
        int samples = Math.max(1, (int) Math.ceil(path.length() / 1.2));
        for (int i = 0; i < samples; i++) {
            Vec3 p = from.add(path.scale((i + random.nextDouble()) / samples));
            send(level, ModParticles.BUDDHA_GLOW, p, 2, 1.3, 1.3, 1.3, 0.0);
            send(level, ModParticles.BUDDHA_SPARK, p, 1, 1.6, 1.6, 1.6, 0.015);
            if ((i + age) % 2 == 0) {
                send(level, ModParticles.BUDDHA_GLOW_ORANGE, p, 1, 1.8, 1.8, 1.8, 0.0);
                send(level, ModParticles.BUDDHA_PETAL, p, 1, 2.0, 2.0, 2.0, 0.0);
            }
        }
        if (age % 3 == 0) {
            Vec3 ringCenter = to.subtract(forward.scale(2.0));
            Vec3[] b = basis(forward);
            int points = 22;
            for (int i = 0; i < points; i++) {
                double a = 2.0 * Math.PI * i / points + age * 0.4;
                Vec3 dir = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
                send(level, ModParticles.BUDDHA_SPARK, ringCenter.add(dir.scale(2.6)), 0, dir.x, dir.y, dir.z, 0.08);
            }
        }
        if (age % 4 == 0) {
            send(level, ModParticles.BUDDHA_SPARK, to, 3, 1.5, 1.5, 1.5, 0.1);
        }
    }

    /**
     * Pendant la chute : juste avant l'impact, le souffle de la main plaque la poussière dorée au sol et la chasse vers l'extérieur.
     * (Le cercle au sol n'est plus fait de particules : c'est le modèle 3D du sceau, voir BuddhaSeal.)
     */
    public static void descent(ServerLevel level, Vec3 palm, Vec3 landing, int age) {
        double distance = palm.distanceTo(landing);
        Vec3 ground = landing.add(0.0, 0.3, 0.0);

        // Souffle d'air juste avant l'impact : la poussière part en anneau depuis le point d'impact
        if (distance < 12.0) {
            double force = 1.0 - distance / 12.0;
            int points = 28;
            for (int i = 0; i < points; i++) {
                double a = 2.0 * Math.PI * i / points + age;
                send(level, ModParticles.BUDDHA_MIST, ground, 0, Math.cos(a), 0.05, Math.sin(a), 0.25 + 0.6 * force);
            }
        }
    }

    // ---------------------------------------------------------------- impact

    /** Impact : éclair de lumière, explosion de lumière dorée, puis onde de choc qui s'élargit (voir drawShock). */
    public static void palmImpact(ServerLevel level, Vec3 point, Vec3 heading, double radius) {
        // Éclairs de lumière (le centre + quelques-uns autour pour l'ampleur)
        send(level, ModParticles.BUDDHA_FLASH, point, 1, 0.0, 0.0, 0.0, 0.0);
        send(level, ModParticles.BUDDHA_FLASH, point, 4, radius * 0.3, radius * 0.2, radius * 0.3, 0.0);

        // Explosion de lumière dorée dans toutes les directions
        sphere(level, ModParticles.BUDDHA_SPARK, point, 180, 1.3);
        sphere(level, ModParticles.BUDDHA_PETAL, point, 110, 0.9);
        sphere(level, ModParticles.BUDDHA_SPARK, point, 70, 1.6);
        send(level, ModParticles.BUDDHA_GLOW, point, 160, radius * 0.35, radius * 0.3, radius * 0.35, 0.0);
        send(level, ModParticles.BUDDHA_GLOW_ORANGE, point, 90, radius * 0.45, radius * 0.25, radius * 0.45, 0.0);
        send(level, ModParticles.BUDDHA_GLOW_PALE, point, 50, radius * 0.2, radius * 0.2, radius * 0.2, 0.0);

        // Destruction : débris du sol projetés en l'air, nuage de poussière au ras du sol, fumée qui monte
        BlockState ground = groundBlock(level, point);
        if (ground != null) {
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            send(level, debris, point, 260, radius * 0.45, 0.6, radius * 0.45, 0.4);
            RandomSource random = level.getRandom();
            for (int i = 0; i < 90; i++) {
                double a = random.nextDouble() * 2.0 * Math.PI;
                double spread = 0.3 + random.nextDouble() * 0.7;
                send(level, debris, point, 0, Math.cos(a) * spread, 0.8 + random.nextDouble() * 0.8, Math.sin(a) * spread,
                        0.9 + random.nextDouble() * 0.6);
            }
        }
        for (int i = 0; i < 72; i++) {
            double a = 2.0 * Math.PI * i / 72;
            send(level, ModParticles.BUDDHA_MIST, new Vec3(point.x, point.y + 0.3, point.z), 0, Math.cos(a), 0.04, Math.sin(a), 1.1);
            if (i % 2 == 0) {
                send(level, ModParticles.BUDDHA_MIST, new Vec3(point.x, point.y + 0.8, point.z), 0, Math.cos(a), 0.2, Math.sin(a), 0.6);
            }
        }
        send(level, ModParticles.BUDDHA_MIST, point, 30, radius * 0.4, 0.8, radius * 0.4, 0.03);

        level.playSound(null, point.x, point.y, point.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 4.0F, 0.5F);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4.0F, 0.5F);

        Vec3 flat = new Vec3(heading.x, 0.0, heading.z);
        SHOCKS.add(new Shock(level, point, flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize(), radius));
    }

    /**
     * Une étape de l'onde de choc : dôme de lumière,
     * gigantesque auréole dressée face à la direction du coup, et colonne de lumière qui monte vers le ciel.
     */
    private static void drawShock(Shock s) {
        ServerLevel level = s.level;
        Vec3 c = s.center;
        double progress = (double) s.age / SHOCK_TICKS;
        double eased = 1.0 - (1.0 - progress) * (1.0 - progress); // part vite, ralentit à la fin

        // (L'anneau au sol n'est plus fait de particules : le sceau BuddhaSeal s'élargit lui-même en onde de choc.)

        // Dôme de lumière (pendant la première moitié)
        if (progress <= 0.55) {
            double domeR = s.radius * 1.1 * Math.min(1.0, eased * 1.6);
            int dome = 60;
            for (int i = 0; i < dome; i++) {
                double dy = (i + 0.5) / dome;
                double ring = Math.sqrt(1.0 - dy * dy);
                double a = i * GOLDEN_ANGLE + s.age * 0.2;
                send(level, i % 2 == 0 ? ModParticles.BUDDHA_SPARK : ModParticles.BUDDHA_GLOW_PALE,
                        new Vec3(c.x + Math.cos(a) * ring * domeR, c.y + dy * domeR * 0.8, c.z + Math.sin(a) * ring * domeR),
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        // Auréole géante dressée face au coup : le "sceau" du Bouddha qui s'imprime au-dessus de l'impact
        if (s.age <= 12) {
            double haloR = s.radius * (0.5 + 0.7 * eased);
            Vec3 haloCenter = c.add(0.0, haloR * 0.9, 0.0);
            circle(level, ModParticles.BUDDHA_GLOW, haloCenter, s.heading, haloR, 64, s.age * 0.1);
            circle(level, ModParticles.BUDDHA_SPARK, haloCenter, s.heading, haloR * 0.75, 40, -s.age * 0.15);
            // Rayons de l'auréole (tous les 2 ticks)
            if (s.age % 2 == 0) {
                Vec3[] b = basis(s.heading);
                for (int k = 0; k < 16; k++) {
                    double a = 2.0 * Math.PI * k / 16 + s.age * 0.1;
                    Vec3 dir = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
                    for (double t = 1.05; t <= 1.35; t += 0.1) {
                        send(level, ModParticles.BUDDHA_GLOW_PALE, haloCenter.add(dir.scale(haloR * t)), 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
        }

        // Colonne de lumière qui monte
        if (s.age <= 10) {
            double top = PILLAR_HEIGHT * Math.min(1.0, s.age / 8.0);
            for (double h = 0.0; h <= top; h += 0.8) {
                send(level, h % 2.4 < 0.8 ? ModParticles.BUDDHA_GLOW_PALE : ModParticles.BUDDHA_SPARK,
                        new Vec3(c.x, c.y + h, c.z), 2, 0.35, 0.2, 0.35, 0.02);
            }
        }

        // Second choc, plus discret, à mi-parcours (l'impact "résonne")
        if (s.age == SHOCK_TICKS / 2) {
            sphere(level, ModParticles.BUDDHA_SPARK, c, 90, 0.9);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.5F, 0.8F);
        }
    }

    // ---------------------------------------------------------------- Onde de choc (attaque 3)

    /** Le dôme d'énergie qui éclate instantanément devant la paume : éclair, sphère de lumière, pétales. */
    public static void shockwaveBurst(ServerLevel level, Vec3 palm, Vec3 dir) {
        send(level, ModParticles.BUDDHA_FLASH, palm, 1, 0.0, 0.0, 0.0, 0.0);
        sphere(level, ModParticles.BUDDHA_SPARK, palm, 70, 0.5);
        send(level, ModParticles.BUDDHA_GLOW_PALE, palm, 40, 0.6, 0.6, 0.6, 0.0);
        send(level, ModParticles.BUDDHA_PETAL, palm, 16, 0.8, 0.8, 0.8, 0.05);
        circle(level, ModParticles.BUDDHA_GLOW, palm, dir, 1.6, 28, 0.0);
    }

    /**
     * Le front de l'onde à la distance {@code front} : un anneau d'énergie perpendiculaire à l'onde (rayon du cône), des
     * anneaux de distorsion d'air (poussière dorée qui s'écarte), et le sol qui se soulève en gerbes de débris sous le front
     * (particules de blocs : le terrain n'est pas détruit).
     */
    public static void shockwaveFront(ServerLevel level, Vec3 origin, Vec3 dir, double front, double radius, int age) {
        Vec3 c = origin.add(dir.scale(front));
        Vec3[] b = basis(dir);
        int points = Math.min(140, Math.max(20, (int) (radius * 9)));
        for (int i = 0; i < points; i++) {
            double a = 2.0 * Math.PI * i / points + age * 0.35;
            Vec3 out = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
            Vec3 p = c.add(out.scale(radius));
            send(level, i % 2 == 0 ? ModParticles.BUDDHA_GLOW : ModParticles.BUDDHA_GLOW_ORANGE, p, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 3 == 0) {
                send(level, ModParticles.BUDDHA_SPARK, p, 0, out.x + dir.x, out.y + dir.y, out.z + dir.z, 0.25);
            }
            if (i % 4 == 0) {
                // distorsion d'air : la poussière dorée est chassée vers l'extérieur
                send(level, ModParticles.BUDDHA_MIST, c.add(out.scale(radius * 0.7)), 0, out.x, out.y, out.z, 0.18);
            }
        }
        send(level, ModParticles.BUDDHA_GLOW_PALE, c, 6, radius * 0.4, radius * 0.4, radius * 0.4, 0.0);

        // Le sol se soulève sous le front : gerbes de débris sur toute la largeur du cône
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        RandomSource random = level.getRandom();
        for (int k = -6; k <= 6; k++) {
            Vec3 probe = c.add(side.scale(radius * k / 6.0));
            BlockHitResult hit = level.clip(new ClipContext(probe.add(0.0, 1.0, 0.0), probe.add(0.0, -radius - 3.0, 0.0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 g = hit.getLocation();
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (state.isAir()) {
                continue;
            }
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, state);
            for (int j = 0; j < 5; j++) {
                send(level, debris, g.add(0.0, 0.2, 0.0), 0, (random.nextDouble() - 0.5) * 0.5 + dir.x * 0.4,
                        1.0, (random.nextDouble() - 0.5) * 0.5 + dir.z * 0.4, 0.5 + random.nextDouble() * 0.4);
            }
            send(level, debris, g.add(0.0, 0.1, 0.0), 6, 0.5, 0.05, 0.5, 0.1); // fissure : éclats au ras du sol
            send(level, ModParticles.BUDDHA_MIST, g.add(0.0, 0.3, 0.0), 1, 0.4, 0.1, 0.4, 0.02);
        }
    }

    /** Incantation de l'Onde de choc : l'énergie converge vers la paume, une lueur grossit, l'anneau de la paume se forme. */
    public static void shockwaveWindup(ServerLevel level, Vec3 palm, double progress, int age) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 6 + (int) (progress * 10); i++) {
            Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (d.lengthSqr() < 1.0E-4) {
                continue;
            }
            d = d.normalize();
            double r = 2.0 + random.nextDouble() * 2.5;
            send(level, i % 4 == 0 ? ModParticles.BUDDHA_PETAL : ModParticles.BUDDHA_SPARK, palm.add(d.scale(r)), 0, -d.x, -d.y, -d.z, 0.11 * r);
        }
        send(level, ModParticles.BUDDHA_GLOW_PALE, palm, 1 + (int) (progress * 5), 0.15 * progress, 0.15 * progress, 0.15 * progress, 0.0);
        if (age % 3 == 0) {
            circle(level, ModParticles.BUDDHA_GLOW, palm, new Vec3(0.0, 1.0, 0.0), 0.6 + progress, 16, age * 0.5);
        }
    }

    /**
     * Après la propagation : l'onde reste suspendue dans les airs (anneaux d'énergie le long du cône) et se dissipe
     * progressivement ; {@code t} va de 0 (juste après la propagation) à 1 (disparue). Les anneaux s'élargissent un peu et
     * s'éclaircissent, la poussière dorée dérive doucement.
     */
    public static void shockwaveLinger(ServerLevel level, Vec3 origin, Vec3 dir, double range, double r0, double r1, double t, int age) {
        if (age % 2 != 0) {
            return;
        }
        double fade = 1.0 - t;
        Vec3[] b = basis(dir);
        for (int k = 1; k <= 5; k++) {
            double along = range * k / 5.0;
            double radius = (r0 + (r1 - r0) * along / range) * (1.0 + 0.15 * t);
            Vec3 c = origin.add(dir.scale(along + t * 1.5)); // l'onde dérive encore un peu vers l'avant
            int points = (int) (radius * 3.5 * fade);
            for (int i = 0; i < points; i++) {
                double a = 2.0 * Math.PI * i / Math.max(1, points) + age * 0.05 + k;
                Vec3 out = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
                send(level, (i + k) % 3 == 0 ? ModParticles.BUDDHA_GLOW_ORANGE : ModParticles.BUDDHA_GLOW, c.add(out.scale(radius)),
                        1, 0.0, 0.0, 0.0, 0.0);
                if (i % 6 == 0) {
                    send(level, ModParticles.BUDDHA_MIST, c.add(out.scale(radius * 0.85)), 0, out.x + dir.x, out.y + dir.y, out.z + dir.z, 0.03);
                }
            }
        }
        // Le dôme devant la paume s'éteint doucement
        send(level, ModParticles.BUDDHA_GLOW_PALE, origin, (int) (6 * fade), r0 * 0.5, r0 * 0.5, r0 * 0.5, 0.0);
    }

    /**
     * Empreinte d'une PAUME GÉANTE dorée dans le plan perpendiculaire à {@code dir}, centrée en {@code c} : paume (anneau +
     * rayons), quatre doigts dressés et le pouce, à l'échelle {@code scale} (1 = environ 3 blocs de haut).
     */
    public static void palmImprint(ServerLevel level, Vec3 c, Vec3 dir, double scale, ParticleOptions particle) {
        Vec3[] b = basis(dir);
        Vec3 side = b[0];
        Vec3 up = b[1].y < 0 ? b[1].scale(-1.0) : b[1];
        if (Math.abs(dir.y) > 0.9) { // regard vers le haut / le bas : la main reste lisible
            up = new Vec3(0.0, 0.0, 1.0);
            side = dir.cross(up).normalize();
        }
        double step = 0.3;
        // paume : anneau et rayons
        double palmR = 1.0 * scale;
        int ring = (int) (palmR * 2 * Math.PI / step);
        for (int i = 0; i < ring; i++) {
            double a = 2.0 * Math.PI * i / ring;
            send(level, particle, c.add(side.scale(Math.cos(a) * palmR)).add(up.scale(Math.sin(a) * palmR * 0.9)), 1, 0, 0, 0, 0);
        }
        for (int k = 0; k < 6; k++) {
            double a = Math.PI * 2 * k / 6;
            for (double t = 0.2; t < palmR; t += step) {
                send(level, ModParticles.BUDDHA_GLOW_PALE, c.add(side.scale(Math.cos(a) * t)).add(up.scale(Math.sin(a) * t * 0.9)), 1, 0, 0, 0, 0);
            }
        }
        // doigts
        double[] xs = {-0.72, -0.24, 0.24, 0.72};
        double[] len = {1.25, 1.6, 1.5, 1.1};
        for (int f = 0; f < 4; f++) {
            Vec3 base = c.add(side.scale(xs[f] * scale)).add(up.scale(0.75 * scale));
            for (double t = 0; t <= len[f] * scale; t += step) {
                Vec3 p = base.add(up.scale(t));
                send(level, particle, p.add(side.scale(-0.16 * scale)), 1, 0, 0, 0, 0);
                send(level, particle, p.add(side.scale(0.16 * scale)), 1, 0, 0, 0, 0);
            }
            send(level, ModParticles.BUDDHA_SPARK, base.add(up.scale(len[f] * scale)), 2, 0.05, 0.05, 0.05, 0.02);
        }
        // pouce, en diagonale sur le côté
        Vec3 thumbDir = side.scale(-0.75).add(up.scale(0.65)).normalize();
        Vec3 thumbBase = c.add(side.scale(-0.9 * scale)).add(up.scale(-0.1 * scale));
        for (double t = 0; t <= 1.0 * scale; t += step) {
            send(level, particle, thumbBase.add(thumbDir.scale(t)), 1, 0, 0, 0, 0);
        }
    }

    /** Éclairs dorés : lignes brisées qui partent de {@code from} dans le cône de l'onde. */
    public static void goldenBolts(ServerLevel level, Vec3 from, Vec3 dir, double length, int count) {
        RandomSource random = level.getRandom();
        Vec3[] b = basis(dir);
        for (int k = 0; k < count; k++) {
            Vec3 p = from;
            Vec3 heading = dir.add(b[0].scale((random.nextDouble() - 0.5) * 0.9)).add(b[1].scale((random.nextDouble() - 0.5) * 0.9)).normalize();
            double travelled = 0;
            while (travelled < length) {
                double seg = 1.2 + random.nextDouble() * 1.6;
                Vec3 jitter = b[0].scale((random.nextDouble() - 0.5) * 1.4).add(b[1].scale((random.nextDouble() - 0.5) * 1.4));
                Vec3 next = p.add(heading.scale(seg)).add(jitter);
                Vec3 d = next.subtract(p);
                int n = Math.max(2, (int) (d.length() / 0.25));
                for (int i = 0; i <= n; i++) {
                    send(level, i % 3 == 0 ? ModParticles.BUDDHA_GLOW_PALE : ModParticles.BUDDHA_SPARK, p.add(d.scale((double) i / n)), 1, 0, 0, 0, 0);
                }
                p = next;
                travelled += seg;
            }
            send(level, ModParticles.BUDDHA_FLASH, p, 1, 0, 0, 0, 0);
        }
    }

    /** Anneau d'onde au sol autour de {@code center} (rayon {@code radius}) : lumière dorée et éclats du sol soulevés. */
    public static void groundRing(ServerLevel level, Vec3 center, double radius) {
        BlockState ground = groundBlock(level, center);
        int points = (int) (radius * 10);
        for (int i = 0; i < points; i++) {
            double a = 2.0 * Math.PI * i / points;
            Vec3 out = new Vec3(Math.cos(a), 0.0, Math.sin(a));
            Vec3 p = center.add(out.scale(radius)).add(0.0, 0.15, 0.0);
            send(level, i % 2 == 0 ? ModParticles.BUDDHA_GLOW : ModParticles.BUDDHA_GLOW_ORANGE, p, 0, out.x, 0.05, out.z, 0.35);
            if (ground != null && i % 3 == 0) {
                send(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), p, 0, out.x * 0.4, 1.0, out.z * 0.4, 0.45);
            }
        }
    }

    /** Impact final de l'onde à sa portée maximale : éclair, sphère d'étincelles, paume dorée et anneau de lumière. */
    public static void shockwaveImpact(ServerLevel level, Vec3 c, Vec3 dir, double radius) {
        send(level, ModParticles.BUDDHA_FLASH, c, 2, 0.5, 0.5, 0.5, 0.0);
        sphere(level, ModParticles.BUDDHA_SPARK, c, 120, 0.9);
        send(level, ModParticles.BUDDHA_GLOW_PALE, c, 60, radius * 0.5, radius * 0.5, radius * 0.5, 0.0);
        palmImprint(level, c, dir, radius * 0.38, ModParticles.BUDDHA_GLOW);
        circle(level, ModParticles.BUDDHA_GLOW_ORANGE, c, dir, radius * 1.1, (int) (radius * 9), 0.0);
        send(level, ModParticles.BUDDHA_PETAL, c, 30, radius * 0.4, radius * 0.4, radius * 0.4, 0.08);
    }

    // ---------------------------------------------------------------- Déflagration (attaque 4)

    /** Charge de la Déflagration : l'énergie converge de tous côtés vers le joueur et une aura dorée l'enveloppe, de plus en plus dense. */
    public static void ultimateCharge(ServerLevel level, Vec3 center, double size, double progress, int age) {
        RandomSource random = level.getRandom();
        int gather = 16 + (int) (progress * 30);
        for (int i = 0; i < gather; i++) {
            Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian());
            if (d.lengthSqr() < 1.0E-4) {
                continue;
            }
            d = d.normalize();
            double r = (6.0 + random.nextDouble() * 8.0) * Math.max(1.0, size * 0.6);
            send(level, i % 4 == 0 ? ModParticles.BUDDHA_PETAL : ModParticles.BUDDHA_SPARK, center.add(d.scale(r)), 0,
                    -d.x, -d.y, -d.z, 0.09 * r);
        }
        double aura = (0.8 + 0.6 * progress) * size;
        send(level, ModParticles.BUDDHA_GLOW, center, 6 + (int) (progress * 12), aura * 0.5, aura * 0.8, aura * 0.5, 0.0);
        circle(level, ModParticles.BUDDHA_GLOW_PALE, center, new Vec3(0.0, 1.0, 0.0), aura * 1.6, 24, age * 0.4);
        if (age % 3 == 0) {
            send(level, ModParticles.BUDDHA_MIST, center.add(0.0, -0.8 * size, 0.0), 4, aura, 0.1, aura, 0.04);
        }
    }

    /** Libération de la Déflagration : flash, explosion de lumière, puis sphère qui s'étend à 360° (voir drawNova). */
    public static void nova(ServerLevel level, Vec3 center, double radius) {
        send(level, ModParticles.BUDDHA_FLASH, center, 1, 0.0, 0.0, 0.0, 0.0);
        send(level, ModParticles.BUDDHA_FLASH, center, 8, radius * 0.3, radius * 0.2, radius * 0.3, 0.0);
        sphere(level, ModParticles.BUDDHA_SPARK, center, 260, 1.8);
        sphere(level, ModParticles.BUDDHA_PETAL, center, 120, 1.1);
        send(level, ModParticles.BUDDHA_GLOW_PALE, center, 120, radius * 0.2, radius * 0.2, radius * 0.2, 0.0);
        // Fumée dorée : anneau qui roule au ras du sol + nuage qui monte
        for (int i = 0; i < 96; i++) {
            double a = 2.0 * Math.PI * i / 96;
            send(level, ModParticles.BUDDHA_MIST, center.add(0.0, -0.5, 0.0), 0, Math.cos(a), 0.03, Math.sin(a), 1.4);
        }
        send(level, ModParticles.BUDDHA_MIST, center, 80, radius * 0.35, 1.5, radius * 0.35, 0.05);
        BlockState ground = groundBlock(level, center.add(0.0, -0.5, 0.0));
        if (ground != null) {
            send(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), center, 400, radius * 0.35, 1.0, radius * 0.35, 0.6);
        }
        NOVAS.add(new Nova(level, center, radius));
    }

    /** Une étape de l'expansion : la coque de la sphère dorée (points répartis sur la sphère) et deux anneaux au sol, qui grandissent. */
    private static void drawNova(Nova n) {
        double progress = (double) n.age / NOVA_TICKS;
        double eased = 1.0 - (1.0 - progress) * (1.0 - progress);
        double r = n.radius * eased;
        int points = Math.min(320, 60 + (int) (r * 12));
        for (int i = 0; i < points; i++) {
            double dy = 1.0 - 2.0 * (i + 0.5) / points;
            if (dy < -0.35) {
                continue; // la sphère est surtout au-dessus du sol
            }
            double ring = Math.sqrt(1.0 - dy * dy);
            double a = i * GOLDEN_ANGLE + n.age * 0.15;
            Vec3 p = n.center.add(Math.cos(a) * ring * r, dy * r, Math.sin(a) * ring * r);
            send(n.level, i % 3 == 0 ? ModParticles.BUDDHA_GLOW_ORANGE : ModParticles.BUDDHA_GLOW, p, 1, 0.0, 0.0, 0.0, 0.0);
        }
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 ground = n.center.add(0.0, -0.8, 0.0);
        circle(n.level, ModParticles.BUDDHA_GLOW_PALE, ground, up, r, Math.min(200, 30 + (int) (r * 8)), n.age * 0.1);
        circle(n.level, ModParticles.BUDDHA_SPARK, ground, up, r * 0.8, 40, -n.age * 0.1);
        if (n.age % 2 == 0) {
            circle(n.level, ModParticles.BUDDHA_MIST, ground, up, r * 0.9, 36, 0.0);
        }
    }


    // ---------------------------------------------------------------- outils


    /** Envoie des particules à tous les joueurs proches (limite de distance levée : les grands cercles restent entiers). */
    private static void send(ServerLevel level, ParticleOptions particle, Vec3 p, int count, double dx, double dy, double dz, double speed) {
        level.sendParticles(particle, true, true, p.x, p.y, p.z, count, dx, dy, dz, speed);
    }

    /** Deux vecteurs unitaires perpendiculaires à {@code normal} (et entre eux) : le plan d'un cercle face à cette direction. */
    private static Vec3[] basis(Vec3 normal) {
        Vec3 n = normal.normalize();
        Vec3 ref = Math.abs(n.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 u = n.cross(ref).normalize();
        Vec3 v = n.cross(u).normalize();
        return new Vec3[] {u, v};
    }

    /** Cercle de particules immobiles dans le plan perpendiculaire à {@code normal}, tourné de {@code spin} radians. */
    private static void circle(ServerLevel level, ParticleOptions particle, Vec3 center, Vec3 normal, double radius, int points, double spin) {
        Vec3[] b = basis(normal);
        for (int i = 0; i < points; i++) {
            double a = 2.0 * Math.PI * i / points + spin;
            send(level, particle, center.add(b[0].scale(Math.cos(a) * radius)).add(b[1].scale(Math.sin(a) * radius)), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** {@code count} rayons (lignes de particules) entre les rayons {@code inner} et {@code outer}, dans le plan perpendiculaire à {@code normal}. */
    private static void spokes(ServerLevel level, ParticleOptions particle, Vec3 center, Vec3 normal, double inner, double outer,
                               int count, double spin) {
        Vec3[] b = basis(normal);
        for (int k = 0; k < count; k++) {
            double a = 2.0 * Math.PI * k / count + spin;
            Vec3 dir = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
            for (double t = inner; t <= outer; t += 0.8) {
                send(level, particle, center.add(dir.scale(t)), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Le bloc du sol sous le point d'impact (jusqu'à 3 blocs plus bas), pour colorer les débris ; null si rien de solide. */
    public static BlockState groundBlock(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point.x, point.y - 0.2, point.z);
        for (int i = 0; i < 4; i++) {
            BlockState state = level.getBlockState(pos.below(i));
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return state;
            }
        }
        return null;
    }

    /** {@code points} particules réparties sur une sphère, qui partent vers l'extérieur (count = 0 : les "offsets" sont la direction). */
    private static void sphere(ServerLevel level, ParticleOptions particle, Vec3 c, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double dy = 1.0 - 2.0 * (i + 0.5) / points;
            double ring = Math.sqrt(1.0 - dy * dy);
            double a = i * GOLDEN_ANGLE;
            send(level, particle, c, 0, Math.cos(a) * ring, dy, Math.sin(a) * ring, speed);
        }
    }
}
