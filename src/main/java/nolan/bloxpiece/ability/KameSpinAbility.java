package nolan.bloxpiece.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Kame Kame no Mi, touche 3 : la carapace tournoyante. Carapace portée : le joueur rentre ses membres d'un coup
 * (Kame.SPIN_RETRACT_TICKS), puis seule la carapace reste, couchée, qui tourne sur elle-même et file là où il regarde pendant
 * Kame.SPIN_TICKS ticks : chaque créature touchée prend des dégâts et un recul (une fois par demi-seconde au plus). À la fin,
 * ses membres ressortent et la carapace reste sur son dos.
 */
public class KameSpinAbility implements Ability {

    @Override
    public String id() {
        return "kame_spin";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 8;
    }

    @Override
    public int requiredLevel() {
        return 10;
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        if (!Kame.shellReady(player) || Kame.inPocket(player)) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.kame.needs_shell"), true);
            return false;
        }
        return !Kame.retracted(player);
    }

    @Override
    public void use(ServerPlayer player) {
        Kame.retract(player, Kame.KIND_SPIN);
    }
}
