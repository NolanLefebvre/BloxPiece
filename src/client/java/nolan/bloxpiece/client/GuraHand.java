package nolan.bloxpiece.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModAttachments;

/**
 * Position exacte d'une main du joueur dans le monde (côté client), pour ancrer les effets du Gura Gura no Mi sur le poing.
 * Reproduit la pose du bras du modèle du joueur (mêmes rotations que PlayerModelMixin : bras tendu = pose 1, bras levés = pose 2,
 * sinon bras le long du corps) et les transformations du rendu des entités (rotation du corps, retournement, taille).
 * En première personne, la main n'est pas le bras du modèle : l'effet est placé en bas de l'écran, du côté de la main, pour être
 * visible sans cacher le centre.
 */
public final class GuraHand {

    private GuraHand() {
    }

    /** Point d'ancrage de l'effet porté par la main {@code hand} (1 = droite, -1 = gauche). */
    public static Vec3 anchor(Player player, int hand, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            Vec3 eye = player.getEyePosition(partialTick);
            Vec3 look = player.getViewVector(partialTick);
            Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
            right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
            Vec3 up = right.cross(look);
            return eye.add(look.scale(0.9)).add(right.scale(0.48 * hand)).add(up.scale(-0.32));
        }
        return modelHand(player, hand, partialTick);
    }

    public static boolean isFirstPerson(Player player) {
        Minecraft mc = Minecraft.getInstance();
        return player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
    }

    /** Le poing du modèle du joueur (troisième personne). */
    private static Vec3 modelHand(Player player, int hand, float partialTick) {
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float pitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        float netHeadYaw = (float) Math.toRadians(Mth.wrapDegrees(headYaw - bodyYaw));
        float headX = (float) Math.toRadians(pitch);

        // Pose du bras (radians), comme PlayerModelMixin
        Integer pose = player.getAttached(ModAttachments.BUDDHA_POSE);
        float rx = 0.0F, ry = 0.0F, rz = 0.0F;
        if (pose != null && pose == 1 && hand > 0) {
            rx = -1.55F + headX;
            ry = -0.1F + netHeadYaw;
        } else if (pose != null && pose == 2) {
            rx = -2.6F;
            rz = hand > 0 ? 0.55F : -0.55F;
        } else if (pose != null && pose >= 600 && pose <= 700) {
            float t = (pose - 600) / 100.0F;
            float e = t * t * (3.0F - 2.0F * t);
            rx = -2.6F * (1.0F - e);
            rz = (0.55F + (1.5708F - 0.55F) * e) * (hand > 0 ? 1.0F : -1.0F);
        }

        // Poing dans le repère du bras (px, y vers le bas), puis rotations Z * Y * X du modèle, puis pivot de l'épaule
        double x = hand > 0 ? -1.0 : 1.0, y = 10.0, z = 0.0;
        double c = Math.cos(rx), s = Math.sin(rx);
        double y1 = y * c - z * s, z1 = y * s + z * c;
        y = y1;
        z = z1;
        c = Math.cos(ry);
        s = Math.sin(ry);
        double x2 = x * c + z * s, z2 = -x * s + z * c;
        x = x2;
        z = z2;
        c = Math.cos(rz);
        s = Math.sin(rz);
        double x3 = x * c - y * s, y3 = x * s + y * c;
        x = x3 + (hand > 0 ? -5.0 : 5.0);
        y = y3 + 2.0;

        // Rendu des entités : retournement (-1, -1, 1), décalage de 1,501 bloc, rotation du corps (180 - yaw), taille du joueur
        double wx = -x / 16.0, wy = 1.501 - y / 16.0, wz = z / 16.0;
        double phi = Math.toRadians(180.0 - bodyYaw);
        c = Math.cos(phi);
        s = Math.sin(phi);
        double fx = wx * c + wz * s, fz = -wx * s + wz * c;
        double scale = player.getScale();
        return player.getPosition(partialTick).add(fx * scale, wy * scale, fz * scale);
    }
}
