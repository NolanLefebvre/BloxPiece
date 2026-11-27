package nolan.bloxpiece.ability;

import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.race.Races;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Race Fishman (karaté des hommes-poissons) : état commun des quatre attaques, côté serveur.
 * <ul>
 *   <li>L'attaque en cours est synchronisée avec TOUS les joueurs (FISHMAN_ACTION = début x 8 + type) : chacun voit les poses
 *       (FishmanPose), l'eau sur les bras (FishmanLayer) et la première personne (FishmanFirstPerson) aux mêmes instants.</li>
 *   <li>Une seule attaque à la fois ; tout s'arrête proprement à la mort, la déconnexion, le changement de race.</li>
 *   <li>Les coups réutilisent les outils de la race Humaine (Human.strike : dégâts de joueur, recul, jamais deux fois la même
 *       cible par activation) et Goro.targets (Party, créatif exclus).</li>
 * </ul>
 */
public final class Fishman {

    /** Types d'attaque (animations). */
    public static final int WAVE = 1, SPEAR = 2, TIDE = 3, DEPTHS = 4;

    private Fishman() {
    }

    public static long now(ServerPlayer player) {
        return player.level().getGameTime();
    }

    static void setAction(ServerPlayer player, int kind) {
        player.setAttached(ModAttachments.FISHMAN_ACTION, now(player) * 8 + kind);
    }

    static void clearAction(ServerPlayer player, int kind) {
        Long current = player.getAttached(ModAttachments.FISHMAN_ACTION);
        if (current != null && current % 8 == kind) {
            player.removeAttached(ModAttachments.FISHMAN_ACTION);
        }
    }

    static boolean refuseBusy(ServerPlayer player) {
        if (player.getAttached(ModAttachments.FISHMAN_ACTION) != null) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.fishman.busy"), true);
            return true;
        }
        return false;
    }

    /** Le joueur peut-il continuer (vivant, connecté, même dimension, toujours Fishman, conscient, pas menotté) ? */
    static boolean valid(ServerPlayer player, ServerLevel level) {
        return player.isAlive() && !player.isRemoved() && !player.hasDisconnected() && player.level() == level
                && Races.of(player) == Races.FISHMAN && !nolan.bloxpiece.haki.Unconscious.is(player)
                && !nolan.bloxpiece.handcuffs.Handcuffs.isCuffed(player);
    }

    /** Point devant la main ({@code side} 1 = droite, -1 = gauche), à hauteur de poitrine, dans l'axe du regard. */
    static Vec3 hand(ServerPlayer player, double side, double forward) {
        return player.getEyePosition().add(0.0, -0.45, 0.0).add(Human.right(player).scale(0.33 * side)).add(player.getLookAngle().scale(forward));
    }

    /** Fin d'un trajet droit : le premier bloc solide sur la ligne, sinon le bout de la portée. */
    static Vec3 clip(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    /**
     * Le volume qui avance de {@code a} à {@code b} (rayon {@code radius}) touche les cibles qu'il traverse : chacune une seule fois
     * par projectile ({@code hit}) et, si {@code count} n'est pas null, au plus {@code maxPerTarget} fois en tout (plusieurs
     * projectiles de la même attaque). Dégâts de joueur et recul dans le sens du trajet, plus {@code up}. Renvoie le nombre touché.
     */
    static int sweep(ServerLevel level, ServerPlayer player, Vec3 a, Vec3 b, double radius, float damage, double push, double up,
                     Set<LivingEntity> hit, Map<LivingEntity, Integer> count, int maxPerTarget) {
        Vec3 d = b.subtract(a);
        double len2 = d.lengthSqr();
        Vec3 dir = len2 < 1.0E-6 ? Vec3.ZERO : d.normalize();
        int n = 0;
        for (LivingEntity e : Goro.targets(level, player, new AABB(a, b).inflate(radius + 1.0))) {
            if (hit.contains(e)) {
                continue;
            }
            Vec3 c = e.getBoundingBox().getCenter();
            double t = len2 < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, c.subtract(a).dot(d) / len2));
            Vec3 near = a.add(d.scale(t));
            double reach = radius + Math.max(e.getBbWidth(), e.getBbHeight()) * 0.5;
            if (!e.getBoundingBox().inflate(radius).contains(near) && near.distanceTo(c) > reach) {
                continue;
            }
            int k = count == null ? 0 : count.getOrDefault(e, 0);
            if (count != null && k >= maxPerTarget) {
                continue;
            }
            if (Human.strike(level, player, e, damage, dir.scale(push).add(0.0, up, 0.0), hit)) {
                if (count != null) {
                    count.put(e, k + 1);
                }
                n++;
            }
        }
        return n;
    }

    /** Éclaboussures (particules vanilla en complément du rendu 3D). */
    static void splash(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, count, spread, spread * 0.6, spread, 0.25);
        level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, count / 3 + 1, spread, spread * 0.5, spread, 0.05);
    }

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> handler.getPlayer().removeAttached(ModAttachments.FISHMAN_ACTION));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> handler.getPlayer().removeAttached(ModAttachments.FISHMAN_ACTION));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            oldPlayer.removeAttached(ModAttachments.FISHMAN_ACTION);
            newPlayer.removeAttached(ModAttachments.FISHMAN_ACTION);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                player.removeAttached(ModAttachments.FISHMAN_ACTION);
            }
        });
    }
}
