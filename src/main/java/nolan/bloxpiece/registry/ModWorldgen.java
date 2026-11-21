package nolan.bloxpiece.registry;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.worldgen.FoundationProcessor;

/**
 * Génération du monde. Les filons eux-mêmes sont décrits en JSON (data/bloxpiece/worldgen/feature et placed_feature) :
 * ici, on les ajoute seulement aux biomes voulus.
 * <ul>
 *   <li>Minerai de Granite Marin : dans tous les océans, un filon sur ~3 tronçons (1 à 2 par tronçon choisi), posé AU NIVEAU du
 *       plancher océanique (heightmap OCEAN_FLOOR_WG) : il remplace le sable, le gravier, l'argile ou la roche de surface, donc
 *       il est toujours exposé à l'eau et se repère en nageant. Pour changer la rareté : "chance" et "count" dans
 *       placed_feature/seastone_ore.json ; la taille des filons : "size" dans feature/seastone_ore.json.</li>
 *   <li>Camp de bandits (structure/bandit_camp.nbt, généré par src/blockbench/structures/make_bandit_camp.py ; 5 pirates en bandits,
 *       9 coffres et tonneaux à butin) : plaines, prairies, savanes ; assez rare (structure_set/bandit_camps.json : un emplacement
 *       possible tous les 26 tronçons environ, 10 au moins entre deux) ; terrain plat et sec seulement (BanditCampExclusion) ;
 *       raccord au terrain par Minecraft (terrain_adaptation « beard_thin »).</li>
 *   <li>Base de la Marine (structure/marine_base.nbt, 47 Marines persistants, prison souterraine avec 10 pirates menottés) : plaines, plages, savanes, prairies
 *       (tags/worldgen/biome/has_structure/marine_base.json), même rareté que les navires pirates (structure_set/marine_bases.json).
 *       Posée sur la surface, loin des villages (exclusion_zone), à plus de 500 blocs du spawn, sur terrain plat, sans autre structure à moins de 8 tronçons (MarineBaseExclusion) ; le processeur "bloxpiece:foundation" (FoundationProcessor) comble le
 *       dessous et raccorde le terrain autour en pente douce.</li>
 * </ul>
 */
public class ModWorldgen {

    public static final ResourceKey<PlacedFeature> SEASTONE_ORE =
            ResourceKey.create(Registries.PLACED_FEATURE, BloxPiece.id("seastone_ore"));

    /**
     * Processeur de structure "bloxpiece:foundation" (template_pool/marine_base/start.json). En 26.3 le registre des processeurs
     * contient directement leur codec (StructureProcessorType ne sert plus qu'à ranger les constantes vanilla).
     */
    public static final MapCodec<FoundationProcessor> FOUNDATION = Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR,
            ResourceKey.create(Registries.STRUCTURE_PROCESSOR, BloxPiece.id("foundation")), FoundationProcessor.CODEC);

    public static void initialize() {
        nolan.bloxpiece.worldgen.MarineBaseExclusion.initialize(); // pas de Base de la Marine près du spawn / du village de départ
        BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_OCEAN), GenerationStep.Decoration.UNDERGROUND_ORES, SEASTONE_ORE);
    }
}
