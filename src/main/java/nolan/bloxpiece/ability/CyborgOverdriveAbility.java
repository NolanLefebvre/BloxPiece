package nolan.bloxpiece.ability;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import nolan.bloxpiece.registry.ModAttachments;

/**
 * Race Cyborg, niveau 30 : SURCHARGE MOTEUR. Démarrage ({@link Cyborg#OVERDRIVE_WINDUP} ticks : le moteur monte en puissance,
 * énergie et décharges autour du corps, bras incandescent), puis surcharge {@link Cyborg#OVERDRIVE_TICKS} ticks (bonus de
 * Cyborg : dégâts, vitesse, vitesse d'attaque, armure, résistance au recul), puis ÉPUISEMENT {@link Cyborg#EXHAUST_TICKS} ticks
 * (malus de dégâts, de vitesse et de vitesse d'attaque, jamais paralysant). Impossible de relancer pendant l'épuisement.
 */
public class CyborgOverdriveAbility implements Ability {

    public static final int COOLDOWN = 20 * 120;

    private final int level;

    public CyborgOverdriveAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "cyborg_engine_overdrive";
    }

    @Override
    public int cooldownTicks() {
        return COOLDOWN;
    }

    @Override
    public int requiredLevel() {
        return level;
    }

    @Override
    public String nameKey() {
        return "race.bloxpiece.attack." + id();
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        if (player.getAttached(ModAttachments.CYBORG_EXHAUSTED_UNTIL) != null) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.cyborg.still_exhausted"), true);
            return false;
        }
        return !Cyborg.refuseBusy(player) && !Cyborg.overdriveActive(player);
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Cyborg.setAction(player, Cyborg.OVERDRIVE);
        Goro.start(new Goro.Session() {
            int t;

            @Override
            public boolean tick() {
                t++;
                if (!Cyborg.valid(player, level)) {
                    cancel();
                    return false;
                }
                float k = t / (float) Cyborg.OVERDRIVE_WINDUP;
                if (t % 3 == 0) { // le moteur monte en régime : son de plus en plus aigu, énergie qui s'accumule
                    level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BEACON_AMBIENT,
                            SoundSource.PLAYERS, 0.8F, 0.6F + 1.2F * k);
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), (int) (2 + 8 * k),
                        0.5, 0.8, 0.5, 0.1 + 0.3 * k);
                if (t >= Cyborg.OVERDRIVE_WINDUP) {
                    Cyborg.clearAction(player, Cyborg.OVERDRIVE);
                    Cyborg.startOverdrive(player);
                    level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.2, 0.4, 0.2, 0.35);
                    level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.4, 0.4, 0.4, 0.0);
                    level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.GENERIC_EXPLODE,
                            SoundSource.PLAYERS, 0.9F, 1.6F);
                    player.sendSystemMessage(Component.translatable("message.bloxpiece.cyborg.overdrive", Cyborg.OVERDRIVE_TICKS / 20)
                            .withStyle(ChatFormatting.GOLD), true);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Cyborg.clearAction(player, Cyborg.OVERDRIVE);
            }
        });
    }
}
