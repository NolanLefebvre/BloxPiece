package nolan.bloxpiece.client.minks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import nolan.bloxpiece.ability.Minks;
import nolan.bloxpiece.ability.MinksClawsAbility;
import nolan.bloxpiece.ability.MinksDanceAbility;
import nolan.bloxpiece.ability.MinksSulongAbility;

/**
 * Race Minks à la PREMIÈRE PERSONNE (même principe que Cyborg et Humain) : les deux mains suivent les attaques, avec les mêmes
 * instants que la pose vue de l'extérieur (MinksPose) ; l'autre main, que Minecraft ne dessine pas vide, est ajoutée pendant
 * les attaques (CyborgFirstPersonMixin). Les griffes sont dessinées sur les mains (MinksLayer.submitHand).
 * <ul>
 *   <li>Griffes : les mains se croisent devant, puis s'écartent ;</li>
 *   <li>Barrage : chaque main recule en bas de l'écran puis jaillit vers le centre, en alternance ; dernier coup armé très loin ;</li>
 *   <li>Danse : à chaque bond, une main balaie l'écran en griffant (revers, uppercut, double griffe) ;</li>
 *   <li>Sulong : les poings se serrent en bas de l'écran et tremblent.</li>
 * </ul>
 */
public final class MinksFirstPerson {

    private static final float HAND_X = 0.56F, HAND_Y = -0.52F, HAND_Z = -0.72F;

    private MinksFirstPerson() {
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

    /** Pose d'une main ({@code right} : la main droite du joueur) ; x positif = vers l'extérieur. */
    private static Key key(int kind, float e, float age, boolean right) {
        if (kind == Minks.CLAWS) {
            float d = MinksClawsAbility.ACTIVATE, half = d / 2.0F;
            float w = MinksClient.ramp(e, 0.0F, 2.0F) * (1.0F - MinksClient.ramp(e, d - 3.0F, d));
            Key crossed = new Key(-0.3F, 0.18F, -0.05F, -8.0F, 25.0F, -20.0F, 1.0F);
            Key open = new Key(0.12F, 0.12F, -0.12F, -14.0F, -12.0F, 18.0F, 1.0F);
            return Key.REST.lerp(Key.REST.lerp(crossed, 1.0F).lerp(open, MinksClient.snap(e, half - 1.0F, half + 1.0F)), w);
        }
        if (MinksClient.barrage(kind)) {
            // chaque main : armée en bas de l'écran (reculée), JAILLIT vers le centre, puis revient ; les deux en décalé
            float end = nolan.bloxpiece.ability.MinksBarrageAbility.duration(MinksClient.punches(kind));
            float w = MinksClient.ramp(e, 0.0F, 3.0F) * (1.0F - MinksClient.ramp(e, end - 5.0F, end));
            float ext = MinksClient.armExt(kind, e, right) * MinksClient.ramp(e, 0.0F, nolan.bloxpiece.ability.MinksBarrageAbility.GUARD);
            Key cocked = new Key(0.1F, -0.2F, 0.24F, 22.0F, -6.0F, 4.0F, 1.0F);
            Key hit = new Key(-0.3F, 0.14F, -0.6F, -10.0F, 14.0F, 0.0F, 1.0F);
            Key deep = new Key(0.2F, -0.32F, 0.38F, 32.0F, -12.0F, 8.0F, 1.0F); // armé très loin (coup final)
            Key k = ext >= 0.0F ? cocked.lerp(hit, ext) : cocked.lerp(deep, -ext);
            return Key.REST.lerp(k, w);
        }
        if (MinksClient.dance(kind)) {
            boolean sulong = kind == Minks.DANCE_S;
            float end = MinksDanceAbility.duration(sulong), ret = MinksDanceAbility.returnTick(sulong);
            float w = MinksClient.ramp(e, 0.0F, 3.0F) * (1.0F - MinksClient.ramp(e, end - 5.0F, end));
            if (e < MinksDanceAbility.PREP) {
                return Key.REST.lerp(new Key(0.06F, -0.12F, 0.1F, 12.0F, 0.0F, 0.0F, 1.0F), w).shake(0.006F, age);
            }
            if (e < ret) {
                float step = MinksClient.step(kind);
                float x = (e - MinksDanceAbility.PREP) / step;
                int idx = (int) Math.floor(x);
                float hit = MinksClient.snap((x - idx) * step, MinksDanceAbility.STRIKE - 1.0F, MinksDanceAbility.STRIKE + 0.5F);
                boolean active = switch (idx % 3) {
                    case 0 -> right;
                    case 1 -> !right;
                    default -> true;
                };
                if (!active) {
                    return Key.REST.lerp(new Key(0.05F, -0.05F, 0.08F, 8.0F, 0.0F, 0.0F, 1.0F), w);
                }
                Key from = new Key(0.25F, 0.2F, -0.1F, -20.0F, -25.0F, 30.0F, 1.0F);
                Key to = new Key(-0.45F, -0.05F, -0.3F, 10.0F, 30.0F, -35.0F, 1.0F);
                return Key.REST.lerp(from.lerp(to, hit), w);
            }
            float fin = ret + MinksDanceAbility.FINAL_DELAY;
            Key up = new Key(-0.05F, 0.5F, -0.1F, -30.0F, 0.0F, 0.0F, 1.0F), down = new Key(-0.15F, -0.2F, -0.35F, 30.0F, 10.0F, 0.0F, 1.0F);
            return Key.REST.lerp(Key.REST.lerp(up, MinksClient.ramp(e, ret, fin - 1.0F)).lerp(down, MinksClient.snap(e, fin - 1.0F, fin + 0.5F)), w);
        }
        if (kind == Minks.SULONG) {
            // poings serrés en bas de l'écran, qui tremblent de plus en plus ; jetés vers le haut à l'explosion ; puis redescendent
            float c = MinksSulongAbility.CHARGE, end = c + MinksSulongAbility.REVEAL;
            float w = MinksClient.ramp(e, 0.0F, 4.0F) * (1.0F - MinksClient.ramp(e, end - 4.0F, end));
            Key low = new Key(0.05F, -0.2F, 0.1F, 18.0F, -10.0F, 12.0F, 1.0F);
            Key tight = new Key(-0.08F, -0.28F, 0.16F, 26.0F, -14.0F, 18.0F, 1.0F);
            Key thrown = new Key(0.28F, 0.45F, -0.15F, -35.0F, -18.0F, 30.0F, 1.0F);
            Key k = Key.REST.lerp(low, 1.0F).lerp(tight, MinksClient.ramp(e, 8.0F, c - 2.0F))
                    .shake(0.025F * MinksClient.ramp(e, 6.0F, c) * (e < c ? 1.0F : 0.0F), age + (right ? 0.0F : 1.7F));
            k = k.lerp(thrown, MinksClient.snap(e, c - 1.0F, c + 1.0F) * (1.0F - MinksClient.ramp(e, c + 3.0F, c + 9.0F)));
            return Key.REST.lerp(k, w);
        }
        return null;
    }

    private static Key current(LocalPlayer player, float partialTicks, boolean right) {
        if (!MinksClient.isMinks(player)) {
            return null;
        }
        int kind = MinksClient.kind(player);
        float age = player.tickCount + partialTicks;
        float e = MinksClient.elapsed(player, age);
        return kind == 0 || e < 0.0F ? null : key(kind, e, age, right);
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

    /** L'autre main pendant les attaques (main secondaire vide) : pose la transformation et renvoie son côté, sinon null. */
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
}
