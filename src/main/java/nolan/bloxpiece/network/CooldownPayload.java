package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/**
 * Paquet envoyé par le serveur au client : "cette attaque est en recharge pour encore N ticks".
 * ticks = 0 : l'attaque est de nouveau disponible (recharge effacée).
 * La clé est la même que celle utilisée par AbilityManager côté serveur ("fruit:attaque").
 * Le client s'en sert uniquement pour AFFICHER le HUD, la vraie recharge reste gérée par le serveur.
 */
public record CooldownPayload(String key, int ticks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CooldownPayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("cooldown"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CooldownPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CooldownPayload::key,
            ByteBufCodecs.VAR_INT, CooldownPayload::ticks,
            CooldownPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
