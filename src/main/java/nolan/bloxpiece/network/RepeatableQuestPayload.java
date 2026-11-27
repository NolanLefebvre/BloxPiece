package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/**
 * Client -> serveur : action sur la mission du Tavernier (entité {@code keeperId}) : {@link #ACCEPT} la mission proposée, ou
 * {@link #CLAIM} la récompense d'une mission terminée. Le serveur vérifie tout (RepeatableQuests).
 */
public record RepeatableQuestPayload(int action, int keeperId) implements CustomPacketPayload {

    public static final int ACCEPT = 0;
    public static final int CLAIM = 1;

    public static final CustomPacketPayload.Type<RepeatableQuestPayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("repeatable_quest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RepeatableQuestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RepeatableQuestPayload::action,
            ByteBufCodecs.VAR_INT, RepeatableQuestPayload::keeperId,
            RepeatableQuestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
