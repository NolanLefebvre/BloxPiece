package nolan.bloxpiece.client.lunarian;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.ability.Lunarian;
import nolan.bloxpiece.ability.LunarianFeathersAbility;
import nolan.bloxpiece.ability.LunarianFistAbility;
import nolan.bloxpiece.ability.LunarianFlamesAbility;
import nolan.bloxpiece.ability.LunarianJudgmentAbility;
import nolan.bloxpiece.race.Races;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Lecture côté client de l'état Lunarian d'un joueur (attachments synchronisés : RACE, LUNARIAN_ACTION, LUNARIAN_FLAME) et
 * ANIMATION DES AILES et de l'inclinaison du corps, lissées image par image pour chaque joueur :
 * <ul>
 *   <li>au repos, ailes repliées qui « respirent » ; en marchant, elles se balancent avec le pas ; en courant, elles se
 *       plaquent ; en l'air, elles s'ouvrent et BATTENT (fort en montée, plané en descente) ;</li>
 *   <li>pendant les attaques, chaque capacité commande l'ouverture, l'amplitude et la cadence des battements et le balayage
 *       vers l'arrière (dash, plongée) ; tout passe en douceur d'un état à l'autre (aucun saut).</li>
 * </ul>
 * Purement visuel : tous les joueurs voient la même chose (mêmes données synchronisées).
 */
public final class LunarianClient {

    private LunarianClient() {
    }

    public static boolean isLunarian(Entity entity) {
        return entity instanceof Player player && Races.of(player) == Races.LUNARIAN;
    }

    private static float now(Entity entity, float ageInTicks) {
        return Minecraft.getInstance().level == null ? 0.0F : Minecraft.getInstance().level.getGameTime() + (ageInTicks - entity.tickCount);
    }

    public static int kind(Entity entity) {
        Long action = entity.getAttached(ModAttachments.LUNARIAN_ACTION);
        return action == null ? 0 : (int) (action % 8);
    }

