package nolan.bloxpiece.client.minks;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.ability.Minks;
import nolan.bloxpiece.ability.MinksBarrageAbility;
import nolan.bloxpiece.ability.MinksClawsAbility;
import nolan.bloxpiece.ability.MinksDanceAbility;
import nolan.bloxpiece.race.Races;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Lecture côté client de l'état Minks d'un joueur (attachments synchronisés par le serveur : RACE, MINKS_ACTION, MINKS_CLAWS,
 * MINKS_SULONG) : apparence (oreilles, queue, Sulong), griffes, poses et effets. Purement visuel.
 */
public final class MinksClient {

    private MinksClient() {
    }

    public static boolean isMinks(Entity entity) {
        return entity instanceof Player player && Races.of(player) == Races.MINKS;
    }

    private static float now(Entity entity, float ageInTicks) {
        return Minecraft.getInstance().level == null ? 0.0F : Minecraft.getInstance().level.getGameTime() + (ageInTicks - entity.tickCount);
    }

    /** Type de l'attaque en cours (Minks.CLAWS...), ou 0. */
    public static int kind(Entity entity) {
        Long action = entity.getAttached(ModAttachments.MINKS_ACTION);
        return action == null ? 0 : (int) (action % 8);
    }

    /** Ticks écoulés depuis le début de l'attaque en cours, ou -1. */
    public static float elapsed(Entity entity, float ageInTicks) {
        Long action = entity.getAttached(ModAttachments.MINKS_ACTION);
        return action == null || Minecraft.getInstance().level == null ? -1.0F : Math.max(0.0F, now(entity, ageInTicks) - action / 8);
    }

