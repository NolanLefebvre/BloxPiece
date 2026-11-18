package nolan.bloxpiece.fruit;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.ability.BuddhaTransformAbility;

/**
 * Malédiction de l'eau, commune aux fruits du démon. Exécutée à chaque tick serveur pour le porteur (DevilFruit.tickWeakness) :
 * <ul>
 *   <li><b>pieds dans l'eau</b> (isInWater) : toute transformation est annulée sur-le-champ, AbilityManager refuse toute
 *       attaque (nouvelle transformation comprise, message dans la barre d'action, cases grisées en bleu dans le HUD) et le passif
 *       Logia du feu ne protège plus ;</li>
 *   <li><b>entièrement immergé</b> (isUnderWater : la tête sous l'eau) : en plus, le joueur est aspiré vers le fond. Sa nage
 *       vers le haut est annulée et il coule à pic, comme une enclume (côté client, voir ClientWaterCurse).</li>
 * </ul>
 * Aucun effet de potion, aucun dégât : la noyade normale de Minecraft (barre d'air) s'applique comme pour tout joueur.
 * Une bulle d'air du Corail à Bulles (BubbleCoral) isole complètement de l'eau : voir {@link #touchesWater}.
 */
public class WaterCurse implements Weakness {

    /** Vitesse de chute dans l'eau (blocs/tick) : bien plus rapide qu'un joueur qui coule normalement (~0,02). */
    private static final double SINK_SPEED = 0.45;
    /** Les déplacements horizontaux sont presque impossibles : le corps est lourd et sans force. */
    private static final double HORIZONTAL_DRAG = 0.35;

    /**
     * L'eau touche-t-elle cette entité ? Pieds dans l'eau, SAUF si une bulle du Corail à Bulles l'en isole. C'est CE test (et non
     * isInWater) que tout le mod utilise pour la faiblesse à l'eau des fruits du démon : pouvoirs bloqués, détransformation,
     * passif Logia annulé, attaques interrompues. Valable côté serveur et côté client.
     */
    public static boolean touchesWater(Entity entity) {
        return entity.isInWater() && !BubbleCoral.isActive(entity);
    }

    @Override
    public void tick(ServerPlayer player) {
        if (!touchesWater(player) || player.isSpectator() || player.getAbilities().flying) {
            return;
        }
        // Contact avec l'eau : détransformation immédiate (la taille revient tout de suite à la normale)
        if (BuddhaTransformAbility.isActive(player)) {
            BuddhaTransformAbility.reset(player);
        }
        // Immergé : l'aspiration vers le fond est appliquée côté CLIENT (ClientWaterCurse), car c'est le client qui calcule les
        // déplacements de son propre joueur ; une vitesse imposée par le serveur serait aussitôt écrasée par sa nage.
    }

    /**
     * Vitesse d'un porteur entièrement sous l'eau : il coule à pic, sans pouvoir remonter (ni nage, ni saut), et se déplace à
     * peine à l'horizontale. Utilisé par ClientWaterCurse à chaque tick.
     */
    public static Vec3 sinkingVelocity(Vec3 v) {
        return new Vec3(v.x * HORIZONTAL_DRAG, Math.min(v.y, -SINK_SPEED), v.z * HORIZONTAL_DRAG);
    }
}
