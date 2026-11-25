package nolan.bloxpiece.ability;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.HumanFx;

/**
 * Race Humaine, niveau 20 : RUÉE DESTRUCTRICE. Le joueur se baisse et prend appui ({@link #CROUCH} ticks, épaule droite
 * placée devant), puis fonce droit devant (direction horizontale du regard, figée au départ) à {@link #SPEED} blocs/tick sur
 * {@link #DISTANCE} blocs au plus. La PREMIÈRE créature percutée prend le coup d'épaule (dégâts, forte projection vers l'arrière,
 * traînée de projection) ; celles collées à elle prennent un choc plus faible. Collision : onde de choc 3D, poussière, débris
 * (HumanFx.RUSH). Puis récupération ({@link #RECOVER} ticks).
 * <p>
 * Déplacement validé côté serveur, sans jamais traverser les murs : le mouvement est la VITESSE du joueur (envoyée à son
 * client, dont la physique normale gère les collisions avec les blocs) ; le serveur mesure l'avance réelle et arrête la ruée
 * dès qu'elle bute contre un obstacle ({@link #STALL}) ou qu'elle a parcouru sa distance. La ruée s'arrête AVANT la cible (elle
 * est repoussée) : le joueur ne reste jamais pris dans une entité. Toutes les valeurs sont configurables ici.
 */
public class HumanDestructiveRushAbility implements Ability {

    public static final int CROUCH = 8, RECOVER = 12;
    public static final double SPEED = 1.15, DISTANCE = 9.0;
    /** Nombre maximal de ticks de course (distance / vitesse, plus une petite marge). */
    public static final int DASH_TICKS = (int) Math.ceil(DISTANCE / SPEED) + 2;
    /** Avance minimale (fraction de SPEED) en dessous de laquelle la ruée est considérée bloquée par un obstacle. */
    public static final double STALL = 0.3;
    /** Portée du coup d'épaule devant le corps (blocs). */
    public static final double REACH = 0.7;
    public static final int COOLDOWN = 20 * 12;
    public static final float DAMAGE = 16.0F, SPLASH_DAMAGE = 7.0F;
    public static final double KNOCKBACK = 1.7, LIFT = 0.5, SPLASH_RADIUS = 1.8;
    public static final float FX_SIZE = 1.6F;
    /** Temps (ticks) que met la traînée de vitesse à s'effacer après la course. */
    public static final int TRAIL_FADE = 14;
    public static final int FX_LIFE = 24;

    private final int level;

