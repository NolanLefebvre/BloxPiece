package nolan.bloxpiece.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Une quête journalière d'un joueur : type, objectif, progression, récompense tirée et si elle a été récupérée.
 * Donnée immuable (toute modification crée une nouvelle valeur).
 */
public record DailyQuest(String type, int target, int progress, boolean claimed, String rewardId, int rewardCount) {

    public static final Codec<DailyQuest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(DailyQuest::type),
            Codec.INT.fieldOf("target").forGetter(DailyQuest::target),
            Codec.INT.optionalFieldOf("progress", 0).forGetter(DailyQuest::progress),
            Codec.BOOL.optionalFieldOf("claimed", false).forGetter(DailyQuest::claimed),
            Codec.STRING.fieldOf("reward").forGetter(DailyQuest::rewardId),
            Codec.INT.fieldOf("reward_count").forGetter(DailyQuest::rewardCount)
    ).apply(instance, DailyQuest::new));

    public static final StreamCodec<ByteBuf, DailyQuest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DailyQuest::type,
            ByteBufCodecs.VAR_INT, DailyQuest::target,
            ByteBufCodecs.VAR_INT, DailyQuest::progress,
            ByteBufCodecs.BOOL, DailyQuest::claimed,
            ByteBufCodecs.STRING_UTF8, DailyQuest::rewardId,
            ByteBufCodecs.VAR_INT, DailyQuest::rewardCount,
            DailyQuest::new
    );

    public boolean isComplete() {
        return this.progress >= this.target;
    }

    public QuestType questType() {
        return QuestType.byId(this.type);
    }

    public QuestType.Reward reward() {
        return new QuestType.Reward(this.rewardId, this.rewardCount);
    }

    DailyQuest withProgress(int value) {
        return new DailyQuest(this.type, this.target, Math.min(this.target, value), this.claimed, this.rewardId, this.rewardCount);
    }

    DailyQuest asClaimed() {
        return new DailyQuest(this.type, this.target, this.progress, true, this.rewardId, this.rewardCount);
    }
}
