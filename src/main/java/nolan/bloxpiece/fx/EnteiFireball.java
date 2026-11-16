package nolan.bloxpiece.fx;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Le projectile (invisible sous le soleil) de Dai Enkai: Entei : une boule de feu vanilla (même type, même vitesse, mêmes dégâts directs)
 * dont SEUL le cratère change.
 *
 * L'explosion vanilla creuse un cratère en lançant des rayons depuis une grille cubique : à cette puissance, le trou obtenu est carré /
 * cubique. Ici l'explosion garde exactement la même puissance pour les dégâts et le recul (mêmes entités touchées, mêmes dégâts, même
 * ordre : les dégâts sont calculés avant que les blocs ne disparaissent), mais elle ne détruit plus aucun bloc elle-même : le cratère est
 * creusé ensuite comme une sphère parfaite.
 * <ul>
 *   <li>Terrain meuble (résistance &lt; 3 : terre, sable, gravier, feuilles, bois...) : sphère de {@link #SOFT_RADIUS} blocs.</li>
 *   <li>Roches et minerais (résistance 3 à 100) : sphère plus petite, concentrique, de {@link #HARD_RADIUS} blocs (la roche résistait déjà
 *       bien plus que la terre dans l'explosion vanilla).</li>
 *   <li>Obsidienne, bedrock, eau, lave (résistance &ge; 100) : jamais détruits, comme avant.</li>
 * </ul>
 * Comme avant, rien n'est détruit si la règle mobGriefing est désactivée ; les blocs à inventaire (coffres...) lâchent leur contenu et des
 * flammes se posent au hasard sur le fond du cratère.
 */
public class EnteiFireball extends LargeFireball {

    /** Rayon du cratère en terrain meuble (blocs). */
    private static final double SOFT_RADIUS = 22.0;

    /** Rayon du cratère dans la roche (blocs). */
    private static final double HARD_RADIUS = 8.0;

    /** En dessous : terrain meuble ; au-dessus (et jusqu'à {@link #INDESTRUCTIBLE}) : roche. */
    private static final float SOFT_MAX_RESISTANCE = 3.0F;

    /** Résistance à partir de laquelle un bloc survit (obsidienne, bedrock, eau, lave...). */
    private static final float INDESTRUCTIBLE = 100.0F;

    private final int blastPower;

    public EnteiFireball(Level level, LivingEntity owner, Vec3 movement, int power) {
        super(level, owner, movement, power);
        this.blastPower = power;
    }

    @Override
    protected void onHit(HitResult hit) {
        // Dégâts directs sur l'entité touchée : exactement ceux de la boule de feu vanilla
        if (hit instanceof EntityHitResult entityHit) {
            this.onHitEntity(entityHit);
        }
        if (this.level() instanceof ServerLevel level) {
            // Explosion de la même puissance (dégâts et recul inchangés), mais sans détruire de blocs : le cratère est creusé ci-dessous
            level.explode(this, this.getX(), this.getY(), this.getZ(), (float) this.blastPower, false, Level.ExplosionInteraction.NONE);
            if (level.getGameRules().get(GameRules.MOB_GRIEFING)) {
                carveSphere(level, this.position(), this);
            }
            this.discard();
        }
    }

    /** Creuse le cratère sphérique autour de {@code center} (voir la description de la classe). */
    private static void carveSphere(ServerLevel level, Vec3 center, Entity source) {
        int reach = (int) Math.ceil(SOFT_RADIUS);
        BlockPos origin = BlockPos.containing(center);
        double softSqr = SOFT_RADIUS * SOFT_RADIUS;
        double hardSqr = HARD_RADIUS * HARD_RADIUS;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<BlockPos> removed = new ArrayList<>();

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    // distance du centre du bloc au centre de l'explosion : la forme est une sphère exacte
                    double ex = pos.getX() + 0.5 - center.x;
                    double ey = pos.getY() + 0.5 - center.y;
                    double ez = pos.getZ() + 0.5 - center.z;
                    double distSqr = ex * ex + ey * ey + ez * ez;
                    if (distSqr > softSqr || level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    float resistance = state.getBlock().getExplosionResistance();
                    if (resistance >= INDESTRUCTIBLE || (resistance >= SOFT_MAX_RESISTANCE && distSqr > hardSqr)) {
                        continue;
                    }
                    BlockPos target = pos.immutable();
                    if (state.hasBlockEntity()) {
                        level.destroyBlock(target, true, source, 512); // coffre, four... : le contenu tombe au sol
                    } else {
                        level.setBlock(target, air, Block.UPDATE_ALL);
                    }
                    removed.add(target);
                }
            }
        }

        // Flammes au hasard sur le fond du cratère (comme l'explosion vanilla avec mobGriefing)
        RandomSource random = level.getRandom();
        for (BlockPos p : removed) {
            if (random.nextInt(3) == 0 && level.getBlockState(p).isAir()) {
                BlockPos below = p.below();
                if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    level.setBlock(p, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }
}
