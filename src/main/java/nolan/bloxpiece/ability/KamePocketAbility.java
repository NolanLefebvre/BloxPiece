package nolan.bloxpiece.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Kame Kame no Mi, touche 2 : la dimension de poche. Carapace portée : le joueur rentre lentement tête, bras et jambes
 * (Kame.POCKET_RETRACT_TICKS), la carapace se détache et tombe au sol (KameShellEntity), et il se retrouve dans sa parcelle
 * personnelle de la dimension de poche, où il construit librement. Dans la poche, la même touche le fait ressortir : il
 * réapparaît là où sa carapace l'attendait, qui se remet sur son dos.
 */
public class KamePocketAbility implements Ability {

    @Override
    public String id() {
        return "kame_pocket";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 5;
    }

    @Override
    public int requiredLevel() {
        return 5;
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        if (Kame.inPocket(player)) {
            return !Kame.retracted(player);
        }
        if (!Kame.shellReady(player)) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.kame.needs_shell"), true);
            return false;
        }
        return !Kame.retracted(player);
    }

    @Override
    public void use(ServerPlayer player) {
        if (Kame.inPocket(player)) {
            Kame.exitPocket(player);
        } else {
            Kame.retract(player, Kame.KIND_POCKET);
        }
    }
}
