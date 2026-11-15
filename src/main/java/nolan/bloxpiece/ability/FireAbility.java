package nolan.bloxpiece.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.FireEffects;

/**
 * Première attaque du fruit du feu : lance une boule de feu dans la direction du regard.
 */
public class FireAbility implements Ability {

    private static final double SPEED = 1.5;

    @Override
    public String id() {
        return "fire_fist";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 3; // 3 secondes
    }

    @Override
    public int requiredLevel() {
        return 1;
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();

        SmallFireball fireball = new SmallFireball(level, player, look);
        fireball.setPos(
                player.getX() + look.x * 1.2,
                player.getEyeY() - 0.2 + look.y * 1.2,
                player.getZ() + look.z * 1.2
        );
        fireball.setDeltaMovement(look.scale(SPEED));
        level.addFreshEntity(fireball);

        // Traînée + impact spectaculaire
        FireEffects.track(fireball, player, FireEffects.Kind.SMALL);

        // Petite gerbe de flammes au lancement
        level.sendParticles(ParticleTypes.FLAME,
                player.getX() + look.x * 1.2, player.getEyeY() - 0.3 + look.y * 1.2, player.getZ() + look.z * 1.2,
                20, 0.25, 0.25, 0.25, 0.06);

        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}
