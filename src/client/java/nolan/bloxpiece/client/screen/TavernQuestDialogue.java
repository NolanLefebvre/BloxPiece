package nolan.bloxpiece.client.screen;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import nolan.bloxpiece.network.RepeatableQuestPayload;
import nolan.bloxpiece.quest.QuestTarget;
import nolan.bloxpiece.quest.RepeatableQuest;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Le Tavernier, donneur de missions (quêtes répétables) : ce qu'il répond au bouton « missions » de son dialogue
 * (DialogueScreen), d'après les données synchronisées du joueur (attachment REPEATABLE_QUESTS) :
 * <ul>
 *   <li>mission TERMINÉE : il félicite le joueur — bouton « Récupérer N XP » (le serveur vérifie et donne l'XP) ;</li>
 *   <li>mission EN COURS : il rappelle ce qu'il reste à faire ;</li>
 *   <li>sinon : il propose la mission tirée pour ce joueur (ennemis, nombres, difficulté, XP) — « Accepter » / « Plus tard ».</li>
 * </ul>
 * L'affichage ne fait que refléter l'état : tout est décidé par le serveur (RepeatableQuests).
 */
final class TavernQuestDialogue {

    private TavernQuestDialogue() {
    }

    static void open(DialogueScreen screen) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        RepeatableQuest.Data data = player.getAttached(ModAttachments.REPEATABLE_QUESTS);
        if (data != null && data.active().isPresent()) {
            RepeatableQuest quest = data.active().get();
            if (quest.completed()) {
                screen.say(Component.translatable(screen.npcKey("missions.done"), Component.translatable(quest.titleKey()), quest.reward()));
                screen.setOptions(List.of(
                        new DialogueScreen.Option(Component.translatable(screen.npcKey("missions.claim"), quest.reward()), () -> {
                            ClientPlayNetworking.send(new RepeatableQuestPayload(RepeatableQuestPayload.CLAIM, screen.entityId()));
                            screen.showMain(Component.translatable(screen.npcKey("missions.claimed"), quest.reward()));
                        }),
                        new DialogueScreen.Option(Component.translatable(screen.npcKey("missions.later")),
                                () -> screen.showMain(Component.translatable(screen.npcKey("missions.later_answer"))))));
            } else {
                screen.showMain(Component.translatable(screen.npcKey("missions.active"), Component.translatable(quest.titleKey()),
                        remaining(quest)));
            }
            return;
        }
        if (data == null || data.offer().isEmpty()) {
            screen.showMain(Component.translatable(screen.npcKey("missions.none")));
            return;
        }
        RepeatableQuest offer = data.offer().get();
        screen.say(Component.translatable(screen.npcKey("missions.offer"), Component.translatable(offer.titleKey()), objectives(offer),
                Component.translatable(RepeatableQuest.difficultyKey(offer.difficulty())), offer.reward()));
        screen.setOptions(List.of(
                new DialogueScreen.Option(Component.translatable(screen.npcKey("missions.accept")), () -> {
                    ClientPlayNetworking.send(new RepeatableQuestPayload(RepeatableQuestPayload.ACCEPT, screen.entityId()));
                    screen.showMain(Component.translatable(screen.npcKey("missions.accepted"), Component.translatable(offer.titleKey())));
                }),
                new DialogueScreen.Option(Component.translatable(screen.npcKey("missions.later")),
                        () -> screen.showMain(Component.translatable(screen.npcKey("missions.later_answer"))))));
    }

    /** « 10 Pirates, 1 Capitaine Pirate » : chaque objectif (nombre demandé et cible). */
    static MutableComponent objectives(RepeatableQuest quest) {
        MutableComponent text = Component.empty();
        for (int i = 0; i < quest.objectives().size(); i++) {
            RepeatableQuest.Objective o = quest.objectives().get(i);
            if (i > 0) {
                text.append(", ");
            }
            text.append(o.count() + " ").append(targetName(o));
        }
        return text;
    }

    /** Ce qu'il reste à tuer, objectif par objectif. */
    private static MutableComponent remaining(RepeatableQuest quest) {
        MutableComponent text = Component.empty();
        boolean first = true;
        for (RepeatableQuest.Objective o : quest.objectives()) {
            if (o.done()) {
                continue;
            }
            if (!first) {
                text.append(", ");
            }
            text.append((o.count() - o.progress()) + " ").append(targetName(o));
            first = false;
        }
        return text;
    }

    static Component targetName(RepeatableQuest.Objective o) {
        QuestTarget target = o.questTarget();
        return target == null ? Component.literal(o.target()) : Component.translatable(target.nameKey());
    }
}
