package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.fx.BuddhaEffects;
import nolan.bloxpiece.fx.BuddhaSeal;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Quatrième attaque (ultime) du fruit du Bouddha : la Déflagration du Bouddha.
 *
 * 1. Charge, tant que la touche est maintenue : bras levés (pose synchronisée), l'énergie dorée converge de tous côtés vers le
 *    joueur, le sceau doré (BuddhaSeal, déjà utilisé par la Paume) se dessine au sol sous ses pieds et grandit, la caméra tremble
 *    de plus en plus. La charge dure au moins {@link #MIN_CHARGE_TICKS} ticks (un simple appui donne la Déflagration normale) ;
 *    maintenue jusqu'à {@link #MAX_CHARGE_TICKS} ticks, elle est deux fois plus grosse (rayon, cratère) et part toute seule.
 * 2. Libération (touche relâchée ou charge pleine) : une sphère d'énergie dorée s'étend à 360° jusqu'à {@link #RADIUS} blocs
 *    (x la puissance de charge, 1 à 2) (flash, anneaux au sol, fumée dorée,
 *    voir BuddhaEffects.nova). Toutes les entités du rayon subissent des dégâts massifs (une seule fois) et sont éjectées très loin ;
 *    une explosion creuse un cratère autour du joueur (si mobGriefing est actif). Le lanceur est immunisé : ni dégâts, ni recul, et
 *    une chute lente l'empêche de se blesser en tombant dans son propre cratère.
 *
 * Tout est géré côté serveur (tick(), appelé par ModEvents) ; particules, pose, sceau et tremblement sont envoyés à tous les joueurs.
 */
public class BuddhaUltimateAbility implements Ability {

    /** Charge minimale (ticks) : Déflagration normale. */
    private static final int MIN_CHARGE_TICKS = 16;
    /** Charge pleine (ticks) : Déflagration deux fois plus grosse, déclenchée automatiquement. */
    private static final int MAX_CHARGE_TICKS = 60;
    /** Taille de la Déflagration à pleine charge (multiple de la taille normale). */
    private static final double MAX_POWER = 2.0;
    /** Rayon de la déflagration à charge minimale (blocs). */
    private static final double RADIUS = 22.0;
    /** Dégâts au contact du joueur, puis au bord du rayon. */
    private static final float NEAR_DAMAGE = 60.0F;
    private static final float FAR_DAMAGE = 24.0F;
    /** Éjection (blocs/tick) au contact du joueur, et vers le haut. */
    private static final double PUSH = 5.5;
    private static final double LIFT = 1.4;
    /** Puissance de l'explosion qui creuse le cratère (Hiken = 5, Paume = 7). */
    private static final float CRATER_POWER = 9.0F;

    private static final class Charge {
        int age;
        boolean released; // la touche a été relâchée : la Déflagration part dès la charge minimale atteinte
        BuddhaSeal seal;

        /** Puissance de charge : 1 à la charge minimale, MAX_POWER à la charge pleine. */
        double power() {
            double t = (double) (this.age - MIN_CHARGE_TICKS) / (MAX_CHARGE_TICKS - MIN_CHARGE_TICKS);
            return 1.0 + (MAX_POWER - 1.0) * Math.max(0.0, Math.min(1.0, t));
        }
    }

    private static final Map<UUID, Charge> CHARGES = new HashMap<>();

    @Override
    public String id() {
        return "buddha_ultimate";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60; // 60 secondes, comme l'ultime du fruit du feu
    }

    @Override
    public int requiredLevel() {
        return 8; // comme Dai Enkai: Entei
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        return !CHARGES.containsKey(player.getUUID()) && BuddhaHits.requireTransformed(player);
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Charge charge = new Charge();
        charge.seal = BuddhaSeal.spawn(level, ground(level, player));
        CHARGES.put(player.getUUID(), charge);
        player.setAttached(ModAttachments.BUDDHA_POSE, 2); // bras levés (chez tous les joueurs)
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3.0F, 0.4F);
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0F, 0.5F);
    }

    @Override
    public void release(ServerPlayer player) {
        Charge charge = CHARGES.get(player.getUUID());
        if (charge != null) {
            charge.released = true;
        }
    }

    /** À appeler à chaque tick serveur (voir ModEvents). */
    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Charge>> it = CHARGES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Charge> entry = it.next();
            Charge charge = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || player.isInWater() || player.level() != charge.seal.level()
                    || Fruits.get(player.getAttached(ModAttachments.FRUIT)) != Fruits.BUDDHA) {
                charge.seal.discard();
                if (player != null) {
                    player.removeAttached(ModAttachments.BUDDHA_POSE);
                }
                it.remove();
                continue;
            }
            ServerLevel level = (ServerLevel) player.level();
            charge.age++;
            charge.seal.place(ground(level, player));
            Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
            double power = charge.power();
            charge.seal.setSize((float) power);
            boolean ready = charge.age >= MIN_CHARGE_TICKS && (charge.released || charge.age >= MAX_CHARGE_TICKS);
            if (!ready) {
                double progress = Math.min(1.0, (double) charge.age / MIN_CHARGE_TICKS);
                BuddhaEffects.ultimateCharge(level, center, player.getScale() * power, progress, charge.age);
                if (charge.age % 10 == 0) {
                    // La tension monte : tremblement et son de plus en plus forts et aigus
                    BuddhaHits.shake(level, center, 30.0 * power, 0.5F + 0.6F * (float) (power - 1.0), 10);
                    level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS,
                            2.0F, 0.6F + 0.5F * (float) (power - 1.0));
                }
                continue;
            }
            it.remove();
            release(level, player, center, charge.seal, power);
        }
    }

    public static void clear() {
        for (Charge charge : CHARGES.values()) {
            charge.seal.discard();
        }
        CHARGES.clear();
    }

    /** Le sol sous les pieds du joueur (jusqu'à 8 blocs plus bas), où se pose le sceau. */
    private static Vec3 ground(ServerLevel level, ServerPlayer player) {
        Vec3 feet = player.position();
        BlockHitResult hit = level.clip(new ClipContext(feet.add(0.0, 0.2, 0.0), feet.add(0.0, -8.0, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        return hit.getType() == HitResult.Type.MISS ? feet : hit.getLocation();
    }

    private static void release(ServerLevel level, ServerPlayer player, Vec3 center, BuddhaSeal seal, double power) {
        player.removeAttached(ModAttachments.BUDDHA_POSE);
        double radius = RADIUS * power;
        float damageScale = (float) (0.5 + 0.5 * power); // x1 à charge minimale, x1,5 à charge pleine
        double pushScale = Math.sqrt(power);

        // Dégâts massifs et éjection : toutes les entités du rayon, une seule fois chacune (une seule passe)
        AABB zone = new AABB(center, center).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, zone, e -> BuddhaHits.canHit(e, player))) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(center);
            double distance = to.length();
            if (distance > radius) {
                continue;
            }
            double falloff = distance / radius;
            float damage = (NEAR_DAMAGE + (FAR_DAMAGE - NEAR_DAMAGE) * (float) falloff) * damageScale;
            Vec3 flat = new Vec3(to.x, 0.0, to.z);
            Vec3 out = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
            Vec3 push = out.scale(PUSH * pushScale * (1.0 - 0.5 * falloff)).add(0.0, LIFT * pushScale * (1.0 - 0.4 * falloff), 0.0);
            BuddhaHits.strike(level, player, target, damage, push);
        }

        // Cratère : explosion vanilla SANS dégâts ni recul (déjà appliqués), blocs détruits si mobGriefing est actif
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 4, 0, false, false)); // ne se blesse pas en tombant dans le cratère
        Vec3 feet = player.position();
        level.explode(player, Explosion.getDefaultDamageSource(level, player), new ExplosionDamageCalculator() {
            @Override
            public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                return false;
            }

            @Override
            public float getKnockbackMultiplier(Entity entity) {
                return 0.0F;
            }
        }, feet.x, feet.y, feet.z, (float) (CRATER_POWER * power), false, Level.ExplosionInteraction.MOB);

        seal.impact(); // le sceau devient l'anneau d'onde de choc au sol
        BuddhaEffects.nova(level, center, radius);
        BuddhaHits.shake(level, center, 64.0 * power, 4.0F * (float) pushScale, 24);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5.0F, 0.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 5.0F, 0.4F);
    }
}