    public static float elapsed(Entity entity, float ageInTicks) {
        Long action = entity.getAttached(ModAttachments.LUNARIAN_ACTION);
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

    /** Flammes du dos (0 : éteintes, 1 : pleines) : elles grandissent ou s'éteignent pendant Lunarian.TRANSITION ticks. */
    public static float flames(Entity entity, float ageInTicks) {
        Long v = entity.getAttached(ModAttachments.LUNARIAN_FLAME);
        if (v == null) {
            return 0.0F;
        }
        float since = now(entity, ageInTicks) - (v >> 1);
        return (v & 1L) == 1L ? ramp(since, 0.0F, Lunarian.TRANSITION) : 1.0F - ramp(since, 0.0F, Lunarian.TRANSITION);
    }

    /** Ticks depuis le dernier changement de mode (allumage ou extinction), et vrai si allumage ; null si jamais changé. */
    public static float[] flameChange(Entity entity, float ageInTicks) {
        Long v = entity.getAttached(ModAttachments.LUNARIAN_FLAME);
        return v == null ? null : new float[] {now(entity, ageInTicks) - (v >> 1), (v & 1L) == 1L ? 1.0F : 0.0F};
    }

    /** Feu du poing droit (0 à 1) : Poing embrasé (armé, dash) et Tempête (préparation, envol, plongée, impact). */
    public static float fist(Entity entity, float age) {
        int kind = kind(entity);
        float e = elapsed(entity, age);
        if (e < 0.0F) {
            return 0.0F;
        }
        return switch (kind) {
            case Lunarian.FIST -> ramp(e, 0.0F, LunarianFistAbility.WINDUP);
            case Lunarian.FIST_END -> 1.0F - ramp(e, 0.0F, LunarianFistAbility.RECOVER * 0.7F);
            case Lunarian.JUDGMENT -> 0.35F + 0.65F * ramp(e, 0.0F, LunarianJudgmentAbility.PREP);
            case Lunarian.JUDGMENT_END -> 1.0F - ramp(e, 2.0F, LunarianJudgmentAbility.RECOVER * 0.8F);
            default -> 0.0F;
        };
    }

    /** Phase de la Tempête déduite du mouvement (synchronisé) : 0 préparation, 1 ascension, 2 surplace, 3 plongée. */
    public static int judgmentPhase(Entity entity, float e) {
        if (e < LunarianJudgmentAbility.PREP) {
            return 0;
        }
        double vy = entity.getY() - entity.yo;
        return vy > 0.12 ? 1 : vy < -0.4 ? 3 : 2;
    }

    // ------------------------------------------------------------------ animation lissée

    /** État d'animation lissé d'un joueur : ouverture, amplitude et phase des battements, balayage, inclinaison du corps. */
    public static final class Anim {
        public float open, amp, phase, sweep, pitch, lastAge = Float.NaN;
    }

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();

    /** Cibles {ouverture, amplitude (degrés), cadence (radians/tick), balayage, inclinaison du corps (degrés)}. */
    private static float[] targets(Entity entity, float age, float walk) {
        int kind = kind(entity);
        float e = elapsed(entity, age);
        boolean ground = entity.onGround();
        double vy = entity.getY() - entity.yo;
        // déplacements ordinaires
        float open = ground ? (entity.isSprinting() ? 0.0F : 0.05F + 0.1F * walk) : (vy > 0.05 ? 0.95F : 0.8F);
        float amp = ground ? 0.0F : (vy > 0.05 ? 42.0F : 12.0F), speed = ground ? 0.2F : (vy > 0.05 ? 0.7F : 0.28F);
        float sweep = entity.isSprinting() && ground ? 0.25F : 0.0F, pitch = 0.0F;
        if (e >= 0.0F) {
            switch (kind) {
                case Lunarian.FLAMES -> { // un ample mouvement d'ailes qui accompagne l'apparition des flammes
                    float b = ramp(e, 0.0F, 6.0F) * (1.0F - ramp(e, 12.0F, LunarianFlamesAbility.DURATION));
                    open = Math.max(open, 0.8F * b);
                    amp = 22.0F * b;
                    speed = 0.45F;
                }
                case Lunarian.FEATHERS -> {
                    float d = LunarianFeathersAbility.DEPLOY;
                    float last = LunarianFeathersAbility.FIRST + LunarianFeathersAbility.EVERY * LunarianFeathersAbility.COUNT;
                    float w = snap(e, 0.0F, d) * (1.0F - ramp(e, last + 4.0F, LunarianFeathersAbility.DURATION));
                    open = Math.max(open, w);
                    // préparation : les ailes s'arment vers l'arrière ; tir : un coup d'aile à chaque plume
                    sweep = 0.35F * ramp(e, d, LunarianFeathersAbility.FIRST - 1.0F) * (1.0F - ramp(e, LunarianFeathersAbility.FIRST - 1.0F,
                            LunarianFeathersAbility.FIRST + 1.0F));
                    boolean firing = e >= LunarianFeathersAbility.FIRST - 1.0F && e < last + 1.0F;
                    amp = firing ? 20.0F : 6.0F * w;
                    speed = firing ? (float) Math.PI / LunarianFeathersAbility.EVERY : 0.3F;
                }
                case Lunarian.FIST -> {
                    float wu = LunarianFistAbility.WINDUP;
                    open = Math.max(open, snap(e, 0.0F, wu * 0.6F));
                    amp = e < wu ? 8.0F : 4.0F;
                    speed = 0.4F;
                    sweep = ramp(e, wu - 2.0F, wu); // pendant le dash : ailes balayées vers l'arrière
                    pitch = sweep;                  // le corps fonce (angle complet : LunarianRotation)
                }
                case Lunarian.FIST_END -> {
                    float k = ramp(e, 0.0F, LunarianFistAbility.RECOVER);
                    open = Math.max(open, 1.0F - k);
                    sweep = 1.0F - snap(e, 0.0F, 5.0F);
                    pitch = sweep;
                    amp = 6.0F * (1.0F - k);
                }
                case Lunarian.JUDGMENT -> {
                    int ph = judgmentPhase(entity, e);
                    float p = LunarianJudgmentAbility.PREP;
                    if (ph == 0) { // les ailes se déploient, s'arment, puis un grand battement au décollage
                        open = Math.max(open, snap(e, 2.0F, p - 6.0F));
                        sweep = 0.4F * ramp(e, p - 8.0F, p - 1.0F);
                        amp = e > p - 2.0F ? 55.0F : 4.0F;
                        speed = 0.6F;
                    } else if (ph == 1) { // ascension : battements puissants
                        open = 1.0F;
                        amp = 48.0F;
                        speed = 0.75F;
                    } else if (ph == 2) { // surplace : il bascule, ailes grandes ouvertes
                        open = 1.0F;
                        amp = 14.0F;
                        speed = 0.35F;
                        pitch = 1.0F;
                    } else { // plongée : ailes repliées en flèche
                        open = 0.6F;
                        sweep = 1.0F;
                        amp = 3.0F;
                        speed = 0.6F;
                        pitch = 1.0F;
                    }
                }
                case Lunarian.JUDGMENT_END -> { // impact : ailes jetées grandes ouvertes, puis repli lent
                    float k = ramp(e, 6.0F, LunarianJudgmentAbility.RECOVER);
                    open = Math.max(open, 1.0F - k);
                    amp = 10.0F * (1.0F - k);
                    speed = 0.3F;
                }
                default -> {
                }
            }
        }
        return new float[] {open, amp, speed, sweep, pitch};
    }

    /** Animation lissée du joueur à cette image (mise à jour une fois par instant : appels multiples sans effet). */
    public static Anim anim(Entity entity, float age, float walk) {
        Anim a = ANIMS.computeIfAbsent(entity.getId(), id -> new Anim());
        if (ANIMS.size() > 256) {
            ANIMS.clear(); // (joueurs partis : on repart de zéro)
            ANIMS.put(entity.getId(), a);
        }
        float dt = Float.isNaN(a.lastAge) ? 0.0F : Mth.clamp(age - a.lastAge, 0.0F, 2.0F);
        if (Float.isNaN(a.lastAge) || age < a.lastAge - 40.0F) {
            dt = 0.0F;
        }
        a.lastAge = age;
        if (dt <= 0.0F) {
            return a;
        }
        float[] t = targets(entity, age, walk);
        float f = 1.0F - (float) Math.exp(-dt * 0.4F), g = 1.0F - (float) Math.exp(-dt * 0.25F);
        a.open += (t[0] - a.open) * f;
        a.amp += (t[1] - a.amp) * g;
        a.phase += dt * t[2];
        a.sweep += (t[3] - a.sweep) * f;
        a.pitch += (t[4] - a.pitch) * (1.0F - (float) Math.exp(-dt * 0.3F));
        return a;
    }
}
