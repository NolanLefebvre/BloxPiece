package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/**
 * Client -> serveur : « j'accepte la quête unique {@code quest} que me propose Silvers Rayleigh (entité {@code rayleighId}) ».
 * Le serveur vérifie tout (UniqueQuests.accept) : Rayleigh à portée, niveau, quête ni en cours ni terminée.
 */
public record AcceptUniqueQuestPayload(String quest, int rayleighId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AcceptUniqueQuestPayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("accept_unique_quest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AcceptUniqueQuestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AcceptUniqueQuestPayload::quest,
            ByteBufCodecs.VAR_INT, AcceptUniqueQuestPayload::rayleighId,
            AcceptUniqueQuestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
