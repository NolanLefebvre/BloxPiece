package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.fx.KameShellEntity;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModParticles;

/**
 * Kame Kame no Mi (fruit de la Tortue) : tout l'état côté serveur, partagé par ses trois attaques (KameShellAbility,
 * KamePocketAbility, KameSpinAbility). L'état vit dans des attachments synchronisés (le rendu de chaque client en découle) :
 * <ul>
 *   <li>KAME_SHELL : carapace portée (pousse en {@link #GROW_TICKS}, rentre en {@link #SHRINK_TICKS}) ; tant qu'elle est
 *       portée, bonus d'armure ({@link #ARMOR}, {@link #TOUGHNESS} : réduction modérée des dégâts), par un modificateur
 *       d'attribut à identifiant fixe (jamais cumulé, retiré dès qu'elle n'est plus portée, synchronisé par Minecraft) ;</li>
 *   <li>KAME_RETRACT / KAME_RETRACT_KIND : membres rentrés (1 = vers la poche, lent ; 2 = carapace tournoyante, rapide ;
 *       3 = les membres ressortent) ;</li>
 *   <li>KAME_RETURN : le joueur est dans sa dimension de poche, sa carapace (KameShellEntity) l'attend dans le monde.</li>
 * </ul>
 * Dimension de poche « bloxpiece:kame_pocket » (vide, data/bloxpiece/dimension) : chaque joueur y a son CUBE fermé en bedrock
 * (KAME_PLOT), à {@link #PLOT_SPACING} blocs des autres ; on ne peut casser ou poser des blocs qu'à l'intérieur de son propre
 * cube. Les constructions sont sauvegardées avec le monde, comme partout.
 * Mort, déconnexion, changement de fruit, eau... : rien ne reste bloqué (voir {@link #reset}).
 */
public final class Kame {

    // ------------------------------------------------------------------ réglages
    public static final int GROW_TICKS = 30;
    public static final int SHRINK_TICKS = 15;
    /**
     * Vers la poche : bras écartés (presque en T) en {@link #POCKET_SPREAD_TICKS}, tenue jusqu'à {@link #POCKET_SUCK_AT}, puis
     * tête, bras et jambes ASPIRÉS d'un coup dans la carapace jusqu'à {@link #POCKET_RETRACT_TICKS}.
     */
    public static final int POCKET_SPREAD_TICKS = 10;
    public static final int POCKET_SUCK_AT = 14;
    public static final int POCKET_RETRACT_TICKS = 20;
    /**
     * Carapace tournoyante : même entrée que vers la poche (bras écartés, puis « ploup ») ; elle commence à tourner une fois
     * tout rentré. À la fin, même éjection des membres que la sortie de la poche (KIND_EXIT).
     */
    public static final int SPIN_RETRACT_TICKS = POCKET_RETRACT_TICKS;
    public static final int EMERGE_TICKS = 8;
    /** Sortie de la poche : la carapace posée se redresse jusqu'au torse ({@link #RISE_TICKS}), puis les membres en sortent. */
    public static final int RISE_TICKS = 16;
    public static final int EXIT_EMERGE_TICKS = 12;
    /** Carapace tournoyante : durée (ajustable), vitesse (blocs/tick), dégâts, délai entre deux coups sur une même cible, recul. */
    public static final int SPIN_TICKS = 130;
    /** Saut de la carapace tournoyante (touche de saut) : élan vertical (0,9 : ~4 blocs) ; petit bond automatique contre une marche. */
    public static final double SPIN_JUMP = 0.9;
    public static final double SPIN_HOP = 0.5;
    public static final double SPIN_SPEED = 0.75;
    public static final float SPIN_DAMAGE = 8.0F;
    public static final int SPIN_HIT_COOLDOWN = 10;
    public static final double SPIN_KNOCKBACK = 0.9;
    /** Bonus de la carapace portée : armure et robustesse (une plastron de fer donne 6 d'armure). */
    public static final double ARMOR = 6.0;
    public static final double TOUGHNESS = 2.0;
    /**
     * Dimension de poche : un CUBE fermé en bedrock par joueur (dimension vide autour). Intérieur de {@link #CUBE_INNER} blocs de
     * côté et {@link #CUBE_HEIGHT} de haut, sol à y = {@link #CUBE_FLOOR}. Les cubes sont à {@link #PLOT_SPACING} blocs les uns
     * des autres.
     */
    public static final int PLOT_SPACING = 4096;
    public static final int CUBE_INNER = 21;
    public static final int CUBE_HEIGHT = 16;
    public static final int CUBE_FLOOR = 0;

