package nolan.bloxpiece.fx;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModEntities;
import nolan.bloxpiece.registry.ModParticles;

/**
 * Lame dévastatrice du Haki des Rois (attaque spéciale de l'épée Ace, voir AceItem) : un croissant d'énergie noir et rouge géant
 * (modèle AceSlashModel, forme AceSlashShape) qui file droit devant, incliné en diagonale.
 * <ol>
 *   <li><b>Charge</b> (AceItem, clic droit maintenu) : le joueur lève l'épée, le Haki des Rois s'accumule dessus. Cette entité
 *       n'existe pas encore.</li>
 *   <li><b>Frappe</b> : au relâchement, l'entité naît (invisible) collée au lanceur pendant le coup en diagonale, et part au
 *       moment précis où l'épée termine sa frappe ({@link #start}, délai fourni par AceItem). Sa taille dépend de la charge
 *       (synchronisée : {@link #scale}).</li>
 *   <li><b>Vol</b> : {@link #SPEED} blocs par tick pendant {@link #FLIGHT_TICKS} ticks ({@link #RANGE} blocs). Elle traverse
 *       tout : chaque entité touchée subit {@link #DAMAGE} dégâts une seule fois, les blocs sur son passage sont détruits (si
 *       la règle mobGriefing est active ; jamais les blocs incassables, très résistants ou protégés : village de départ).
 *       Collisions = le croissant (points de AceSlashShape, mis à l'échelle de la charge, balayés d'une position à la suivante).
 *       Elle s'estompe sur ses derniers ticks puis disparaît.</li>
 * </ol>
 * Tout est décidé par le serveur ; les clients ne font qu'afficher (position, direction, taille et phase synchronisées).
 */
public class AceSlash extends ModFxEntity {

    // ------------------------------------------------------------------ réglages
    /** Charge : durée pour atteindre la charge maximale, et durée maximale de maintien (au-delà, le coup part tout seul). */
    public static final int MAX_CHARGE_TICKS = 60;
    public static final int MAX_HOLD_TICKS = 200;
    /** Grossissement du croissant à pleine charge (+30 % : modéré). */
    public static final float MAX_SCALE_BONUS = 0.3F;
    /** Animation du joueur (troisième et première personne) : bras levé en {@link #RAISE_TICKS} (puis tenu tant que la charge
     *  dure), frappe en diagonale de {@link #STRIKE_TICKS} ticks (la lame part à la fin de la frappe), retour en
     *  {@link #RECOVER_TICKS}. */
    public static final float RAISE_TICKS = 5.0F;
    public static final float STRIKE_TICKS = 4.0F;
    public static final float RECOVER_TICKS = 9.0F;
    /** Vitesse (blocs par tick) et portée (blocs). */
    public static final double SPEED = 2.0;
    public static final double RANGE = 72.0;
    public static final int FLIGHT_TICKS = (int) Math.ceil(RANGE / SPEED);
    /** Derniers ticks du vol pendant lesquels la lame s'estompe. */
    public static final int FADE_TICKS = 8;
    /** Dégâts par entité touchée (une seule fois chacune). */
    public static final float DAMAGE = 40.0F;
    /** Recul donné aux entités touchées, dans le sens du vol. */
    public static final double KNOCKBACK = 1.2;
    /** Blocs : résistance à partir de laquelle un bloc n'est jamais détruit (obsidienne : 1200, comme EnteiFireball). */
    private static final float INDESTRUCTIBLE = 100.0F;
    /** Blocs détruits au maximum par tick (sécurité). */
    private static final int MAX_BLOCKS_PER_TICK = 600;
    /** Distance devant les yeux où la lame apparaît. */
    private static final double LAUNCH_OFFSET = 1.6;

