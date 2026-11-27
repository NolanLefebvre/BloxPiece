package nolan.bloxpiece.client.lunarian;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import nolan.bloxpiece.ability.Lunarian;
import nolan.bloxpiece.ability.LunarianFeathersAbility;
import nolan.bloxpiece.ability.LunarianFistAbility;
import nolan.bloxpiece.ability.LunarianFlamesAbility;
import nolan.bloxpiece.ability.LunarianJudgmentAbility;

import static nolan.bloxpiece.client.lunarian.LunarianFire.ORANGE;
import static nolan.bloxpiece.client.lunarian.LunarianFire.YELLOW;
import static nolan.bloxpiece.client.lunarian.LunarianFire.argb;

/**
 * Race Lunarian à la PREMIÈRE PERSONNE (même principe que Cyborg, Humain, Minks et Fishman) :
 * <ul>
 *   <li>les deux mains suivent les capacités, aux mêmes instants que la pose vue de l'extérieur (LunarianPose) ; l'autre main
 *       est ajoutée pendant les capacités (CyborgFirstPersonMixin) ; le feu du poing est sur la main (LunarianLayer.submitHand) ;</li>
 *       côtés de l'écran, avec le même battement que vu de l'extérieur ;</li>
 *   <li>les FLAMMES : pendant l'allumage et l'extinction, des langues de feu et des braises montent depuis le bas de l'écran
 *       (de la fumée à l'extinction) : le joueur voit la transition sans que la vue soit masquée.</li>
 * </ul>
 * La caméra de la Tempête (vue à la troisième personne pendant l'envol et la plongée) : {@link #tickCamera}.
 */
public final class LunarianFirstPerson {

    private static final float HAND_X = 0.56F, HAND_Y = -0.52F, HAND_Z = -0.72F;

    private LunarianFirstPerson() {
    }

    private record Key(float x, float y, float z, float rx, float ry, float rz, float w) {
        static final Key REST = new Key(0, 0, 0, 0, 0, 0, 0);

        Key lerp(Key b, float k) {
            return new Key(Mth.lerp(k, x, b.x), Mth.lerp(k, y, b.y), Mth.lerp(k, z, b.z), Mth.lerp(k, rx, b.rx), Mth.lerp(k, ry, b.ry),
                    Mth.lerp(k, rz, b.rz), Mth.lerp(k, w, b.w));
        }

        Key shake(float amount, float age) {
            return new Key(x + Mth.sin(age * 3.9F) * amount, y + Mth.cos(age * 3.3F) * amount, z, rx, ry, rz, w);
        }
    }

