package nolan.bloxpiece.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import nolan.bloxpiece.treasure.TreasureChests;

/**
 * Le Coffre au Trésor posé. Il regarde vers le joueur qui l'a posé (serrure devant). Clic droit : la roulette des fruits du démon
 * s'ouvre ; le coffre ne disparaît qu'au clic sur OUVRIR (voir TreasureChests). Cassé, il se ramasse comme un objet normal (table de butin blocks/treasure_chest).
 */
public class TreasureChestBlock extends Block {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Forme du coffre posé (en pixels) : coffre compact de 8,5 x 6 px, 7 px de haut (même forme quelle que soit l'orientation). */
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 7.5, 13.0);

    public TreasureChestBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // La serrure (face nord du modèle) se tourne vers le joueur
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            TreasureChests.openFromBlock(serverPlayer, pos); // retiré seulement au clic sur OUVRIR
        }
        return InteractionResult.SUCCESS;
    }
}
