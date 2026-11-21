package nolan.bloxpiece.registry;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

import nolan.bloxpiece.ability.AbilityManager;
import nolan.bloxpiece.ability.BuddhaPalmAbility;
import nolan.bloxpiece.ability.BuddhaShockwaveAbility;
import nolan.bloxpiece.ability.BuddhaUltimateAbility;
import nolan.bloxpiece.ability.GuraGekishinAbility;
import nolan.bloxpiece.ability.GuraKabutowariAbility;
import nolan.bloxpiece.ability.GuraShimaYurashiAbility;
import nolan.bloxpiece.ability.GuraTerrain;
import nolan.bloxpiece.ability.IceAgeAbility;
import nolan.bloxpiece.ability.IceEagleAbility;
import nolan.bloxpiece.ability.IceFreeze;
import nolan.bloxpiece.ability.IceSaberAbility;
import nolan.bloxpiece.ability.IceTerrain;
import nolan.bloxpiece.ability.BuddhaTransformAbility;
import nolan.bloxpiece.ability.DaiEnkaiEnteiAbility;
import nolan.bloxpiece.fruit.DevilFruit;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.fruit.IceFrostWalk;
import nolan.bloxpiece.fruit.LogiaIntangibility;
import nolan.bloxpiece.fx.BuddhaEffects;
import nolan.bloxpiece.fx.FireEffects;
import nolan.bloxpiece.mob.ArmedPirate;
import nolan.bloxpiece.mob.BloxMob;
import nolan.bloxpiece.quest.QuestType;
import nolan.bloxpiece.quest.Quests;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.entity.monster.Enemy;
import nolan.bloxpiece.progression.LevelScaling;

import nolan.bloxpiece.treasure.TreasureChests;
import nolan.bloxpiece.fruit.BubbleCoral;
import nolan.bloxpiece.handcuffs.Handcuffs;

public class ModEvents {

    /** Joueurs qui viennent de réapparaître : ticks avant de leur renvoyer leur vie pleine (voir AFTER_RESPAWN). */
    private static final java.util.Map<java.util.UUID, Integer> RESPAWN_HEAL = new java.util.HashMap<>();

