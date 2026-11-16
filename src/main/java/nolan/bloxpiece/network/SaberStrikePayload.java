package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/** Paquet client -> serveur : "je frappe avec le trident de glace" (lance l'animation d'estoc chez tous les joueurs). */
public record SaberStrikePayload(int unused) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaberStrikePayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("saber_strike"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaberStrikePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SaberStrikePayload::unused,
            SaberStrikePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
