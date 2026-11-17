package nolan.bloxpiece.registry;

import nolan.bloxpiece.spawn.GroupSpawnRule;
import nolan.bloxpiece.spawn.GroupSpawner;

/**
 * Les règles d'apparition naturelle des mobs du mod. Un nouveau mob = un nouveau bloc register(...) ici,
 * le système (GroupSpawner) est déjà là pour tous.
 *
 * Toutes les durées sont en ticks : utilise seconds(...) et minutes(...) pour rester lisible.
 */
public class ModSpawns {

    public static void initialize() {
        // PIRATES : groupes de 2 à 8, entre 40 et 100 blocs du joueur. Un mélange de Sabreurs (65 %) et de Tireurs (35 %)
        GroupSpawner.register(new GroupSpawnRule(ModEntities.PIRATE_SWORDSMAN)
                .mixWith(ModEntities.PIRATE_GUNNER, 0.35)
                .groupSize(2, 8)
                .groupSpread(6)
                .distance(40, 100)
                .interval(seconds(45), minutes(2))     // avant : 2 à 5 minutes
                .globalGap(seconds(10))                // avant : 15 secondes
                .retryDelay(seconds(5))                // avant : 10 secondes
                .populationLimits(12, 60)
                .populationRadius(100)
                .minGroupSeparation(64)                // avant : 80
                .debug(true));                         // mets false quand l'équilibrage te convient

        GroupSpawner.initialize();
    }

    private static int seconds(int seconds) {
        return seconds * 20;
    }

    private static int minutes(int minutes) {
        return minutes * 60 * 20;
    }
}
