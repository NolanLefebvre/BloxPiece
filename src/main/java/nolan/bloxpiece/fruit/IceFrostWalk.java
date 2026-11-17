package nolan.bloxpiece.fruit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Passif du fruit de la Glace : Frost Walker permanent, comme l'enchantement Semelles givrantes. Quand le porteur est au sol (ou
 * sur la glace), l'eau sous et autour de ses pieds devient de la glace givrée, qui fond toute seule peu après (comme avec
 * l'enchantement). Il peut ainsi marcher sur l'eau sans jamais la toucher (et donc sans subir la malédiction de l'eau).
 */
public final class IceFrostWalk {

    /** Rayon de gel (blocs) : celui de Semelles givrantes II. */
    private static final int RADIUS = 3;

    private IceFrostWalk() {
    }

    public static void tick(ServerPlayer player) {
        if (!player.onGround() || player.isInWater() || player.isSpectator()) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos feet = player.blockPosition();
        BlockState frosted = Blocks.FROSTED_ICE.defaultBlockState();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) {
                    continue;
                }
                BlockPos pos = feet.offset(dx, -1, dz);
                BlockState state = level.getBlockState(pos);
                // Seulement de l'eau immobile (source), avec de l'air juste au-dessus : comme Semelles givrantes
                if (state.is(Blocks.WATER) && state.getFluidState().isSource() && level.getBlockState(pos.above()).isAir()) {
                    level.setBlockAndUpdate(pos, frosted);
                    level.scheduleTick(pos, Blocks.FROSTED_ICE, Mth.nextInt(level.getRandom(), 60, 120)); // fonte progressive
                }
            }
        }
    }
}
