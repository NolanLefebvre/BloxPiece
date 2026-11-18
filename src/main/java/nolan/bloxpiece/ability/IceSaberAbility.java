package nolan.bloxpiece.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.fx.IceEffects;
import nolan.bloxpiece.item.IceSaberItem;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModItems;
import nolan.bloxpiece.fruit.WaterCurse;

/**
 * Première attaque du fruit de la Glace : Ice Saber, le trident de glace.
 *
 * Le trident est un VRAI item (ModItems.ICE_SABER, modèle 3D de trident de glace) posé dans le premier emplacement libre de la
 * barre d'inventaire. Il disparaît :
 * <ul>
 *   <li>si on relance l'attaque (elle sert d'interrupteur) ;</li>
 *   <li>s'il est lancé par l'attaque 2 (Partisan) ;</li>
 *   <li>s'il est jeté au sol (ItemEntityMixin le fait fondre aussitôt), y compris à la mort ;</li>
 *   <li>dans l'eau, ou si le joueur perd le fruit de la Glace.</li>
 * </ul>
 * Tenu en main, chaque coup de mêlée inflige de lourds dégâts de glace et un ralentissement intense (AttackEntityCallback, voir
 * ModEvents), et le joueur frappe d'un estoc de lance (animation synchronisée, voir PlayerModelMixin). Des cristaux et de la brume
 * froide l'entourent.
 */
public class IceSaberAbility implements Ability {

    /** Dégâts de glace d'un coup : ceux affichés dans l'infobulle du trident. */
    private static final float HIT_DAMAGE = IceSaberItem.DAMAGE;
    private static final int HIT_SLOW_TICKS = 80;
    private static final int HOTBAR_SIZE = 9;

    @Override
    public String id() {
        return "ice_saber";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 2; // court : l'attaque sert aussi à faire disparaître le trident
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        if (has(player) || freeHotbarSlot(player.getInventory()) >= 0) {
            return true;
        }
        player.sendSystemMessage(Component.translatable("message.bloxpiece.ice_saber_no_slot"), true);
        return false;
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        if (remove(player)) {
            // Deuxième appui : le trident se brise et disparaît
            IceEffects.shatter(level, handOf(player), 1.0F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
            return;
        }
        int slot = freeHotbarSlot(player.getInventory());
        if (slot < 0) {
            return;
        }
        player.getInventory().setItem(slot, new ItemStack(ModItems.ICE_SABER));
        IceEffects.saberForm(level, handOf(player));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.2F, 0.5F);
    }

    /** Premier emplacement vide de la barre d'inventaire (0 à 8), ou -1. */
    private static int freeHotbarSlot(Inventory inventory) {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (inventory.getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** Le joueur a-t-il un trident de glace quelque part dans son inventaire ? */
    public static boolean has(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.ICE_SABER)) {
                return true;
            }
        }
        return false;
    }

    /** Le trident est-il tenu en main principale ? */
    public static boolean isHeld(LivingEntity entity) {
        return entity.getMainHandItem().is(ModItems.ICE_SABER);
    }

    /** Retire tous les tridents de glace de l'inventaire du joueur (et de son curseur) : vrai s'il y en avait un. */
    public static boolean remove(ServerPlayer player) {
        boolean found = false;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.ICE_SABER)) {
                inventory.setItem(i, ItemStack.EMPTY);
                found = true;
            }
        }
        if (player.containerMenu.getCarried().is(ModItems.ICE_SABER)) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            found = true;
        }
        return found;
    }

    /** Le joueur frappe avec le trident en main (clic d'attaque, même dans le vide) : l'estoc est animé chez tous les joueurs. */
    public static void strike(ServerPlayer player) {
        if (isHeld(player)) {
            player.setAttached(ModAttachments.ICE_SABER_STRIKE, (int) player.level().getGameTime());
        }
    }

    /** Coup de mêlée (ModEvents, AttackEntityCallback) : trident en main, lourds dégâts de glace et givre. */
    public static void onMelee(ServerPlayer player, Entity target) {
        if (isHeld(player) && target instanceof LivingEntity living && IceFreeze.canHit(living, player)) {
            ServerLevel level = (ServerLevel) player.level();
            IceFreeze.hit(level, player, living, HIT_DAMAGE, HIT_SLOW_TICKS);
            level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 1.2F, 0.7F);
        }
    }

    /** La main principale du joueur, à peu près (pour les particules) : devant et à droite, à hauteur de la taille. */
    static Vec3 handOf(ServerPlayer player) {
        double size = player.getScale();
        double yaw = Math.toRadians(player.yBodyRot);
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        return player.position().add(0.0, 0.9 * size, 0.0).add(right.scale(0.4 * size)).add(forward.scale(0.3 * size));
    }

    /** À appeler à chaque tick serveur (ModEvents) : aura de givre en main, et le trident fond dans l'eau ou sans le fruit. */
    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean holding = isHeld(player);
            boolean lost = WaterCurse.touchesWater(player) || Fruits.get(player.getAttached(ModAttachments.FRUIT)) != Fruits.ICE;
            if (lost && remove(player)) { // sans le fruit de la Glace (ou dans l'eau), le trident fond aussitôt
                IceEffects.shatter((ServerLevel) player.level(), handOf(player), 1.0F);
                continue;
            }
            if (holding) {
                IceEffects.saberAura((ServerLevel) player.level(), handOf(player), player.tickCount);
            }
        }
    }
}
