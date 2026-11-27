package nolan.bloxpiece.quest;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.haki.HakiManager;
import nolan.bloxpiece.haki.HakiMastery;
import nolan.bloxpiece.haki.HakiType;
import nolan.bloxpiece.progression.Progression;

/**
 * Les quêtes UNIQUES (réalisables une seule fois dans toute la partie), données par Silvers Rayleigh, le maître des Haki :
 * une quête de déblocage par Haki, puis une quête d'évolution par Haki. Tout le contenu se règle ICI :
 * <ul>
 *   <li>{@code level} : niveau BloxPiece requis pour que Rayleigh la propose (vérifié côté serveur à l'acceptation) ;</li>
 *   <li>{@code objectives} : liste d'objectifs (voir {@link Objective}) ; une liste VIDE = quête « pas encore disponible »
 *       (affichée comme telle, impossible à accepter ni à terminer) ;</li>
 *   <li>{@code requires} : quête à avoir terminée avant (les évolutions demandent le Haki de base) ;</li>
 *   <li>récompense : débloque le Haki (HakiManager, bits HAKI_UNLOCKED) ou son évolution (HakiMastery) — voir {@link #grant}.</li>
 * </ul>
 * Pour écrire plus tard la vraie quête de l'Observation, des Rois ou d'une évolution : remplacer sa liste d'objectifs ci-dessous
 * (et ses textes dans la lang). Rien d'autre à toucher : Rayleigh, le suivi, la sauvegarde et le menu suivent tout seuls.
 * L'ordre des valeurs est celui du menu et des propositions de Rayleigh.
 */
public enum UniqueQuest {

    HAKI_ARMAMENT("haki_armament", HakiType.ARMAMENT, false, 15, null, List.of(
            Objective.kill("bloxpiece:humandrill", 25),
            Objective.punchHostiles(200))),
    // PROVISOIRE : objectif temporaire (casser 1 bloc) en attendant la vraie quête
    HAKI_OBSERVATION("haki_observation", HakiType.OBSERVATION, false, 25, null, List.of(
            Objective.breakBlocks(1))),
    // PROVISOIRE : objectif temporaire (casser 1 bloc) en attendant la vraie quête
    HAKI_CONQUEROR("haki_conqueror", HakiType.CONQUEROR, false, 40, null, List.of(
            Objective.breakBlocks(1))),
    // Évolutions : prévues, pas encore disponibles (aucun objectif)
    HAKI_ARMAMENT_EVOLVED("haki_armament_evolved", HakiType.ARMAMENT, true, 50, HAKI_ARMAMENT, List.of()),
    HAKI_OBSERVATION_EVOLVED("haki_observation_evolved", HakiType.OBSERVATION, true, 65, HAKI_OBSERVATION, List.of()),
    HAKI_CONQUEROR_EVOLVED("haki_conqueror_evolved", HakiType.CONQUEROR, true, 80, HAKI_CONQUEROR, List.of());

    /** Sortes d'objectifs suivies par UniqueQuests (côté serveur). */
    public enum Kind {
        /** Tuer des mobs d'un type ({@code subject} = identifiant de l'entité), tués par le joueur lui-même. */
        KILL("kill"),
        /** Coups de poing (main vide) réellement portés à des mobs hostiles. */
        PUNCH_HOSTILE("punch_hostile"),
        /** Blocs cassés. */
        BREAK_BLOCK("break_block");

        public final String id;

        Kind(String id) {
            this.id = id;
        }
    }

    /** Un objectif : sa sorte, le nombre à atteindre et son sujet éventuel (type d'entité pour KILL). */
    public record Objective(Kind kind, int target, String subject) {
        public static Objective kill(String entityId, int count) {
            return new Objective(Kind.KILL, count, entityId);
        }

        public static Objective punchHostiles(int count) {
            return new Objective(Kind.PUNCH_HOSTILE, count, "");
        }

        public static Objective breakBlocks(int count) {
            return new Objective(Kind.BREAK_BLOCK, count, "");
        }

