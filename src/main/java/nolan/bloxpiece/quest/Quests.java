package nolan.bloxpiece.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.progression.Progression;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Logique serveur des quêtes journalières.
 * <ul>
 *   <li>Chaque jour Minecraft (24 000 ticks, numéro = temps du jour de l'Overworld / 24 000), chaque joueur reçoit un nouveau
 *       tirage de {@link #DAILY_COUNT} quêtes de types différents, progression à zéro (y compris les quêtes non récupérées).</li>
 *   <li>Progression : appelée par les événements du jeu (ModEvents, AbilityManager), voir {@link #progress}.</li>
 *   <li>Récompense : le client demande (ClaimQuestPayload), le serveur vérifie et donne l'objet ou l'XP BloxPiece.</li>
 * </ul>
 * Tout passe par l'attachment QUESTS : sauvegarde et synchronisation avec le client sont automatiques.
 */
public final class Quests {

    public static final int DAILY_COUNT = 3;
    public static final long DAY_TICKS = 24000L;
    /** Déplacement plus grand qu'en un tick normal (téléportation, ender pearl) : ignoré pour la quête de distance. */
    private static final double MAX_STEP = 2.0;

    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Double> TRAVELED = new HashMap<>();

    private Quests() {
    }

    /**
     * Temps total de l'horloge du jour de l'Overworld (depuis Minecraft 26, l'heure du jour passe par les "horloges" du monde :
     * clockManager). Suit /time set et le sommeil. Seul endroit à changer si le calcul du temps devait évoluer.
     */
    private static long clockTicks(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.dimensionType().defaultClock()
                .map(clock -> server.clockManager().getInstance(clock).totalTicks())
                .orElse(overworld.getGameTime());
    }

    /** Numéro du jour Minecraft en cours (Overworld). */
    public static long currentDay(MinecraftServer server) {
        return clockTicks(server) / DAY_TICKS;
    }

    /** Temps de jeu (getGameTime, connu du client) auquel commencera le prochain jour : sert au minuteur du menu. */
    private static long resetAt(MinecraftServer server) {
        long left = DAY_TICKS - Math.floorMod(clockTicks(server), DAY_TICKS);
        return server.overworld().getGameTime() + left;
    }

    /** Les quêtes du joueur pour aujourd'hui (tirées si c'est un nouveau jour). */
    public static QuestData ensure(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        long day = currentDay(server);
        QuestData data = player.getAttached(ModAttachments.QUESTS);
        if (data == null || data.day() != day) {
            data = new QuestData(day, resetAt(server), roll(player));
            player.setAttached(ModAttachments.QUESTS, data);
        } else if (Math.abs(data.resetAt() - resetAt(server)) > 40) {
            // L'heure a été changée sans changer de jour (/time set...) : on recale le minuteur du client
            data = new QuestData(data.day(), resetAt(server), data.daily());
            player.setAttached(ModAttachments.QUESTS, data);
        }
        return data;
    }

    private static List<DailyQuest> roll(ServerPlayer player) {
        RandomSource random = player.getRandom();
        boolean hasFruit = player.getAttached(ModAttachments.FRUIT) != null;
        List<QuestType> pool = new ArrayList<>();
        for (QuestType type : QuestType.values()) {
            if (!type.needsFruit || hasFruit) {
                pool.add(type);
            }
        }
        List<DailyQuest> quests = new ArrayList<>();
        while (quests.size() < DAILY_COUNT && !pool.isEmpty()) {
            QuestType type = pool.remove(random.nextInt(pool.size()));
            int steps = (type.max - type.min) / type.step;
            int target = type.min + random.nextInt(steps + 1) * type.step;
            QuestType.Reward reward = type.rewards.get(random.nextInt(type.rewards.size()));
            quests.add(new DailyQuest(type.id, target, 0, false, reward.id(), reward.count()));
        }
        return quests;
    }

    /** Fait avancer les quêtes de ce type (ex : un pirate tué = KILL_PIRATES, 1). Prévient le joueur quand une quête est finie. */
    public static void progress(ServerPlayer player, QuestType type, int amount) {
        if (amount <= 0) {
            return;
        }
        QuestData data = ensure(player);
        List<DailyQuest> updated = new ArrayList<>(data.daily());
        boolean changed = false;
        for (int i = 0; i < updated.size(); i++) {
            DailyQuest quest = updated.get(i);
            if (!quest.type().equals(type.id) || quest.isComplete()) {
                continue;
            }
            DailyQuest next = quest.withProgress(quest.progress() + amount);
            updated.set(i, next);
            changed = true;
            if (next.isComplete()) {
                player.sendSystemMessage(Component.translatable("message.bloxpiece.quest.complete",
                        Component.translatable(type.titleKey())), true);
                player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
            }
        }
        if (changed) {
            player.setAttached(ModAttachments.QUESTS, new QuestData(data.day(), data.resetAt(), updated));
        }
    }

    /** Le joueur récupère la récompense de la quête n° {@code index} (vérifié ici : terminée et pas encore récupérée). */
    public static void claim(ServerPlayer player, int index) {
        QuestData data = ensure(player);
        if (index < 0 || index >= data.daily().size()) {
            return;
        }
        DailyQuest quest = data.daily().get(index);
        if (!quest.isComplete() || quest.claimed()) {
            return;
        }
        List<DailyQuest> updated = new ArrayList<>(data.daily());
        updated.set(index, quest.asClaimed());
        player.setAttached(ModAttachments.QUESTS, new QuestData(data.day(), data.resetAt(), updated));

        // cristaux d'XP garantis (selon la difficulté), puis la récompense tirée
        QuestType type = quest.questType();
        if (type != null) {
            give(player, type.crystal(quest.target()));
        }
        give(player, quest.reward());
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    private static void give(ServerPlayer player, QuestType.Reward reward) {
        if (reward.isXp()) {
            Progression.addXp(player, reward.count());
            return;
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(reward.id()));
        ItemStack stack = new ItemStack(item, reward.count());
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.spawnAtLocation((ServerLevel) player.level(), stack); // inventaire plein : l'objet tombe aux pieds du joueur
        }
    }

    /** À chaque tick serveur (ModEvents) : nouveau jour = nouvelles quêtes ; distance parcourue à pied / à la nage. */
    public static void tick(MinecraftServer server) {
        boolean second = server.getTickCount() % 20 == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            Vec3 pos = player.position();
            Vec3 last = LAST_POS.put(id, pos);
            if (last != null && !player.isPassenger() && !player.getAbilities().flying && !player.isSpectator()) {
                double dx = pos.x - last.x, dz = pos.z - last.z;
                double step = Math.sqrt(dx * dx + dz * dz);
                if (step < MAX_STEP) {
                    TRAVELED.merge(id, step, Double::sum);
                }
            }
            if (second) {
                ensure(player); // reset dès que le jour change, même sans rien faire
                double traveled = TRAVELED.getOrDefault(id, 0.0);
                int blocks = (int) traveled;
                if (blocks > 0) {
                    TRAVELED.put(id, traveled - blocks);
                    progress(player, QuestType.TRAVEL, blocks);
                }
            }
        }
    }

    /** Oublie les positions (déconnexion, arrêt du serveur). */
    public static void forget(UUID id) {
        LAST_POS.remove(id);
        TRAVELED.remove(id);
    }

    public static void clear() {
        LAST_POS.clear();
        TRAVELED.clear();
    }
}
