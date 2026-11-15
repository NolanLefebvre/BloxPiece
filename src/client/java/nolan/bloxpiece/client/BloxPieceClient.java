package nolan.bloxpiece.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;

import nolan.bloxpiece.client.entity.EnteiSunModel;
import nolan.bloxpiece.client.entity.EnteiSunRenderer;
import nolan.bloxpiece.client.entity.PirateRenderer;
import nolan.bloxpiece.client.hud.AbilityHud;
import nolan.bloxpiece.client.hud.ClientCooldowns;
import nolan.bloxpiece.client.screen.CharacterScreen;
import nolan.bloxpiece.network.CooldownPayload;
import nolan.bloxpiece.network.UseAbilityPayload;
import nolan.bloxpiece.registry.ModEntities;

public class BloxPieceClient implements ClientModInitializer {

    // Les mêmes touches pour tous les fruits : c'est le serveur qui décide quelle attaque lancer.
    // Pour changer les touches par défaut, modifie les SDL_SCANCODE_* (le joueur peut aussi les changer dans Options > Contrôles).
    private static final KeyMapping[] ABILITY_KEYS = {
            registerKey("key.bloxpiece.ability_1", SDLScancode.SDL_SCANCODE_Z),
            registerKey("key.bloxpiece.ability_2", SDLScancode.SDL_SCANCODE_X),
            registerKey("key.bloxpiece.ability_3", SDLScancode.SDL_SCANCODE_C),
            registerKey("key.bloxpiece.ability_4", SDLScancode.SDL_SCANCODE_V)
    };

    // Menu du personnage (modifiable dans Options > Contrôles)
    private static final KeyMapping CHARACTER_KEY = registerKey("key.bloxpiece.character", SDLScancode.SDL_SCANCODE_G);

    /** Touche de l'attaque de ce slot (le HUD en affiche le nom), ou null si ce slot n'a pas de touche. */
    public static KeyMapping abilityKey(int slot) {
        return slot >= 0 && slot < ABILITY_KEYS.length ? ABILITY_KEYS[slot] : null;
    }

    private static KeyMapping registerKey(String translationKey, int scancode) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(translationKey, InputConstants.Type.KEYBOARD, scancode, KeyMapping.Category.MISC));
    }

    @Override
    public void onInitializeClient() {
        // Apparence des mobs du mod (un renderer par type d'entité)
        EntityRendererRegistry.register(ModEntities.PIRATE, PirateRenderer::new);
        // Le calque du modèle doit être enregistré AVANT le renderer qui s'en sert
        ModelLayerRegistry.registerModelLayer(EnteiSunModel.LAYER, EnteiSunModel::createBodyLayer);
        EntityRendererRegistry.register(ModEntities.ENTEI_SUN, EnteiSunRenderer::new);

        // HUD des 4 attaques à côté de la hotbar
        AbilityHud.register();

        // Le serveur nous indique les recharges en cours (affichage uniquement)
        ClientPlayNetworking.registerGlobalReceiver(CooldownPayload.TYPE, (payload, context) -> {
            ClientLevel level = context.client().level;
            if (level != null) {
                ClientCooldowns.set(payload.key(), payload.ticks(), level.getGameTime());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> ClientCooldowns.clear());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getConnection() == null) {
                return;
            }
            // Touche G : ouvre le menu du personnage (le fermer se fait dans le menu lui-même)
            while (CHARACTER_KEY.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new CharacterScreen(CHARACTER_KEY));
                }
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
