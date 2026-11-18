package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/**
 * Client -> serveur : action sur la touche du Haki {@code slot} (0 Armement, 1 Observation, 2 Rois) : {@link #PRESS} appui,
 * {@link #RELEASE} relâchement, {@link #HOLD} touche maintenue assez longtemps (mesuré par le client : corps entier d'Armement).
 */
public record UseHakiPayload(int slot, int action) implements CustomPacketPayload {

    public static final int PRESS = 0;
    public static final int RELEASE = 1;
    public static final int HOLD = 2;

    public static final CustomPacketPayload.Type<UseHakiPayload> TYPE = new CustomPacketPayload.Type<>(BloxPiece.id("use_haki"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UseHakiPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UseHakiPayload::slot,
            ByteBufCodecs.VAR_INT, UseHakiPayload::action,
            UseHakiPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
