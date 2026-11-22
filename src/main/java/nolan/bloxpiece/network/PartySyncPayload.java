package nolan.bloxpiece.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import nolan.bloxpiece.BloxPiece;

/**
 * Paquet serveur -> client : l'état de la Party du joueur (page « Party » du menu personnage), renvoyé à chaque changement
 * (création, arrivée, départ, exclusion, nouveau chef, connexion / déconnexion d'un membre, invitation reçue ou expirée).
 * Le client ne fait que l'afficher : toutes les décisions sont prises par le serveur (Parties).
 *
 * @param inParty    le joueur est dans une Party
 * @param partyId    identifiant unique de sa Party ("" sinon)
 * @param name       nom de la Party (pseudo du fondateur : le client affiche « Party de &lt;nom&gt; »)
 * @param leader     UUID du chef (texte)
 * @param maxMembers limite de membres (chef compris)
 * @param members    membres, dans l'ordre d'arrivée
 * @param candidates joueurs connectés qu'il peut inviter (seulement pour le chef)
 * @param invites    invitations reçues en attente (seulement s'il n'est dans aucune Party)
 */
public record PartySyncPayload(boolean inParty, String partyId, String name, String leader, int maxMembers,
                               List<Member> members, List<Candidate> candidates, List<Invite> invites)
        implements CustomPacketPayload {

    /** Un membre : UUID (texte), pseudo, connecté ou non. */
    public record Member(String uuid, String name, boolean online) {
    }

    /** Un joueur connecté qu'on peut inviter. */
    public record Candidate(String uuid, String name) {
    }

    /** Une invitation reçue : Party, nom de la Party (pseudo du fondateur), pseudo de celui qui invite, secondes restantes. */
    public record Invite(String partyId, String partyName, String inviter, int secondsLeft) {
    }

    public static final CustomPacketPayload.Type<PartySyncPayload> TYPE =
            new CustomPacketPayload.Type<>(BloxPiece.id("party_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartySyncPayload> CODEC =
            StreamCodec.ofMember(PartySyncPayload::write, PartySyncPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(inParty);
        buf.writeUtf(partyId);
        buf.writeUtf(name);
        buf.writeUtf(leader);
        buf.writeVarInt(maxMembers);
        buf.writeVarInt(members.size());
        for (Member m : members) {
            buf.writeUtf(m.uuid());
            buf.writeUtf(m.name());
            buf.writeBoolean(m.online());
        }
        buf.writeVarInt(candidates.size());
        for (Candidate c : candidates) {
            buf.writeUtf(c.uuid());
            buf.writeUtf(c.name());
        }
        buf.writeVarInt(invites.size());
        for (Invite i : invites) {
            buf.writeUtf(i.partyId());
            buf.writeUtf(i.partyName());
            buf.writeUtf(i.inviter());
            buf.writeVarInt(i.secondsLeft());
        }
    }

    private static PartySyncPayload read(RegistryFriendlyByteBuf buf) {
        boolean inParty = buf.readBoolean();
        String partyId = buf.readUtf();
        String name = buf.readUtf();
        String leader = buf.readUtf();
        int max = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), 64);
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            members.add(new Member(buf.readUtf(), buf.readUtf(), buf.readBoolean()));
        }
        n = Math.min(buf.readVarInt(), 256);
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            candidates.add(new Candidate(buf.readUtf(), buf.readUtf()));
        }
        n = Math.min(buf.readVarInt(), 64);
        List<Invite> invites = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            invites.add(new Invite(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt()));
        }
        return new PartySyncPayload(inParty, partyId, name, leader, max, members, candidates, invites);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
