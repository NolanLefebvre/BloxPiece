package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.KameShellEntity;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Kame Kame no Mi : VISITEURS de la dimension de poche. Tout se décide côté serveur.
 * <ol>
 *   <li>Clic droit sur la carapace posée d'un joueur qui est dans sa poche (KameShellEntity) : une DEMANDE lui est envoyée, avec
 *       le nom du demandeur et deux boutons dans le chat, [Accepter] et [Refuser] (commandes /bloxfruit kame accept|refuse
 *       &lt;joueur&gt;, que seul le propriétaire peut utiliser pour SES demandes). Une demande par joueur toutes les
 *       {@link #REQUEST_COOLDOWN} ticks, valable {@link #REQUEST_TTL} ticks.</li>
 *   <li>Acceptée : le visiteur part dans le cube du PROPRIÉTAIRE (jamais le sien), plusieurs visiteurs possibles à la fois.
 *       Attachment KAME_VISIT (sauvegardé) : propriétaire + point de retour sûr près de la carapace.</li>
 *   <li>Visiteur : il se déplace, ouvre portes, trappes, portillons, coffres et autres conteneurs, tables de craft, utilise
 *       boutons et leviers ; il ne pose ni ne casse aucun bloc (Kame.mayBuild), ne vide pas de seau, ne touche pas aux cadres
 *       et supports d'armure, et n'utilise aucun pouvoir de fruit dans le cube (AbilityManager).</li>
 *   <li>Le propriétaire ressort (ou meurt, ou se déconnecte) : tous ses visiteurs sont ramenés dehors, sur un endroit sûr à côté
 *       de la carapace, AVANT lui. Ses demandes en attente sont annulées.</li>
 * </ol>
 * Un visiteur qui se reconnecte alors que son hôte n'est plus dans sa poche est ramené dehors ; un joueur qui se retrouve dans
 * la dimension de poche sans y être autorisé (ni propriétaire, ni visiteur) en est sorti (créatif et spectateur exceptés).
 */
public final class KameVisits {

    public static final int REQUEST_COOLDOWN = 20 * 10;
    public static final int REQUEST_TTL = 20 * 60;
    /** Le demandeur doit encore être près de la carapace quand le propriétaire accepte. */
    private static final double ACCEPT_RANGE = 16.0;

    private record Request(UUID shell, long at) {
    }

    /** propriétaire -> (demandeur -> demande). */
    private static final Map<UUID, Map<UUID, Request>> REQUESTS = new HashMap<>();
    /** demandeur -> tick de sa dernière demande (anti-répétition). */
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

    private KameVisits() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(KameVisits::tick);
        // visiteur : aucun objet qui modifie le décor (seaux), aucun cadre ni support d'armure, aucun bloc frappé (pas de casse)
        UseItemCallback.EVENT.register((player, level, hand) -> isGuestIn(player, level)
                && player.getItemInHand(hand).getItem() instanceof BucketItem ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            // clic droit sur une carapace posée : demande d'entrée à son propriétaire (côté serveur ; le client fait juste le geste)
            if (entity instanceof KameShellEntity shell) {
                if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) {
                    request(serverPlayer, shell);
                }
                return InteractionResult.SUCCESS;
            }
            return isGuestIn(player, level) && decoration(entity) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> isGuestIn(player, level) && decoration(entity)
                ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> isGuestIn(player, level)
                ? InteractionResult.FAIL : InteractionResult.PASS);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                player.removeAttached(ModAttachments.KAME_VISIT); // visiteur mort : il réapparaît chez lui, plus visiteur
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            REQUESTS.remove(player.getUUID());
            for (Map<UUID, Request> pending : REQUESTS.values()) {
                pending.remove(player.getUUID());
            }
            if (Kame.inPocket(player)) {
                expelGuests(player); // hôte déconnecté : ses visiteurs sortent
            }
        });
    }

    // ------------------------------------------------------------------ état

    /** Ce joueur est-il un visiteur, dans la dimension de poche ? */
    public static boolean isGuestIn(Player player, Level level) {
        return level.dimension() == Kame.POCKET && player.getAttached(ModAttachments.KAME_VISIT) != null;
    }

    private static boolean decoration(Entity entity) {
        return !(entity instanceof LivingEntity) || entity instanceof ArmorStand;
    }

    /** Blocs qu'un visiteur peut utiliser (mécanismes et rangements), sans rien poser ni modifier. */
    public static boolean guestMayUse(Player player, Level level, BlockPos pos) {
        if (player.isSecondaryUseActive() && !player.getMainHandItem().isEmpty()) {
            return false; // accroupi avec un objet : Minecraft poserait ou utiliserait l'objet sur le bloc
        }
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.BUTTONS) || state.getBlock() instanceof LeverBlock
                || state.getBlock() instanceof CraftingTableBlock || level.getBlockEntity(pos) instanceof MenuProvider;
    }

    // ------------------------------------------------------------------ demandes

    /** Clic droit d'un joueur sur une carapace posée : demande d'entrée envoyée à son propriétaire. */
    public static void request(ServerPlayer requester, KameShellEntity shell) {
        String ownerId = shell.getAttached(ModAttachments.KAME_SHELL_OWNER);
        MinecraftServer server = requester.level().getServer();
        ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(UUID.fromString(ownerId));
        if (owner == null || owner == requester) {
            return;
        }
        if (!waitsOn(owner, shell.getUUID())) {
            requester.sendSystemMessage(Component.translatable("message.bloxpiece.kame.visit_absent", owner.getDisplayName()), true);
            return;
        }
        long now = requester.level().getGameTime();
        Long last = LAST_REQUEST.get(requester.getUUID());
        if (last != null && now - last < REQUEST_COOLDOWN) {
            requester.sendSystemMessage(Component.translatable("message.bloxpiece.kame.visit_wait"), true);
            return;
        }
        LAST_REQUEST.put(requester.getUUID(), now);
        REQUESTS.computeIfAbsent(owner.getUUID(), k -> new HashMap<>()).put(requester.getUUID(), new Request(shell.getUUID(), now));
        String name = requester.getName().getString();
        Component accept = Component.translatable("message.bloxpiece.kame.visit_accept").withStyle(s -> s
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/bloxfruit kame accept " + name))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("/bloxfruit kame accept " + name))));
        Component refuse = Component.translatable("message.bloxpiece.kame.visit_refuse").withStyle(s -> s
                .withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/bloxfruit kame refuse " + name))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("/bloxfruit kame refuse " + name))));
        owner.sendSystemMessage(Component.translatable("message.bloxpiece.kame.visit_request", requester.getDisplayName())
                .append(" ").append(accept).append(" ").append(refuse));
        owner.level().playSound(null, owner.getX(), owner.getY() + 1.0, owner.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.8F, 0.6F);
        requester.sendSystemMessage(Component.translatable("message.bloxpiece.kame.visit_sent", owner.getDisplayName()), true);
    }

    /** Le propriétaire est dans sa poche, et c'est bien CETTE carapace qui l'attend. */
    private static boolean waitsOn(ServerPlayer owner, UUID shell) {
        String wait = owner.getAttached(ModAttachments.KAME_RETURN);
        return wait != null && wait.endsWith(shell.toString());
    }

    /** /bloxfruit kame accept|refuse <joueur> : seulement par le propriétaire, pour une demande qui le concerne. */
    public static int answer(ServerPlayer owner, ServerPlayer requester, boolean accepted) {
        Map<UUID, Request> pending = REQUESTS.get(owner.getUUID());
        Request request = pending == null ? null : pending.remove(requester.getUUID());
        if (request == null || owner.level().getGameTime() - request.at() > REQUEST_TTL || !waitsOn(owner, request.shell())) {
            owner.sendSystemMessage(Component.translatable("message.bloxpiece.kame.visit_none"), true);
            return 0;
        }
        if (!accepted) {
            requester.sendSystemMessage(Component.translatable("message.bloxpiece.kame.visit_refused", owner.getDisplayName()), true);
            owner.sendSystemMessage(Component.translatable("message.bloxpiece.kame.visit_done"), true);
            return 1;
        }
        // le demandeur doit encore être dehors, près de la carapace, libre (ni dans une poche, ni déjà visiteur)
        Entity shell = findShell(owner, request.shell());
        if (shell == null || requester.level() != shell.level() || requester.distanceTo(shell) > ACCEPT_RANGE
                || Kame.inPocket(requester) || requester.getAttached(ModAttachments.KAME_VISIT) != null || Kame.retracted(requester)) {
            owner.sendSystemMessage(Component.translatable("message.bloxpiece.kame.visit_gone", requester.getDisplayName()), true);
            return 0;
        }
        ServerLevel pocket = owner.level().getServer().getLevel(Kame.POCKET);
        if (pocket == null || owner.level() != pocket) {
            return 0;
        }
        // retour sûr : à côté de la carapace (là où il était si rien ne mieux)
        Vec3 back = safeSpotNear((ServerLevel) shell.level(), shell.position(), requester.position());
        requester.setAttached(ModAttachments.KAME_VISIT, owner.getUUID() + ";" + shell.level().dimension() + ";" + back.x + ";" + back.y
                + ";" + back.z);
        BlockPos center = Kame.plotOf(owner);
        requester.teleportTo(pocket, center.getX() + 2.5, Kame.CUBE_FLOOR + 1.0, center.getZ() + 0.5, Set.of(), requester.getYRot(),
                0.0F, true);
        requester.sendSystemMessage(Component.translatable("message.bloxpiece.kame.visit_welcome", owner.getDisplayName()), true);
        owner.sendSystemMessage(Component.translatable("message.bloxpiece.kame.visit_done"), true);
        return 1;
    }

    private static Entity findShell(ServerPlayer owner, UUID shell) {
        String wait = owner.getAttached(ModAttachments.KAME_RETURN);
        if (wait == null) {
            return null;
        }
        ServerLevel level = levelNamed(owner.level().getServer(), wait.split(";")[0]);
        return level == null ? null : level.getEntity(shell);
    }

    // ------------------------------------------------------------------ sorties

    /** L'hôte quitte sa poche (sortie, mort, déconnexion) : demandes annulées, tous ses visiteurs ramenés dehors, AVANT lui. */
    public static void expelGuests(ServerPlayer owner) {
        REQUESTS.remove(owner.getUUID());
        String id = owner.getUUID().toString();
        for (ServerPlayer guest : owner.level().getServer().getPlayerList().getPlayers()) {
            String visit = guest.getAttached(ModAttachments.KAME_VISIT);
            if (visit != null && visit.startsWith(id)) {
                sendBack(guest);
                guest.sendSystemMessage(Component.translatable("message.bloxpiece.kame.visit_over", owner.getDisplayName()), true);
            }
        }
    }

    /** Le visiteur retourne à son point de retour sûr, près de la carapace. */
    public static void sendBack(ServerPlayer guest) {
        String[] info = guest.getAttached(ModAttachments.KAME_VISIT).split(";");
        guest.removeAttached(ModAttachments.KAME_VISIT);
        MinecraftServer server = guest.level().getServer();
        ServerLevel level = levelNamed(server, info[1]);
        if (level == null) {
            level = server.overworld();
        }
        guest.teleportTo(level, Double.parseDouble(info[2]), Double.parseDouble(info[3]), Double.parseDouble(info[4]), Set.of(),
                guest.getYRot(), guest.getXRot(), true);
    }

    /** /bloxfruit kame leave : un visiteur s'en va de lui-même. */
    public static int leave(ServerPlayer guest) {
        if (guest.getAttached(ModAttachments.KAME_VISIT) == null) {
            return 0;
        }
        sendBack(guest);
        return 1;
    }

    private static ServerLevel levelNamed(MinecraftServer server, String name) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().toString().equals(name)) {
                return level;
            }
        }
        return null;
    }

    /**
     * Un endroit sûr à côté de la carapace : 2 blocs d'air (ou non solides) au-dessus d'un bloc solide, sans lave ni feu, dans un
     * rayon de 3 blocs ; sinon la position d'où le demandeur est parti.
     */
    static Vec3 safeSpotNear(ServerLevel level, Vec3 around, Vec3 fallback) {
        BlockPos base = BlockPos.containing(around);
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = 2; dy >= -2; dy--) {
                        BlockPos feet = base.offset(dx, dy, dz);
                        if (safe(level, feet)) {
                            return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                        }
                    }
                }
            }
        }
        return fallback;
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        BlockState below = level.getBlockState(feet.below());
        return below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty()
                && !below.is(BlockTags.FIRE) && !level.getBlockState(feet).is(BlockTags.FIRE);
    }

    // ------------------------------------------------------------------ contrôle (une fois par seconde)

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        long now = server.overworld().getGameTime();
        REQUESTS.values().forEach(m -> m.values().removeIf(r -> now - r.at() > REQUEST_TTL));
        REQUESTS.values().removeIf(Map::isEmpty);
        LAST_REQUEST.values().removeIf(t -> now - t > REQUEST_COOLDOWN);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String visit = player.getAttached(ModAttachments.KAME_VISIT);
            if (visit != null) {
                // visiteur dont l'hôte n'est plus dans sa poche (déconnecté, sorti...) : dehors
                ServerPlayer host = server.getPlayerList().getPlayer(UUID.fromString(visit.split(";")[0]));
                if (host == null || !Kame.inPocket(host) || player.level().dimension() != Kame.POCKET) {
                    if (player.level().dimension() == Kame.POCKET) {
                        sendBack(player);
                    } else {
                        player.removeAttached(ModAttachments.KAME_VISIT);
                    }
                }
            } else if (player.level().dimension() == Kame.POCKET && !Kame.inPocket(player) && !player.isCreative()
                    && !player.isSpectator()) {
                // dans une poche sans autorisation : renvoyé au point d'apparition du monde
                ServerLevel overworld = server.overworld();
                BlockPos spawn = nolan.bloxpiece.village.StarterVillage.worldSpawn(overworld);
                if (spawn == null) {
                    spawn = new BlockPos(0, 100, 0);
                }
                player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.of(), player.getYRot(),
                        player.getXRot(), true);
            }
        }
    }
}
