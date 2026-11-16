package nolan.bloxpiece.ability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

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
 * 1. Le joueur pose la main au sol (pose synchronisée 3, voir PlayerModelMixin) : le froid converge vers sa main ({@link #CHARGE_TICKS} ticks).
 * 2. Une vague de froid extrême part à 360° jusqu'à {@link #RADIUS} blocs en {@link #WAVE_TICKS} ticks : mur de blizzard et
 *    brouillard glacé au front de la vague, sol et eau changés en glace (temporairement, IceTerrain), pics de glace 3D qui
 *    jaillissent du sol (IceSpike). Chaque entité atteinte (sauf le lanceur) subit des dégâts massifs UNE fois et est emprisonnée
 *    dans une statue de glace qui l'immobilise complètement (IceFreeze).
 */
public class IceAgeAbility implements Ability {

    private static final int CHARGE_TICKS = 14;
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

    private static final class Wave {
        final ServerPlayer owner;
        final ServerLevel level;
        Vec3 center;
        final Set<Integer> hit = new HashSet<>();
        int charge;
        int age;

        Wave(ServerPlayer owner) {
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
    public void use(ServerPlayer player) {
        WAVES.add(new Wave(player));
        player.setAttached(ModAttachments.BUDDHA_POSE, 3); // main posée au sol (pose partagée avec les attaques du Bouddha)
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 3.0F, 0.4F);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Wave> it = WAVES.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            ServerPlayer player = w.owner;
            if (w.charge < CHARGE_TICKS) {
                if (player.isRemoved() || !player.isAlive() || player.isInWater() || player.level() != w.level) {
                    player.removeAttached(ModAttachments.BUDDHA_POSE);
                    it.remove();
                    continue;
                }
                w.charge++;
                w.center = player.position();
                IceEffects.iceAgeCharge(w.level, hand(player), (double) w.charge / CHARGE_TICKS);
                if (w.charge == CHARGE_TICKS) {
                    player.removeAttached(ModAttachments.BUDDHA_POSE);
                    BuddhaHits.shake(w.level, w.center, RADIUS * 2.0, 3.0F, 30);
                    w.level.playSound(null, w.center.x, w.center.y, w.center.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 4.0F, 0.4F);
                    w.level.playSound(null, w.center.x, w.center.y, w.center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 1.4F);
                }
                continue;
            }
            advance(w);
            if (++w.age >= WAVE_TICKS) {
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
        double inner = RADIUS * w.age / WAVE_TICKS;
        double outer = RADIUS * (w.age + 1) / WAVE_TICKS;
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
                double top = IceTerrain.freezeColumn(level, x, cy, z, TERRAIN_TICKS);
                if (!Double.isNaN(top) && d > 2.5 && random.nextDouble() < SPIKE_CHANCE) {
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)); // incliné vers l'extérieur
                    IceSpike.spawn(level, new Vec3(x + 0.5, top, z + 0.5), randomSpikeSize(random), yaw,
                            5.0F + random.nextFloat() * 25.0F, SPIKE_LIFE_TICKS + random.nextInt(120));
                }
            }
        }

        // Entités atteintes par le front : dégâts massifs une fois, puis statue de glace
        AABB zone = new AABB(c, c).inflate(outer, 12.0, outer);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, zone, e -> IceFreeze.canHit(e, w.owner))) {
            double d = Math.sqrt(target.distanceToSqr(c.x, target.getY(), c.z));
            if (d > outer || w.hit.contains(target.getId())) {
                continue;
            }
            w.hit.add(target.getId());
            float damage = NEAR_DAMAGE + (FAR_DAMAGE - NEAR_DAMAGE) * (float) (d / RADIUS);
            IceFreeze.hit(level, w.owner, target, damage, FREEZE_TICKS + 40);
            IceFreeze.freeze(level, target, FREEZE_TICKS, true);
        }

        IceEffects.iceAgeFront(level, c, outer, w.age);
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
