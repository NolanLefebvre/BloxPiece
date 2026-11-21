package nolan.bloxpiece.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.ability.BuddhaHits;
import nolan.bloxpiece.haki.ConquerorHaki;
import nolan.bloxpiece.registry.ModEntities;
import nolan.bloxpiece.registry.ModParticles;

/**
 * Activation du Haki des Rois : la séquence visuelle du déclenchement, et SEULEMENT elle (les éclairs et surcharges tant que le
 * Haki reste libéré ne changent pas, voir ConquerorHaki). Purement visuel, jamais sauvegardé.
 * <ol>
 *   <li>0 à {@link #BUILD_TICKS} : la volonté monte : premiers éclairs noirs et rouges autour du corps, étincelles, grondement ;</li>
 *   <li>{@link #BUILD_TICKS} : DÉCHARGE : gerbe d'éclairs qui jaillissent du corps, éclats et débris projetés, son grave
 *       (onde sonique + tonnerre), léger tremblement de caméra, flash rouge sombre (ConquerorFlash, côté client) ;</li>
 *   <li>puis l'ONDE DE CHOC (ConquerorShockwaveRenderer) : plusieurs anneaux noirs et rouge sombre et un mur d'air repoussé qui
 *       s'étendent jusqu'à {@link #MAX_RADIUS} blocs en {@link #EXPAND_TICKS} ticks, accompagnés d'éclairs au front de l'onde,
 *       puis s'estompent.</li>
 * </ol>
 */
public class ConquerorShockwave extends ModFxEntity {

    public static final int BUILD_TICKS = 5;
    public static final int EXPAND_TICKS = 10;
    public static final int FADE_TICKS = 10;
    public static final double MAX_RADIUS = 16.0;
    public static final int LIFE = BUILD_TICKS + EXPAND_TICKS + FADE_TICKS;

    private LivingEntity owner;
    /** Taille de l'onde (1 = activation du joueur ; Gol D. Roger : bien plus grande). Synchronisée pour le rendu. */
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(ConquerorShockwave.class,
            EntityDataSerializers.FLOAT);

    public ConquerorShockwave(EntityType<? extends ConquerorShockwave> type, Level level) {
        super(type, level);
    }

    public static void start(ServerLevel level, ServerPlayer player) {
        start(level, player, 1.0F);
    }

    /** Activation amplifiée (Gol D. Roger) : onde, éclairs du front et décharge {@code scale} fois plus grands. */
    public static void start(ServerLevel level, LivingEntity player, float scale) {
        ConquerorShockwave wave = new ConquerorShockwave(ModEntities.CONQUEROR_SHOCKWAVE, level);
        wave.managed = true;
        wave.entityData.set(SCALE, scale);
        wave.owner = player;
        wave.setPos(player.getX(), player.getY(), player.getZ());
        level.addFreshEntity(wave);
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS,
                1.2F, 0.6F);
    }

    public float scale() {
        return this.entityData.get(SCALE);
    }

    /** Âge de l'onde depuis la décharge (négatif pendant la montée). */
    public float waveAge(float partialTick) {
        return this.tickCount + partialTick - BUILD_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }
        if (this.owner == null || this.owner.isRemoved() || this.tickCount >= LIFE) {
            this.discard();
            return;
        }
        RandomSource random = level.getRandom();
        int t = this.tickCount;
        if (t < BUILD_TICKS) {
            // la volonté monte : quelques premiers éclairs autour du corps
            if (random.nextFloat() < 0.6F) {
                ConquerorHaki.spawnBolt(level, this.owner, random);
            }
            Vec3 c = this.owner.position();
            level.sendParticles(ModParticles.HAKI_SPARK, c.x, c.y + 1.0, c.z, 3, 0.35, 0.6, 0.35, 0.05);
            level.sendParticles(ModParticles.HAKI_MIST, c.x, c.y + 0.1, c.z, 2, 0.4, 0.05, 0.4, 0.01);
        } else if (t == BUILD_TICKS) {
            this.detonate(level, random);
        } else if (t < BUILD_TICKS + EXPAND_TICKS) {
            // éclairs qui accompagnent le front de l'onde
            float k = this.scale();
            double radius = radiusAt(t - BUILD_TICKS) * k;
            for (int i = 0; i < Math.round(2 * k); i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                Vec3 p = this.position().add(Math.cos(a) * radius, 0.3 + random.nextDouble() * 1.2 * k, Math.sin(a) * radius);
                float yaw = (float) Math.toDegrees(Math.atan2(Math.cos(a), Math.sin(a)));
                HakiBolt.spawn(level, p, yaw, 40.0F + random.nextFloat() * 60.0F, -(0.35F + random.nextFloat() * 0.25F) * k,
                        4 + random.nextInt(3), k, k <= 1.0F ? 1.0F : 1.0F + 0.5F * (k - 1.0F));
                level.sendParticles(ModParticles.HAKI_SPARK, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.1);
            }
        }
    }

    /** Rayon de l'onde (blocs) {@code age} ticks après la décharge : jaillit vite puis ralentit. */
    public static double radiusAt(float age) {
        float k = Math.max(0.0F, Math.min(1.0F, age / EXPAND_TICKS));
        return MAX_RADIUS * (1.0 - (1.0 - k) * (1.0 - k) * (1.0 - k));
    }

    private void detonate(ServerLevel level, RandomSource random) {
        Vec3 c = this.owner.position().add(0.0, 1.0, 0.0);
        // son grave et puissant, tremblement léger, éclairs qui jaillissent du corps (comme avant), éclats et pression au sol
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.8F, 0.45F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.3F, 0.6F);
        BuddhaHits.shake(level, c, MAX_RADIUS * 2.0 * this.scale(), 1.6F, 14);
        ConquerorHaki.activationBurst(level, this.owner);
        for (int i = 0; i < 12; i++) {
            ConquerorHaki.spawnBolt(level, this.owner, random);
        }
        // débris : morceaux du sol arrachés et projetés vers l'extérieur
        BlockPos below = this.owner.blockPosition().below();
        BlockState ground = level.getBlockState(below);
        if (!ground.isAir()) {
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int i = 0; i < 40; i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double r = 0.5 + random.nextDouble() * 2.0;
                level.sendParticles(debris, c.x + Math.cos(a) * r, this.owner.getY() + 0.1, c.z + Math.sin(a) * r, 0,
                        Math.cos(a), 0.4 + random.nextDouble() * 0.6, Math.sin(a), 0.4 + random.nextDouble() * 0.4);
            }
        }
        // poussière soufflée tout autour (l'air est violemment repoussé)
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2.0 * i / 48;
            level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * 1.2, this.owner.getY() + 0.2, c.z + Math.sin(a) * 1.2, 0,
                    Math.cos(a), 0.02, Math.sin(a), 0.9);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SCALE, 1.0F); // le reste se déduit de l'âge de l'entité
    }
}
