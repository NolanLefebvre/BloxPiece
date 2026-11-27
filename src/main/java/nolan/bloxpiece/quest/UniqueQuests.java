package nolan.bloxpiece.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

import nolan.bloxpiece.mob.Rayleigh;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Logique SERVEUR des quêtes uniques (quêtes de Haki de Silvers Rayleigh). Tout est décidé ici ; le client ne fait qu'afficher
 * (attachment UNIQUE_QUESTS synchronisé) et demander l'acceptation (AcceptUniqueQuestPayload).
 * <ul>
 *   <li>{@link #accept} : le joueur parle à Rayleigh (vérifié : Rayleigh existe, à portée) et accepte une quête DISPONIBLE
 *       (niveau atteint, jamais acceptée, contenu publié, quête précédente terminée). Une quête en cours ou terminée ne peut
 *       pas être reprise.</li>
 *   <li>Suivi : appelé par les événements du jeu (ModEvents) : mob tué ({@link #onKill}), coup de poing ({@link #onPunchStart}
 *       puis {@link #onDamage}), bloc cassé ({@link #onBlockBroken}). Seules les quêtes EN COURS avancent.</li>
 *   <li>Quand tous les objectifs sont atteints : la quête passe « terminée » PUIS la récompense est donnée (Haki débloqué pour
 *       de bon : HakiManager / HakiMastery), une seule fois (l'entrée est déjà marquée terminée : aucun double gain).</li>
 * </ul>
 */
public final class UniqueQuests {

    /** Distance maximale (blocs) à Rayleigh pour accepter une quête. */
    private static final double TALK_RANGE = 8.0;

    /** Coup de poing en cours : la cible frappée et le tick (confirmé par les dégâts réellement infligés dans le même tick). */
    private record Punch(int targetId, long tick) {
    }

    private static final Map<UUID, Punch> PUNCHES = new HashMap<>();

    private UniqueQuests() {
    }

    public static UniqueQuestData data(ServerPlayer player) {
        UniqueQuestData data = player.getAttached(ModAttachments.UNIQUE_QUESTS);
        return data == null ? UniqueQuestData.EMPTY : data;
    }

    // ------------------------------------------------------------------ acceptation (Rayleigh)

    /** Le joueur accepte la quête {@code id} auprès de Rayleigh (entité {@code rayleighId}). Tout est vérifié ici. */
    public static void accept(ServerPlayer player, String id, int rayleighId) {
        UniqueQuest quest = UniqueQuest.byId(id);
        Entity npc = player.level().getEntity(rayleighId);
        if (quest == null || !(npc instanceof Rayleigh) || !npc.isAlive() || npc.distanceToSqr(player) > TALK_RANGE * TALK_RANGE) {
            return;
        }
        if (quest.status(player) != UniqueQuest.Status.AVAILABLE) {
            return; // niveau insuffisant, déjà en cours, déjà terminée, ou pas encore publiée
        }
        List<Integer> zero = new ArrayList<>();
        for (int i = 0; i < quest.objectives.size(); i++) {
            zero.add(0);
        }
        player.setAttached(ModAttachments.UNIQUE_QUESTS, data(player).with(new UniqueQuestData.Entry(quest.id, false, zero)));
        player.sendSystemMessage(Component.translatable("message.bloxpiece.unique_quest.accepted",
                Component.translatable(quest.titleKey())).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
    }

    // ------------------------------------------------------------------ suivi des objectifs

    /** Un mob tué : compte seulement si ce joueur en est l'auteur (ModEvents ne l'appelle que pour le joueur responsable). */
    public static void onKill(ServerPlayer killer, LivingEntity victim) {
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
        progress(killer, UniqueQuest.Kind.KILL, type, 1);
    }

    /** Bloc cassé par le joueur. */
    public static void onBlockBroken(ServerPlayer player) {
        progress(player, UniqueQuest.Kind.BREAK_BLOCK, "", 1);
    }

    /**
     * Le joueur frappe une entité en mêlée (AttackEntityCallback) : si c'est un mob hostile et qu'il a la main vide, on retient
     * ce coup ; il ne compte que si les dégâts sont réellement infligés dans le même tick ({@link #onDamage}). Un coup sur une
     * cible invulnérable (temps de récupération), bloqué, ou porté avec un objet en main ne compte donc pas.
     */
    public static void onPunchStart(ServerPlayer player, Entity target) {
        if (player.getMainHandItem().isEmpty() && target instanceof Enemy && target instanceof LivingEntity && hasActive(player,
                UniqueQuest.Kind.PUNCH_HOSTILE)) {
            PUNCHES.put(player.getUUID(), new Punch(target.getId(), player.level().getGameTime()));
        } else {
            PUNCHES.remove(player.getUUID());
        }
    }

    /** Dégâts réellement subis (ServerLivingEntityEvents.AFTER_DAMAGE) : confirme un coup de poing retenu, une seule fois. */
    public static void onDamage(LivingEntity victim, DamageSource source, float taken, boolean blocked) {
        if (blocked || taken <= 0.0F || !(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player
                || !source.is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        Punch punch = PUNCHES.remove(player.getUUID()); // retiré : le même coup ne peut pas compter deux fois
        if (punch != null && punch.targetId() == victim.getId() && punch.tick() == player.level().getGameTime()
                && player.getMainHandItem().isEmpty()) {
            progress(player, UniqueQuest.Kind.PUNCH_HOSTILE, "", 1);
        }
    }

    private static boolean hasActive(ServerPlayer player, UniqueQuest.Kind kind) {
        for (UniqueQuestData.Entry entry : data(player).quests()) {
            UniqueQuest quest = UniqueQuest.byId(entry.id());
            if (quest != null && !entry.completed()) {
                for (UniqueQuest.Objective o : quest.objectives) {
                    if (o.kind() == kind) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Fait avancer les objectifs de cette sorte (et de ce sujet) dans toutes les quêtes en cours du joueur. */
    private static void progress(ServerPlayer player, UniqueQuest.Kind kind, String subject, int amount) {
        UniqueQuestData data = data(player);
        UniqueQuestData updated = data;
        List<UniqueQuest> finished = new ArrayList<>();
        for (UniqueQuestData.Entry entry : data.quests()) {
            UniqueQuest quest = UniqueQuest.byId(entry.id());
            if (quest == null || entry.completed()) {
                continue;
            }
            List<Integer> values = new ArrayList<>();
            boolean changed = false;
            for (int i = 0; i < quest.objectives.size(); i++) {
                UniqueQuest.Objective o = quest.objectives.get(i);
                int value = Math.min(o.target(), entry.progress(i));
                if (o.kind() == kind && o.subject().equals(subject) && value < o.target()) {
                    value = Math.min(o.target(), value + amount);
                    changed = true;
                    if (value >= o.target()) {
                        player.sendSystemMessage(Component.translatable("message.bloxpiece.unique_quest.objective_done",
                                objectiveText(o)).withStyle(ChatFormatting.GREEN), true);
                    }
                }
                values.add(value);
            }
            if (!changed) {
                continue;
            }
            boolean done = true;
            for (int i = 0; i < quest.objectives.size(); i++) {
                done &= values.get(i) >= quest.objectives.get(i).target();
            }
            updated = updated.with(new UniqueQuestData.Entry(quest.id, done, values));
            if (done) {
                finished.add(quest);
            }
        }
        if (updated != data) {
            player.setAttached(ModAttachments.UNIQUE_QUESTS, updated); // marquée terminée AVANT la récompense
            for (UniqueQuest quest : finished) {
                reward(player, quest);
            }
        }
    }

    /** Texte d'un objectif (avec le nom de l'entité pour un objectif « tuer »). */
    public static Component objectiveText(UniqueQuest.Objective o) {
        if (o.kind() == UniqueQuest.Kind.KILL) {
            return Component.translatable(o.textKey(), Component.translatable("entity." + o.subject().replace(':', '.')));
        }
        return Component.translatable(o.textKey());
    }

    /** Quête validée : le Haki (ou son évolution) est débloqué pour de bon, puis le joueur est prévenu. */
    private static void reward(ServerPlayer player, UniqueQuest quest) {
        quest.grant(player);
        Component haki = Component.translatable(quest.haki.nameKey());
        player.sendSystemMessage(Component.translatable("message.bloxpiece.unique_quest.complete",
                Component.translatable(quest.titleKey()), haki).withStyle(ChatFormatting.GOLD));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.bloxpiece.unique_quest.title")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(quest.rewardKey(), haki)
                .withStyle(ChatFormatting.YELLOW)));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // ------------------------------------------------------------------ connexion

    /**
     * À la connexion : un Haki déjà débloqué avant ce système (ancienne sauvegarde, commande d'administrateur) compte comme
     * quête terminée (Rayleigh ne la repropose pas, le menu l'affiche « terminée »). Ne débloque jamais rien.
     */
    public static void onJoin(ServerPlayer player) {
        UniqueQuestData data = data(player);
        UniqueQuestData updated = data;
        for (UniqueQuest quest : UniqueQuest.values()) {
            UniqueQuestData.Entry entry = data.get(quest);
            if ((entry == null || !entry.completed()) && quest.rewardOwned(player)) {
                List<Integer> full = new ArrayList<>();
                for (UniqueQuest.Objective o : quest.objectives) {
                    full.add(o.target());
                }
                updated = updated.with(new UniqueQuestData.Entry(quest.id, true, full));
            }
        }
        if (updated != data || player.getAttached(ModAttachments.UNIQUE_QUESTS) == null) {
            player.setAttached(ModAttachments.UNIQUE_QUESTS, updated);
        }
    }

    public static void forget(UUID id) {
        PUNCHES.remove(id);
    }
}
