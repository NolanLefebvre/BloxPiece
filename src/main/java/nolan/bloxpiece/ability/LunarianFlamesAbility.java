package nolan.bloxpiece.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Race Lunarian, niveau 10 : FLAMMES LUNARIAN — MODE DÉFENSE / MODE VITESSE. Chaque utilisation bascule de mode :
 * <ul>
 *   <li>Allumer (mode Défense) : les ailes se gonflent et s'ouvrent, le buste se cambre, les flammes naissent entre les ailes et
 *       grandissent pendant Lunarian.TRANSITION ticks ; ensuite seulement, les bonus de défense et la pénalité de dégâts
 *       s'appliquent (Lunarian).</li>
 *   <li>Éteindre (mode Vitesse) : les ailes se resserrent, les flammes diminuent puis s'éteignent ; ensuite, la défense est
 *       retirée et la vitesse revient.</li>
 * </ul>
 * Pas une attaque : aucune cible, juste la transition animée ({@link #DURATION}) et un court délai de recharge.
 */
public class LunarianFlamesAbility implements Ability {

    public static final int DURATION = 20;
    public static final int COOLDOWN = 20 * 3;

    private final int level;

    public LunarianFlamesAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "lunarian_flame_mode";
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
        return !Lunarian.refuseBusy(player);
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        boolean on = !Lunarian.flamesOn(player);
        Lunarian.setAction(player, Lunarian.FLAMES);
        Lunarian.setFlames(player, on);
        if (on) {
            level.playSound(null, player.getX(), player.getY() + 1.4, player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
            level.playSound(null, player.getX(), player.getY() + 1.4, player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6F, 0.6F);
        } else {
            level.playSound(null, player.getX(), player.getY() + 1.4, player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.9F);
        }
        player.sendSystemMessage(Component.translatable(on ? "message.bloxpiece.lunarian.defense" : "message.bloxpiece.lunarian.speed"), true);
        Goro.start(new Goro.Session() {
            int t;

            @Override
            public boolean tick() {
                if (!Lunarian.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                if (on && t % 3 == 0 && t < Lunarian.TRANSITION) { // braises qui s'envolent pendant que les flammes grandissent
                    level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.5, player.getZ(), 3, 0.25, 0.3, 0.25, 0.02);
                }
                if (!on && t % 3 == 0 && t < Lunarian.TRANSITION) {
                    level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.5, player.getZ(), 3, 0.25, 0.3, 0.25, 0.02);
                }
                if (t == Lunarian.TRANSITION && on) {
                    level.playSound(null, player.getX(), player.getY() + 1.4, player.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.8F);
                }
                if (t >= DURATION) {
                    Lunarian.clearAction(player, Lunarian.FLAMES);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Lunarian.clearAction(player, Lunarian.FLAMES);
            }
        });
    }
}