    private static final EntityDataAccessor<Integer> LAUNCHED_AT =
            SynchedEntityData.defineId(AceSlash.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE =
            SynchedEntityData.defineId(AceSlash.class, EntityDataSerializers.FLOAT);
    /** Inclinaison de la lame (diagonale choisie, AceSlashShape.roll), fixée à la naissance. */
    private static final EntityDataAccessor<Float> ROLL =
            SynchedEntityData.defineId(AceSlash.class, EntityDataSerializers.FLOAT);

    /** Ticks à attendre avant le lancer (fin de la frappe) ; serveur seulement. */
    private int delay;

    private final Set<Integer> hit = new HashSet<>();

    public AceSlash(EntityType<? extends AceSlash> type, Level level) {
        super(type, level);
    }

    /**
     * Frappe : l'entité naît collée au lanceur (invisible) et part dans {@code delay} ticks, à la fin du coup d'épée.
     * {@code charge} de 0 à 1 : taille du croissant (1 + {@link #MAX_SCALE_BONUS} x charge).
     */
    public static void start(ServerLevel level, LivingEntity owner, float charge, int delay, float roll) {
        AceSlash slash = new AceSlash(ModEntities.ACE_SLASH, level);
        slash.managed = true;
        slash.entityData.set(ROLL, roll);
        slash.delay = Math.max(1, delay);
        slash.entityData.set(SCALE, 1.0F + MAX_SCALE_BONUS * Math.max(0.0F, Math.min(1.0F, charge)));
        slash.setOwner(owner);
        slash.setPos(owner.getEyePosition());
        slash.setYRot(owner.getYRot());
        slash.setXRot(owner.getXRot());
        level.addFreshEntity(slash);
    }

    /** Inclinaison de la lame (degrés) : la diagonale du coup. */
    public float roll() {
        return this.entityData.get(ROLL);
    }

    /** Taille du croissant (1 = normale), fixée par la charge. */
    public float scale() {
        return this.entityData.get(SCALE);
    }

    /** Tick du lancer (âge de l'entité), ou -1 pendant l'armé. */
    public int launchedAt() {
        return this.entityData.get(LAUNCHED_AT);
    }

    public boolean flying() {
        return this.launchedAt() >= 0;
    }

    /** Opacité de 0 à 1 (estompée sur la fin du vol). */
    public float fade(float partialTick) {
        float left = FLIGHT_TICKS - (this.tickCount + partialTick - this.launchedAt());
        return Math.max(0.0F, Math.min(1.0F, left / FADE_TICKS));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) {
            return;
        }
        if (!this.flying()) {
            if (this.level() instanceof ServerLevel level) {
                this.windup(level);
            }
            return;
        }
        Vec3 from = this.position();
        Vec3 to = from.add(this.getDeltaMovement());
        this.setPos(to); // déplacement prédit aussi côté client (le serveur corrige à chaque tick)
        if (this.level() instanceof ServerLevel level) {
            this.sweep(level, from, to);
            this.flightFx(level);
            if (this.tickCount - this.launchedAt() >= FLIGHT_TICKS) {
                this.discard();
            }
        }
    }

    // ------------------------------------------------------------------ armé puis lancer

    private void windup(ServerLevel level) {
        if (!(this.getOwner() instanceof LivingEntity owner) || !owner.isAlive() || owner.level() != level) {
            this.discard(); // lanceur mort, parti ou changé de dimension : l'attaque est annulée
            return;
        }
        this.setPos(owner.getEyePosition());
        if (this.tickCount >= this.delay) {
            this.launch(level, owner);
        }
    }

