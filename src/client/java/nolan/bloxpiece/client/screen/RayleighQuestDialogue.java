package nolan.bloxpiece.client.screen;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import nolan.bloxpiece.network.AcceptUniqueQuestPayload;
import nolan.bloxpiece.quest.UniqueQuest;

/**
 * Silvers Rayleigh, maître des Haki : ce qu'il répond au bouton « quêtes » de son dialogue (DialogueScreen), d'après la
 * progression du joueur (niveau et quêtes uniques, synchronisés par le serveur). Dans l'ordre :
 * <ol>
 *   <li>une quête DISPONIBLE (niveau atteint, jamais acceptée) : il la propose — boutons « Accepter » / « Plus tard » ; Accepter
 *       envoie AcceptUniqueQuestPayload et le SERVEUR vérifie tout (niveau, Rayleigh à portée, pas déjà en cours ni terminée) ;</li>
 *   <li>sinon une quête EN COURS : il encourage le joueur et le renvoie au menu des quêtes ;</li>
 *   <li>sinon la prochaine quête trop haute : il indique le niveau à atteindre ;</li>
 *   <li>sinon (tout le reste terminé) : les évolutions viendront plus tard.</li>
 * </ol>
 * L'affichage ne fait que refléter l'état : rien n'est décidé ici.
 */
final class RayleighQuestDialogue {

    private RayleighQuestDialogue() {
    }

    static void open(DialogueScreen screen) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        UniqueQuest active = null, locked = null;
        boolean unreleased = false;
        for (UniqueQuest quest : UniqueQuest.values()) {
            UniqueQuest.Status status = quest.status(player);
            if (status == UniqueQuest.Status.AVAILABLE) {
                offer(screen, quest);
                return;
            }
            if (status == UniqueQuest.Status.ACTIVE && active == null) {
                active = quest;
            } else if (status == UniqueQuest.Status.LOCKED && (locked == null || quest.level < locked.level)) {
                locked = quest;
            } else if (status == UniqueQuest.Status.UNRELEASED) {
                unreleased = true;
            }
        }
        if (active != null) {
            screen.showMain(Component.translatable(screen.npcKey("quests.active"), Component.translatable(active.titleKey())));
        } else if (locked != null) {
            screen.showMain(Component.translatable(screen.npcKey("quests.locked"), locked.level,
                    Component.translatable(locked.haki.nameKey())));
        } else if (unreleased) {
            screen.showMain(Component.translatable(screen.npcKey("quests.evolutions_soon")));
        } else {
            screen.showMain(Component.translatable(screen.npcKey("quests.all_done")));
        }
    }

    private static void offer(DialogueScreen screen, UniqueQuest quest) {
        screen.say(Component.translatable(quest.offerKey()));
        screen.setOptions(List.of(
                new DialogueScreen.Option(Component.translatable(screen.npcKey("quests.accept")), () -> {
                    ClientPlayNetworking.send(new AcceptUniqueQuestPayload(quest.id, screen.entityId()));
                    screen.showMain(Component.translatable(screen.npcKey("quests.accepted"), Component.translatable(quest.titleKey())));
                }),
                new DialogueScreen.Option(Component.translatable(screen.npcKey("quests.later")),
                        () -> screen.showMain(Component.translatable(screen.npcKey("quests.later_answer"))))));
    }
}
