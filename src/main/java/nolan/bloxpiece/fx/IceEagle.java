package nolan.bloxpiece.fx;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.ability.BuddhaHits;
import nolan.bloxpiece.ability.IceFreeze;
import nolan.bloxpiece.ability.IceTerrain;
import nolan.bloxpiece.registry.ModEntities;

/**
 * L'aigle de glace (Pheasant Peck, attaque 3 du fruit de la Glace) : modèle 3D animé IceEagleModel, ailes déployées qui battent.
 * Il fonce droit devant lui sur une longue distance et TRAVERSE les ennemis : chacun n'est touché qu'une fois (gros dégâts de zone,
 * repoussé dans le sens du vol, ralenti). Sous lui, l'eau et le sol gèlent (temporairement). Il se brise en éclats contre un mur
 * ou au bout de sa course. Position et orientation synchronisées chaque tick ; traînée de givre envoyée par le serveur.
 */
public class IceEagle extends ModFxEntity {

    public static final double MODEL_SCALE = 1.6; // 1 px = 0,1 bloc : envergure ~7,5 blocs (lue par le renderer)

    private static final double SPEED = 1.6;
    private static final int MAX_AGE = 28;          // ~45 blocs
    private static final double HIT_RADIUS = 2.6;   // demi-envergure touchée autour du corps
    private static final float DAMAGE = 24.0F;
    private static final double PUSH = 1.8;
    private static final double LIFT = 0.45;
    private static final int GROUND_FREEZE_TICKS = 20 * 15;

    private Vec3 velocity = Vec3.ZERO;
    private int age;
    private final Set<Integer> hit = new HashSet<>();

    public IceEagle(EntityType<? extends IceEagle> type, Level level) {
        super(type, level);
    }

    public static IceEagle spawn(ServerLevel level, ServerPlayer owner, Vec3 start, Vec3 direction) {
        IceEagle eagle = new IceEagle(ModEntities.ICE_EAGLE, level);
        eagle.managed = true;
        eagle.setOwner(owner);
        Vec3 dir = direction.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1.0, 1.0)));
        eagle.setYRot(yaw);
        eagle.setXRot(pitch);
        eagle.yRotO = yaw;
        eagle.xRotO = pitch;
        eagle.setPos(start.x, start.y, start.z);
        eagle.velocity = dir.scale(SPEED);
        eagle.setDeltaMovement(eagle.velocity);
        level.addFreshEntity(eagle);
        IceEffects.eagleLaunch(level, start, dir);
        return eagle;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }
        ServerPlayer owner = this.getOwner() instanceof ServerPlayer p ? p : null;
        if (owner == null) {
            this.discard();
            return;
        }
        this.setDeltaMovement(this.velocity);
        Vec3 start = this.position();
        Vec3 end = start.add(this.velocity);
        Vec3 dir = this.velocity.normalize();

        // Traverse les ennemis : chacun touché une seule fois
        AABB sweep = new AABB(start, end).inflate(HIT_RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> IceFreeze.canHit(e, owner))) {
            if (this.hit.contains(target.getId()) || distanceToSegment(target.getBoundingBox().getCenter(), start, end) > HIT_RADIUS) {
                continue;
            }
            this.hit.add(target.getId());
            IceFreeze.hit(level, owner, target, DAMAGE, 80);
            target.push(dir.x * PUSH, LIFT, dir.z * PUSH);
        }

        // Le sol et l'eau gèlent sous l'aigle
        int gy = Mth.floor(end.y) - 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                IceTerrain.freezeColumn(level, Mth.floor(end.x) + dx, gy, Mth.floor(end.z) + dz, GROUND_FREEZE_TICKS);
            }
        }

        // Mur : l'aigle se brise
        BlockHitResult wall = level.clip(new ClipContext(start, end.add(dir.scale(1.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS || ++this.age >= MAX_AGE) {
            Vec3 at = wall.getType() != HitResult.Type.MISS ? wall.getLocation().subtract(dir.scale(0.3)) : end;
            this.crash(level, owner, at);
            return;
        }

        IceEffects.eagleTrail(level, start, end, dir, HIT_RADIUS * 0.9, this.age);
        if (this.age % 6 == 0) {
            level.playSound(null, end.x, end.y, end.z, SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.5F, 0.6F);
        }
        this.setPos(end);
    }

    /** Fin de course : l'aigle se brise et gèle tout autour (blocs en glace / neige, pics, éclats, entités proches ralenties). */
    private void crash(ServerLevel level, ServerPlayer owner, Vec3 at) {
        IceTerrain.freezeDisc(level, at, 8.0, GROUND_FREEZE_TICKS);   // grande zone de sol et d'eau gelés (glace / neige)
        IceTerrain.iceCluster(level, at, 3.5, GROUND_FREEZE_TICKS);  // amas de blocs de glace qui poussent autour de l'impact
        for (int i = 0; i < 14; i++) {
            double a = 2.0 * Math.PI * i / 14 + level.getRandom().nextDouble() * 0.4;
            double r = 1.5 + level.getRandom().nextDouble() * 5.5;
            int x = Mth.floor(at.x + Math.cos(a) * r), z = Mth.floor(at.z + Math.sin(a) * r);
            double y = IceTerrain.freezeColumn(level, x, Mth.floor(at.y), z, GROUND_FREEZE_TICKS);
            if (!Double.isNaN(y)) {
                float yaw = (float) Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a)));
                IceSpike.spawn(level, new Vec3(x + 0.5, y, z + 0.5), 0.7F + level.getRandom().nextFloat() * 1.3F, yaw,
                        10.0F + level.getRandom().nextFloat() * 25.0F, 20 * 12 + level.getRandom().nextInt(80));
            }
        }
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(4.0), e -> IceFreeze.canHit(e, owner))) {
            if (!this.hit.contains(target.getId())) {
                IceFreeze.hit(level, owner, target, DAMAGE * 0.5F, 100);
            }
        }
        IceEffects.shatter(level, at, 6.0F);
        IceEffects.tridentImpact(level, at, 8.0);
        BuddhaHits.shake(level, at, 24.0, 1.5F, 10);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0F, 0.4F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0F, 0.8F);
        this.discard();
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = ab.lengthSqr() < 1.0E-6 ? 0.0 : Mth.clamp(p.subtract(a).dot(ab) / ab.lengthSqr(), 0.0, 1.0);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // position, orientation et vitesse : envoyées par Minecraft
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0 * 256.0;
    }
}
