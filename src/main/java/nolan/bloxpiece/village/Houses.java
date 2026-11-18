package nolan.bloxpiece.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Les 5 maisons du village, toutes différentes (repère local, voir Frame : façade / porte en z = 0, la maison s'étend vers -Z).
 * Chacune est meublée mais garde de la place libre au centre ({@code *_FREE} : emplacement pour un futur PNJ ou un objet clé).
 * Coffres : table de butin chests/starter_village/house.
 */
public final class Houses {

    /** Emplacements libres (repère local de chaque maison) : futurs PNJ / objets clés. */
    public static final BlockPos PORCH_HOUSE_FREE = new BlockPos(0, 1, -3);
    public static final BlockPos BALCONY_HOUSE_FREE = new BlockPos(0, 1, -4);
    public static final BlockPos WORKSHOP_FREE = new BlockPos(2, 1, -3);
    public static final BlockPos L_HOUSE_FREE = new BlockPos(1, 1, -2);
    public static final BlockPos DORMER_HOUSE_FREE = new BlockPos(0, 1, -4);

    private Houses() {
    }

    private static void chest(Frame f, int x, int y, int z, Direction facing) {
        f.facing(x, y, z, VillageBlocks.CHEST, facing);
        f.builder().loot(f.pos(x, y, z), "house");
    }

    private static void corners(Frame f, int x1, int z1, int x2, int z2, int y1, int y2, net.minecraft.world.level.block.Block log) {
        for (int y = y1; y <= y2; y++) {
            f.log(x1, y, z1, log, Direction.Axis.Y);
            f.log(x2, y, z1, log, Direction.Axis.Y);
            f.log(x1, y, z2, log, Direction.Axis.Y);
            f.log(x2, y, z2, log, Direction.Axis.Y);
        }
    }

    /** Jardinière sous une fenêtre de façade : dalle en encorbellement + pot de fleurs. */
    private static void planter(Frame f, int x, int y, int z, boolean red) {
        f.slab(x, y - 1, z, VillageBlocks.SPRUCE_SLAB, true);
        f.set(x, y, z, (Block) (red ? VillageBlocks.POTTED_RED_TULIP : VillageBlocks.POTTED_DANDELION));
    }

    // ------------------------------------------------------------------ 1. Maison à porche (7 x 7, toit de sapin)

    public static void porchHouse(Frame f) {
        f.foundation(-3, -6, 3, 0, VillageBlocks.COBBLESTONE);
        f.fill(-2, 0, -5, 2, 0, -1, VillageBlocks.OAK_PLANKS);
        f.air(-2, 1, -5, 2, 7, -1);
        f.walls(-3, -6, 3, 0, 1, 3, VillageBlocks.OAK_PLANKS);
        corners(f, -3, -6, 3, 0, 0, 3, VillageBlocks.SPRUCE_LOG);
        f.set(-2, 2, 0, VillageBlocks.GLASS_PANE);
        f.set(2, 2, 0, VillageBlocks.GLASS_PANE);
        f.fill(-3, 2, -4, -3, 2, -2, VillageBlocks.GLASS_PANE);
        f.fill(3, 2, -4, 3, 2, -2, VillageBlocks.GLASS_PANE);
        f.set(0, 2, -6, VillageBlocks.GLASS_PANE);
        f.door(0, 1, 0, VillageBlocks.OAK_DOOR, Direction.SOUTH, false);
        f.gableRoofX(-4, 4, -7, 1, 4, VillageBlocks.SPRUCE_STAIRS, VillageBlocks.SPRUCE_SLAB, VillageBlocks.OAK_PLANKS);
        // porche : plancher, poteaux, auvent, banc
        f.fill(-3, 0, 1, 3, 0, 2, VillageBlocks.SPRUCE_PLANKS);
        f.fill(-3, 1, 2, -3, 3, 2, VillageBlocks.OAK_FENCE);
        f.fill(3, 1, 2, 3, 3, 2, VillageBlocks.OAK_FENCE);
        for (int x = -3; x <= 3; x++) {
            f.slab(x, 4, 2, VillageBlocks.SPRUCE_SLAB, false);
        }
        f.stairs(-2, 1, 1, VillageBlocks.SPRUCE_STAIRS, Direction.NORTH, false);
        f.lantern(0, 3, 2, true);
        planter(f, -2, 2, 1, true);
        planter(f, 2, 2, 1, false);
        // intérieur
        f.bed(-2, 1, -4, VillageBlocks.RED_BED, Direction.NORTH);
        chest(f, -1, 1, -5, Direction.SOUTH);
        f.set(2, 1, -5, VillageBlocks.CRAFTING_TABLE);
        f.facing(2, 1, -4, VillageBlocks.FURNACE, Direction.WEST);
        f.set(2, 1, -2, VillageBlocks.BOOKSHELF);
        f.fill(-1, 1, -3, 1, 1, -2, VillageBlocks.LIGHT_BLUE_CARPET);
        f.lantern(0, 3, -3, true);
        f.set(-2, 1, -1, VillageBlocks.POTTED_POPPY);
        f.barrel(-4, 1, 1, Direction.UP);
        f.barrel(-4, 2, 1, Direction.UP);
    }

    // ------------------------------------------------------------------ 2. Maison à balcon (7 x 9, deux étages, tuiles rouges)

    public static void balconyHouse(Frame f) {
        f.foundation(-3, -8, 3, 0, VillageBlocks.STONE_BRICKS);
        f.fill(-2, 0, -7, 2, 0, -1, VillageBlocks.SPRUCE_PLANKS);
        f.air(-2, 1, -7, 2, 11, -1);
        f.walls(-3, -8, 3, 0, 1, 3, VillageBlocks.COBBLESTONE);               // rez-de-chaussée en pierre
        f.walls(-3, -8, 3, 0, 4, 4, VillageBlocks.STRIPPED_OAK_LOG);
        f.walls(-3, -8, 3, 0, 5, 7, VillageBlocks.BIRCH_PLANKS);              // étage en bois clair
        f.fill(-2, 4, -7, 2, 4, -1, VillageBlocks.OAK_PLANKS);
        corners(f, -3, -8, 3, 0, 0, 7, VillageBlocks.OAK_LOG);
        f.set(-2, 2, 0, VillageBlocks.GLASS_PANE);
        f.set(2, 2, 0, VillageBlocks.GLASS_PANE);
        f.fill(-3, 2, -6, -3, 2, -2, VillageBlocks.GLASS_PANE);
        f.fill(3, 6, -6, 3, 6, -2, VillageBlocks.GLASS_PANE);
        f.fill(-3, 6, -6, -3, 6, -2, VillageBlocks.GLASS_PANE);
        f.door(0, 1, 0, VillageBlocks.BIRCH_DOOR, Direction.SOUTH, false);
        f.door(0, 5, 0, VillageBlocks.BIRCH_DOOR, Direction.SOUTH, true);      // porte-fenêtre du balcon
        // balcon en bois sur la façade
        for (int x = -2; x <= 2; x++) {
            f.slab(x, 4, 1, VillageBlocks.SPRUCE_SLAB, true);
            f.set(x, 5, 2, VillageBlocks.SPRUCE_FENCE);
        }
        f.set(-3, 5, 1, VillageBlocks.SPRUCE_FENCE);
        f.set(3, 5, 1, VillageBlocks.SPRUCE_FENCE);
        f.set(-3, 5, 2, VillageBlocks.SPRUCE_FENCE);
        f.set(3, 5, 2, VillageBlocks.SPRUCE_FENCE);
        f.set(-2, 6, 2, VillageBlocks.POTTED_BLUE_ORCHID);
        f.set(2, 6, 2, VillageBlocks.POTTED_RED_TULIP);
        f.gableRoofX(-4, 4, -9, 2, 8, VillageBlocks.BRICK_STAIRS, VillageBlocks.BRICK_SLAB, VillageBlocks.BIRCH_PLANKS);
        // échelle vers l'étage
        f.fill(2, 1, -7, 2, 4, -7, VillageBlocks.LADDER.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        // rez-de-chaussée : cuisine
        f.facing(-2, 1, -7, VillageBlocks.FURNACE, Direction.SOUTH);
        f.facing(-1, 1, -7, VillageBlocks.SMOKER, Direction.SOUTH);
        f.set(0, 1, -7, VillageBlocks.CAULDRON);
        f.table(-1, 1, -3);
        f.stairs(-2, 1, -3, VillageBlocks.BIRCH_STAIRS, Direction.WEST, false);
        f.lantern(0, 3, -4, true);
        // étage : chambre
        f.bed(-2, 5, -6, VillageBlocks.YELLOW_BED, Direction.NORTH);
        chest(f, 0, 5, -7, Direction.SOUTH);
        f.fill(-1, 5, -4, 1, 5, -2, VillageBlocks.YELLOW_CARPET);
        f.set(2, 5, -2, VillageBlocks.BOOKSHELF);
        f.lantern(0, 7, -4, true);
    }

    // ------------------------------------------------------------------ 3. Atelier avec remise ouverte (9 x 7)

    public static void workshop(Frame f) {
        f.foundation(-4, -6, 4, 0, VillageBlocks.COBBLESTONE);
        // partie fermée (x -4..0)
        f.fill(-3, 0, -5, -1, 0, -1, VillageBlocks.SPRUCE_PLANKS);
        f.air(-3, 1, -5, -1, 7, -1);
        f.walls(-4, -6, 0, 0, 1, 3, VillageBlocks.SPRUCE_PLANKS);
        corners(f, -4, -6, 0, 0, 0, 3, VillageBlocks.STRIPPED_SPRUCE_LOG);
        f.set(-3, 2, 0, VillageBlocks.GLASS_PANE);
        f.fill(-4, 2, -4, -4, 2, -2, VillageBlocks.GLASS_PANE);
        f.door(-1, 1, 0, VillageBlocks.SPRUCE_DOOR, Direction.SOUTH, true);
        f.air(0, 1, -3, 0, 2, -3);                                    // passage intérieur vers la remise
        f.gableRoofX(-5, 1, -7, 1, 4, VillageBlocks.OAK_STAIRS, VillageBlocks.OAK_SLAB, VillageBlocks.SPRUCE_PLANKS);
        // remise ouverte (x 1..4) : sol en pierre, poteaux, toit plat en dalles
        f.fill(1, 0, -6, 4, 0, 0, VillageBlocks.STONE_BRICKS);
        for (int z : new int[] {0, -6}) {
            f.fill(4, 1, z, 4, 3, z, VillageBlocks.SPRUCE_FENCE);
        }
        for (int x = 1; x <= 5; x++) {
            for (int z = -7; z <= 1; z++) {
                f.slab(x, 4, z, VillageBlocks.SPRUCE_SLAB, false);
            }
        }
        f.facing(1, 1, -5, VillageBlocks.ANVIL, Direction.SOUTH);
        f.set(2, 1, -6 + 1, VillageBlocks.SMITHING_TABLE);
        f.set(3, 1, -5, VillageBlocks.CRAFTING_TABLE);
        f.barrel(4, 1, -4, Direction.UP);
        f.barrel(4, 2, -4, Direction.UP);
        f.barrel(4, 1, -3, Direction.WEST);
        f.set(4, 1, -1, VillageBlocks.HAY_BLOCK);
        f.set(3, 1, -1, VillageBlocks.COMPOSTER);
        f.lantern(2, 3, -3, true);
        // intérieur : chambre de l'artisan
        f.bed(-3, 1, -4, VillageBlocks.BROWN_BED, Direction.NORTH);
        chest(f, -2, 1, -5, Direction.SOUTH);
        f.set(-1, 1, -5, VillageBlocks.LOOM);
        f.lantern(-2, 3, -3, true);
        f.set(-1, 1, -1, VillageBlocks.POTTED_POPPY);
        planter(f, -3, 2, 1, true);
    }

    // ------------------------------------------------------------------ 4. Maison en L (9 x 9, deux toits croisés)

    public static void lHouse(Frame f) {
        // aile avant (x -4..4, z -4..0) et aile arrière (x -4..-1, z -8..-5)
        f.foundation(-4, -4, 4, 0, VillageBlocks.STONE_BRICKS);
        f.foundation(-4, -8, -1, -5, VillageBlocks.STONE_BRICKS);
        f.fill(-3, 0, -3, 3, 0, -1, VillageBlocks.OAK_PLANKS);
        f.fill(-3, 0, -7, -2, 0, -4, VillageBlocks.OAK_PLANKS);
        f.air(-3, 1, -3, 3, 6, -1);
        f.air(-3, 1, -7, -2, 6, -4);
        f.walls(-4, -4, 4, 0, 1, 3, VillageBlocks.SPRUCE_PLANKS);
        f.walls(-4, -8, -1, -4, 1, 3, VillageBlocks.SPRUCE_PLANKS);
        f.air(-3, 1, -4, -2, 3, -4);                                    // ouverture entre les deux ailes
        corners(f, -4, -4, 4, 0, 0, 3, VillageBlocks.STRIPPED_OAK_LOG);
        corners(f, -4, -8, -1, -4, 0, 3, VillageBlocks.STRIPPED_OAK_LOG);
        f.fill(-2, 2, 0, -1, 2, 0, VillageBlocks.GLASS_PANE);
        f.fill(2, 2, 0, 3, 2, 0, VillageBlocks.GLASS_PANE);
        f.fill(4, 2, -3, 4, 2, -1, VillageBlocks.GLASS_PANE);
        f.fill(-4, 2, -7, -4, 2, -5, VillageBlocks.GLASS_PANE);
        f.door(0, 1, 0, VillageBlocks.OAK_DOOR, Direction.SOUTH, false);
        f.gableRoofX(-5, 5, -5, 1, 4, VillageBlocks.BRICK_STAIRS, VillageBlocks.BRICK_SLAB, VillageBlocks.SPRUCE_PLANKS);
        f.gableRoofZ(-5, 0, -9, -5, 4, VillageBlocks.BRICK_STAIRS, VillageBlocks.BRICK_SLAB, VillageBlocks.SPRUCE_PLANKS);
        // intérieur
        f.bed(-3, 1, -6, VillageBlocks.GREEN_BED, Direction.NORTH);
        chest(f, -2, 1, -7, Direction.SOUTH);
        f.set(-3, 1, -4, VillageBlocks.BOOKSHELF);
        f.table(3, 1, -2);
        f.stairs(3, 1, -1, VillageBlocks.OAK_STAIRS, Direction.SOUTH, false);
        f.set(3, 1, -3, VillageBlocks.CRAFTING_TABLE);
        f.fill(-1, 1, -2, 1, 1, -1, VillageBlocks.GREEN_CARPET);
        f.lantern(0, 3, -2, true);
        f.lantern(-2, 3, -6, true);
        planter(f, -2, 2, 1, false);
        planter(f, 3, 2, 1, true);
        // tonneaux et caisses empilés dehors, dans le creux du L
        f.barrel(1, 1, -6, Direction.UP);
        f.barrel(2, 1, -6, Direction.NORTH);
        f.barrel(1, 2, -6, Direction.UP);
        f.set(2, 1, -7, VillageBlocks.HAY_BLOCK);
    }

    // ------------------------------------------------------------------ 5. Maison à lucarne (7 x 8, toit pentu)

    public static void dormerHouse(Frame f) {
        f.foundation(-3, -7, 3, 0, VillageBlocks.MOSSY_COBBLESTONE);
        f.fill(-2, 0, -6, 2, 0, -1, VillageBlocks.BIRCH_PLANKS);
        f.air(-2, 1, -6, 2, 8, -1);
        f.walls(-3, -7, 3, 0, 1, 3, VillageBlocks.BIRCH_PLANKS);
        corners(f, -3, -7, 3, 0, 0, 3, VillageBlocks.BIRCH_LOG);
        f.set(-2, 2, 0, VillageBlocks.GLASS_PANE);
        f.set(2, 2, 0, VillageBlocks.GLASS_PANE);
        f.fill(3, 2, -5, 3, 2, -2, VillageBlocks.GLASS_PANE);
        f.fill(-3, 2, -5, -3, 2, -2, VillageBlocks.GLASS_PANE);
        f.door(0, 1, 0, VillageBlocks.BIRCH_DOOR, Direction.SOUTH, false);
        f.gableRoofX(-4, 4, -8, 1, 4, VillageBlocks.SPRUCE_STAIRS, VillageBlocks.SPRUCE_SLAB, VillageBlocks.BIRCH_PLANKS);
        // lucarne sur le pan avant du toit : petite fenêtre et son petit toit
        f.set(-1, 5, 0, VillageBlocks.BIRCH_PLANKS);
        f.set(1, 5, 0, VillageBlocks.BIRCH_PLANKS);
        f.set(0, 5, 0, VillageBlocks.GLASS_PANE);
        f.fill(-1, 6, 0, 1, 6, 0, VillageBlocks.BIRCH_PLANKS);
        f.air(0, 5, -1, 0, 6, -1);                                       // passage vers le grenier
        f.fill(-1, 5, -1, -1, 6, -1, VillageBlocks.BIRCH_PLANKS);              // joues de la lucarne
        f.fill(1, 5, -1, 1, 6, -1, VillageBlocks.BIRCH_PLANKS);
        f.fill(-1, 7, -1, 1, 7, -1, VillageBlocks.SPRUCE_SLAB.defaultBlockState());
        f.stairs(-1, 7, 0, VillageBlocks.SPRUCE_STAIRS, Direction.EAST, false);
        f.stairs(1, 7, 0, VillageBlocks.SPRUCE_STAIRS, Direction.WEST, false);
        f.slab(0, 7, 0, VillageBlocks.SPRUCE_SLAB, false);
        f.fill(-1, 4, -6, 1, 4, -1, VillageBlocks.SPRUCE_PLANKS);              // plancher du grenier
        // intérieur
        f.bed(2, 1, -5, VillageBlocks.LIGHT_BLUE_BED, Direction.NORTH);
        chest(f, 1, 1, -6, Direction.SOUTH);
        f.set(-2, 1, -6, VillageBlocks.BOOKSHELF);
        f.set(-2, 2, -6, VillageBlocks.BOOKSHELF);
        f.set(-1, 1, -6, VillageBlocks.BOOKSHELF);
        f.set(-2, 1, -3, VillageBlocks.LECTERN);
        f.fill(-1, 1, -4, 1, 1, -2, VillageBlocks.CYAN_CARPET);
        f.lantern(0, 3, -3, true);
        f.set(2, 1, -1, VillageBlocks.POTTED_BLUE_ORCHID);
        planter(f, -2, 2, 1, true);
        planter(f, 2, 2, 1, true);
        f.barrel(3, 1, 1, Direction.UP);
    }
}
