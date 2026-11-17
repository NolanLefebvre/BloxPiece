package nolan.bloxpiece.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.IceTrident;

/**
 * Deuxième attaque du fruit de la Glace : Partisan, le lancer du trident de glace.
 *
 * Il faut tenir le trident de glace (attaque 1) en main : il est lancé et disparaît de l'inventaire. Sans trident en main,
 * l'attaque ne part pas (message dans la barre d'action). Le projectile 3D (IceTrident) file à grande vitesse vers le regard, laisse une traînée de neige et d'éclats, et à
 * l'impact gèle sa cible sur place et fait jaillir des pics de glace autour du point touché (voir IceTrident).
 */
public class IceTridentThrowAbility implements Ability {

    private static final double SPEED = 2.6;

    @Override
    public String id() {
        return "ice_partisan";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 8;
    }

    @Override
    public int requiredLevel() {
        return 3;
    }

    /** Il faut tenir le trident de glace (attaque 1) en main principale : sinon rien ne part et la recharge n'est pas consommée. */
    @Override
    public boolean canUse(ServerPlayer player) {
        if (IceSaberAbility.isHeld(player)) {
            return true;
        }
        player.sendSystemMessage(Component.translatable("message.bloxpiece.ice_partisan_no_saber"), true);
        return false;
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        IceSaberAbility.remove(player); // le trident tenu part : il disparaît de l'inventaire
        Vec3 look = player.getLookAngle();
        double size = player.getScale();
        double yaw = Math.toRadians(player.getYRot());
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        Vec3 start = player.getEyePosition().add(look.scale(1.0 * size)).add(right.scale(0.3 * size)).add(0.0, -0.15 * size, 0.0);
        IceTrident.spawn(level, player, start, look, SPEED);

        InteractionHand hand = player.getMainArm() == HumanoidArm.RIGHT ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        player.swing(hand, SwingAnimation.DEFAULT, true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.5F, 0.7F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.0F, 1.4F);
    }
}
