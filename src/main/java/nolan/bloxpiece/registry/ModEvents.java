package nolan.bloxpiece.registry;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

import nolan.bloxpiece.ability.AbilityManager;
import nolan.bloxpiece.ability.BuddhaTransformAbility;
import nolan.bloxpiece.ability.DaiEnkaiEnteiAbility;
import nolan.bloxpiece.fruit.DevilFruit;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.fx.FireEffects;
import nolan.bloxpiece.mob.BloxMob;

public class ModEvents {

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Traînées et impacts des projectiles de feu : à chaque tick
            FireEffects.tick();
            // Charge de Dai Enkai: Entei (les flammes se rassemblent, puis le soleil part)
            DaiEnkaiEnteiAbility.tick();
            // Transformation en Bouddha (croissance / rétrécissement progressifs)
            BuddhaTransformAbility.tick(server);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
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
            }
        });

        // Immunité aux dégâts de feu / lave / boules de feu pour les porteurs d'un fruit ignifugé.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            // Un joueur ne se blesse jamais avec ses propres attaques de feu (explosions comprises).
            // Seul le créateur est épargné : tout le monde d'autre subit les dégâts normalement.
            if (FireEffects.isOwnAttack(entity, source)) {
                return false;
            }

            if (entity instanceof ServerPlayer player) {
                DevilFruit fruit = Fruits.get(player.getAttached(ModAttachments.FRUIT));
                if (fruit != null && fruit.isImmuneTo(source)) {
                    return false; // dégâts annulés
                }
            }
            return true;
        });

        // Quand un mob du mod meurt : XP BloxPiece pour le joueur responsable (s'il y en a un).
        // Tous les futurs mobs (qui étendent BloxMob) sont couverts automatiquement.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof BloxMob mob) {
                mob.rewardKiller(source);
            }
            // Un joueur qui meurt transformé en Bouddha redevient normal
            if (entity instanceof ServerPlayer player) {
                BuddhaTransformAbility.reset(player);
            }
        });

        // Quand un joueur rejoint : on lui renvoie ses recharges encore en cours (affichage du HUD)
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            AbilityManager.syncCooldowns(listener.getPlayer());
            BuddhaTransformAbility.reset(listener.getPlayer()); // jamais de taille ou de modificateur resté d'une session précédente
        });

        // Réapparition (mort, retour de l'End...) : toujours à taille normale
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> BuddhaTransformAbility.reset(newPlayer));

        // Évite de garder des projectiles d'un ancien monde en mémoire
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            FireEffects.clear();
            DaiEnkaiEnteiAbility.clear();
            BuddhaTransformAbility.clear();
        });
    }
}
