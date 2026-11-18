package nolan.bloxpiece.haki;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Évanouissement (Haki des Rois) : l'entité (joueur ou mob) s'effondre, allongée sur le côté (rendu client), et ne peut plus
 * rien faire jusqu'à son réveil : ni bouger, ni sauter, ni attaquer, ni viser, ni utiliser d'objet, de bloc, d'attaque de fruit
 * ou de Haki. De l'écume et des étoiles d'étourdissement s'échappent de sa tête. Elle peut toujours être blessée.
 * La fin est un tick de jeu (attachment UNCONSCIOUS_UNTIL, sauvegardé et synchronisé), lu de la même façon par le client.
 */
public final class Unconscious {

    private static final Identifier SPEED = BloxPiece.id("unconscious_speed");
    private static final Identifier JUMP = BloxPiece.id("unconscious_jump");

    /** Entités évanouies suivies (chargées dans le monde). */
    private static final Set<LivingEntity> TRACKED = new HashSet<>();

    private Unconscious() {
    }

    /** L'entité est-elle évanouie en ce moment ? (serveur ET client) */
    public static boolean is(Entity entity) {
        Long until = entity.getAttached(ModAttachments.UNCONSCIOUS_UNTIL);
        return until != null && entity.level().getGameTime() < until;
    }

    /** Fait s'évanouir {@code target} pendant {@code ticks} ticks (prolonge si elle l'est déjà). */
    public static void knockOut(LivingEntity target, int ticks) {
        long until = target.level().getGameTime() + ticks;
        Long current = target.getAttached(ModAttachments.UNCONSCIOUS_UNTIL);
        boolean fresh = current == null || target.level().getGameTime() >= current;
        target.setAttached(ModAttachments.UNCONSCIOUS_UNTIL, Math.max(until, current == null ? 0L : current));
        TRACKED.add(target);
        if (!fresh) {
            return;
        }
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        if (target instanceof ServerPlayer player) {
            HakiManager.stopAll(player);
            player.sendSystemMessage(Component.translatable("message.bloxpiece.haki.unconscious"), true);
        }
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    private static void wake(LivingEntity entity) {
        entity.removeAttached(ModAttachments.UNCONSCIOUS_UNTIL);
        setFrozen(entity, false);
    }

    /** Paralysie d'un joueur : vitesse et saut à zéro (les mobs, eux, sont arrêtés à chaque tick). */
    private static void setFrozen(LivingEntity entity, boolean frozen) {
        if (!(entity instanceof Player)) {
            return;
        }
        apply(entity.getAttribute(Attributes.MOVEMENT_SPEED), SPEED, frozen);
        apply(entity.getAttribute(Attributes.JUMP_STRENGTH), JUMP, frozen);
    }

    private static void apply(AttributeInstance attribute, Identifier id, boolean on) {
        if (attribute == null) {
            return;
        }
        boolean has = attribute.getModifier(id) != null;
        if (on && !has) {
            attribute.addTransientModifier(new AttributeModifier(id, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!on && has) {
            attribute.removeModifier(id);
        }
    }

    public static void tick(MinecraftServer server) {
        List<LivingEntity> list = new ArrayList<>(TRACKED);
        for (LivingEntity entity : list) {
            if (entity.isRemoved() || !entity.isAlive()) {
                TRACKED.remove(entity);
                continue;
            }
            if (!is(entity)) {
                wake(entity);
                TRACKED.remove(entity);
                continue;
            }
            setFrozen(entity, true);
            if (entity instanceof Mob mob) {
                mob.setTarget(null);
                mob.setAggressive(false);
                mob.getNavigation().stop();
            }
            Vec3 v = entity.getDeltaMovement();
            entity.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
            if (entity.tickCount % 8 == 0 && entity.level() instanceof ServerLevel level) {
                // écume à la bouche et étoiles d'étourdissement, au ras du sol (l'entité est allongée)
                Vec3 head = entity.position().add(entity.getLookAngle().scale(-0.1)).add(0.0, 0.3, 0.0);
                level.sendParticles(ParticleTypes.BUBBLE_POP, head.x, head.y, head.z, 4, 0.2, 0.05, 0.2, 0.01);
                level.sendParticles(ParticleTypes.CRIT, head.x, head.y + 0.5, head.z, 3, 0.3, 0.1, 0.3, 0.02);
            }
        }
    }

    private static InteractionResult blocked(Player player) {
        return is(player) ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    public static void initialize() {
        // évanoui : aucune interaction (client et serveur)
        UseItemCallback.EVENT.register((player, level, hand) -> blocked(player));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> blocked(player));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> blocked(player));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> blocked(player));
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> blocked(player));
        // une entité évanouie n'inflige aucun dégât (coups, projectiles qu'elle aurait tirés juste avant)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(source.getEntity() != null && is(source.getEntity())));
        // rechargée inconsciente (sauvegarde, chunk) : de nouveau suivie
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof LivingEntity living && is(living)) {
                TRACKED.add(living);
            }
        });
    }

    public static void clear() {
        TRACKED.clear();
    }
}
