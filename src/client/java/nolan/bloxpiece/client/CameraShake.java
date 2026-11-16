package nolan.bloxpiece.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;

/**
 * Tremblement de caméra (attaques du Bouddha) : à chaque tick client, le regard du joueur est décalé d'un petit angle aléatoire,
 * puis le décalage précédent est retiré : la vue tremble sans jamais dériver. La force décroît jusqu'à la fin. Déclenché par le
 * serveur (ShakePayload), pour tous les joueurs proches de l'attaque.
 */
public final class CameraShake {

    private static float strength;
    private static int ticks;
    private static int total;
    private static float offsetYaw;
    private static float offsetPitch;

    private CameraShake() {
    }

    /** Démarre (ou renforce) un tremblement : le plus fort des deux l'emporte. */
    public static void start(float newStrength, int duration) {
        if (newStrength * duration >= strength * ticks) {
            strength = newStrength;
            ticks = duration;
            total = duration;
        }
    }

    /** À appeler à chaque fin de tick client. */
    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            ticks = 0;
            offsetYaw = offsetPitch = 0.0F;
            return;
        }
        // Retire le décalage du tick précédent
        player.setYRot(player.getYRot() - offsetYaw);
        player.setXRot(player.getXRot() - offsetPitch);
        offsetYaw = offsetPitch = 0.0F;
        if (ticks <= 0) {
            return;
        }
        float power = strength * ticks / total;
        RandomSource random = player.getRandom();
        offsetYaw = (random.nextFloat() * 2.0F - 1.0F) * power;
        offsetPitch = (random.nextFloat() * 2.0F - 1.0F) * power * 0.7F;
        player.setYRot(player.getYRot() + offsetYaw);
        player.setXRot(player.getXRot() + offsetPitch);
        ticks--;
    }
}
