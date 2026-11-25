package nolan.bloxpiece.client.human;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.race.Races;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Lecture côté client de l'état des attaques Humaines d'un joueur (attachments synchronisés par le serveur : RACE,
 * HUMAN_ACTION) : pour les poses, l'aura du poing, les traînées. Purement visuel.
 */
public final class HumanClient {

    private HumanClient() {
    }

    public static boolean isHuman(Entity entity) {
        return entity instanceof Player player && Races.of(player) == Races.HUMAN;
    }

    /** Type de l'attaque en cours (Human.IMPACT...), ou 0. */
    public static int kind(Entity entity) {
        Long action = entity.getAttached(ModAttachments.HUMAN_ACTION);
        return action == null ? 0 : (int) (action % 8);
    }

    /** Ticks écoulés depuis le début de la phase en cours (fractionnaires), ou -1. */
    public static float elapsed(Entity entity, float ageInTicks) {
        Long action = entity.getAttached(ModAttachments.HUMAN_ACTION);
        if (action == null || Minecraft.getInstance().level == null) {
            return -1.0F;
        }
        float now = Minecraft.getInstance().level.getGameTime() + (ageInTicks - entity.tickCount);
        return Math.max(0.0F, now - action / 8);
    }

    /** Montée douce (0 à 1) entre {@code a} et {@code b}. */
    public static float ramp(float t, float a, float b) {
        float k = Mth.clamp((t - a) / Math.max(1.0E-3F, b - a), 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    /** Montée rapide puis amortie (un coup qui part) : 0 à 1 entre {@code a} et {@code b}. */
    public static float snap(float t, float a, float b) {
        float k = Mth.clamp((t - a) / Math.max(1.0E-3F, b - a), 0.0F, 1.0F);
        return 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
    }
}
