package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/**
 * Paquet client -> serveur pour la roulette du Coffre au Trésor : {@link #START} = "j'ai cliqué sur OUVRIR" (le serveur consomme
 * le coffre et tire le fruit), {@link #CANCEL} = "j'ai refermé la fenêtre sans lancer" (le joueur garde son coffre).
 */
public record StartRoulettePayload(int action) implements CustomPacketPayload {

    public static final int START = 0;
    public static final int CANCEL = 1;

    public static final CustomPacketPayload.Type<StartRoulettePayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("start_roulette"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StartRoulettePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StartRoulettePayload::action,
            StartRoulettePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
