package nolan.bloxpiece.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Race Minks, niveau 10 : GRIFFES ÉLECTRIQUES. Le joueur croise les bras devant lui, l'électricité crépite, puis il les écarte :
 * des griffes électriques jaillissent de ses deux mains (animation {@link #ACTIVATE} ticks ; modèles : MinksLayer). Pendant
 * Minks.CLAWS_TICKS, chaque coup de poing À MAINS NUES ajoute des dégâts électriques et une courte paralysie (Minks : calcul
 * côté serveur, une arme ou un objet en main n'ajoute rien). Sulong : bonus et paralysie x Minks.SULONG_POWER.
 */
public class MinksClawsAbility implements Ability {

    public static final int ACTIVATE = 12;
    public static final int COOLDOWN = 20 * 30;

    private final int level;

    public MinksClawsAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "minks_electric_claws";
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
        return !Minks.refuseBusy(player);
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Minks.setAction(player, Minks.CLAWS);
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.7F, 1.8F);
        Goro.start(new Goro.Session() {
            int t;

            @Override
            public boolean tick() {
                if (!Minks.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                if (t == ACTIVATE / 2) { // les bras s'écartent : les griffes jaillissent
                    Minks.showClaws(player, Minks.CLAWS_TICKS);
                    level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.TRIDENT_THUNDER.value(),
                            SoundSource.PLAYERS, 0.35F, 1.7F);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.1, player.getZ(), 24, 0.5, 0.3, 0.5, 0.25);
                }
                if (t >= ACTIVATE) {
                    Minks.clearAction(player);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Minks.clearAction(player);
            }
        });
    }
}
