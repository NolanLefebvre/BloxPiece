package nolan.bloxpiece.ability;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.HumanFx;

/**
 * Race Humaine, niveau 10 : IMPACT FULGURANT. Le joueur concentre sa force dans son poing droit, recule le bras, puis frappe
 * droit devant à une vitesse fulgurante. Au contact EXACT ({@link #STRIKE}) : une seule cible (la première sur la ligne du
 * regard, à {@link #RANGE} blocs au plus) prend les dégâts et un léger recul ; l'air se comprime et une onde de choc 3D part
 * du point de contact (HumanFx.PUNCH), avec un petit nuage de poussière. Sans cible, le coup frappe l'air (ou le bloc visé) :
 * même onde, sans dégâts.
 * Déroulement (ticks) : concentration et armé 0-{@link #WINDUP}, frappe jusqu'au contact {@link #STRIKE}, tenue brève puis
 * retour du bras jusqu'à {@link #DURATION} (animations : HumanPose, HumanFistLayer).
 */
public class HumanLightningImpactAbility implements Ability {

    public static final int WINDUP = 7, STRIKE = 10, DURATION = 22;
    public static final int COOLDOWN = 20 * 6;
    public static final float DAMAGE = 10.0F;
    public static final double RANGE = 3.6, KNOCKBACK = 0.75, LIFT = 0.25;
    /** Taille et durée de l'onde de choc au point de contact. */
    public static final float FX_SIZE = 1.0F;
    public static final int FX_LIFE = 16;

    private final int level;

    public HumanLightningImpactAbility(int level) {
        this.level = level;
    }

    @Override
    public String id() {
        return "human_lightning_impact";
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
        return !Human.refuseBusy(player);
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Human.setAction(player, Human.IMPACT);
        Goro.start(new Goro.Session() {
            int t;

            @Override
            public boolean tick() {
                if (!Human.valid(player, level)) {
                    cancel();
                    return false;
                }
                t++;
                if (t == 1) { // le poing se serre : la force se concentre
                    Vec3 f = Human.rightFist(player, 0.3);
                    level.playSound(null, f.x, f.y, f.z, SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8F, 0.6F);
                }
                if (t == WINDUP) { // départ du coup : l'air siffle
                    Vec3 f = Human.rightFist(player, 0.6);
                    level.playSound(null, f.x, f.y, f.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.7F);
                }
                if (t == STRIKE) {
                    strike(level, player);
                }
                if (t >= DURATION) {
                    Human.clearAction(player, Human.IMPACT);
                    return false;
                }
                return true;
            }

            @Override
            public void cancel() {
                Human.clearAction(player, Human.IMPACT);
            }
        });
    }

    /** Le contact : première cible sur la ligne du regard (ou le bloc visé, ou l'air au bout de la portée). */
    private static void strike(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 far = eye.add(look.scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        LivingEntity target = null;
        Vec3 contact = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : Human.targets(level, player, new AABB(eye, end).inflate(1.0))) {
            Optional<Vec3> clip = e.getBoundingBox().inflate(0.35).clip(eye, end);
            if (clip.isPresent() && clip.get().distanceToSqr(eye) < best) {
                best = clip.get().distanceToSqr(eye);
                target = e;
                contact = clip.get();
            }
        }
        Vec3 at = contact != null ? contact : end;
        // pas d'onde collée à la caméra du lanceur : au moins à hauteur du poing tendu
        if (at.distanceTo(eye) < 0.9) {
            at = eye.add(look.scale(0.9));
        }
        if (target != null) {
            Vec3 push = new Vec3(look.x, 0.0, look.z);
            push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(KNOCKBACK);
            Human.strike(level, player, target, DAMAGE, push.add(0.0, LIFT, 0.0), new HashSet<>());
            Human.knockTrail(level, target, 6);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.15, 0.15, 0.15, 0.45);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2F, 0.8F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F);
        } else if (block.getType() != HitResult.Type.MISS) {
            // le poing frappe un bloc : éclats de ce bloc
            BlockState state = level.getBlockState(block.getBlockPos());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 16, 0.2, 0.2, 0.2, 0.15);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
        // l'air comprimé claque (sur cible ou non)
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.45F, 1.9F);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.12);
        // poussière soulevée si le coup part près du sol
        double gy = Human.ground(level, at.x, at.z, at.y);
        if (at.y - gy < 1.6) {
            Human.groundBurst(level, new Vec3(at.x, gy, at.z), 10, 0.5, 0.08);
            level.sendParticles(ParticleTypes.CLOUD, at.x, gy + 0.2, at.z, 5, 0.4, 0.05, 0.4, 0.03);
        }
        HumanFx.spawn(level, player, HumanFx.PUNCH, at, look, FX_SIZE * (target != null ? 1.0F : 0.75F), FX_LIFE);
        BuddhaHits.shake(level, at, 6.0, target != null ? 0.35F : 0.2F, 4);
    }
}
