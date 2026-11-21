package nolan.bloxpiece.ability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.GoroBolt;
import nolan.bloxpiece.fx.GoroCloud;
import nolan.bloxpiece.fx.GoroShock;

/**
 * Goro Goro no Mi, attaque 1 : Mamaragan (décharge électrique de zone, SANS AUCUNE destruction de terrain).
 * <ol>
 *   <li>Le joueur lève la main ; un grand nuage d'orage (GoroCloud, il le suit) se forme au-dessus de lui et se charge pendant
 *       {@link #FIRST_STRIKE} ticks.</li>
 *   <li>Puis, une à une, des éclairs épais descendent du nuage sur chaque entité de la zone ({@link #RADIUS} blocs autour du
 *       joueur) : dégâts de frappe ({@link #STRIKE_DAMAGE}) et paralysie ({@link #STUN} ticks).</li>
 *   <li>La cible frappée reste ÉLECTRISÉE jusqu'à la fin de l'attaque (GoroShock : éclair permanent entre le nuage et elle,
 *       éclairs qui parcourent son corps, aura, arcs au sol ; l'effet la suit quand elle bouge) et subit des dégâts
 *       périodiques : {@link #PULSE_DAMAGE} toutes les {@link #PULSE_EVERY} ticks, avec une brève secousse paralysante
 *       ({@link #PULSE_STUN} ticks). Jamais de dégâts à chaque tick.</li>
 *   <li>Le nuage reste actif {@link #DURATION} ticks après la première frappe : les nouvelles entités qui entrent dans la zone
 *       sont frappées à leur tour (recherche toutes les {@link #RESCAN} ticks). Une cible qui s'éloigne à plus de
 *       {@link #LEASH} blocs du joueur est libérée.</li>
 *   <li>Fin (durée écoulée, ou lanceur mort / déconnecté / sans le fruit) : le nuage et les électrisations s'éteignent en douceur.</li>
 * </ol>
 * Chaque cible n'est frappée qu'une fois par le premier éclair ; le lanceur et les joueurs en créatif ne le sont jamais
 * (ciblage commun BuddhaHits). La paralysie ne peut pas dépasser la durée de l'attaque.
 * <p>
 * Terrain : cette attaque n'appelle JAMAIS GoroTerrain ni GuraTerrain. Les impacts au sol (étincelles, poussière, onde,
 * arcs) sont purement visuels : aucun bloc n'est cassé, remplacé ou modifié.
 */
public class GoroMamaraganAbility implements Ability {

    // --- zone
    private static final double RADIUS = 14.0;
    private static final double LEASH = 20.0;           // au-delà, la cible est libérée
    // --- rythme et durée
    private static final int FIRST_STRIKE = 22;         // formation et charge du nuage
    private static final int STRIKE_EVERY = 3;          // une nouvelle cible frappée tous les 3 ticks
    private static final int DESCEND = 2;
    private static final int DURATION = 160;            // durée de l'attaque après la première frappe (8 s)
    private static final int RESCAN = 20;               // recherche de nouvelles cibles dans la zone
    // --- dégâts (frappe + périodiques)
    private static final float STRIKE_DAMAGE = 12.0F;
    private static final int STUN = 50;
    private static final float PULSE_DAMAGE = 4.0F;
    private static final int PULSE_EVERY = 20;          // 1 coup par seconde
    private static final int PULSE_STUN = 10;
    // --- visuels
    private static final double CLOUD_HEIGHT = 8.0;
    private static final float CLOUD_SIZE = 5.0F;
    private static final float BOLT_WIDTH = 1.8F;      // épaisseur des éclairs de cette attaque

