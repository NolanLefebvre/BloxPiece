package nolan.bloxpiece.village;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

import static nolan.bloxpiece.village.VillageBlocks.*;

/**
 * Intérieurs des bâtiments (repère local, voir Frame et Building : intérieur x de -hw+1 à hw-1, z de zb+1 à -1, rez-de-chaussée
 * y 1..3, étage y 5..7). Chaque intérieur garde un espace libre au centre (futurs PNJ / objets clés). Coffres : tables de butin
 * chests/starter_village/*.
 */
public final class Interiors {

    private Interiors() {
    }

    private static void chest(Frame f, int x, int y, int z, Direction facing, String loot) {
        f.facing(x, y, z, CHEST, facing);
        f.builder().loot(f.pos(x, y, z), loot);
    }

    /** Échelle vers l'étage dans le coin arrière droit (trémie dans le plancher). */
    private static void ladderUp(Frame f, Building b) {
        f.ladder(b.halfWidth - 1, 1, Building.STOREY, b.backZ() + 1, LADDER, Direction.SOUTH);
    }

    // ------------------------------------------------------------------ maison

    public static void home(Frame f, Building b, Block bed, Block carpet) {
        int hw = b.halfWidth, zb = b.backZ();
        // rez-de-chaussée : cuisine et coin repas (le centre reste libre)
        f.facing(-hw + 1, 1, zb + 1, FURNACE, Direction.SOUTH);
        f.set(-hw + 2, 1, zb + 1, CRAFTING_TABLE);
        f.barrel(-hw + 1, 1, zb + 2, Direction.UP);
        f.table(-hw + 1, 1, -2);
        f.stairs(-hw + 1, 1, -1, OAK_STAIRS, Direction.SOUTH, false);
        f.set(hw - 1, 1, -1, POTTED_POPPY);
        f.fill(-1, 1, zb + 3, 1, 1, -2, carpet);
        if (b.floors >= 2) {
            ladderUp(f, b);
            f.lantern(0, 3, (zb + 1) / 2, true);
            // étage : chambre
            f.bed(-hw + 1, 5, zb + 2, bed, Direction.NORTH);
            chest(f, -hw + 2, 5, zb + 1, Direction.SOUTH, "house");
            f.set(hw - 2, 5, zb + 1, BOOKSHELF);
            f.set(hw - 1, 5, -1, BOOKSHELF);
            f.fill(-1, 5, zb + 3, 1, 5, -2, carpet);
            f.set(hw - 1, 5, (zb + 1) / 2, LANTERN);
        } else {
            f.bed(hw - 1, 1, zb + 2, bed, Direction.NORTH);
            chest(f, hw - 2, 1, zb + 1, Direction.SOUTH, "house");
            f.set(hw - 1, 1, -2, BOOKSHELF);
        }
    }

    // ------------------------------------------------------------------ échoppes

    /** Épicerie : étagères de tonneaux et de foin, comptoir sur rue (Building.shopFront), place du marchand derrière. */
    public static void grocer(Frame f, Building b) {
        int hw = b.halfWidth, zb = b.backZ();
        for (int x = -hw + 1; x <= hw - 1; x++) {
            f.barrel(x, 1, zb + 1, Direction.SOUTH);
            if ((x & 1) == 0) {
                f.barrel(x, 2, zb + 1, Direction.SOUTH);
            }
        }
        f.set(-hw + 1, 1, zb + 2, HAY_BLOCK);
        f.set(-hw + 1, 1, zb + 3, COMPOSTER);
        chest(f, hw - 1, 1, -2, Direction.WEST, "house");
        f.lantern(0, 3, zb + 2, true);
        if (b.floors >= 2) {
            ladderUp(f, b);
            f.bed(-hw + 1, 5, zb + 2, ORANGE_BED, Direction.NORTH);
            f.fill(-1, 5, zb + 2, 1, 5, -2, ORANGE_CARPET);
            f.set(hw - 2, 5, zb + 1, LANTERN);
        }
    }

    /** Échoppe d'artisan (tailleur / cartographe) : métier à tisser, table de cartographie, bibliothèques. */
    public static void craftsman(Frame f, Building b) {
        int hw = b.halfWidth, zb = b.backZ();
        f.set(-hw + 1, 1, zb + 1, LOOM);
        f.set(-hw + 2, 1, zb + 1, CARTOGRAPHY_TABLE);
        f.set(hw - 2, 1, zb + 1, BOOKSHELF);
        f.set(hw - 2, 2, zb + 1, BOOKSHELF);
        f.set(-hw + 1, 1, zb + 2, FLETCHING_TABLE);
        chest(f, hw - 1, 1, -2, Direction.WEST, "house");
        f.lantern(0, 3, zb + 2, true);
        if (b.floors >= 2) {
            ladderUp(f, b);
            f.bed(-hw + 1, 5, zb + 2, PURPLE_BED, Direction.NORTH);
            f.fill(-1, 5, zb + 2, 1, 5, -2, PURPLE_CARPET);
            f.set(hw - 2, 5, zb + 1, LANTERN);
        }
    }

