package nolan.bloxpiece.ability;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.CyborgBeam;

/**
 * Race Cyborg, niveau 50 : RADICAL BEAM, l'attaque la plus puissante. Les deux mains se rejoignent devant lui, une boule
 * d'énergie jaune grossit entre elles ({@link #CHARGE} ticks), puis un rayon laser jaune géant (CyborgBeam : volume, coeur
 * blanc, gaine jaune, halo orangé) part dans l'axe du regard jusqu'au premier obstacle ({@link #RANGE} blocs au plus). Tout ce
 * qu'il traverse est touché une fois ; à l'impact, une énorme explosion : dégâts dégressifs sur {@link #BLAST_RADIUS} blocs,
 * onde de choc, cratère, fissures et débris (GoroTerrain : règle mobGriefing, village protégé, blocs à contenu épargnés).
 */
public class CyborgRadicalBeamAbility implements Ability {

    public static final int CHARGE = 32, BEAM_LIFE = 26, DURATION = CHARGE + BEAM_LIFE + 10;
    /** Vitesse du front du rayon (blocs/tick) : il PARCOURT la distance jusqu'à l'impact (64 blocs en 11 ticks). */
    public static final double BEAM_SPEED = 6.0;
    /** Après l'arrivée du front : le rayon tient encore {@link #HOLD} ticks, l'explosion dure {@link #BLAST_LIFE} ticks (visuel). */
    public static final int HOLD = 14, BLAST_LIFE = 56;

    /** Ticks que met le front pour parcourir {@code distance} blocs (au moins 1). Partagé avec le rendu. */
    public static int travelTicks(double distance) {
        return Math.max(1, (int) Math.ceil(distance / BEAM_SPEED));
    }
    public static final int COOLDOWN = 20 * 90;
    public static final double RANGE = 64.0;
    public static final float BEAM_RADIUS = 1.6F, BEAM_DAMAGE = 30.0F;
    public static final double BLAST_RADIUS = 8.0;
    public static final float BLAST_DAMAGE = 55.0F;

    private final int level;

    public CyborgRadicalBeamAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "cyborg_radical_beam";
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
        return !Cyborg.refuseBusy(player);
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Cyborg.setAction(player, Cyborg.BEAM);
        Goro.start(new Goro.Session() {
            int t;

            @Override
            public boolean tick() {
                t++;
                if (!Cyborg.valid(player, level)) {
                    cancel();
                    return false;
                }
                if (t < CHARGE) {
                    // concentration d'énergie entre les mains : étincelles aspirées, lueur, son qui monte
                    float k = t / (float) CHARGE;
                    Vec3 c = Cyborg.handsFront(player, 0.8);
                    double r = 1.6 * (1.0 - k) + 0.2;
                    Vec3 eye = player.getEyePosition();
                    for (int i = 0; i < 3; i++) {
                        double a = level.getRandom().nextDouble() * Math.PI * 2, b = (level.getRandom().nextDouble() - 0.5) * Math.PI;
                        Vec3 p = c.add(Math.cos(a) * Math.cos(b) * r, Math.sin(b) * r, Math.sin(a) * Math.cos(b) * r);
                        if (p.distanceTo(eye) < 0.9 || p.subtract(eye).dot(player.getLookAngle()) < 0.3) {
                            continue; // jamais collées à la caméra du lanceur (vue à la première personne dégagée)
                        }
                        Vec3 v = c.subtract(p).scale(0.25);
                        level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, v.x, v.y, v.z, 1.0);
                    }
                    if (t % 4 == 0) {
                        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.7F + 1.3F * k);
                    }
                    return true;
                }
                if (t == CHARGE) {
                    fire(level, player);
                }
                if (t >= DURATION) {
                    Cyborg.clearAction(player, Cyborg.BEAM);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Cyborg.clearAction(player, Cyborg.BEAM);
            }
        });
    }

    private static void fire(ServerLevel level, ServerPlayer player) {
        Vec3 from = Cyborg.handsFront(player, 0.9);
        Vec3 look = player.getLookAngle();
        Vec3 far = from.add(look.scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 to = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        int travel = travelTicks(to.distanceTo(from));
        CyborgBeam.fire(level, player, from, to, BEAM_RADIUS, travel, travel + BLAST_LIFE);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 0.9F);
        // tout ce que le rayon traverse : touché UNE fois, à l'instant où son front l'atteint
        Set<LivingEntity> hit = new HashSet<>();
        for (LivingEntity target : Goro.targets(level, player, new AABB(from, to).inflate(BEAM_RADIUS + 1.0))) {
            Vec3 c = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
            if (distanceToSegment(c, from, to) <= BEAM_RADIUS + target.getBbWidth() * 0.5 && hit.add(target)) {
                int delay = travelTicks(Math.max(0.0, c.subtract(from).dot(look)));
                Goro.later(delay, () -> {
                    if (player.isAlive() && !player.isRemoved() && target.isAlive()) {
                        BuddhaHits.strike(level, player, target, BEAM_DAMAGE, look.scale(1.2).add(0.0, 0.3, 0.0));
                    }
                });
            }
        }
        // l'explosion quand le front du rayon atteint son point d'impact
        Goro.later(travel, () -> explode(level, player, to, hit));
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double k = len2 < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / len2));
        return p.distanceTo(a.add(ab.scale(k)));
    }

    private static void explode(ServerLevel level, ServerPlayer owner, Vec3 at, Set<LivingEntity> alreadyHit) {
        if (owner.isAlive() && !owner.isRemoved()) {
            for (LivingEntity target : Goro.targets(level, owner, new AABB(at, at).inflate(BLAST_RADIUS))) {
                double d = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).distanceTo(at);
                if (d > BLAST_RADIUS + target.getBbWidth() * 0.5) {
                    continue;
                }
                // l'explosion touche aussi ceux que le rayon a traversés (deux coups distincts : rayon, puis souffle)
                float damage = (float) (BLAST_DAMAGE * (1.0 - 0.6 * Math.min(1.0, d / BLAST_RADIUS)));
                Vec3 dir = target.position().subtract(at);
                Vec3 push = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.8, 0.0) : dir.normalize().scale(1.8).add(0.0, 0.7, 0.0);
                BuddhaHits.strike(level, owner, target, damage, push);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 3, 2.0, 1.0, 2.0, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 120, 1.0, 1.0, 1.0, 0.9);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 90, 4.0, 2.0, 4.0, 0.08);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.5, at.z, 30, 4.0, 1.0, 4.0, 0.03);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 60, 3.0, 1.5, 3.0, 0.15);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6.0F, 0.55F);
        BuddhaHits.shake(level, at, 48.0, 2.5F, 22);
        // cratère, fissures et débris (mêmes règles de destruction que les autres grandes attaques)
        GoroTerrain.scar(level, at, 6.5, 4.0, 14.0, 12, 30);
    }
}
