package nolan.bloxpiece.mob.ai;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.ability.BuddhaHits;
import nolan.bloxpiece.fx.ConquerorShockwave;
import nolan.bloxpiece.fx.HakiBolt;
import nolan.bloxpiece.haki.ConquerorHaki;
import nolan.bloxpiece.mob.GolDRoger;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModParticles;

/**
 * Gol D. Roger à 50 % de sa vie : il libère son Haki des Rois, une version AMPLIFIÉE de celle du joueur (mêmes éclairs
 * HakiBolt, même onde ConquerorShockwave, même flash, mais bien plus grands), pendant {@link #DURATION} ticks (5 s), puis
 * reprend son combat. Sa pression est celle d'un niveau {@link #ROGER_LEVEL} : elle assomme ceux qui ne sont pas plus forts
 * (voir {@link #knockOut}). Lancé par GolDRoger (passage du seuil, une seule fois), décidé côté serveur ; les clients voient tout par
 * les entités d'effets et l'attachment CONQUEROR_RELEASE (posture, flash).
 * <ol>
 *   <li>0 à {@link #BUILD} : il s'immobilise et se redresse ; premiers éclairs brutaux autour du corps, grondement ;</li>
 *   <li>{@link #BUILD} : DÉCHARGE : onde de choc géante (x{@link #WAVE_SCALE}, ~37 blocs), gerbe d'éclairs, débris, son grave,
 *       tremblement de caméra ;</li>
 *   <li>jusqu'à {@link #PEAK_END} : intensité maximale : de grands éclairs (jusqu'à ~12 blocs, épais) jaillissent de son corps et
 *       traversent toute la zone jusqu'à {@link #ZONE_RADIUS} blocs, avec de grandes décharges régulières ;</li>
 *   <li>jusqu'à {@link #DURATION} : tout faiblit progressivement (moins d'éclairs, plus petits), puis il reprend le combat.</li>
 * </ol>
 * Priorité maximale avec les drapeaux MOVE et LOOK : ni coups d'épée, ni Lame dévastatrice pendant la séquence.
 */
public class RogerConquerorGoal extends Goal {

    public static final int DURATION = 100;
    private static final int BUILD = 10;
    private static final int PEAK_END = 65;
    private static final float WAVE_SCALE = 2.3F;
    private static final double ZONE_RADIUS = 26.0;

    /**
     * Puissance du Haki des Rois de Roger : celle d'un joueur de ce niveau. Un joueur de niveau STRICTEMENT supérieur n'en
     * ressent rien ; les autres s'évanouissent (comme sous le Haki des Rois d'un joueur), dans {@link #KNOCKOUT_RADIUS} blocs,
     * tant que la libération est à pleine puissance.
     */
    public static final int ROGER_LEVEL = 80;
    private static final double KNOCKOUT_RADIUS = 24.0;

    private final GolDRoger roger;
    private int age;
    /** Joueurs déjà prévenus qu'ils résistent (un seul message par libération). */
    private final java.util.Set<java.util.UUID> warned = new java.util.HashSet<>();