    // ------------------------------------------------------------------ forge

    public static void forge(Frame f, Building b) {
        int hw = b.halfWidth, zb = b.backZ();
        f.fill(-hw + 1, 0, zb + 1, hw - 1, 0, -1, STONE_BRICKS);   // sol de pierre
        f.facing(-hw + 2, 1, zb + 1, BLAST_FURNACE, Direction.SOUTH);
        f.facing(-hw + 3, 1, zb + 1, FURNACE, Direction.SOUTH);
        f.set(-hw + 1, 0, zb + 2, MAGMA_BLOCK);                     // braises du foyer
        f.facing(0, 1, zb + 3, ANVIL, Direction.EAST);
        f.set(hw - 1, 1, zb + 1, SMITHING_TABLE);
        f.facing(hw - 1, 1, zb + 2, GRINDSTONE, Direction.WEST);
        f.set(hw - 1, 1, -2, CAULDRON);
        chest(f, hw - 2, 1, zb + 1, Direction.SOUTH, "forge");
        f.barrel(-hw + 1, 1, -1, Direction.UP);
        f.set(1, 1, zb + 1, LANTERN);
        // fenêtres à barreaux sur la façade (au lieu de vitres)
        for (int x : new int[] {-(hw - 1), hw - 1}) {
            f.set(x, 2, 0, IRON_BARS);
        }
    }

    // ------------------------------------------------------------------ entrepôt

    public static void warehouse(Frame f, Building b) {
        int hw = b.halfWidth, zb = b.backZ();
        for (int z = zb + 1; z <= -2; z++) {
            int stack = 1 + Math.floorMod(z * 7, 3);
            for (int y = 1; y <= stack; y++) {
                f.barrel(-hw + 1, y, z, Direction.UP);
                if (z % 2 == 0) {
                    f.barrel(hw - 1, y, z, Direction.UP);
                }
            }
        }
        f.fill(-hw + 2, 1, zb + 1, -hw + 3, 2, zb + 1, HAY_BLOCK);
        f.fill(hw - 3, 1, zb + 1, hw - 2, 1, zb + 1, HAY_BLOCK);
        chest(f, 0, 1, zb + 1, Direction.SOUTH, "warehouse");
        chest(f, 1, 1, zb + 1, Direction.SOUTH, "warehouse");
        f.set(-1, 1, zb + 1, CRAFTING_TABLE);
        f.set(hw - 2, 1, -2, CAULDRON);
        f.set(0, 1, zb + 3, LANTERN);
    }

    // ------------------------------------------------------------------ maison du maire

    public static void mayor(Frame f, Building b) {
        int hw = b.halfWidth, zb = b.backZ();
        f.fill(-hw + 1, 1, zb + 1, -hw + 3, 2, zb + 1, BOOKSHELF);
        f.facing(0, 1, zb + 2, LECTERN, Direction.SOUTH);
        f.table(hw - 2, 1, zb + 2);
        f.stairs(hw - 2, 1, zb + 3, DARK_OAK_STAIRS, Direction.SOUTH, false);
        chest(f, hw - 1, 1, zb + 1, Direction.SOUTH, "tavern");
        f.fill(-2, 1, zb + 4, 2, 1, -2, RED_CARPET);
        f.set(-hw + 1, 1, -1, POTTED_BLUE_ORCHID);
        ladderUp(f, b);
        f.lantern(0, 3, (zb + 1) / 2, true);
        f.bed(-hw + 1, 5, zb + 2, RED_BED, Direction.NORTH);
        f.bed(-hw + 3, 5, zb + 2, RED_BED, Direction.NORTH);
        f.fill(-1, 5, zb + 4, 1, 5, -2, WHITE_CARPET);
        f.set(hw - 2, 5, zb + 1, LANTERN);
        f.set(hw - 1, 5, -2, BOOKSHELF);
    }

    // ------------------------------------------------------------------ taverne

