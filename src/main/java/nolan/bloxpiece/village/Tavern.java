package nolan.bloxpiece.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * La taverne (façon Partys Bar de Makino), au nord de la place, façade vers la place. Repère local (voir Frame) :
 * <ul>
 *   <li>bâtiment x -7..7, z -10..0 (façade en z = 0), porche couvert en z 1..3 avec enseigne suspendue et tables dehors ;</li>
 *   <li>rez-de-chaussée (y 0..4) : comptoir de bar le long du mur du fond, tonneaux derrière, cheminée à l'ouest, tables et
 *       tabourets sur les côtés ; ESPACE LIBRE devant le comptoir et près de l'entrée pour de futurs PNJ ({@link #NPC_SPOTS}) ;</li>
 *   <li>étage (y 5..8) : deux chambres, table, bibliothèque, coffre ; escalier contre le mur est ;</li>
 *   <li>toit à deux pans en briques (tuiles rouges), cheminée qui dépasse.</li>
 * </ul>
 */
public final class Tavern {

    /** Places réservées aux futurs PNJ (repère local) : derrière le comptoir (barman), devant le comptoir, près de l'entrée. */
    public static final BlockPos[] NPC_SPOTS = {
            new BlockPos(0, 1, -8),   // derrière le comptoir (barman / patronne)
            new BlockPos(-2, 1, -5),  // devant le comptoir (donneur de quêtes)
            new BlockPos(2, 1, -5),   // devant le comptoir (marchand)
            new BlockPos(0, 1, -2)    // près de l'entrée
    };

    private Tavern() {
    }

