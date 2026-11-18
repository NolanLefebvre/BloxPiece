package nolan.bloxpiece.village;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import nolan.bloxpiece.BloxPiece;

/**
 * Tous les blocs utilisés par le village, retrouvés par leur IDENTIFIANT (ex : "red_wool") dans le registre de Minecraft au
 * lieu des constantes Java Blocks.RED_WOOL. Une constante renommée ou déplacée dans une nouvelle version de Minecraft ne peut donc
 * plus empêcher la compilation : un bloc introuvable devient de l'air (avertissement dans la console) et le village se construit
 * quand même. Pour ajouter un bloc : une ligne ici, avec son identifiant vanilla (celui de la commande /give).
 */
public final class VillageBlocks {

    public static final Block AIR = get("air");
    public static final Block PODZOL = get("podzol");
    public static final Block COARSE_DIRT = get("coarse_dirt");
    public static final Block SAND = get("sand");
    public static final Block SNOW = get("snow");
    public static final Block MYCELIUM = get("mycelium");
    public static final Block STONE_BRICK_WALL = get("stone_brick_wall");
    public static final Block POLISHED_ANDESITE = get("polished_andesite");
    public static final Block YELLOW_WOOL = get("yellow_wool");
    public static final Block CYAN_WOOL = get("cyan_wool");
    public static final Block BLUE_WOOL = get("blue_wool");
    public static final Block ORANGE_WOOL = get("orange_wool");
    public static final Block LIME_WOOL = get("lime_wool");
    public static final Block PUMPKIN = get("pumpkin");
    public static final Block MELON = get("melon");
    public static final Block CAKE = get("cake");
    public static final Block STRIPPED_SPRUCE_WOOD = get("stripped_spruce_wood");
    public static final Block CALCITE = get("calcite");
    public static final Block CHISELED_STONE_BRICKS = get("chiseled_stone_bricks");
    public static final Block STONE_BRICK_SLAB = get("stone_brick_slab");
    public static final Block CRACKED_STONE_BRICKS = get("cracked_stone_bricks");
    public static final Block POLISHED_GRANITE_STAIRS = get("polished_granite_stairs");
    public static final Block POLISHED_GRANITE_SLAB = get("polished_granite_slab");
    public static final Block POTTED_AZALEA_BUSH = get("potted_azalea_bush");
    public static final Block POTTED_FERN = get("potted_fern");
    public static final Block ALLIUM = get("allium");
    public static final Block ANDESITE = get("andesite");
    public static final Block ANVIL = get("anvil");
    public static final Block AZURE_BLUET = get("azure_bluet");
    public static final Block BARREL = get("barrel");
    public static final Block BIRCH_DOOR = get("birch_door");
    public static final Block BIRCH_LEAVES = get("birch_leaves");
    public static final Block BIRCH_LOG = get("birch_log");
    public static final Block BIRCH_PLANKS = get("birch_planks");
    public static final Block BIRCH_STAIRS = get("birch_stairs");
    public static final Block BLUE_BED = get("blue_bed");
    public static final Block BOOKSHELF = get("bookshelf");
    public static final Block BRICKS = get("bricks");
    public static final Block BRICK_SLAB = get("brick_slab");
    public static final Block BRICK_STAIRS = get("brick_stairs");
    public static final Block BROWN_BED = get("brown_bed");
    public static final Block CAMPFIRE = get("campfire");
    public static final Block CAULDRON = get("cauldron");
    public static final Block CHEST = get("chest");
    public static final Block COBBLESTONE = get("cobblestone");
    public static final Block COMPOSTER = get("composter");
    public static final Block CORNFLOWER = get("cornflower");
    public static final Block CRAFTING_TABLE = get("crafting_table");
    public static final Block CYAN_CARPET = get("cyan_carpet");
    public static final Block DANDELION = get("dandelion");
    public static final Block DIRT = get("dirt");
    public static final Block DIRT_PATH = get("dirt_path");
    public static final Block FURNACE = get("furnace");
    public static final Block GLASS_PANE = get("glass_pane");
    public static final Block GRASS_BLOCK = get("grass_block");
    public static final Block GRAVEL = get("gravel");
    public static final Block GREEN_BED = get("green_bed");
    public static final Block GREEN_CARPET = get("green_carpet");
    public static final Block HAY_BLOCK = get("hay_block");
    public static final Block ICE = get("ice");
    public static final Block JUNGLE_LEAVES = get("jungle_leaves");
    public static final Block JUNGLE_LOG = get("jungle_log");
    public static final Block LADDER = get("ladder");
    public static final Block LANTERN = get("lantern");
    public static final Block LECTERN = get("lectern");
    public static final Block LIGHT_BLUE_BED = get("light_blue_bed");
    public static final Block LIGHT_BLUE_CARPET = get("light_blue_carpet");
    public static final Block LOOM = get("loom");
    public static final Block MOSSY_COBBLESTONE = get("mossy_cobblestone");
    public static final Block MOSSY_STONE_BRICKS = get("mossy_stone_bricks");
    public static final Block OAK_DOOR = get("oak_door");
    public static final Block OAK_FENCE = get("oak_fence");
    public static final Block OAK_HANGING_SIGN = get("oak_hanging_sign");
    public static final Block OAK_LEAVES = get("oak_leaves");
    public static final Block OAK_LOG = get("oak_log");
    public static final Block OAK_PLANKS = get("oak_planks");
    public static final Block OAK_PRESSURE_PLATE = get("oak_pressure_plate");
    public static final Block OAK_SLAB = get("oak_slab");
    public static final Block OAK_STAIRS = get("oak_stairs");
    public static final Block ORANGE_TULIP = get("orange_tulip");
    public static final Block OXEYE_DAISY = get("oxeye_daisy");
    public static final Block PACKED_ICE = get("packed_ice");
    public static final Block POPPY = get("poppy");
    public static final Block POTTED_BLUE_ORCHID = get("potted_blue_orchid");
    public static final Block POTTED_DANDELION = get("potted_dandelion");
    public static final Block POTTED_POPPY = get("potted_poppy");
    public static final Block POTTED_RED_TULIP = get("potted_red_tulip");
    public static final Block RED_BED = get("red_bed");
    public static final Block RED_CARPET = get("red_carpet");
    public static final Block RED_TULIP = get("red_tulip");
    public static final Block RED_WOOL = get("red_wool");
    public static final Block SHORT_GRASS = get("short_grass");
    public static final Block SMITHING_TABLE = get("smithing_table");
    public static final Block SMOKER = get("smoker");
    public static final Block SPRUCE_DOOR = get("spruce_door");
    public static final Block SPRUCE_FENCE = get("spruce_fence");
    public static final Block SPRUCE_LOG = get("spruce_log");
    public static final Block SPRUCE_PLANKS = get("spruce_planks");
    public static final Block SPRUCE_SLAB = get("spruce_slab");
    public static final Block SPRUCE_STAIRS = get("spruce_stairs");
    public static final Block STONE_BRICKS = get("stone_bricks");
    public static final Block STRIPPED_OAK_LOG = get("stripped_oak_log");
    public static final Block STRIPPED_SPRUCE_LOG = get("stripped_spruce_log");
    public static final Block TALL_GRASS = get("tall_grass");
    public static final Block WATER = get("water");
    public static final Block WHITE_CARPET = get("white_carpet");
    public static final Block WHITE_WOOL = get("white_wool");
    public static final Block YELLOW_BED = get("yellow_bed");
    public static final Block YELLOW_CARPET = get("yellow_carpet");

