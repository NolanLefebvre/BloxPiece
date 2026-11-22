package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/**
 * Paquet client -> serveur : touche « Basculer fruit / race ». Une simple DEMANDE : le serveur (RaceAbilities.toggle) change
 * l'ensemble d'attaques des touches d'attaque (fruit du démon ou race), l'enregistre et le synchronise.
 */
public record ToggleAttackSetPayload() implements CustomPacketPayload {

    public static final ToggleAttackSetPayload INSTANCE = new ToggleAttackSetPayload();

    public static final CustomPacketPayload.Type<ToggleAttackSetPayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("toggle_attack_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleAttackSetPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
