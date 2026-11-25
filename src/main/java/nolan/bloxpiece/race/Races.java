package nolan.bloxpiece.race;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.progression.Progression;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * TOUTES les races du mod et leurs données : chances d'obtention (20 % chacune : toutes égales), bonus passifs, attaques et niveaux de déblocage. C'est le seul
 * fichier à modifier pour équilibrer (valeurs des bonus, chances, niveaux) ou pour ajouter une race / une attaque ; le reste du
 * système (RaceManager côté serveur, menu Race côté client) lit ces données.
 * <p>
 * Bonus volontairement FAIBLES : l'identité de chaque race viendra surtout de ses attaques (à venir).
 */
public final class Races {

    private static final Map<String, Race> REGISTRY = new LinkedHashMap<>();

    /** Niveaux de déblocage des attaques raciales (1re, 2e, 3e, 4e, ultime) : modifiables ici pour toutes les races. */
    public static final int[] ATTACK_LEVELS = {10, 25, 40, 60, 100};

    // ------------------------------------------------------------------ les races

    /** Humain : équilibré, juste un demi-cœur de plus. */
    public static final Race HUMAN = register(new Race("human", 0xFFE8C9A0, 20,
            List.of(RaceBonus.attribute("max_health", Attributes.MAX_HEALTH, Operation.ADD_VALUE, 1.0)),
            // attaques DÉVELOPPÉES (ability/Human*) : force physique et arts martiaux, niveaux 10 / 20 / 30 / 50
            List.of(RaceAttack.of("human_lightning_impact", 10, new nolan.bloxpiece.ability.HumanLightningImpactAbility(10)),
                    RaceAttack.of("human_destructive_rush", 20, new nolan.bloxpiece.ability.HumanDestructiveRushAbility(20)),
                    RaceAttack.of("human_earth_breaker", 30, new nolan.bloxpiece.ability.HumanEarthBreakerAbility(30)),
                    RaceAttack.of("human_conqueror_fist", 50, new nolan.bloxpiece.ability.HumanConquerorFistAbility(50)))));

    /** Fishman : un peu plus fort au corps-à-corps, rapide dans l'eau, respire sous l'eau. */
    public static final Race FISHMAN = register(new Race("fishman", 0xFF4FA8E0, 20,
            List.of(RaceBonus.attribute("melee_damage", Attributes.ATTACK_DAMAGE, Operation.ADD_MULTIPLIED_TOTAL, 0.05),
                    RaceBonus.attribute("knockback", Attributes.ATTACK_KNOCKBACK, Operation.ADD_VALUE, 0.2),
                    RaceBonus.attribute("swim_speed", Attributes.WATER_MOVEMENT_EFFICIENCY, Operation.ADD_VALUE, 0.33, 100.0),
                    RaceBonus.special(RaceBonus.Kind.WATER_BREATHING, "water_breathing", 0.0)),
            attacks("fishman_karate_punch", "fishman_water_shot", "fishman_shock_wave", "fishman_shark_roar", "fishman_great_tide")));

    /** Lunarian : un peu plus résistant, brûle moins longtemps, mieux protégé quand ses flammes sont actives. */
    public static final Race LUNARIAN = register(new Race("lunarian", 0xFFFF7A3C, 20,
            List.of(RaceBonus.attribute("armor", Attributes.ARMOR, Operation.ADD_VALUE, 2.0),
                    RaceBonus.attribute("burn_time", Attributes.BURNING_TIME, Operation.ADD_MULTIPLIED_TOTAL, -0.5),
                    RaceBonus.special(RaceBonus.Kind.FLAME_GUARD, "flame_guard", 2.0)),
            attacks("lunarian_ember_breath", "lunarian_cinder_wings", "lunarian_flame_shell", "lunarian_blazing_rain",
                    "lunarian_celestial_pyre")));

    /** Cyborg : un cœur de plus, un peu moins repoussé, poison et faim s'estompent plus vite. */
    public static final Race CYBORG = register(new Race("cyborg", 0xFF9AB4C8, 20,
            List.of(RaceBonus.attribute("max_health", Attributes.MAX_HEALTH, Operation.ADD_VALUE, 2.0),
                    RaceBonus.attribute("knockback_resistance", Attributes.KNOCKBACK_RESISTANCE, Operation.ADD_VALUE, 0.1, 100.0),
                    RaceBonus.special(RaceBonus.Kind.EFFECT_RESIST, "effect_resist", 2.0)),
            // attaques DÉVELOPPÉES (ability/cyborg) : niveaux propres au Cyborg, 10 / 20 / 30 / 50
            List.of(RaceAttack.of("cyborg_cannon_arm", 10, new nolan.bloxpiece.ability.CyborgCannonArmAbility(10)),
                    RaceAttack.of("cyborg_energy_shield", 20, new nolan.bloxpiece.ability.CyborgEnergyShieldAbility(20)),
                    RaceAttack.of("cyborg_engine_overdrive", 30, new nolan.bloxpiece.ability.CyborgOverdriveAbility(30)),
                    RaceAttack.of("cyborg_radical_beam", 50, new nolan.bloxpiece.ability.CyborgRadicalBeamAbility(50)))));

