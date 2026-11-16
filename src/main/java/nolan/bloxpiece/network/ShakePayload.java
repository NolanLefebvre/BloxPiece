package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/** Paquet serveur -> client : "fais trembler ta caméra" (force en degrés, durée en ticks). Effet purement visuel côté client. */
public record ShakePayload(float strength, int ticks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShakePayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("shake"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShakePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ShakePayload::strength,
            ByteBufCodecs.VAR_INT, ShakePayload::ticks,
            ShakePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
