package nolan.bloxpiece.quest;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Une mission du Tavernier (quête répétable) : la mission tirée ({@code template}, RepeatableTemplates), sa difficulté, ses
 * objectifs (cible, nombre demandé, nombre déjà tué), la récompense en XP fixée au tirage et si elle est terminée. Immuable.
 */
public record RepeatableQuest(String template, int difficulty, List<Objective> objectives, int reward, boolean completed) {

    /** Un objectif : la cible (QuestTarget.id), le nombre à tuer, le nombre déjà tué. */
    public record Objective(String target, int count, int progress) {

        public static final Codec<Objective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("target").forGetter(Objective::target),
                Codec.INT.fieldOf("count").forGetter(Objective::count),
                Codec.INT.optionalFieldOf("progress", 0).forGetter(Objective::progress)
        ).apply(instance, Objective::new));

        public static final StreamCodec<ByteBuf, Objective> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Objective::target,
                ByteBufCodecs.VAR_INT, Objective::count,
                ByteBufCodecs.VAR_INT, Objective::progress,
                Objective::new
        );

        public Objective {
            count = Math.max(1, count);
            progress = Math.max(0, Math.min(count, progress)); // une sauvegarde modifiée ne dépasse jamais l'objectif
        }

        public boolean done() {
            return this.progress >= this.count;
        }

        public QuestTarget questTarget() {
            return QuestTarget.byId(this.target);
        }
    }

    public static final Codec<RepeatableQuest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("template").forGetter(RepeatableQuest::template),
            Codec.INT.fieldOf("difficulty").forGetter(RepeatableQuest::difficulty),
            Objective.CODEC.listOf().fieldOf("objectives").forGetter(RepeatableQuest::objectives),
            Codec.INT.fieldOf("reward").forGetter(RepeatableQuest::reward),
            Codec.BOOL.optionalFieldOf("completed", false).forGetter(RepeatableQuest::completed)
    ).apply(instance, RepeatableQuest::new));

    public static final StreamCodec<ByteBuf, RepeatableQuest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RepeatableQuest::template,
            ByteBufCodecs.VAR_INT, RepeatableQuest::difficulty,
            Objective.STREAM_CODEC.apply(ByteBufCodecs.list()), RepeatableQuest::objectives,
            ByteBufCodecs.VAR_INT, RepeatableQuest::reward,
            ByteBufCodecs.BOOL, RepeatableQuest::completed,
            RepeatableQuest::new
    );

    public RepeatableQuest {
        objectives = List.copyOf(objectives);
        difficulty = Math.max(0, Math.min(3, difficulty));
        reward = Math.max(0, reward);
    }

    public String titleKey() {
        return "quest.bloxpiece.repeatable." + this.template + ".title";
    }

    public String descriptionKey() {
        return "quest.bloxpiece.repeatable.description." + this.difficulty;
    }

    public static String difficultyKey(int difficulty) {
        return "quest.bloxpiece.repeatable.difficulty." + difficulty;
    }

    /** Couleur de la difficulté (menus) : vert, jaune, orange, rouge. */
    public static int difficultyColor(int difficulty) {
        return switch (difficulty) {
            case 0 -> 0xFF5BE36B;
            case 1 -> 0xFFFFD25A;
            case 2 -> 0xFFFF9A3C;
            default -> 0xFFFF5050;
        };
    }

    /** Les données de quêtes répétables d'un joueur : la mission proposée, la mission en cours, et l'historique. */
    public record Data(Optional<RepeatableQuest> offer, Optional<RepeatableQuest> active, int completedCount, String lastTemplate) {

        public static final Data EMPTY = new Data(Optional.empty(), Optional.empty(), 0, "");

        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                RepeatableQuest.CODEC.optionalFieldOf("offer").forGetter(Data::offer),
                RepeatableQuest.CODEC.optionalFieldOf("active").forGetter(Data::active),
                Codec.INT.optionalFieldOf("completed_count", 0).forGetter(Data::completedCount),
                Codec.STRING.optionalFieldOf("last_template", "").forGetter(Data::lastTemplate)
        ).apply(instance, Data::new));

        public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(RepeatableQuest.STREAM_CODEC), Data::offer,
                ByteBufCodecs.optional(RepeatableQuest.STREAM_CODEC), Data::active,
                ByteBufCodecs.VAR_INT, Data::completedCount,
                ByteBufCodecs.STRING_UTF8, Data::lastTemplate,
                Data::new
        );
    }
}
