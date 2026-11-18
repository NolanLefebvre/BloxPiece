package nolan.bloxpiece.treasure;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.item.DevilFruitItem;
import nolan.bloxpiece.network.OpenRoulettePayload;
import nolan.bloxpiece.registry.ModBlocks;
import nolan.bloxpiece.registry.ModItems;

/**
 * Ouverture d'un Coffre au Trésor, côté serveur (c'est lui qui décide de tout) :
 * <ol>
 *   <li>clic droit (coffre en main ou coffre posé) : la roulette s'ouvre (OpenRoulettePayload sans fruit). RIEN n'est consommé :
 *       le joueur peut refermer la fenêtre (Échap) et garder son coffre ;</li>
 *   <li>clic sur OUVRIR (StartRoulettePayload) : le serveur vérifie que le coffre est toujours là (dans la même main, ou toujours
 *       posé au même endroit et à portée), le consomme, PUIS tire le fruit selon sa rareté (FruitRarity) et l'envoie au client
 *       pour l'animation. Le fruit n'étant tiré qu'à ce moment, fermer / rouvrir la fenêtre ne permet pas de choisir son fruit ;</li>
 *   <li>le fruit est donné à la fin de l'animation ({@link #GIVE_DELAY_TICKS} après OUVRIR), quoi que fasse le client.
 *       Inventaire plein : il tombe aux pieds du joueur. Déconnexion pendant l'animation : il est donné tout de suite.</li>
 * </ol>
 * Un joueur n'a qu'une roulette à la fois. Une fenêtre restée ouverte sans clic sur OUVRIR est oubliée au bout de
 * {@link #IDLE_TIMEOUT_TICKS} (le coffre n'a pas été consommé).
 */
public final class TreasureChests {

    /** Durée de l'animation de la roulette côté client (RouletteScreen.SPIN_MS = 4 200 ms) + une petite marge. */
    public static final int GIVE_DELAY_TICKS = 88;

    /** Fenêtre ouverte sans clic sur OUVRIR : oubliée après 5 minutes (rien n'a été consommé). */
    public static final int IDLE_TIMEOUT_TICKS = 20 * 60 * 5;

    /** Distance maximale (au carré) entre le joueur et le coffre posé au moment de cliquer sur OUVRIR. */
    private static final double MAX_BLOCK_DISTANCE_SQR = 8.0 * 8.0;

    /** Une roulette ouverte : d'où vient le coffre (main OU bloc posé), puis le fruit tiré une fois OUVRIR cliqué. */
    private static final class Session {
        final InteractionHand hand;
        final BlockPos pos;
        int dueTick;
        Item fruit;     // null tant que OUVRIR n'a pas été cliqué

        Session(InteractionHand hand, BlockPos pos, int dueTick) {
            this.hand = hand;
            this.pos = pos;
            this.dueTick = dueTick;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int tick;

    private TreasureChests() {
    }

    /** Tous les fruits du démon du mod (objets), dans l'ordre du registre. */
    public static List<Item> allFruits() {
        return BuiltInRegistries.ITEM.stream().filter(item -> item instanceof DevilFruitItem).toList();
    }

    /** Clic droit avec le coffre en main : ouvre la roulette (sans rien consommer). */
    public static boolean openFromHand(ServerPlayer player, InteractionHand hand) {
        return open(player, new Session(hand, null, tick + IDLE_TIMEOUT_TICKS));
    }

    /** Clic droit sur un coffre posé : ouvre la roulette (le coffre reste posé tant que OUVRIR n'est pas cliqué). */
    public static boolean openFromBlock(ServerPlayer player, BlockPos pos) {
        return open(player, new Session(null, pos.immutable(), tick + IDLE_TIMEOUT_TICKS));
    }

    private static boolean open(ServerPlayer player, Session session) {
        Session current = SESSIONS.get(player.getUUID());
        if (current != null && current.fruit != null) {
            return false; // une roulette tourne déjà
        }
        if (allFruits().isEmpty()) {
            return false;
        }
        SESSIONS.put(player.getUUID(), session); // remplace une ancienne fenêtre jamais lancée
        ServerPlayNetworking.send(player, new OpenRoulettePayload(OpenRoulettePayload.OPEN));
        return true;
    }

    /** Clic sur OUVRIR : consomme le coffre, tire le fruit et l'envoie au client. Coffre introuvable : la roulette se ferme. */
    public static void start(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.fruit != null) {
            return;
        }
        if (!consumeChest(player, session)) {
            SESSIONS.remove(player.getUUID());
            ServerPlayNetworking.send(player, new OpenRoulettePayload(OpenRoulettePayload.FAILED));
            player.sendSystemMessage(Component.translatable("message.bloxpiece.treasure_chest.missing"));
            return;
        }
        session.fruit = FruitRarity.pick(allFruits(), player.getRandom());
        session.dueTick = tick + GIVE_DELAY_TICKS;
        ServerPlayNetworking.send(player, new OpenRoulettePayload(BuiltInRegistries.ITEM.getKey(session.fruit).toString()));
    }

    /** Fenêtre refermée sans lancer la roulette : on l'oublie, le coffre reste au joueur. */
    public static void cancel(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.fruit == null) {
            SESSIONS.remove(player.getUUID());
        }
    }

    private static boolean consumeChest(ServerPlayer player, Session session) {
        if (session.hand != null) {
            ItemStack stack = player.getItemInHand(session.hand);
            if (!stack.is(ModItems.TREASURE_CHEST)) {
                return false; // le coffre n'est plus dans cette main
            }
            stack.shrink(1); // consommé même en créatif : un coffre = un fruit
            return true;
        }
        Level level = player.level();
        if (!level.getBlockState(session.pos).is(ModBlocks.TREASURE_CHEST)
                || player.distanceToSqr(Vec3.atCenterOf(session.pos)) > MAX_BLOCK_DISTANCE_SQR) {
            return false; // coffre cassé, déplacé, ou joueur trop loin
        }
        level.removeBlock(session.pos, false); // le coffre disparaît sans rien lâcher
        return true;
    }

    /** À chaque tick serveur : donne les fruits dont l'animation est terminée, oublie les fenêtres restées sans réponse. */
    public static void tick(MinecraftServer server) {
        tick++;
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            Session session = entry.getValue();
            if (tick < session.dueTick) {
                continue;
            }
            if (session.fruit != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    give(player, session.fruit);
                }
            }
            it.remove();
        }
    }

    /** Déconnexion : roulette lancée = le fruit est donné tout de suite ; fenêtre jamais lancée = rien (le coffre est toujours là). */
    public static void onDisconnect(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null && session.fruit != null) {
            give(player, session.fruit);
        }
    }

    private static void give(ServerPlayer player, Item fruit) {
        ItemStack stack = new ItemStack(fruit);
        Component name = stack.getHoverName();
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.spawnAtLocation((ServerLevel) player.level(), stack); // inventaire plein : le fruit tombe aux pieds du joueur
        }
        player.sendSystemMessage(Component.translatable("message.bloxpiece.treasure_chest.won", name));
    }
}
