package nolan.bloxpiece.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.FireEffects;

/**
 * Deuxième attaque du fruit du feu : une énorme boule de feu avec une traînée en spirale,
 * qui EXPLOSE à l'impact (onde de choc, colonne de feu, dégâts de zone).
 */
public class GreatFireballAbility implements Ability {

    private static final double SPEED = 1.3;
    private static final int EXPLOSION_POWER = 3; // 1 = petit, 3 = comparable à la TNT, 4+ = énorme

    @Override
    public String id() {
        return "great_fireball";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 8; // 8 secondes
    }

    @Override
    public int requiredLevel() {
        return 3;
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();

        LargeFireball fireball = new LargeFireball(level, player, look, EXPLOSION_POWER);
        fireball.setPos(
                player.getX() + look.x * 1.5,
                player.getEyeY() - 0.2 + look.y * 1.5,
                player.getZ() + look.z * 1.5
        );
        fireball.setDeltaMovement(look.scale(SPEED));
        level.addFreshEntity(fireball);

        FireEffects.track(fireball, player, FireEffects.Kind.GREAT);

        // Lancement : onde de choc au sol, tourbillon de feu autour du joueur, gerbe devant lui
        FireEffects.shockwave(level, ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 40, 0.45);
        FireEffects.fireTornado(level, player.getX(), player.getY(), player.getZ(), 1.4, 2.5, 2);
        level.sendParticles(ParticleTypes.FLAME,
                player.getX() + look.x * 1.5, player.getEyeY() - 0.3 + look.y * 1.5, player.getZ() + look.z * 1.5,
                60, 0.5, 0.5, 0.5, 0.15);

        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 0.7f);
    }
}
