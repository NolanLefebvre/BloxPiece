package nolan.bloxpiece.village;

import java.lang.reflect.Method;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import nolan.bloxpiece.registry.ModAttachments;

/**
 * Zone sécurisée du village de départ : un cylindre de rayon {@link #RADIUS} autour du centre de la place (toute la zone
 * aplanie et ses abords), de {@link #DEPTH} blocs sous la place à {@link #HEIGHT} blocs au-dessus.
 * <ul>
 *   <li>Les joueurs y sont invulnérables : PvP, monstres, chute, feu, noyade, explosions, attaques de fruit... (seuls le vide et
 *       /kill passent encore, pour ne jamais rester bloqué).</li>
 *   <li>Aucun bloc ne peut y être cassé, posé ou modifié par un joueur (sauf en Créatif ou opérateur), ni par une explosion
 *       (creeper, TNT, boule de feu, attaques explosives des fruits : voir ServerExplosionMixin), ni par les pouvoirs du mod qui
 *       creusent, gèlent ou enflamment le terrain (ils appellent {@link #isProtected}).</li>
 *   <li>Les interactions restent libres : portes, trappes, portillons, coffres, tonneaux, fours, établis, lits, boutons,
 *       leviers, cloches, PNJ, quêtes.</li>
 * </ul>
 */
public final class SafeZone {

    /** Rayon horizontal (blocs) : le terrain aplani du village + une marge. */
    public static final int RADIUS = VillageLayout.TERRAIN_FLAT + 6;
    public static final int DEPTH = 24;
    public static final int HEIGHT = 80;

    private SafeZone() {
    }

    /** Centre du village de ce niveau (Overworld côté serveur), ou null. */
    private static BlockPos center(Level level) {
        if (!(level instanceof ServerLevel server) || server != server.getServer().overworld()) {
            return null;
        }
        return server.getAttached(ModAttachments.VILLAGE_CENTER);
    }

    public static boolean contains(Level level, double x, double y, double z) {
        BlockPos c = center(level);
        if (c == null) {
            return false;
        }
        double dx = x - (c.getX() + 0.5), dz = z - (c.getZ() + 0.5);
        return dx * dx + dz * dz <= (double) RADIUS * RADIUS && y >= c.getY() - DEPTH && y <= c.getY() + HEIGHT;
    }

    /** Ce bloc est-il dans la zone sécurisée (donc intouchable) ? À appeler avant toute destruction / pose par le mod. */
    public static boolean isProtected(Level level, BlockPos pos) {
        return contains(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** Créatif ou opérateur (niveau 2) : peut construire dans le village. */
    public static boolean canBuild(Player player) {
        if (player.isCreative()) {
            return true;
        }
        // le nom de la méthode de permission change selon les versions : essai sans risque
        try {
            Method m = player.getClass().getMethod("hasPermissions", int.class);
            return Boolean.TRUE.equals(m.invoke(player, 2));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /** Blocs « fonctionnels » qu'on peut utiliser dans le village (ouvrir, s'asseoir, dormir, ranger...). */
    private static boolean isInteractive(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof SignBlock) {
            return false; // pas de réécriture des panneaux
        }
        return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof BedBlock || block instanceof ButtonBlock || block instanceof LeverBlock
                || block instanceof BellBlock || block instanceof BaseEntityBlock
                || state.getMenuProvider(level, pos) != null; // établi, enclume, métier à tisser, meule...
    }

    private static boolean guarded(Player player, Level level, BlockPos pos) {
        return !level.isClientSide() && isProtected(level, pos) && !canBuild(player);
    }

    public static void initialize() {
        // 1. Invulnérabilité des joueurs
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return true;
            }
            if (contains(player.level(), player.getX(), player.getY(), player.getZ())) {
                return false; // joueur dans le village : aucun dégât
            }
            // PvP depuis le village vers l'extérieur interdit aussi (pas d'attaque à l'abri de la zone)
            return !(source.getEntity() instanceof Player attacker && attacker != player
                    && contains(attacker.level(), attacker.getX(), attacker.getY(), attacker.getZ()));
        });
        // 2. Ni casse...
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !guarded(player, level, pos));
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                guarded(player, level, pos) ? InteractionResult.FAIL : InteractionResult.PASS);
        // ... ni pose / modification, mais interactions libres
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            BlockPos pos = hit.getBlockPos();
            if (!guarded(player, level, pos) && !guarded(player, level, pos.relative(hit.getDirection()))) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);
            BlockState state = level.getBlockState(pos);
            if (isInteractive(level, pos, state) && !player.isSecondaryUseActive()) {
                return InteractionResult.PASS; // la porte s'ouvre, le coffre s'ouvre... (l'objet tenu n'est pas utilisé)
            }
            // sur un bloc décoratif : rien en main = rien à faire ; un objet = pose / modification interdite
            return stack.isEmpty() ? InteractionResult.PASS : InteractionResult.FAIL;
        });
        // seaux et objets qu'on pose sur l'eau (nénuphar...) : utilisés « dans le vide », sans viser de bloc
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            boolean placesSomething = stack.getItem() instanceof BucketItem || stack.getItem() instanceof PlaceOnWaterBlockItem;
            if (placesSomething && !level.isClientSide() && contains(level, player.getX(), player.getY(), player.getZ())
                    && !canBuild(player)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }
}
