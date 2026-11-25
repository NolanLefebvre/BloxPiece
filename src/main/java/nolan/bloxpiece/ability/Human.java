package nolan.bloxpiece.ability;

import java.util.Set;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.race.Races;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Race Humaine : ce qui est commun à ses quatre attaques (HumanLightningImpactAbility, HumanDestructiveRushAbility,
 * HumanEarthBreakerAbility, HumanConquerorFistAbility), côté SERVEUR. Même organisation que la race Cyborg :
 * <ul>
 *   <li>Animations : l'attaque en cours est écrite dans l'attachment HUMAN_ACTION (début x 8 + type), synchronisé avec tous
 *       les joueurs proches : tous voient les poses, l'aura du poing, les traînées (client : HumanPose, HumanFistLayer).</li>
 *   <li>Une seule attaque Humaine à la fois ({@link #busy}). Chaque attaque est une session (Goro.start, déroulement tick par
 *       tick) qui s'arrête proprement si le joueur meurt, se déconnecte, change de dimension, est évanoui ou n'est plus Humain
 *       ({@link #valid}).</li>
 *   <li>Coups : {@link #strike} (dégâts de coup de joueur, une fois par cible et par attaque, recul réduit par la résistance
 *       au recul, synchronisé aussi pour un joueur touché). Jamais le lanceur, sa Party ou un joueur en créatif
 *       (BuddhaHits.canHit).</li>
 * </ul>
 * Toutes les valeurs (durées, recharges, dégâts, portées) sont des constantes en haut de chaque attaque.
 */
public final class Human {

    // ---- Types d'attaque (animations) ; RUSH_END = récupération après la ruée (sa durée dépend de la collision)
    public static final int IMPACT = 1, RUSH = 2, BREAKER = 3, CONQUEROR = 4, RUSH_END = 5;

    private Human() {
    }

    // ------------------------------------------------------------------ état des attaques (animations synchronisées)

    public static long now(ServerPlayer player) {
        return player.level().getGameTime();
    }

    /** Début d'une phase d'attaque : tous les joueurs proches voient son animation. */
    static void setAction(ServerPlayer player, int kind) {
        player.setAttached(ModAttachments.HUMAN_ACTION, now(player) * 8 + kind);
    }

    static void clearAction(ServerPlayer player, int kind) {
        Long current = player.getAttached(ModAttachments.HUMAN_ACTION);
        if (current != null && current % 8 == kind) {
            player.removeAttached(ModAttachments.HUMAN_ACTION);
        }
    }

    /** Une attaque Humaine est-elle en cours ? Une seule à la fois. */
    static boolean busy(ServerPlayer player) {
        return player.getAttached(ModAttachments.HUMAN_ACTION) != null;
    }

    /** Refus commun : déjà une attaque Humaine en cours (aucune recharge consommée). */
    static boolean refuseBusy(ServerPlayer player) {
        if (busy(player)) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.human.busy"), true);
            return true;
        }
        return false;
    }

    /** Le joueur peut-il continuer son attaque (vivant, connecté, même dimension, toujours Humain, conscient, pas menotté) ? */
    static boolean valid(ServerPlayer player, ServerLevel level) {
        return player.isAlive() && !player.isRemoved() && !player.hasDisconnected() && player.level() == level
                && Races.of(player) == Races.HUMAN && !nolan.bloxpiece.haki.Unconscious.is(player)
                && !nolan.bloxpiece.handcuffs.Handcuffs.isCuffed(player);
    }

    // ------------------------------------------------------------------ repères autour du joueur

    /** Direction horizontale du regard (jamais nulle). */
    static Vec3 flatLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            double yaw = Math.toRadians(player.getYRot());
            return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        }
        return flat.normalize();
    }

    /** Vecteur horizontal vers la DROITE du joueur. */
    static Vec3 right(ServerPlayer player) {
        Vec3 f = flatLook(player);
        return new Vec3(-f.z, 0.0, f.x);
    }

    /** Point devant le poing droit tendu : à hauteur d'épaule, côté droit, dans l'axe du regard. */
    static Vec3 rightFist(ServerPlayer player, double forward) {
        return player.getEyePosition().add(0.0, -0.4, 0.0).add(right(player).scale(0.33)).add(player.getLookAngle().scale(forward));
    }

    /**
     * Hauteur du sol SOUS (x, z), à partir de la hauteur {@code nearY} (les pieds du joueur, un point d'impact...) : la recherche
     * part juste au-dessus (marche d'un bloc tolérée) et DESCEND. Elle ne part jamais de plus haut : sous un arbre, le feuillage
     * au-dessus du joueur n'est jamais pris pour le sol. Rien trouvé sur {@link #GROUND_DEPTH} blocs (vide, falaise) : la hauteur
     * {@code nearY} elle-même.
     */
    static double ground(ServerLevel level, double x, double z, double nearY) {
        BlockPos.MutableBlockPos p = BlockPos.containing(x, nearY + 0.5, z).mutable();
        for (int i = 0; i <= GROUND_DEPTH; i++, p.move(0, -1, 0)) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.getCollisionShape(level, p).isEmpty()) {
                return p.getY() + s.getCollisionShape(level, p).max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return nearY;
    }

    /** Profondeur maximale de la recherche du sol sous un point (blocs). */
    private static final int GROUND_DEPTH = 8;

    /** Le bloc du sol sous {@code at} (pour la poussière et les éclats aux couleurs du vrai terrain), ou null. */
    static BlockState groundBlock(ServerLevel level, Vec3 at) {
        BlockPos.MutableBlockPos p = BlockPos.containing(at.x, at.y + 0.5, at.z).mutable();
        for (int i = 0; i < 4; i++, p.move(0, -1, 0)) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.getCollisionShape(level, p).isEmpty()) {
                return s;
            }
        }
        return null;
    }

    /** Éclats du vrai sol (particules de bloc) en {@code at}. */
    static void groundBurst(ServerLevel level, Vec3 at, int count, double spread, double speed) {
        BlockState state = groundBlock(level, at);
        if (state != null) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y + 0.15, at.z, count,
                    spread, 0.1, spread, speed);
        }
    }

    // ------------------------------------------------------------------ coups

    /** Une créature que l'attaque de {@code owner} peut toucher (pas lui, pas sa Party, pas un joueur en créatif). */
    static boolean canHit(LivingEntity entity, ServerPlayer owner) {
        return BuddhaHits.canHit(entity, owner);
    }

    /**
     * Un COUP physique : dégâts de coup de joueur (armure, Haki et protections normales s'appliquent), puis projection
     * ({@code push} en blocs/tick, réduite par la résistance au recul). {@code hit} : les cibles déjà touchées par CETTE
     * activation (jamais deux fois). Renvoie vrai si la cible vient d'être frappée.
     */
    static boolean strike(ServerLevel level, ServerPlayer owner, LivingEntity target, float damage, Vec3 push, Set<LivingEntity> hit) {
        if (!hit.add(target)) {
            return false;
        }
        DamageSource source = level.damageSources().playerAttack(owner);
        target.setLastHurtByMob(owner); // XP et riposte attribuées au lanceur
        target.setInvulnerableTime(0); // une attaque distincte : pas absorbée par l'invulnérabilité d'un coup précédent
        target.hurtServer(level, source, damage);
        double resistance = Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        Vec3 v = push.scale(1.0 - resistance);
        target.push(v.x, v.y, v.z);
        target.fallDistance = 0.0;
        if (target instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player)); // un joueur touché est bien projeté
        }
        return true;
    }

    /**
     * Projection visible : pendant {@code ticks} ticks, la cible projetée laisse une traînée de souffle et de poussière
     * (accompagne le recul, quelle que soit la cible).
     */
    static void knockTrail(ServerLevel level, LivingEntity target, int ticks) {
        Goro.start(new Goro.Session() {
            int t;

            @Override
            public boolean tick() {
                if (++t > ticks || !target.isAlive() || target.isRemoved() || target.level() != level) {
                    return false;
                }
                Vec3 c = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
                level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 2, target.getBbWidth() * 0.3, target.getBbHeight() * 0.3,
                        target.getBbWidth() * 0.3, 0.01);
                if (t % 2 == 0) {
                    level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 1, 0.1, 0.1, 0.1, 0.02);
                }
                if (target.onGround()) {
                    groundBurst(level, target.position(), 3, 0.25, 0.05);
                }
                return true;
            }
        });
    }

    /** Toutes les créatures touchables dans la boîte. */
    static java.util.List<LivingEntity> targets(ServerLevel level, ServerPlayer owner, AABB box) {
        return Goro.targets(level, owner, box);
    }

    /** Fige la vitesse horizontale du joueur (fin de ruée, appui de la frappe) et prévient son client. */
    static void halt(ServerPlayer player) {
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    // ------------------------------------------------------------------ événements

    public static void initialize() {
        // animation d'une ancienne session (déconnexion en pleine attaque) : effacée
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> handler.getPlayer().removeAttached(ModAttachments.HUMAN_ACTION));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> handler.getPlayer().removeAttached(ModAttachments.HUMAN_ACTION));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            oldPlayer.removeAttached(ModAttachments.HUMAN_ACTION);
            newPlayer.removeAttached(ModAttachments.HUMAN_ACTION);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                player.removeAttached(ModAttachments.HUMAN_ACTION);
            }
        });
    }
}
