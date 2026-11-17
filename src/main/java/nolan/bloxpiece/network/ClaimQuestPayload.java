package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/** Paquet client -> serveur : "je récupère la récompense de ma quête journalière n° index" (vérifié par le serveur). */
public record ClaimQuestPayload(int index) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClaimQuestPayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("claim_quest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimQuestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ClaimQuestPayload::index,
            ClaimQuestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
