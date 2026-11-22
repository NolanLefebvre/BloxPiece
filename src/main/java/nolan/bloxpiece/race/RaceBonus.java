package nolan.bloxpiece.race;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Un bonus passif de race. Deux sortes :
 * <ul>
 *   <li>bonus d'ATTRIBUT ({@link #attribute}) : un modificateur d'attribut propre aux races (identifiant
 *       « bloxpiece:race/&lt;key&gt; » : deux races qui touchent le même attribut doivent utiliser la même clé), qui s'AJOUTE aux autres (armes, Haki, fruits, objets, niveau...) sans jamais les
 *       toucher ; posé et retiré par RaceManager ;</li>
 *   <li>bonus SPÉCIAL ({@link Kind} autre que ATTRIBUTE) : une règle appliquée par RaceManager (respiration sous l'eau...).</li>
 * </ul>
 * Le texte du menu est construit à partir de {@code amount} : la valeur affichée est TOUJOURS la valeur appliquée.
 * Clé de texte : « race.bloxpiece.bonus.&lt;key&gt; », avec la valeur en paramètre (%s).
 */
public record RaceBonus(Kind kind, String key, Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount,
                        double displayFactor) {

    public enum Kind {
        /** Modificateur d'attribut. */
        ATTRIBUTE,
        /** Respire sous l'eau (l'air ne descend jamais). */
        WATER_BREATHING,
        /** En feu : armure supplémentaire de {@code amount} (Lunarian ; future mécanique de flammes : RaceManager.flamesActive). */
        FLAME_GUARD,
        /** Effets négatifs (poison, faim) qui s'écoulent {@code amount} fois plus vite (Cyborg). */
        EFFECT_RESIST
    }

    /** Bonus d'attribut. {@code amount} : valeur du modificateur (ex : 0.05 = +5 % pour une opération multiplicative). */
    public static RaceBonus attribute(String key, Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount) {
        boolean percent = operation != AttributeModifier.Operation.ADD_VALUE;
        return new RaceBonus(Kind.ATTRIBUTE, key, attribute, operation, amount, percent ? 100.0 : 1.0);
    }

    /** Bonus d'attribut affiché multiplié par {@code displayFactor} (ex : 100 pour afficher 0,1 comme « 10 % »). */
    public static RaceBonus attribute(String key, Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount,
                                      double displayFactor) {
        return new RaceBonus(Kind.ATTRIBUTE, key, attribute, operation, amount, displayFactor);
    }

    public static RaceBonus special(Kind kind, String key, double amount) {
        return new RaceBonus(kind, key, null, null, amount, 1.0);
    }

    /** Valeur à afficher (valeur appliquée x displayFactor : pourcentage pour les bonus en %), sans signe ni zéros inutiles. */
    public String displayValue() {
        double v = amount * displayFactor;
        double abs = Math.round(Math.abs(v) * 10.0) / 10.0; // 0,05 x 100 = 5,000000000000001 -> 5
        String text = abs == Math.rint(abs) ? Long.toString(Math.round(abs)) : String.valueOf(abs);
        return text;
    }

    /** Ligne du menu Race (texte traduit, avec la valeur exacte). */
    public Component describe() {
        return Component.translatable("race.bloxpiece.bonus." + key, displayValue());
    }
}
