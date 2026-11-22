package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/**
 * Paquet client -> serveur : bouton « Reroll » du menu Race. Ce n'est qu'une DEMANDE, sans aucune donnée : le serveur
 * (RaceManager.reroll) vérifie le token, tire la race, consomme le token et applique les bonus.
 */
public record RaceRerollPayload() implements CustomPacketPayload {

    public static final RaceRerollPayload INSTANCE = new RaceRerollPayload();

    public static final CustomPacketPayload.Type<RaceRerollPayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("race_reroll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaceRerollPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
