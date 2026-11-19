package nolan.bloxpiece.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.AceSlash;
import nolan.bloxpiece.fx.HakiBolt;
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
        AceSlash.start(level, entity, charge(used), strikeStart - now + (int) AceSlash.STRIKE_TICKS);
        if (entity instanceof Player player) {
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        }
    }

    /** Axe de la lame dans le modèle d'Ace (pixels du modèle : garde, pointe ; models/item/ace.json). */
    private static final double[] GUARD = {4.6, 4.6}, TIP = {18.8, 23.45};
    /** Les éclairs naissent sur la lame entre cette fraction (0 = garde) et la pointe : jamais sur la poignée ni la garde. */
    private static final double BLADE_START = 0.2;

    /**
     * Position dans le monde d'un point du modèle d'Ace (pixels du modèle, plan médian z = 8) quand l'épée est LEVÉE pendant la
     * charge : même chaîne que le rendu (modèle du joueur, bras droit dans la pose levée de PlayerModelMixin, placement de l'objet
     * en main, transformation « tenu en main, 3e personne » d'Ace). Les éclairs partent ainsi de la lame elle-même.
     */
    private static Vec3 heldPoint(LivingEntity entity, double mx, double my) {
        double[] p = {mx / 16.0, my / 16.0, 0.5};
        // transformation d'affichage d'Ace (3e personne, main droite), puis placement de l'objet dans la main
        add(p, -0.5, -0.5, -0.5);
        mul(p, 0.85, 0.85, 0.85);
        rotZ(p, -145.0);
        rotY(p, 90.0);
        rotX(p, -180.0);
        add(p, 0.0, 5.56 / 16.0, 0.05 / 16.0);
        add(p, 1.0 / 16.0, 0.125, -0.625);
        rotY(p, 180.0);
        rotX(p, -90.0);
        // bras droit levé (PlayerModelMixin : xRot -3,7 + 0,7 x regard, zRot = plan de la lame), pivot de l'épaule
        rotX(p, Math.toDegrees(-3.7 + 0.7 * Math.toRadians(entity.getXRot())));
        rotZ(p, -(90.0 + nolan.bloxpiece.fx.AceSlashShape.ROLL));
        add(p, -5.0 / 16.0, 2.0 / 16.0, 0.0);
        // modèle du joueur -> monde (comme le rendu des joueurs : retourné, échelle 0,9375, tourné selon le corps)
        add(p, 0.0, -1.501, 0.0);
        mul(p, -0.9375, -0.9375, 0.9375);
        rotY(p, 180.0 - entity.yBodyRot);
        double s = entity.getScale();
        return entity.position().add(p[0] * s, p[1] * s, p[2] * s);
    }

    private static void add(double[] p, double x, double y, double z) {
        p[0] += x;
        p[1] += y;
        p[2] += z;
    }

    private static void mul(double[] p, double x, double y, double z) {
        p[0] *= x;
        p[1] *= y;
        p[2] *= z;
    }

    private static void rotX(double[] p, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), sn = Math.sin(a), y = p[1], z = p[2];
        p[1] = y * c - z * sn;
        p[2] = y * sn + z * c;
    }

    private static void rotY(double[] p, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), sn = Math.sin(a), x = p[0], z = p[2];
        p[0] = x * c + z * sn;
        p[2] = -x * sn + z * c;
    }

    private static void rotZ(double[] p, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), sn = Math.sin(a), x = p[0], y = p[1];
        p[0] = x * c - y * sn;
        p[1] = x * sn + y * c;
    }

    /**
     * Accumulation du Haki des Rois sur l'épée levée : éclairs du Haki des Rois (entités HakiBolt, comme la première version) qui
     * JAILLISSENT de la lame dans toutes les directions, de plus en plus nombreux et grands avec la charge, et étincelles le long
     * de la lame. Ils naissent toujours sur la lame (de {@link #BLADE_START} à la pointe), jamais sur la poignée. Brume noire au
     * sol et grondement qui monte ; pleine charge : un signal sonore.
     */
    private static void chargeFx(ServerLevel level, LivingEntity entity, int used, float charge) {
        if (used < AceSlash.RAISE_TICKS) {
            return; // l'épée se lève encore
        }
        RandomSource random = level.getRandom();
        Vec3 guard = heldPoint(entity, GUARD[0], GUARD[1]);
        Vec3 axis = heldPoint(entity, TIP[0], TIP[1]).subtract(guard);
        int sparks = 1 + Math.round(4 * charge);
        for (int i = 0; i < sparks; i++) {
            Vec3 p = guard.add(axis.scale(BLADE_START + random.nextDouble() * (1.0 - BLADE_START)));
            level.sendParticles(ModParticles.HAKI_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.03 + 0.05 * charge);
        }
        // éclairs qui jaillissent de la lame, plus fréquents, nombreux et grands avec la charge
        if (random.nextFloat() < 0.15F + 0.65F * charge) {
            int n = 1 + (charge > 0.6F && random.nextBoolean() ? 1 : 0);
            for (int i = 0; i < n; i++) {
                Vec3 p = guard.add(axis.scale(BLADE_START + random.nextDouble() * (1.0 - BLADE_START)));
                float size = 0.22F + 0.35F * charge + random.nextFloat() * 0.1F;
                HakiBolt.spawn(level, p, (float) (random.nextDouble() * 360.0), 20.0F + random.nextFloat() * 140.0F, -size,
                        4 + random.nextInt(4));
            }
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