    @Override
    public String id() {
        return "goro_mamaragan";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 14;
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true); // la main se lève vers le ciel
        GoroCloud cloud = GoroCloud.spawn(level, player.position().add(0, CLOUD_HEIGHT, 0), CLOUD_SIZE, FIRST_STRIKE + DURATION + 40,
                FIRST_STRIKE).follow(player, CLOUD_HEIGHT);
        level.playSound(null, player.getX(), player.getY() + 3, player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                1.0F, 1.3F);
        Goro.start(new Run(this, player, level, cloud));
    }

    /** Une cible électrisée : son effet visuel et le tick de son prochain coup périodique. */
    private static final class Victim {
        final LivingEntity entity;
        final GoroShock shock;
        int nextPulse;

        Victim(LivingEntity entity, GoroShock shock, int nextPulse) {
            this.entity = entity;
            this.shock = shock;
            this.nextPulse = nextPulse;
        }
    }

    private static final class Run implements Goro.Session {
        final GoroMamaraganAbility ability;
        final ServerPlayer player;
        final ServerLevel level;
        final GoroCloud cloud;
        final List<LivingEntity> queue = new ArrayList<>();
        final Set<LivingEntity> struck = new HashSet<>();                  // déjà frappées par un éclair (une seule fois)
        final Map<LivingEntity, Victim> victims = new IdentityHashMap<>(); // électrisées en ce moment
        int age;
        boolean ended;

        Run(GoroMamaraganAbility ability, ServerPlayer player, ServerLevel level, GoroCloud cloud) {
            this.ability = ability;
            this.player = player;
            this.level = level;
            this.cloud = cloud;
        }

        int end() {
            return FIRST_STRIKE + DURATION;
        }

        @Override
        public boolean tick() {
            if (!Goro.stillValid(this.player, this.level, this.ability) || this.cloud.isRemoved()) {
                this.cancel();
                return false;
            }
            this.age++;
            Vec3 c = this.cloud.position();
            if (this.age % 2 == 0) {
                this.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y - 1.0, c.z, 6, CLOUD_SIZE * 0.9, 0.4, CLOUD_SIZE * 0.9, 0.05);
            }
            if (this.age < FIRST_STRIKE) {
                this.charging(c);
                return true;
            }
            if (this.age >= this.end()) {
                this.cancel();
                return false;
            }
            // nouvelles cibles : au début, puis régulièrement (celles qui entrent dans la zone)
            if ((this.age - FIRST_STRIKE) % RESCAN == 0) {
                this.scan();
            }
            if ((this.age - FIRST_STRIKE) % STRIKE_EVERY == 0) {
                while (!this.queue.isEmpty()) {
                    LivingEntity t = this.queue.remove(0);
                    if (t.isAlive() && !t.isRemoved() && t.level() == this.level) {
                        this.strike(c, t);
                        break;
                    }
                }
            }
            this.electrify();
            if (this.age % 30 == 0) {
                this.level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6F, 1.5F);
            }
            return true;
        }

        /** Charge du nuage : grondements et décharges d'essai qui pendent dessous. */
        private void charging(Vec3 c) {
            if (this.age % 8 == 0) {
                this.level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F,
                        1.0F + this.age / (float) FIRST_STRIKE * 0.5F);
            }
            if (this.age > FIRST_STRIKE / 2 && this.age % 3 == 0) {
                double a = this.level.getRandom().nextDouble() * Math.PI * 2;
                Vec3 from = c.add(Math.cos(a) * CLOUD_SIZE, -0.8, Math.sin(a) * CLOUD_SIZE);
                GoroBolt.strike(this.level, from, from.add(-Math.cos(a) * 2.0, -2.0 - this.level.getRandom().nextDouble() * 1.5,
                        -Math.sin(a) * 2.0), 0.6F, 4);
            }
        }

        /** Ajoute à la file les entités de la zone pas encore frappées, des plus proches aux plus lointaines. */
        private void scan() {
            Vec3 p = this.player.position();
            List<LivingEntity> found = new ArrayList<>(Goro.targets(this.level, this.player, this.player.getBoundingBox().inflate(RADIUS, 10.0, RADIUS)));
            found.removeIf(e -> e.position().distanceToSqr(p) > RADIUS * RADIUS || this.struck.contains(e) || this.queue.contains(e));
            found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(this.player)));
            this.queue.addAll(found);
        }

        /** Éclair principal + deux décharges secondaires du nuage vers la cible ; la frappe a lieu quand la pointe arrive. */
        private void strike(Vec3 c, LivingEntity t) {
            this.struck.add(t);
            var random = this.level.getRandom();
            Vec3 from = c.add((random.nextDouble() - 0.5) * CLOUD_SIZE, -1.0, (random.nextDouble() - 0.5) * CLOUD_SIZE);
            Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
            GoroBolt.strike(this.level, from, to, BOLT_WIDTH, DESCEND + 7, DESCEND);
            for (int k = 0; k < 2; k++) {
                Vec3 f2 = c.add((random.nextDouble() - 0.5) * CLOUD_SIZE * 1.4, -0.8, (random.nextDouble() - 0.5) * CLOUD_SIZE * 1.4);
                Vec3 t2 = t.position().add((random.nextDouble() - 0.5) * 2.5, 0.1, (random.nextDouble() - 0.5) * 2.5);
                GoroBolt.strike(this.level, f2, t2, BOLT_WIDTH * 0.35F, DESCEND + 5, DESCEND + 1);
            }
            this.level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.6F, 1.2F);
            Goro.later(DESCEND, () -> this.impact(t));
        }

        /** La pointe arrive : dégâts de frappe, paralysie, effets au sol PUREMENT VISUELS, puis électrisation durable. */
        private void impact(LivingEntity t) {
            if (this.ended || this.player.isRemoved() || !t.isAlive() || t.isRemoved() || t.level() != this.level) {
                return;
            }
            Goro.hit(this.level, this.player, t, STRIKE_DAMAGE, this.player.position(), 0.2, 0.15);
            Goro.stun(t, Math.max(1, Math.min(STUN, this.end() - this.age)));
            Vec3 g = new Vec3(t.getX(), Goro.ground(this.level, t.getX(), t.getZ(), t.getY()), t.getZ());
            if (t.getY() - g.y < 3.0) {
                // étincelles, poussière, onde et arcs au sol : aucun bloc touché (pas de GoroTerrain ici)
                Goro.impact(this.level, g, 1.2);
                Goro.groundArcs(this.level, g, 3, 4.0, 0.45F);
                Goro.wave(this.level, g, 4.5F, 10, false);
            }
            if (!this.victims.containsKey(t)) {
                GoroShock shock = GoroShock.spawn(this.level, t, this.cloud, this.end() - this.age + 8);
                this.victims.put(t, new Victim(t, shock, this.age + PULSE_EVERY));
            }
        }

        /** Cibles électrisées : dégâts périodiques, étincelles ; libérées si mortes, disparues ou trop loin. */
        private void electrify() {
            Vec3 p = this.player.position();
            Iterator<Victim> it = this.victims.values().iterator();
            while (it.hasNext()) {
                Victim v = it.next();
                LivingEntity e = v.entity;
                if (!e.isAlive() || e.isRemoved() || e.level() != this.level || e.position().distanceToSqr(p) > LEASH * LEASH
                        || v.shock.isRemoved()) {
                    v.shock.fadeOut(6);
                    it.remove();
                    continue;
                }
                if (this.age >= v.nextPulse) {
                    v.nextPulse = this.age + PULSE_EVERY;
                    Goro.hit(this.level, this.player, e, PULSE_DAMAGE, p, 0.0, 0.0);
                    Goro.stun(e, Math.max(1, Math.min(PULSE_STUN, this.end() - this.age)));
                    v.shock.pulse();
                    this.level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5F,
                            1.8F);
                } else if (this.age % 4 == 0) {
                    this.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 4,
                            e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, e.getBbWidth() * 0.5, 0.1);
                }
            }
        }

        @Override
        public void cancel() {
            this.ended = true;
            this.cloud.fadeOut(14);
            for (Victim v : this.victims.values()) {
                v.shock.fadeOut(6);
            }
            this.victims.clear();
            this.queue.clear();
        }
    }
}
