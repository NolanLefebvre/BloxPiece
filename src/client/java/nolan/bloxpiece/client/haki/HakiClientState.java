package nolan.bloxpiece.client.haki;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.haki.ArmamentHaki;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Côté client : moments où chaque joueur visible a changé d'état de Haki (début du revêtement, propagation à tout le corps,
 * esquive), pour animer les transitions sans rien synchroniser de plus que l'état lui-même.
 */
public final class HakiClientState {

    /** Revêtement : mode affiché, début du bras, début du reste du corps (-1 = pas encore). */
    private record Armament(int mode, float armSince, float bodySince) {
    }

    /** Esquive : côté, et tick de jeu (client) du début. */
    private record Dodge(int variant, boolean right, long start) {
    }

    private static final Map<Integer, Armament> ARMAMENT = new HashMap<>();
    private static final Map<Integer, Dodge> DODGE = new HashMap<>();

    private HakiClientState() {
    }

    /** Durée de la transition « en fusion » puis noir métallique (ticks). */
    public static final float TRANSITION_TICKS = 16.0F;

    /**
     * Progression du revêtement (0 = rien, 1 = noir métallique figé) : [bras principal, reste du corps], ou null sans Haki.
     * {@code age} = âge en ticks de l'entité (avec fraction).
     */
    public static float[] armament(Entity entity, float age) {
        int mode = ArmamentHaki.mode(entity);
        if (mode == 0) {
            ARMAMENT.remove(entity.getId());
            return null;
        }
        Armament last = ARMAMENT.get(entity.getId());
        if (last == null) {
            last = new Armament(mode, age, mode == ArmamentHaki.FULL_BODY ? age : -1.0F);
            ARMAMENT.put(entity.getId(), last);
        } else if (last.mode() != mode) {
            // maintien de la touche : le bras reste couvert, le Haki se propage au reste du corps
            last = new Armament(mode, last.armSince(), mode == ArmamentHaki.FULL_BODY ? age : -1.0F);
            ARMAMENT.put(entity.getId(), last);
        }
        float arm = clamp((age - last.armSince()) / TRANSITION_TICKS);
        float body = last.bodySince() < 0.0F ? 0.0F : clamp((age - last.bodySince()) / (TRANSITION_TICKS * 1.4F));
        return new float[] {arm, body};
    }

    /**
     * Esquive en cours, ou null. {@code amount} 0..1 : déjà lissé (penché très vite, puis retour en douceur) ; {@code side} : -1 ou
     * 1 ; {@code variant} : type d'esquive (PlayerModelMixin applique la pose correspondante) ; {@code t} : progression 0..1.
     */
    public record DodgePose(int variant, float side, float amount, float t) {
    }

    public static DodgePose dodge(Entity entity, float partialTick) {
        Dodge last = DODGE.get(entity.getId());
        if (last == null) {
            return null;
        }
        float duration = DODGE_TICKS[Math.floorMod(last.variant(), DODGE_TICKS.length)];
        float t = ((entity.level().getGameTime() - last.start()) + partialTick) / duration;
        if (t >= 1.0F) {
            DODGE.remove(entity.getId());
            return null;
        }
        if (t < 0.0F) {
            return null;
        }
        float amount = t < 0.2F ? t / 0.2F : 1.0F - (t - 0.2F) / 0.8F;
        amount = amount * amount * (3.0F - 2.0F * amount);
        // côté en axe X du modèle : -1 = vers la droite du joueur (le bras droit est en x négatif), 1 = vers sa gauche
        return new DodgePose(last.variant(), last.right() ? -1.0F : 1.0F, amount, t);
    }

    /** Toutes les esquives en cours (identifiant d'entité), pour la traînée des yeux. */
    public static java.util.Set<Integer> dodging() {
        return DODGE.keySet();
    }

    /**
     * Déplacement de la TÊTE pendant l'esquive, dans le repère du modèle (pixels ; +x gauche du joueur, +y vers le bas, +z vers
     * l'arrière) et ses rotations ajoutées (radians) : {x, y, z, xRot, yRot, zRot}. Utilisé par PlayerModelMixin (pose) ET par
     * HakiEyeTrail (position des yeux), pour que la traînée suive exactement la tête.
     */
    public static float[] headDelta(DodgePose d) {
        float[] h = new float[6];
        float k = d.amount();
        float side = d.side();
        switch (d.variant()) {
            case 1 -> leanBack(h, k * 0.55F, side * k * 0.12F);
            case 2 -> h[4] += side * k * 0.85F * 1.3F;
            case 3 -> {
                float a = side * k;
                h[0] += 2.6F * a;
                h[1] += 0.8F * k;
                h[5] += 0.55F * a;
                h[4] += 0.35F * a;
            }
            case 4 -> {
                leanBack(h, -0.55F * k, side * 0.15F * k);
                h[3] += 0.5F * k; // tête baissée : on plonge sous le coup
            }
            case 5 -> {
                leanBack(h, k * 0.8F, 0.0F);
                h[4] += side * k * 0.5F * 1.3F;
            }
            default -> {
                float a = side * k * 0.42F;
                h[0] += 12.0F * (float) Math.sin(a);
                h[5] += a * 1.5F;
            }
        }
        return h;
    }

    /** Tête qui suit un buste penché en arrière (angle > 0) ou en avant (angle < 0), pivot aux hanches. */
    private static void leanBack(float[] h, float angle, float roll) {
        h[2] += 12.0F * (float) Math.sin(angle);
        h[1] += 12.0F * (1.0F - (float) Math.cos(angle));
        h[3] -= angle * 0.6F;
        h[5] += roll * 1.5F;
    }

    /** Durée de chaque type d'esquive (ticks) : latérale, recul, torsion, tête, plongeon, matrice. */
    private static final float[] DODGE_TICKS = {9.0F, 11.0F, 8.0F, 7.0F, 10.0F, 13.0F};

    /** Paquet HakiDodgePayload reçu : l'animation démarre maintenant. */
    public static void startDodge(int entityId, int variant, boolean right) {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) {
            DODGE.put(entityId, new Dodge(variant, right, level.getGameTime()));
        }
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    public static void clear() {
        ARMAMENT.clear();
        DODGE.clear();
    }
}
