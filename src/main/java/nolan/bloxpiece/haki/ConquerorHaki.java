package nolan.bloxpiece.haki;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.ability.BuddhaHits;
import nolan.bloxpiece.fx.HakiBolt;
import nolan.bloxpiece.mob.TavernKeeper;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Haki des Rois (Haoshoku). Appui : une onde de choc jaillit, des éclairs 3D noirs et rouges crépitent autour de l'utilisateur
 * pendant {@link #DURATION} ticks (HakiBolt), avec un son de pression écrasante. Tout mob ou joueur qui se trouve ou ENTRE
 * dans le rayon {@link #RADIUS} pendant ce temps s'évanouit {@link #KNOCKOUT_TICKS} ticks (Unconscious). Les boss (Wither,
 * Dragon, Warden), les PNJ et les autres utilisateurs du Haki des Rois en train de le libérer résistent.
 */
public final class ConquerorHaki implements Haki {

    static final double RADIUS = 12.0;
    private static final int DURATION = 20 * 5;
    private static final int KNOCKOUT_TICKS = 20 * 8;
    private static final int COOLDOWN = 20 * 60;

    /** Résistent au Haki des Rois : les boss et les supports d'armure (identifiants Minecraft). */
    private static final java.util.Set<String> RESISTANT = java.util.Set.of("wither", "ender_dragon", "warden", "armor_stand",
            "elder_guardian");

    private final Map<UUID, Long> until = new HashMap<>();
    /** Prochaine surcharge (tick de jeu) de chaque joueur qui libère le Haki des Rois. */
    private final Map<UUID, Long> nextSurge = new HashMap<>();

    @Override
    public HakiType type() {
        return HakiType.CONQUEROR;
    }

    @Override
    public void press(ServerPlayer player) {
        if (isActive(player)) {
            return;
        }
        player.setAttached(ModAttachments.HAKI_CONQUEROR, true);
        this.until.put(player.getUUID(), player.level().getGameTime() + DURATION);
        ServerLevel level = (ServerLevel) player.level();
        Vec3 c = player.position().add(0.0, 1.0, 0.0);
        // onde de choc : anneaux au sol, souffle, et caméra qui tremble chez les joueurs proches
        for (int r = 1; r <= 3; r++) {
            int points = 28 * r;
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                double rad = RADIUS * r / 3.0;
                level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x + Math.cos(a) * rad, player.getY() + 0.2, c.z + Math.sin(a) * rad,
                        1, 0.0, 0.05, 0.0, 0.0);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 6, 1.5, 0.6, 1.5, 0.0);
        BuddhaHits.shake(level, c, RADIUS * 2.5, 2.5F, 18);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.5F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4F, 0.45F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2F, 0.7F);
        risingTrails(level, player, level.getRandom(), 12);
        // gerbe initiale : 10 longs éclairs en étoile, en diagonale tout autour du corps
        for (int i = 0; i < 10; i++) {
            spawnBolt(level, player, level.getRandom());
        }
    }

    /**
     * Éclair qui JAILLIT DU CORPS en diagonale, vers l'extérieur : surtout vers le haut (25 à 65 degrés), parfois vers le bas
     * (20 à 45 degrés), dans toutes les directions autour du joueur. Long (environ 1,5 à 2,5 blocs) mais fin ; il s'élance dans sa
     * direction pendant sa courte vie (voir HakiBoltRenderer). Taille négative = éclair orienté (convention du renderer).
     */
    private static void spawnBolt(ServerLevel level, ServerPlayer player, RandomSource random) {
        double a = random.nextDouble() * Math.PI * 2;                  // direction horizontale, vers l'extérieur
        double elevation = random.nextFloat() < 0.72F
                ? 25.0 + random.nextDouble() * 40.0                         // diagonale montante
                : -(20.0 + random.nextDouble() * 25.0);                     // diagonale descendante
        Vec3 p = player.position().add(Math.cos(a) * 0.35, 0.5 + random.nextDouble() * 1.1, Math.sin(a) * 0.35);
        // orientation du renderer : chaîne pointée par (rotation Y, puis X) ; X = angle depuis la verticale
        float yaw = (float) Math.toDegrees(Math.atan2(Math.cos(a), Math.sin(a)));
        float pitch = (float) (90.0 - elevation);
        float size = 0.65F + random.nextFloat() * 0.3F;
        HakiBolt.spawn(level, p, yaw, pitch, -size, 5 + random.nextInt(4));
    }

    /**
     * Surcharge : la puissance déborde d'un coup. Gerbe de 3 à 5 éclairs, onde circulaire au sol (anneau de fumée noire et de
     * braises rouges qui s'étend), grondement et léger tremblement de caméra.
     */
    private static void surge(ServerLevel level, ServerPlayer player, RandomSource random) {
        int bolts = 3 + random.nextInt(3);
        for (int i = 0; i < bolts; i++) {
            spawnBolt(level, player, random);
        }
        double radius = 1.6 + random.nextDouble() * 1.4;
        int points = 36;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            double cos = Math.cos(a), sin = Math.sin(a);
            double x = player.getX() + cos * radius, z = player.getZ() + sin * radius;
            // l'anneau s'étend vers l'extérieur (nombre 0 : (dx, dy, dz) = vitesse)
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, player.getY() + 0.1, z, 0, cos, 0.02, sin, 0.18);
            if (i % 2 == 0) {
                level.sendParticles(nolan.bloxpiece.registry.ModParticles.FIRE_EMBER, x, player.getY() + 0.15, z, 0, cos, 0.05, sin, 0.25);
            }
        }
        BuddhaHits.shake(level, player.position(), RADIUS * 1.5, 0.9F, 6);
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                0.7F, 0.5F + random.nextFloat() * 0.3F);
    }

    /** Traînées d'énergie qui s'élèvent du corps : fumée noire et braises rouges lancées vers le haut. */
    private static void risingTrails(ServerLevel level, ServerPlayer player, RandomSource random, int count) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 0.3 + random.nextDouble() * 0.6;
            double x = player.getX() + Math.cos(a) * r;
            double y = player.getY() + 0.2 + random.nextDouble() * 1.6;
            double z = player.getZ() + Math.sin(a) * r;
            double speed = 0.25 + random.nextDouble() * 0.25;
            // nombre 0 : (dx, dy, dz) devient la vitesse de la particule
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 0, 0.0, 1.0, 0.0, speed);
            level.sendParticles(nolan.bloxpiece.registry.ModParticles.FIRE_EMBER, x, y, z, 0, 0.0, 1.0, 0.0, speed * 1.3);
        }
    }

    @Override
    public void tick(ServerPlayer player) {
        Long end = this.until.get(player.getUUID());
        if (end == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        if (now >= end) {
            stop(player);
            HakiManager.startCooldown(player, type(), COOLDOWN);
            return;
        }
        RandomSource random = level.getRandom();
        // débordement incontrôlable : cadence irrégulière (0 à 2 éclairs par tick, environ 1 en moyenne) et surcharges
        // soudaines à intervalles imprévisibles
        float roll = random.nextFloat();
        int bolts = roll < 0.25F ? 0 : roll < 0.8F ? 1 : 2;
        for (int i = 0; i < bolts; i++) {
            spawnBolt(level, player, random);
        }
        Long next = this.nextSurge.get(player.getUUID());
        if (next == null || now >= next) {
            if (next != null) {
                surge(level, player, random);
            }
            this.nextSurge.put(player.getUUID(), now + 6 + random.nextInt(9));
        }
        risingTrails(level, player, random, 2);
        Vec3 c = player.position().add(0.0, 1.0, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, player.getY() + 0.3, c.z, 1, 1.4, 0.2, 1.4, 0.01); // au sol, hors du champ de vision
        level.sendParticles(nolan.bloxpiece.registry.ModParticles.FIRE_EMBER, c.x, c.y, c.z, 5, 1.2, 1.0, 1.2, 0.0);
        if (now % 20 == 0) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 0.6F);
        }
        // tout ce qui est (ou entre) dans le rayon s'évanouit
        AABB box = player.getBoundingBox().inflate(RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            if (target.distanceToSqr(player) > RADIUS * RADIUS || resists(target) || Unconscious.is(target)) {
                continue;
            }
            Unconscious.knockOut(target, KNOCKOUT_TICKS);
        }
    }

    private static boolean resists(LivingEntity target) {
        // par identifiant (les noms des constantes Java changent selon les versions de Minecraft)
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath();
        if (RESISTANT.contains(id) || target instanceof TavernKeeper) {
            return true;
        }
        if (target instanceof ServerPlayer p && (p.isCreative() || p.isSpectator()
                || Boolean.TRUE.equals(p.getAttached(ModAttachments.HAKI_CONQUEROR)))) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isActive(ServerPlayer player) {
        return Boolean.TRUE.equals(player.getAttached(ModAttachments.HAKI_CONQUEROR));
    }

    @Override
    public void stop(ServerPlayer player) {
        player.removeAttached(ModAttachments.HAKI_CONQUEROR);
        this.until.remove(player.getUUID());
        this.nextSurge.remove(player.getUUID());
    }
}
