package nolan.bloxpiece.ability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.GoroBolt;
import nolan.bloxpiece.fx.GoroCloud;

/**
 * Goro Goro no Mi, attaque 2 : Kari (pluie de foudre). Le joueur vise une zone au sol ; {@link #STRIKES} points d'impact y sont
 * répartis (le premier en plein centre). Pour CHAQUE point, l'un après l'autre :
 * <ol>
 *   <li>un nuage électrique (GoroCloud) se forme {@link #CLOUD_HEIGHT} blocs au-dessus du point et se charge pendant
 *       {@link #FORM} ticks (lueurs et arcs bleus de plus en plus nombreux à l'intérieur et autour de lui) ;</li>
 *   <li>au maximum de sa charge, un énorme éclair bleu JAILLIT DU DESSOUS DE CE NUAGE et fonce vers le sol en
 *       {@link #DESCEND} ticks (pointe lumineuse et ramifications pendant la descente), avec deux éclairs secondaires ;</li>
 *   <li>à l'instant où la pointe touche le sol : dégâts à {@link #IMPACT_RADIUS} blocs (une fois par impact et par cible), onde
 *       de choc, décharges qui courent au sol, éclats, poussière, et un vrai cratère avec ses fissures (GoroTerrain).</li>
 * </ol>
 * Les nuages apparaissent tous les {@link #EVERY} ticks : les frappes s'enchaînent comme une vraie pluie de foudre.
 * Toutes les valeurs sont ajustables ci-dessous.
 */
public class GoroKariAbility implements Ability {

    // --- zone et cadence
    private static final double MAX_RANGE = 48.0;
    private static final int STRIKES = 10;
    private static final int EVERY = 4;              // un nouveau nuage tous les 4 ticks
    private static final double ZONE = 10.0;         // rayon de la zone touchée
    // --- nuages et éclairs
    private static final double CLOUD_HEIGHT = 17.0;
    private static final float CLOUD_SIZE = 2.6F;
    private static final int FORM = 18;              // formation + charge du nuage avant la frappe
    private static final int DESCEND = 3;            // durée de la descente de l'éclair
    private static final float BOLT_WIDTH = 7.0F;
    // --- impact
    private static final double IMPACT_RADIUS = 4.5;
    private static final float DAMAGE = 16.0F;
    private static final double CRATER_RADIUS = 3.6;
    private static final double CRATER_DEPTH = 2.8;
    private static final double CRACKS = 8.5;

    @Override
    public String id() {
        return "goro_kari";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 16;
    }

    @Override
    public int requiredLevel() {
        return 3;
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        if (AimHelper.findTarget(player, MAX_RANGE) == null) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.no_target", (int) MAX_RANGE));
            return false;
        }
        return true;
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 target = AimHelper.findTarget(player, MAX_RANGE);
        if (target == null) {
            return;
        }
        player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        Vec3[] points = new Vec3[STRIKES];
        for (int i = 0; i < STRIKES; i++) {
            // points bien répartis : angle d'or, distance croissante avec un peu de hasard
            double a = i * 2.39996 + level.getRandom().nextDouble() * 0.6;
            double d = i == 0 ? 0.0 : ZONE * Math.sqrt(i / (double) (STRIKES - 1)) * (0.8 + 0.2 * level.getRandom().nextDouble());
            double x = target.x + Math.cos(a) * d, z = target.z + Math.sin(a) * d;
            points[i] = new Vec3(x, Goro.ground(level, x, z, target.y), z);
        }
        level.playSound(null, target.x, target.y + CLOUD_HEIGHT, target.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.5F,
                0.6F);
        Goro.start(new Goro.Session() {
            final List<GoroCloud> clouds = new ArrayList<>();
            int age;

            @Override
            public boolean tick() {
                if (player.isRemoved() || player.level() != level) {
                    this.cancel();
                    return false;
                }
                this.age++;
                // un nouveau nuage au-dessus du point suivant
                if (this.age % EVERY == 0 && this.age / EVERY - 1 < STRIKES) {
                    Vec3 p = points[this.age / EVERY - 1];
                    float size = CLOUD_SIZE * (0.85F + 0.3F * level.getRandom().nextFloat());
                    this.clouds.add(GoroCloud.spawn(level, p.add(0, CLOUD_HEIGHT, 0), size, FORM + DESCEND + 18, FORM));
                }
                // chaque nuage frappe quand il est chargé
                for (int i = 0; i < STRIKES; i++) {
                    int born = (i + 1) * EVERY;
                    if (this.age == born + FORM) {
                        strike(level, player, points[i]);
                    } else if (this.age > born && this.age < born + FORM && this.age % 3 == i % 3) {
                        Vec3 c = points[i].add(0, CLOUD_HEIGHT - 1.0, 0);
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 4, CLOUD_SIZE, 0.4, CLOUD_SIZE, 0.05);
                    }
                }
                return this.age <= STRIKES * EVERY + FORM + DESCEND + 2;
            }

            @Override
            public void cancel() {
                for (GoroCloud c : this.clouds) {
                    c.discard();
                }
            }
        });
    }

    /** L'éclair jaillit du dessous du nuage au-dessus de {@code p} ; l'impact a lieu quand sa pointe touche le sol. */
    private static void strike(ServerLevel level, ServerPlayer player, Vec3 p) {
        Vec3 base = p.add(0, CLOUD_HEIGHT - 0.8, 0); // dessous du nuage
        GoroBolt.strike(level, base, p, BOLT_WIDTH, DESCEND + 8, DESCEND);
        GoroBolt.strike(level, base.add(0.8, 0, -0.5), p.add(0.6, 0, 0.4), BOLT_WIDTH * 0.45F, DESCEND + 6, DESCEND);
        GoroBolt.strike(level, base.add(-0.7, 0, 0.6), p.add(-0.9, 0, -0.3), BOLT_WIDTH * 0.35F, DESCEND + 5, DESCEND + 1);
        level.playSound(null, base.x, base.y, base.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 1.1F);
        Goro.later(DESCEND, () -> impact(level, player, p));
    }

    private static void impact(ServerLevel level, ServerPlayer player, Vec3 p) {
        Set<LivingEntity> hit = new HashSet<>();
        for (LivingEntity e : Goro.targets(level, player, new AABB(p, p).inflate(IMPACT_RADIUS, 4.0, IMPACT_RADIUS))) {
            double dx = e.getX() - p.x, dz = e.getZ() - p.z;
            double r = IMPACT_RADIUS + e.getBbWidth() * 0.5;
            if (dx * dx + dz * dz <= r * r && hit.add(e)) {
                Goro.hit(level, player, e, DAMAGE, p, 0.7, 0.45);
            }
        }
        Goro.impact(level, p, 2.4);
        Goro.groundArcs(level, p, 5, CRACKS, 1.0F);
        Goro.wave(level, p, (float) (IMPACT_RADIUS * 2.0), 12, false);
        GoroTerrain.scar(level, p, CRATER_RADIUS, CRATER_DEPTH, CRACKS, 6, 4);
        BuddhaHits.shake(level, p, 20.0, 0.7F, 8);
    }
}
