package nolan.bloxpiece.ability;

import java.util.HashSet;
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
import nolan.bloxpiece.fx.GoroColumn;

/**
 * Goro Goro no Mi, attaque 3 : El Thor. Le joueur désigne un point au sol :
 * <ol>
 *   <li>un IMMENSE nuage d'orage (GoroCloud, taille {@link #CLOUD_SIZE}) se forme {@link #CLOUD_HEIGHT} blocs au-dessus et se
 *       charge longuement ({@link #CHARGE} ticks) : lueurs de plus en plus vives, éclairs qui courent autour du nuage et
 *       pendent sous lui, étincelles qui tombent, grondements de plus en plus graves, sol qui tremble ;</li>
 *   <li>puis une gigantesque colonne de foudre (GoroColumn : pilier d'énergie de {@link #COLUMN_RADIUS} blocs de rayon, éclairs
 *       qui s'enroulent autour) s'abat du nuage jusqu'au sol en {@link #DESCEND} ticks, renforcée pendant {@link #COLUMN}
 *       ticks par des éclairs géants entrelacés ;</li>
 *   <li>impact : dégâts très lourds dans tout le cylindre ({@link #RADIUS} blocs, une fois par cible) et paralysie, énorme
 *       explosion électrique, grande onde de choc avec dôme de lumière, décharges au sol, et un énorme cratère profond
 *       (GoroTerrain : {@link #CRATER_RADIUS} x {@link #CRATER_DEPTH} blocs) avec des fissures jusqu'à {@link #CRACKS} blocs
 *       et des blocs projetés.</li>
 * </ol>
 * Toutes les valeurs sont ajustables ci-dessous.
 */
public class GoroElThorAbility implements Ability {

    // --- visée et rythme
    private static final double MAX_RANGE = 64.0;
    private static final int CHARGE = 60;
    private static final int DESCEND = 3;
    private static final int COLUMN = 20;
    // --- nuage et colonne
    private static final float CLOUD_SIZE = 11.0F;
    private static final double CLOUD_HEIGHT = 34.0;
    private static final float COLUMN_RADIUS = 3.4F;
    // --- impact
    private static final double RADIUS = 9.0;
    private static final float DAMAGE = 40.0F;
    private static final int STUN = 40;
    private static final double CRATER_RADIUS = 12.0;
    private static final double CRATER_DEPTH = 8.0;
    private static final double CRACKS = 30.0;
    private static final float WAVE = 36.0F;

    @Override
    public String id() {
        return "goro_el_thor";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 40;
    }

