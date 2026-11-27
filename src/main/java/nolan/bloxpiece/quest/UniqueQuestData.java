package nolan.bloxpiece.quest;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Les quêtes uniques d'UN joueur (attachment ModAttachments.UNIQUE_QUESTS : sauvegardé avec le joueur, donc lié à son UUID,
 * conservé à la mort, synchronisé avec SON client seulement). Une entrée par quête acceptée : en cours (progression de chaque
 * objectif) ou terminée. Une quête sans entrée n'a jamais été acceptée. Donnée immuable : toute modification crée une nouvelle
 * valeur (UniqueQuests).
 */
public record UniqueQuestData(List<Entry> quests) {

    /** Une quête acceptée : {@code progress} = avancement de chaque objectif, dans l'ordre de UniqueQuest.objectives. */
    public record Entry(String id, boolean completed, List<Integer> progress) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Entry::id),
                Codec.BOOL.optionalFieldOf("completed", false).forGetter(Entry::completed),
                Codec.INT.listOf().optionalFieldOf("progress", List.of()).forGetter(Entry::progress)
        ).apply(instance, Entry::new));

        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::id,
                ByteBufCodecs.BOOL, Entry::completed,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Entry::progress,
                Entry::new
        );

        public Entry {
            progress = List.copyOf(progress);
        }

        /** Avancement de l'objectif n° i (0 s'il manque). */
        public int progress(int i) {
            return i >= 0 && i < this.progress.size() ? Math.max(0, this.progress.get(i)) : 0;
        }
    }

    public static final UniqueQuestData EMPTY = new UniqueQuestData(List.of());

    public static final Codec<UniqueQuestData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Entry.CODEC.listOf().optionalFieldOf("quests", List.of()).forGetter(UniqueQuestData::quests)
    ).apply(instance, UniqueQuestData::new));

    public static final StreamCodec<ByteBuf, UniqueQuestData> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), UniqueQuestData::quests,
            UniqueQuestData::new
    );

    public UniqueQuestData {
        quests = List.copyOf(quests);
    }

    public Entry get(UniqueQuest quest) {
        for (Entry entry : this.quests) {
            if (entry.id().equals(quest.id)) {
                return entry;
            }
        }
        return null;
    }

    /** Copie avec l'entrée de cette quête remplacée (ou ajoutée). */
    UniqueQuestData with(Entry entry) {
        List<Entry> list = new ArrayList<>();
        boolean replaced = false;
        for (Entry e : this.quests) {
            if (e.id().equals(entry.id())) {
                list.add(entry);
                replaced = true;
            } else {
                list.add(e);
            }
        }
        if (!replaced) {
            list.add(entry);
        }
        return new UniqueQuestData(list);
    }
}
