package nolan.bloxpiece.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import nolan.bloxpiece.fx.CyborgShield;

/**
 * Race Cyborg, niveau 20 : BOUCLIER ÉNERGÉTIQUE. Les deux bras levés au ciel ({@link #DEPLOY} ticks), un dôme d'hexagones bleus
 * (CyborgShield) se forme autour du joueur, grandit jusqu'à {@link #RADIUS} blocs, le protège {@link #DURATION} ticks puis se
 * dissipe. Protection côté serveur : aucun dégât ne l'atteint (Cyborg.shielded), les entités sont repoussées hors du dôme, les
 * projectiles renvoyés (CyborgShield). Les membres de sa Party peuvent entrer.
 */
public class CyborgEnergyShieldAbility implements Ability {

    public static final int DEPLOY = 20, DURATION = 20 * 8;
    public static final int COOLDOWN = 20 * 45;
    /**
     * Rayon de la sphère (blocs), centrée sur le milieu du corps (CyborgShield.CENTER_Y) : serrée autour du joueur (1,8 bloc de
     * haut : 1,4 bloc de marge au-dessus de la tête, 2,3 sur les côtés), mais assez grande pour que rien ne l'atteigne.
     */
    public static final float RADIUS = 2.3F;

    private final int level;

    public CyborgEnergyShieldAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "cyborg_energy_shield";
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
        return !Cyborg.refuseBusy(player) && !Cyborg.shielded(player);
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Cyborg.setAction(player, Cyborg.SHIELD);
        CyborgShield shield = CyborgShield.spawn(level, player, RADIUS, DEPLOY, DEPLOY + DURATION);
        Cyborg.shieldUp(player, shield);
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.4F);
        Goro.start(new Goro.Session() {
            int t;

            @Override
            public boolean tick() {
                t++;
                if (!Cyborg.valid(player, level)) {
                    cancel();
                    return false;
                }
                if (t < DEPLOY && t % 2 == 0) { // l'énergie se rassemble autour du joueur
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.2, player.getZ(), 6,
                            RADIUS * 0.4, 1.0, RADIUS * 0.4, 0.2);
                }
                if (t == DEPLOY) {
                    Cyborg.clearAction(player, Cyborg.SHIELD); // bras redescendus, le dôme reste
                    level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BEACON_POWER_SELECT,
                            SoundSource.PLAYERS, 1.0F, 1.6F);
                }
                if (!shield.isAlive()) {
                    Cyborg.shieldDown(player.getUUID());
                    level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BEACON_DEACTIVATE,
                            SoundSource.PLAYERS, 1.0F, 1.4F);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Cyborg.clearAction(player, Cyborg.SHIELD);
                Cyborg.shieldDown(player.getUUID());
                shield.collapse();
            }
        });
    }
}
