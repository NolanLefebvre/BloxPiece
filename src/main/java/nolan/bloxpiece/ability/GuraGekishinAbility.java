package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.QuakeFx;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Deuxième attaque du Gura Gura no Mi : Gekishin (Coup sismique). Le joueur fonce loin vers l'avant (dash entretenu : la vitesse
 * est renvoyée à chaque tick, donc le frottement du sol ne le freine plus), le bras droit tendu, le poing enveloppé d'une bulle
 * sismique 3D. Au sol, le coup RACLE le terrain : un peu de résistance, gerbes de terre, grondement et blocs arrachés sur la
 * trajectoire, puis un impact plus lourd. Au contact du premier ennemi ou d'un mur (ou à la fin du dash), la bulle éclate : lourds
 * dégâts sur la cible, onde de choc qui blesse et projette les ennemis proches, cratère.
 */
public class GuraGekishinAbility implements Ability {

    /** Vitesse du dash (blocs/tick) en l'air, et au sol (raclement : un peu de résistance). Environ 20 blocs en l'air. */
    private static final double DASH_SPEED = 1.45;
    private static final double SCRAPE_SPEED = 1.15;
    private static final int DASH_TICKS = 14;
    /** La bulle reste visible un moment après l'impact (ticks). */
    private static final int ORB_LINGER_TICKS = 8;
    private static final double CONTACT = 1.6;
    private static final float MAIN_DAMAGE = 40.0F;
    private static final float SPLASH_DAMAGE = 20.0F;
    private static final double SPLASH_RADIUS = 6.5;
    private static final float CRATER_POWER = 5.0F;

    private static final class Dash {
        final ServerPlayer player;
        final Vec3 dir;
        final QuakeFx orb;
        int age;
        int scraped; // ticks passés à racler le sol : impact plus lourd

        Dash(ServerPlayer player, Vec3 dir, QuakeFx orb) {
            this.player = player;
            this.dir = dir;
            this.orb = orb;
        }
    }

    private static final Map<UUID, Dash> DASHES = new HashMap<>();
    /** Après l'impact, le bras reste tendu tant que la bulle éclate encore (joueur -> ticks restants). */
    private static final Map<ServerPlayer, Integer> POSE_LEFT = new HashMap<>();

    @Override
    public String id() {
        return "gura_gekishin";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 7;
    }