    /**
     * Taverne (hw 7, profondeur 11 : intérieur x -6..6, z -9..-1). Rez-de-chaussée :
     * <ul>
     *   <li>fond : cheminée (x = chimneyX), tonneaux contre le mur, comptoir en z = -7 de x -3 à 3, passage derrière le bar par
     *       x = 4..5 ; la place du barman (z = -8) reste libre ;</li>
     *   <li>escalier le long du mur est (x = 6) qui MONTE vers le fond (z -4 -> -7) : départ dégagé en z = -3, arrivée à
     *       l'étage en z = -8, aucun meuble sur son passage ;</li>
     *   <li>tables et tabourets à l'ouest (et une table à l'est, loin de l'escalier) ; centre et devant du comptoir libres (PNJ).</li>
     * </ul>
     * Étage : deux lits contre le fond à l'ouest, bibliothèque, table, coin tapis ; arrivée de l'escalier dégagée.
     */
    public static void tavern(Frame f, Building b) {
        int hw = b.halfWidth, zb = b.backZ();
        // foyer au pied de la cheminée
        int cx = b.chimneyX;
        f.fill(cx - 1, 1, zb + 1, cx - 1, 3, zb + 1, BRICKS);
        f.fill(cx + 1, 1, zb + 1, cx + 1, 3, zb + 1, BRICKS);
        f.set(cx, 3, zb + 1, BRICKS);
        f.set(cx, 2, zb + 1, AIR);
        f.set(cx, 1, zb + 1, CAMPFIRE);
        // tonneaux contre le mur du fond, derrière le comptoir
        for (int x = -2; x <= 4; x++) {
            f.barrel(x, 1, zb + 1, Direction.SOUTH);
            if ((x & 1) == 0) {
                f.barrel(x, 2, zb + 1, Direction.SOUTH);
            }
        }
        // comptoir : une rangée de rondins écorcés, sans plateau en dalles au-dessus (le Tavernier reste bien visible)
        for (int x = -3; x <= 3; x++) {
            f.log(x, 1, zb + 3, STRIPPED_DARK_OAK_LOG, Direction.Axis.X);
        }
        // escalier vers l'étage, le long du mur est, montant vers le fond ; trémie dans le plancher au-dessus
        for (int i = 0; i < 4; i++) {
            f.stairs(hw - 1, 1 + i, -4 - i, SPRUCE_STAIRS, Direction.NORTH, false);
            f.set(hw - 1, Building.STOREY, -4 - i, AIR);
        }
        f.stairs(hw - 1, Building.STOREY, -7, SPRUCE_STAIRS, Direction.NORTH, false); // dernière marche, au niveau de l'étage
        f.set(hw - 2, Building.STOREY, -7, SPRUCE_PLANKS);                            // palier
        // tables et tabourets : deux à l'ouest, une à l'est près de l'entrée (loin de l'escalier)
        for (int[] t : new int[][] {{-hw + 2, -2}, {-hw + 2, -5}, {3, -2}}) {
            f.table(t[0], 1, t[1]);
            f.stairs(t[0], 1, t[1] + 1, OAK_STAIRS, Direction.SOUTH, false);
            f.stairs(t[0], 1, t[1] - 1, OAK_STAIRS, Direction.NORTH, false);
        }
        for (int[] l : new int[][] {{-3, -4}, {2, -4}}) { // plus de lanterne au-dessus du Tavernier (derrière le comptoir)
            f.lantern(l[0], 3, l[1], true);
        }
        f.fill(-1, 1, -2, 0, 1, -1, RED_CARPET);
        // étage : chambres
        f.bed(-hw + 1, 5, zb + 2, RED_BED, Direction.NORTH);
        f.bed(-hw + 3, 5, zb + 2, BLUE_BED, Direction.NORTH);
        chest(f, -hw + 2, 5, zb + 1, Direction.SOUTH, "tavern");
        f.fill(0, 5, zb + 1, 2, 6, zb + 1, BOOKSHELF);
        f.table(1, 5, -4);
        f.stairs(0, 5, -4, SPRUCE_STAIRS, Direction.WEST, false);
        f.fill(-hw + 1, 5, -4, -hw + 4, 5, -2, WHITE_CARPET);
        f.set(-hw + 1, 5, -1, POTTED_RED_TULIP);
        f.set(3, 5, -1, LANTERN);
    }

    /** Porche couvert de la taverne : plancher, poteaux, auvent, enseigne suspendue, tables dehors. */
    public static void tavernPorch(Frame f, Building b) {
        int hw = b.halfWidth;
        f.fill(-hw + 2, 0, 1, hw - 2, 0, 3, SPRUCE_PLANKS);
        for (int x : new int[] {-hw + 2, hw - 2}) {
            for (int y = 1; y <= 3; y++) {
                f.log(x, y, 3, DARK_OAK_LOG, Direction.Axis.Y);
            }
        }
        for (int x = -hw + 1; x <= hw - 1; x++) {
            for (int z = 1; z <= 4; z++) {
                f.slab(x, 4, z, DARK_OAK_SLAB, false);
            }
            f.stairs(x, 4, 4, DARK_OAK_STAIRS, Direction.NORTH, false);
        }
        f.set(0, 3, 3, OAK_HANGING_SIGN.defaultBlockState()
                .trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACHED, true)
                .trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ROTATION_16, 0));
        f.lantern(-3, 3, 2, true);
        f.lantern(3, 3, 2, true);
        for (int x : new int[] {-hw + 4, hw - 4}) {
            f.table(x, 1, 2);
            f.stairs(x - 1, 1, 2, SPRUCE_STAIRS, Direction.WEST, false);
            f.stairs(x + 1, 1, 2, SPRUCE_STAIRS, Direction.EAST, false);
        }
        f.barrel(-hw + 2, 1, 1, Direction.UP);
        f.barrel(hw - 2, 1, 1, Direction.UP);
        f.barrel(hw - 2, 2, 1, Direction.UP);
        // marches pour descendre du porche
        f.stairs(-1, 0, 4, SPRUCE_STAIRS, Direction.NORTH, false);
        f.stairs(0, 0, 4, SPRUCE_STAIRS, Direction.NORTH, false);
    }
}
