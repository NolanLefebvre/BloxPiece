package nolan.bloxpiece.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import nolan.bloxpiece.fx.MinksFx;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Race Minks, niveau 50 : TRANSFORMATION SULONG. Uniquement la NUIT, DEUX FOIS AU PLUS par nuit (PER_NIGHT) (vérifié par le serveur : dimension avec un cycle jour /
 * nuit, heure du monde entre {@link #NIGHT_START} et {@link #NIGHT_END}) ; sinon refusée avec un message, sans consommer la
 * recharge. Cinématique : préparation et montée en puissance ({@link #CHARGE} ticks : le joueur se ramasse, l'électricité
 * s'accumule puis parcourt tout son corps), explosion de transformation, posture de puissance ({@link #REVEAL} ticks), et Sulong pendant Minks.SULONG_TICKS (30 s) : oreilles et queue blanches et duveteuses, yeux lumineux, aura
 * électrique (client : MinksLayer), bonus d'attributs (Minks), trois autres attaques renforcées.
 */
public class MinksSulongAbility implements Ability {

    public static final int CHARGE = 30;
    /** Après l'explosion : posture de puissance (animation, ticks) ; le Sulong, lui, a déjà commencé. */
    public static final int REVEAL = 16;
    public static final int COOLDOWN = 20 * 180;
    /** Nuit (heure du monde, ticks dans la journée) : comme le moment où l'on peut dormir. */
    public static final long NIGHT_START = 12542, NIGHT_END = 23460;
    /** Transformations au plus par nuit (la recharge reste la même). */
    public static final int PER_NIGHT = 2;

    private final int level;

    public MinksSulongAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "minks_sulong";
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

    /** Fait-il nuit là où se trouve le joueur ? (dimension sans cycle jour / nuit : jamais) */
    public static boolean night(ServerLevel level) {
        if (level.dimensionType().hasFixedTime()) {
            return false;
        }
        long time = Math.floorMod(level.getDefaultClockTime(), 24000L); // heure du monde (horloge de la dimension)
        return time >= NIGHT_START && time <= NIGHT_END;
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        if (Minks.refuseBusy(player)) {
            return false;
        }
        if (Minks.sulong(player)) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.minks.sulong_already"), true);
            return false;
        }
        if (!night((ServerLevel) player.level())) {
            player.sendSystemMessage(Component.translatable(((ServerLevel) player.level()).dimensionType().hasFixedTime()
                    ? "message.bloxpiece.minks.sulong_no_moon" : "message.bloxpiece.minks.sulong_day"), true);
            return false;
        }
        if (usedTonight(player) >= PER_NIGHT) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.minks.sulong_limit", PER_NIGHT), true);
            return false;
        }
        return true;
    }

    /** Jour du monde (une nuit = un jour : la nuit tient entière dans la même tranche de 24000 ticks). */
    private static long day(ServerPlayer player) {
        return Math.floorDiv(((ServerLevel) player.level()).getDefaultClockTime(), 24000L);
    }

    /** Transformations déjà faites cette nuit. */
    public static int usedTonight(ServerPlayer player) {
        Long v = player.getAttached(ModAttachments.MINKS_SULONG_NIGHT);
        return v != null && Math.floorDiv(v, 4L) == day(player) ? (int) Math.floorMod(v, 4L) : 0;
    }

    private static void countUse(ServerPlayer player) {
        player.setAttached(ModAttachments.MINKS_SULONG_NIGHT, day(player) * 4L + Math.min(3, usedTonight(player) + 1));
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Minks.setAction(player, Minks.SULONG);
        Goro.start(new Goro.Session() {
            int t;

            @Override
            public boolean tick() {
                if (!Minks.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                if (t < CHARGE) {
                    // phases 1 et 2 : il se ramasse et accumule l'électricité (le joueur reste en place)
                    Human.halt(player);
                    float k = t / (float) CHARGE;
                    if (t % 2 == 0) {
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 2 + (int) (8 * k),
                                0.6 - 0.3 * k, 0.8, 0.6 - 0.3 * k, 0.1 + 0.2 * k);
                    }
                    if (t % 6 == 0) {
                        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS,
                                1.0F, 0.8F + 1.2F * k);
                    }
                    return true;
                }
                if (t == CHARGE) {
                    // phase 3 : explosion de transformation (le Sulong commence ICI : ses 30 s comptent à partir de maintenant)
                    Minks.startSulong(player);
                    countUse(player); // compte pour la limite de la nuit (au moment où la transformation a vraiment lieu)
                    MinksFx.spawn(level, player, MinksFx.SULONG, player.position(), player.getLookAngle(), 1.0F, 40);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS,
                            1.6F, 0.9F);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.3F);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.8, 1.0, 0.8, 0.5);
                    BuddhaHits.shake(level, player.position(), 18.0, 1.0F, 10);
                    player.sendSystemMessage(Component.translatable("message.bloxpiece.minks.sulong", Minks.SULONG_TICKS / 20), true);
                }
                // phase 4 : posture de puissance (animation seulement), puis la main revient au joueur
                if (t >= CHARGE + REVEAL) {
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
