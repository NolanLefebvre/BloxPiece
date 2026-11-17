package nolan.bloxpiece.quest;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Toutes les quêtes d'un joueur (attachment ModAttachments.QUESTS : sauvegardé, conservé à la mort, synchronisé avec son
 * client). Pour l'instant une seule catégorie, les quêtes journalières du jour {@code day} (numéro du jour Minecraft). Une
 * autre catégorie (quêtes principales, hebdomadaires...) s'ajoute ici comme un champ de plus.
 * {@code resetAt} : temps de jeu (getGameTime) auquel le jour change, pour le minuteur du menu (le client n'a qu'à soustraire).
 */
public record QuestData(long day, long resetAt, List<DailyQuest> daily) {

    public static final Codec<QuestData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("day").forGetter(QuestData::day),
            Codec.LONG.optionalFieldOf("reset_at", 0L).forGetter(QuestData::resetAt),
            DailyQuest.CODEC.listOf().fieldOf("daily").forGetter(QuestData::daily)
    ).apply(instance, QuestData::new));

    public static final StreamCodec<ByteBuf, QuestData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, QuestData::day,
            ByteBufCodecs.VAR_LONG, QuestData::resetAt,
            DailyQuest.STREAM_CODEC.apply(ByteBufCodecs.list()), QuestData::daily,
            QuestData::new
    );

    public QuestData {
        daily = List.copyOf(daily);
    }
}
