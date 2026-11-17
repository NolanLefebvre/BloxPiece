package nolan.bloxpiece.spawn;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.mob.BloxMob;

/**
 * Les règles d'apparition naturelle d'un mob du mod, sous forme de groupes.
 * Tous les réglages d'équilibrage sont ici (valeurs par défaut) et se changent avec les méthodes
 * ci-dessous quand la règle est déclarée (voir ModSpawns).
 *
 * Les durées sont en ticks (20 ticks = 1 seconde), les distances en blocs.
 */
public final class GroupSpawnRule {

    final EntityType<? extends BloxMob> type;

    /** Variante mélangée au groupe (facultative) et sa proportion (0 à 1), ex : 35 % de tireurs parmi les sabreurs. */
    EntityType<? extends BloxMob> mixType;
    double mixChance;

    // --- Taille des groupes ---
    int minGroup = 2;
    int maxGroup = 8;
    /** Rayon (blocs) dans lequel les membres d'un groupe sont répartis autour du point d'apparition. */
    double groupSpread = 6.0;

    // --- Où : distance au joueur ---
    int minDistance = 40;
    int maxDistance = 100;
    /** Écart de hauteur maximal avec le joueur (évite de faire apparaître des mobs au-dessus d'une grotte, par exemple). */
    int maxVerticalDelta = 24;
    /** En dessous de cette distance, un groupe ne peut pas apparaître s'il est dans le champ de vision du joueur. */
    int viewCheckDistance = 80;
    ResourceKey<Level> dimension = Level.OVERWORLD;

    // --- Fréquence ---
    /** Délai aléatoire entre deux groupes pour un même joueur. */
    int minInterval = 2 * 60 * 20;
    int maxInterval = 5 * 60 * 20;
    /** Délai avant de réessayer quand une tentative échoue (pas de place, trop de pirates, etc.). */
    int retryTicks = 10 * 20;
    /** Délai minimal entre deux groupes sur tout le serveur : évite les apparitions simultanées en multijoueur. */
    int globalGapTicks = 15 * 20;

    // --- Limites de population ---
    /** Zone autour du joueur dans laquelle on compte les mobs déjà présents. */
    int populationRadius = 100;
    int maxNearPlayer = 12;
    /** Limite globale par dimension (protège les performances). */
    int maxPerLevel = 60;
    /** Distance minimale entre deux groupes récents (deux joueurs proches ne créent pas deux groupes au même endroit). */
    int minGroupSeparation = 80;

    // --- Tentatives ---
    int anchorAttempts = 10;
    int memberAttempts = 8;

    boolean debug = false;

    public GroupSpawnRule(EntityType<? extends BloxMob> type) {
        this.type = type;
    }

    /** Nombre de mobs par groupe (tirage aléatoire entre min et max inclus). */
    /** Mélange une autre variante dans les groupes : chaque membre a {@code chance} (0 à 1) d'être de ce type. */
    public GroupSpawnRule mixWith(EntityType<? extends BloxMob> other, double chance) {
        this.mixType = other;
        this.mixChance = chance;
        return this;
    }

    /** Le type d'un nouveau membre du groupe (le type principal, ou la variante mélangée). */
    EntityType<? extends BloxMob> pickType(net.minecraft.util.RandomSource random) {
        return this.mixType != null && random.nextDouble() < this.mixChance ? this.mixType : this.type;
    }

    /** Ce mob compte-t-il dans la population de cette règle (type principal ou variante) ? */
    boolean matches(EntityType<?> other) {
        return other == this.type || other == this.mixType;
    }

    public GroupSpawnRule groupSize(int min, int max) {
        this.minGroup = Math.max(1, min);
        this.maxGroup = Math.max(this.minGroup, max);
        return this;
    }

    /** Rayon de dispersion des membres d'un même groupe. */
    public GroupSpawnRule groupSpread(double blocks) {
        this.groupSpread = Math.max(2.0, blocks);
        return this;
    }

    /** Distance minimale et maximale entre le joueur et le point d'apparition du groupe. */
    public GroupSpawnRule distance(int min, int max) {
        this.minDistance = Math.max(8, min);
        this.maxDistance = Math.max(this.minDistance + 1, max);
        return this;
    }

    public GroupSpawnRule maxVerticalDelta(int blocks) {
        this.maxVerticalDelta = Math.max(1, blocks);
        return this;
    }

    public GroupSpawnRule dimension(ResourceKey<Level> dimension) {
        this.dimension = dimension;
        return this;
    }

    /** Délai aléatoire (ticks) entre deux groupes pour un même joueur. */
    public GroupSpawnRule interval(int minTicks, int maxTicks) {
        this.minInterval = Math.max(20, minTicks);
        this.maxInterval = Math.max(this.minInterval, maxTicks);
        return this;
    }

    public GroupSpawnRule retryDelay(int ticks) {
        this.retryTicks = Math.max(20, ticks);
        return this;
    }

    /** Délai minimal (ticks) entre deux groupes, tous joueurs confondus. */
    public GroupSpawnRule globalGap(int ticks) {
        this.globalGapTicks = Math.max(0, ticks);
        return this;
    }

    /** Nombre maximal de mobs de ce type autour d'un joueur (dans le rayon de population), et au total dans la dimension. */
    public GroupSpawnRule populationLimits(int maxNearPlayer, int maxPerLevel) {
        this.maxNearPlayer = Math.max(1, maxNearPlayer);
        this.maxPerLevel = Math.max(1, maxPerLevel);
        return this;
    }

    public GroupSpawnRule populationRadius(int blocks) {
        this.populationRadius = Mth.clamp(blocks, 16, 256);
        return this;
    }

    public GroupSpawnRule minGroupSeparation(int blocks) {
        this.minGroupSeparation = Math.max(0, blocks);
        return this;
    }

    /** Affiche une ligne dans les logs à chaque groupe créé (pratique pour tester l'équilibrage). */
    public GroupSpawnRule debug(boolean debug) {
        this.debug = debug;
        return this;
    }
}
