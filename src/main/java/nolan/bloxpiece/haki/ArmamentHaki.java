package nolan.bloxpiece.haki;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Haki de l'Armement (Busoshoku). Appui : le bras principal se couvre de Haki (lueur en fusion violacée, puis revêtement noir
 * métallique : rendu client HakiArmamentLayer) — dégâts au corps à corps et des armes fortement augmentés. Touche MAINTENUE
 * ({@link #HOLD_TICKS} ticks) : le revêtement se propage à tout le corps — dégâts augmentés et dégâts subis fortement réduits.
 * Nouvel appui : le Haki se retire. Pas de durée maximale : il tient tant que la jauge de Haki n'est pas vide.
 */
public final class ArmamentHaki implements Haki {

    public static final int ARM = 1;
    public static final int FULL_BODY = 2;

    /** Maintien de la touche avant le corps entier (0,6 s) : mesuré CÔTÉ CLIENT, qui envoie alors l'action HOLD. */
    public static final int HOLD_TICKS = 12;
    private static final int COOLDOWN_MANUAL = 20 * 4;

    /** Bonus de dégâts : bras +70 % ; corps entier +45 %. */
    private static final double ARM_DAMAGE = 0.70;
    private static final double FULL_DAMAGE = 0.45;
    /** Corps entier : dégâts subis x 0,55 (45 % de réduction). */
    public static final float FULL_BODY_DAMAGE_TAKEN = 0.55F;

    private static final Identifier DAMAGE_MODIFIER = BloxPiece.id("haki_armament_damage");

    private final Map<UUID, Long> activeSince = new HashMap<>();

    @Override
    public HakiType type() {
        return HakiType.ARMAMENT;
    }

    public static int mode(net.minecraft.world.entity.Entity entity) {
        Integer m = entity.getAttached(ModAttachments.HAKI_ARMAMENT);
        return m == null ? 0 : m;
    }

    @Override
    public void press(ServerPlayer player) {
        if (isActive(player)) {
            stop(player);
            HakiManager.startCooldown(player, type(), COOLDOWN_MANUAL);
            return;
        }
        long now = player.level().getGameTime();
        player.setAttached(ModAttachments.HAKI_ARMAMENT, ARM);
        this.activeSince.put(player.getUUID(), now);
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9F, 0.6F);
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.6F);
    }

    /**
     * Touche maintenue (action HOLD envoyée par le client après {@link #HOLD_TICKS} ticks) : le Haki se propage du bras à tout
     * le corps. Si le bras n'était pas encore couvert (appui pendant une recharge déjà finie, etc.), il est d'abord activé.
     */
    public void hold(ServerPlayer player) {
        if (mode(player) == FULL_BODY) {
            return;
        }
        if (mode(player) == 0) {
            press(player);
        }
        player.setAttached(ModAttachments.HAKI_ARMAMENT, FULL_BODY);
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 0.5F);
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 0.8F);
        level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.35, 0.6, 0.35, 0.01);
    }

    @Override
    public void tick(ServerPlayer player) {
        int mode = mode(player);
        if (mode == 0) {
            setDamageBonus(player, 0.0);
            return;
        }
        long now = player.level().getGameTime();
        // arme (ou tout objet tenu qui n'est pas un bloc) imprégnée de Haki : léger bonus en plus, proportionnel aux dégâts
        setDamageBonus(player, (mode == FULL_BODY ? FULL_DAMAGE : ARM_DAMAGE) + (isImbued(player, player.getMainHandItem()) ? WEAPON_BONUS : 0.0));
        // fumerolles sombres autour du revêtement
        if (player.tickCount % 6 == 0 && player.level() instanceof ServerLevel level) {
            Vec3 c = player.position().add(0.0, mode == FULL_BODY ? 1.0 : 1.2, 0.0);
            double spread = mode == FULL_BODY ? 0.35 : 0.15;
            level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 1, spread, mode == FULL_BODY ? 0.6 : 0.1, spread, 0.0);
        }
        // plus de durée maximale : le Haki reste actif jusqu'à ce que la jauge de Haki soit vide (HakiEnergy) ou qu'on le retire
    }

    /** Bonus des dégâts quand l'objet tenu est imprégné de Haki (+15 %, multiplicateur des dégâts totaux). */
    private static final double WEAPON_BONUS = 0.15;

    /**
     * L'objet tenu peut-il être imprégné de Haki (arme, outil, objet quelconque ; pas la main vide ni un bloc) ? Réservé au job
     * Épéiste : les autres jobs ne propagent pas le Haki sur leur arme.
     */
    public static boolean isImbued(net.minecraft.world.entity.Entity holder, net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof net.minecraft.world.item.BlockItem)
                && nolan.bloxpiece.job.Jobs.is(holder, nolan.bloxpiece.job.Job.SWORDMAN);
    }

    private static void setDamageBonus(ServerPlayer player, double bonus) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) {
            return;
        }
        AttributeModifier current = attack.getModifier(DAMAGE_MODIFIER);
        if (current != null && current.amount() == bonus) {
            return;
        }
        if (current != null) {
            attack.removeModifier(DAMAGE_MODIFIER);
        }
        if (bonus > 0.0) {
            attack.addTransientModifier(new AttributeModifier(DAMAGE_MODIFIER, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @Override
    public boolean isActive(ServerPlayer player) {
        return mode(player) != 0;
    }

    @Override
    public void stop(ServerPlayer player) {
        if (mode(player) != 0) {
            player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS, 0.6F, 1.4F);
        }
        player.removeAttached(ModAttachments.HAKI_ARMAMENT);
        this.activeSince.remove(player.getUUID());
        setDamageBonus(player, 0.0);
    }
}
