package nolan.bloxpiece.fruit;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.BuddhaPalm;
import nolan.bloxpiece.fx.BuddhaSeal;
import nolan.bloxpiece.fx.EnteiSun;
import nolan.bloxpiece.fx.FireEffects;
import nolan.bloxpiece.fx.HikenFist;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModParticles;

/**
 * Passif Logia du fruit du Feu : intangibilité élémentaire.
 *
 * <ul>
 *   <li>Les dégâts physiques classiques (mêlée, flèches, tridents et autres projectiles, chutes, cactus, enclumes...) sont annulés.
 *       Les attaques des fruits du démon du mod (Bouddha, Feu) touchent normalement : elles ne passent pas par ces types de dégâts,
 *       ou viennent d'une entité d'attaque du mod (voir {@link #isDevilFruitAttack}).</li>
 *   <li>Les projectiles traversent le corps (ProjectileMixin : ils ne le "voient" pas), sans rebondir ni se planter.</li>
 *   <li>La partie du corps touchée (tête, torse, bras ou jambe, droite ou gauche) se disperse en flammes pendant
 *       {@link #DISPERSE_TICKS} ticks : le serveur la note dans l'attachment synchronisé LOGIA_PARTS ; chez tous les joueurs, le
 *       client cache ce membre (PlayerModelMixin), le redessine sculpté dans le feu avec sa pose exacte (LogiaFlameLayer) et en
 *       fait jaillir des flammes qui restent collées au membre (LogiaClient). Pas de fumée.</li>
 * </ul>
 */
public final class LogiaIntangibility {

    // Parties du corps (bits de l'attachment LOGIA_PARTS, lus aussi par PlayerModelMixin)
    public static final int HEAD = 1, BODY = 2, RIGHT_ARM = 4, LEFT_ARM = 8, RIGHT_LEG = 16, LEFT_LEG = 32;
    private static final int PARTS = 6;

    /** Durée de la dispersion en flammes d'un membre (ticks) : 1,5 s. */
    private static final int DISPERSE_TICKS = 30;


    private static final class State {
        final long[] until = new long[PARTS]; // fin de la dispersion de chaque partie (temps de jeu)
        int lastMask;
        int lastProjectile = -1;              // évite de redéclencher l'effet à chaque tick pendant qu'une flèche traverse
        long lastProjectileTime;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private LogiaIntangibility() {
    }

    /** Le joueur a-t-il le passif Logia du feu ? L'eau l'annule : pieds dans l'eau, le corps redevient tangible. */
    public static boolean isLogia(ServerPlayer player) {
        return Fruits.get(player.getAttached(ModAttachments.FRUIT)) == Fruits.FIRE && !player.isInWater();
    }

    /**
     * Appelé avant que des dégâts soient appliqués (ModEvents) : vrai si le corps de feu les annule. Déclenche alors la
     * dispersion en flammes de la partie touchée.
     */
    public static boolean absorbs(ServerPlayer player, DamageSource source) {
        if (!isLogia(player) || !isPhysical(source) || isDevilFruitAttack(source)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            disperse(player, RIGHT_LEG | LEFT_LEG, player.position());
        } else {
            Vec3 point = hitPoint(player, source);
            disperse(player, partAt(player, point), point);
        }
        return true;
    }

