package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/**
 * Serveur -> clients : le joueur {@code entityId} vient d'esquiver une attaque avec le Haki de l'Observation. {@code variant} :
 * type d'esquive (0 à {@link #VARIANTS} - 1, voir HakiClientState), {@code right} : côté. Envoyé tout de suite à lui et aux
 * joueurs proches, qui jouent l'animation d'esquive.
 */
public record HakiDodgePayload(int entityId, int variant, boolean right) implements CustomPacketPayload {

    /** Nombre de types d'esquive différents. */
    public static final int VARIANTS = 6;

    public static final CustomPacketPayload.Type<HakiDodgePayload> TYPE = new CustomPacketPayload.Type<>(BloxPiece.id("haki_dodge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HakiDodgePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HakiDodgePayload::entityId,
            ByteBufCodecs.VAR_INT, HakiDodgePayload::variant,
            ByteBufCodecs.BOOL, HakiDodgePayload::right,
            HakiDodgePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
