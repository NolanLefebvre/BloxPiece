package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/**
 * Serveur -> client : ouvre la fenêtre de dialogue du PNJ {@code npc} (entité {@code entityId}, pour son nom et son regard).
 * {@code speech} : sa première réplique (suffixe de clé « npc.bloxpiece.&lt;pnj&gt;.* »), vide pour son accueil habituel
 * (« greeting ») ; le Tavernier s'en sert pour annoncer le kit de départ.
 */
public record OpenDialoguePayload(String npc, int entityId, String speech) implements CustomPacketPayload {

    public OpenDialoguePayload(String npc, int entityId) {
        this(npc, entityId, "");
    }

    public static final CustomPacketPayload.Type<OpenDialoguePayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("open_dialogue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDialoguePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenDialoguePayload::npc,
            ByteBufCodecs.VAR_INT, OpenDialoguePayload::entityId,
            ByteBufCodecs.STRING_UTF8, OpenDialoguePayload::speech,
            OpenDialoguePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