    /** Minks : un peu plus rapide, frappe un peu plus vite, tombe de plus haut sans se blesser. */
    public static final Race MINKS = register(new Race("minks", 0xFFE8D25A, 20,
            List.of(RaceBonus.attribute("move_speed", Attributes.MOVEMENT_SPEED, Operation.ADD_MULTIPLIED_BASE, 0.05),
                    RaceBonus.attribute("attack_speed", Attributes.ATTACK_SPEED, Operation.ADD_MULTIPLIED_TOTAL, 0.05),
                    RaceBonus.attribute("safe_fall", Attributes.SAFE_FALL_DISTANCE, Operation.ADD_VALUE, 1.0)),
            // attaques DÉVELOPPÉES (ability/Minks*) : électricité, vitesse, griffes, Sulong ; niveaux 10 / 20 / 30 / 50
            List.of(RaceAttack.of("minks_electric_claws", 10, new nolan.bloxpiece.ability.MinksClawsAbility(10)),
                    RaceAttack.of("minks_claw_barrage", 20, new nolan.bloxpiece.ability.MinksBarrageAbility(20)),
                    RaceAttack.of("minks_lightning_dance", 30, new nolan.bloxpiece.ability.MinksDanceAbility(30)),
                    RaceAttack.of("minks_sulong", 50, new nolan.bloxpiece.ability.MinksSulongAbility(50)))));

    /** Race affichée tant qu'un joueur n'a pas encore reçu la sienne (elle est tirée à sa première connexion). */
    public static final Race DEFAULT = HUMAN;

    private Races() {
    }

    /** Les 5 attaques d'une race, aux niveaux ATTACK_LEVELS, toutes « à venir » (pas encore développées). */
    private static List<RaceAttack> attacks(String... ids) {
        List<RaceAttack> list = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            list.add(RaceAttack.planned(ids[i], ATTACK_LEVELS[Math.min(i, ATTACK_LEVELS.length - 1)]));
        }
        return Collections.unmodifiableList(list);
    }

    private static Race register(Race race) {
        REGISTRY.put(race.id(), race);
        return race;
    }

    /** Renvoie la race correspondant à cet id, ou null si elle n'existe pas. */
    public static Race get(String id) {
        return id == null ? null : REGISTRY.get(id);
    }

    public static List<Race> all() {
        return List.copyOf(REGISTRY.values());
    }

    /** Race actuelle d'un joueur (la race par défaut si aucune n'est enregistrée). Utilisable côté client et serveur. */
    public static Race of(Player player) {
        Race race = get(player.getAttached(ModAttachments.RACE));
        return race != null ? race : DEFAULT;
    }

    /** Le joueur a-t-il déjà une race enregistrée ? */
    public static boolean hasRace(Player player) {
        return get(player.getAttached(ModAttachments.RACE)) != null;
    }

    // ------------------------------------------------------------------ tirage

    /** Les chances sont-elles valides (toutes positives, total = 100 %) ? */
    public static boolean weightsValid() {
        int total = 0;
        for (Race race : REGISTRY.values()) {
            if (race.weight() < 0) {
                return false;
            }
            total += race.weight();
        }
        return total == 100;
    }

    /**
     * Tire une race selon les chances. {@code exclude} (peut être null) : race à ne pas retirer (reroll : on change forcément de
     * race ; les autres gardent leurs proportions). Chances invalides : erreur dans le journal et tirage équitable (jamais de
     * plantage, jamais de joueur sans race).
     */
    public static Race roll(RandomSource random, Race exclude) {
        boolean valid = weightsValid();
        if (!valid) {
            BloxPiece.LOGGER.error("[BloxPiece] Chances des races invalides (total différent de 100 %) : tirage équitable.");
        }
        List<Race> pool = new ArrayList<>();
        int total = 0;
        for (Race race : REGISTRY.values()) {
            if (race == exclude) {
                continue;
            }
            int w = valid ? race.weight() : 1;
            if (w > 0) {
                pool.add(race);
                total += w;
            }
        }
        if (pool.isEmpty() || total <= 0) {
            return exclude != null ? exclude : DEFAULT;
        }
        int pick = random.nextInt(total);
        for (Race race : pool) {
            pick -= valid ? race.weight() : 1;
            if (pick < 0) {
                return race;
            }
        }
        return pool.get(pool.size() - 1);
    }

    // ------------------------------------------------------------------ attaques (pour les futures attaques raciales)

    /**
     * Un joueur peut-il utiliser cette attaque raciale ? À vérifier CÔTÉ SERVEUR avant toute attaque raciale : elle appartient à
     * SA race, elle est développée (pas « À venir »), et son niveau BloxPiece (Progression) atteint le niveau requis.
     */
    public static boolean canUse(ServerPlayer player, RaceAttack attack) {
        return attack != null && attack.implemented() && of(player).attacks().contains(attack)
                && Progression.get(player).level() >= attack.requiredLevel();
    }
}
