package nolan.bloxpiece.fx;

import java.util.List;

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

import nolan.bloxpiece.ability.IceFreeze;
import nolan.bloxpiece.ability.IceTerrain;
import nolan.bloxpiece.registry.ModEntities;

/**
 * Le trident de glace lancé (Partisan, attaque 2 du fruit de la Glace) : modèle 3D IceTridentModel, orienté dans le sens du vol.
 * Vol rapide en ligne droite (pas de gravité), collisions faites à la main tous les 0,5 bloc : la première entité touchée (ou le
 * premier bloc) déclenche l'impact, UNE seule fois :
 * <ul>
 *   <li>la cible touchée subit de gros dégâts et est gelée sur place, emprisonnée dans la glace ;</li>
 *   <li>les entités proches subissent des dégâts de zone et sont fortement ralenties ;</li>
 *   <li>des pics de glace éclatent du sol en cercle autour du point d'impact, et le sol gèle (temporairement).</li>
 * </ul>
 * Position et orientation synchronisées chaque tick (updateInterval 1) ; particules envoyées par le serveur.
 */
public class IceTrident extends ModFxEntity {

    private static final double STEP = 0.5;
    private static final double HIT_RADIUS = 0.7;
    private static final int MAX_AGE = 40;

    private static final float DIRECT_DAMAGE = 28.0F;
    private static final int DIRECT_FREEZE_TICKS = 60;
    private static final double AREA_RADIUS = 4.5;
    private static final float AREA_DAMAGE = 12.0F;
    private static final int SPIKES = 9;

    private Vec3 velocity = Vec3.ZERO;
    private int age;
    private boolean impacted;

    public IceTrident(EntityType<? extends IceTrident> type, Level level) {
        super(type, level);
    }

    /** Lance le trident depuis {@code start} vers {@code direction} à {@code speed} blocs/tick. */
    public static IceTrident spawn(ServerLevel level, ServerPlayer owner, Vec3 start, Vec3 direction, double speed) {
        IceTrident trident = new IceTrident(ModEntities.ICE_TRIDENT, level);
        trident.managed = true;
        trident.setOwner(owner);
        Vec3 dir = direction.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1.0, 1.0)));
        trident.setYRot(yaw);
        trident.setXRot(pitch);
        trident.yRotO = yaw;
        trident.xRotO = pitch;
        trident.setPos(start.x, start.y, start.z);
        trident.velocity = dir.scale(speed);
        trident.setDeltaMovement(trident.velocity);
        level.addFreshEntity(trident);
        return trident;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.impacted || this.isRemoved()) {
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

        // Bloc touché sur le trajet ?
        BlockHitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double reach = block.getType() == HitResult.Type.MISS ? this.velocity.length() : block.getLocation().distanceTo(start);

        // Entité touchée avant le bloc ? (balayage par pas de 0,5 bloc)
        Vec3 dir = this.velocity.normalize();
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(start, end).inflate(HIT_RADIUS + 1.0), e -> IceFreeze.canHit(e, owner));
        for (double d = 0.0; d <= reach; d += STEP) {
            Vec3 p = start.add(dir.scale(d));
            for (LivingEntity target : candidates) {
                if (target.getBoundingBox().inflate(HIT_RADIUS).contains(p)) {
                    this.impact(level, owner, p, target);
                    return;
                }
            }
        }
        if (block.getType() != HitResult.Type.MISS) {
            this.impact(level, owner, block.getLocation().subtract(dir.scale(0.2)), null);
            return;
        }
        if (++this.age >= MAX_AGE) {
            this.impact(level, owner, end, null);
            return;
        }
        IceEffects.tridentTrail(level, start, end);
        this.setPos(end);
    }

    private void impact(ServerLevel level, ServerPlayer owner, Vec3 point, LivingEntity direct) {
        this.impacted = true;
        if (direct != null) {
            IceFreeze.hit(level, owner, direct, DIRECT_DAMAGE, 100);
            IceFreeze.freeze(level, direct, DIRECT_FREEZE_TICKS, true);
        }
        AABB zone = new AABB(point, point).inflate(AREA_RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, zone, e -> IceFreeze.canHit(e, owner))) {
            if (target != direct && target.getBoundingBox().getCenter().distanceTo(point) <= AREA_RADIUS) {
                IceFreeze.hit(level, owner, target, AREA_DAMAGE, 80);
            }
        }
        // Le sol et l'eau gèlent autour de l'impact, puis des pics de glace jaillissent en cercle
        IceTerrain.freezeDisc(level, point, 3.5, 20 * 15);
        for (int i = 0; i < SPIKES; i++) {
            double a = 2.0 * Math.PI * i / SPIKES + level.getRandom().nextDouble() * 0.4;
            double r = 1.4 + level.getRandom().nextDouble() * 1.8;
            int x = Mth.floor(point.x + Math.cos(a) * r), z = Mth.floor(point.z + Math.sin(a) * r);
            double y = IceTerrain.freezeColumn(level, x, Mth.floor(point.y), z, 20 * 15);
            if (!Double.isNaN(y)) {
                float yaw = (float) Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a)));
                IceSpike.spawn(level, new Vec3(x + 0.5, y, z + 0.5), 0.7F + level.getRandom().nextFloat() * 0.5F, yaw, 15.0F + level.getRandom().nextFloat() * 15.0F);
            }
        }
        // Petits cristaux qui hérissent la zone gelée
        for (int i = 0; i < 6; i++) {
            double a = level.getRandom().nextDouble() * 2.0 * Math.PI;
            double r = level.getRandom().nextDouble() * 1.2;
            int x = Mth.floor(point.x + Math.cos(a) * r), z = Mth.floor(point.z + Math.sin(a) * r);
            double y = IceTerrain.freezeColumn(level, x, Mth.floor(point.y), z, 20 * 15);
            if (!Double.isNaN(y)) {
                IceSpike.spawn(level, new Vec3(x + 0.2 + level.getRandom().nextDouble() * 0.6, y, z + 0.2 + level.getRandom().nextDouble() * 0.6),
                        0.25F + level.getRandom().nextFloat() * 0.25F, (float) Math.toDegrees(a), 25.0F, 100);
            }
        }
        IceEffects.tridentImpact(level, point, AREA_RADIUS);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0F, 0.6F);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 2.0F, 0.8F);
        this.discard();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // position, orientation et vitesse : envoyées par Minecraft
    }
}
