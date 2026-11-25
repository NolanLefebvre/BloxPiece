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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.CyborgShield;
import nolan.bloxpiece.race.Races;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Race Cyborg : ce qui est commun à ses quatre attaques (CyborgCannonArmAbility, CyborgEnergyShieldAbility,
 * CyborgOverdriveAbility, CyborgRadicalBeamAbility), côté SERVEUR.
 * <ul>
 *   <li>Animations : l'attaque en cours est écrite dans l'attachment CYBORG_ACTION (début x 8 + type), synchronisé avec tous
 *       les joueurs proches : tous voient le bras se transformer, les poses, l'aura (client : CyborgArmLayer, CyborgPose).</li>
 *   <li>Une seule attaque Cyborg à la fois ({@link #busy}). Chaque attaque est une session (Goro.start, le même déroulement
 *       tick par tick que les attaques du Goro) qui s'arrête proprement si le joueur meurt, se déconnecte, change de dimension
 *       ou n'est plus Cyborg ({@link #valid}).</li>
 *   <li>Surcharge moteur : bonus temporaires (modificateurs d'attributs À PART, « bloxpiece:cyborg/overdrive_* »), puis
 *       épuisement (« bloxpiece:cyborg/exhaust_* ») pendant {@link #EXHAUST_TICKS}, sauvegardé avec le joueur. Aucun autre
 *       modificateur (armes, Haki, fruits, race, niveau) n'est touché.</li>
 *   <li>Bouclier énergétique : tant que le dôme (CyborgShield) protège son lanceur, aucun dégât ne l'atteint (sauf /kill et le
 *       vide) ; le dôme repousse lui-même les entités et renvoie les projectiles.</li>
 * </ul>
 * Toutes les valeurs (durées, recharges, dégâts, bonus) sont des constantes en haut de ce fichier et de chaque attaque.
 */
public final class Cyborg {

    // ---- Types d'attaque (animations)
    public static final int CANNON = 1, SHIELD = 2, OVERDRIVE = 3, BEAM = 4;

    // ---- Surcharge moteur : durées et bonus (configurables)
    /** Montée en puissance (animation de démarrage), puis durée de la surcharge (ticks). */
    public static final int OVERDRIVE_WINDUP = 24;
    public static final int OVERDRIVE_TICKS = 20 * 15;
    /** Épuisement après la surcharge (ticks) : 3 minutes. */
    public static final int EXHAUST_TICKS = 20 * 60 * 3;
    /** Bonus de la surcharge : dégâts (x total), vitesse (x base), vitesse d'attaque (x total), armure (+), résistance au recul (+). */
    public static final double OVERDRIVE_DAMAGE = 0.35, OVERDRIVE_SPEED = 0.30, OVERDRIVE_ATTACK_SPEED = 0.30;
    public static final double OVERDRIVE_ARMOR = 4.0, OVERDRIVE_KNOCKBACK_RESISTANCE = 0.4;
    /** Épuisement : malus (jamais paralysant) de dégâts, vitesse, vitesse d'attaque. */
    public static final double EXHAUST_DAMAGE = -0.25, EXHAUST_SPEED = -0.20, EXHAUST_ATTACK_SPEED = -0.20;

    private static final String[] BONUS_KEYS = {"damage", "speed", "attack_speed", "armor", "knockback_resistance"};
    /** Joueur en surcharge -> fin de la surcharge (temps de jeu). */
    private static final Map<UUID, Long> OVERDRIVE_END = new HashMap<>();
    /** Joueur protégé -> son dôme. */
    private static final Map<UUID, CyborgShield> SHIELDS = new HashMap<>();

    private Cyborg() {
    }

    // ------------------------------------------------------------------ état des attaques (animations synchronisées)

    public static long now(ServerPlayer player) {
        return player.level().getGameTime();
    }

    /** Début d'une attaque : tous les joueurs proches voient son animation. */
    static void setAction(ServerPlayer player, int kind) {
        player.setAttached(ModAttachments.CYBORG_ACTION, now(player) * 8 + kind);
    }

    static void clearAction(ServerPlayer player, int kind) {
        Long current = player.getAttached(ModAttachments.CYBORG_ACTION);
        if (current != null && current % 8 == kind) {
            player.removeAttached(ModAttachments.CYBORG_ACTION);
        }
    }

    /**
     * Une animation d'attaque Cyborg est-elle en cours (canon, déploiement du bouclier, démarrage de la surcharge, rayon) ? Une
     * seule à la fois. Le dôme déjà déployé et la surcharge active n'empêchent pas les autres attaques.
     */
    static boolean busy(ServerPlayer player) {
        return player.getAttached(ModAttachments.CYBORG_ACTION) != null;
    }

    /** Le joueur peut-il continuer son attaque (vivant, connecté, même dimension, toujours Cyborg) ? */
    static boolean valid(ServerPlayer player, ServerLevel level) {
        return player.isAlive() && !player.isRemoved() && !player.hasDisconnected() && player.level() == level
                && Races.of(player) == Races.CYBORG && !nolan.bloxpiece.haki.Unconscious.is(player);
    }

    /** Refus commun : déjà une attaque Cyborg en cours. */
    static boolean refuseBusy(ServerPlayer player) {
        if (busy(player)) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.cyborg.busy"), true);
            return true;
        }
        return false;
    }

    /** Point devant le bras gauche (bouche du canon) : à hauteur d'épaule, dans l'axe du regard, côté gauche. */
    static Vec3 leftMuzzle(ServerPlayer player, double forward) {
        Vec3 look = player.getLookAngle();
        double yaw = Math.toRadians(player.getYRot());
        Vec3 left = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
        return player.getEyePosition().add(0.0, -0.35, 0.0).add(left.scale(0.36)).add(look.scale(forward));
    }

    /** Point entre les deux mains tendues devant le joueur (Radical Beam). */
    static Vec3 handsFront(ServerPlayer player, double forward) {
        return player.getEyePosition().add(0.0, -0.35, 0.0).add(player.getLookAngle().scale(forward));
    }

    // ------------------------------------------------------------------ surcharge moteur et épuisement

    static void startOverdrive(ServerPlayer player) {
        OVERDRIVE_END.put(player.getUUID(), now(player) + OVERDRIVE_TICKS);
        player.setAttached(ModAttachments.CYBORG_OVERDRIVE, now(player)); // aura visible par tous
        double[] bonus = {OVERDRIVE_DAMAGE, OVERDRIVE_SPEED, OVERDRIVE_ATTACK_SPEED, OVERDRIVE_ARMOR, OVERDRIVE_KNOCKBACK_RESISTANCE};
        setModifiers(player, "overdrive", bonus);
    }

    /** Fin de la surcharge (durée écoulée, mort, déconnexion...) : bonus retirés, épuisement posé. */
    static void endOverdrive(ServerPlayer player, boolean exhaust) {
        if (OVERDRIVE_END.remove(player.getUUID()) == null) {
            return;
        }
        setModifiers(player, "overdrive", new double[BONUS_KEYS.length]);
        player.removeAttached(ModAttachments.CYBORG_OVERDRIVE);
        if (exhaust && player.level().getServer() != null) {
            long until = player.level().getServer().overworld().getGameTime() + EXHAUST_TICKS;
            player.setAttached(ModAttachments.CYBORG_EXHAUSTED_UNTIL, until);
            applyExhaust(player);
            player.sendSystemMessage(Component.translatable("message.bloxpiece.cyborg.exhausted", EXHAUST_TICKS / 20 / 60)
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    public static boolean overdriveActive(ServerPlayer player) {
        return OVERDRIVE_END.containsKey(player.getUUID());
    }

    /** Malus d'épuisement : posés tant que l'épuisement dure (sauvegardé : même après une reconnexion), retirés ensuite. */
    private static void applyExhaust(ServerPlayer player) {
        Long until = player.getAttached(ModAttachments.CYBORG_EXHAUSTED_UNTIL);
        boolean active = until != null && player.level().getServer() != null
                && player.level().getServer().overworld().getGameTime() < until;
        if (!active && until != null) {
            player.removeAttached(ModAttachments.CYBORG_EXHAUSTED_UNTIL);
            player.sendSystemMessage(Component.translatable("message.bloxpiece.cyborg.recovered").withStyle(ChatFormatting.GREEN), true);
        }
        double[] malus = active ? new double[] {EXHAUST_DAMAGE, EXHAUST_SPEED, EXHAUST_ATTACK_SPEED, 0.0, 0.0}
                : new double[BONUS_KEYS.length];
        setModifiers(player, "exhaust", malus);
    }

    /** Pose (ou retire, valeur 0) les modificateurs « bloxpiece:cyborg/&lt;groupe&gt;_&lt;stat&gt; » : rien d'autre n'est touché. */
    private static void setModifiers(ServerPlayer player, String group, double[] values) {
        @SuppressWarnings("unchecked")
        Holder<Attribute>[] attributes = new Holder[] {Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED, Attributes.ATTACK_SPEED,
                Attributes.ARMOR, Attributes.KNOCKBACK_RESISTANCE};
        AttributeModifier.Operation[] ops = {AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                AttributeModifier.Operation.ADD_VALUE, AttributeModifier.Operation.ADD_VALUE};
        for (int i = 0; i < attributes.length; i++) {
            AttributeInstance instance = player.getAttribute(attributes[i]);
            if (instance == null) {
                continue;
            }
            Identifier id = BloxPiece.id("cyborg/" + group + "_" + BONUS_KEYS[i]);
            AttributeModifier current = instance.getModifier(id);
            if (current != null && current.amount() == values[i]) {
                continue;
            }
            if (current != null) {
                instance.removeModifier(id);
            }
            if (values[i] != 0.0) {
                instance.addTransientModifier(new AttributeModifier(id, values[i], ops[i]));
            }
        }
    }

    // ------------------------------------------------------------------ bouclier

    static void shieldUp(ServerPlayer player, CyborgShield shield) {
        SHIELDS.put(player.getUUID(), shield);
    }

    public static void shieldDown(UUID owner) {
        SHIELDS.remove(owner);
    }

    /** Le joueur est-il à l'abri dans son dôme (dôme vivant et lui dedans) ? */
    public static boolean shielded(ServerPlayer player) {
        CyborgShield shield = SHIELDS.get(player.getUUID());
        return shield != null && shield.isAlive() && shield.protects(player);
    }

    // ------------------------------------------------------------------ événements

    public static void initialize() {
        // Bouclier : AUCUN dégât n'atteint le lanceur à l'abri (coups, projectiles, explosions, pouvoirs...), sauf /kill et le vide
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && shielded(player)));
        ServerTickEvents.END_SERVER_TICK.register(Cyborg::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            handler.getPlayer().removeAttached(ModAttachments.CYBORG_ACTION); // animation d'une ancienne session : effacée
            handler.getPlayer().removeAttached(ModAttachments.CYBORG_OVERDRIVE);
            applyExhaust(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stopAll(handler.getPlayer(), true));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            stopAll(oldPlayer, true);
            newPlayer.removeAttached(ModAttachments.CYBORG_ACTION);
            newPlayer.removeAttached(ModAttachments.CYBORG_OVERDRIVE);
            applyExhaust(newPlayer); // l'épuisement continue après la mort (attachment conservé)
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                stopAll(player, true);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            OVERDRIVE_END.clear();
            SHIELDS.clear();
        });
    }

    /** Coupe tout ce qui est en cours pour ce joueur (mort, déconnexion) : surcharge -> épuisement, bouclier, animation. */
    private static void stopAll(ServerPlayer player, boolean exhaust) {
        endOverdrive(player, exhaust);
        CyborgShield shield = SHIELDS.remove(player.getUUID());
        if (shield != null) {
            shield.collapse();
        }
        player.removeAttached(ModAttachments.CYBORG_ACTION);
        player.removeAttached(ModAttachments.CYBORG_OVERDRIVE);
    }

    private static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Long end = OVERDRIVE_END.get(player.getUUID());
            if (end != null) {
                if (Races.of(player) != Races.CYBORG || now(player) >= end) {
                    endOverdrive(player, true);
                } else if (player.level() instanceof ServerLevel level && tick % 4 == 0) {
                    // surcharge : quelques étincelles qui tombent du corps (les effets 3D : CyborgOverdriveLayer, côté client)
                    Vec3 c = player.position().add(0.0, 1.0, 0.0);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 2, 0.3, 0.5, 0.3, 0.12);
                }
            }
            if (tick % 20 == 0 && player.getAttached(ModAttachments.CYBORG_EXHAUSTED_UNTIL) != null) {
                applyExhaust(player);
            }
            CyborgShield shield = SHIELDS.get(player.getUUID());
            if (shield != null && !shield.isAlive()) {
                SHIELDS.remove(player.getUUID());
            }
        }
    }
}
