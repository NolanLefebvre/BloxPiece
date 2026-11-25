package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.MinksFx;
import nolan.bloxpiece.race.Races;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Race Minks : ce qui est commun à ses quatre attaques (MinksClawsAbility, MinksBarrageAbility, MinksDanceAbility,
 * MinksSulongAbility), côté SERVEUR. Même organisation que les races Cyborg et Humaine :
 * <ul>
 *   <li>Animations : l'attaque en cours est écrite dans MINKS_ACTION (début x 8 + type ; les types « _S » sont les versions
 *       Sulong, plus longues), synchronisé avec tous les joueurs proches (client : MinksPose, MinksLayer).</li>
 *   <li>Griffes électriques : MINKS_CLAWS = fin (temps de jeu) des griffes, synchronisé. Tant qu'elles sont sorties, chaque coup
 *       de poing À MAINS NUES (main principale vide, coup de joueur direct) ajoute des dégâts électriques et une courte
 *       paralysie ({@link #onMelee}). Une arme ou un objet en main : rien n'est ajouté.</li>
 *   <li>Paralysie : celle du Goro Goro no Mi (Goro.stun : immobile, n'attaque plus, étincelles), limitée par cible
 *       ({@link #STUN_COOLDOWN}) : jamais d'immobilisation permanente.</li>
 *   <li>Sulong : MINKS_SULONG = début de la transformation, synchronisé ; bonus d'attributs À PART
 *       (« bloxpiece:minks/sulong_* »), retirés à la fin (durée, mort, déconnexion, changement de race). Pendant Sulong, les
 *       trois autres attaques sont renforcées ({@link #power}).</li>
 * </ul>
 */
public final class Minks {

    // ---- Types d'attaque (animations) ; _S : version Sulong (plus de coups / de bonds)
    public static final int CLAWS = 1, BARRAGE = 2, DANCE = 3, SULONG = 4, BARRAGE_S = 5, DANCE_S = 6;

    // ---- Griffes électriques (configurables)
    /** Durée des griffes sorties (ticks). */
    public static final int CLAWS_TICKS = 20 * 20;
    /** Bonus de dégâts électriques d'un coup à mains nues, et paralysie (ticks). */
    public static final float CLAW_BONUS = 4.0F;
    public static final int CLAW_STUN = 12;
    /** Délai minimal (ticks) entre deux paralysies d'une même cible, toutes attaques Minks confondues. */
    public static final int STUN_COOLDOWN = 40;

    // ---- Sulong (configurables)
    public static final int SULONG_TICKS = 20 * 30;
    /** Multiplicateur des attaques Minks pendant Sulong (dégâts, paralysie). */
    public static final float SULONG_POWER = 1.5F;
    /** Bonus : dégâts (x total), vitesse (x base), vitesse d'attaque (x total), armure (+), robustesse (+), résistance au recul (+). */
    public static final double SULONG_DAMAGE = 0.5, SULONG_SPEED = 0.35, SULONG_ATTACK_SPEED = 0.4;
    public static final double SULONG_ARMOR = 6.0, SULONG_TOUGHNESS = 4.0, SULONG_KNOCKBACK_RESISTANCE = 0.3;

    private static final String[] BONUS_KEYS = {"damage", "speed", "attack_speed", "armor", "toughness", "knockback_resistance"};
    /** Joueur en Sulong -> fin (temps de jeu). */
    private static final Map<UUID, Long> SULONG_END = new HashMap<>();
    /** Cible -> dernier instant (temps de jeu) où une attaque Minks l'a paralysée. */
    private static final Map<UUID, Long> LAST_STUN = new HashMap<>();
    /** Joueur -> diagonale de sa dernière série de griffures (elles alternent d'une frappe à l'autre). */
    private static final Map<UUID, Boolean> SLASH_SIDE = new HashMap<>();
    /** Garde : le bonus des griffes est lui-même un coup (ne doit pas se redéclencher). */
    private static boolean inBonus;

    private Minks() {
    }

    // ------------------------------------------------------------------ état des attaques (animations synchronisées)

    public static long now(ServerPlayer player) {
        return player.level().getGameTime();
    }

    static void setAction(ServerPlayer player, int kind) {
        player.setAttached(ModAttachments.MINKS_ACTION, now(player) * 8 + kind);
    }

    static void clearAction(ServerPlayer player) {
        player.removeAttached(ModAttachments.MINKS_ACTION);
    }

    static boolean busy(ServerPlayer player) {
        return player.getAttached(ModAttachments.MINKS_ACTION) != null;
    }

    static boolean refuseBusy(ServerPlayer player) {
        if (busy(player)) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.minks.busy"), true);
            return true;
        }
        return false;
    }

    /** Le joueur peut-il continuer (vivant, connecté, même dimension, toujours Minks, conscient, pas menotté) ? */
    static boolean valid(ServerPlayer player, ServerLevel level) {
        return player.isAlive() && !player.isRemoved() && !player.hasDisconnected() && player.level() == level
                && Races.of(player) == Races.MINKS && !nolan.bloxpiece.haki.Unconscious.is(player)
                && !nolan.bloxpiece.handcuffs.Handcuffs.isCuffed(player);
    }

    /** Puissance des attaques Minks : x {@link #SULONG_POWER} en Sulong, x 1 sinon. */
    public static float power(ServerPlayer player) {
        return sulong(player) ? SULONG_POWER : 1.0F;
    }

    public static boolean sulong(ServerPlayer player) {
        return SULONG_END.containsKey(player.getUUID());
    }

    // ------------------------------------------------------------------ griffes

    /** Sort les griffes électriques jusqu'à {@code now + ticks} (prolonge si elles sont déjà sorties). */
    static void showClaws(ServerPlayer player, int ticks) {
        Long until = player.getAttached(ModAttachments.MINKS_CLAWS);
        long end = now(player) + ticks;
        if (until == null || until < end) {
            player.setAttached(ModAttachments.MINKS_CLAWS, end);
        }
    }

    static boolean clawsOut(ServerPlayer player) {
        Long until = player.getAttached(ModAttachments.MINKS_CLAWS);
        return until != null && now(player) < until;
    }

    /**
     * Paralysie électrique (celle du Goro : Goro.stun), si la cible n'a pas été paralysée par une attaque Minks depuis
     * {@link #STUN_COOLDOWN} ticks. Renvoie vrai si elle l'est.
     */
    static boolean tryStun(LivingEntity target, int ticks) {
        long t = target.level().getGameTime();
        Long last = LAST_STUN.get(target.getUUID());
        if (last != null && t - last < STUN_COOLDOWN) {
            return false;
        }
        LAST_STUN.put(target.getUUID(), t);
        Goro.stun(target, ticks);
        return true;
    }

    /**
     * Un coup de griffe (attaques Minks) : dégâts de coup de joueur, recul léger, paralysie limitée. L'invulnérabilité du coup
     * précédent est levée (les coups s'enchaînent très vite) : le nombre de coups par cible est limité par chaque attaque.
     */
    static void claw(ServerLevel level, ServerPlayer owner, LivingEntity target, float damage, Vec3 push, int stun) {
        target.setLastHurtByMob(owner);
        target.setInvulnerableTime(0);
        inBonus = true;
        try {
            target.hurtServer(level, level.damageSources().playerAttack(owner), damage);
        } finally {
            inBonus = false;
        }
        target.push(push.x, push.y, push.z);
        if (target instanceof ServerPlayer p) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
        }
        if (stun > 0) {
            tryStun(target, stun);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 10,
                target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.2);
    }

    /**
     * Après chaque coup reçu : si c'est un coup de poing À MAINS NUES d'un Minks aux griffes sorties, il ajoute ses dégâts
     * électriques (calculés ici, côté serveur) et une courte paralysie. Une arme, un objet en main : rien.
     */
    private static void onMelee(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float taken) {
        if (inBonus || !(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player
                || !source.is(DamageTypes.PLAYER_ATTACK) || !player.getMainHandItem().isEmpty() || !clawsOut(player)
                || Races.of(player) != Races.MINKS || !(target.level() instanceof ServerLevel level) || !BuddhaHits.canHit(target, player)) {
            return;
        }
        float p = power(player);
        Vec3 push = target.position().subtract(player.position());
        push = new Vec3(push.x, 0.0, push.z);
        push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(0.15);
        claw(level, player, target, CLAW_BONUS * p, push.add(0.0, 0.05, 0.0), (int) (CLAW_STUN * p));
        Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
        Vec3 dir = at.subtract(player.getEyePosition());
        slash(level, player, MinksFx.IMPACT, at, dir.lengthSqr() < 1.0E-4 ? player.getLookAngle() : dir.normalize(), 0.8F * p, 12);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.25F, 1.9F);
    }

    /**
     * Pose l'effet d'une frappe de griffes (IMPACT, BARRAGE_HIT, FINAL) en ALTERNANT la diagonale des trois griffures à chaque
     * frappe de ce joueur (« \ » puis « / »), même pour des frappes très rapprochées : décidé ici, côté serveur, et synchronisé
     * avec tous les joueurs. ENTRECROISEMENT : les frappes vont par paires ; la seconde d'une paire se pose au centre exact de
     * la première (même point, même plan) si celle-ci est récente et proche, et la première est prolongée : les deux séries de
     * trois griffures forment une marque en X. La frappe suivante commence une nouvelle paire.
     */
    static MinksFx slash(ServerLevel level, ServerPlayer player, int kind, Vec3 at, Vec3 dir, float size, int life) {
        boolean flip = !SLASH_SIDE.getOrDefault(player.getUUID(), Boolean.TRUE);
        SLASH_SIDE.put(player.getUUID(), flip);
        MinksFx first = PAIR_START.remove(player.getUUID());
        if (kind == MinksFx.FINAL) { // coup final : il dessine lui-même son X complet (deux séries), sans s'empiler sur une paire
            return MinksFx.spawn(level, player, kind, at, dir, size, life).flip(flip);
        }
        if (first != null && first.isAlive() && first.level() == level && first.tickCount <= CROSS_WINDOW
                && first.position().distanceTo(at) <= CROSS_DISTANCE) {
            // seconde frappe de la paire : sur la première, dans son plan ; la première reste visible le temps du X
            float[] d = first.reach();
            first.extend(first.tickCount + life);
            return MinksFx.spawn(level, player, kind, first.position(), new Vec3(d[0], d[1], d[2]), size, life).flip(flip);
        }
        MinksFx fx = MinksFx.spawn(level, player, kind, at, dir, size, life).flip(flip);
        PAIR_START.put(player.getUUID(), fx); // première frappe d'une nouvelle paire
        return fx;
    }

    /** Délai (ticks) et distance (blocs) maximaux pour que deux frappes se croisent sur la même marque. */
    private static final int CROSS_WINDOW = 16;
    private static final double CROSS_DISTANCE = 1.8;
    /** Joueur -> première frappe de la paire en cours (attend sa frappe croisée). */
    private static final Map<UUID, MinksFx> PAIR_START = new HashMap<>();

    // ------------------------------------------------------------------ Sulong

    static void startSulong(ServerPlayer player) {
        SULONG_END.put(player.getUUID(), now(player) + SULONG_TICKS);
        player.setAttached(ModAttachments.MINKS_SULONG, now(player));
        setModifiers(player, new double[] {SULONG_DAMAGE, SULONG_SPEED, SULONG_ATTACK_SPEED, SULONG_ARMOR, SULONG_TOUGHNESS,
                SULONG_KNOCKBACK_RESISTANCE});
    }

    /** Fin de Sulong : bonus retirés (eux seuls), apparence normale ; {@code announce} : message et petite décharge. */
    static void endSulong(ServerPlayer player, boolean announce) {
        boolean was = SULONG_END.remove(player.getUUID()) != null;
        setModifiers(player, new double[BONUS_KEYS.length]);
        player.removeAttached(ModAttachments.MINKS_SULONG);
        if (was && announce && player.level() instanceof ServerLevel level) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.minks.sulong_end").withStyle(ChatFormatting.GRAY), true);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.7, 0.4, 0.3);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
    }

    /** Pose (ou retire, valeur 0) les modificateurs « bloxpiece:minks/sulong_&lt;stat&gt; » : aucun autre n'est touché. */
    private static void setModifiers(ServerPlayer player, double[] values) {
        @SuppressWarnings("unchecked")
        Holder<Attribute>[] attributes = new Holder[] {Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED, Attributes.ATTACK_SPEED,
                Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE};
        AttributeModifier.Operation[] ops = {AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, AttributeModifier.Operation.ADD_VALUE, AttributeModifier.Operation.ADD_VALUE,
                AttributeModifier.Operation.ADD_VALUE};
        for (int i = 0; i < attributes.length; i++) {
            AttributeInstance instance = player.getAttribute(attributes[i]);
            if (instance == null) {
                continue;
            }
            Identifier id = BloxPiece.id("minks/sulong_" + BONUS_KEYS[i]);
            if (instance.getModifier(id) != null) {
                instance.removeModifier(id);
            }
            if (values[i] != 0.0) {
                instance.addTransientModifier(new AttributeModifier(id, values[i], ops[i]));
            }
        }
    }

    // ------------------------------------------------------------------ événements

    public static void initialize() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (!blocked && taken > 0.0F) {
                onMelee(entity, source, taken);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Minks::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> reset(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            reset(oldPlayer);
            reset(newPlayer);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                reset(player);
            }
            LAST_STUN.remove(entity.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SULONG_END.clear();
            SLASH_SIDE.clear();
            PAIR_START.clear();
            LAST_STUN.clear();
        });
    }

    /** Tout ce qui est en cours pour ce joueur s'arrête (connexion, déconnexion, mort) : rien ne reste actif. */
    private static void reset(ServerPlayer player) {
        endSulong(player, false);
        player.removeAttached(ModAttachments.MINKS_ACTION);
        player.removeAttached(ModAttachments.MINKS_CLAWS);
        player.removeAttached(ModAttachments.MINKS_MOVE);
        player.setNoGravity(false);
        SLASH_SIDE.remove(player.getUUID());
        PAIR_START.remove(player.getUUID());
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Long end = SULONG_END.get(player.getUUID());
            if (end != null) {
                if (Races.of(player) != Races.MINKS || now(player) >= end) {
                    endSulong(player, true);
                } else if (player.level() instanceof ServerLevel level && server.getTickCount() % 3 == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.35, 0.6, 0.35, 0.1);
                }
            }
            Long claws = player.getAttached(ModAttachments.MINKS_CLAWS);
            if (claws != null && (now(player) >= claws || Races.of(player) != Races.MINKS)) {
                player.removeAttached(ModAttachments.MINKS_CLAWS);
            }
        }
        if (server.getTickCount() % 200 == 0 && !LAST_STUN.isEmpty()) {
            long t = server.overworld().getGameTime();
            LAST_STUN.values().removeIf(v -> t - v > 400);
        }
    }
}
