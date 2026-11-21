package nolan.bloxpiece.handcuffs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.ability.BuddhaTransformAbility;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.mob.ArmedHumanoid;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModItems;

/**
 * Menottes en Granite Marin, côté serveur (attachment HANDCUFFED, synchronisé avec tous les joueurs pour l'affichage).
 * <ul>
 *   <li>Pose sur un autre joueur : clic droit avec des menottes (SeastoneHandcuffsItem), à DEUX conditions : la cible est à
 *       {@link #MAX_HEALTH_RATIO} de ses PV ou moins, et on se trouve dans son dos (dans un cône de {@link #BEHIND_ANGLE}° derrière
 *       elle). Sinon la pose échoue avec un message. Les menottes sont consommées.</li>
 *   <li>Auto-menottage : accroupi + clic droit (dans le vide ou sur un bloc) avec les menottes en main.</li>
 *   <li>Tout joueur menotté a les mains liées dans le dos : il ne peut utiliser aucun objet, ni frapper, ni casser de bloc, ni
 *       interagir avec un bloc ou une entité ; côté client, ses cases d'inventaire sont verrouillées (cadenas, voir HandcuffsClient).
 *       Seule exception : la Clé de Menottes.</li>
 *   <li>Porteur d'un fruit du démon : le Granite Marin lui retire ses pouvoirs. Vitesse -15 %, attaques bloquées (AbilityManager),
 *       passif Logia désactivé (LogiaIntangibility), transformation annulée et impossible.</li>
 *   <li>Retrait : clic droit avec une Clé de Menottes sur le joueur, ou sur soi-même (clic droit dans le vide avec la clé en main).
 *       Les menottes sont détruites. À la mort, elles tombent au sol. Sans clé, elles s'ouvrent d'elles-mêmes au bout de
 *       {@link #DURATION_SECONDS} secondes (5 minutes, joueurs et mobs) ; le joueur voit le temps restant à l'écran.</li>
 *   <li>Mobs : mêmes conditions (10 % de PV ou moins, poseur dans son dos), seulement les mobs qui ont des bras (tag d'entités
 *       bloxpiece:has_arms : zombies, squelettes, piglins, pirates, Marines Épéiste et Fusilier...). Un mob menotté a les bras dans le dos, range son arme (un
 *       squelette range son arc), ne peut plus prendre de cible (MobMixin : aucune attaque, visée ni tir), ne peut plus infliger
 *       le moindre dégât, et s'enfuit loin des joueurs proches. La clé le libère (menottes
 *       détruites) et il reprend son arme.</li>
 *   <li>Inventaire : le joueur menotté peut l'ouvrir, mais seule la Clé de Menottes peut y être déplacée
 *       (AbstractContainerMenuMixin), pour la mettre dans sa hotbar et se libérer.</li>
 * </ul>
 */
public final class Handcuffs {

    /** Ralentissement d'un porteur de fruit menotté : -15 % de vitesse. */
    private static final Identifier SPEED_MODIFIER = BloxPiece.id("handcuffs_slowness");
    private static final double FRUIT_SLOWNESS = -0.15;

    /** Durée des menottes : elles s'ouvrent d'elles-mêmes au bout de 5 minutes (la clé les retire plus tôt). */
    public static final int DURATION_SECONDS = 5 * 60;

    /** Seuil de PV : la cible doit être à 10 % de ses PV maximum ou moins. */
    private static final float MAX_HEALTH_RATIO = 0.10F;
    /** Dans le dos : le poseur doit être à moins de 60° de l'axe arrière de la cible (cône de 120° derrière elle). */
    private static final double BEHIND_ANGLE = 120.0;
    private static final double BEHIND_COS = Math.cos(Math.toRadians(BEHIND_ANGLE / 2.0));

    /** Mobs qui ont des bras (on peut les menotter). Liste : data/bloxpiece/tags/entity_type/has_arms.json. */
    public static final TagKey<EntityType<?>> HAS_ARMS = TagKey.create(Registries.ENTITY_TYPE, BloxPiece.id("has_arms"));

    /** Mobs menottés chargés dans le monde (pour les neutraliser à chaque tick) et les armes qu'ils ont rangées. */
    private static final Set<Mob> CUFFED_MOBS = new HashSet<>();
    private static final Map<UUID, ItemStack[]> STORED_WEAPONS = new HashMap<>();

    private Handcuffs() {
    }

    public static boolean isCuffed(Entity entity) {
        return Boolean.TRUE.equals(entity.getAttached(ModAttachments.HANDCUFFED));
    }

    public static boolean isKey(ItemStack stack) {
        return stack.is(ModItems.HANDCUFF_KEY);
    }