    /**
     * Rétractions : 1 = vers la poche ; 2 = carapace tournoyante ; 3 = les membres ressortent (vite) ; 4 = caché, la carapace posée
     * se redresse (sortie de la poche) ; 5 = les membres ressortent de la carapace (sortie de la poche, plus lentement).
     */
    public static final int KIND_POCKET = 1, KIND_SPIN = 2, KIND_EMERGE = 3, KIND_HIDDEN = 4, KIND_EXIT = 5;

    public static final ResourceKey<Level> POCKET = ResourceKey.create(Registries.DIMENSION, BloxPiece.id("kame_pocket"));
    private static final Identifier ARMOR_ID = BloxPiece.id("kame_shell_armor");

    /** Derniers coups de la carapace tournoyante : joueur -> (cible -> tick du coup). */
    private static final Map<UUID, Map<Integer, Long>> HITS = new HashMap<>();
    /** Vitesse verticale de la carapace tournoyante (saut, chute), tenue par le serveur. */
    private static final Map<UUID, Double> SPIN_VY = new HashMap<>();
    /** Sortie de la poche en cours : joueur -> carapace posée qu'il rejoint (UUID). */
    private static final Map<UUID, UUID> EXITING = new HashMap<>();

    private Kame() {
    }

    // ------------------------------------------------------------------ état

    /** Carapace portée (en train de pousser ou poussée ; pas en train de rentrer). */
    public static boolean hasShell(ServerPlayer player) {
        Integer v = player.getAttached(ModAttachments.KAME_SHELL);
        return v != null && v > 0;
    }

    /** Carapace entièrement poussée (les attaques qui s'en servent l'exigent). */
    public static boolean shellReady(ServerPlayer player) {
        Integer v = player.getAttached(ModAttachments.KAME_SHELL);
        return v != null && v > 0 && player.level().getGameTime() - v >= GROW_TICKS;
    }

    public static boolean retracted(ServerPlayer player) {
        return player.getAttached(ModAttachments.KAME_RETRACT) != null;
    }

    public static boolean inPocket(ServerPlayer player) {
        return player.getAttached(ModAttachments.KAME_RETURN) != null;
    }

