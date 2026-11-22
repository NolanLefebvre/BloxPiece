package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;

import nolan.bloxpiece.BloxPiece;

/**
 * Paquet serveur -> client : résultat d'un reroll de race, DÉJÀ tiré, appliqué et payé (token consommé) par le serveur
 * (RaceManager.reroll). Le menu Race en fait seulement une roulette animée qui s'arrête sur cette race : l'animation ne décide
 * jamais rien.
 */
public record RaceRollResultPayload(String race) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RaceRollResultPayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("race_roll_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaceRollResultPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), RaceRollResultPayload::race,
            RaceRollResultPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