    /**
     * Menotte {@code target} (posées par {@code by}). Renvoie false si c'est impossible : déjà menotté, PV au-dessus du seuil, ou
     * poseur pas dans le dos de la cible (un message explique pourquoi).
     */
    public static boolean cuff(ServerPlayer target, ServerPlayer by) {
        if (target == by || isCuffed(target) || !target.isAlive()) {
            return false;
        }
        if (target.getHealth() > target.getMaxHealth() * MAX_HEALTH_RATIO) {
            by.sendSystemMessage(Component.translatable("message.bloxpiece.handcuffs.too_strong"), true);
            return false;
        }
        if (!isBehind(target, by)) {
            by.sendSystemMessage(Component.translatable("message.bloxpiece.handcuffs.not_behind"), true);
            return false;
        }
        return apply(target, by);
    }

    /** Accroupi + clic droit avec les menottes : le joueur se menotte lui-même (aucune condition). */
    public static boolean cuffSelf(ServerPlayer player) {
        if (isCuffed(player) || !player.isAlive()) {
            return false;
        }
        return apply(player, player);
    }

    /** Le poseur est-il dans le dos de la cible ? (direction cible -> poseur opposée à l'orientation du corps de la cible) */
    private static boolean isBehind(LivingEntity target, ServerPlayer by) {
        Vec3 facing = Vec3.directionFromRotation(0.0F, target.yBodyRot);
        Vec3 toAttacker = new Vec3(by.getX() - target.getX(), 0.0, by.getZ() - target.getZ());
        if (toAttacker.lengthSqr() < 1.0E-4) {
            return false;
        }
        return toAttacker.normalize().dot(facing) <= -BEHIND_COS;
    }

