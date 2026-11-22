package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/**
 * Paquet client -> serveur : touche d'attaque RACIALE n° {@code slot} enfoncée ({@code pressed} = true) ou relâchée. Comme
 * UseAbilityPayload pour les fruits : le client n'envoie que le numéro de la touche, le serveur (RaceAbilities) vérifie tout
 * (race, attaque développée, niveau, recharge...).
 */
public record UseRaceAttackPayload(int slot, boolean pressed) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UseRaceAttackPayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("use_race_attack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UseRaceAttackPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UseRaceAttackPayload::slot,
            ByteBufCodecs.BOOL, UseRaceAttackPayload::pressed,
            UseRaceAttackPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
