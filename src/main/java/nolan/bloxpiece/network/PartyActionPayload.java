package nolan.bloxpiece.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/**
 * Paquet client -> serveur : un bouton de la page « Party » (créer, inviter, accepter, refuser, quitter, exclure, nommer chef,
 * dissoudre). Ce n'est qu'une DEMANDE : le serveur (Parties) vérifie tout (chef, invitation valide, limite, anti-spam...).
 *
 * @param action une des constantes ci-dessous
 * @param arg    selon l'action : UUID du joueur visé, identifiant de la Party de l'invitation, ou "" (texte limité à 64)
 */
public record PartyActionPayload(int action, String arg) implements CustomPacketPayload {

    public static final int CREATE = 0;
    public static final int INVITE = 1;
    public static final int ACCEPT = 2;
    public static final int REFUSE = 3;
    public static final int LEAVE = 4;
    public static final int KICK = 5;
    public static final int TRANSFER = 6;
    public static final int DISBAND = 7;

    public static final CustomPacketPayload.Type<PartyActionPayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("party_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartyActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PartyActionPayload::action,
            ByteBufCodecs.stringUtf8(64), PartyActionPayload::arg,
            PartyActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
