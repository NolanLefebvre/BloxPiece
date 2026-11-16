package nolan.bloxpiece.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.FireEffects;
import nolan.bloxpiece.fx.HikenFist;

/**
 * Deuxième attaque du fruit du feu : Hiken (le "poing de feu" de Portgas D. Ace). Remplace l'ancienne Grande boule de feu :
 * même emplacement (touche 2), même niveau requis, même recharge.
 *
 * Un gigantesque poing de feu (modèle 3D : poing fermé + avant-bras de flammes + masse de flammes, entité HikenFist) jaillit
 * devant le joueur et fonce dans la direction de son regard. Le poing EST le projectile : HikenFist est une entité projectile
 * personnalisée (déplacement, collisions et explosion propres), sans aucune boule de feu vanilla. À l'impact : dégâts directs,
 * explosion, puis dégâts de zone + mise à feu + onde de choc et colonne de flammes (FireEffects.Kind.HIKEN).
 * Tout est géré côté serveur ; l'entité HikenFist (position, orientation) est synchronisée avec les clients par Minecraft.
 */
public class HikenAbility implements Ability {

    private static final double SPEED = 1.8;           // rapide : un bras propulsé (l'ancienne Grande boule de feu : 1.3)
    private static final int EXPLOSION_POWER = 4;      // 3 = ancienne Grande boule de feu / météores, 4 = TNT
    private static final double START_DISTANCE = 2.5;  // distance entre les yeux du joueur et le devant du poing au départ

    @Override
    public String id() {
        return "hiken";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 8; // 8 secondes (comme l'attaque qu'il remplace)
    }

    @Override
    public int requiredLevel() {
        return 3; // comme l'attaque qu'il remplace
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();

        // Point de départ : devant les yeux du joueur, légèrement sous la ligne de visée (le bras sort de son épaule)
        Vec3 start = new Vec3(
                player.getX() + look.x * START_DISTANCE,
                player.getEyeY() - 0.3 + look.y * START_DISTANCE,
                player.getZ() + look.z * START_DISTANCE);

        // Le poing de feu est le projectile lui-même : le devant de ses phalanges part de `start` et fonce vers le regard du joueur.
        // FireEffects suit cette entité (traînée de particules) et déclenche l'effet d'impact quand elle disparaît.
        HikenFist fist = HikenFist.spawn(level, player, start, look, SPEED, EXPLOSION_POWER);
        FireEffects.track(fist, player, FireEffects.Kind.HIKEN);

        // Le joueur envoie son coup avec son bras principal
        InteractionHand punchingHand = player.getMainArm() == HumanoidArm.RIGHT ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        player.swing(punchingHand, SwingAnimation.DEFAULT, true);

        // Lancement : déflagration à l'origine du poing, onde de choc et tourbillon de feu autour du joueur
        level.sendParticles(ParticleTypes.FLAME, start.x, start.y, start.z, 50, 0.8, 0.8, 0.8, 0.15);
        level.sendParticles(ParticleTypes.LAVA, start.x, start.y, start.z, 10, 1.0, 1.0, 1.0, 0.0);
        FireEffects.shockwave(level, ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 56, 0.6);
        FireEffects.fireTornado(level, player.getX(), player.getY(), player.getZ(), 1.5, 3.0, 2);

        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5f, 0.8f);
    }
}