    private static Key key(LocalPlayer player, int kind, float e, float age, boolean right) {
        switch (kind) {
            case Lunarian.FLAMES -> { // les mains s'écartent, poitrine ouverte, pendant que les flammes naissent
                float w = LunarianClient.ramp(e, 0.0F, 5.0F) * (1.0F - LunarianClient.ramp(e, 13.0F, LunarianFlamesAbility.DURATION));
                return Key.REST.lerp(new Key(0.22F, -0.12F, 0.12F, 18.0F, -20.0F, 25.0F, 1.0F), w);
            }
            case Lunarian.FEATHERS -> {
                float first = LunarianFeathersAbility.FIRST, last = first + LunarianFeathersAbility.EVERY * LunarianFeathersAbility.COUNT;
                float w = LunarianClient.ramp(e, 0.0F, 4.0F) * (1.0F - LunarianClient.ramp(e, last + 4.0F, LunarianFeathersAbility.DURATION));
                Key open = new Key(0.25F, -0.1F, 0.15F, 12.0F, -25.0F, 30.0F, 1.0F);
                Key thrust = new Key(-0.22F, 0.1F, -0.55F, -12.0F, 14.0F, 0.0F, 1.0F);
                float t = 0.0F;
                if (e >= first - 1.0F) {
                    int i = Math.max(0, (int) ((e - first + 1.0F) / LunarianFeathersAbility.EVERY));
                    float local = e - (first + i * LunarianFeathersAbility.EVERY);
                    if (i < LunarianFeathersAbility.COUNT && (i % 2 == 0) == right) {
                        t = LunarianClient.snap(local, -1.0F, 0.3F) * (1.0F - LunarianClient.ramp(local, 0.6F, 1.8F));
                    }
                }
                return Key.REST.lerp(open.lerp(thrust, t), w);
            }
            case Lunarian.FIST -> {
                float wu = LunarianFistAbility.WINDUP;
                float w = LunarianClient.ramp(e, 0.0F, 3.0F);
                if (right) {
                    Key cock = new Key(0.16F, -0.26F, 0.3F, 24.0F, -12.0F, 10.0F, 1.0F).shake(0.01F * LunarianClient.ramp(e, 3.0F, wu), age);
                    Key out = new Key(-0.3F, 0.12F, -0.7F, -12.0F, 16.0F, 0.0F, 1.0F);
                    return Key.REST.lerp(cock.lerp(out, LunarianClient.snap(e, wu - 1.5F, wu)), w);
                }
                Key guard = new Key(-0.18F, 0.08F, -0.12F, -10.0F, 15.0F, -10.0F, 1.0F);
                Key back = new Key(0.15F, -0.3F, 0.25F, 20.0F, 0.0F, 0.0F, 1.0F);
                return Key.REST.lerp(guard.lerp(back, LunarianClient.snap(e, wu - 1.5F, wu)), w);
            }
            case Lunarian.FIST_END -> {
                float w = 1.0F - LunarianClient.ramp(e, 2.0F, LunarianFistAbility.RECOVER);
                return Key.REST.lerp(right ? new Key(-0.3F, 0.12F, -0.7F, -12.0F, 16.0F, 0.0F, 1.0F) : new Key(0.15F, -0.3F, 0.25F, 20.0F, 0.0F, 0.0F, 1.0F), w);
            }
            case Lunarian.JUDGMENT -> {
                int ph = LunarianClient.judgmentPhase(player, e);
                float w = LunarianClient.ramp(e, 0.0F, 4.0F);
                if (ph == 0) {
                    Key low = right ? new Key(0.12F, -0.3F, 0.3F, 26.0F, -12.0F, 10.0F, 1.0F) : new Key(-0.15F, -0.02F, -0.1F, -5.0F, 15.0F, -8.0F, 1.0F);
                    return Key.REST.lerp(low.shake(0.015F * LunarianClient.ramp(e, 6.0F, LunarianJudgmentAbility.PREP), age + (right ? 0 : 1.7F)), w);
                }
                if (ph == 1) { // envol : bras plaqués, descendus hors du champ
                    return Key.REST.lerp(new Key(0.1F, -0.45F, 0.3F, 30.0F, 0.0F, 0.0F, 1.0F), w);
                }
                return Key.REST.lerp(right ? new Key(-0.28F, 0.1F, -0.7F, -14.0F, 16.0F, 0.0F, 1.0F) : new Key(0.1F, -0.35F, 0.3F, 25.0F, 0.0F, 0.0F, 1.0F), w);
            }
            case Lunarian.JUDGMENT_END -> {
                float w = 1.0F - LunarianClient.ramp(e, 6.0F, LunarianJudgmentAbility.RECOVER);
                return Key.REST.lerp(right ? new Key(-0.15F, -0.42F, -0.42F, 50.0F, 10.0F, 0.0F, 1.0F) : new Key(0.1F, -0.3F, 0.2F, 25.0F, 0.0F, 0.0F, 1.0F), w);
            }
            default -> {
                return null;
            }
        }
    }

    private static Key current(LocalPlayer player, float partialTicks, boolean right) {
        if (!LunarianClient.isLunarian(player)) {
            return null;
        }
        int kind = LunarianClient.kind(player);
        float age = player.tickCount + partialTicks;
        float e = LunarianClient.elapsed(player, age);
        return kind == 0 || e < 0.0F ? null : key(player, kind, e, age, right);
    }

