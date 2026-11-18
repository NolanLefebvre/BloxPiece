package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/** Serveur -> client : ouvre la fenêtre de dialogue du PNJ {@code npc} (entité {@code entityId}, pour son nom et son regard). */
public record OpenDialoguePayload(String npc, int entityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenDialoguePayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("open_dialogue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDialoguePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenDialoguePayload::npc,
            ByteBufCodecs.VAR_INT, OpenDialoguePayload::entityId,
            OpenDialoguePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
