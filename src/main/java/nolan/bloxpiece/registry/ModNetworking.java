package nolan.bloxpiece.registry;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import nolan.bloxpiece.ability.AbilityManager;
import nolan.bloxpiece.ability.IceSaberAbility;
import nolan.bloxpiece.network.ClaimQuestPayload;
import nolan.bloxpiece.network.CooldownPayload;
import nolan.bloxpiece.network.OpenRoulettePayload;
import nolan.bloxpiece.network.SaberStrikePayload;
import nolan.bloxpiece.network.ShakePayload;
import nolan.bloxpiece.network.StartRoulettePayload;
import nolan.bloxpiece.network.UseAbilityPayload;
import nolan.bloxpiece.quest.Quests;
import nolan.bloxpiece.treasure.TreasureChests;

public class ModNetworking {

    public static void initialize() {
        // Client -> Serveur
        PayloadTypeRegistry.serverboundPlay().register(UseAbilityPayload.TYPE, UseAbilityPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(nolan.bloxpiece.network.UseHakiPayload.TYPE,
                nolan.bloxpiece.network.UseHakiPayload.CODEC); // touches des Hakis
        PayloadTypeRegistry.serverboundPlay().register(SaberStrikePayload.TYPE, SaberStrikePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SaberStrikePayload.TYPE, (payload, context) ->
                IceSaberAbility.strike(context.player()));
        // Choix du job dans le menu du personnage (le serveur vérifie le niveau)
        PayloadTypeRegistry.serverboundPlay().register(nolan.bloxpiece.network.ChooseJobPayload.TYPE,
                nolan.bloxpiece.network.ChooseJobPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(nolan.bloxpiece.network.ChooseJobPayload.TYPE, (payload, context) ->
                nolan.bloxpiece.job.Jobs.choose(context.player(), payload.job()));
        // Bouton "Récupérer" d'une quête journalière (le serveur vérifie tout)
        PayloadTypeRegistry.serverboundPlay().register(ClaimQuestPayload.TYPE, ClaimQuestPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ClaimQuestPayload.TYPE, (payload, context) ->
                Quests.claim(context.player(), payload.index()));

        // Quêtes uniques : acceptation d'une quête de Haki auprès de Silvers Rayleigh (le serveur vérifie tout)
        PayloadTypeRegistry.serverboundPlay().register(nolan.bloxpiece.network.AcceptUniqueQuestPayload.TYPE,
                nolan.bloxpiece.network.AcceptUniqueQuestPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(nolan.bloxpiece.network.AcceptUniqueQuestPayload.TYPE, (payload, context) ->
                nolan.bloxpiece.quest.UniqueQuests.accept(context.player(), payload.quest(), payload.rayleighId()));

        // Quêtes répétables : accepter la mission du Tavernier, ou récupérer sa récompense (le serveur vérifie tout)
        PayloadTypeRegistry.serverboundPlay().register(nolan.bloxpiece.network.RepeatableQuestPayload.TYPE,
                nolan.bloxpiece.network.RepeatableQuestPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(nolan.bloxpiece.network.RepeatableQuestPayload.TYPE, (payload, context) -> {
            if (payload.action() == nolan.bloxpiece.network.RepeatableQuestPayload.ACCEPT) {
                nolan.bloxpiece.quest.RepeatableQuests.accept(context.player(), payload.keeperId());
            } else if (payload.action() == nolan.bloxpiece.network.RepeatableQuestPayload.CLAIM) {
                nolan.bloxpiece.quest.RepeatableQuests.claim(context.player(), payload.keeperId());
            }
        });

        // Roulette du Coffre au Trésor : bouton OUVRIR (coffre consommé, fruit tiré) ou fenêtre refermée sans lancer
        PayloadTypeRegistry.serverboundPlay().register(StartRoulettePayload.TYPE, StartRoulettePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StartRoulettePayload.TYPE, (payload, context) -> {
            if (payload.action() == StartRoulettePayload.START) {
                TreasureChests.start(context.player());
            } else {
                TreasureChests.cancel(context.player()); // fenêtre refermée sans lancer : le coffre n'est pas consommé
            }
        });

        // Page « Party » du menu personnage : demandes du client (le serveur vérifie tout : party/Parties)
        PayloadTypeRegistry.serverboundPlay().register(nolan.bloxpiece.network.PartyActionPayload.TYPE,
                nolan.bloxpiece.network.PartyActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(nolan.bloxpiece.network.PartyActionPayload.TYPE, (payload, context) ->
                nolan.bloxpiece.party.Parties.handle(context.player(), payload.action(), payload.arg()));
        // Serveur -> Client : état de la Party du joueur
        PayloadTypeRegistry.clientboundPlay().register(nolan.bloxpiece.network.PartySyncPayload.TYPE,
                nolan.bloxpiece.network.PartySyncPayload.CODEC);

        // Touche « Basculer fruit / race » : ensemble d'attaques des touches d'attaque (le serveur décide : RaceAbilities)
        PayloadTypeRegistry.serverboundPlay().register(nolan.bloxpiece.network.ToggleAttackSetPayload.TYPE,
                nolan.bloxpiece.network.ToggleAttackSetPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(nolan.bloxpiece.network.ToggleAttackSetPayload.TYPE, (payload, context) ->
                nolan.bloxpiece.race.RaceAbilities.toggle(context.player()));
        // Menu Race : bouton Reroll (le serveur vérifie le token et tire la race : race/RaceManager)
        PayloadTypeRegistry.serverboundPlay().register(nolan.bloxpiece.network.RaceRerollPayload.TYPE,
                nolan.bloxpiece.network.RaceRerollPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(nolan.bloxpiece.network.RaceRerollPayload.TYPE, (payload, context) ->
                nolan.bloxpiece.race.RaceManager.reroll(context.player()));

        // Serveur -> Client : résultat d'un reroll de race (déjà tiré par le serveur), pour la roulette du menu Race
        PayloadTypeRegistry.clientboundPlay().register(nolan.bloxpiece.network.RaceRollResultPayload.TYPE,
                nolan.bloxpiece.network.RaceRollResultPayload.CODEC);

        // Serveur -> Client (recharges affichées dans le HUD)
        PayloadTypeRegistry.clientboundPlay().register(CooldownPayload.TYPE, CooldownPayload.CODEC);
        // Serveur -> Client (tremblement de caméra des attaques du Bouddha)
        PayloadTypeRegistry.clientboundPlay().register(ShakePayload.TYPE, ShakePayload.CODEC);
        // Serveur -> Client (ouverture d'un Coffre au Trésor : roulette des fruits du démon)
        PayloadTypeRegistry.clientboundPlay().register(OpenRoulettePayload.TYPE, OpenRoulettePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(nolan.bloxpiece.network.HakiDodgePayload.TYPE,
                nolan.bloxpiece.network.HakiDodgePayload.CODEC); // esquive du Haki de l'Observation (animation)
        PayloadTypeRegistry.clientboundPlay().register(nolan.bloxpiece.network.OpenDialoguePayload.TYPE,
                nolan.bloxpiece.network.OpenDialoguePayload.CODEC); // dialogue des PNJ (Tavernier)

        ServerPlayNetworking.registerGlobalReceiver(nolan.bloxpiece.network.UseHakiPayload.TYPE, (payload, context) -> {
            switch (payload.action()) {
                case nolan.bloxpiece.network.UseHakiPayload.PRESS -> nolan.bloxpiece.haki.HakiManager.press(context.player(), payload.slot());
                case nolan.bloxpiece.network.UseHakiPayload.HOLD -> nolan.bloxpiece.haki.HakiManager.hold(context.player(), payload.slot());
                default -> nolan.bloxpiece.haki.HakiManager.release(context.player(), payload.slot());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(UseAbilityPayload.TYPE, (payload, context) -> {
            // mêmes touches pour les deux ensembles : en mode race (ou sans fruit), elles lancent les attaques de la race
            if (nolan.bloxpiece.race.RaceAbilities.raceMode(context.player())) {
                if (payload.pressed()) {
                    nolan.bloxpiece.race.RaceAbilities.tryUse(context.player(), payload.slot());
                } else {
                    nolan.bloxpiece.race.RaceAbilities.release(context.player(), payload.slot());
                }
                return;
            }
            if (payload.pressed()) {
                AbilityManager.tryUse(context.player(), payload.slot());
            } else {
                AbilityManager.release(context.player(), payload.slot());
            }
        });
    }
}
