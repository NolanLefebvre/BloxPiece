package nolan.bloxpiece.registry;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import nolan.bloxpiece.BloxPiece;

/**
 * Génération du monde. Les filons eux-mêmes sont décrits en JSON (data/bloxpiece/worldgen/feature et placed_feature) :
 * ici, on les ajoute seulement aux biomes voulus.
 * <ul>
 *   <li>Minerai de Granite Marin : dans tous les océans, un filon sur ~3 tronçons (1 à 2 par tronçon choisi), posé AU NIVEAU du
 *       plancher océanique (heightmap OCEAN_FLOOR_WG) : il remplace le sable, le gravier, l'argile ou la roche de surface, donc
 *       il est toujours exposé à l'eau et se repère en nageant. Pour changer la rareté : "chance" et "count" dans
 *       placed_feature/seastone_ore.json ; la taille des filons : "size" dans feature/seastone_ore.json.</li>
 * </ul>
 */
public class ModWorldgen {

    public static final ResourceKey<PlacedFeature> SEASTONE_ORE =
            ResourceKey.create(Registries.PLACED_FEATURE, BloxPiece.id("seastone_ore"));

    public static void initialize() {
        BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_OCEAN), GenerationStep.Decoration.UNDERGROUND_ORES, SEASTONE_ORE);
    }
}