    private static boolean apply(ServerPlayer target, ServerPlayer by) {
        target.setAttached(ModAttachments.HANDCUFFED, true);
        target.setAttached(ModAttachments.HANDCUFF_SECONDS, DURATION_SECONDS);
        if (BuddhaTransformAbility.isActive(target)) {
            BuddhaTransformAbility.reset(target);
        }
        nolan.bloxpiece.ability.Kame.cancelPowers(target); // Kame Kame no Mi : plus de carapace
        ServerLevel level = (ServerLevel) target.level();
        level.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 1.8F);
        level.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.6F, 1.6F);
        target.sendSystemMessage(Component.translatable("message.bloxpiece.handcuffs.cuffed"), true);
        if (by != target) {
            by.sendSystemMessage(Component.translatable("message.bloxpiece.handcuffs.cuffed_other", target.getDisplayName()), true);
        }
        return true;
    }

    /**
     * Retire les menottes de {@code target} avec la clé de {@code giveTo} (lui-même ou un autre joueur). Les menottes sont
     * détruites : elles ne sont rendues à personne.
     */
    public static boolean release(ServerPlayer target, ServerPlayer giveTo) {
        if (!isCuffed(target)) {
            return false;
        }
        target.removeAttached(ModAttachments.HANDCUFFED);
        target.removeAttached(ModAttachments.HANDCUFF_SECONDS);
        updateSpeed(target, false);
        ServerLevel level = (ServerLevel) target.level();
        level.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 0.6F, 1.8F);
        level.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25F, 2.0F);
        target.sendSystemMessage(Component.translatable("message.bloxpiece.handcuffs.released"), true);
        if (giveTo != target) {
            giveTo.sendSystemMessage(Component.translatable("message.bloxpiece.handcuffs.released_other", target.getDisplayName()), true);
        }
        return true;
    }

    // ------------------------------------------------------------------ mobs

    /** Ce mob a-t-il des bras (peut-il être menotté) ? */
    public static boolean hasArms(LivingEntity entity) {
        return entity instanceof Mob && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).is(HAS_ARMS);
    }

    /** Menotte un mob : mêmes conditions que pour un joueur (PV, dans son dos) + le mob doit avoir des bras. */
    public static boolean cuffMob(Mob mob, ServerPlayer by) {
        if (isCuffed(mob) || !mob.isAlive()) {
            return false;
        }
        if (!hasArms(mob)) {
            by.sendSystemMessage(Component.translatable("message.bloxpiece.handcuffs.no_arms"), true);
            return false;
        }
        if (mob.getHealth() > mob.getMaxHealth() * MAX_HEALTH_RATIO) {
            by.sendSystemMessage(Component.translatable("message.bloxpiece.handcuffs.too_strong"), true);
            return false;
        }
        if (!isBehind(mob, by)) {
            by.sendSystemMessage(Component.translatable("message.bloxpiece.handcuffs.not_behind"), true);
            return false;
        }
        mob.setAttached(ModAttachments.HANDCUFFED, true);
        mob.setAttached(ModAttachments.HANDCUFF_SECONDS, DURATION_SECONDS);
        stowWeapons(mob);
        neutralize(mob);
        CUFFED_MOBS.add(mob);
        ServerLevel level = (ServerLevel) mob.level();
        level.playSound(null, mob.getX(), mob.getY() + 1.0, mob.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.3F, 1.8F);
        level.playSound(null, mob.getX(), mob.getY() + 1.0, mob.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.HOSTILE, 0.6F, 1.6F);
        by.sendSystemMessage(Component.translatable("message.bloxpiece.handcuffs.cuffed_other", mob.getDisplayName()), true);
        return true;
    }

    /** Libère un mob (clé) : les menottes sont détruites, il reprend son arme. */
    public static boolean releaseMob(Mob mob, ServerPlayer by) {
        if (!isCuffed(mob)) {
            return false;
        }
        freeMob(mob);
        by.sendSystemMessage(Component.translatable("message.bloxpiece.handcuffs.released_other", mob.getDisplayName()), true);
        return true;
    }

    /** Retire les menottes d'un mob (clé ou expiration) : il reprend son arme. */
    private static void freeMob(Mob mob) {
        mob.removeAttached(ModAttachments.HANDCUFFED);
        mob.removeAttached(ModAttachments.HANDCUFF_SECONDS);
        CUFFED_MOBS.remove(mob);
        restoreWeapons(mob);
        ServerLevel level = (ServerLevel) mob.level();
        level.playSound(null, mob.getX(), mob.getY() + 1.0, mob.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.HOSTILE, 0.6F, 1.8F);
    }

    /**
     * Compte à rebours des menottes (une fois par seconde) : à zéro, elles s'ouvrent d'elles-mêmes et disparaissent. Une entité
     * menottée avant l'ajout du compteur (ancienne sauvegarde) repart pour 5 minutes.
     */
    private static boolean countDown(Entity entity) {
        Integer left = entity.getAttached(ModAttachments.HANDCUFF_SECONDS);
        int next = (left == null ? DURATION_SECONDS : left) - 1;
        if (next <= 0) {
            return true;
        }
        entity.setAttached(ModAttachments.HANDCUFF_SECONDS, next);
        return false;
    }

    /** Menottes ouvertes par le temps (joueur) : comme avec la clé, avec son propre message. */
    private static void expire(ServerPlayer player) {
        release(player, player);
        player.sendSystemMessage(Component.translatable("message.bloxpiece.handcuffs.expired"), true);
    }

    /** Range l'arme (et la main secondaire) : un squelette range son arc, un pirate son sabre ou son pistolet. */
    private static void stowWeapons(Mob mob) {
        ItemStack main = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off = mob.getItemBySlot(EquipmentSlot.OFFHAND);
        if (main.isEmpty() && off.isEmpty()) {
            return;
        }
        STORED_WEAPONS.put(mob.getUUID(), new ItemStack[] {main.copy(), off.copy()});
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    private static void restoreWeapons(Mob mob) {
        ItemStack[] stored = STORED_WEAPONS.remove(mob.getUUID());
        if (stored != null) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, stored[0]);
            mob.setItemSlot(EquipmentSlot.OFFHAND, stored[1]);
        }
    }

    /** Distance (blocs) à laquelle un mob menotté prend peur d'un joueur et s'enfuit. */
    private static final double FLEE_RADIUS = 14.0;
    private static final double FLEE_SPEED = 1.3;

    /**
     * Plus de cible ni d'agressivité, aucune visée : le mob menotté n'attaque plus personne. (MobMixin l'empêche en plus de
     * reprendre une cible ; les IA à "cerveau" comme les piglins gardent leur cible dans une mémoire, effacée ici.)
     */
    private static void neutralize(Mob mob) {
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
        if (mob instanceof ArmedHumanoid pirate) {
            pirate.setAiming(false); // bras baissé (pas de pose de visée)
        }
    }

    /** Le mob menotté a peur : il s'éloigne du joueur le plus proche (comme un villageois effrayé). */
    private static void flee(Mob mob) {
        if (!(mob instanceof PathfinderMob walker) || mob.tickCount % 10 != 0) {
            return;
        }
        Player player = mob.level().getNearestPlayer(mob, FLEE_RADIUS);
        if (player == null || player.isSpectator() || player.isCreative()) {
            return;
        }
        Vec3 away = DefaultRandomPos.getPosAway(walker, 16, 7, player.position());
        if (away != null) {
            walker.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
        }
    }

    /** Les dégâts viennent-ils d'un mob menotté (coup direct, ou projectile qu'il a tiré) ? Ils sont alors annulés. */
    private static boolean fromCuffedMob(DamageSource source) {
        return (source.getEntity() instanceof Mob attacker && isCuffed(attacker))
                || (source.getDirectEntity() instanceof Mob direct && isCuffed(direct));
    }

    /** -15 % de vitesse pour un porteur de fruit neutralisé par le Granite Marin (vérifié à chaque tick : ajouté / retiré au bon moment). */
    private static void updateSpeed(ServerPlayer player, boolean slowed) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean has = speed.getModifier(SPEED_MODIFIER) != null;
        if (slowed && !has) {
            speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER, FRUIT_SLOWNESS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!slowed && has) {
            speed.removeModifier(SPEED_MODIFIER);
        }
    }

    /**
     * À chaque tick serveur : un porteur de fruit neutralisé par le Granite Marin (menotté, ou objet en Granite Marin sur lui, voir
     * Seastone) est ralenti de 15 % et ne peut pas rester transformé.
     */
    public static void tick(MinecraftServer server) {
        // Mobs menottés : toujours neutralisés (leurs IA essaient de reprendre une cible)
        CUFFED_MOBS.removeIf(mob -> mob.isRemoved() || !isCuffed(mob));
        // Compte à rebours des menottes, une fois par seconde (5 minutes au total)
        if (server.getTickCount() % 20 == 0) {
            for (Mob mob : new ArrayList<>(CUFFED_MOBS)) {
                if (countDown(mob)) {
                    freeMob(mob);
                }
            }
            for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
                if (isCuffed(player) && countDown(player)) {
                    expire(player);
                }
            }
        }
        for (Mob mob : CUFFED_MOBS) {
            neutralize(mob);
            flee(mob);
            if (!mob.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() || !mob.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
                stowWeapons(mob); // un objet ramassé entre-temps est rangé aussi
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean hasFruit = Fruits.get(player.getAttached(ModAttachments.FRUIT)) != null;
            boolean suppressed = hasFruit && Seastone.suppresses(player);
            updateSpeed(player, suppressed);
            if ((suppressed || isCuffed(player)) && BuddhaTransformAbility.isActive(player)) {
                BuddhaTransformAbility.reset(player); // détransformé sur-le-champ
            }
            if (suppressed || isCuffed(player)) {
                // Kame Kame no Mi : carapace retirée, carapace tournoyante ou rétraction interrompue
                nolan.bloxpiece.ability.Kame.cancelPowers(player);
            }
        }
    }

    /** Un joueur menotté ne peut rien faire de ses mains (sauf utiliser une Clé de Menottes). Côté client ET serveur. */
    private static InteractionResult blockedUnlessKey(Player player, InteractionHand hand) {
        if (isCuffed(player) && !isKey(player.getItemInHand(hand))) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    public static void initialize() {
        UseItemCallback.EVENT.register((player, level, hand) -> blockedUnlessKey(player, hand));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> blockedUnlessKey(player, hand));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> blockedUnlessKey(player, hand));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                isCuffed(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                isCuffed(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        // À la mort, les menottes tombent au sol (le joueur réapparaît libre) ; un mob lâche aussi l'arme qu'il avait rangée
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player && isCuffed(player)) {
                player.spawnAtLocation((ServerLevel) player.level(), new ItemStack(ModItems.SEASTONE_HANDCUFFS));
                player.removeAttached(ModAttachments.HANDCUFFED);
                player.removeAttached(ModAttachments.HANDCUFF_SECONDS);
            } else if (entity instanceof Mob mob && isCuffed(mob) && mob.level() instanceof ServerLevel level) {
                mob.spawnAtLocation(level, new ItemStack(ModItems.SEASTONE_HANDCUFFS));
                ItemStack[] stored = STORED_WEAPONS.remove(mob.getUUID());
                if (stored != null) {
                    for (ItemStack stack : stored) {
                        if (!stack.isEmpty()) {
                            mob.spawnAtLocation(level, stack);
                        }
                    }
                }
                CUFFED_MOBS.remove(mob);
            }
        });
        // Un mob menotté ne peut infliger AUCUN dégât (coups, projectiles)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !fromCuffedMob(source));
        // Mob menotté rechargé (sauvegarde, chunk) : de nouveau suivi et neutralisé. Les pirates prisonniers de la Base de la
        // Marine arrivent ainsi déjà menottés : leurs menottes sont écrites dans le fichier de la structure (marine_base.nbt).
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Mob mob && isCuffed(mob)) {
                CUFFED_MOBS.add(mob);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof Mob mob) {
                CUFFED_MOBS.remove(mob);
            }
        });
    }
}
