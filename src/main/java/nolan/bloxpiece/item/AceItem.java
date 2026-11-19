package nolan.bloxpiece.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.fx.AceSlash;
import nolan.bloxpiece.haki.HakiManager;
import nolan.bloxpiece.haki.HakiType;
import nolan.bloxpiece.handcuffs.Handcuffs;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModParticles;

/**
 * Ace, le sabre de Gol D. Roger. En plus des coups normaux (sabre : voir PirateSaberItem), attaque spéciale « Lame dévastatrice
 * du Haki des Rois » (AceSlash), qui se CHARGE avec le système d'utilisation de Minecraft (comme l'arc) :
 * <ol>
 *   <li><b>clic droit maintenu</b> : le joueur lève l'épée en diagonale et la garde levée ; le Haki des Rois s'accumule dessus
 *       (éclairs noirs et rouges sur la lame et autour de la main, de plus en plus nombreux et intenses) jusqu'à la charge
 *       maximale ({@link AceSlash#MAX_CHARGE_TICKS}) ;</li>
 *   <li><b>relâchement</b> : frappe en diagonale, la lame part à la fin du coup, d'autant plus grande que la charge était longue
 *       (modérément : +30 % au maximum). Maintenu au-delà de {@link AceSlash#MAX_HOLD_TICKS}, le coup part tout seul.</li>
 * </ol>
 * Deux diagonales, même bras droit et même épée : du haut à droite vers le bas à gauche, ou du haut à gauche vers le bas à
 * droite (geste de revers). Le serveur en tire une au hasard (une chance sur deux) à chaque attaque, chargée ou non ; la
 * préparation, la frappe, le croissant et les éclairs la suivent (attachment ACE_REVERSE, synchronisé).
 * <p>
 * Réservée aux joueurs qui ont débloqué le Haki des Rois (HakiManager, le déblocage du mod) : sinon ni charge ni lancer, et un
 * message s'affiche. Vérifié par le serveur au début de la charge, pendant et au relâchement.
 * <p>
 * Recharge : celle de Minecraft sur l'objet ({@link #COOLDOWN_TICKS}, comme les armes à feu, FlintlockItem) : l'icône d'Ace se
 * grise puis se remplit ; pendant ce temps, impossible de recommencer à charger. Un seul lancer par relâchement (l'état de charge,
 * attachment ACE_CHARGE, est consommé). Tout est décidé par le serveur ; la charge et le coup sont synchronisés (attachments
 * ACE_CHARGE et ACE_SLASH), donc tous les joueurs voient le bras levé et les éclairs.
 */
public class AceItem extends PirateSaberItem {

    /** Recharge de l'attaque spéciale (ticks, 20 = 1 s). */
    public static final int COOLDOWN_TICKS = 300;

