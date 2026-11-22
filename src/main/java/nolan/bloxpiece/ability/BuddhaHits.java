package nolan.bloxpiece.ability;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.EnteiSun;
import nolan.bloxpiece.network.ShakePayload;

/**
 * Outils communs aux ondes de choc du fruit du Bouddha (Onde de choc, Déflagration) : qui peut être touché, le coup lui-même
 * (dégâts + projection) et le tremblement de caméra des joueurs proches. Le lanceur n'est jamais touché.
 */
public final class BuddhaHits {

    private BuddhaHits() {
    }

    /**
     * Les attaques 3 et 4 du Bouddha exigent la transformation : sinon, rien ne part (pas de recharge consommée) et un message
     * s'affiche dans la barre d'action.
     */
    static boolean requireTransformed(ServerPlayer player) {
        if (BuddhaTransformAbility.isTransformed(player)) {
            return true;
        }
        player.sendSystemMessage(Component.translatable("message.bloxpiece.buddha_not_transformed"), true);
        return false;
    }

    /** Une entité vivante que l'attaque de {@code owner} peut toucher. */
    static boolean canHit(LivingEntity entity, ServerPlayer owner) {
        if (entity == owner || !entity.isAlive() || entity.isSpectator() || entity instanceof EnteiSun) {
            return false;
        }
        if (nolan.bloxpiece.party.Parties.allies(entity, owner)) {
            return false; // membre de la même Party : ni dégâts, ni recul, ni paralysie
        }
        return !(entity instanceof Player player && player.isCreative());
    }

    /**
     * Inflige les dégâts puis projette l'entité ({@code push} en blocs/tick, réduit par sa résistance au recul). À n'appeler
     * qu'une fois par entité et par attaque (chaque attaque garde la liste des entités déjà touchées).
     */
    static void strike(ServerLevel level, ServerPlayer owner, LivingEntity target, float damage, Vec3 push) {
        DamageSource source = level.damageSources().source(DamageTypes.PLAYER_EXPLOSION, owner, owner);
        target.setLastHurtByMob(owner); // XP et riposte attribuées au lanceur
        target.hurtServer(level, source, damage);
        double resistance = Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        Vec3 v = push.scale(1.0 - resistance);
        target.push(v.x, v.y, v.z); // demande aussi la synchronisation de la vitesse avec les clients
        target.fallDistance = 0.0;
    }

    /** Fait trembler la caméra des joueurs à moins de {@code radius} blocs, plus fort près du centre. */
    public static void shake(ServerLevel level, Vec3 center, double radius, float strength, int ticks) {
        for (ServerPlayer player : level.players()) {
            double distance = player.position().distanceTo(center);
            if (distance < radius) {
                float power = strength * (float) (1.0 - 0.7 * distance / radius);
                ServerPlayNetworking.send(player, new ShakePayload(power, ticks));
            }
        }
    }
}
