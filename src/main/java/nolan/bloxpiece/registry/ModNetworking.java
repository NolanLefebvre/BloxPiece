package nolan.bloxpiece.registry;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import nolan.bloxpiece.ability.AbilityManager;
import nolan.bloxpiece.ability.IceSaberAbility;
import nolan.bloxpiece.network.ClaimQuestPayload;
import nolan.bloxpiece.network.CooldownPayload;
import nolan.bloxpiece.network.SaberStrikePayload;
import nolan.bloxpiece.network.ShakePayload;
import nolan.bloxpiece.network.UseAbilityPayload;
import nolan.bloxpiece.quest.Quests;

public class ModNetworking {

    public static void initialize() {
        // Client -> Serveur
        PayloadTypeRegistry.serverboundPlay().register(UseAbilityPayload.TYPE, UseAbilityPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SaberStrikePayload.TYPE, SaberStrikePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SaberStrikePayload.TYPE, (payload, context) ->
                IceSaberAbility.strike(context.player()));
        // Bouton "Récupérer" d'une quête journalière (le serveur vérifie tout)
        PayloadTypeRegistry.serverboundPlay().register(ClaimQuestPayload.TYPE, ClaimQuestPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ClaimQuestPayload.TYPE, (payload, context) ->
                Quests.claim(context.player(), payload.index()));

        // Serveur -> Client (recharges affichées dans le HUD)
        PayloadTypeRegistry.clientboundPlay().register(CooldownPayload.TYPE, CooldownPayload.CODEC);
        // Serveur -> Client (tremblement de caméra des attaques du Bouddha)
        PayloadTypeRegistry.clientboundPlay().register(ShakePayload.TYPE, ShakePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(UseAbilityPayload.TYPE, (payload, context) -> {
            if (payload.pressed()) {
                AbilityManager.tryUse(context.player(), payload.slot());
            } else {
                AbilityManager.release(context.player(), payload.slot());
            }
        });
    }
}