    public static void build(Frame f) {
        // ---------------------------------------------------------------- gros oeuvre
        f.foundation(-7, -10, 7, 0, VillageBlocks.STONE_BRICKS);
        f.fill(-6, 0, -9, 6, 0, -1, VillageBlocks.SPRUCE_PLANKS);                 // plancher
        f.air(-6, 1, -9, 6, 8, -1);
        f.walls(-7, -10, 7, 0, 1, 4, VillageBlocks.OAK_PLANKS);                     // murs du rez-de-chaussée
        f.walls(-7, -10, 7, 0, 6, 8, VillageBlocks.BIRCH_PLANKS);                   // murs de l'étage (plus clairs)
        f.walls(-7, -10, 7, 0, 5, 5, VillageBlocks.STRIPPED_SPRUCE_LOG);            // ceinture de bois entre les étages
        f.fill(-6, 5, -9, 6, 5, -1, VillageBlocks.SPRUCE_PLANKS);                   // plancher de l'étage
        for (int x : new int[] {-7, -3, 3, 7}) {                              // poteaux d'angle et de façade
            for (int z : new int[] {-10, 0}) {
                for (int y = 0; y <= 8; y++) {
                    f.log(x, y, z, VillageBlocks.SPRUCE_LOG, Direction.Axis.Y);
                }
            }
        }
        for (int z : new int[] {-5}) {
            for (int y = 0; y <= 8; y++) {
                f.log(-7, y, z, VillageBlocks.SPRUCE_LOG, Direction.Axis.Y);
                f.log(7, y, z, VillageBlocks.SPRUCE_LOG, Direction.Axis.Y);
            }
        }
        // fenêtres
        for (int x : new int[] {-5, -4, 4, 5}) {
            f.fill(x, 2, 0, x, 3, 0, VillageBlocks.GLASS_PANE);
            f.fill(x, 2, -10, x, 3, -10, VillageBlocks.GLASS_PANE);
            f.fill(x, 7, 0, x, 7, 0, VillageBlocks.GLASS_PANE);
        }
        for (int z : new int[] {-8, -7, -3, -2}) {
            f.fill(7, 2, z, 7, 3, z, VillageBlocks.GLASS_PANE);
            f.fill(-7, 7, z, -7, 7, z, VillageBlocks.GLASS_PANE);
            f.fill(7, 7, z, 7, 7, z, VillageBlocks.GLASS_PANE);
        }
        f.fill(-7, 2, -3, -7, 3, -2, VillageBlocks.GLASS_PANE);

        // porte double d'entrée
        f.door(-1, 1, 0, VillageBlocks.SPRUCE_DOOR, Direction.SOUTH, false);
        f.door(0, 1, 0, VillageBlocks.SPRUCE_DOOR, Direction.SOUTH, true);
        f.air(1, 1, 0, 1, 2, 0);
        f.set(1, 1, 0, VillageBlocks.OAK_PLANKS);
        f.set(1, 2, 0, VillageBlocks.OAK_PLANKS);

        // ---------------------------------------------------------------- toit (tuiles rouges) et cheminée
        f.gableRoofX(-8, 8, -11, 1, 9, VillageBlocks.BRICK_STAIRS, VillageBlocks.BRICK_SLAB, VillageBlocks.BIRCH_PLANKS);
        for (int y = 1; y <= 16; y++) {
            f.set(-6, y, -6, VillageBlocks.BRICKS);                                  // conduit de cheminée (traverse l'étage et le toit)
        }
        f.set(-6, 17, -6, VillageBlocks.CAMPFIRE); // fumée qui sort de la cheminée

        // ---------------------------------------------------------------- porche couvert
        f.fill(-4, 0, 1, 4, 0, 3, VillageBlocks.SPRUCE_PLANKS);
        for (int x : new int[] {-4, 4}) {
            f.fill(x, 1, 3, x, 3, 3, VillageBlocks.SPRUCE_FENCE);
        }
        for (int x = -5; x <= 5; x++) {
            for (int z = 1; z <= 4; z++) {
                f.slab(x, 4, z, VillageBlocks.SPRUCE_SLAB, false);
            }
        }
        f.set(0, 3, 3, VillageBlocks.OAK_HANGING_SIGN.defaultBlockState()               // enseigne suspendue
                .trySetValue(BlockStateProperties.ATTACHED, true).trySetValue(BlockStateProperties.ROTATION_16, 0));
        f.lantern(-3, 3, 2, true);
        f.lantern(3, 3, 2, true);
        // tables et tabourets dehors, de part et d'autre de la porte
        for (int x : new int[] {-3, 3}) {
            f.table(x, 1, 2);
            f.stairs(x - 1, 1, 2, VillageBlocks.SPRUCE_STAIRS, Direction.WEST, false);
            f.stairs(x + 1, 1, 2, VillageBlocks.SPRUCE_STAIRS, Direction.EAST, false);
        }
        f.barrel(-5, 1, 2, Direction.UP);
        f.barrel(5, 1, 2, Direction.UP);
        f.barrel(5, 2, 2, Direction.UP);

        // ---------------------------------------------------------------- rez-de-chaussée : le bar
        // comptoir (z = -7) de x -4 à 4, plateau en dalles, ouverture côté est pour passer derrière
        for (int x = -4; x <= 3; x++) {
            f.set(x, 1, -7, VillageBlocks.STRIPPED_OAK_LOG.defaultBlockState().trySetValue(BlockStateProperties.AXIS, Direction.Axis.X));
            f.slab(x, 2, -7, VillageBlocks.SPRUCE_SLAB, false);
        }
        f.set(-1, 3, -7, VillageBlocks.LANTERN.defaultBlockState()); // lanterne posée sur le comptoir
        // tonneaux derrière le comptoir, contre le mur du fond (couchés, comme des fûts)
        for (int x = -5; x <= 5; x++) {
            f.barrel(x, 1, -9, Direction.SOUTH);
            if (x % 2 == 0) {
                f.barrel(x, 2, -9, Direction.SOUTH);
            }
        }
        f.barrel(5, 1, -8, Direction.UP);
        // cheminée : foyer en briques contre le mur ouest
        f.fill(-6, 1, -7, -6, 3, -7, VillageBlocks.BRICKS);
        f.fill(-6, 1, -5, -6, 3, -5, VillageBlocks.BRICKS);
        f.set(-6, 3, -6, VillageBlocks.BRICKS);
        f.set(-6, 1, -6, VillageBlocks.CAMPFIRE);
        f.air(-6, 2, -6, -6, 2, -6);
        // tables et tabourets sur les côtés (le centre reste libre pour les PNJ)
        for (int[] t : new int[][] {{-4, -3}, {4, -3}, {5, -6}}) {
            f.table(t[0], 1, t[1]);
            f.stairs(t[0], 1, t[1] + 1, VillageBlocks.OAK_STAIRS, Direction.SOUTH, false);
            f.stairs(t[0], 1, t[1] - 1, VillageBlocks.OAK_STAIRS, Direction.NORTH, false);
        }
        // lanternes suspendues au plafond
        for (int[] l : new int[][] {{-3, -5}, {3, -5}, {0, -3}, {0, -8}}) {
            f.lantern(l[0], 4, l[1], true);
        }
        f.set(-1, 1, -1, VillageBlocks.RED_CARPET);
        f.set(0, 1, -1, VillageBlocks.RED_CARPET);

        // escalier vers l'étage, contre le mur est (monte vers le fond)
        for (int i = 0; i < 4; i++) {
            f.stairs(6, 1 + i, -1 - i, VillageBlocks.SPRUCE_STAIRS, Direction.NORTH, false);
            f.air(6, 5, -1 - i, 6, 5, -1 - i);               // trémie dans le plancher de l'étage
        }

        // ---------------------------------------------------------------- étage : chambres
        f.bed(-5, 6, -8, VillageBlocks.RED_BED, Direction.NORTH);
        f.bed(-2, 6, -8, VillageBlocks.BLUE_BED, Direction.NORTH);
        f.set(-3, 6, -9, VillageBlocks.CHEST.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        f.builder().loot(f.pos(-3, 6, -9), "tavern");
        f.fill(2, 6, -9, 4, 7, -9, VillageBlocks.BOOKSHELF);
        f.table(2, 6, -4);
        f.stairs(1, 6, -4, VillageBlocks.SPRUCE_STAIRS, Direction.WEST, false);
        f.fill(-5, 6, -5, -2, 6, -3, VillageBlocks.WHITE_CARPET);
        f.lantern(-3, 8, -5, true);
        f.lantern(3, 8, -5, true);
        f.set(-6, 6, -1, VillageBlocks.POTTED_RED_TULIP);

        // jardinières sous les fenêtres de façade
        for (int x : new int[] {-5, -4, 4, 5}) {
            f.slab(x, 1, 1, VillageBlocks.SPRUCE_SLAB, true);
            f.set(x, 2, 1, (Block) (x % 2 == 0 ? VillageBlocks.POTTED_POPPY : VillageBlocks.POTTED_DANDELION));
        }
    }
}
