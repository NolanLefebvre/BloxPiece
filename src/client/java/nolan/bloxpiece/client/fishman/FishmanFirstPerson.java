package nolan.bloxpiece.client.fishman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import nolan.bloxpiece.ability.Fishman;
import nolan.bloxpiece.ability.FishmanDepthsAbility;
import nolan.bloxpiece.ability.FishmanSpearAbility;
import nolan.bloxpiece.ability.FishmanTideAbility;
import nolan.bloxpiece.ability.FishmanWaveAbility;

/**
 * Race Fishman à la PREMIÈRE PERSONNE (même principe que Cyborg, Humain et Minks) : les deux mains suivent les attaques, avec les
 * mêmes instants que la pose vue de l'extérieur (FishmanPose) ; l'autre main, que Minecraft ne dessine pas vide, est ajoutée
 * pendant les attaques (CyborgFirstPersonMixin). L'eau et l'aileron sont dessinés sur les mains (FishmanLayer.submitHand).
 * <ul>
 *   <li>Onde aquatique : main gauche ouverte dressée dans le champ, poing droit armé bas, puis lancé au centre de l'écran ;</li>
 *   <li>Lance aquatique : mains rapprochées au centre (les gouttes tournent autour), recul, projection vers l'avant ;</li>
 *   <li>Vague dévastatrice : mains basses devant, levées en haut de l'écran, puis abattues vers le bas ;</li>
 *   <li>Perforation : poing droit armé en bas de l'écran qui tremble (la spirale l'entoure), lancé loin au centre.</li>
 * </ul>
 */
public final class FishmanFirstPerson {

    private static final float HAND_X = 0.56F, HAND_Y = -0.52F, HAND_Z = -0.72F;

    private FishmanFirstPerson() {
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
        switch (kind) {
            case Fishman.WAVE -> {
                float st = FishmanWaveAbility.STANCE, s = FishmanWaveAbility.STRIKE, end = FishmanWaveAbility.DURATION;
                float w = FishmanClient.ramp(e, 0.0F, 4.0F) * (1.0F - FishmanClient.ramp(e, end - 6.0F, end));
                if (right) {
                    Key guard = new Key(0.08F, -0.18F, 0.2F, 18.0F, -8.0F, 6.0F, 1.0F);
                    Key cock = new Key(0.15F, -0.26F, 0.32F, 26.0F, -12.0F, 10.0F, 1.0F);
                    Key punch = new Key(-0.32F, 0.12F, -0.62F, -12.0F, 16.0F, 0.0F, 1.0F);
                    return Key.REST.lerp(guard.lerp(cock, FishmanClient.ramp(e, st, s - 2.0F)).lerp(punch, FishmanClient.snap(e, s - 1.5F, s + 0.5F)), w);
                }
                Key knife = new Key(-0.2F, 0.3F, -0.12F, -25.0F, -18.0F, -55.0F, 1.0F); // main ouverte dressée (garde)
                Key pulled = new Key(0.1F, -0.25F, 0.25F, 20.0F, 0.0F, 0.0F, 1.0F);
                return Key.REST.lerp(knife.lerp(pulled, FishmanClient.snap(e, s - 1.5F, s + 0.5F)), w);
            }
            case Fishman.SPEAR -> {
                float g = FishmanSpearAbility.GATHER, th = FishmanSpearAbility.THROW, end = FishmanSpearAbility.DURATION;
                float w = FishmanClient.ramp(e, 0.0F, 4.0F) * (1.0F - FishmanClient.ramp(e, end - 6.0F, end));
                float sway = Mth.sin(age * 0.45F + (right ? 0.0F : 1.5F)) * 0.04F * (1.0F - FishmanClient.ramp(e, g, th));
                Key hold = new Key(-0.32F + sway, 0.06F + sway, -0.12F, -10.0F, 22.0F, -15.0F, 1.0F);
                Key back = new Key(-0.12F, -0.12F, 0.16F, 16.0F, 8.0F, -5.0F, 1.0F);
                Key thrown = new Key(-0.22F, 0.12F, -0.58F, -14.0F, 10.0F, 0.0F, 1.0F);
                Key k = hold.lerp(back, FishmanClient.ramp(e, g - 3.0F, th - 0.5F)).lerp(thrown, FishmanClient.snap(e, th - 0.5F, th + 1.0F));
                int[] throws_ = FishmanSpearAbility.THROWS; // salves suivantes : même mouvement, réarmé puis projeté
                for (int i = 1; i < throws_.length; i++) {
                    k = k.lerp(back, FishmanClient.ramp(e, throws_[i - 1] + 2.0F, throws_[i] - 0.5F))
                            .lerp(thrown, FishmanClient.snap(e, throws_[i] - 0.5F, throws_[i] + 1.0F));
                }
                return Key.REST.lerp(k, w);
            }
            case Fishman.TIDE -> {
                float c = FishmanTideAbility.CROUCH, s = FishmanTideAbility.SLAM, end = FishmanTideAbility.DURATION;
                float w = FishmanClient.ramp(e, 0.0F, 3.0F) * (1.0F - FishmanClient.ramp(e, end - 10.0F, end));
                Key low = new Key(-0.15F, -0.08F, -0.05F, 8.0F, 10.0F, 0.0F, 1.0F);
                Key raised = new Key(-0.1F, 0.55F, 0.05F, -40.0F, 5.0F, 0.0F, 1.0F);
                Key slam = new Key(-0.2F, -0.42F, -0.42F, 55.0F, 10.0F, 0.0F, 1.0F);
                Key k = low.lerp(raised, FishmanClient.ramp(e, c, s - 2.0F)).lerp(slam, FishmanClient.snap(e, s - 1.5F, s + 0.3F));
                return Key.REST.lerp(k, w);
            }
            case Fishman.DEPTHS -> {
                float c = FishmanDepthsAbility.CHARGE, s = FishmanDepthsAbility.STRIKE, end = FishmanDepthsAbility.DURATION;
                float w = FishmanClient.ramp(e, 0.0F, 5.0F) * (1.0F - FishmanClient.ramp(e, end - 8.0F, end));
                if (right) {
                    Key wound = new Key(0.18F, -0.28F, 0.32F, 24.0F, -14.0F, 10.0F, 1.0F)
                            .shake(0.022F * FishmanClient.ramp(e, 8.0F, c) * (e < s ? 1.0F : 0.0F), age);
                    Key hit = new Key(-0.32F, 0.12F, -0.72F, -10.0F, 16.0F, 0.0F, 1.0F);
                    Key ready = new Key(0.0F, -0.12F, 0.12F, 12.0F, -6.0F, 4.0F, 1.0F);
                    return Key.REST.lerp(ready.lerp(wound, FishmanClient.ramp(e, 4.0F, s - 2.0F)).lerp(hit, FishmanClient.snap(e, s - 1.0F, s + 0.6F)), w);
                }
                Key guide = new Key(-0.2F, 0.05F, -0.15F, -10.0F, 15.0F, -10.0F, 1.0F);
                Key pulled = new Key(0.12F, -0.3F, 0.25F, 20.0F, 0.0F, 0.0F, 1.0F);
                return Key.REST.lerp(guide.lerp(pulled, FishmanClient.snap(e, s - 1.0F, s + 0.6F)), w);
            }
            default -> {
                return null;
            }
        }
    }

    private static Key current(LocalPlayer player, float partialTicks, boolean right) {
        if (!FishmanClient.isFishman(player)) {
            return null;
        }
        int kind = FishmanClient.kind(player);
        float age = player.tickCount + partialTicks;
        float e = FishmanClient.elapsed(player, age);
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
