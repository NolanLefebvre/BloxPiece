package nolan.bloxpiece.ability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.IceEffects;
import nolan.bloxpiece.fx.IceSpike;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Quatrième attaque (ultime) du fruit de la Glace : Ice Age, l'Ère glaciaire.
 *
 * 1. Charge, comme la Déflagration du Bouddha : le joueur pose la main au sol (pose synchronisée 3, voir PlayerModelMixin), le
 *    froid converge vers sa main et un cercle de givre au sol montre la portée de la future vague. Appui court : la vague part après
 *    {@link #MIN_CHARGE_TICKS} ticks à x{@link #MIN_POWER} de sa taille d'origine. Touche maintenue : la charge monte jusqu'à x{@link #MAX_POWER}
 *    ({@link #MAX_CHARGE_TICKS} ticks, départ automatique) ; rayon, hauteur de la zone touchée, taille des pics, dégâts et durée
 *    du gel sont multipliés par la puissance. La recharge démarre au départ de la vague (cooldownOnRelease).
 * 2. Une vague de froid extrême part à 360° jusqu'à {@link #RADIUS} blocs (x la puissance) : mur de blizzard et
 *    brouillard glacé au front de la vague, sol et eau changés en glace (temporairement, IceTerrain), pics de glace 3D qui
 *    jaillissent du sol (IceSpike). Chaque entité atteinte (sauf le lanceur) subit des dégâts massifs UNE fois et est emprisonnée
 *    dans une statue de glace qui l'immobilise complètement (IceFreeze).
 */
public class IceAgeAbility implements Ability {

    private static final int MIN_CHARGE_TICKS = 14;
    private static final int MAX_CHARGE_TICKS = 60;
    private static final double MIN_POWER = 0.5; // appui court (x la taille d'origine)
    private static final double MAX_POWER = 1.5; // pleine charge
    /** Givre en hauteur sur les blocs de la zone (murs, arbres) : 10 blocs à l'appui court, 15 à pleine charge. */
    private static final int MIN_FROST_HEIGHT = 10;
    private static final int MAX_FROST_HEIGHT = 15;
    private static final double RADIUS = 28.0;
    private static final int WAVE_TICKS = 28;
    private static final float NEAR_DAMAGE = 36.0F;
    private static final float FAR_DAMAGE = 20.0F;
    private static final int FREEZE_TICKS = 100;
    private static final int TERRAIN_TICKS = 20 * 25;
    /** Probabilité qu'un pic de glace jaillisse d'une colonne gelée (environ 70 pics en tout). */
    private static final double SPIKE_CHANCE = 0.03;
    /** Les pics restent plantés 20 à 26 secondes (chacun un peu différemment, pour une fonte naturelle). */
    private static final int SPIKE_LIFE_TICKS = 20 * 20;
    /** Hauteur d'un pic en blocs par unité de taille (1 = ~2,5 blocs) : l'air libre exigé au-dessus du sol. */
    private static final float SPIKE_BLOCKS_PER_SIZE = 2.6F;

    private static final class Wave {
        final ServerPlayer owner;
        final ServerLevel level;
        Vec3 center;
        final Set<Integer> hit = new HashSet<>();
        final Ability ability;
        int charge;
        int age;
        boolean released;
        boolean started; // la vague est partie (fin de la charge)
        double power = 1.0;

        /** Puissance : MIN_POWER à la charge minimale, MAX_POWER à pleine charge. */
        double power() {
            double t = (double) (this.charge - MIN_CHARGE_TICKS) / (MAX_CHARGE_TICKS - MIN_CHARGE_TICKS);
            return MIN_POWER + (MAX_POWER - MIN_POWER) * Math.max(0.0, Math.min(1.0, t));
        }

        double radius() {
            return RADIUS * this.power;
        }

        /** Hauteur (blocs) jusqu'où les blocs au-dessus du sol sont givrés : MIN_FROST_HEIGHT à MAX_FROST_HEIGHT selon la charge. */
        int frostHeight() {
            double over = (this.power - MIN_POWER) / (MAX_POWER - MIN_POWER);
            return MIN_FROST_HEIGHT + (int) Math.round((MAX_FROST_HEIGHT - MIN_FROST_HEIGHT) * over);
        }

        /** La vague garde la même vitesse : plus grande, elle dure plus longtemps. */
        int waveTicks() {
            return (int) Math.ceil(WAVE_TICKS * this.power);
        }

        Wave(ServerPlayer owner, Ability ability) {
            this.ability = ability;
            this.owner = owner;
            this.level = (ServerLevel) owner.level();
            this.center = owner.position();
        }
    }

    private static final List<Wave> WAVES = new ArrayList<>();

    @Override
    public String id() {
        return "ice_age";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60; // 60 secondes : l'ultime
    }

    @Override
    public int requiredLevel() {
        return 8;
    }

    @Override
    public boolean cooldownOnRelease() {
        return true;
    }

    /** Une seule Ère glaciaire en charge à la fois par joueur. */
    @Override
    public boolean canUse(ServerPlayer player) {
        return charging(player) == null;
    }

    @Override
    public void release(ServerPlayer player) {
        Wave w = charging(player);
        if (w != null) {
            w.released = true;
        }
    }

    private static Wave charging(ServerPlayer player) {
        for (Wave w : WAVES) {
            if (w.owner == player && w.age == 0 && !w.started) {
                return w;
            }
        }
        return null;
    }

    @Override
    public void use(ServerPlayer player) {
        WAVES.add(new Wave(player, this));
        player.setAttached(ModAttachments.BUDDHA_POSE, 3); // main posée au sol (pose partagée avec les attaques du Bouddha)
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 3.0F, 0.4F);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Wave> it = WAVES.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            ServerPlayer player = w.owner;
            if (!w.started) {
                if (player.isRemoved() || !player.isAlive() || player.isInWater() || player.level() != w.level) {
                    player.removeAttached(ModAttachments.BUDDHA_POSE); // charge annulée : pas de recharge consommée
                    it.remove();
                    continue;
                }
                w.charge++;
                w.center = player.position();
                w.power = w.power();
                double progress = Math.min(1.0, (double) w.charge / MIN_CHARGE_TICKS);
                IceEffects.iceAgeCharge(w.level, hand(player), progress * w.power);
                IceEffects.iceAgeChargeRing(w.level, w.center, w.radius() * progress, w.charge);
                float over = (float) ((w.power - MIN_POWER) / (MAX_POWER - MIN_POWER)); // 0 = appui court, 1 = pleine charge
                if (over > 0.0F && w.charge % 10 == 0) {
                    // Surcharge : le sol tremble de plus en plus, le froid craque
                    BuddhaHits.shake(w.level, w.center, 30.0 * w.power, 0.4F + 0.8F * over, 10);
                    w.level.playSound(null, w.center.x, w.center.y, w.center.z, SoundEvents.AMETHYST_BLOCK_RESONATE,
                            SoundSource.PLAYERS, 2.5F, 0.4F + 0.5F * over);
                }
                if (w.charge >= MIN_CHARGE_TICKS && (w.released || w.charge >= MAX_CHARGE_TICKS)) {
                    w.started = true;
                    player.removeAttached(ModAttachments.BUDDHA_POSE);
                    AbilityManager.startCooldown(player, w.ability); // la recharge démarre au départ de la vague
                    BuddhaHits.shake(w.level, w.center, w.radius() * 2.0, 3.0F * (float) Math.sqrt(w.power), 30);
                    w.level.playSound(null, w.center.x, w.center.y, w.center.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 4.0F, 0.4F);
                    w.level.playSound(null, w.center.x, w.center.y, w.center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 1.4F);
                }
                continue;
            }
            advance(w);
            if (++w.age >= w.waveTicks()) {
                it.remove();
            }
        }
    }

    /** La main du joueur posée au sol. */
    private static Vec3 hand(ServerPlayer player) {
        double yaw = Math.toRadians(player.yBodyRot);
        return player.position().add(-Math.sin(yaw) * 0.6, 0.1, Math.cos(yaw) * 0.6);
    }

    /** Une étape de la vague : gèle l'anneau de terrain atteint, fait jaillir des pics, frappe et emprisonne les entités. */
    private static void advance(Wave w) {
        ServerLevel level = w.level;
        double radius = w.radius();
        double inner = radius * w.age / w.waveTicks();
        double outer = radius * (w.age + 1) / w.waveTicks();
        Vec3 c = w.center;
        int cy = Mth.floor(c.y) - 1;
        RandomSource random = level.getRandom();

        // Terrain de l'anneau [inner, outer[ : glace, et quelques pics de glace
        int r = (int) Math.ceil(outer);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < inner || d >= outer) {
                    continue;
                }
                int x = Mth.floor(c.x) + dx, z = Mth.floor(c.z) + dz;
                // Le sol de cette colonne, au-dessus OU en dessous du joueur : la vague suit collines, creux et falaises
                int gy = IceTerrain.findSurface(level, x, cy, z, searchRange(d));
                if (gy == IceTerrain.NO_SURFACE) {
                    continue;
                }
                BlockPos ground = new BlockPos(x, gy, z);
                boolean iced = IceTerrain.freezeGround(level, ground, TERRAIN_TICKS);
                // Murs, arbres, constructions : givrés en hauteur, de 10 blocs (appui court) à 15 blocs (pleine charge)
                IceTerrain.freezeAbove(level, ground, w.frostHeight(), TERRAIN_TICKS);
                // Plus de surface en charge : chance réduite (le nombre de pics grandit comme la puissance, pas comme l'aire)
                if (iced && d > 2.5 && random.nextDouble() < SPIKE_CHANCE / w.power) {
                    float size = randomSpikeSize(random) * (float) w.power; // taille des pics x la puissance
                    // Seulement sur de la glace dégagée, avec assez d'air au-dessus : jamais dans un bloc, un arbre ou de l'herbe
                    if (!IceTerrain.hasAirAbove(level, ground, (int) Math.ceil(size * SPIKE_BLOCKS_PER_SIZE))) {
                        continue;
                    }
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)); // incliné vers l'extérieur
                    IceSpike.spawn(level, new Vec3(x + 0.5, gy + 1.0, z + 0.5), size, yaw,
                            5.0F + random.nextFloat() * 25.0F, SPIKE_LIFE_TICKS + random.nextInt(120));
                }
            }
        }

        // Entités atteintes par le front : dégâts massifs une fois, puis statue de glace
        AABB zone = new AABB(c, c).inflate(outer, Math.max(12.0 * w.power, searchRange(outer) + 3.0), outer); // suit aussi le relief
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, zone, e -> IceFreeze.canHit(e, w.owner))) {
            double d = Math.sqrt(target.distanceToSqr(c.x, target.getY(), c.z));
            if (d > outer || w.hit.contains(target.getId())) {
                continue;
            }
            w.hit.add(target.getId());
            float damage = (NEAR_DAMAGE + (FAR_DAMAGE - NEAR_DAMAGE) * (float) (d / radius)) * (float) w.power;
            int freeze = (int) (FREEZE_TICKS * w.power); // gel plus long en charge
            IceFreeze.hit(level, w.owner, target, damage, freeze + 40);
            IceFreeze.freeze(level, target, freeze, true);
        }

        IceEffects.iceAgeFront(level, c, outer, w.age, (px, pz) -> {
            int gy = IceTerrain.findSurface(level, Mth.floor(px), cy, Mth.floor(pz), searchRange(outer));
            return gy == IceTerrain.NO_SURFACE ? c.y : gy + 1.0;
        });
    }

    /** Écart de hauteur toléré à la distance {@code d} du joueur : plus loin, le relief peut monter ou descendre davantage. */
    private static int searchRange(double d) {
        return Math.min(24, 6 + (int) (d * 0.5));
    }

    /**
     * Taille d'un pic (1 = ~2,5 blocs) : surtout des petits, des moyens, et quelques très grands pics de 6 à 8 blocs, répartis
     * au hasard pour un champ de glace chaotique.
     */
    private static float randomSpikeSize(RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.55F) {
            return 0.5F + random.nextFloat() * 0.5F;  // petits : 1,2 à 2,5 blocs
        }
        if (roll < 0.88F) {
            return 1.1F + random.nextFloat() * 0.7F;  // moyens : 2,7 à 4,5 blocs
        }
        return 2.4F + random.nextFloat() * 0.9F;      // très grands : 6 à 8 blocs
    }

    public static void clear() {
        for (Wave w : WAVES) {
            w.owner.removeAttached(ModAttachments.BUDDHA_POSE);
        }
        WAVES.clear();
    }
}
