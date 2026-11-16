package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/**
 * Paquet envoyé par le client au serveur : "j'ai appuyé sur la touche numéro slot" (pressed = true), ou "je l'ai relâchée"
 * (pressed = false, utile aux attaques qui se chargent tant que la touche est maintenue).
 */
public record UseAbilityPayload(int slot, boolean pressed) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UseAbilityPayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("use_ability"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UseAbilityPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UseAbilityPayload::slot,
            ByteBufCodecs.BOOL, UseAbilityPayload::pressed,
            UseAbilityPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
