package nolan.bloxpiece;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nolan.bloxpiece.registry.ModEffects;
import nolan.bloxpiece.registry.ModItems;

public class BloxPiece implements ModInitializer {
    public static final String MOD_ID = "bloxpiece";

    // Sert à écrire dans la console et dans le fichier de logs.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Hello Fabric world!");

        // L'ordre compte : les effets d'abord, car les items peuvent les utiliser.
        ModEffects.initialize();
        ModItems.initialize();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
