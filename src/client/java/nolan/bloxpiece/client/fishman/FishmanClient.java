package nolan.bloxpiece.client.fishman;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.race.Races;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Lecture côté client de l'état Fishman d'un joueur (attachments synchronisés : RACE, FISHMAN_ACTION) : ailerons, poses et
 * effets d'eau sur les bras. Purement visuel.
 */
public final class FishmanClient {

    private FishmanClient() {
    }

    public static boolean isFishman(Entity entity) {
        return entity instanceof Player player && Races.of(player) == Races.FISHMAN;
    }

    private static float now(Entity entity, float ageInTicks) {
        return Minecraft.getInstance().level == null ? 0.0F : Minecraft.getInstance().level.getGameTime() + (ageInTicks - entity.tickCount);
    }

    /** Type de l'attaque en cours (Fishman.WAVE...), ou 0. */
    public static int kind(Entity entity) {
        Long action = entity.getAttached(ModAttachments.FISHMAN_ACTION);
        return action == null ? 0 : (int) (action % 8);
    }

    /** Ticks écoulés depuis le début de l'attaque en cours, ou -1. */
    public static float elapsed(Entity entity, float ageInTicks) {
        Long action = entity.getAttached(ModAttachments.FISHMAN_ACTION);
        return action == null || Minecraft.getInstance().level == null ? -1.0F : Math.max(0.0F, now(entity, ageInTicks) - action / 8);
    }

    /** Transition douce de 0 (avant {@code a}) à 1 (après {@code b}). */
    public static float ramp(float t, float a, float b) {
        float k = Mth.clamp((t - a) / Math.max(1.0E-3F, b - a), 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    /** Transition sèche (frappe) : très rapide au début. */
    public static float snap(float t, float a, float b) {
        float k = Mth.clamp((t - a) / Math.max(1.0E-3F, b - a), 0.0F, 1.0F);
        return 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
    }
}
