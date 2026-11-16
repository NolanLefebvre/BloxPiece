package nolan.bloxpiece.ability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.BuddhaEffects;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Troisième attaque du fruit du Bouddha : l'Onde de choc (la frappe paume ouverte de Sengoku, droit devant).
 *
 * Réservée à la forme transformée. Incantation ({@link #WINDUP_TICKS} ticks) : le joueur lève et tend lentement le bras, paume
 * ouverte (pose synchronisée, voir PlayerModelMixin), pendant que l'énergie se concentre dans sa paume. Puis un dôme d'énergie
 * dorée éclate devant la paume et s'étend en un cône massif sur {@link #RANGE} blocs, en {@link #TICKS} ticks ; l'onde reste
 * ensuite visible dans les airs et se dissipe lentement ({@link #LINGER_TICKS} ticks). Le front de l'onde touche chaque entité
 * UNE fois : lourds dégâts (plus forts près du joueur) et projection violente vers l'arrière. Sur son passage, le sol se soulève
 * en gerbes de débris (particules : le terrain n'est pas détruit). La caméra des joueurs proches tremble.
 *
 * Tout est calculé côté serveur (tick(), appelé par ModEvents) ; particules, pose et tremblement sont envoyés à tous les joueurs.
 */
public class BuddhaShockwaveAbility implements Ability {

    /** Portée de l'onde (blocs). */
    static final double RANGE = 28.0; // doublée (avant : 14)
    /** Durée de propagation jusqu'à la portée maximale (ticks). */
    private static final int TICKS = 10; // un peu plus long pour une onde deux fois plus grande (avant : 7)
    /** Rayon du cône au départ (blocs, devant la paume) et à la portée maximale. */
    private static final double START_RADIUS = 3.0;  // doublés (avant : 1,5 et 6,5)
    private static final double END_RADIUS = 13.0;
    /** Dégâts au contact du joueur, puis à la portée maximale (décroissance linéaire). */
    private static final float NEAR_DAMAGE = 30.0F;
    private static final float FAR_DAMAGE = 16.0F;
    /** Projection (blocs/tick) dans l'axe de l'onde, et vers le haut. */
    private static final double PUSH = 3.2;
    private static final double LIFT = 0.7;
    /** Incantation (ticks) : le bras se lève et se tend lentement vers l'avant pendant que l'énergie se concentre dans la paume. */
    private static final int WINDUP_TICKS = 14;
    /** Après la propagation, l'onde reste visible dans les airs et se dissipe progressivement pendant ce nombre de ticks. */
    private static final int LINGER_TICKS = 36;
    /** Le bras reste tendu jusqu'à ce que l'onde ait presque disparu. */
    private static final int POSE_TICKS = TICKS + LINGER_TICKS / 2;

    /** Onde en cours de propagation (état serveur). */
    private static final class Wave {
        final ServerLevel level;
        final ServerPlayer owner;
        final Vec3 origin;
        final Vec3 dir;
        final Set<Integer> hit = new HashSet<>();
        int windup; // ticks d'incantation déjà écoulés
        int age;    // ticks depuis le déclenchement de l'onde

        Wave(ServerLevel level, ServerPlayer owner, Vec3 origin, Vec3 dir) {
            this.level = level;
            this.owner = owner;
            this.origin = origin;
            this.dir = dir;
        }
    }

    private static final List<Wave> WAVES = new ArrayList<>();

    @Override
    public String id() {
        return "buddha_shockwave";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 10; // 10 secondes
    }

    @Override
    public int requiredLevel() {
        return 5; // comme la troisième attaque du fruit du feu (Pluie de météores)
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        return BuddhaHits.requireTransformed(player);
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        player.setAttached(ModAttachments.BUDDHA_POSE, raisePose(0.0)); // le bras commence à se lever (chez tous les joueurs)
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0F, 0.6F);
        WAVES.add(new Wave(level, player, palmOf(player), player.getLookAngle()));
    }

    /** La paume du joueur : devant ses yeux, un peu plus bas (suit sa taille). */
    private static Vec3 palmOf(ServerPlayer player) {
        double size = player.getScale();
        return player.getEyePosition().add(player.getLookAngle().scale(1.2 * size)).add(0.0, -0.3 * size, 0.0);
    }

    /** Pose "bras qui se lève" synchronisée : 100 + pourcentage de levée (0 = bras baissé, 100 = tendu). Voir PlayerModelMixin. */
    private static int raisePose(double progress) {
        return 100 + (int) Math.round(Math.max(0.0, Math.min(1.0, progress)) * 100.0);
    }

    /** Fin de l'incantation : le dôme éclate devant la paume et l'onde part dans la direction du regard à cet instant. */
    private static Wave fire(Wave wave) {
        ServerPlayer player = wave.owner;
        Vec3 dir = player.getLookAngle();
        Wave fired = new Wave(wave.level, player, palmOf(player), dir);
        fired.windup = WINDUP_TICKS;
        player.setAttached(ModAttachments.BUDDHA_POSE, 1); // bras tendu, paume ouverte
        BuddhaEffects.shockwaveBurst(wave.level, fired.origin, dir);
        BuddhaHits.shake(wave.level, fired.origin, 40.0, 2.0F, 12);
        wave.level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.5F, 0.8F);
        wave.level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0F, 0.7F);
        advance(fired); // le dôme apparaît instantanément devant la paume
        return fired;
    }

    /** À appeler à chaque tick serveur (voir ModEvents). */
    public static void tick(MinecraftServer server) {
        for (int i = 0; i < WAVES.size(); i++) {
            Wave wave = WAVES.get(i);
            if (wave.owner.isRemoved() || (wave.windup < WINDUP_TICKS && (!wave.owner.isAlive() || wave.owner.isInWater()))) {
                wave.owner.removeAttached(ModAttachments.BUDDHA_POSE);
                WAVES.remove(i--);
                continue;
            }
            // 1. Incantation : le bras se lève lentement, l'énergie se concentre dans la paume
            if (wave.windup < WINDUP_TICKS) {
                wave.windup++;
                double progress = (double) wave.windup / WINDUP_TICKS;
                wave.owner.setAttached(ModAttachments.BUDDHA_POSE, raisePose(progress));
                BuddhaEffects.shockwaveWindup(wave.level, palmOf(wave.owner), progress, wave.windup);
                if (wave.windup == WINDUP_TICKS) {
                    WAVES.set(i, fire(wave));
                }
                continue;
            }
            // 2. Propagation, puis 3. persistance : l'onde reste dans les airs et se dissipe lentement
            if (wave.age < TICKS) {
                advance(wave);
                continue;
            }
            int lingered = wave.age - TICKS;
            BuddhaEffects.shockwaveLinger(wave.level, wave.origin, wave.dir, RANGE, START_RADIUS, END_RADIUS,
                    (double) lingered / LINGER_TICKS, wave.age);
            if (wave.age == POSE_TICKS) {
                wave.owner.removeAttached(ModAttachments.BUDDHA_POSE);
            }
            wave.age++;
            if (lingered + 1 >= LINGER_TICKS) {
                WAVES.remove(i--);
            }
        }
    }

    public static void clear() {
        for (Wave wave : WAVES) {
            wave.owner.removeAttached(ModAttachments.BUDDHA_POSE);
        }
        WAVES.clear();
    }

    /** Fait avancer le front de l'onde d'une étape : touche les entités qu'il vient d'atteindre (une fois chacune), puis les effets. */
    private static void advance(Wave wave) {
        double front = RANGE * Math.min(1.0, (wave.age + 1.0) / TICKS);
        double radius = coneRadius(front);
        Vec3 c = wave.origin;

        AABB area = new AABB(c, c).inflate(front + END_RADIUS);
        for (LivingEntity target : wave.level.getEntitiesOfClass(LivingEntity.class, area, e -> BuddhaHits.canHit(e, wave.owner))) {
            if (wave.hit.contains(target.getId())) {
                continue;
            }
            Vec3 to = target.getBoundingBox().getCenter().subtract(c);
            double along = to.dot(wave.dir);
            if (along < -1.0 || along > front) {
                continue;
            }
            double side = to.subtract(wave.dir.scale(along)).length();
            if (side > coneRadius(Math.max(0.0, along)) + target.getBbWidth() * 0.5) {
                continue;
            }
            wave.hit.add(target.getId());
            double falloff = Math.max(0.0, along) / RANGE;
            float damage = NEAR_DAMAGE + (FAR_DAMAGE - NEAR_DAMAGE) * (float) falloff;
            Vec3 push = new Vec3(wave.dir.x, 0.0, wave.dir.z).normalize().scale(PUSH * (1.0 - 0.4 * falloff)).add(0.0, LIFT, 0.0);
            BuddhaHits.strike(wave.level, wave.owner, target, damage, push);
        }

        BuddhaEffects.shockwaveFront(wave.level, c, wave.dir, front, radius, wave.age);
        wave.age++;
    }

    private static double coneRadius(double distance) {
        return START_RADIUS + (END_RADIUS - START_RADIUS) * Math.min(1.0, distance / RANGE);
    }
}