    public AceItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (Handcuffs.isCuffed(player)) {
            return InteractionResult.FAIL; // menotté : aucun pouvoir
        }
        // Haki des Rois obligatoire (déblocage du mod : HakiManager ; vérifié par le serveur, le client le connaît aussi car
        // l'attachment des Hakis débloqués lui est synchronisé : pas de charge affichée pour rien)
        if (!HakiManager.isUnlocked(player, HakiType.CONQUEROR)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("message.bloxpiece.ace_needs_conqueror"), true);
            }
            return InteractionResult.FAIL;
        }
        // (pendant la recharge, Minecraft n'appelle même pas cette méthode : la charge ne peut pas commencer)
        if (level instanceof ServerLevel serverLevel) {
            player.setAttached(ModAttachments.ACE_CHARGE, (int) serverLevel.getGameTime());
            // diagonale tirée au hasard PAR LE SERVEUR (une chance sur deux), dès le début : la préparation, la frappe, le
            // croissant et les éclairs la suivent ; synchronisée avec tous les joueurs (attachment ACE_REVERSE)
            player.setAttached(ModAttachments.ACE_REVERSE, serverLevel.getRandom().nextBoolean());
            serverLevel.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.PLAYERS, 1.0F, 0.7F);
        }
        player.startUsingItem(hand);
        // CONSUME : action réussie SANS le balancement du bras de Minecraft (l'animation est la nôtre)
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return AceSlash.MAX_HOLD_TICKS;
    }

    /** Charge de 0 à 1 selon le temps de maintien. */
    private static float charge(int usedTicks) {
        return Math.min(1.0F, usedTicks / (float) AceSlash.MAX_CHARGE_TICKS);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(level instanceof ServerLevel serverLevel) || entity.getAttached(ModAttachments.ACE_CHARGE) == null) {
            return;
        }
        if (!HakiManager.isUnlocked(entity, HakiType.CONQUEROR)) {
            entity.removeAttached(ModAttachments.ACE_CHARGE); // Haki des Rois retiré pendant la charge : rien ne partira
            entity.stopUsingItem();
            return;
        }
        int used = AceSlash.MAX_HOLD_TICKS - remainingUseDuration;
        chargeFx(serverLevel, entity, used, charge(used));
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level instanceof ServerLevel serverLevel) {
            this.strike(serverLevel, entity, stack, AceSlash.MAX_HOLD_TICKS - timeLeft);
        }
        return true;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // maintenu jusqu'au bout : le coup part tout seul (pas de charge infinie)
        if (level instanceof ServerLevel serverLevel) {
            this.strike(serverLevel, entity, stack, AceSlash.MAX_HOLD_TICKS);
        }
        return stack;
    }

    /** Relâchement : frappe en diagonale, puis lancer de la lame à la fin du coup. Une seule fois par charge. */
    private void strike(ServerLevel level, LivingEntity entity, ItemStack stack, int used) {
        Integer chargeStart = entity.getAttached(ModAttachments.ACE_CHARGE);
        if (chargeStart == null) {
            return; // déjà lancée (ou aucune charge en cours) : jamais deux attaques pour un même relâchement
        }
        entity.removeAttached(ModAttachments.ACE_CHARGE);
        if (!HakiManager.isUnlocked(entity, HakiType.CONQUEROR)) {
            return; // jamais de lancer sans le Haki des Rois, même au relâchement
        }
        int now = (int) level.getGameTime();
        // l'épée doit d'abord être levée : relâché trop tôt, la frappe attend la fin de la levée
        int strikeStart = Math.max(now, chargeStart + (int) Math.ceil(AceSlash.RAISE_TICKS));
        entity.setAttached(ModAttachments.ACE_SLASH, strikeStart);
        boolean reverse = Boolean.TRUE.equals(entity.getAttached(ModAttachments.ACE_REVERSE));
        AceSlash.start(level, entity, charge(used), strikeStart - now + (int) AceSlash.STRIKE_TICKS,
                nolan.bloxpiece.fx.AceSlashShape.roll(reverse));
        if (entity instanceof Player player) {
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        }
    }

    /**
     * Accumulation du Haki des Rois, côté serveur : brume noire au sol et grondement qui montent avec la charge ; pleine charge :
     * un signal sonore. Les éclairs qui jaillissent de la lame sont dessinés par chaque client dans le repère de l'épée
     * (AceChargeLightning, d'après l'attachment ACE_CHARGE synchronisé : tous les joueurs les voient), pour partir exactement
     * de la lame.
     */
    private static void chargeFx(ServerLevel level, LivingEntity entity, int used, float charge) {
        if (used < AceSlash.RAISE_TICKS) {
            return; // l'épée se lève encore
        }
        if (used % 4 == 0) {
            level.sendParticles(ModParticles.HAKI_MIST, entity.getX(), entity.getY() + 0.2, entity.getZ(), 1 + Math.round(3 * charge),
                    0.5, 0.1, 0.5, 0.02);
        }
        if (used % 12 == 0) {
            level.playSound(null, entity.getX(), entity.getY() + 1.0, entity.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.PLAYERS, 0.2F + 0.4F * charge, 1.4F - 0.5F * charge);
        }
        if (used == AceSlash.MAX_CHARGE_TICKS) {
            level.playSound(null, entity.getX(), entity.getY() + 1.0, entity.getZ(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }
}
