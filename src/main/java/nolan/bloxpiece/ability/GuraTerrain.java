package nolan.bloxpiece.ability;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * Destruction du terrain par le Gura Gura no Mi (seulement si la règle mobGriefing est active) :
 * <ul>
 *   <li>{@link #blast} : explosion qui creuse un vrai cratère (blocs détruits) SANS dégâts ni recul (déjà appliqués par l'attaque) ;</li>
 *   <li>{@link #cracks} : fissures dans le sol, des tranchées en zigzag qui se propagent depuis le centre ;</li>
 *   <li>{@link #debris} : blocs de surface arrachés et projetés en l'air (vrais blocs qui tombent : ils retombent plus loin).</li>
 * </ul>
 * Jamais touchés : blocs incassables, blocs à contenu (coffres...), liquides.
 */
public final class GuraTerrain {

    /** Explosion pour les blocs uniquement : pas de dégâts aux entités, pas de recul. */
    private static final ExplosionDamageCalculator BLOCKS_ONLY = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return 0.0F;
        }
    };

    private GuraTerrain() {
    }

    static boolean griefing(ServerLevel level) {
        return level.getGameRules().get(GameRules.MOB_GRIEFING);
    }

    /** Cratère d'explosion de puissance {@code power} (4 = TNT). */
    static void blast(ServerLevel level, ServerPlayer owner, Vec3 at, float power) {
        level.explode(owner, Explosion.getDefaultDamageSource(level, owner), BLOCKS_ONLY,
                at.x, at.y, at.z, power, false, Level.ExplosionInteraction.MOB);
    }

    /** Bloc destructible par une attaque (protections communes, réutilisées par GoroTerrain). */
    static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && level.getBlockEntity(pos) == null
                && !nolan.bloxpiece.village.SafeZone.isProtected(level, pos) // village de départ : intouchable
                && state.getDestroySpeed(level, pos) >= 0.0F && state.getDestroySpeed(level, pos) < 40.0F; // pierre, minerais, deepslate... (pas l'obsidienne)
    }

    /** Une fissure qui avance dans le sol, un peu à chaque tick. */
    private static final class Crack {
        final ServerLevel level;
        final Vec3 center;
        final int cy;
        final double radius;
        final double step;
        double angle;
        double r;

        Crack(ServerLevel level, Vec3 center, double angle, double from, double radius, double step) {
            this.level = level;
            this.center = center;
            this.cy = Mth.floor(center.y) - 1;
            this.angle = angle;
            this.r = from;
            this.radius = radius;
            this.step = step;
        }
    }

    private static final List<Crack> CRACKS = new ArrayList<>();

    /**
     * {@code rays} fissures en zigzag de {@code from} à {@code radius} blocs du centre, qui se PROPAGENT depuis le centre en
     * {@code ticks} ticks (au même rythme que les fissures 3D) : le sol s'ouvre sur 1 à 2 blocs de profondeur et quelques blocs
     * sont projetés en l'air au passage du front.
     */
    static void cracks(ServerLevel level, Vec3 c, double from, double radius, int rays, int ticks) {
        if (!griefing(level)) {
            return;
        }
        RandomSource random = level.getRandom();
        double step = (radius - from) / Math.max(1, ticks);
        for (int i = 0; i < rays; i++) {
            CRACKS.add(new Crack(level, c, i * Math.PI * 2 / rays + random.nextDouble() * 0.4, from, radius, step));
        }
    }

    /** À chaque tick serveur (ModEvents) : chaque fissure avance d'un pas. */
    public static void tick() {
        Iterator<Crack> it = CRACKS.iterator();
        while (it.hasNext()) {
            Crack crack = it.next();
            ServerLevel level = crack.level;
            RandomSource random = level.getRandom();
            double target = Math.min(crack.radius, crack.r + crack.step);
            for (; crack.r <= target; crack.r += 0.7) {
                crack.angle += (random.nextDouble() - 0.5) * 0.35;
                int x = Mth.floor(crack.center.x + Math.cos(crack.angle) * crack.r);
                int z = Mth.floor(crack.center.z + Math.sin(crack.angle) * crack.r);
                int gy = IceTerrain.findSurface(level, x, crack.cy, z, 5);
                if (gy == IceTerrain.NO_SURFACE) {
                    continue;
                }
                int depth = crack.r < crack.radius * 0.6 ? 2 : 1; // plus profond près du centre
                for (int d = 0; d < depth; d++) {
                    BlockPos pos = new BlockPos(x, gy - d, z);
                    BlockState state = level.getBlockState(pos);
                    if (!breakable(level, pos, state)) {
                        continue;
                    }
                    if (d == 0 && random.nextFloat() < 0.18F && state.isCollisionShapeFullBlock(level, pos)) {
                        // Au passage du front, un bloc saute en l'air au lieu de simplement se briser
                        FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
                        block.setDeltaMovement((random.nextDouble() - 0.5) * 0.2, 0.5 + random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.2);
                    } else {
                        level.destroyBlock(pos, false);
                    }
                }
            }
            if (crack.r >= crack.radius) {
                it.remove();
            }
        }
    }

    public static void clear() {
        CRACKS.clear();
    }

    /**
     * Creuse la colonne sous {@code p} sur {@code depth} blocs (tranchée d'une vague). Si {@code launch} n'est pas nul, le bloc
     * de surface est arraché et projeté à cette vitesse au lieu d'être simplement brisé.
     */
    static void gouge(ServerLevel level, Vec3 p, int depth, Vec3 launch) {
        int x = Mth.floor(p.x), z = Mth.floor(p.z);
        int gy = IceTerrain.findSurface(level, x, Mth.floor(p.y) - 1, z, 6);
        if (gy == IceTerrain.NO_SURFACE) {
            return;
        }
        for (int d = 0; d < depth; d++) {
            BlockPos pos = new BlockPos(x, gy - d, z);
            BlockState state = level.getBlockState(pos);
            if (!breakable(level, pos, state)) {
                continue;
            }
            if (d == 0 && launch != null && state.isCollisionShapeFullBlock(level, pos)) {
                FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
                block.setDeltaMovement(launch);
            } else {
                level.destroyBlock(pos, false);
            }
            // Éclatement en morceaux de tailles variées (gerbes de fragments), pour casser l'effet de découpe carrée
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x + 0.5, gy - d + 0.8, z + 0.5,
                    4 + level.getRandom().nextInt(6), 0.45, 0.35, 0.45, 0.15 + level.getRandom().nextDouble() * 0.2);
        }
    }

    /** Arrache jusqu'à {@code count} blocs de surface dans le rayon et les projette en l'air, vers l'extérieur. */
    static void debris(ServerLevel level, Vec3 c, double radius, int count, double speed) {
        if (!griefing(level)) {
            return;
        }
        RandomSource random = level.getRandom();
        int cy = Mth.floor(c.y) - 1;
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 1.0 + random.nextDouble() * (radius - 1.0);
            int x = Mth.floor(c.x + Math.cos(a) * r), z = Mth.floor(c.z + Math.sin(a) * r);
            int gy = IceTerrain.findSurface(level, x, cy, z, 6);
            if (gy == IceTerrain.NO_SURFACE) {
                continue;
            }
            BlockPos pos = new BlockPos(x, gy, z);
            BlockState state = level.getBlockState(pos);
            if (!breakable(level, pos, state) || !state.isCollisionShapeFullBlock(level, pos)) {
                continue;
            }
            FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
            block.setDeltaMovement(Math.cos(a) * speed * 0.4 + (random.nextDouble() - 0.5) * 0.2,
                    speed * (0.7 + random.nextDouble() * 0.6),
                    Math.sin(a) * speed * 0.4 + (random.nextDouble() - 0.5) * 0.2);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x + 0.5, gy + 1.0, z + 0.5, 6, 0.3, 0.1, 0.3, 0.1);
        }
    }
}
