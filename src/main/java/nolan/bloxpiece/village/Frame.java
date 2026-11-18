package nolan.bloxpiece.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Repère LOCAL d'un bâtiment : chaque bâtiment est dessiné une seule fois, façade (porte) vers +Z (le "sud" local), x = largeur,
 * y = hauteur (0 = niveau du sol du village), puis tourné et placé dans le monde : sa façade regarde la place du village.
 * Les orientations des blocs (escaliers, portes, lits, coffres...) sont données dans ce repère local et tournées avec lui.
 * NONE : façade vers le sud ; CLOCKWISE_90 : vers l'ouest ; COUNTERCLOCKWISE_90 : vers l'est ; CLOCKWISE_180 : vers le nord.
 */
public final class Frame {

    private final VillageBuilder builder;
    private final BlockPos origin;
    private final Rotation rotation;
    /**
     * Variation de matière (null = aucune) : appliquée aux blocs posés par set(Block), stairs et slab. Un bâtiment l'active pour
     * son gros oeuvre (enduit calcite / terre cuite, pierres mêlées, tuiles de deux teintes), puis la coupe avant l'intérieur.
     */
    public java.util.function.UnaryOperator<Block> texture;

    public Frame(VillageBuilder builder, BlockPos origin, Rotation rotation) {
        this.builder = builder;
        this.origin = origin;
        this.rotation = rotation;
    }

    public VillageBuilder builder() {
        return this.builder;
    }

    /** Position MONDE d'un point local. */
    public BlockPos pos(int x, int y, int z) {
        return this.origin.offset(new BlockPos(x, y, z).rotate(this.rotation));
    }

    public void set(int x, int y, int z, BlockState state) {
        this.builder.set(pos(x, y, z), state.rotate(this.rotation));
    }

    private Block tex(Block block) {
        return this.texture == null ? block : this.texture.apply(block);
    }

    /** Pose « vivante » (l'eau coule tout de suite) : voir VillageBuilder.setLive. */
    public void live(int x, int y, int z, Block block) {
        this.builder.setLive(pos(x, y, z), block.defaultBlockState());
    }

    public void set(int x, int y, int z, Block block) {
        set(x, y, z, tex(block).defaultBlockState());
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        fill(x1, y1, z1, x2, y2, z2, block.defaultBlockState());
    }

