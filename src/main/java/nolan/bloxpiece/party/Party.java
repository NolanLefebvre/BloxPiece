package nolan.bloxpiece.party;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Une Party (groupe de joueurs) : identifiant unique, nom (pseudo du fondateur : affiché « Party de &lt;pseudo&gt; »), chef, membres dans l'ordre d'arrivée (le chef d'abord). Le dernier
 * pseudo connu de chaque membre est gardé pour l'afficher même quand il est déconnecté. Modifiée seulement par Parties (serveur).
 */
public final class Party {

    final UUID id;
    String name;
    UUID leader;
    /** Membres -> dernier pseudo connu, dans l'ordre d'arrivée (sert à choisir le nouveau chef). */
    final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();

    Party(UUID id, String name, UUID leader, String leaderName) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.members.put(leader, leaderName);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID leader() {
        return leader;
    }

    public Map<UUID, String> members() {
        return java.util.Collections.unmodifiableMap(members);
    }

    public int size() {
        return members.size();
    }

    // ------------------------------------------------------------------ sauvegarde (attachment de l'Overworld)

    /** Forme sauvegardée d'une Party (identifiants en texte). */
    public record Saved(String id, String name, String leader, List<String> members, List<String> names) {
        public static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Saved::id),
                Codec.STRING.fieldOf("name").forGetter(Saved::name),
                Codec.STRING.fieldOf("leader").forGetter(Saved::leader),
                Codec.STRING.listOf().fieldOf("members").forGetter(Saved::members),
                Codec.STRING.listOf().fieldOf("names").forGetter(Saved::names)
        ).apply(instance, Saved::new));
    }

    /** Toutes les Party du monde, telles que sauvegardées (ModAttachments.PARTIES). */
    public static final Codec<List<Saved>> SAVE_CODEC = Saved.CODEC.listOf();

    Saved save() {
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        members.forEach((uuid, n) -> {
            ids.add(uuid.toString());
            names.add(n);
        });
        return new Saved(id.toString(), name, leader.toString(), ids, names);
    }

    /** Relit une Party sauvegardée ; null si elle est invalide (vide, chef absent...). */
    static Party load(Saved saved) {
        try {
            if (saved.members().isEmpty() || saved.members().size() != saved.names().size()) {
                return null;
            }
            UUID leader = UUID.fromString(saved.leader());
            int li = saved.members().indexOf(saved.leader());
            if (li < 0) {
                return null;
            }
            Party party = new Party(UUID.fromString(saved.id()), saved.name(), leader, saved.names().get(li));
            for (int i = 0; i < saved.members().size(); i++) {
                party.members.putIfAbsent(UUID.fromString(saved.members().get(i)), saved.names().get(i));
            }
            return party;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
