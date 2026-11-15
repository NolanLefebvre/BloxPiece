package nolan.bloxpiece.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

import nolan.bloxpiece.network.UseAbilityPayload;

public class BloxPieceClient implements ClientModInitializer {

    // Les mêmes touches pour tous les fruits : c'est le serveur qui décide quelle attaque lancer.
    // Pour changer les touches par défaut, modifie les SDL_SCANCODE_* (le joueur peut aussi les changer dans Options > Contrôles).
    private static final KeyMapping[] ABILITY_KEYS = {
            registerKey("key.bloxpiece.ability_1", SDLScancode.SDL_SCANCODE_Z),
            registerKey("key.bloxpiece.ability_2", SDLScancode.SDL_SCANCODE_X),
            registerKey("key.bloxpiece.ability_3", SDLScancode.SDL_SCANCODE_C)
    };

    private static KeyMapping registerKey(String translationKey, int scancode) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(translationKey, InputConstants.Type.KEYBOARD, scancode, KeyMapping.Category.MISC));
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getConnection() == null) {
                return;
            }
            for (int slot = 0; slot < ABILITY_KEYS.length; slot++) {
                // while : au cas où la touche a été pressée plusieurs fois dans le même tick
                while (ABILITY_KEYS[slot].consumeClick()) {
                    ClientPlayNetworking.send(new UseAbilityPayload(slot));
                }
            }
        });
    }
}