    public HumanDestructiveRushAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "human_destructive_rush";
    }

    @Override
    public int cooldownTicks() {
        return COOLDOWN;
    }

    @Override
    public int requiredLevel() {
        return level;
    }

    @Override
    public String nameKey() {
        return "race.bloxpiece.attack." + id();
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        return !Human.refuseBusy(player);
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Human.setAction(player, Human.RUSH);
        Goro.start(new Goro.Session() {
            int t;
            int dash = -1;         // ticks de course (-1 : pas encore partie)
            int recover = -1;      // ticks de récupération (-1 : pas encore)
            int stalls;
            double travelled;
            Vec3 dir;
            Vec3 last;

            @Override
            public boolean tick() {
                if (!Human.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                player.fallDistance = 0.0;
                if (recover >= 0) { // récupération après la course
                    if (++recover >= RECOVER) {
                        Human.clearAction(player, Human.RUSH_END);
                        return false;
                    }
                    return true;
                }
                if (dash < 0) {
                    // prise d'appui : le joueur se fige, la poussière se soulève sous ses pieds
                    Human.halt(player);
                    if (t % 3 == 0) {
                        Human.groundBurst(level, player.position(), 3, 0.3, 0.03);
                    }
                    if (t == CROUCH - 3) {
                        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE,
                                SoundSource.PLAYERS, 0.9F, 0.5F);
                    }
                    if (t >= CROUCH) {
                        dash = 0;
                        dir = Human.flatLook(player);
                        last = player.position();
                        // la traînée de vitesse (visuelle) : elle suit le trajet réel du joueur chez tous les clients
                        HumanFx.spawn(level, player, HumanFx.TRAIL, player.position(), dir, 1.0F, DASH_TICKS + RECOVER + TRAIL_FADE);
                        // départ : le sol est poussé derrière le joueur
                        Vec3 back = player.position().subtract(dir.scale(0.4));
                        Human.groundBurst(level, back, 18, 0.35, 0.12);
                        level.sendParticles(ParticleTypes.CLOUD, back.x, back.y + 0.15, back.z, 8, 0.3, 0.05, 0.3, 0.05);
                        level.playSound(null, back.x, back.y, back.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.1F, 0.6F);
                        level.playSound(null, back.x, back.y, back.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.3F, 1.8F);
                    }
                    return true;
                }
                // ---- course
                dash++;
                Vec3 pos = player.position();
                double step = new Vec3(pos.x - last.x, 0.0, pos.z - last.z).length();
                last = pos;
                if (dash > 2) { // (le premier tick : le client n'a pas encore reçu la vitesse)
                    travelled += step;
                    stalls = step < SPEED * STALL ? stalls + 1 : 0;
                }
                // collision avec une créature, juste devant l'épaule
                AABB front = player.getBoundingBox().expandTowards(dir.scale(REACH + SPEED * 0.5)).inflate(0.2, 0.0, 0.2);
                LivingEntity target = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity e : Human.targets(level, player, front)) {
                    double d = e.position().distanceToSqr(pos);
                    if (d < best) {
                        best = d;
                        target = e;
                    }
                }
                if (target != null) {
                    collide(level, player, target, dir);
                    return finish();
                }
                if (stalls >= 2 || travelled >= DISTANCE || dash > DASH_TICKS) {
                    if (stalls >= 2) { // bute contre un mur : choc sourd, poussière
                        Vec3 wall = pos.add(dir.scale(0.5)).add(0.0, 0.9, 0.0);
                        level.sendParticles(ParticleTypes.POOF, wall.x, wall.y, wall.z, 8, 0.2, 0.4, 0.2, 0.05);
                        level.playSound(null, wall.x, wall.y, wall.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 0.6F);
                    }
                    return finish();
                }
                // le joueur fonce : sa vitesse est imposée (les collisions avec les blocs restent celles de Minecraft)
                double vy = player.onGround() ? -0.08 : Math.max(-0.35, player.getDeltaMovement().y);
                player.setDeltaMovement(dir.x * SPEED, vy, dir.z * SPEED);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                // traînée au sol et souffle autour du corps (suivent le joueur à chaque tick)
                if (player.onGround()) {
                    Human.groundBurst(level, pos, 4, 0.25, 0.08);
                    level.sendParticles(ParticleTypes.CLOUD, pos.x - dir.x * 0.6, pos.y + 0.1, pos.z - dir.z * 0.6, 2, 0.15, 0.02, 0.15, 0.02);
                }
                if (dash % 2 == 0) {
                    level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.35F, 0.5F + dash * 0.05F);
                }
                return true;
            }

            /** Fin de la course : arrêt net, passage à la récupération. */
            private boolean finish() {
                Human.halt(player);
                Human.setAction(player, Human.RUSH_END);
                recover = 0;
                return true;
            }

            @Override
            public void cancel() {
                Human.clearAction(player, Human.RUSH);
                Human.clearAction(player, Human.RUSH_END);
            }
        });
    }

    /** Le coup d'épaule : dégâts, projection, choc sur les créatures collées à la cible, onde de choc et poussière. */
    private static void collide(ServerLevel level, ServerPlayer player, LivingEntity target, Vec3 dir) {
        Set<LivingEntity> hit = new HashSet<>();
        Vec3 chest = player.position().add(0.0, 1.0, 0.0);
        Vec3 other = target.position().add(0.0, Math.min(1.0, target.getBbHeight() * 0.55), 0.0);
        Vec3 contact = chest.add(other.subtract(chest).scale(0.55));
        Human.strike(level, player, target, DAMAGE, dir.scale(KNOCKBACK).add(0.0, LIFT, 0.0), hit);
        Human.knockTrail(level, target, 12);
        for (LivingEntity e : Human.targets(level, player, new AABB(contact, contact).inflate(SPLASH_RADIUS))) {
            if (!hit.contains(e) && e.position().distanceTo(contact) <= SPLASH_RADIUS + e.getBbWidth() * 0.5) {
                Vec3 away = e.position().subtract(contact);
                Vec3 flat = new Vec3(away.x, 0.0, away.z);
                Vec3 push = (flat.lengthSqr() < 1.0E-4 ? dir : flat.normalize().add(dir).normalize()).scale(KNOCKBACK * 0.6);
                Human.strike(level, player, e, SPLASH_DAMAGE, push.add(0.0, LIFT * 0.7, 0.0), hit);
            }
        }
        // (le lanceur s'arrête net sur l'impact : finish() fige sa vitesse ; le contrecoup est dans sa pose, HumanPose)
        level.sendParticles(ParticleTypes.EXPLOSION, contact.x, contact.y, contact.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.CRIT, contact.x, contact.y, contact.z, 24, 0.3, 0.3, 0.3, 0.6);
        level.sendParticles(ParticleTypes.CLOUD, contact.x, contact.y, contact.z, 12, 0.3, 0.4, 0.3, 0.12);
        double gy = Human.ground(level, contact.x, contact.z, contact.y);
        Human.groundBurst(level, new Vec3(contact.x, gy, contact.z), 30, 1.0, 0.18);
        level.playSound(null, contact.x, contact.y, contact.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.4F, 0.5F);
        level.playSound(null, contact.x, contact.y, contact.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, contact.x, contact.y, contact.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.4F);
        HumanFx.spawn(level, player, HumanFx.RUSH, contact, dir, FX_SIZE, FX_LIFE);
        BuddhaHits.shake(level, contact, 10.0, 0.7F, 7);
    }
}
