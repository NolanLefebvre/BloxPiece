package nolan.bloxpiece.client.cyborg;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.ability.Cyborg;
import nolan.bloxpiece.race.Races;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Lecture côté client de l'état Cyborg d'un joueur (attachments synchronisés par le serveur : RACE, CYBORG_ACTION,
 * CYBORG_OVERDRIVE) : pour le bras mécanique, ses transformations, les poses et l'aura. Purement visuel.
 */
public final class CyborgClient {

    private CyborgClient() {
    }

    /** Ce joueur est-il Cyborg (bras mécanique à afficher) ? */
    public static boolean isCyborg(Entity entity) {
        return entity instanceof Player player && Races.of(player) == Races.CYBORG;
    }

    /** Type de l'attaque en cours (Cyborg.CANNON...), ou 0. */
    public static int kind(Entity entity) {
        Long action = entity.getAttached(ModAttachments.CYBORG_ACTION);
        return action == null ? 0 : (int) (action % 8);
    }

    /** Ticks écoulés depuis le début de l'attaque en cours (fractionnaires), ou -1. */
    public static float elapsed(Entity entity, float ageInTicks) {
        Long action = entity.getAttached(ModAttachments.CYBORG_ACTION);
        if (action == null || Minecraft.getInstance().level == null) {
            return -1.0F;
        }
        float now = Minecraft.getInstance().level.getGameTime() + (ageInTicks - entity.tickCount);
        return Math.max(0.0F, now - action / 8);
    }

    /** Surcharge moteur : ticks depuis son début (fractionnaires), ou -1 si elle n'est pas active. */
    public static float overdrive(Entity entity, float ageInTicks) {
        Long start = entity.getAttached(ModAttachments.CYBORG_OVERDRIVE);
        if (start == null || Minecraft.getInstance().level == null) {
            return -1.0F;
        }
        float now = Minecraft.getInstance().level.getGameTime() + (ageInTicks - entity.tickCount);
        return Math.max(0.0F, now - start);
    }

    /** Montée douce de 0 à 1 entre {@code a} et {@code b}. */
    public static float ramp(float t, float a, float b) {
        float k = Mth.clamp((t - a) / Math.max(1.0E-3F, b - a), 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    /** Puissance du moteur (0 à 1) : montée pendant le démarrage de la surcharge, pleine pendant la surcharge. */
    public static float engine(Entity entity, float ageInTicks) {
        if (overdrive(entity, ageInTicks) >= 0.0F) {
            return 1.0F;
        }
        if (kind(entity) == Cyborg.OVERDRIVE) {
            return ramp(elapsed(entity, ageInTicks), 0.0F, Cyborg.OVERDRIVE_WINDUP);
        }
        return 0.0F;
    }
}