    @Override
    public int requiredLevel() {
        return 6;
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
        Vec3 aim = AimHelper.findTarget(player, MAX_RANGE);
        if (aim == null) {
            return;
        }
        Vec3 ground = new Vec3(aim.x, Goro.ground(level, aim.x, aim.z, aim.y), aim.z);
        player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        GoroCloud cloud = GoroCloud.spawn(level, ground.add(0, CLOUD_HEIGHT, 0), CLOUD_SIZE, CHARGE + COLUMN + 30, CHARGE);
        level.playSound(null, ground.x, ground.y + 10, ground.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0F, 0.5F);
        Goro.start(new Goro.Session() {
            int age;

            @Override
            public boolean tick() {
                if (player.isRemoved() || player.level() != level) {
                    cloud.discard();
                    return false;
                }
                this.age++;
                Vec3 base = cloud.position().add(0, -2.0, 0);
                double charge = this.age / (double) CHARGE;
                if (this.age < CHARGE) {
                    this.charge(base, charge);
                    return true;
                }
                int t = this.age - CHARGE;
                if (t == 0) {
                    GoroColumn.spawn(level, base, COLUMN_RADIUS, (float) (base.y - ground.y), COLUMN + 10);
                    level.playSound(null, ground.x, ground.y + 8, ground.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6.0F,
                            0.4F);
                }
                if (t < COLUMN) {
                    // éclairs géants entrelacés dans la colonne, renouvelés à chaque tick
                    int n = t < DESCEND ? 3 : 2;
                    for (int i = 0; i < n; i++) {
                        double a = level.getRandom().nextDouble() * Math.PI * 2, r = level.getRandom().nextDouble() * COLUMN_RADIUS;
                        Vec3 from = base.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                        double a2 = level.getRandom().nextDouble() * Math.PI * 2, r2 = level.getRandom().nextDouble() * COLUMN_RADIUS * 1.3;
                        GoroBolt.strike(level, from, ground.add(Math.cos(a2) * r2, 0.1, Math.sin(a2) * r2), i == 0 ? 10.0F : 5.5F,
                                t < DESCEND ? DESCEND + 3 : 3, t < DESCEND ? DESCEND - t : 0);
                    }
                    level.sendParticles(ParticleTypes.END_ROD, ground.x, ground.y + 8, ground.z, 24, COLUMN_RADIUS, 8.0, COLUMN_RADIUS, 0.05);
                    if (t == DESCEND) {
                        impact(level, player, ground);
                    }
                    if (t > DESCEND && t % 4 == 0) { // la colonne continue de ronger le sol
                        Goro.groundArcs(level, ground, 4, RADIUS + 4.0, 1.4F);
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, ground.x, ground.y + 1, ground.z, 60, RADIUS * 0.6, 1.0,
                                RADIUS * 0.6, 0.6);
                    }
                    return true;
                }
                if (t > COLUMN + 26) {
                    cloud.discard();
                    return false;
                }
                return true;
            }

            /** Charge du nuage : étincelles, éclairs autour et sous le nuage, grondements, tremblement croissant. */
            private void charge(Vec3 base, double charge) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, base.x, base.y, base.z, 8 + (int) (charge * 24), CLOUD_SIZE * 1.2, 1.0,
                        CLOUD_SIZE * 1.2, 0.1);
                if (this.age % 10 == 0) {
                    level.playSound(null, base.x, base.y, base.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0F,
                            (float) (0.9 - 0.5 * charge));
                    BuddhaHits.shake(level, ground, 40.0, 0.2F + 0.8F * (float) charge, 10);
                }
                int every = charge < 0.5 ? 6 : (charge < 0.8 ? 3 : 2);
                if (this.age > 10 && this.age % every == 0) {
                    // éclair qui fait le tour du nuage (d'un bord à l'autre, par l'extérieur)
                    double a = level.getRandom().nextDouble() * Math.PI * 2;
                    double span = 0.8 + level.getRandom().nextDouble() * 1.2;
                    double rr = CLOUD_SIZE * 1.5;
                    Vec3 from = base.add(Math.cos(a) * rr, 1.0 + level.getRandom().nextDouble() * 3.0, Math.sin(a) * rr);
                    Vec3 to = base.add(Math.cos(a + span) * rr, 0.5 + level.getRandom().nextDouble() * 3.0, Math.sin(a + span) * rr);
                    GoroBolt.strike(level, from, to, 1.6F + 1.6F * (float) charge, 5);
                }
                if (charge > 0.4 && this.age % 4 == 0) {
                    // décharges qui pendent sous le nuage, de plus en plus longues
                    double a = level.getRandom().nextDouble() * Math.PI * 2, r = level.getRandom().nextDouble() * CLOUD_SIZE;
                    Vec3 from = base.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    GoroBolt.strike(level, from, from.add((level.getRandom().nextDouble() - 0.5) * 6.0, -4.0 - charge * 10.0,
                            (level.getRandom().nextDouble() - 0.5) * 6.0), 1.4F, 4, 1);
                }
                if (charge > 0.6 && this.age % 2 == 0) { // le sol se met à crépiter sous la cible
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, ground.x, ground.y + 0.3, ground.z, 10, RADIUS * 0.7, 0.2,
                            RADIUS * 0.7, 0.2);
                }
            }

            @Override
            public void cancel() {
                cloud.discard();
            }
        });
    }

    private static void impact(ServerLevel level, ServerPlayer player, Vec3 ground) {
        Set<LivingEntity> hit = new HashSet<>();
        // hitbox : tout le cylindre de la colonne, du sol (un peu en dessous, cratère) jusqu'au nuage
        AABB box = new AABB(ground, ground).inflate(RADIUS, 0, RADIUS).expandTowards(0, CLOUD_HEIGHT, 0).expandTowards(0, -CRATER_DEPTH, 0);
        for (LivingEntity e : Goro.targets(level, player, box)) {
            double dx = e.getX() - ground.x, dz = e.getZ() - ground.z;
            double r = RADIUS + e.getBbWidth() * 0.5;
            if (dx * dx + dz * dz <= r * r && hit.add(e)) {
                Goro.hit(level, player, e, DAMAGE, ground, 1.4, 0.7);
                Goro.stun(e, STUN);
            }
        }
        Goro.impact(level, ground, 5.5);
        Goro.groundArcs(level, ground, 14, CRACKS * 0.7, 2.2F);
        Goro.wave(level, ground, WAVE, 26, true);
        Goro.later(5, () -> Goro.wave(level, ground, WAVE * 0.6F, 18, false)); // deuxième anneau
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ground.x, ground.y + 2, ground.z, 3, RADIUS * 0.4, 1.0, RADIUS * 0.4, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, ground.x, ground.y + 1, ground.z, 40, RADIUS * 0.7, 1.0, RADIUS * 0.7, 0.04);
        level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6.0F, 0.5F);
        BuddhaHits.shake(level, ground, 80.0, 2.6F, 24);
        GoroTerrain.scar(level, ground, CRATER_RADIUS, CRATER_DEPTH, CRACKS, 14, 40);
    }
}
