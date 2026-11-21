package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import nolan.bloxpiece.fx.GoroOrb;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Goro Goro no Mi, attaque 4 (ultime) : Raigo. Même mécanique de charge que le Dai Enkai: Entei (touche maintenue, recharge au
 * tir, les deux bras levés : RAIGO_CHARGING, annulée si le joueur meurt, se déconnecte, change de dimension, touche l'eau ou perd son
 * fruit), mais tout est électrique :
 * <ol>
 *   <li>Charge : une petite boule électrique (GoroOrb) naît au-dessus du joueur et grossit ({@link #MIN_RADIUS} à
 *       {@link #MAX_RADIUS} blocs de rayon en {@link #MAX_CHARGE} ticks), arcs et ramifications sur sa surface (rendu client),
 *       étincelles et éclairs qui jaillissent d'elle vers le sol, grondements, tremblement de caméra croissant.</li>
 *   <li>Tir (touche relâchée ou charge pleine) : la boule descend lentement vers le point visé.</li>
 *   <li>Impact (au point visé, ou dès qu'elle touche le sol, un bloc ou une entité) : énorme explosion électrique, éclairs qui
 *       fusent au sol, tombent du ciel et jaillissent vers le haut, dôme de lumière et trois ondes de choc, dégâts très lourds
 *       dans {@link #BLAST_FACTOR} x son rayon (une seule fois par cible), un cratère immense et profond creusé bloc par bloc
 *       (GoroTerrain) et des fissures sur une grande distance. Dégâts, cratère et fissures grandissent avec la charge.</li>
 * </ol>
 */
public class GoroRaigoAbility implements Ability {

    // --- charge
    private static final int MIN_CHARGE = 15;
    private static final int MAX_CHARGE = 110;
    private static final float MIN_RADIUS = 2.0F;
    private static final float MAX_RADIUS = 14.0F;
    private static final double HEIGHT = 5.0;           // bas de la boule au-dessus des pieds du joueur
    private static final double ROOM = 18.0;            // place libre exigée au-dessus du joueur
    // --- vol
    private static final double MAX_RANGE = 80.0;
    private static final int MAX_FLIGHT = 200;
    // --- explosion (de 0 = charge minimale à 1 = pleine charge)
    private static final double BLAST_FACTOR = 1.8;     // rayon des dégâts = rayon de la boule x 1.8 (jusqu'à 25 blocs)
    private static final float DAMAGE_MIN = 30.0F, DAMAGE_MAX = 100.0F;
    private static final double CRATER_MIN = 7.0, CRATER_MAX = 24.0;
    private static final double DEPTH_MIN = 4.0, DEPTH_MAX = 13.0;
    private static final double CRACKS_FACTOR = 1.9;    // fissures jusqu'à 1.9 x le rayon du cratère

    private static final Map<UUID, Charge> CHARGES = new HashMap<>();

    @Override
    public String id() {
        return "goro_raigo";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60;
    }

    @Override
    public int requiredLevel() {
        return 8;
    }

    @Override
    public boolean cooldownOnRelease() {
        return true;
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        if (CHARGES.containsKey(player.getUUID())) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (AimHelper.clearanceAbove(level, player.position(), ROOM + 2.0) < ROOM) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.no_room_above"));
            return false;
        }
        return true;
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        GoroOrb orb = GoroOrb.spawn(level, player.position().add(0, HEIGHT + 0.3, 0), 0.3F);
        Charge charge = new Charge(this, player, level, orb);
        CHARGES.put(player.getUUID(), charge);
        player.setAttached(ModAttachments.RAIGO_CHARGING, true); // les deux bras se lèvent vers la boule (chez tout le monde)
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0F, 1.6F);
        Goro.start(charge);
    }

    @Override
    public void release(ServerPlayer player) {
        Charge charge = CHARGES.get(player.getUUID());
        if (charge != null) {
            charge.released = true;
        }
    }

    private static final class Charge implements Goro.Session {
        final GoroRaigoAbility ability;
        final ServerPlayer player;
        final ServerLevel level;
        final GoroOrb orb;
        int age;
        boolean released;
        // vol
        boolean flying;
        Vec3 target;
        Vec3 center;
        float radius;
        double power;
        int flight;

        Charge(GoroRaigoAbility ability, ServerPlayer player, ServerLevel level, GoroOrb orb) {
            this.ability = ability;
            this.player = player;
            this.level = level;
            this.orb = orb;
        }

        /** Avancement de la charge (0 = boule formée, 1 = pleine charge). */
        double charge01() {
            return Math.max(0.0, Math.min(1.0, (this.age - MIN_CHARGE) / (double) (MAX_CHARGE - MIN_CHARGE)));
        }

        float currentRadius() {
            if (this.age < MIN_CHARGE) {
                return 0.3F + (MIN_RADIUS - 0.3F) * this.age / MIN_CHARGE;
            }
            return (float) (MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * this.charge01());
        }

        @Override
        public boolean tick() {
            if (this.flying) {
                return this.fly();
            }
            if (!Goro.stillValid(this.player, this.level, this.ability)) {
                this.cancel();
                return false;
            }
            this.age++;
            float r = this.currentRadius();
            Vec3 c = this.player.position().add(0, HEIGHT + r, 0);
            this.orb.setPos(c.x, c.y, c.z);
            this.orb.setRadius(r);
            // énergie qui se concentre : étincelles aspirées, éclairs vers le sol, grondements, caméra qui tremble
            this.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 6 + (int) (r * 3), r * 1.2, r * 1.2, r * 1.2, 0.2);
            int every = this.charge01() < 0.5 ? 6 : 3;
            if (this.age % every == 0 && r > 2.0F) {
                // éclairs qui jaillissent de la boule et frappent le sol autour du joueur
                double a = this.level.getRandom().nextDouble() * Math.PI * 2;
                Vec3 g = this.player.position().add(Math.cos(a) * (r * 0.6 + 3), 0, Math.sin(a) * (r * 0.6 + 3));
                GoroBolt.strike(this.level, c.add(Math.cos(a) * r * 0.9, -r * 0.6, Math.sin(a) * r * 0.9),
                        new Vec3(g.x, Goro.ground(this.level, g.x, g.z, this.player.getY()) + 0.1, g.z), 1.2F + r * 0.15F, 6, 1);
            }
            if (this.age % 5 == 0) { // décharges qui courent au sol autour du joueur
                Goro.groundArcs(this.level, this.player.position(), 2 + (int) (3 * this.charge01()), 3.0 + r * 0.5, 0.7F);
            }
            if (this.age % 4 == 0) { // l'énergie est aspirée vers la boule
                for (int k = 0; k < 6; k++) {
                    double a = this.level.getRandom().nextDouble() * Math.PI * 2, b = (this.level.getRandom().nextDouble() - 0.5) * Math.PI;
                    double d = r * 2.2;
                    this.level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * Math.cos(b) * d, c.y + Math.sin(b) * d,
                            c.z + Math.sin(a) * Math.cos(b) * d, 0, -Math.cos(a) * Math.cos(b), -Math.sin(b), -Math.sin(a) * Math.cos(b),
                            0.4 + r * 0.05);
                }
            }
            if (this.age % 10 == 0) {
                this.level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5F,
                        (float) (1.2 - 0.6 * this.charge01()));
                BuddhaHits.shake(this.level, c, 20.0 + r * 4, 0.3F + 1.2F * (float) this.charge01(), 10);
            }
            if (this.age >= MIN_CHARGE && (this.released || this.age >= MAX_CHARGE)) {
                this.launch(c, r);
            }
            return true;
        }

        private void launch(Vec3 c, float r) {
            CHARGES.remove(this.player.getUUID());
            this.player.removeAttached(ModAttachments.RAIGO_CHARGING);
            AbilityManager.startCooldown(this.player, this.ability); // la recharge démarre au tir
            this.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            Vec3 aim = AimHelper.findTarget(this.player, MAX_RANGE);
            if (aim == null) {
                Vec3 far = this.player.getEyePosition().add(this.player.getLookAngle().scale(40.0));
                aim = new Vec3(far.x, Goro.ground(this.level, far.x, far.z, far.y), far.z);
            }
            this.target = aim;
            this.center = c;
            this.radius = r;
            this.power = this.charge01();
            this.flying = true;
            this.level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5.0F, 0.4F);
            Goro.wave(this.level, this.player.position(), 6.0F + r, 12, false); // le départ souffle le sol autour du joueur
        }

        /** Vol de la boule vers le point visé ; impact au point, au sol, contre un bloc ou une entité. */
        private boolean fly() {
            if (this.orb.isRemoved()) {
                return false;
            }
            this.flight++;
            double speed = 0.45 + 0.25 * this.power;
            Vec3 to = this.target.subtract(this.center);
            double dist = to.length();
            boolean hits = dist <= Math.max(1.0, this.radius * 0.6) || this.flight > MAX_FLIGHT;
            Vec3 bottom = this.center.add(0, -this.radius * 0.9, 0);
            if (!hits && AimHelper.isBlocked(this.level, bottom)) {
                hits = true;
            }
            if (!hits && !Goro.targets(this.level, this.player, new AABB(this.center, this.center).inflate(this.radius * 0.8)).isEmpty()) {
                hits = true;
            }
            if (hits) {
                this.explode(dist <= Math.max(1.0, this.radius * 0.6) ? this.target : bottom);
                return false;
            }
            this.center = this.center.add(to.scale(Math.min(1.0, speed / dist)));
            this.orb.setPos(this.center.x, this.center.y, this.center.z);
            if (this.flight % 3 == 0) {
                this.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.center.x, this.center.y, this.center.z, 10, this.radius,
                        this.radius, this.radius, 0.3);
            }
            return true;
        }

        private void explode(Vec3 at) {
            this.orb.discard();
            Vec3 g = new Vec3(at.x, Goro.ground(this.level, at.x, at.z, at.y), at.z);
            double p = this.power;
            double blast = this.radius * BLAST_FACTOR;
            float damage = (float) (DAMAGE_MIN + (DAMAGE_MAX - DAMAGE_MIN) * p);
            double crater = CRATER_MIN + (CRATER_MAX - CRATER_MIN) * p;
            double depth = DEPTH_MIN + (DEPTH_MAX - DEPTH_MIN) * p;
            // dégâts : un dôme autour de l'impact (rayon blast, une seule fois par cible)
            Set<LivingEntity> hit = new HashSet<>();
            for (LivingEntity e : Goro.targets(this.level, this.player, new AABB(g, g).inflate(blast, blast * 0.8, blast))) {
                double dx = e.getX() - g.x, dy = Math.max(0.0, e.getY() - g.y), dz = e.getZ() - g.z;
                double rr = blast + e.getBbWidth() * 0.5;
                if (dx * dx + dz * dz + dy * dy * 1.5 <= rr * rr && hit.add(e)) {
                    Goro.hit(this.level, this.player, e, damage, g, 1.8, 0.8);
                    Goro.stun(e, 40);
                }
            }
            // explosion électrique : flash, dôme et ondes de choc, éclairs dans toutes les directions
            Goro.impact(this.level, g, 3.0 + this.radius * 0.6);
            Goro.wave(this.level, g, (float) (blast * 2.2), 34, true);
            Goro.later(5, () -> Goro.wave(this.level, g, (float) (blast * 1.4), 24, false));
            Goro.later(11, () -> Goro.wave(this.level, g, (float) (blast * 0.9), 18, false));
            Goro.groundArcs(this.level, g, 24, blast * 1.5, 2.0F);
            var random = this.level.getRandom();
            for (int i = 0; i < 10; i++) { // éclairs qui retombent du ciel tout autour
                double a = Math.PI * 2 * i / 10 + random.nextDouble() * 0.3, d = blast * (0.5 + 0.6 * random.nextDouble());
                Vec3 q = g.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                q = new Vec3(q.x, Goro.ground(this.level, q.x, q.z, g.y), q.z);
                GoroBolt.strike(this.level, q.add(0, 22, 0), q, 4.0F, 10, 2);
            }
            for (int i = 0; i < 8; i++) { // éclairs projetés vers le ciel, en gerbe
                double a = Math.PI * 2 * i / 8 + random.nextDouble() * 0.4;
                double len = blast * (0.8 + 0.5 * random.nextDouble());
                GoroBolt.strike(this.level, g.add(0, 1, 0), g.add(Math.cos(a) * len * 0.6, len * 0.9, Math.sin(a) * len * 0.6), 3.5F, 9);
            }
            this.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, g.x, g.y + 1, g.z, 300, blast, 1.5, blast, 1.2); // onde d'étincelles
            this.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, g.x, g.y + 2, g.z, 4 + (int) (4 * p), blast * 0.4, 2.0, blast * 0.4, 0);
            this.level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, g.x, g.y + 1, g.z, 60, blast * 0.6, 1.5, blast * 0.6, 0.05);
            this.level.playSound(null, g.x, g.y, g.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 8.0F, 0.4F);
            this.level.playSound(null, g.x, g.y, g.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 8.0F, 0.3F);
            BuddhaHits.shake(this.level, g, 96.0, 3.0F, 30);
            // cratère immense et fissures sur une grande distance (tout grandit avec la charge)
            GoroTerrain.scar(this.level, g, crater, depth, crater * CRACKS_FACTOR, 12 + (int) (8 * p), 30 + (int) (70 * p));
        }

        @Override
        public void cancel() {
            CHARGES.remove(this.player.getUUID());
            this.player.removeAttached(ModAttachments.RAIGO_CHARGING);
            this.orb.discard();
        }
    }
}
