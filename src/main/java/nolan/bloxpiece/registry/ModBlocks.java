package nolan.bloxpiece.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.block.TreasureChestBlock;

/** Tous les blocs du mod. Un bloc est enregistré ici ; son objet (BlockItem) est enregistré dans ModItems. */
public class ModBlocks {

    /** Le Coffre au Trésor posé (modèle 3D du coffre pirate) : clic droit = l'ouvrir (roulette des fruits du démon). */
    public static final Block TREASURE_CHEST = register("treasure_chest", new TreasureChestBlock(
            properties("treasure_chest")
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)          // comme un coffre en bois
                    .sound(SoundType.WOOD)
                    .noOcclusion()));        // le modèle ne remplit pas tout le bloc

    /**
     * Minerai de Granite Marin (Seastone) : au fond des océans, affleurant sur le plancher océanique (voir ModWorldgen). Se mine à
     * la pioche en fer ou mieux (tags mineable/pickaxe et needs_iron_tool) et donne du Granite Marin brut (fortune comprise).
     */
    public static final Block SEASTONE_ORE = register("seastone_ore", new Block(
            properties("seastone_ore")
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(4.5F, 6.0F)            // un peu plus dur que le minerai de fer
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    private static BlockBehaviour.Properties properties(String name) {
        return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, BloxPiece.id(name)));
    }

    private static Block register(String name, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, BloxPiece.id(name)), block);
    }

    /** Force le chargement de la classe (les blocs sont enregistrés par les champs statiques ci-dessus). */
    public static void initialize() {
    }
}
