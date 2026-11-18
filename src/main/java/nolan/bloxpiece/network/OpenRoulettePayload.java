package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/**
 * Paquet serveur -> client pour la roulette du Coffre au Trésor (TreasureChests) :
 * <ul>
 *   <li>{@link #OPEN} : ouvre la fenêtre (rien n'est encore consommé ni tiré) ;</li>
 *   <li>id d'un objet (ex : bloxpiece:mera_mera_no_mi) : réponse au clic sur OUVRIR, la roulette doit s'arrêter sur ce fruit
 *       (le serveur le donnera lui-même à la fin de l'animation) ;</li>
 *   <li>{@link #FAILED} : le coffre n'est plus là, la fenêtre se ferme.</li>
 * </ul>
 */
public record OpenRoulettePayload(String fruitItemId) implements CustomPacketPayload {

    public static final String OPEN = "";
    public static final String FAILED = "-";

    public static final CustomPacketPayload.Type<OpenRoulettePayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("open_roulette"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRoulettePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenRoulettePayload::fruitItemId,
            OpenRoulettePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