    /**
     * Appelé par ProjectileMixin quand un projectile pourrait toucher ce joueur : vrai si le projectile doit le traverser (pas de
     * collision, donc ni dégâts, ni rebond, ni flèche plantée). L'effet de flammes n'est déclenché qu'une fois par projectile.
     */
    public static boolean letsThrough(ServerPlayer player, Projectile projectile) {
        if (!isLogia(player) || isDevilFruitEntity(projectile)) {
            return false;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        long now = player.level().getGameTime();
        if (state.lastProjectile != projectile.getId() || now - state.lastProjectileTime > 20) {
            Vec3 point = projectile.getBoundingBox().getCenter();
            disperse(player, partAt(player, point), point);
        }
        state.lastProjectile = projectile.getId();
        state.lastProjectileTime = now;
        return true;
    }

    /** Dégâts "physiques classiques" : mêlée, projectiles, chutes et chocs (pas le feu, la magie, les explosions, le vide...). */
    private static boolean isPhysical(DamageSource source) {
        return source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.STING)
                || source.is(DamageTypes.THORNS) || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)
                || source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_ANVIL)
                || source.is(DamageTypes.FALLING_STALACTITE) || source.is(DamageTypes.STALAGMITE)
                || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.MACE_SMASH);
    }

    /** Les attaques des fruits du démon du mod passent toujours. */
    private static boolean isDevilFruitAttack(DamageSource source) {
        return isDevilFruitEntity(source.getDirectEntity());
    }

    private static boolean isDevilFruitEntity(Entity entity) {
        return entity instanceof HikenFist || entity instanceof BuddhaPalm || entity instanceof BuddhaSeal
                || entity instanceof EnteiSun || (entity != null && FireEffects.isTracked(entity));
    }

    /** Point d'impact : le projectile, sinon l'endroit du corps visé par l'attaquant (son regard), sinon le centre du corps. */
    private static Vec3 hitPoint(ServerPlayer player, DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile) {
            return direct.getBoundingBox().getCenter();
        }
        if (source.getEntity() instanceof LivingEntity attacker) {
            Vec3 eye = attacker.getEyePosition();
            Optional<Vec3> hit = player.getBoundingBox().inflate(0.1).clip(eye, eye.add(attacker.getLookAngle().scale(8.0)));
            if (hit.isPresent()) {
                return hit.get();
            }
            double y = Math.max(player.getY(), Math.min(player.getY() + player.getBbHeight(), attacker.getEyeY()));
            return new Vec3(player.getX(), y, player.getZ());
        }
        return player.getBoundingBox().getCenter();
    }

    /** La partie du corps à cet endroit, d'après la hauteur et le côté (repère du corps du joueur, taille comprise). */
    private static int partAt(ServerPlayer player, Vec3 point) {
        double scale = player.getScale();
        Vec3 rel = point.subtract(player.position());
        double yaw = Math.toRadians(player.yBodyRot);
        double side = (rel.x * -Math.cos(yaw) + rel.z * -Math.sin(yaw)) / scale; // > 0 : côté droit du joueur
        double height = rel.y / scale;
        if (player.isCrouching()) {
            height += 0.3; // accroupi, le corps est plus bas
        }
        if (height >= 1.5) {
            return HEAD;
        }
        if (height >= 0.75) {
            return Math.abs(side) <= 0.25 ? BODY : side > 0 ? RIGHT_ARM : LEFT_ARM;
        }
        return side > 0 ? RIGHT_LEG : LEFT_LEG;
    }

    /** Disperse ces parties du corps en flammes (ou prolonge la dispersion), avec une gerbe de flammes au point d'impact. */
    private static void disperse(ServerPlayer player, int mask, Vec3 point) {
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        long until = player.level().getGameTime() + DISPERSE_TICKS;
        for (int i = 0; i < PARTS; i++) {
            if ((mask & (1 << i)) != 0) {
                state.until[i] = until;
            }
        }
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ModParticles.FIRE_FLAME, point.x, point.y, point.z, 14, 0.15, 0.15, 0.15, 0.08);
        level.sendParticles(ModParticles.FIRE_EMBER, point.x, point.y, point.z, 6, 0.1, 0.1, 0.1, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.6F);
        updateMask(player, state);
    }

    /** À appeler à chaque tick serveur (ModEvents) : reforme les membres à la fin de leur dispersion. */
    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            State state = entry.getValue();
            if (player == null) {
                it.remove();
                continue;
            }
            int mask = updateMask(player, state);
            if (mask == 0) {
                if (player.level().getGameTime() - state.lastProjectileTime > 40) {
                    it.remove();
                }
                continue;
            }
        }
    }

    /** Recalcule les parties dispersées et ne synchronise l'attachment que s'il change (pas de paquet inutile à chaque tick). */
    private static int updateMask(ServerPlayer player, State state) {
        long now = player.level().getGameTime();
        int mask = 0;
        for (int i = 0; i < PARTS; i++) {
            if (state.until[i] > now) {
                mask |= 1 << i;
            }
        }
        if (mask != state.lastMask) {
            state.lastMask = mask;
            if (mask == 0) {
                player.removeAttached(ModAttachments.LOGIA_PARTS);
            } else {
                player.setAttached(ModAttachments.LOGIA_PARTS, mask);
            }
        }
        return mask;
    }


    public static void clear() {
        STATES.clear();
    }
}