    public RogerConquerorGoal(GolDRoger roger) {
        this.roger = roger;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return this.roger.conquerorPending() && this.roger.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.age < DURATION && this.roger.isAlive();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        ServerLevel level = (ServerLevel) this.roger.level();
        this.age = 0;
        this.warned.clear();
        this.roger.clearConquerorPending();
        this.roger.getNavigation().stop();
        this.roger.removeAttached(ModAttachments.ACE_CHARGE); // une Lame dévastatrice en cours de charge est abandonnée
        level.playSound(null, this.roger.getX(), this.roger.getY() + 1.0, this.roger.getZ(), SoundEvents.WARDEN_SONIC_CHARGE,
                SoundSource.HOSTILE, 2.0F, 0.45F);
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) this.roger.level();
        RandomSource random = level.getRandom();
        this.roger.getNavigation().stop();
        LivingEntity target = this.roger.getTarget();
        if (target != null) {
            this.roger.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        this.age++;
        int t = this.age;
        Vec3 c = this.roger.position().add(0.0, 1.1, 0.0);
        if (t == BUILD - ConquerorShockwave.BUILD_TICKS) {
            // l'onde géante : même séquence que l'activation du joueur, en bien plus grand ; lancée pour que SA décharge tombe
            // pile au tick BUILD. Même instant : posture (ConquerorPose) et flash à l'écran (ConquerorFlash) chez tous les clients
            this.roger.setAttached(ModAttachments.CONQUEROR_RELEASE, (int) level.getGameTime());
            ConquerorShockwave.start(level, this.roger, WAVE_SCALE);
        }
        if (t < BUILD) {
            // premiers éclairs, brutaux, tout autour du corps
            for (int i = 0; i < 2; i++) {
                ConquerorHaki.spawnBolt(level, this.roger, random);
            }
            level.sendParticles(ModParticles.HAKI_SPARK, c.x, c.y, c.z, 6, 0.5, 0.8, 0.5, 0.08);
            return;
        }
        if (t == BUILD) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0F, 0.35F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 3.0F, 0.5F);
            BuddhaHits.shake(level, c, ZONE_RADIUS * 2.0, 2.2F, 24);
            for (int i = 0; i < 16; i++) {
                giantBolt(level, random, 1.0F);
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (t >= BUILD && t <= PEAK_END) {
            this.knockOut(level);
        }
        // intensité : maximale jusqu'à PEAK_END, puis elle faiblit progressivement jusqu'à la fin
        float power = t <= PEAK_END ? 1.0F : Math.max(0.0F, 1.0F - (t - PEAK_END) / (float) (DURATION - PEAK_END));
        if (power <= 0.0F) {
            return;
        }
        // éclairs géants qui jaillissent du corps (1 à 2 par tick au maximum)
        if (random.nextFloat() < 0.9F * power) {
            giantBolt(level, random, power);
        }
        if (random.nextFloat() < 0.6F * power) {
            giantBolt(level, random, power);
        }
        // éclairs qui traversent la zone : nés au loin, ils zèbrent l'air autour du boss
        if (random.nextFloat() < 0.7F * power) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 6.0 + random.nextDouble() * (ZONE_RADIUS - 6.0);
            Vec3 p = this.roger.position().add(Math.cos(a) * r, 0.5 + random.nextDouble() * 4.0, Math.sin(a) * r);
            float size = (0.6F + random.nextFloat() * 0.8F) * (0.5F + 0.5F * power);
            HakiBolt.spawn(level, p, (float) (random.nextDouble() * 360.0), 30.0F + random.nextFloat() * 120.0F, -size,
                    6 + random.nextInt(5), 1.5F, 1.2F + 0.8F * power);
            level.sendParticles(ModParticles.HAKI_SPARK, p.x, p.y, p.z, 3, 0.4, 0.4, 0.4, 0.1);
        }
        // grande décharge toutes les secondes : gerbe d'éclairs et onde de pression au sol
        if (t % 20 == BUILD % 20 && t > BUILD && power > 0.5F) {
            for (int i = 0; i < 5; i++) {
                giantBolt(level, random, power);
            }
            level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 2.0F, 0.6F);
            BuddhaHits.shake(level, c, ZONE_RADIUS * 1.5, 1.0F, 8);
            ConquerorHaki.activationBurst(level, this.roger);
        }
        // aura : brume noire et étincelles autour de lui, de moins en moins
        if (t % 2 == 0) {
            level.sendParticles(ModParticles.HAKI_MIST, this.roger.getX(), this.roger.getY() + 0.2, this.roger.getZ(),
                    Math.round(4 * power), 1.2, 0.2, 1.2, 0.03);
            level.sendParticles(ModParticles.HAKI_SPARK, c.x, c.y, c.z, Math.round(4 * power), 0.6, 0.9, 0.6, 0.1);
        }
    }

    /**
     * Pression du Haki des Rois de Roger (niveau {@link #ROGER_LEVEL}), mêmes règles que celui du joueur (ConquerorHaki) :
     * les boss, PNJ et créatures qui y résistent tiennent bon ; un joueur de niveau supérieur à {@link #ROGER_LEVEL} n'en ressent
     * rien ; un joueur qui libère lui aussi son Haki des Rois tient bon à niveau égal, et sinon est écrasé (son Haki est coupé) ;
     * tous les autres s'évanouissent ConquerorHaki.KNOCKOUT_TICKS ticks.
     */
    private void knockOut(ServerLevel level) {
        net.minecraft.world.phys.AABB box = this.roger.getBoundingBox().inflate(KNOCKOUT_RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this.roger && e.isAlive())) {
            if (target.distanceToSqr(this.roger) > KNOCKOUT_RADIUS * KNOCKOUT_RADIUS
                    || nolan.bloxpiece.haki.Unconscious.is(target)) {
                continue;
            }
            if (target instanceof net.minecraft.server.level.ServerPlayer player) {
                if (player.isCreative() || player.isSpectator()) {
                    continue;
                }
                int lvl = ConquerorHaki.level(player);
                if (lvl > ROGER_LEVEL) {
                    if (this.warned.add(player.getUUID())) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                "message.bloxpiece.roger.haki_resisted"), true);
                    }
                    continue; // plus fort que le Haki de Roger : il ne ressent rien
                }
                nolan.bloxpiece.haki.Haki conqueror = nolan.bloxpiece.haki.HakiManager.get(nolan.bloxpiece.haki.HakiType.CONQUEROR);
                if (conqueror.isActive(player)) {
                    if (lvl == ROGER_LEVEL) {
                        continue; // deux Hakis des Rois de même force : il tient bon
                    }
                    conqueror.stop(player); // écrasé : son Haki des Rois est coupé
                    nolan.bloxpiece.haki.HakiManager.startCooldown(player, nolan.bloxpiece.haki.HakiType.CONQUEROR, 20 * 20);
                }
            } else if (ConquerorHaki.resists(target)) {
                continue;
            }
            nolan.bloxpiece.haki.Unconscious.knockOut(target, ConquerorHaki.KNOCKOUT_TICKS);
        }
    }

    /** Un éclair géant qui jaillit du corps de Roger : même éclair que le joueur, 3 à 5 fois plus long et 2 fois plus épais. */
    private void giantBolt(ServerLevel level, RandomSource random, float power) {
        double a = random.nextDouble() * Math.PI * 2.0;
        double elevation = random.nextFloat() < 0.7F ? 15.0 + random.nextDouble() * 50.0 : -(10.0 + random.nextDouble() * 30.0);
        Vec3 p = this.roger.position().add(Math.cos(a) * 0.4, 0.6 + random.nextDouble() * 1.4, Math.sin(a) * 0.4);
        float yaw = (float) Math.toDegrees(Math.atan2(Math.cos(a), Math.sin(a)));
        float pitch = (float) (90.0 - elevation);
        float size = (1.4F + random.nextFloat() * 0.9F) * (0.4F + 0.6F * power); // ~7 à 12 blocs de long
        HakiBolt.spawn(level, p, yaw, pitch, -size, 8 + random.nextInt(5), 3.0F, 1.6F + 0.8F * power);
    }
}
