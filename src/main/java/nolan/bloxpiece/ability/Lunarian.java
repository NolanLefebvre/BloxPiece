package nolan.bloxpiece.ability;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.race.Races;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Race Lunarian : état commun, côté serveur.
 * <ul>
 *   <li>MODES : par défaut, mode VITESSE (flammes du dos éteintes : bonus de vitesse de déplacement). La première capacité
 *       (LunarianFlamesAbility) allume les flammes : mode DÉFENSE (armure, robustesse, résistance au recul en plus, dégâts
 *       reçus réduits) au prix d'une forte réduction de TOUS les dégâts infligés (coups, fruits, attaques de race, projectiles).
 *       Bonus d'attributs : modificateurs transitoires propres à la race (identifiants « bloxpiece:lunarian/... »), ajoutés ou
 *       retirés sans toucher aux autres (équipement, Haki, fruits...). Dégâts : multiplicateurs appliqués au seul point de
 *       passage des dégâts du mod (LivingEntityMixin.hurtServer), donc cumulables avec tout le reste. Toutes les valeurs sont
 *       ici (configurables).</li>
 *   <li>L'attaque en cours (LUNARIAN_ACTION) et le mode (LUNARIAN_FLAME) sont synchronisés avec tous les joueurs : ailes,
 *       flammes et poses identiques pour tous.</li>
 *   <li>Mort, déconnexion, réapparition, changement de dimension ou de race : attaque interrompue, gravité rendue, retour au
 *       mode Vitesse, modificateurs retirés.</li>
 * </ul>
 */
public final class Lunarian {

    /** Types d'attaque (animations). */
    public static final int FLAMES = 1, FEATHERS = 2, FIST = 3, JUDGMENT = 4;
    /** Récupérations (leur début dépend du déroulement : fin du dash, impact au sol). */
    public static final int FIST_END = 5, JUDGMENT_END = 6;

    // ---- réglages des modes (configurables)
    /** Mode Vitesse : vitesse de déplacement en plus (x base). */
    public static final double SPEED_BONUS = 0.15;
    /** Mode Défense : armure, robustesse d'armure, résistance au recul en plus. */
    public static final double DEFENSE_ARMOR = 8.0, DEFENSE_TOUGHNESS = 4.0, DEFENSE_KNOCKBACK = 0.3;
    /** Mode Défense : dégâts REÇUS multipliés par... (forte résistance) et dégâts INFLIGÉS multipliés par... (forte pénalité). */
    public static final float DEFENSE_TAKEN = 0.55F, DEFENSE_DEALT = 0.35F;
    /** Durée de l'apparition / de l'extinction des flammes (ticks). */
    public static final int TRANSITION = 16;

    private static final Identifier SPEED_ID = BloxPiece.id("lunarian/speed_mode");
    private static final Identifier ARMOR_ID = BloxPiece.id("lunarian/defense_armor");
    private static final Identifier TOUGH_ID = BloxPiece.id("lunarian/defense_toughness");
    private static final Identifier KNOCK_ID = BloxPiece.id("lunarian/defense_knockback");

    private Lunarian() {
    }

    public static long now(ServerPlayer player) {
        return player.level().getGameTime();
    }

    // ------------------------------------------------------------------ attaques en cours

    static void setAction(ServerPlayer player, int kind) {
        player.setAttached(ModAttachments.LUNARIAN_ACTION, now(player) * 8 + kind);
    }

    static void clearAction(ServerPlayer player, int kind) {
        Long current = player.getAttached(ModAttachments.LUNARIAN_ACTION);
        if (current != null && current % 8 == kind) {
            player.removeAttached(ModAttachments.LUNARIAN_ACTION);
        }
    }