    public void air(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, VillageBlocks.AIR);
    }

    /** Murs (contour seulement) d'un rectangle, de y1 à y2. */
    public void walls(int x1, int z1, int x2, int z2, int y1, int y2, Block block) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                set(x, y, z1, block);
                set(x, y, z2, block);
            }
            for (int z = z1; z <= z2; z++) {
                set(x1, y, z, block);
                set(x2, y, z, block);
            }
        }
    }

    /**
     * Soubassement : sous le plancher (y = -1) un rang de pierre, prolongé vers le bas jusqu'au sol (le terrain est aplani,
     * mais un bord de colline peut laisser un vide).
     */
    public void foundation(int x1, int z1, int x2, int z2, Block block) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int y = -1; y >= -6; y--) {
                    BlockPos p = pos(x, y, z);
                    if (y < -1 && !this.builder.get(p).canBeReplaced()) {
                        break;
                    }
                    set(x, y, z, block);
                }
            }
        }
    }

    public void stairs(int x, int y, int z, Block block, Direction facing, boolean upsideDown) {
        set(x, y, z, tex(block).defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .trySetValue(BlockStateProperties.HALF, upsideDown ? Half.TOP : Half.BOTTOM));
    }

    public void slab(int x, int y, int z, Block block, boolean top) {
        set(x, y, z, tex(block).defaultBlockState().trySetValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM));
    }

    /** Bloc orienté (coffre, four, enclume, tonneau horizontal...). */
    public void facing(int x, int y, int z, Block block, Direction facing) {
        set(x, y, z, block.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing));
    }

    /** Tonneau (orientation libre, haut compris). */
    public void barrel(int x, int y, int z, Direction facing) {
        set(x, y, z, VillageBlocks.BARREL.defaultBlockState().trySetValue(BlockStateProperties.FACING, facing));
    }

    public void log(int x, int y, int z, Block block, Direction.Axis axis) {
        Direction.Axis rotated = axis == Direction.Axis.Y || this.rotation == Rotation.NONE || this.rotation == Rotation.CLOCKWISE_180
                ? axis : (axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        this.builder.set(pos(x, y, z), block.defaultBlockState().trySetValue(BlockStateProperties.AXIS, rotated));
    }

    /** Porte (deux moitiés). {@code facing} : vers où regarde la porte fermée (vers l'extérieur pour une porte d'entrée). */
    public void door(int x, int y, int z, Block door, Direction facing, boolean hingeRight) {
        BlockState lower = door.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .trySetValue(BlockStateProperties.DOOR_HINGE, hingeRight ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT)
                .trySetValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        set(x, y, z, lower);
        set(x, y + 1, z, lower.trySetValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    /** Lit : pied en (x, z), tête dans la direction {@code headDirection}. */
    public void bed(int x, int y, int z, Block bed, Direction headDirection) {
        BlockState foot = bed.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, headDirection)
                .trySetValue(BlockStateProperties.BED_PART, BedPart.FOOT);
        set(x, y, z, foot);
        set(x + headDirection.getStepX(), y, z + headDirection.getStepZ(), foot.trySetValue(BlockStateProperties.BED_PART, BedPart.HEAD));
    }

    /** Lanterne posée, ou suspendue sous le bloc du dessus. */
    public void lantern(int x, int y, int z, boolean hanging) {
        set(x, y, z, VillageBlocks.LANTERN.defaultBlockState().trySetValue(BlockStateProperties.HANGING, hanging));
    }

    /** Plante haute (deux moitiés : hautes herbes, rosier...). */
    public void tallPlant(int x, int y, int z, Block plant) {
        set(x, y, z, plant.defaultBlockState().trySetValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        set(x, y + 1, z, plant.defaultBlockState().trySetValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    /** Feuillage permanent (ne disparaît jamais, même loin d'un tronc). */
    public void leaves(int x, int y, int z, Block leaves) {
        set(x, y, z, leaves.defaultBlockState().trySetValue(BlockStateProperties.PERSISTENT, true));
    }

    /** Table de taverne : poteau de clôture + plaque de pression (le plateau). */
    public void table(int x, int y, int z) {
        set(x, y, z, VillageBlocks.OAK_FENCE);
        set(x, y + 1, z, VillageBlocks.OAK_PRESSURE_PLATE);
    }

    /** Toit à deux pans, faîte parallèle à X : pans d'escaliers de z1 à z2 (débord compris), pignons pleins. */
    public void gableRoofX(int x1, int x2, int z1, int z2, int y, Block stairs, Block slab, Block gable) {
        int depth = z2 - z1 + 1;
        int steps = depth / 2;
        for (int i = 0; i < steps; i++) {
            for (int x = x1; x <= x2; x++) {
                stairs(x, y + i, z1 + i, stairs, Direction.SOUTH, false);
                stairs(x, y + i, z2 - i, stairs, Direction.NORTH, false);
            }
            // pignons (murs triangulaires) sous le toit, aux deux bouts
            for (int z = z1 + i + 1; z <= z2 - i - 1; z++) {
                set(x1 + 1, y + i, z, gable);
                set(x2 - 1, y + i, z, gable);
            }
        }
        if (depth % 2 == 1) {
            for (int x = x1; x <= x2; x++) {
                slab(x, y + steps, z1 + steps, slab, false);
            }
        }
    }

    /** Toit à deux pans, faîte parallèle à Z : pans d'escaliers de x1 à x2 (débord compris), pignons pleins aux deux bouts. */
    public void gableRoofZ(int x1, int x2, int z1, int z2, int y, Block stairs, Block slab, Block gable) {
        int width = x2 - x1 + 1;
        int steps = width / 2;
        for (int i = 0; i < steps; i++) {
            for (int z = z1; z <= z2; z++) {
                stairs(x1 + i, y + i, z, stairs, Direction.EAST, false);
                stairs(x2 - i, y + i, z, stairs, Direction.WEST, false);
            }
            for (int x = x1 + i + 1; x <= x2 - i - 1; x++) {
                set(x, y + i, z1 + 1, gable);
                set(x, y + i, z2 - 1, gable);
            }
        }
        if (width % 2 == 1) {
            for (int z = z1; z <= z2; z++) {
                slab(x1 + steps, y + steps, z, slab, false);
            }
        }
    }

    /**
     * Trappe : volet ouvert contre un mur ({@code open} = true, {@code facing} = côté vers lequel elle s'ouvre, donc vers
     * l'extérieur du mur) ou plaque à plat (open = false, en haut ou en bas du bloc).
     */
    public void trapdoor(int x, int y, int z, Block block, Direction facing, boolean open, boolean top) {
        set(x, y, z, block.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .trySetValue(BlockStateProperties.OPEN, open)
                .trySetValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM));
    }

    /** Échelle contre un mur : {@code facing} = côté vers lequel on la regarde (opposé au mur). */
    public void ladder(int x, int y1, int y2, int z, Block ladder, Direction facing) {
        for (int y = y1; y <= y2; y++) {
            set(x, y, z, ladder.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing));
        }
    }
}
