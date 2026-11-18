package nolan.bloxpiece;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModBlocks;
import nolan.bloxpiece.registry.ModCommands;
import nolan.bloxpiece.registry.ModEntities;
import nolan.bloxpiece.registry.ModEvents;
import nolan.bloxpiece.registry.ModItems;
import nolan.bloxpiece.registry.ModNetworking;
import nolan.bloxpiece.registry.ModParticles;
import nolan.bloxpiece.registry.ModSpawns;
import nolan.bloxpiece.registry.ModWorldgen;
import nolan.bloxpiece.village.StarterVillage;

public class BloxPiece implements ModInitializer {
    public static final String MOD_ID = "bloxpiece";

    // Sert à écrire dans la console et dans le fichier de logs.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Hello Fabric world!");

        ModAttachments.initialize();
        ModBlocks.initialize(); // avant les objets : le Coffre au Trésor (objet) pose le bloc du même nom
        ModItems.initialize();
        ModParticles.initialize();
        ModEntities.initialize();
        ModSpawns.initialize();
        ModWorldgen.initialize(); // minerai de Granite Marin au fond des océans
        StarterVillage.initialize(); // village pirate de départ, au spawn d'un monde neuf
        ModNetworking.initialize();
        ModCommands.initialize();
        ModEvents.initialize();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