    static boolean refuseBusy(ServerPlayer player) {
        if (player.getAttached(ModAttachments.LUNARIAN_ACTION) != null) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.lunarian.busy"), true);
            return true;
        }
        return false;
    }

    static boolean isLunarian(ServerPlayer player) {
        return Races.of(player) == Races.LUNARIAN;
    }

    /** Le joueur peut-il continuer (vivant, connecté, même dimension, toujours Lunarian, conscient, pas menotté) ? */
    static boolean valid(ServerPlayer player, ServerLevel level) {
        return player.isAlive() && !player.isRemoved() && !player.hasDisconnected() && player.level() == level && isLunarian(player)
                && !nolan.bloxpiece.haki.Unconscious.is(player) && !nolan.bloxpiece.handcuffs.Handcuffs.isCuffed(player);
    }

    // ------------------------------------------------------------------ modes Défense / Vitesse

    /** Mode Défense demandé (flammes allumées ou en train de s'allumer) ? */
    public static boolean flamesOn(ServerPlayer player) {
        Long v = player.getAttached(ModAttachments.LUNARIAN_FLAME);
        return v != null && (v & 1L) == 1L;
    }

    /**
     * Mode Défense ACTIF (bonus et pénalité appliqués) ? Une fois les flammes complètement allumées, et jusqu'à ce qu'elles soient
     * complètement éteintes.
     */
    public static boolean defense(ServerPlayer player) {
        Long v = player.getAttached(ModAttachments.LUNARIAN_FLAME);
        if (v == null || !isLunarian(player)) {
            return false;
        }
        long since = now(player) - (v >> 1);
        return (v & 1L) == 1L ? since >= TRANSITION : since < TRANSITION;
    }

    /** Commence la transition (les flammes grandissent ou s'éteignent) ; les modificateurs suivent à la fin (voir {@link #tick}). */
    static void setFlames(ServerPlayer player, boolean on) {
        player.setAttached(ModAttachments.LUNARIAN_FLAME, now(player) * 2 + (on ? 1 : 0));
    }

    /** Remet les modificateurs du joueur exactement à ceux de son mode (aucun s'il n'est pas Lunarian). */
    static void applyModifiers(ServerPlayer player) {
        boolean lunar = isLunarian(player);
        boolean def = defense(player);
        set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, lunar && !def ? SPEED_BONUS : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.ARMOR, ARMOR_ID, def ? DEFENSE_ARMOR : 0.0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ARMOR_TOUGHNESS, TOUGH_ID, def ? DEFENSE_TOUGHNESS : 0.0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCK_ID, def ? DEFENSE_KNOCKBACK : 0.0, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current != null && current.amount() == amount && current.operation() == op) {
            return;
        }
        if (current != null) {
            instance.removeModifier(id);
        }
        if (amount != 0.0) {
            instance.addTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    /** Dégâts INFLIGÉS par un Lunarian en mode Défense : multipliés par {@link #DEFENSE_DEALT} (tous les types de dégâts). */
    public static float dealtMultiplier(DamageSource source, LivingEntity target) {
        if (source.getEntity() instanceof ServerPlayer player && player != target && defense(player)) {
            return DEFENSE_DEALT;
        }
        return 1.0F;
    }

    /** Dégâts REÇUS par un Lunarian en mode Défense : multipliés par {@link #DEFENSE_TAKEN}. */
    public static float takenMultiplier(LivingEntity target) {
        return target instanceof ServerPlayer player && defense(player) ? DEFENSE_TAKEN : 1.0F;
    }

    // ------------------------------------------------------------------ nettoyage

    /** Tout s'arrête : attaque, gravité, retour au mode Vitesse, modificateurs remis selon la race. */
    static void reset(ServerPlayer player) {
        player.removeAttached(ModAttachments.LUNARIAN_ACTION);
        player.removeAttached(ModAttachments.LUNARIAN_FLAME);
        player.setNoGravity(false);
        applyModifiers(player);
    }

    public static void initialize() {
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
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
            player.removeAttached(ModAttachments.LUNARIAN_ACTION);
            player.setNoGravity(false);
        });
        ServerTickEvents.END_SERVER_TICK.register(Lunarian::tick);
    }

    /** Chaque tick : modificateurs du mode (fin de transition, changement de race) ; plus de flammes si plus Lunarian. */
    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isLunarian(player) && player.getAttached(ModAttachments.LUNARIAN_FLAME) != null) {
                player.removeAttached(ModAttachments.LUNARIAN_FLAME);
            }
            applyModifiers(player);
        }
    }
}