        /** Clé de traduction du texte de l'objectif (argument : le sujet traduit, s'il y en a un). */
        public String textKey() {
            return "quest.bloxpiece.objective." + this.kind.id;
        }
    }

    /** État d'une quête pour un joueur (calculé, jamais sauvegardé). */
    public enum Status {
        /** Proposée par Rayleigh : le joueur a le niveau, ne l'a pas encore acceptée. */
        AVAILABLE("available"),
        ACTIVE("active"),
        COMPLETED("completed"),
        /** Niveau trop bas (ou quête précédente pas terminée). */
        LOCKED("locked"),
        /** Pas encore implémentée (aucun objectif). */
        UNRELEASED("unreleased");

        public final String id;

        Status(String id) {
            this.id = id;
        }

        public String key() {
            return "screen.bloxpiece.quests.unique.status." + this.id;
        }
    }

    public final String id;
    public final HakiType haki;
    /** Évolution du Haki (maîtrise) plutôt que son déblocage. */
    public final boolean evolution;
    public final int level;
    public final UniqueQuest requires;
    public final List<Objective> objectives;

    UniqueQuest(String id, HakiType haki, boolean evolution, int level, UniqueQuest requires, List<Objective> objectives) {
        this.id = id;
        this.haki = haki;
        this.evolution = evolution;
        this.level = level;
        this.requires = requires;
        this.objectives = List.copyOf(objectives);
    }

    /** La quête a-t-elle un contenu (objectifs) ? Sinon : « pas encore disponible ». */
    public boolean isReleased() {
        return !this.objectives.isEmpty();
    }

    public String titleKey() {
        return "quest.bloxpiece.unique." + this.id + ".title";
    }

    public String descriptionKey() {
        return "quest.bloxpiece.unique." + this.id + ".description";
    }

    /** Réplique de Rayleigh quand il propose la quête. */
    public String offerKey() {
        return "quest.bloxpiece.unique." + this.id + ".offer";
    }

    /** Texte de la récompense (« Débloque le Haki de l'Armement », « Évolution : Ryuo »...). */
    public String rewardKey() {
        return this.evolution ? "quest.bloxpiece.unique.reward.evolution" : "quest.bloxpiece.unique.reward.unlock";
    }

    /** Le joueur possède-t-il déjà ce que donne la quête ? (lisible côté client comme côté serveur : attachments synchronisés) */
    public boolean rewardOwned(Player player) {
        return this.evolution ? HakiMastery.isMastered(player, this.haki) : HakiManager.isUnlocked(player, this.haki);
    }

    /** Donne la récompense (serveur) : déblocage du Haki ou de son évolution, sauvegardé avec le joueur. */
    void grant(ServerPlayer player) {
        if (this.evolution) {
            HakiMastery.setMastered(player, this.haki, true);
        } else {
            HakiManager.setUnlocked(player, this.haki, true);
        }
    }

    /** État de cette quête pour ce joueur (client ou serveur). */
    public Status status(Player player) {
        UniqueQuestData data = player.getAttached(nolan.bloxpiece.registry.ModAttachments.UNIQUE_QUESTS);
        UniqueQuestData.Entry entry = data == null ? null : data.get(this);
        // déjà terminée, ou ce Haki est déjà débloqué (avant ce système, par un administrateur) : rien à refaire
        if (entry != null && entry.completed() || this.rewardOwned(player)) {
            return Status.COMPLETED;
        }
        if (entry != null) {
            return Status.ACTIVE;
        }
        if (!this.isReleased()) {
            return Status.UNRELEASED;
        }
        if (Progression.get(player).level() < this.level
                || this.requires != null && this.requires.status(player) != Status.COMPLETED) {
            return Status.LOCKED;
        }
        return Status.AVAILABLE;
    }

    public static UniqueQuest byId(String id) {
        for (UniqueQuest quest : values()) {
            if (quest.id.equals(id)) {
                return quest;
            }
        }
        return null;
    }
}
