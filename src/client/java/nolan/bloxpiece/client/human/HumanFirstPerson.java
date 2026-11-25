package nolan.bloxpiece.client.human;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import nolan.bloxpiece.ability.Human;
import nolan.bloxpiece.ability.HumanConquerorFistAbility;
import nolan.bloxpiece.ability.HumanDestructiveRushAbility;
import nolan.bloxpiece.ability.HumanEarthBreakerAbility;
import nolan.bloxpiece.ability.HumanLightningImpactAbility;

/**
 * Race Humaine à la PREMIÈRE PERSONNE : la main principale suit les mêmes phases que la pose vue de l'extérieur (HumanPose),
 * avec les mêmes courbes : le bras recule puis part droit devant (Impact Fulgurant), se rentre pendant la ruée, monte hors de
 * l'écran puis s'abat vers le sol (Briseur Terrestre), se ramène tout près en tremblant puis jaillit (Poing du Conquérant).
 * Appelé par HumanFirstPersonMixin au début du dessin de la main (repère de la caméra : x droite, y haut, -z devant).
 */
public final class HumanFirstPerson {

    /** Position de la main droite tenue par Minecraft (repère de la caméra). */
    private static final float HAND_X = 0.56F, HAND_Y = -0.52F, HAND_Z = -0.72F;

    private HumanFirstPerson() {
    }

    /** Une pose de la main : déplacement (blocs) et rotations (degrés) autour de la main. */
    private record Key(float x, float y, float z, float rx, float ry, float rz) {
        static final Key REST = new Key(0, 0, 0, 0, 0, 0);

        Key lerp(Key b, float k) {
            return new Key(Mth.lerp(k, x, b.x), Mth.lerp(k, y, b.y), Mth.lerp(k, z, b.z), Mth.lerp(k, rx, b.rx), Mth.lerp(k, ry, b.ry),
                    Mth.lerp(k, rz, b.rz));
        }

        Key shake(float amount, float age) {
            return new Key(x + Mth.sin(age * 3.3F) * amount, y + Mth.cos(age * 2.7F) * amount, z, rx, ry, rz);
        }
    }

    public static void apply(PoseStack pose, float partialTicks) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !HumanClient.isHuman(player)) {
            return;
        }
        int kind = HumanClient.kind(player);
        float age = player.tickCount + partialTicks;
        float e = HumanClient.elapsed(player, age);
        if (kind == 0 || e < 0.0F) {
            return;
        }
        Key k = key(kind, e, age);
        if (k == null) {
            return;
        }
        float side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float hx = HAND_X * side;
        pose.translate(k.x * side, k.y, k.z);
        pose.translate(hx, HAND_Y, HAND_Z);
        pose.rotateDegrees(Axis.XP, k.rx);
        pose.rotateDegrees(Axis.YP, k.ry * side);
        pose.rotateDegrees(Axis.ZP, k.rz * side);
        pose.translate(-hx, -HAND_Y, -HAND_Z);
    }

    private static Key key(int kind, float e, float age) {
        switch (kind) {
            case Human.IMPACT -> {
                float w = HumanLightningImpactAbility.WINDUP, s = HumanLightningImpactAbility.STRIKE, d = HumanLightningImpactAbility.DURATION;
                Key armed = new Key(0.1F, -0.14F, 0.26F, 12.0F, -8.0F, 6.0F);
                Key struck = new Key(-0.3F, 0.2F, -0.48F, -12.0F, 6.0F, 0.0F);
                Key k = Key.REST.lerp(armed, HumanClient.ramp(e, 0.0F, w)).shake(0.006F * HumanClient.ramp(e, w - 3.0F, w), age);
                k = k.lerp(struck, HumanClient.snap(e, w, s));
                return Key.REST.lerp(k, 1.0F - HumanClient.ramp(e, s + 3.0F, d));
            }
            case Human.RUSH -> {
                float c = HumanDestructiveRushAbility.CROUCH;
                Key tucked = new Key(-0.18F, -0.16F, 0.08F, 10.0F, 0.0F, 22.0F);
                Key k = Key.REST.lerp(tucked, HumanClient.ramp(e, 0.0F, c));
                return e > c ? k.shake(0.012F, age) : k;
            }
            case Human.RUSH_END -> {
                Key tucked = new Key(-0.18F, -0.16F, 0.08F, 10.0F, 0.0F, 22.0F);
                return Key.REST.lerp(tucked, 1.0F - HumanClient.ramp(e, 2.0F, HumanDestructiveRushAbility.RECOVER));
            }
            case Human.BREAKER -> {
                float r = HumanEarthBreakerAbility.RAISE, h = HumanEarthBreakerAbility.HOLD, i = HumanEarthBreakerAbility.IMPACT;
                Key raised = new Key(-0.05F, 0.75F, 0.1F, 40.0F, 0.0F, -8.0F);
                Key slammed = new Key(-0.15F, -0.4F, -0.35F, -55.0F, 0.0F, 0.0F);
                Key k = Key.REST.lerp(raised, HumanClient.ramp(e, 0.0F, r)).shake(0.01F * HumanClient.ramp(e, r - 2.0F, h), age);
                float slam = Mth.clamp((e - h) / (i - h), 0.0F, 1.0F);
                k = k.lerp(slammed, slam * slam);
                return Key.REST.lerp(k, 1.0F - HumanClient.ramp(e, i + 8.0F, HumanEarthBreakerAbility.DURATION));
            }
            case Human.CONQUEROR -> {
                float c = HumanConquerorFistAbility.CHARGE, rel = HumanConquerorFistAbility.RELEASE;
                Key drawn = new Key(0.16F, -0.22F, 0.34F, 14.0F, -22.0F, 10.0F);
                Key struck = new Key(-0.32F, 0.18F, -0.62F, -10.0F, 8.0F, 0.0F);
                Key k = Key.REST.lerp(drawn, HumanClient.ramp(e, 0.0F, 14.0F)).shake(0.014F * HumanClient.ramp(e, 8.0F, c), age);
                k = k.lerp(struck, HumanClient.snap(e, c, rel));
                if (e >= rel && e < rel + 10.0F) {
                    k = k.shake(0.01F * (1.0F - (e - rel) / 10.0F), age);
                }
                return Key.REST.lerp(k, 1.0F - HumanClient.ramp(e, rel + 12.0F, HumanConquerorFistAbility.DURATION));
            }
            default -> {
                return null;
            }
        }
    }
}
