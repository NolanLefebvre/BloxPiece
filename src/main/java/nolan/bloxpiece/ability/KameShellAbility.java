package nolan.bloxpiece.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Kame Kame no Mi, touche 1 : la carapace. Appui : elle POUSSE sur le joueur (le dos bombé et le plastron grandissent ensemble
 * depuis le torse, KameShellLayer) et reste portée : bonus d'armure modéré tant qu'elle est là (Kame.ARMOR). Nouvel appui : elle
 * rentre. Impossible pendant une rétraction ou depuis la dimension de poche (la carapace est restée dehors).
 */
public class KameShellAbility implements Ability {

    @Override
    public String id() {
        return "kame_shell";
    }

    @Override
    public int cooldownTicks() {
        return 40;
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        if (Kame.inPocket(player)) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.kame.shell_outside"), true);
            return false;
        }
        return !Kame.retracted(player);
    }

    @Override
    public void use(ServerPlayer player) {
        if (Kame.hasShell(player)) {
            Kame.shrinkShell(player);
        } else {
            Kame.growShell(player);
        }
    }
}