    @Override
    public int requiredLevel() {
        return 3;
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        return !DASHES.containsKey(player.getUUID());
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, Math.max(-0.3, Math.min(0.35, look.y)), look.z).normalize();
        push(player, dir, player.onGround());
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        // La bulle est ancrée sur le poing droit (placée exactement sur la main par le client), bras tendu devant (pose 1)
        QuakeFx orb = QuakeFx.spawn(level, QuakeFx.ORB, fist(player, dir), player.getYRot(), 0.0F, 0.45F, DASH_TICKS + 20)
                .anchorTo(player, 1);
        player.setAttached(ModAttachments.BUDDHA_POSE, 1);
        player.setAttached(ModAttachments.HIDE_HAND_ITEM, true); // l'objet en main ne traverse pas la boule
        DASHES.put(player.getUUID(), new Dash(player, dir, orb));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2F, 1.6F);
    }

    /**
     * Pousse le joueur dans la direction du dash. La vitesse d'un joueur est gérée par son client : on la lui envoie à chaque
     * tick. Au sol, pas de composante vers le bas (il raclerait contre le sol) et un peu moins vite ; en l'air, il suit le regard.
     */
    private static void push(ServerPlayer player, Vec3 dir, boolean grounded) {
        Vec3 v = grounded
                ? new Vec3(dir.x, 0.0, dir.z).normalize().scale(SCRAPE_SPEED).add(0.0, 0.05, 0.0)
                : dir.scale(DASH_SPEED).add(0.0, 0.04, 0.0);
        player.setDeltaMovement(v);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /** Le poing droit, un peu devant le joueur. */
    private static Vec3 fist(ServerPlayer player, Vec3 dir) {
        return IceSaberAbility.handOf(player).add(dir.scale(0.7)).add(0.0, 0.35, 0.0);
    }

    public static void tick(MinecraftServer server) {
        POSE_LEFT.entrySet().removeIf(e -> {
            if (e.getValue() <= 1) {
                e.getKey().removeAttached(ModAttachments.BUDDHA_POSE);
                e.getKey().removeAttached(ModAttachments.HIDE_HAND_ITEM);
                return true;
            }
            e.setValue(e.getValue() - 1);
            return false;
        });
        Iterator<Dash> it = DASHES.values().iterator();
        while (it.hasNext()) {
            Dash d = it.next();
            ServerPlayer player = d.player;
            if (player.isRemoved() || !player.isAlive() || player.isInWater()) {
                d.orb.discard();
                player.removeAttached(ModAttachments.BUDDHA_POSE);
                player.removeAttached(ModAttachments.HIDE_HAND_ITEM);
                it.remove();
                continue;
            }
            ServerLevel level = (ServerLevel) player.level();
            d.age++;
            player.fallDistance = 0.0;
            boolean grounded = player.onGround();
            push(player, d.dir, grounded);
            if (grounded) {
                scrape(level, player, d);
            }
            Vec3 fist = fist(player, d.dir);
            d.orb.follow(fist);
            level.sendParticles(ParticleTypes.END_ROD, fist.x, fist.y, fist.z, 2, 0.25, 0.25, 0.25, 0.01);
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 2, 0.3, 0.3, 0.3, 0.02);

            // Premier ennemi au contact du poing
            LivingEntity target = null;
            double best = CONTACT * CONTACT;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(fist, fist).inflate(CONTACT + 1.0),
                    e -> BuddhaHits.canHit(e, player))) {
                double dist = e.getBoundingBox().distanceToSqr(fist);
                if (dist < best) {
                    best = dist;
                    target = e;
                }
            }
            // Mur : seulement un obstacle à hauteur du poing (une marche ou une dalle au sol ne coupe plus le dash)
            Vec3 flat = new Vec3(d.dir.x, 0.0, d.dir.z).normalize();
            boolean wall = level.clip(new ClipContext(fist, fist.add(flat.scale(1.2)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS;
            if (target != null || wall || d.age >= DASH_TICKS) {
                impact(level, player, d, fist, target);
                it.remove();
            }
        }
    }

    /** Le coup racle le sol : gerbes de terre, grondement, blocs arrachés sur la trajectoire. */
    private static void scrape(ServerLevel level, ServerPlayer player, Dash d) {
        d.scraped++;
        net.minecraft.core.BlockPos below = player.blockPosition().below();
        net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(below);
        if (!ground.isAir()) {
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground),
                    player.getX(), player.getY() + 0.1, player.getZ(), 14, 0.6, 0.15, 0.6, 0.25);
        }
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 4, 0.5, 0.1, 0.5, 0.05);
        if (d.scraped % 2 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.2F, 0.5F);
            GuraTerrain.debris(level, player.position().subtract(d.dir.scale(1.5)), 1.8, 2, 0.6);
        }
        if (d.scraped % 4 == 0) {
            BuddhaHits.shake(level, player.position(), 12.0, 0.8F, 4);
        }
    }

    private static void impact(ServerLevel level, ServerPlayer player, Dash d, Vec3 point, LivingEntity target) {
        // La bulle reste encore un instant sur le poing pendant qu'elle éclate
        d.orb.setLife(d.orb.tickCount + ORB_LINGER_TICKS);
        POSE_LEFT.put(player, ORB_LINGER_TICKS);
        // Un coup qui a raclé le sol frappe plus lourd (jusqu'à +40 %)
        float heavy = 1.0F + Math.min(0.4F, d.scraped * 0.04F);
        if (target != null) {
            BuddhaHits.strike(level, player, target, MAIN_DAMAGE * heavy, d.dir.scale(3.8).add(0.0, 0.8, 0.0));
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(SPLASH_RADIUS),
                e -> e != target && BuddhaHits.canHit(e, player))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(point);
            double dist = to.length();
            if (dist > SPLASH_RADIUS) {
                continue;
            }
            Vec3 out = to.lengthSqr() < 1.0E-4 ? d.dir : to.normalize();
            double falloff = 1.0 - dist / SPLASH_RADIUS;
            BuddhaHits.strike(level, player, e, SPLASH_DAMAGE * (float) (0.5 + 0.5 * falloff), out.scale(2.8 * falloff + 0.6).add(0.0, 0.7, 0.0));
        }
        // Le joueur s'arrête net (léger recul)
        player.setDeltaMovement(d.dir.scale(-0.25).add(0.0, 0.15, 0.0));
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        // La sphère éclate en onde de choc fissurée
        QuakeFx.spawn(level, QuakeFx.BLAST, point, player.getYRot(), 0.0F, 3.5F, 12);
        QuakeFx.spawn(level, QuakeFx.PANEL, point, player.getYRot(), player.getXRot(), 3.2F, 12);
        // Destruction : cratère au point d'impact, blocs projetés
        GuraTerrain.blast(level, player, point.add(d.dir.scale(0.8)), CRATER_POWER * heavy);
        GuraTerrain.debris(level, point, 4.5, 10, 0.9);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.CLOUD, point.x, point.y, point.z, 30, 1.2, 0.8, 1.2, 0.15);
        level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 24, 1.0, 1.0, 1.0, 0.2);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.6F);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
        BuddhaHits.shake(level, point, 30.0, 2.8F, 12);
    }

    public static void clear() {
        DASHES.values().forEach(d -> {
            d.orb.discard();
            d.player.removeAttached(ModAttachments.BUDDHA_POSE);
            d.player.removeAttached(ModAttachments.HIDE_HAND_ITEM);
        });
        DASHES.clear();
        POSE_LEFT.keySet().forEach(p -> {
            p.removeAttached(ModAttachments.BUDDHA_POSE);
            p.removeAttached(ModAttachments.HIDE_HAND_ITEM);
        });
        POSE_LEFT.clear();
    }
}
