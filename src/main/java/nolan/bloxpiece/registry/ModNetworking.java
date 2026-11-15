package nolan.bloxpiece.registry;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import nolan.bloxpiece.ability.AbilityManager;
import nolan.bloxpiece.network.CooldownPayload;
import nolan.bloxpiece.network.UseAbilityPayload;

public class ModNetworking {

    public static void initialize() {
        // Client -> Serveur
        PayloadTypeRegistry.serverboundPlay().register(UseAbilityPayload.TYPE, UseAbilityPayload.CODEC);

        // Serveur -> Client (recharges affichées dans le HUD)
        PayloadTypeRegistry.clientboundPlay().register(CooldownPayload.TYPE, CooldownPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(UseAbilityPayload.TYPE, (payload, context) ->
                AbilityManager.tryUse(context.player(), payload.slot()));
    }
}
