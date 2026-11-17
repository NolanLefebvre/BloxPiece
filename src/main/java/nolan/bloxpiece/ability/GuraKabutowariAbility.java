package nolan.bloxpiece.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.QuakeFx;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Troisième attaque du Gura Gura no Mi : Kabutowari (Séisme localisé). Le joueur lève le poing au-dessus de sa tête
 * ({@link #WINDUP_TICKS} ticks, pose 4) puis l'abat violemment sur le sol (pose 5). À l'impact : des fissures 3D se propagent
 * depuis le point de frappe (branches seules, sans cercle), avec une onde de choc au ras du sol ; les ennemis à moins de
 * {@link #RADIUS} blocs sont projetés haut en l'air. Le sol se fend au même rythme que les fissures, des cratères s'ouvrent
 * autour du joueur et des blocs arrachés sont projetés dans les airs (si la règle mobGriefing est active).
 */
public class GuraKabutowariAbility implements Ability {

    private static final double RADIUS = 10.0;
    private static final float NEAR_DAMAGE = 26.0F;
    private static final float FAR_DAMAGE = 14.0F;
    private static final double LIFT = 1.7;
    private static final double PUSH = 0.9;
    /** Temps pour lever le poing avant de l'abattre (ticks), puis durée de la pose de frappe. */
    private static final int WINDUP_TICKS = 5;
    private static final int SLAM_POSE_TICKS = 10;
    /** Propagation des fissures (ticks), identique pour le modèle 3D et le terrain. */
    private static final int PROPAGATE_TICKS = 14;

    private static final class Strike {
        final ServerPlayer player;
        int age;

        Strike(ServerPlayer player) {
            this.player = player;
        }
    }

    private static final java.util.Map<java.util.UUID, Strike> STRIKES = new java.util.HashMap<>();

    @Override
    public String id() {
        return "gura_kabutowari";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 10;
    }

    @Override
    public int requiredLevel() {
        return 5;
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        return !STRIKES.containsKey(player.getUUID());
    }

    /** Le joueur lève le poing ; la frappe part après {@link #WINDUP_TICKS} ticks (tick()). */
    @Override
    public void use(ServerPlayer player) {
        STRIKES.put(player.getUUID(), new Strike(player));
        player.setAttached(ModAttachments.BUDDHA_POSE, 4);
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.0F, 1.4F);
    }

    public static void tick(MinecraftServer server) {
        java.util.Iterator<Strike> it = STRIKES.values().iterator();
        while (it.hasNext()) {
            Strike s = it.next();
            ServerPlayer player = s.player;
            if (player.isRemoved() || !player.isAlive()) {
                player.removeAttached(ModAttachments.BUDDHA_POSE);
                it.remove();
                continue;
            }
            s.age++;
            if (s.age == WINDUP_TICKS) {
                player.setAttached(ModAttachments.BUDDHA_POSE, 5); // le poing s'abat
                player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                slam((ServerLevel) player.level(), player);
            } else if (s.age >= WINDUP_TICKS + SLAM_POSE_TICKS) {
                player.removeAttached(ModAttachments.BUDDHA_POSE);
                it.remove();
            }
        }
    }

    public static void clear() {
        STRIKES.values().forEach(s -> s.player.removeAttached(ModAttachments.BUDDHA_POSE));
        STRIKES.clear();
    }

    /** L'impact du poing sur le sol. */
    private static void slam(ServerLevel level, ServerPlayer player) {
        Vec3 c = player.position();
        RandomSource random = level.getRandom();

        // Fissures 3D qui se propagent depuis le point de frappe (couchées au sol : pitch 90) + onde de choc au ras du sol
        QuakeFx.spawn(level, QuakeFx.RING, c.add(0.0, 0.08, 0.0), player.getYRot(), 90.0F, (float) (RADIUS * 0.55), 44);
        for (int i = 0; i < 80; i++) {
            double a = i * Math.PI * 2.0 / 80;
            level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * 0.8, c.y + 0.15, c.z + Math.sin(a) * 0.8,
                    0, Math.cos(a), 0.02, Math.sin(a), 0.8);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 6, 2.5, 0.2, 2.5, 0.0);

        // Ennemis projetés haut en l'air
        AABB zone = new AABB(c, c).inflate(RADIUS, 5.0, RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, zone, e -> BuddhaHits.canHit(e, player))) {
            Vec3 to = target.position().subtract(c);
            double dist = Math.sqrt(to.x * to.x + to.z * to.z);
            if (dist > RADIUS) {
                continue;
            }
            double falloff = dist / RADIUS;
            Vec3 out = dist < 0.1 ? Vec3.ZERO : new Vec3(to.x / dist, 0.0, to.z / dist);
            float damage = NEAR_DAMAGE + (FAR_DAMAGE - NEAR_DAMAGE) * (float) falloff;
            BuddhaHits.strike(level, player, target, damage, out.scale(PUSH).add(0.0, LIFT * (1.0 - 0.3 * falloff), 0.0));
        }

        // Destruction : le sol se fend, des cratères s'ouvrent autour du joueur (pas sous ses pieds), des blocs volent
        GuraTerrain.cracks(level, c, 2.0, RADIUS, 9, PROPAGATE_TICKS);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + random.nextDouble();
            double r = RADIUS * (0.45 + random.nextDouble() * 0.35);
            GuraTerrain.blast(level, player, c.add(Math.cos(a) * r, 0.0, Math.sin(a) * r), 3.5F);
        }
        GuraTerrain.debris(level, c, RADIUS, 28, 0.9);

        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.5F, 0.45F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
        BuddhaHits.shake(level, c, 30.0, 3.0F, 16);
    }
}