    public static float ramp(float t, float a, float b) {
        float k = Mth.clamp((t - a) / Math.max(1.0E-3F, b - a), 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    public static float snap(float t, float a, float b) {
        float k = Mth.clamp((t - a) / Math.max(1.0E-3F, b - a), 0.0F, 1.0F);
        return 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
    }

    /** Forme Sulong (0 à 1) : apparition en 10 ticks après la transformation, disparition sur les 20 derniers. */
    public static float sulong(Entity entity, float ageInTicks) {
        Long start = entity.getAttached(ModAttachments.MINKS_SULONG);
        if (start == null) {
            return 0.0F;
        }
        float since = now(entity, ageInTicks) - start;
        return ramp(since, 0.0F, 10.0F) * Mth.clamp((Minks.SULONG_TICKS - since) / 20.0F, 0.0F, 1.0F);
    }

    /** Griffes sorties (0 à 1) : jaillissent pendant l'animation des Griffes, s'effacent sur les 10 derniers ticks. */
    public static float claws(Entity entity, float ageInTicks) {
        float grow = 1.0F;
        if (kind(entity) == Minks.CLAWS) {
            float half = MinksClawsAbility.ACTIVATE / 2.0F;
            grow = snap(elapsed(entity, ageInTicks), half - 1.0F, half + 2.0F);
        }
        Long until = entity.getAttached(ModAttachments.MINKS_CLAWS);
        if (until == null) {
            return kind(entity) == Minks.CLAWS ? grow : 0.0F;
        }
        return grow * Mth.clamp((until - now(entity, ageInTicks)) / 10.0F, 0.0F, 1.0F);
    }

    /**
     * Danse : mouvement en cours {type (MinksDanceAbility.FRONT...), ticks depuis son début}, ou null (synchronisé par le
     * serveur : MINKS_MOVE).
     */
    public static float[] move(Entity entity, float ageInTicks) {
        Long m = entity.getAttached(ModAttachments.MINKS_MOVE);
        return m == null || Minecraft.getInstance().level == null ? null : new float[] {(float) (m % 8), Math.max(0.0F, now(entity, ageInTicks) - m / 8)};
    }

    public static boolean barrage(int kind) {
        return kind == Minks.BARRAGE || kind == Minks.BARRAGE_S;
    }

    public static boolean dance(int kind) {
        return kind == Minks.DANCE || kind == Minks.DANCE_S;
    }

    /** Nombre de coups du barrage selon la version (Sulong : plus). */
    public static int punches(int kind) {
        return kind == Minks.BARRAGE_S ? MinksBarrageAbility.PUNCHES_SULONG : MinksBarrageAbility.PUNCHES;
    }

    /** Durée d'un bond de la Danse et nombre de bonds selon la version. */
    public static int step(int kind) {
        return kind == Minks.DANCE_S ? MinksDanceAbility.STEP_SULONG : MinksDanceAbility.STEP;
    }

    public static int steps(int kind) {
        return kind == Minks.DANCE_S ? MinksDanceAbility.STEPS_SULONG : MinksDanceAbility.STEPS;
    }

    /**
     * Barrage : extension d'UN bras à l'instant {@code e} : 1 = tendu (coup porté), 0 = armé en arrière, négatif = armé très
     * loin en arrière (coup final). Chaque bras fait un vrai cycle de 2 intervalles (armé, projeté devant, retour en arrière),
     * décalé d'un intervalle avec l'autre bras : pendant qu'un bras frappe, l'autre recule. Le bras est tendu EXACTEMENT au tick
     * où le serveur porte son coup (coup n° j au tick GUARD + j x INTERVAL : impairs = bras droit, pairs = bras gauche).
     */
    public static float armExt(int kind, float e, boolean right) {
        int n = punches(kind);
        float g = MinksBarrageAbility.GUARD, iv = MinksBarrageAbility.INTERVAL;
        float fin = MinksBarrageAbility.finalTick(n);
        float wind = MinksBarrageAbility.FINAL_WINDUP;
        if (e >= fin - wind - 1.0F) {
            // coup final, du bras droit : armé très loin, puis lancé de tout le corps ; le bras gauche se ramène en garde
            if (!right) {
                return 0.35F * (1.0F - ramp(e, fin + 2.0F, fin + 7.0F));
            }
            if (e < fin - 0.6F) {
                return -ramp(e, fin - wind - 1.0F, fin - 0.6F);
            }
            return snap(e, fin - 0.6F, fin) * (1.0F - ramp(e, fin + 3.0F, fin + 8.0F));
        }
        float first = g + (right ? 1.0F : 2.0F) * iv;      // premier coup de ce bras
        float last = g + (right ? (n % 2 == 1 ? n : n - 1) : (n % 2 == 0 ? n : n - 1)) * iv; // dernier coup de ce bras
        float thrust = 1.2F, hold = 0.35F, period = 2.0F * iv;
        if (e < first - thrust) {
            return 0.0F; // armé en arrière (le passage de la garde à l'armé : voir la pose)
        }
        float d = e - first;
        float local = ((d + thrust) % period + period) % period - thrust; // position dans le cycle, 0 = coup porté
        float peak = e - local;
        if (peak > last + 0.01F) {
            return local < 0.0F ? 0.0F : 1.0F - ramp(local, hold, period - thrust); // plus de coup pour ce bras : il revient
        }
        if (local < 0.0F) {
            return snap(local, -thrust, 0.0F);                 // projeté devant
        }
        if (local < hold) {
            return 1.0F;                                        // tendu : le coup porte
        }
        return 1.0F - ramp(local, hold, period - thrust);      // ramené en arrière
    }

    /** Barrage : le bras le plus tendu et son extension {bras (1 droit, -1 gauche, 0 aucun), extension} (traînées). */
    public static float[] punch(int kind, float e) {
        float r = armExt(kind, e, true), l = armExt(kind, e, false);
        if (Math.max(r, l) <= 0.0F) {
            return new float[] {0.0F, 0.0F};
        }
        return r >= l ? new float[] {1.0F, r} : new float[] {-1.0F, l};
    }
}
