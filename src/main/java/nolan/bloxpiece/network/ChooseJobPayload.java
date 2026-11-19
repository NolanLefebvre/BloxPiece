package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/** Client -> serveur : le joueur choisit ce job dans le menu du personnage (le serveur vérifie le niveau). */
public record ChooseJobPayload(String job) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ChooseJobPayload> TYPE = new CustomPacketPayload.Type<>(BloxPiece.id("choose_job"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChooseJobPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ChooseJobPayload::job,
            ChooseJobPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
