package nolan.bloxpiece.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.network.PartyActionPayload;
import nolan.bloxpiece.network.PartySyncPayload;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Système de Party (groupes de joueurs). TOUT est décidé ici, côté serveur ; le client ne fait qu'envoyer des demandes
 * (PartyActionPayload, ou les commandes /bloxfruit party ...) et afficher l'état reçu (PartySyncPayload).
 * <ul>
 *   <li>Un joueur est dans une seule Party à la fois ({@link #PARTY_OF}). Limite : {@link #MAX_MEMBERS} membres, chef compris.</li>
 *   <li>Création : le joueur devient chef, la Party reçoit un identifiant unique et le nom « Party de &lt;chef&gt; ».</li>
 *   <li>Invitations (chef seulement) : vers un joueur connecté, hors de toute Party ; valables {@link #INVITE_TTL} ticks ; une
 *       invitation toutes les {@link #INVITE_COOLDOWN} ticks par chef ; pas de deuxième invitation de la même Party en attente ;
 *       après un refus, la même Party ne peut pas réinviter ce joueur avant {@link #REFUSE_COOLDOWN} ticks ; au plus
 *       {@link #MAX_PENDING} invitations en attente par joueur. L'invité reçoit un message avec [Accepter] [Refuser]
 *       (commandes /bloxfruit party accept|refuse &lt;id&gt;) et la page Party affiche aussi l'invitation.</li>
 *   <li>Acceptation : vérifiée au moment même (invitation encore valable, pas déjà dans une Party, place libre) puis toutes ses
 *       invitations sont effacées : une invitation ne peut servir qu'une fois.</li>
 *   <li>Chef : exclure, nommer un autre chef, dissoudre. Membre : quitter. Le chef qui part (ou qui se déconnecte alors que d'autres
 *       membres sont connectés) passe le rôle au membre arrivé le plus tôt (connecté de préférence) ; plus personne : dissoute.</li>
 *   <li>Protection : deux membres d'une même Party ne peuvent pas se blesser ({@link #allies}) : tous les dégâts (coups, épées,
 *       projectiles, explosions, fruits, Haki...) sont refusés au niveau de ServerLivingEntityEvents.ALLOW_DAMAGE (l'auteur du
 *       dégât est DamageSource.getEntity : le tireur pour un projectile, le lanceur pour un pouvoir). Les effets sans dégâts des
 *       pouvoirs (recul, paralysie, gel, Haki des Rois, mise à feu) sont filtrés au choix des cibles (BuddhaHits.canHit,
 *       IceFreeze.canHit, ConquerorHaki, attaques de feu, AceSlash, Kame, Bouddha). L'appartenance est relue à chaque coup :
 *       la protection commence et s'arrête immédiatement.</li>
 *   <li>Sauvegarde : toutes les Party sont gardées sur l'Overworld (ModAttachments.PARTIES), donc après un redémarrage. Les
 *       invitations, elles, ne sont pas sauvegardées (elles expirent vite).</li>
 * </ul>
 */
public final class Parties {

    /** Limite de membres d'une Party, chef compris. */
    public static final int MAX_MEMBERS = 5;
    /** Durée de validité d'une invitation (60 s). */
    public static final int INVITE_TTL = 20 * 60;
    /** Délai minimal entre deux invitations envoyées par un même chef (3 s). */
    public static final int INVITE_COOLDOWN = 20 * 3;
    /** Après un refus : délai avant que la même Party puisse réinviter ce joueur (30 s). */
    public static final int REFUSE_COOLDOWN = 20 * 30;
    /** Invitations en attente au plus, par joueur invité. */
    public static final int MAX_PENDING = 3;
    /** Délai minimal entre deux actions de Party d'un même joueur (anti-spam de clics / paquets, 0,25 s). */
    private static final int ACTION_COOLDOWN = 5;

    private static final Map<UUID, Party> PARTIES = new LinkedHashMap<>();
    private static final Map<UUID, UUID> PARTY_OF = new HashMap<>();
    /** Invitations en attente : joueur invité -> (Party -> invitation). */
    private static final Map<UUID, Map<UUID, Invite>> INVITES = new HashMap<>();
    private static final Map<UUID, Long> LAST_INVITE = new HashMap<>();
    private static final Map<String, Long> REFUSED = new HashMap<>();
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();
    private static MinecraftServer server;

    private record Invite(UUID party, String inviter, long expiresAt) {
    }

    private Parties() {
    }

    // ------------------------------------------------------------------ protection

    /** Les deux entités sont-elles deux joueurs DIFFÉRENTS de la même Party ? (null ou non-joueur : non) */
    public static boolean allies(Entity a, Entity b) {
        if (!(a instanceof Player) || !(b instanceof Player) || a == b) {
            return false;
        }
        return sameParty(a.getUUID(), b.getUUID());
    }

    /** Les deux joueurs (UUID) sont-ils dans la même Party ? */
    public static boolean sameParty(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        UUID pa = PARTY_OF.get(a);
        return pa != null && pa.equals(PARTY_OF.get(b));
    }

    public static Party partyOf(UUID player) {
        UUID id = PARTY_OF.get(player);
        return id == null ? null : PARTIES.get(id);
    }

    // ------------------------------------------------------------------ actions (toutes vérifiées ici)

    /** Une demande du client (bouton de la page Party). */
    public static void handle(ServerPlayer player, int action, String arg) {
        long now = now();
        Long last = LAST_ACTION.get(player.getUUID());
        if (last != null && now - last < ACTION_COOLDOWN) {
            return; // clics / paquets répétés : ignorés
        }
        LAST_ACTION.put(player.getUUID(), now);
        switch (action) {
            case PartyActionPayload.CREATE -> create(player);
            case PartyActionPayload.INVITE -> invite(player, onlinePlayer(arg));
            case PartyActionPayload.ACCEPT -> answer(player, parseUuid(arg), true);
            case PartyActionPayload.REFUSE -> answer(player, parseUuid(arg), false);
            case PartyActionPayload.LEAVE -> leave(player);
            case PartyActionPayload.KICK -> kick(player, parseUuid(arg));
            case PartyActionPayload.TRANSFER -> transfer(player, parseUuid(arg));
            case PartyActionPayload.DISBAND -> disband(player);
            default -> {
            }
        }
    }

    public static int create(ServerPlayer player) {
        if (PARTY_OF.containsKey(player.getUUID())) {
            tell(player, "already_in");
            return 0;
        }
        // nom : celui du fondateur, affiché « Party de <pseudo> » dans la langue de chaque joueur (displayName)
        Party party = new Party(UUID.randomUUID(), player.getName().getString(), player.getUUID(), player.getName().getString());
        PARTIES.put(party.id, party);
        PARTY_OF.put(player.getUUID(), party.id);
        INVITES.remove(player.getUUID()); // il a sa Party : ses invitations en attente n'ont plus lieu d'être
        player.sendSystemMessage(Component.translatable("message.bloxpiece.party.created", displayName(party))
                .withStyle(ChatFormatting.GOLD));
        changed();
        return 1;
    }

    public static int invite(ServerPlayer leader, ServerPlayer target) {
        Party party = partyOf(leader.getUUID());
        if (party == null) {
            tell(leader, "not_in");
            return 0;
        }
        if (!party.leader.equals(leader.getUUID())) {
            tell(leader, "not_leader");
            return 0;
        }
        if (target == null) {
            tell(leader, "invite_unknown");
            return 0;
        }
        if (target == leader || party.members.containsKey(target.getUUID())) {
            tell(leader, "invite_member");
            return 0;
        }
        if (PARTY_OF.containsKey(target.getUUID())) {
            leader.sendSystemMessage(Component.translatable("message.bloxpiece.party.invite_other", target.getDisplayName()), true);
            return 0;
        }
        if (party.size() >= MAX_MEMBERS) {
            leader.sendSystemMessage(Component.translatable("message.bloxpiece.party.full", MAX_MEMBERS), true);
            return 0;
        }
        long now = now();
        Long lastInvite = LAST_INVITE.get(leader.getUUID());
        if (lastInvite != null && now - lastInvite < INVITE_COOLDOWN) {
            tell(leader, "invite_wait");
            return 0;
        }
        Map<UUID, Invite> pending = INVITES.computeIfAbsent(target.getUUID(), k -> new LinkedHashMap<>());
        if (pending.containsKey(party.id)) {
            leader.sendSystemMessage(Component.translatable("message.bloxpiece.party.invite_pending", target.getDisplayName()), true);
            return 0;
        }
        Long refused = REFUSED.get(party.id + "|" + target.getUUID());
        if (refused != null && now < refused) {
            leader.sendSystemMessage(Component.translatable("message.bloxpiece.party.invite_refused_wait", target.getDisplayName()), true);
            return 0;
        }
        if (pending.size() >= MAX_PENDING) {
            leader.sendSystemMessage(Component.translatable("message.bloxpiece.party.invite_too_many", target.getDisplayName()), true);
            return 0;
        }
        LAST_INVITE.put(leader.getUUID(), now);
        pending.put(party.id, new Invite(party.id, leader.getName().getString(), now + INVITE_TTL));

        String id = party.id.toString();
        Component accept = Component.translatable("message.bloxpiece.party.button_accept").withStyle(s -> s
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/bloxfruit party accept " + id))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.bloxpiece.party.button_accept_hint"))));
        Component refuse = Component.translatable("message.bloxpiece.party.button_refuse").withStyle(s -> s
                .withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/bloxfruit party refuse " + id))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.bloxpiece.party.button_refuse_hint"))));
        target.sendSystemMessage(Component.translatable("message.bloxpiece.party.invite_received", leader.getDisplayName(),
                displayName(party), INVITE_TTL / 20).withStyle(ChatFormatting.GOLD).append(" ").append(accept).append(" ").append(refuse));
        target.level().playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.8F, 0.8F);
        leader.sendSystemMessage(Component.translatable("message.bloxpiece.party.invite_sent", target.getDisplayName()), true);
        sync(target);
        sync(leader);
        return 1;
    }

    /** Réponse à une invitation (bouton du chat, commande ou page Party). */
    public static int answer(ServerPlayer player, UUID partyId, boolean accept) {
        Map<UUID, Invite> pending = INVITES.get(player.getUUID());
        Invite invite = pending == null || partyId == null ? null : pending.get(partyId);
        if (invite == null || invite.expiresAt < now()) {
            if (pending != null && partyId != null) {
                pending.remove(partyId);
            }
            tell(player, "invite_expired");
            sync(player);
            return 0;
        }
        pending.remove(partyId);
        Party party = PARTIES.get(partyId);
        if (party == null) {
            tell(player, "invite_expired");
            sync(player);
            return 0;
        }
        if (!accept) {
            REFUSED.put(partyId + "|" + player.getUUID(), now() + REFUSE_COOLDOWN);
            tell(player, "refused_self");
            ServerPlayer leader = online(party.leader);
            if (leader != null) {
                leader.sendSystemMessage(Component.translatable("message.bloxpiece.party.refused", player.getDisplayName()), true);
            }
            sync(player);
            return 1;
        }
        if (PARTY_OF.containsKey(player.getUUID())) {
            tell(player, "already_in");
            return 0;
        }
        if (party.size() >= MAX_MEMBERS) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.party.full", MAX_MEMBERS), true);
            sync(player);
            return 0;
        }
        party.members.put(player.getUUID(), player.getName().getString());
        PARTY_OF.put(player.getUUID(), party.id);
        INVITES.remove(player.getUUID()); // toutes ses invitations sont consommées
        broadcast(party, Component.translatable("message.bloxpiece.party.joined", player.getDisplayName(), displayName(party))
                .withStyle(ChatFormatting.GREEN));
        changed();
        return 1;
    }

    public static int leave(ServerPlayer player) {
        Party party = partyOf(player.getUUID());
        if (party == null) {
            tell(player, "not_in");
            return 0;
        }
        removeMember(party, player.getUUID());
        player.sendSystemMessage(Component.translatable("message.bloxpiece.party.left_self", displayName(party)).withStyle(ChatFormatting.YELLOW));
        broadcast(party, Component.translatable("message.bloxpiece.party.left", player.getDisplayName()).withStyle(ChatFormatting.YELLOW));
        changed();
        return 1;
    }

    public static int kick(ServerPlayer leader, UUID target) {
        Party party = partyOf(leader.getUUID());
        if (party == null || !party.leader.equals(leader.getUUID())) {
            tell(leader, "not_leader");
            return 0;
        }
        if (target == null || target.equals(leader.getUUID()) || !party.members.containsKey(target)) {
            return 0;
        }
        String name = party.members.get(target);
        removeMember(party, target);
        ServerPlayer kicked = online(target);
        if (kicked != null) {
            kicked.sendSystemMessage(Component.translatable("message.bloxpiece.party.kicked_self", displayName(party)).withStyle(ChatFormatting.RED));
        }
        broadcast(party, Component.translatable("message.bloxpiece.party.kicked", name).withStyle(ChatFormatting.YELLOW));
        changed();
        return 1;
    }

    public static int transfer(ServerPlayer leader, UUID target) {
        Party party = partyOf(leader.getUUID());
        if (party == null || !party.leader.equals(leader.getUUID())) {
            tell(leader, "not_leader");
            return 0;
        }
        if (target == null || target.equals(leader.getUUID()) || !party.members.containsKey(target)) {
            return 0;
        }
        party.leader = target;
        broadcast(party, Component.translatable("message.bloxpiece.party.new_leader", party.members.get(target)).withStyle(ChatFormatting.GOLD));
        changed();
        return 1;
    }

    public static int disband(ServerPlayer leader) {
        Party party = partyOf(leader.getUUID());
        if (party == null || !party.leader.equals(leader.getUUID())) {
            tell(leader, "not_leader");
            return 0;
        }
        broadcast(party, Component.translatable("message.bloxpiece.party.disbanded", displayName(party)).withStyle(ChatFormatting.RED));
        dissolve(party);
        changed();
        return 1;
    }

    // ------------------------------------------------------------------ interne

    /** Retire un membre ; le chef qui part passe la main ; plus personne : la Party est dissoute. */
    private static void removeMember(Party party, UUID member) {
        party.members.remove(member);
        PARTY_OF.remove(member);
        if (party.members.isEmpty()) {
            dissolve(party);
            return;
        }
        if (party.leader.equals(member)) {
            party.leader = nextLeader(party);
            broadcast(party, Component.translatable("message.bloxpiece.party.new_leader", party.members.get(party.leader))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** Nouveau chef : le membre arrivé le plus tôt, connecté de préférence. */
    private static UUID nextLeader(Party party) {
        for (UUID uuid : party.members.keySet()) {
            if (!uuid.equals(party.leader) && online(uuid) != null) {
                return uuid;
            }
        }
        for (UUID uuid : party.members.keySet()) {
            if (!uuid.equals(party.leader)) {
                return uuid;
            }
        }
        return party.leader;
    }

    private static void dissolve(Party party) {
        for (UUID uuid : party.members.keySet()) {
            PARTY_OF.remove(uuid);
        }
        party.members.clear();
        PARTIES.remove(party.id);
        for (Map<UUID, Invite> pending : INVITES.values()) {
            pending.remove(party.id); // ses invitations en attente n'ont plus d'objet
        }
    }

    private static void broadcast(Party party, Component message) {
        for (UUID uuid : party.members.keySet()) {
            ServerPlayer p = online(uuid);
            if (p != null) {
                p.sendSystemMessage(message);
            }
        }
    }

    /** Nom affiché de la Party : « Party de &lt;fondateur&gt; », traduit chez chaque joueur. */
    private static Component displayName(Party party) {
        return Component.translatable("party.bloxpiece.default_name", party.name);
    }

    private static void tell(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable("message.bloxpiece.party." + key), true);
    }

    private static ServerPlayer online(UUID uuid) {
        return server == null || uuid == null ? null : server.getPlayerList().getPlayer(uuid);
    }

    private static ServerPlayer onlinePlayer(String uuid) {
        return online(parseUuid(uuid));
    }

    private static UUID parseUuid(String text) {
        try {
            return text == null || text.isEmpty() ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static long now() {
        return server == null ? 0L : server.overworld().getGameTime();
    }

    /** Quelque chose a changé : sauvegarde + tous les joueurs connectés reçoivent leur état à jour. */
    private static void changed() {
        save();
        syncAll();
    }

    // ------------------------------------------------------------------ synchronisation

    public static void syncAll() {
        if (server == null) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sync(p);
        }
    }

    public static void sync(ServerPlayer player) {
        if (server == null) {
            return;
        }
        Party party = partyOf(player.getUUID());
        List<PartySyncPayload.Member> members = new ArrayList<>();
        List<PartySyncPayload.Candidate> candidates = new ArrayList<>();
        List<PartySyncPayload.Invite> invites = new ArrayList<>();
        long now = now();
        if (party != null) {
            party.members.forEach((uuid, name) -> {
                ServerPlayer m = online(uuid);
                members.add(new PartySyncPayload.Member(uuid.toString(), m != null ? m.getName().getString() : name, m != null));
            });
            if (party.leader.equals(player.getUUID())) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (!PARTY_OF.containsKey(p.getUUID()) && candidates.size() < 200) {
                        candidates.add(new PartySyncPayload.Candidate(p.getUUID().toString(), p.getName().getString()));
                    }
                }
            }
        } else {
            Map<UUID, Invite> pending = INVITES.get(player.getUUID());
            if (pending != null) {
                for (Invite invite : pending.values()) {
                    Party p = PARTIES.get(invite.party);
                    if (p != null && invite.expiresAt > now) {
                        invites.add(new PartySyncPayload.Invite(p.id.toString(), p.name, invite.inviter,
                                (int) ((invite.expiresAt - now + 19) / 20)));
                    }
                }
            }
        }
        ServerPlayNetworking.send(player, new PartySyncPayload(party != null, party == null ? "" : party.id.toString(),
                party == null ? "" : party.name, party == null ? "" : party.leader.toString(), MAX_MEMBERS, members, candidates, invites));
    }

    // ------------------------------------------------------------------ sauvegarde

    private static void save() {
        if (server == null) {
            return;
        }
        List<Party.Saved> saved = new ArrayList<>();
        for (Party party : PARTIES.values()) {
            saved.add(party.save());
        }
        server.overworld().setAttached(ModAttachments.PARTIES, saved);
    }

    private static void load(MinecraftServer srv) {
        PARTIES.clear();
        PARTY_OF.clear();
        List<Party.Saved> saved = srv.overworld().getAttached(ModAttachments.PARTIES);
        if (saved == null) {
            return;
        }
        for (Party.Saved s : saved) {
            Party party = Party.load(s);
            if (party == null || PARTIES.containsKey(party.id)) {
                continue;
            }
            // un joueur dans plusieurs Party (sauvegarde abîmée) : il reste dans la première seulement
            party.members.keySet().removeIf(PARTY_OF::containsKey);
            if (party.members.isEmpty()) {
                continue;
            }
            if (!party.members.containsKey(party.leader)) {
                party.leader = party.members.keySet().iterator().next();
            }
            PARTIES.put(party.id, party);
            for (UUID uuid : party.members.keySet()) {
                PARTY_OF.put(uuid, party.id);
            }
        }
    }

    // ------------------------------------------------------------------ événements

    public static void initialize() {
        // Protection entre membres : TOUT dégât dont l'auteur (tireur, lanceur) est un membre de la même Party est refusé.
        // Enregistré AVANT les autres vérifications de dégâts (esquive du Haki de l'Observation...) : rien ne se déclenche.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !allies(entity, source.getEntity()));

        ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
            server = srv;
            load(srv);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
            save();
            PARTIES.clear();
            PARTY_OF.clear();
            INVITES.clear();
            LAST_INVITE.clear();
            REFUSED.clear();
            LAST_ACTION.clear();
            server = null;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            server = srv;
            ServerPlayer player = handler.getPlayer();
            Party party = partyOf(player.getUUID());
            if (party != null) {
                party.members.put(player.getUUID(), player.getName().getString()); // pseudo à jour
                broadcast(party, Component.translatable("message.bloxpiece.party.online", player.getDisplayName())
                        .withStyle(ChatFormatting.GRAY));
                save();
            }
            syncAll(); // statut « connecté » des membres, liste des joueurs invitables des chefs
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
            ServerPlayer player = handler.getPlayer();
            INVITES.remove(player.getUUID());                    // ses invitations reçues tombent
            LAST_ACTION.remove(player.getUUID());
            Party party = partyOf(player.getUUID());
            if (party != null && party.leader.equals(player.getUUID())) {
                // chef déconnecté : un membre connecté prend le relais (la Party n'est jamais bloquée)
                for (UUID uuid : party.members.keySet()) {
                    ServerPlayer m = online(uuid);
                    if (m != null && m != player) {
                        party.leader = uuid;
                        broadcast(party, Component.translatable("message.bloxpiece.party.new_leader", m.getDisplayName())
                                .withStyle(ChatFormatting.GOLD));
                        break;
                    }
                }
            }
            if (party != null) {
                broadcast(party, Component.translatable("message.bloxpiece.party.offline", player.getDisplayName())
                        .withStyle(ChatFormatting.GRAY));
                save();
            }
            srv.execute(Parties::syncAll); // après son départ de la liste des joueurs connectés
        });
        // invitations expirées : retirées chaque seconde, l'invité voit sa page se mettre à jour
        ServerTickEvents.END_SERVER_TICK.register(srv -> {
            if (srv.getTickCount() % 20 != 0 || INVITES.isEmpty()) {
                return;
            }
            long now = now();
            Iterator<Map.Entry<UUID, Map<UUID, Invite>>> it = INVITES.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Map<UUID, Invite>> entry = it.next();
                boolean removed = entry.getValue().values().removeIf(i -> i.expiresAt <= now || !PARTIES.containsKey(i.party));
                if (entry.getValue().isEmpty()) {
                    it.remove();
                }
                ServerPlayer p = online(entry.getKey());
                if (p != null) {
                    if (removed) {
                        tell(p, "invite_expired");
                    }
                    sync(p); // compte à rebours de la page Party
                }
            }
            REFUSED.values().removeIf(until -> until <= now);
        });
    }
}
