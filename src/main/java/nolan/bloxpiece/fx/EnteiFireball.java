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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Le projectile (invisible sous le soleil) de Dai Enkai: Entei : une boule de feu vanilla (même type, même vitesse, mêmes dégâts directs)
 * dont SEUL le cratère change.
 *
 * L'explosion garde exactement la même puissance pour les dégâts et le recul (mêmes entités touchées, mêmes dégâts, même ordre : les
 * dégâts sont calculés avant que les blocs ne disparaissent), mais elle ne détruit plus aucun bloc elle-même : le cratère est creusé
 * ensuite, comme si une grosse boule avait été enfoncée dans le sol au point d'impact.
 * <ul>
 *   <li>Forme : une boule. En dessous du point d'impact, une calotte sphérique (cuvette) : profondeur maximale au centre, qui
 *       diminue progressivement jusqu'au bord, sans paroi verticale. Au-dessus, la même forme en miroir (un dôme) : dans un endroit
 *       rempli de blocs, l'explosion creuse un rond entier ; en terrain plat, elle rase les reliefs en dôme au lieu d'une limite
 *       plate.</li>
 *   <li>Taille : le rayon du cratère est proportionnel à la puissance de l'explosion ({@link #RADIUS_PER_POWER}) et la profondeur
 *       à son rayon ({@link #DEPTH_RATIO}).</li>
 *   <li>Une seule forme pour tous les matériaux (terre, pierre, minerais...) : plus de marche entre la couche de terre et la roche.</li>
 *   <li>Jamais détruits : tout bloc incassable ou à très forte résistance (bedrock, obsidienne, eau, lave...), comme avant.</li>
 * </ul>
 * Comme avant, rien n'est détruit si la règle mobGriefing est désactivée ; les blocs à inventaire (coffres...) lâchent leur contenu et des
 * flammes se posent au hasard sur le fond du cratère.
 */
public class EnteiFireball extends LargeFireball {

    /** Rayon du cratère à la surface (blocs) = puissance de l'explosion x ce facteur (36 -> ~22 blocs, comme avant). */
    private static final double RADIUS_PER_POWER = 0.6;

    /**
     * Profondeur au centre = rayon x ce facteur. En dessous de 1 (une demi-sphère exacte aurait une paroi verticale au bord) : la cuvette
     * est une calotte d'une sphère un peu plus grande, ce qui donne un bord en pente douce.
     */
    private static final double DEPTH_RATIO = 0.75;

    /** Résistance à partir de laquelle un bloc survit (obsidienne, bedrock, eau, lave...). */
    private static final float INDESTRUCTIBLE = 100.0F;

    private final int blastPower;
    /** Rayon de contact (blocs) : le soleil touche une entité dès qu'elle est à cette distance de son centre (0 = vanilla). */
    private double hitRadius;

    public EnteiFireball(Level level, LivingEntity owner, Vec3 movement, int power) {
        super(level, owner, movement, power);
        this.blastPower = power;
    }

    /** Hitbox agrandie à la taille du soleil (qui grossit avec la charge). */
    public void setHitRadius(double hitRadius) {
        this.hitRadius = hitRadius;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && this.isAlive() && this.hitRadius > 0.5) {
            AABB area = this.getBoundingBox().inflate(this.hitRadius);
            double maxSqr = this.hitRadius * this.hitRadius;
            for (Entity entity : this.level().getEntities(this, area, e -> !(e instanceof EnteiSun) && this.canHitEntity(e))) {
                if (entity.getBoundingBox().distanceToSqr(this.position()) <= maxSqr) {
                    this.onHit(new EntityHitResult(entity));
                    return;
                }
            }
        }
        super.tick();
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
                carveCrater(level, this.position(), this, this.blastPower);
            }
            this.discard();
        }
    }

    /** Creuse la cuvette autour du point d'impact {@code impact} (voir la description de la classe). */
    private static void carveCrater(ServerLevel level, Vec3 impact, Entity source, int power) {
        double radius = power * RADIUS_PER_POWER;       // rayon à la surface
        double depth = radius * DEPTH_RATIO;            // profondeur au centre
        // Calotte sphérique : sphère de rayon R qui coupe le sol (au niveau de l'impact) selon un cercle de rayon "radius" et descend de
        // "depth" sous le point d'impact. Son centre est donc un peu AU-DESSUS du sol.
        double sphereRadius = (radius * radius + depth * depth) / (2.0 * depth);
        double cx = impact.x;
        double cy = impact.y + (sphereRadius - depth);
        double cz = impact.z;
        double sphereSqr = sphereRadius * sphereRadius;

        int horizontal = (int) Math.ceil(radius) + 1;
        int below = (int) Math.ceil(depth) + 1;
        int above = below; // le dôme du dessus est le miroir de la cuvette : même hauteur que la profondeur
        BlockPos origin = BlockPos.containing(impact);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<BlockPos> removed = new ArrayList<>();

        for (int dx = -horizontal; dx <= horizontal; dx++) {
            for (int dz = -horizontal; dz <= horizontal; dz++) {
                for (int dy = -below; dy <= above; dy++) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    // distance du centre du bloc au centre de la boule : la surface du cratère est une sphère exacte
                    double ex = pos.getX() + 0.5 - cx;
                    double by = pos.getY() + 0.5;
                    if (by > impact.y) {
                        by = 2.0 * impact.y - by; // au-dessus de l'impact : symétrique de la cuvette (dôme)
                    }
                    double ey = by - cy;
                    double ez = pos.getZ() + 0.5 - cz;
                    if (ex * ex + ey * ey + ez * ez > sphereSqr) {
                        continue;
                    }
                    if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    // Règles de destruction : on ne touche jamais aux blocs incassables ni à ceux de très forte résistance
                    if (state.getBlock().getExplosionResistance() >= INDESTRUCTIBLE || state.getDestroySpeed(level, pos) < 0.0F) {
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
                BlockPos below2 = p.below();
                if (level.getBlockState(below2).isFaceSturdy(level, below2, Direction.UP)) {
                    level.setBlock(p, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }
}
