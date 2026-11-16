package nolan.bloxpiece.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.fruit.WaterCurse;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Malédiction de l'eau, partie mouvement : un porteur de fruit entièrement sous l'eau ne peut plus remonter.
 *
 * Minecraft laisse le client calculer les déplacements de son propre joueur (le serveur ne fait que vérifier) : c'est donc ici,
 * à la fin de chaque tick client, qu'on remplace sa vitesse par une chute vers le fond. Le tick suivant, la nage ou le saut ne
 * peuvent ajouter qu'un peu de vitesse vers le haut, qui ne compense jamais la chute : le joueur coule à pic jusqu'au fond.
 * Le fruit du joueur est connu du client (attachment FRUIT, synchronisé avec lui).
 */
public final class ClientWaterCurse {

    private ClientWaterCurse() {
    }

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !player.isUnderWater() || player.isSpectator() || player.getAbilities().flying
                || Fruits.get(player.getAttached(ModAttachments.FRUIT)) == null) {
            return;
        }
        player.setDeltaMovement(WaterCurse.sinkingVelocity(player.getDeltaMovement()));
        player.setSwimming(false);
        player.setSprinting(false); // pas de nage rapide
    }
}