    static void growShell(ServerPlayer player) {
        player.setAttached(ModAttachments.KAME_SHELL, (int) Math.max(1L, player.level().getGameTime()));
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.TURTLE_EGG_CRACK, SoundSource.PLAYERS,
                1.0F, 0.6F);
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.TURTLE_SHAMBLE,
                SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    static void shrinkShell(ServerPlayer player) {
        player.setAttached(ModAttachments.KAME_SHELL, (int) -Math.max(1L, player.level().getGameTime()));
        player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.TURTLE_SHAMBLE,
                SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    /**
     * Pouvoirs coupés (eau : WaterCurse ; menottes ou Granite Marin : Handcuffs ; comme la détransformation du Bouddha) : la
     * carapace disparaît tout de suite, et une
     * carapace tournoyante ou une rétraction vers la poche s'interrompt (les membres ressortent). Une sortie de poche en cours
     * n'est pas coupée (la carapace lui est rendue à la fin, puis retirée au tick suivant).
     */
    public static void cancelPowers(ServerPlayer player) {
        Integer kind = player.getAttached(ModAttachments.KAME_RETRACT_KIND);
        if (kind != null && (kind == KIND_HIDDEN || kind == KIND_EXIT)) {
            return;
        }
        if (kind != null && (kind == KIND_POCKET || kind == KIND_SPIN)) {
            endRetract(player);
            retract(player, KIND_EXIT);
        }
        Integer shell = player.getAttached(ModAttachments.KAME_SHELL);
        if (shell != null) {
            player.removeAttached(ModAttachments.KAME_SHELL);
            applyArmor(player, false);
        }
    }

    static void retract(ServerPlayer player, int kind) {
        player.setAttached(ModAttachments.KAME_RETRACT, (int) player.level().getGameTime());
        player.setAttached(ModAttachments.KAME_RETRACT_KIND, kind);
    }

    private static void endRetract(ServerPlayer player) {
        player.removeAttached(ModAttachments.KAME_RETRACT);
        player.removeAttached(ModAttachments.KAME_RETRACT_KIND);
        HITS.remove(player.getUUID());
        SPIN_VY.remove(player.getUUID());
    }

    // ------------------------------------------------------------------ boucle serveur

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(Kame::tick);
        KameVisits.initialize(); // visiteurs de la dimension de poche (demande sur la carapace posée)
        // mort : carapace et rétraction perdues ; mort DANS la poche : la carapace laissée dans le monde n'attend plus personne
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                if (inPocket(player)) {
                    KameVisits.expelGuests(player); // hôte mort : ses visiteurs sortent
                }
                reset(player);
                player.removeAttached(ModAttachments.KAME_SHELL);
                player.removeAttached(ModAttachments.KAME_RETURN);
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> reset(newPlayer));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> reset(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.getPlayer()));
        // dimension de poche : on ne casse et ne pose des blocs que dans SA parcelle
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> mayBuild(player, level, pos));
        // (visiteur : seulement portes, coffres, boutons, leviers... jamais poser ni modifier : KameVisits.guestMayUse)
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (KameVisits.isGuestIn(player, level)) {
                return KameVisits.guestMayUse(player, level, hit.getBlockPos()) ? InteractionResult.PASS : InteractionResult.FAIL;
            }
            // propriétaire : le bloc CLIQUÉ peut être la bedrock (sol, murs, plafond) : seul compte l'emplacement où le bloc serait
            // posé, ou le bloc cliqué s'il est à l'intérieur (remplacé ou utilisé : coffre, porte...)
            return mayBuild(player, level, hit.getBlockPos().relative(hit.getDirection())) || mayBuild(player, level, hit.getBlockPos())
                    ? InteractionResult.PASS : InteractionResult.FAIL;
        });
    }

    /**
     * Rétraction, saut, coups et sortie en cours effacés (jamais d'état invisible ou rétracté bloqué) ; le bonus d'armure suit la
     * carapace au tick suivant. Une sortie de poche interrompue (déconnexion, mort) : la carapace est rendue tout de suite.
     */
    public static void reset(ServerPlayer player) {
        if (EXITING.remove(player.getUUID()) != null && player.isAlive()
                && Fruits.get(player.getAttached(ModAttachments.FRUIT)) == Fruits.KAME) {
            player.setAttached(ModAttachments.KAME_SHELL, (int) Math.max(1L, player.level().getGameTime() - GROW_TICKS));
        }
        endRetract(player);
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean kame = Fruits.get(player.getAttached(ModAttachments.FRUIT)) == Fruits.KAME;
            long now = player.level().getGameTime();
            if (!kame) {
                // fruit perdu ou changé : plus de carapace ; s'il était dans sa poche, il en ressort
                if (inPocket(player)) {
                    exitPocket(player);
                }
                if (player.getAttached(ModAttachments.KAME_SHELL) != null) {
                    player.removeAttached(ModAttachments.KAME_SHELL);
                }
                if (retracted(player)) {
                    endRetract(player);
                }
                applyArmor(player, false);
                continue;
            }
            Integer shell = player.getAttached(ModAttachments.KAME_SHELL);
            if (shell != null && shell < 0 && now + shell >= SHRINK_TICKS) {
                player.removeAttached(ModAttachments.KAME_SHELL); // rentrée
            }
            applyArmor(player, hasShell(player));
            Integer at = player.getAttached(ModAttachments.KAME_RETRACT);
            if (at != null) {
                Integer kind = player.getAttached(ModAttachments.KAME_RETRACT_KIND);
                tickRetract(player, now - at, kind == null ? 0 : kind);
            }
        }
    }

    private static void tickRetract(ServerPlayer player, long age, int kind) {
        ServerLevel level = (ServerLevel) player.level();
        if (kind == KIND_POCKET) {
            if (age == POCKET_SUCK_AT) {
                // aspiration : souffle et tourbillon de poussière qui rentrent dans la carapace
                ploup(level, player, 0.55F);
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2.0 * i / 16;
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, player.getX() + Math.cos(a) * 1.4,
                            player.getY() + 1.0, player.getZ() + Math.sin(a) * 1.4, 0, -Math.cos(a), 0.0, -Math.sin(a), 0.18);
                }
            }
            if (age >= POCKET_RETRACT_TICKS) {
                enterPocket(player);
            }
        } else if (kind == KIND_SPIN) {
            if (age == POCKET_SUCK_AT) {
                ploup(level, player, 0.55F); // aspirés dans la carapace, comme vers la poche
            }
            if (age >= SPIN_RETRACT_TICKS + SPIN_TICKS) {
                retract(player, KIND_EXIT); // fin de l'attaque : les membres sont éjectés, comme à la sortie de la poche
                ploup(level, player, 0.9F);
                SPIN_VY.remove(player.getUUID());
                player.setDeltaMovement(player.getDeltaMovement().multiply(0.3, 1.0, 0.3));
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            } else if (age >= SPIN_RETRACT_TICKS) {
                spin(player, level, age);
            }
        } else if (kind == KIND_HIDDEN) {
            tickExit(player, level, age);
        } else if (age >= (kind == KIND_EXIT ? EXIT_EMERGE_TICKS : EMERGE_TICKS)) {
            endRetract(player);
        }
    }

    /** Bonus d'armure de la carapace : modificateurs à identifiant fixe (mis à jour, jamais cumulés), retirés sinon. */
    private static void applyArmor(ServerPlayer player, boolean on) {
        set(player, Attributes.ARMOR, on ? ARMOR : 0.0);
        set(player, Attributes.ARMOR_TOUGHNESS, on ? TOUGHNESS : 0.0);
    }

    private static void set(ServerPlayer player, Holder<Attribute> attribute, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (amount == 0.0) {
            if (instance.getModifier(ARMOR_ID) != null) {
                instance.removeModifier(ARMOR_ID);
            }
        } else {
            AttributeModifier current = instance.getModifier(ARMOR_ID);
            if (current == null || current.amount() != amount) {
                instance.addOrUpdateTransientModifier(new AttributeModifier(ARMOR_ID, amount, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    // ------------------------------------------------------------------ carapace tournoyante

    /**
     * La carapace file dans la direction où regarde le joueur (il la dirige en tournant la caméra), au sol ou en retombant.
     * Comme le dash Gekishin, la vitesse est envoyée au client du joueur à chaque tick. Toute créature touchée subit
     * {@link #SPIN_DAMAGE} et un recul, au plus une fois tous les {@link #SPIN_HIT_COOLDOWN} ticks ; plusieurs cibles par
     * attaque.
     */
    private static void spin(ServerPlayer player, ServerLevel level, long age) {
        float yaw = player.getYRot();
        Vec3 dir = new Vec3(-Math.sin(Math.toRadians(yaw)), 0.0, Math.cos(Math.toRadians(yaw)));
        // saut : touche de saut (au sol), ou petit bond tout seul contre une marche d'un bloc ; en l'air, gravité de Minecraft
        // (0,08 par tick, freinage 0,98). Les collisions avec les blocs restent celles du joueur : on saute PAR-DESSUS.
        double vy = SPIN_VY.getOrDefault(player.getUUID(), 0.0);
        boolean jump = player.getLastClientInput().jump();
        if (player.onGround() && vy <= 0.0) {
            vy = jump ? SPIN_JUMP : stepAhead(player, level, dir) ? SPIN_HOP : 0.0;
        } else {
            vy = Math.max(-1.5, (vy - 0.08) * 0.98);
        }
        SPIN_VY.put(player.getUUID(), vy);
        player.setDeltaMovement(dir.scale(SPIN_SPEED).add(0.0, vy, 0.0));
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.fallDistance = 0.0F;
        if (age % 4 == 0) {
            level.playSound(null, player.getX(), player.getY() + 0.5, player.getZ(), SoundEvents.TURTLE_SHAMBLE, SoundSource.PLAYERS,
                    0.6F, 1.6F);
        }
        level.sendParticles(ModParticles.FIRE_SMOKE, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0.2, 0.05, 0.2, 0.01);
        Map<Integer, Long> hits = HITS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        long now = level.getGameTime();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.7, 0.2, 0.7),
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            Long last = hits.get(target.getId());
            if (last != null && now - last < SPIN_HIT_COOLDOWN) {
                continue;
            }
            hits.put(target.getId(), now);
            if (target.hurtServer(level, level.damageSources().playerAttack(player), SPIN_DAMAGE)) {
                Vec3 push = target.position().subtract(player.position()).multiply(1.0, 0.0, 1.0).normalize().scale(SPIN_KNOCKBACK);
                target.push(push.x, 0.35, push.z);
            }
            level.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), SoundEvents.ANVIL_LAND,
                    SoundSource.PLAYERS, 1.0F, 0.8F);
            level.sendParticles(ModParticles.FIRE_SMOKE, target.getX(), target.getY() + 1.0, target.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /** Une marche d'un bloc juste devant, avec de la place au-dessus : la carapace bondit par-dessus. */
    private static boolean stepAhead(ServerPlayer player, ServerLevel level, Vec3 dir) {
        BlockPos front = BlockPos.containing(player.getX() + dir.x * 0.9, player.getY() + 0.2, player.getZ() + dir.z * 0.9);
        return !level.getBlockState(front).getCollisionShape(level, front).isEmpty()
                && level.getBlockState(front.above()).getCollisionShape(level, front.above()).isEmpty()
                && level.getBlockState(front.above(2)).getCollisionShape(level, front.above(2)).isEmpty();
    }

    // ------------------------------------------------------------------ dimension de poche

    /** Fin de la rétraction : la carapace se détache (entité posée dans le monde), le joueur part dans sa parcelle. */
    private static void enterPocket(ServerPlayer player) {
        ServerLevel pocket = player.level().getServer().getLevel(POCKET);
        endRetract(player);
        if (pocket == null) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.kame.no_pocket"), true);
            retract(player, KIND_EMERGE);
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        KameShellEntity shell = KameShellEntity.drop(level, player);
        player.setAttached(ModAttachments.KAME_RETURN, level.dimension() + ";" + player.getX() + ";" + player.getY() + ";"
                + player.getZ() + ";" + shell.getUUID());
        player.removeAttached(ModAttachments.KAME_SHELL); // la carapace est restée dehors
        BlockPos center = plotCenter(player);
        buildCube(pocket, center);
        player.teleportTo(pocket, center.getX() + 0.5, CUBE_FLOOR + 1.0, center.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, true);
        retract(player, KIND_EMERGE); // il se déplie dans sa poche
        player.sendSystemMessage(Component.translatable("message.bloxpiece.kame.pocket_entered"), true);
    }

    /**
     * Sortie de la poche (attaque 2 dans la poche, ou fruit perdu). Le joueur est ramené CACHÉ dans sa carapace posée (même place,
     * même direction) ; la carapace se redresse jusqu'à son torse en {@link #RISE_TICKS} ({@link #tickExit}), et c'est seulement
     * là qu'elle quitte le sol pour se remettre sur son dos, pendant que tête, bras et jambes en ressortent.
     */
    static void exitPocket(ServerPlayer player) {
        KameVisits.expelGuests(player); // ses visiteurs sortent d'abord, près de la carapace
        String[] info = player.getAttached(ModAttachments.KAME_RETURN).split(";");
        player.removeAttached(ModAttachments.KAME_RETURN);
        MinecraftServer server = player.level().getServer();
        ServerLevel level = server.overworld();
        for (ServerLevel candidate : server.getAllLevels()) {
            if (candidate.dimension().toString().equals(info[0])) {
                level = candidate; // le monde où la carapace a été posée
            }
        }
        double x = Double.parseDouble(info[1]), y = Double.parseDouble(info[2]), z = Double.parseDouble(info[3]);
        UUID shellId = UUID.fromString(info[4]);
        Entity shell = level.getEntity(shellId);
        float yaw = shell != null ? shell.getYRot() : player.getYRot();
        if (shell != null) {
            x = shell.getX();
            y = shell.getY();
            z = shell.getZ();
        }
        player.teleportTo(level, x, y, z, Set.of(), yaw, player.getXRot(), true);
        if (Fruits.get(player.getAttached(ModAttachments.FRUIT)) != Fruits.KAME) {
            if (shell != null) {
                shell.discard(); // fruit perdu : plus de carapace
            }
            return;
        }
        EXITING.put(player.getUUID(), shellId);
        retract(player, KIND_HIDDEN); // dans la carapace, invisible, le temps qu'elle se redresse
    }

    /**
     * Sortie de la poche, joueur encore caché : on retrouve sa carapace posée (le tronçon vient d'être chargé), elle se
     * redresse ; à {@link #RISE_TICKS}, elle quitte le sol et se remet sur son dos, et ses membres en ressortent (KIND_EXIT).
     * Introuvable au bout de 2 s (cas extrême) : la carapace lui est rendue quand même (jamais perdue).
     */
    private static void tickExit(ServerPlayer player, ServerLevel level, long age) {
        UUID shellId = EXITING.get(player.getUUID());
        Entity shell = shellId == null ? null : level.getEntity(shellId);
        if (shell instanceof KameShellEntity kame && kame.riseStart() < 0) {
            kame.startRise();
            player.teleportTo(level, kame.getX(), kame.getY(), kame.getZ(), Set.of(), kame.getYRot(), player.getXRot(), true);
            player.setAttached(ModAttachments.KAME_RETRACT, (int) level.getGameTime()); // le redressement commence maintenant
            level.playSound(null, kame.getX(), kame.getY() + 0.5, kame.getZ(), SoundEvents.TURTLE_SHAMBLE, SoundSource.PLAYERS, 1.0F,
                    0.8F);
            return;
        }
        boolean risen = shell instanceof KameShellEntity kame && kame.riseStart() >= 0 && age >= RISE_TICKS;
        if (risen || (shell == null && age >= 40)) {
            if (shell != null) {
                shell.discard(); // elle n'est plus au sol : elle est sur son dos
            }
            EXITING.remove(player.getUUID());
            player.setAttached(ModAttachments.KAME_SHELL, (int) Math.max(1L, level.getGameTime() - GROW_TICKS));
            retract(player, KIND_EXIT);
            ploup(level, player, 0.9F); // les membres sont éjectés hors de la carapace
        }
    }

    /** « Ploup » : petit bruit de bulle qui éclate et d'objet aspiré, avec une bouffée de fumée au torse. */
    private static void ploup(ServerLevel level, ServerPlayer player, float pitch) {
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP,
                SoundSource.PLAYERS, 1.4F, pitch);
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                0.9F, pitch * 0.8F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, player.getX(), player.getY() + 0.9, player.getZ(), 6,
                0.25, 0.25, 0.25, 0.02);
    }

    /** Joueur en train de sortir de sa poche (sa carapace posée ne doit pas se retirer d'elle-même). */
    public static boolean exiting(UUID player, UUID shell) {
        return shell.equals(EXITING.get(player));
    }

    /**
     * Le cube de bedrock du joueur (construit à sa première visite, ou reconstruit si son sol a disparu) : sol, plafond et quatre
     * murs, et 12 torches accrochées aux murs (posées une fois, à la création). L'intérieur reste entièrement libre.
     */
    private static void buildCube(ServerLevel pocket, BlockPos center) {
        int h = CUBE_INNER / 2 + 1;
        // déjà construit à cette taille (ses constructions sont sauvegardées avec la dimension) : rien à refaire
        BlockPos probe = new BlockPos(center.getX() + h, CUBE_FLOOR + 1, center.getZ());
        if (pocket.getBlockState(probe).is(net.minecraft.world.level.block.Blocks.BEDROCK)
                && pocket.getBlockState(new BlockPos(center.getX(), CUBE_FLOOR, center.getZ())).is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState bedrock = net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = CUBE_FLOOR + CUBE_HEIGHT + 1;
        for (int dx = -h; dx <= h; dx++) {
            for (int dz = -h; dz <= h; dz++) {
                boolean wall = Math.abs(dx) == h || Math.abs(dz) == h;
                for (int y = CUBE_FLOOR; y <= top; y++) {
                    if (wall || y == CUBE_FLOOR || y == top) {
                        pocket.setBlock(pos.set(center.getX() + dx, y, center.getZ() + dz), bedrock, 2);
                    }
                }
            }
        }
        // torches fixées aux murs, 3 par mur (au centre et à 6 blocs de part et d'autre), à 3 blocs du sol : posées une seule fois
        int ty = CUBE_FLOOR + 3;
        for (int k = -6; k <= 6; k += 6) {
            torch(pocket, pos.set(center.getX() - h + 1, ty, center.getZ() + k), net.minecraft.core.Direction.EAST);
            torch(pocket, pos.set(center.getX() + h - 1, ty, center.getZ() + k), net.minecraft.core.Direction.WEST);
            torch(pocket, pos.set(center.getX() + k, ty, center.getZ() - h + 1), net.minecraft.core.Direction.SOUTH);
            torch(pocket, pos.set(center.getX() + k, ty, center.getZ() + h - 1), net.minecraft.core.Direction.NORTH);
        }
    }

    /** Torche murale accrochée au mur derrière elle, tournée vers {@code facing} (vers l'intérieur du cube). */
    private static void torch(ServerLevel pocket, BlockPos pos, net.minecraft.core.Direction facing) {
        pocket.setBlock(pos, net.minecraft.world.level.block.Blocks.WALL_TORCH.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, facing), 2);
    }

    /** Centre du cube de ce joueur dans la dimension de poche (visiteurs : on les envoie dans celui de l'HÔTE). */
    public static BlockPos plotOf(ServerPlayer player) {
        return plotCenter(player);
    }

    /** Centre de la parcelle du joueur (attribuée à sa première visite, gardée pour toujours). */
    private static BlockPos plotCenter(ServerPlayer player) {
        Integer plot = player.getAttached(ModAttachments.KAME_PLOT);
        if (plot == null) {
            ServerLevel overworld = player.level().getServer().overworld();
            Integer count = overworld.getAttached(ModAttachments.KAME_PLOT_COUNT);
            int next = count == null ? 0 : count;
            overworld.setAttached(ModAttachments.KAME_PLOT_COUNT, next + 1);
            player.setAttached(ModAttachments.KAME_PLOT, next);
            plot = next;
        }
        return new BlockPos(plot * PLOT_SPACING, CUBE_FLOOR, 0);
    }

    /**
     * Dans la dimension de poche, un joueur ne casse et ne pose des blocs qu'à l'INTÉRIEUR de son propre cube (jamais la bedrock
     * des murs, du sol ni du plafond, même en créatif) ; les autres dimensions : aucune restriction.
     */
    private static boolean mayBuild(net.minecraft.world.entity.player.Player player, Level level, BlockPos pos) {
        if (level.dimension() != POCKET || !(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        Integer plot = serverPlayer.getAttached(ModAttachments.KAME_PLOT);
        if (plot == null) {
            return false;
        }
        int inner = CUBE_INNER / 2;
        return Math.abs(pos.getX() - plot * PLOT_SPACING) <= inner && Math.abs(pos.getZ()) <= inner
                && pos.getY() > CUBE_FLOOR && pos.getY() <= CUBE_FLOOR + CUBE_HEIGHT;
    }
}
