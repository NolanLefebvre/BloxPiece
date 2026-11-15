package nolan.bloxpiece.fruit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Malédiction de l'eau : le porteur prend des dégâts de noyade quand sa tête est sous l'eau. */
public class WaterCurse implements Weakness {

    @Override
    public void tick(ServerPlayer player) {
        // Dégâts uniquement si la tête est sous l'eau (entièrement immergé)
        if (player.isUnderWater()) {
            ServerLevel level = (ServerLevel) player.level();
            player.hurtServer(level, level.damageSources().drown(), 1.0f);
        }
    }
}