    public static final Block BIRCH_TRAPDOOR = get("birch_trapdoor");
    public static final Block BLAST_FURNACE = get("blast_furnace");
    public static final Block BROWN_CARPET = get("brown_carpet");
    public static final Block CARTOGRAPHY_TABLE = get("cartography_table");
    public static final Block COBBLESTONE_STAIRS = get("cobblestone_stairs");
    public static final Block CYAN_BED = get("cyan_bed");
    public static final Block DARK_OAK_DOOR = get("dark_oak_door");
    public static final Block DARK_OAK_LOG = get("dark_oak_log");
    public static final Block DARK_OAK_SLAB = get("dark_oak_slab");
    public static final Block DARK_OAK_STAIRS = get("dark_oak_stairs");
    public static final Block DARK_OAK_TRAPDOOR = get("dark_oak_trapdoor");
    public static final Block FLETCHING_TABLE = get("fletching_table");
    public static final Block GRINDSTONE = get("grindstone");
    public static final Block IRON_BARS = get("iron_bars");
    public static final Block MAGMA_BLOCK = get("magma_block");
    public static final Block OAK_TRAPDOOR = get("oak_trapdoor");
    public static final Block ORANGE_BED = get("orange_bed");
    public static final Block ORANGE_CARPET = get("orange_carpet");
    public static final Block PURPLE_BED = get("purple_bed");
    public static final Block PURPLE_CARPET = get("purple_carpet");
    public static final Block SPRUCE_TRAPDOOR = get("spruce_trapdoor");
    public static final Block STONE_BRICK_STAIRS = get("stone_brick_stairs");
    public static final Block STRIPPED_DARK_OAK_LOG = get("stripped_dark_oak_log");
    public static final Block TERRACOTTA = get("terracotta");
    public static final Block WHITE_TERRACOTTA = get("white_terracotta");

    public static final Block COBBLESTONE_WALL = get("cobblestone_wall");
    public static final Block MOSSY_COBBLESTONE_WALL = get("mossy_cobblestone_wall");
    public static final Block ROSE_BUSH = get("rose_bush");
    public static final Block SWEET_BERRY_BUSH = get("sweet_berry_bush");

    private VillageBlocks() {
    }

    private static Block get(String id) {
        Identifier key = Identifier.fromNamespaceAndPath("minecraft", id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            BloxPiece.LOGGER.warn("[BloxPiece] Village : bloc minecraft:{} introuvable dans cette version, remplacé par de l'air", id);
        }
        return BuiltInRegistries.BLOCK.getValue(key); // registre "par défaut" : un identifiant inconnu donne l'air
    }
}