    public static void applyMain(PoseStack pose, float partialTicks) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        HumanoidArm side = player.getMainArm();
        Key k = current(player, partialTicks, side == HumanoidArm.RIGHT);
        if (k != null) {
            transform(pose, k, side, false);
        }
    }

    public static HumanoidArm offArm(PoseStack pose, float partialTicks) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.getOffhandItem().isEmpty() || player.isInvisible()) {
            return null;
        }
        HumanoidArm side = player.getMainArm().getOpposite();
        Key k = current(player, partialTicks, side == HumanoidArm.RIGHT);
        if (k == null || k.w <= 0.01F) {
            return null;
        }
        transform(pose, k, side, true);
        return side;
    }

    private static void transform(PoseStack pose, Key k, HumanoidArm arm, boolean rise) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float hx = HAND_X * side;
        pose.translate(k.x * side, k.y - (rise ? (1.0F - k.w) * 0.9F : 0.0F), k.z);
        pose.translate(hx, HAND_Y, HAND_Z);
        pose.rotateDegrees(Axis.XP, k.rx);
        pose.rotateDegrees(Axis.YP, k.ry * side);
        pose.rotateDegrees(Axis.ZP, k.rz * side);
        pose.translate(-hx, -HAND_Y, -HAND_Z);
    }

    // ------------------------------------------------------------------ flammes dans le champ

    /**
     * À la fin du dessin des mains (repère de la vue : x à droite, y en haut, -z devant). Les AILES ne sont jamais dessinées à
     * la première personne : elles ne gênent jamais la vue (le modèle complet reste visible à la troisième personne et pour les
     * autres joueurs). Seuls de petits effets de feu, toujours sur les BORDS de l'écran :
     * <ul>
     *   <li>allumage et extinction des flammes du dos : courtes langues de feu et braises (ou fumée) qui montent dans les coins
     *       du bas ;</li>
     *   <li>Poing embrasé, pendant le dash : mèches de feu qui filent vers l'arrière sur les côtés (vitesse).</li>
     * </ul>
     */
    public static void submitView(PoseStack pose, SubmitNodeCollector col, float partialTicks, int light) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        LunarianLayer layer = LunarianLayer.shared();
        if (player == null || layer == null || player.isInvisible() || !LunarianClient.isLunarian(player)) {
            return;
        }
        float age = player.tickCount + partialTicks;
        LunarianFire fire = layer.fire;
        fire.time = age;
        float[] up = {0.0F, 1.0F, 0.0F};
        // 1. transitions des flammes du dos, dans les coins du bas
        float[] change = LunarianClient.flameChange(player, age);
        if (change != null && change[0] < Lunarian.TRANSITION + 10) {
            boolean on = change[1] > 0.5F;
            float k = change[0] / (Lunarian.TRANSITION + 10.0F);
            float f = on ? Mth.sin(Math.min(1.0F, k) * Mth.PI) : (1.0F - k) * 0.8F;
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i < 2; i++) {
                    float[] base = {side * (0.62F + 0.14F * i), -0.62F, -0.7F - 0.1F * i};
                    fire.tongue(pose, col, base, up, (0.28F - 0.06F * i) * f, 0.07F, new float[] {side * 0.25F, 0, 0}, 4, 0.8F, 0.6F,
                            on ? 3 : 2, 0.85F * f, player.getId() + side * 3 + i);
                }
            }
            Random r = new Random(player.getId() * 5L);
            for (int i = 0; i < 8; i++) {
                float ph = (age * 0.05F + r.nextFloat()) % 1.0F;
                float side = i % 2 == 0 ? 1.0F : -1.0F;
                float[] p = {side * (0.5F + r.nextFloat() * 0.4F), -0.65F + 0.6F * ph, -0.7F - r.nextFloat() * 0.2F};
                if (on) {
                    fire.ember(pose, col, p, i * 40.0F, 0.018F * (1.0F - ph) * f, argb((1.0F - ph) * f, i % 4 == 0 ? YELLOW : ORANGE));
                } else if (i % 2 == 0) {
                    fire.smoke(pose, col, p, age * 3.0F + i * 30.0F, 0.1F + 0.08F * ph, 0.3F * (1.0F - ph) * (1.0F - k), light);
                }
            }
        }
        // 2. Poing embrasé : mèches de vitesse sur les côtés pendant le dash
        int kind = LunarianClient.kind(player);
        float e = LunarianClient.elapsed(player, age);
        if (kind == Lunarian.FIST && e >= LunarianFistAbility.WINDUP) {
            Random r = new Random(player.getId() * 11L);
            float[] back = {0.0F, 0.0F, 1.0F};
            for (int i = 0; i < 8; i++) {
                float ph = (age * 0.22F + r.nextFloat()) % 1.0F;
                float side = i % 2 == 0 ? 1.0F : -1.0F;
                float[] p = {side * (0.8F + r.nextFloat() * 0.35F), (r.nextFloat() - 0.5F) * 0.8F, -1.5F + 1.6F * ph};
                fire.wisp(pose, col, p, back, 0.45F * (1.0F - ph), 0.05F, 0.0F, 0.7F * (1.0F - ph), player.getId() + i);
            }
        }
    }

    // ------------------------------------------------------------------ caméra de la Tempête

    private static CameraType saved;

    /**
     * Pendant la Tempête du jugement céleste (envol, plongée, impact), la vue du lanceur passe à la troisième personne pour suivre
     * l'envol et la plongée ; la perspective d'avant est rendue exactement ensuite (même en cas d'interruption ou de mort).
     */
    public static void tickCamera(Minecraft mc) {
        LocalPlayer player = mc.player;
        int kind = player == null ? 0 : LunarianClient.kind(player);
        boolean active = player != null && player.isAlive() && LunarianClient.isLunarian(player)
                && (kind == Lunarian.JUDGMENT && LunarianClient.elapsed(player, player.tickCount) >= LunarianJudgmentAbility.PREP - 4 || kind == Lunarian.JUDGMENT_END);
        if (active) {
            if (saved == null) {
                saved = mc.options.getCameraType();
            }
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        } else if (saved != null) {
            mc.options.setCameraType(saved);
            saved = null;
        }
    }
}
