package nolan.bloxpiece.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.FireEffects;

/**
 * Troisième attaque (ultime) du fruit du feu : une pluie de météores enflammés.
 * La zone visée est marquée par des cercles de feu, puis les météores s'abattent avec
 * une traînée en spirale et explosent à l'impact.
 */
public class MeteorRainAbility implements Ability {

    private static final int METEOR_COUNT = 14;
    private static final double TARGET_DISTANCE = 16.0; // distance du centre de la zone devant le joueur
    private static final double SPREAD = 8.0;           // rayon de la zone touchée
    private static final int EXPLOSION_POWER = 3;

    @Override
    public String id() {
        return "meteor_rain";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 30; // 30 secondes
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        RandomSource random = level.getRandom();

        // Centre de la zone : devant le joueur, à l'horizontale (même s'il regarde en haut ou en bas)
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        double centerX = player.getX() + flat.x * TARGET_DISTANCE;
        double centerZ = player.getZ() + flat.z * TARGET_DISTANCE;

        // Lancement : triple onde de choc, double tornade de feu autour du joueur
        FireEffects.shockwave(level, ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 64, 0.35);
        FireEffects.shockwave(level, ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 64, 0.6);
        FireEffects.shockwave(level, ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.3, player.getZ(), 48, 0.45);
        FireEffects.fireTornado(level, player.getX(), player.getY(), player.getZ(), 1.8, 5.0, 3);
        FireEffects.fireTornado(level, player.getX(), player.getY(), player.getZ(), 1.2, 5.0, -3);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);

        // Zone visée : cercles de feu au sol + faisceau de flammes montant vers le ciel
        FireEffects.groundRing(level, centerX, player.getY(), centerZ, SPREAD, 72);
        FireEffects.groundRing(level, centerX, player.getY(), centerZ, SPREAD * 0.6, 48);
        FireEffects.groundRing(level, centerX, player.getY(), centerZ, SPREAD * 0.3, 24);
        for (int h = 0; h < 40; h++) {
            level.sendParticles(ParticleTypes.FLAME, centerX, player.getY() + h, centerZ, 3, 0.3, 0.3, 0.3, 0.02);
        }

        for (int i = 0; i < METEOR_COUNT; i++) {
            double x = centerX + (random.nextDouble() * 2 - 1) * SPREAD;
            double z = centerZ + (random.nextDouble() * 2 - 1) * SPREAD;
            // Hauteurs différentes : les météores ne touchent pas le sol tous en même temps
            double y = player.getY() + 24 + random.nextDouble() * 26;

            Vec3 fall = new Vec3(flat.x * 0.15, -1.0, flat.z * 0.15); // chute légèrement inclinée
            LargeFireball meteor = new LargeFireball(level, player, fall, EXPLOSION_POWER);
            meteor.setPos(x, y, z);
            meteor.setDeltaMovement(fall.scale(1.4));
            level.addFreshEntity(meteor);

            FireEffects.track(meteor, player, FireEffects.Kind.METEOR);

            // Éclat dans le ciel à l'apparition du météore
            level.sendParticles(ParticleTypes.FLAME, x, y, z, 40, 0.8, 0.8, 0.8, 0.1);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0f, 0.5f);
    }
}
