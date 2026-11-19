package nolan.bloxpiece.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import nolan.bloxpiece.fx.AceSlash;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Où en est le geste de la Lame dévastatrice (épée Ace), pour la troisième personne (PlayerModelMixin) et la première personne
 * (AceSlashFirstPersonMixin) : les deux lisent les mêmes attachments synchronisés (ACE_CHARGE, ACE_SLASH) et les mêmes durées
 * (AceSlash), donc bras, épée et départ de la lame restent synchronisés.
 * <p>
 * Le geste passe d'une pose clé à une autre : {@link #REST} (tenue normale), {@link #RAISED} (épée levée en haut à droite,
 * tenue pendant toute la charge), {@link #STRUCK} (fin de la frappe, en bas à gauche : la lame part à cet instant).
 */
public final class AceSwing {

    public static final int REST = 0, RAISED = 1, STRUCK = 2;

    /** De la pose {@code from} vers la pose {@code to}, avancée {@code k} (0 à 1, déjà lissée). */
    public record Step(int from, int to, float k) {
    }

    private AceSwing() {
    }

    /** Deuxième diagonale (haut gauche -> bas droite) choisie pour ce coup ? */
    public static boolean reverse(Entity entity) {
        return Boolean.TRUE.equals(entity.getAttached(ModAttachments.ACE_REVERSE));
    }

    /** Le geste en cours, ou null (pose normale de Minecraft). {@code now} = temps de jeu + fraction de tick. */
    public static Step at(Entity entity, float now) {
        Integer strike = entity.getAttached(ModAttachments.ACE_SLASH);
        if (strike != null) {
            float t = now - strike;
            if (t < 0.0F && t > -AceSlash.RAISE_TICKS - 1.0F) {
                return new Step(RAISED, RAISED, 1.0F); // relâché tôt : la frappe attend que l'épée soit levée
            }
            if (t >= 0.0F && t < AceSlash.STRIKE_TICKS) {
                return new Step(RAISED, STRUCK, smooth(t / AceSlash.STRIKE_TICKS));
            }
            if (t >= AceSlash.STRIKE_TICKS && t < AceSlash.STRIKE_TICKS + AceSlash.RECOVER_TICKS) {
                return new Step(STRUCK, REST, smooth((t - AceSlash.STRIKE_TICKS) / AceSlash.RECOVER_TICKS));
            }
        }
        Integer charge = entity.getAttached(ModAttachments.ACE_CHARGE);
        if (charge != null && entity instanceof LivingEntity living && living.isUsingItem()) {
            return new Step(REST, RAISED, smooth((now - charge) / AceSlash.RAISE_TICKS)); // levée, puis tenue pendant la charge
        }
        return null;
    }

    private static float smooth(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }
}
