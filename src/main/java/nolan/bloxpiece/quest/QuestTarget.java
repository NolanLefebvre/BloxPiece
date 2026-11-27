package nolan.bloxpiece.quest;

import java.util.List;

import net.minecraft.world.entity.EntityType;

import nolan.bloxpiece.progression.MobXp;
import nolan.bloxpiece.registry.ModEntities;

/**
 * Les ennemis que demandent les quêtes répétables du Tavernier : chaque cible regroupe des types d'entités RÉELLEMENT
 * enregistrés par le mod (ModEntities). Une cible large (« Pirates ») ne contient que les mobs ordinaires de la faction : un
 * Capitaine Pirate ne compte jamais comme un pirate ordinaire, un Chef des bandits jamais comme un bandit. Les quêtes visent
 * toujours la faction entière (« Pirates », « Soldats de la Marine »), jamais une seule variante (sabreurs, tireurs...).
 */
public enum QuestTarget {

    // Pirates
    PIRATES("pirates", Faction.PIRATES, ModEntities.PIRATE_SWORDSMAN, ModEntities.PIRATE_GUNNER),
    PIRATE_CAPTAINS("pirate_captains", Faction.PIRATES, ModEntities.PIRATE_CAPTAIN),
    // Bandits
    BANDITS("bandits", Faction.BANDITS, ModEntities.BANDIT),
    BANDIT_LEADERS("bandit_leaders", Faction.BANDITS, ModEntities.BANDIT_CHIEF),
    // Marine
    MARINES("marines", Faction.MARINES, ModEntities.MARINE_SWORDSMAN, ModEntities.MARINE_RIFLEMAN),
    MORGAN("morgan", Faction.MARINES, ModEntities.MORGAN);

    public enum Faction {
        PIRATES, BANDITS, MARINES
    }

    public final String id;
    public final Faction faction;
    public final List<EntityType<?>> types;

    QuestTarget(String id, Faction faction, EntityType<?>... types) {
        this.id = id;
        this.faction = faction;
        this.types = List.of(types);
    }

    public boolean matches(EntityType<?> type) {
        return this.types.contains(type);
    }

    /** XP normale moyenne d'un ennemi de cette cible (MobXp) : sert à calculer la récompense de la quête. */
    public double averageXp() {
        double sum = 0.0;
        for (EntityType<?> type : this.types) {
            sum += MobXp.xpFor(type);
        }
        return this.types.isEmpty() ? 0.0 : sum / this.types.size();
    }

    /** Nom (pluriel) de la cible dans les menus : « quest.bloxpiece.target.&lt;id&gt; ». */
    public String nameKey() {
        return "quest.bloxpiece.target." + this.id;
    }

    public static QuestTarget byId(String id) {
        // anciennes cibles (variantes retirées) : une mission déjà sauvegardée vise désormais toute la faction
        if ("pirate_swordsmen".equals(id) || "pirate_gunners".equals(id)) {
            return PIRATES;
        }
        if ("marine_swordsmen".equals(id) || "marine_riflemen".equals(id)) {
            return MARINES;
        }
        for (QuestTarget target : values()) {
            if (target.id.equals(id)) {
                return target;
            }
        }
        return null;
    }
}