    public static void initialize() {
        // Menottes : mains liées (aucune action possible sauf la clé), menottes rendues à la mort
        Handcuffs.initialize();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Coffres au Trésor : fruit donné à la fin de la roulette
            TreasureChests.tick(server);
            // Corail à Bulles : barre d'air pleine dans la bulle
            BubbleCoral.tick(server);
            // Menottes en Granite Marin : ralentissement des porteurs de fruit, transformation impossible
            Handcuffs.tick(server);
            // Traînées et impacts des projectiles de feu : à chaque tick
            FireEffects.tick();
            // Charge de Dai Enkai: Entei (les flammes se rassemblent, puis le soleil part)
            DaiEnkaiEnteiAbility.tick();
            // Transformation en Bouddha (croissance / rétrécissement progressifs)
            BuddhaTransformAbility.tick(server);
            // Paume du Bouddha : charge, puis onde de choc dorée après l'impact
            BuddhaPalmAbility.tick(server);
            BuddhaShockwaveAbility.tick(server);
            nolan.bloxpiece.fx.GoldenColossus.tick(server); // paume colossale de l'Onde de choc
            BuddhaUltimateAbility.tick(server);
            BuddhaEffects.tick();
            // Passif Logia du fruit du Feu : membres dispersés en flammes
            LogiaIntangibility.tick(server);
            // Fruit de la Glace : trident en main, aigle en formation, Ère glaciaire, cibles gelées, terrain qui dégèle
            IceSaberAbility.tick(server);
            IceEagleAbility.tick(server);
            IceAgeAbility.tick(server);
            IceFreeze.tick(server);
            IceTerrain.tick();
            // Gura Gura no Mi : dash de Gekishin, charge de Shima Yurashi
            GuraGekishinAbility.tick(server);
            GuraShimaYurashiAbility.tick(server);
            GuraKabutowariAbility.tick(server);
            GuraTerrain.tick(); // fissures du sol qui se propagent
            // Quêtes journalières : nouveau jour = nouveau tirage ; distance parcourue
            Quests.tick(server);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // PV maximum selon le niveau (tous les joueurs, avec ou sans fruit)
                LevelScaling.applyHealth(player);

                DevilFruit fruit = Fruits.get(player.getAttached(ModAttachments.FRUIT));
                if (fruit == null || !player.isAlive()) {
                    continue;
                }

                // Immunité au feu : le joueur ne brûle jamais (pas de flammes à l'écran, pas de dégâts)
                if (fruit.isFireproof() && player.isOnFire()) {
                    player.clearFire();
                }

                // Faiblesse du fruit (ex : malédiction de l'eau). Aucun effet de potion, rien dans le HUD.
                fruit.tickWeakness(player);

                // Fruit de la Glace : Frost Walker permanent (l'eau gèle sous ses pas)
                if (fruit == Fruits.ICE) {
                    IceFrostWalk.tick(player);
                }
            }
        });

        // Immunité aux dégâts de feu / lave / boules de feu pour les porteurs d'un fruit ignifugé.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            // Un joueur ne se blesse jamais avec ses propres attaques de feu (explosions comprises).
            // Seul le créateur est épargné : tout le monde d'autre subit les dégâts normalement.
            if (FireEffects.isOwnAttack(entity, source)) {
                return false;
            }

            // Passif Logia du fruit du Feu : les dégâts physiques traversent le corps de feu (sauf attaques de fruits du démon)
            if (entity instanceof ServerPlayer player && LogiaIntangibility.absorbs(player, source)) {
                return false;
            }

            if (entity instanceof ServerPlayer player) {
                DevilFruit fruit = Fruits.get(player.getAttached(ModAttachments.FRUIT));
                if (fruit != null && fruit.isImmuneTo(source)) {
                    return false; // dégâts annulés
                }
                // Corail à Bulles : la bulle éclate au moindre dégât subi (les dégâts eux-mêmes s'appliquent normalement)
                if (amount > 0.0F) {
                    BubbleCoral.pop(player);
                }
            }
            return true;
        });

        // Fruit de la Glace : coups de mêlée portés avec le trident de glace en main
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                IceSaberAbility.onMelee(serverPlayer, entity);
            }
            return InteractionResult.PASS;
        });

        // Quand un mob du mod meurt : XP BloxPiece pour le joueur responsable (s'il y en a un).
        // Tous les futurs mobs (qui étendent BloxMob) sont couverts automatiquement.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof BloxMob mob) {
                mob.rewardKiller(source);
            }
            // Quêtes journalières : pirates et monstres hostiles tués par un joueur
            if (source.getEntity() instanceof ServerPlayer killer && entity != killer) {
                if (entity instanceof ArmedPirate) {
                    Quests.progress(killer, QuestType.KILL_PIRATES, 1);
                }
                if (entity instanceof Enemy) {
                    Quests.progress(killer, QuestType.KILL_HOSTILES, 1);
                }
            }
            // Un joueur qui meurt transformé en Bouddha redevient normal
            if (entity instanceof ServerPlayer player) {
                BuddhaTransformAbility.reset(player);
            }
        });

        // Quêtes journalières : blocs minés (pas les herbes ni les fleurs, cassées d'un coup)
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer && state.getDestroySpeed(level, pos) >= 0.5F) {
                Quests.progress(serverPlayer, QuestType.MINE_BLOCKS, 1);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Quests.forget(handler.getPlayer().getUUID());
            TreasureChests.onDisconnect(handler.getPlayer()); // roulette pas finie : le fruit est donné tout de suite
        });

        // Quand un joueur rejoint : on lui renvoie ses recharges encore en cours (affichage du HUD)
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            AbilityManager.syncCooldowns(listener.getPlayer());
            Quests.ensure(listener.getPlayer()); // quêtes du jour prêtes dès la connexion
            BuddhaTransformAbility.reset(listener.getPlayer()); // jamais de taille ou de modificateur resté d'une session précédente
        });

        // Réapparition (mort, retour de l'End...) : toujours à taille normale
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            BuddhaTransformAbility.reset(newPlayer);
            // Après une mort, le nouveau joueur repart avec 20 PV de base : on lui remet tout de suite ses PV bonus de niveau
            // (LevelScaling) et TOUTE sa vie (toutes les barres de cœurs pleines)
            if (!alive) {
                LevelScaling.applyHealth(newPlayer);
                newPlayer.setHealth(newPlayer.getMaxHealth());
                // le client reçoit la vie AVANT le nouveau maximum (attribut envoyé après) : il la bloquait à 20 PV (1re barre).
                // On la renvoie 2 ticks plus tard, une fois le maximum connu du client : toutes les barres sont pleines.
                RESPAWN_HEAL.put(newPlayer.getUUID(), 2);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> RESPAWN_HEAL.entrySet().removeIf(e -> {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                return true;
            }
            if (e.getValue() > 1) {
                e.setValue(e.getValue() - 1);
                return false;
            }
            LevelScaling.applyHealth(player);
            player.setHealth(player.getMaxHealth());
            player.resetSentInfo(); // force l'envoi de la vie au client
            return true;
        }));

        // Évite de garder des projectiles d'un ancien monde en mémoire
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            FireEffects.clear();
            GuraGekishinAbility.clear();
            GuraShimaYurashiAbility.clear();
            GuraKabutowariAbility.clear();
            GuraTerrain.clear();
            Quests.clear();
            DaiEnkaiEnteiAbility.clear();
            BuddhaTransformAbility.clear();
            BuddhaPalmAbility.clear();
            BuddhaShockwaveAbility.clear();
            nolan.bloxpiece.fx.GoldenColossus.clear(server);
            BuddhaUltimateAbility.clear();
            BuddhaEffects.clear();
            LogiaIntangibility.clear();
            IceEagleAbility.clear();
            IceAgeAbility.clear();
            IceFreeze.clear();
            IceTerrain.clear(); // le terrain gelé redevient comme avant
        });
    }
}
