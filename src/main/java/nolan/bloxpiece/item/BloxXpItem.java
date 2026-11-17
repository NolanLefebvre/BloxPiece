package nolan.bloxpiece.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.progression.LevelCurve;
import nolan.bloxpiece.progression.PlayerProgress;
import nolan.bloxpiece.progression.Progression;

/**
 * Cristal d'XP (Moussaillon, Pirate, Supernova, Empereur) : clic droit = {@link #xp} points d'XP BloxPiece (le système de
 * niveaux du mod, PAS l'XP vanilla), via Progression.addXp : passages de niveau (plusieurs d'un coup si besoin), PV et dégâts des
 * fruits mis à jour, synchronisation avec le client. Consomme 1 objet (sauf en créatif). Inutilisable au niveau maximum.
 * Le montant est affiché dans l'infobulle (client : BloxXpTooltip).
 */
public class BloxXpItem extends Item {

    private final long xp;

    public BloxXpItem(Properties properties, long xp) {
        super(properties);
        this.xp = xp;
    }

    public long xp() {
        return this.xp;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        PlayerProgress before = Progression.get(serverPlayer);
        if (LevelCurve.isMaxLevel(before.level())) {
            serverPlayer.sendSystemMessage(Component.translatable("message.bloxpiece.xp_item.max_level"), true);
            return InteractionResult.FAIL;
        }
        PlayerProgress after = Progression.addXp(serverPlayer, this.xp);
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isCreative()) {
            stack.shrink(1);
        }

        // Son de ramassage d'XP, particules d'énergie autour du joueur ; fanfare si un niveau est passé
        double x = player.getX(), y = player.getY() + 1.0, z = player.getZ();
        serverLevel.playSound(null, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
        serverLevel.sendParticles(ParticleTypes.ENCHANT, x, y + 0.5, z, 40, 0.6, 0.8, 0.6, 0.6);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 12, 0.5, 0.7, 0.5, 0.0);
        int gained = after.level() - before.level();
        if (gained > 0) {
            serverLevel.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
            serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 30, 0.5, 0.8, 0.5, 0.4);
            serverPlayer.sendSystemMessage(Component.translatable("message.bloxpiece.xp_item.level_up", after.level(), gained), true);
        } else {
            serverPlayer.sendSystemMessage(Component.translatable("message.bloxpiece.xp_item.gained", this.xp), true);
        }
        return InteractionResult.SUCCESS;
    }
}
