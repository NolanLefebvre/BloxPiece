package nolan.bloxpiece.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import nolan.bloxpiece.mob.TavernKeeper;
import nolan.bloxpiece.progression.Progression;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Logique SERVEUR des quêtes répétables (missions du Tavernier). Tout est décidé ici ; le client affiche (attachment
 * REPEATABLE_QUESTS synchronisé) et demande (RepeatableQuestPayload : accepter / récupérer).
 * <ol>
 *   <li>Le joueur parle au Tavernier : s'il n'a pas de mission en cours, une mission lui est tirée et GARDÉE (rouvrir le
 *       dialogue ne la change pas) : {@link #prepareOffer}.</li>
 *   <li>Il l'accepte ({@link #accept}) : elle devient sa mission en cours (une seule à la fois).</li>
 *   <li>Les ennemis qu'il tue lui-même la font avancer ({@link #onKill}) ; tous les objectifs atteints = terminée.</li>
 *   <li>Il revient au Tavernier et récupère l'XP ({@link #claim}) : la mission est retirée PUIS l'XP donnée (aucun double
 *       gain), et une nouvelle mission lui est proposée.</li>
 * </ol>
 * La récompense s'ajoute à l'XP normale de chaque ennemi tué (MobXp), qui n'est jamais redonnée ici.
 */
public final class RepeatableQuests {

    /** Distance maximale (blocs) au Tavernier pour accepter ou récupérer. */
    private static final double TALK_RANGE = 8.0;

    private RepeatableQuests() {
    }

    public static RepeatableQuest.Data data(ServerPlayer player) {
        RepeatableQuest.Data data = player.getAttached(ModAttachments.REPEATABLE_QUESTS);
        return data == null ? RepeatableQuest.Data.EMPTY : data;
    }

    private static void set(ServerPlayer player, RepeatableQuest.Data data) {
        player.setAttached(ModAttachments.REPEATABLE_QUESTS, data);
    }

    /** Le joueur parle au Tavernier : une mission lui est proposée s'il n'en a ni en cours ni déjà proposée. */
    public static void prepareOffer(ServerPlayer player) {
        RepeatableQuest.Data data = data(player);
        if (data.active().isEmpty() && data.offer().isEmpty()) {
            RepeatableQuest offer = RepeatableTemplates.roll(player.getRandom(), data.lastTemplate());
            set(player, new RepeatableQuest.Data(Optional.of(offer), Optional.empty(), data.completedCount(), data.lastTemplate()));
        }
    }

    private static boolean nearKeeper(ServerPlayer player, int keeperId) {
        Entity npc = player.level().getEntity(keeperId);
        return npc instanceof TavernKeeper && npc.isAlive() && npc.distanceToSqr(player) <= TALK_RANGE * TALK_RANGE;
    }

    /** Accepte la mission proposée (vérifié : Tavernier à portée, aucune mission en cours, une mission proposée). */
    public static void accept(ServerPlayer player, int keeperId) {
        RepeatableQuest.Data data = data(player);
        if (!nearKeeper(player, keeperId) || data.active().isPresent() || data.offer().isEmpty()) {
            return;
        }
        RepeatableQuest quest = data.offer().get();
        set(player, new RepeatableQuest.Data(Optional.empty(), Optional.of(quest), data.completedCount(), quest.template()));
        player.sendSystemMessage(Component.translatable("message.bloxpiece.repeatable.accepted", Component.translatable(quest.titleKey()))
                .withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Récupère la récompense d'une mission terminée (vérifié : Tavernier à portée, mission terminée et pas encore récupérée). */
    public static void claim(ServerPlayer player, int keeperId) {
        RepeatableQuest.Data data = data(player);
        if (!nearKeeper(player, keeperId) || data.active().isEmpty() || !data.active().get().completed()) {
            return;
        }
        RepeatableQuest quest = data.active().get();
        // la mission est retirée AVANT de donner l'XP, et une nouvelle mission est proposée tout de suite
        RepeatableQuest next = RepeatableTemplates.roll(player.getRandom(), quest.template());
        set(player, new RepeatableQuest.Data(Optional.of(next), Optional.empty(), data.completedCount() + 1, quest.template()));
        Progression.addXp(player, quest.reward());
        player.sendSystemMessage(Component.translatable("message.bloxpiece.repeatable.claimed", Component.translatable(quest.titleKey()),
                quest.reward()).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * Un ennemi tué par CE joueur (ModEvents ne l'appelle que pour l'auteur du coup fatal) : avance le premier objectif de la
     * mission en cours dont la cible correspond exactement à ce type d'ennemi. Une mort = au plus +1 sur un seul objectif.
     */
    public static void onKill(ServerPlayer killer, LivingEntity victim) {
        RepeatableQuest.Data data = data(killer);
        if (data.active().isEmpty() || data.active().get().completed()) {
            return;
        }
        RepeatableQuest quest = data.active().get();
        List<RepeatableQuest.Objective> objectives = new ArrayList<>(quest.objectives());
        boolean changed = false;
        for (int i = 0; i < objectives.size(); i++) {
            RepeatableQuest.Objective o = objectives.get(i);
            QuestTarget target = o.questTarget();
            if (target != null && !o.done() && target.matches(victim.getType())) {
                objectives.set(i, new RepeatableQuest.Objective(o.target(), o.count(), o.progress() + 1));
                changed = true;
                break;
            }
        }
        if (!changed) {
            return;
        }
        boolean done = true;
        for (RepeatableQuest.Objective o : objectives) {
            done &= o.done();
        }
        RepeatableQuest updated = new RepeatableQuest(quest.template(), quest.difficulty(), objectives, quest.reward(), done);
        set(killer, new RepeatableQuest.Data(data.offer(), Optional.of(updated), data.completedCount(), data.lastTemplate()));
        if (done) {
            killer.sendSystemMessage(Component.translatable("message.bloxpiece.repeatable.complete", Component.translatable(quest.titleKey()))
                    .withStyle(ChatFormatting.GREEN));
            killer.level().playSound(null, killer.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        }
    }

    /** À la connexion : l'attachment existe toujours (le menu du client a de quoi afficher). */
    public static void onJoin(ServerPlayer player) {
        if (player.getAttached(ModAttachments.REPEATABLE_QUESTS) == null) {
            set(player, RepeatableQuest.Data.EMPTY);
        }
    }
}