    private void launch(ServerLevel level, LivingEntity owner) {
        Vec3 look = owner.getLookAngle();
        float yaw = owner.getYRot(), pitch = owner.getXRot();
        if (owner instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null) {
            // un mob (Gol D. Roger) lance droit sur sa cible (son corps n'est pas toujours exactement tourné vers elle)
            look = mob.getTarget().getBoundingBox().getCenter().subtract(owner.getEyePosition()).normalize();
            yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
            pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, look.y))));
        }
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.setPos(owner.getEyePosition().add(look.scale(LAUNCH_OFFSET)).add(0.0, -0.2, 0.0));
        this.setDeltaMovement(look.scale(SPEED));
        this.entityData.set(LAUNCHED_AT, this.tickCount);
        Vec3 c = this.position();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.6F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.5F, 0.5F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.9F, 0.8F);
        level.sendParticles(ModParticles.HAKI_RING, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
        // décharge : le Haki accumulé sur l'épée se libère d'un coup (gerbe d'éclairs, plus fournie après une longue charge)
        RandomSource random = level.getRandom();
        float charge = (this.scale() - 1.0F) / MAX_SCALE_BONUS;
        int bolts = 4 + Math.round(6 * charge);
        for (int i = 0; i < bolts; i++) {
            Vec3 at = c.add((random.nextDouble() - 0.5) * 2.0, (random.nextDouble() - 0.5) * 1.5, (random.nextDouble() - 0.5) * 2.0);
            HakiBolt.spawn(level, at, (float) (random.nextDouble() * 360.0), 20.0F + random.nextFloat() * 140.0F,
                    -(0.5F + random.nextFloat() * 0.4F), 5 + random.nextInt(4));
        }
        level.sendParticles(ModParticles.HAKI_SHARD, c.x, c.y, c.z, 20 + Math.round(20 * charge), 0.6, 0.6, 0.6, 0.35);
        level.sendParticles(ModParticles.HAKI_SPARK, c.x, c.y, c.z, 16, 0.6, 0.6, 0.6, 0.15);
    }

    // ------------------------------------------------------------------ vol : collisions, dégâts, destruction

    /** Le croissant, balayé de {@code from} à {@code to} : entités touchées puis blocs détruits. */
    private void sweep(ServerLevel level, Vec3 from, Vec3 to) {
        float yaw = this.getYRot(), pitch = this.getXRot();
        AceSlashShape.Sample[] samples = AceSlashShape.samples(this.scale());
        double k = 1.0; // déjà mis à l'échelle par samples(scale)
        Vec3[] a = new Vec3[samples.length], b = new Vec3[samples.length];
        AABB zone = null;
        for (int i = 0; i < samples.length; i++) {
            AceSlashShape.Sample s = samples[i];
            Vec3 local = AceSlashShape.toWorld(s.x() * k, s.y() * k, s.z() * k, yaw, pitch, this.roll());
            a[i] = from.add(local);
            b[i] = to.add(local);
            AABB box = new AABB(a[i], b[i]).inflate(s.radius() * k);
            zone = zone == null ? box : zone.minmax(box);
        }
        this.hitEntities(level, zone, samples, a, b);
        if (level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            this.breakBlocks(level, samples, a, b);
        }
    }

    private void hitEntities(ServerLevel level, AABB zone, AceSlashShape.Sample[] samples, Vec3[] a, Vec3[] b) {
        Entity owner = this.getOwner();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, zone,
                e -> e != owner && e.isAlive() && !e.isSpectator() && !this.hit.contains(e.getId()));
        if (targets.isEmpty()) {
            return;
        }
        DamageSource source = level.damageSources().source(
                owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, this, owner);
        Vec3 push = this.getDeltaMovement().normalize().scale(KNOCKBACK);
        for (LivingEntity target : targets) {
            AABB box = target.getBoundingBox();
            boolean touched = false;
            for (int i = 0; i < samples.length && !touched; i++) {
                AABB grown = box.inflate(samples[i].radius());
                Vec3 p = a[i];
                boolean inside = p.x >= grown.minX && p.x <= grown.maxX && p.y >= grown.minY && p.y <= grown.maxY
                        && p.z >= grown.minZ && p.z <= grown.maxZ;
                touched = inside || grown.clip(a[i], b[i]).isPresent();
            }
            if (!touched) {
                continue;
            }
            this.hit.add(target.getId()); // une seule fois par entité
            if (owner instanceof LivingEntity ownerLiving) {
                target.setLastHurtByMob(ownerLiving);
            }
            if (target.hurtServer(level, source, DAMAGE)) {
                EnchantmentHelper.doPostAttackEffects(level, target, source);
                target.push(push.x, 0.25, push.z);
            }
            Vec3 c = target.getBoundingBox().getCenter();
            level.sendParticles(ModParticles.HAKI_SHARD, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.3);
            level.sendParticles(ModParticles.HAKI_SPARK, c.x, c.y, c.z, 8, 0.3, 0.4, 0.3, 0.1);
        }
    }

    /** Blocs dont le centre est dans la lame (chaque point de collision, d'une position à la suivante par pas d'un demi-bloc). */
    private void breakBlocks(ServerLevel level, AceSlashShape.Sample[] samples, Vec3[] a, Vec3[] b) {
        Set<BlockPos> done = new HashSet<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        int broken = 0;
        for (int i = 0; i < samples.length; i++) {
            double r = samples[i].radius();
            int steps = Math.max(1, (int) Math.ceil(a[i].distanceTo(b[i]) / 0.5));
            for (int k = 0; k <= steps; k++) {
                double f = k / (double) steps;
                Vec3 p = a[i].add(b[i].subtract(a[i]).scale(f));
                BlockPos min = BlockPos.containing(p.x - r, p.y - r, p.z - r);
                BlockPos max = BlockPos.containing(p.x + r, p.y + r, p.z + r);
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int bx = min.getX(); bx <= max.getX(); bx++)
                for (int by = min.getY(); by <= max.getY(); by++)
                for (int bz = min.getZ(); bz <= max.getZ(); bz++) {
                    pos.set(bx, by, bz);
                    if (broken >= MAX_BLOCKS_PER_TICK) {
                        return;
                    }
                    double dx = pos.getX() + 0.5 - p.x, dy = pos.getY() + 0.5 - p.y, dz = pos.getZ() + 0.5 - p.z;
                    if (dx * dx + dy * dy + dz * dz > r * r || !done.add(pos.immutable())) {
                        continue;
                    }
                    if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || nolan.bloxpiece.village.SafeZone.isProtected(level, pos)) {
                        continue; // rien à casser, ou village de départ (intouchable)
                    }
                    if (state.getBlock().getExplosionResistance() >= INDESTRUCTIBLE || state.getDestroySpeed(level, pos) < 0.0F) {
                        continue; // bedrock, obsidienne, blocs incassables
                    }
                    BlockPos target = pos.immutable();
                    if (state.hasBlockEntity()) {
                        level.destroyBlock(target, true, this, 512); // coffre, four... : le contenu tombe au sol
                    } else {
                        level.setBlock(target, air, Block.UPDATE_ALL);
                    }
                    broken++;
                    if (level.getRandom().nextInt(6) == 0) {
                        level.sendParticles(ModParticles.HAKI_MIST, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                                1, 0.2, 0.2, 0.2, 0.01);
                    }
                }
            }
        }
    }

    /**
     * Traînée : étincelles et éclats semés sur la lame à chaque tick, qui s'éteignent d'eux-mêmes derrière elle. Les éclairs du
     * vol ne sont plus des entités posées dans le monde (elles restaient figées derrière la lame) : ils sont dessinés SUR la lame
     * par AceSlashRenderer et la suivent.
     */
    private void flightFx(ServerLevel level) {
        RandomSource random = level.getRandom();
        AceSlashShape.Sample[] samples = AceSlashShape.samples(this.scale());
        float yaw = this.getYRot(), pitch = this.getXRot();
        for (int k = 0; k < 4; k++) {
            AceSlashShape.Sample s = samples[1 + random.nextInt(samples.length - 2)];
            Vec3 p = this.position().add(AceSlashShape.toWorld(s.x(), s.y(), s.z(), yaw, pitch, this.roll()));
            level.sendParticles(ModParticles.HAKI_SPARK, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.05);
            if (k == 0) {
                level.sendParticles(ModParticles.HAKI_MIST, p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0.01);
                level.sendParticles(ModParticles.HAKI_SHARD, p.x, p.y, p.z, 3, 0.3, 0.3, 0.3, 0.2);
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LAUNCHED_AT, -1);
        builder.define(SCALE, 1.0F);
        builder.define(ROLL, AceSlashShape.ROLL);
    }
}
