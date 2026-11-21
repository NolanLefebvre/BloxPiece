package nolan.bloxpiece.ability;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Destruction du terrain par la foudre du Goro Goro no Mi (côté serveur, seulement si la règle mobGriefing est active).
 * <ul>
 *   <li>{@link #crater} : un vrai cratère en cuvette, creusé bloc par bloc (bord irrégulier, plus profond au centre, qui suit
 *       le relief), le fond et le bord brûlés par la foudre (roche noire, basalte, tuf...). Les blocs disparaissent sans objets
 *       (pas de milliers d'objets au sol) et le terrain modifié est sauvegardé avec le monde comme n'importe quel bloc.</li>
 *   <li>Le travail est étalé sur plusieurs ticks ({@link #BUDGET} blocs par tick pour TOUS les cratères en cours) et part du
 *       centre vers l'extérieur : le cratère s'ouvre sous les yeux, sans pic de lag ni paquet géant en multijoueur.</li>
 *   <li>Mêmes protections que le Gura Gura no Mi (GuraTerrain.breakable) : jamais les blocs incassables ni très résistants
 *       (obsidienne...), ni les blocs à contenu (coffres...), ni les liquides, ni le village de départ (SafeZone). Chaque bloc
 *       est revérifié au moment d'être détruit, et seuls les chunks chargés sont touchés.</li>
 *   <li>Fissures et blocs projetés : ceux du Gura (GuraTerrain.cracks / debris), réutilisés tels quels.</li>
 * </ul>
 */
public final class GoroTerrain {

    /** Nombre maximal de blocs modifiés par tick serveur, tous cratères confondus. */
    static final int BUDGET = 900;
    /** Blocs brûlés par la foudre au fond et au bord du cratère. */
    private static final BlockState[] SCORCH = {
            Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.TUFF.defaultBlockState(),
            Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState()};

    /** Un bloc à modifier : position et nouvel état (null = air). */
    private record Edit(BlockPos pos, BlockState to) {
    }

    private static final class Job {
        final ServerLevel level;
        final List<Edit> edits;
        int next;

        Job(ServerLevel level, List<Edit> edits) {
            this.level = level;
            this.edits = edits;
        }
    }

    private static final List<Job> JOBS = new ArrayList<>();

    private GoroTerrain() {
    }

    /** Hauteur des arbres rasés au-dessus du cratère (les arbres de jungle géants font jusqu'à ~30 blocs). */
    private static final int VEGETATION_CLEAR = 32;

    /**
     * Cratère de foudre centré sur le sol en {@code at} : rayon {@code radius} blocs, profondeur {@code depth} blocs au centre.
     * Le vrai sol est cherché SOUS la végétation (troncs, feuilles, buissons, herbes ne comptent pas comme sol) : le cratère
     * se creuse aussi sous les arbres, et tout ce qui pousse au-dessus de lui (arbres entiers, buissons, fleurs) est rasé.
     * Rien ne se passe si la règle mobGriefing est désactivée.
     */
    static void crater(ServerLevel level, Vec3 at, double radius, double depth) {
        if (!GuraTerrain.griefing(level) || radius < 0.8) {
            return;
        }
        RandomSource random = level.getRandom();
        int cx = Mth.floor(at.x), cz = Mth.floor(at.z);
        // l'impact a pu être placé sur la cime d'un arbre : on redescend jusqu'au vrai sol
        int cy = terrainTop(level, cx, Mth.floor(at.y) + 2, cz, VEGETATION_CLEAR + 8);
        if (cy == IceTerrain.NO_SURFACE) {
            return;
        }
        int range = (int) Math.ceil(radius) + 2;
        double p1 = random.nextDouble() * 6.28, p2 = random.nextDouble() * 6.28;
        List<double[]> columns = new ArrayList<>(); // {x, z, distance}
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= radius + 2.0) {
                    columns.add(new double[] {cx + dx, cz + dz, d});
                }
            }
        }
        columns.sort((a, b) -> Double.compare(a[2], b[2])); // du centre vers le bord : le cratère s'ouvre
        List<Edit> edits = new ArrayList<>();
        int search = (int) Math.ceil(depth) + 8;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (double[] col : columns) {
            int x = (int) col[0], z = (int) col[1];
            double d = col[2];
            double angle = Math.atan2(z - cz, x - cx);
            // bord irrégulier : le rayon varie avec l'angle
            double edge = radius * (1.0 + 0.12 * Math.sin(angle * 3.0 + p1) + 0.07 * Math.sin(angle * 7.0 + p2));
            int gy = terrainTop(level, x, cy + search, z, search * 2);
            if (gy == IceTerrain.NO_SURFACE) {
                continue;
            }
            if (d > edge) {
                // bord du cratère : sol brûlé par endroits
                if (d <= edge + 2.0 && random.nextFloat() < 0.3F) {
                    edits.add(new Edit(new BlockPos(x, gy, z), SCORCH[random.nextInt(SCORCH.length)]));
                }
                if (d <= edge + 1.0) {
                    clearVegetation(level, m, x, gy + 1, z, edits); // les arbres à cheval sur le bord tombent aussi
                }
                continue;
            }
            double k = d / edge;
            double h = depth * Math.sqrt(Math.max(0.0, 1.0 - k * k)) * (0.85 + 0.25 * random.nextDouble()); // cuvette
            int down = (int) Math.round(h);
            int up = (int) Math.round(h * 0.6) + 1; // la cuvette se prolonge vers le haut : buttes, murets, terre rasés
            for (int y = gy + up; y > gy - down; y--) {
                edits.add(new Edit(new BlockPos(x, y, z), null));
            }
            clearVegetation(level, m, x, gy + up + 1, z, edits); // arbres, buissons et plantes au-dessus : rasés entièrement
            if (random.nextFloat() < (k < 0.7 ? 0.55F : 0.3F)) {
                edits.add(new Edit(new BlockPos(x, gy - down, z), SCORCH[random.nextInt(SCORCH.length)])); // fond brûlé
            }
        }
        if (!edits.isEmpty()) {
            JOBS.add(new Job(level, edits));
        }
    }

    /** Végétation : troncs, feuilles, et tout bloc sans collision ou remplaçable (herbes, fleurs, lianes, pousses...). */
    private static boolean vegetation(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                || state.canBeReplaced() || state.getCollisionShape(level, pos).isEmpty());
    }

    /**
     * Le vrai sol de la colonne (x, z) : premier bloc solide en descendant depuis {@code fromY} sur {@code range} blocs, en
     * ignorant la végétation (un tronc d'arbre n'est pas le sol). Liquides : on continue dessous (fond du lac).
     */
    private static int terrainTop(ServerLevel level, int x, int fromY, int z, int range) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = fromY; y >= fromY - range; y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.isAir() || !state.getFluidState().isEmpty() || vegetation(level, pos, state)) {
                continue;
            }
            if (state.isCollisionShapeFullBlock(level, pos)) {
                return y;
            }
        }
        return IceTerrain.NO_SURFACE;
    }

    /** Ajoute la destruction de toute la végétation de la colonne, de {@code fromY} jusqu'à {@link #VEGETATION_CLEAR} blocs plus haut. */
    private static void clearVegetation(ServerLevel level, BlockPos.MutableBlockPos m, int x, int fromY, int z, List<Edit> edits) {
        for (int y = fromY; y <= fromY + VEGETATION_CLEAR; y++) {
            BlockState state = level.getBlockState(m.set(x, y, z));
            if (vegetation(level, m, state)) {
                edits.add(new Edit(new BlockPos(x, y, z), null));
            }
        }
    }

    /**
     * Cicatrice complète d'un impact de foudre : cratère, fissures qui se propagent tout autour (GuraTerrain.cracks) et blocs
     * de surface arrachés et projetés (GuraTerrain.debris). Rien si mobGriefing est désactivé.
     */
    static void scar(ServerLevel level, Vec3 at, double radius, double depth, double crackRadius, int crackRays, int debris) {
        if (!GuraTerrain.griefing(level)) {
            return;
        }
        // centre posé sur le vrai sol (l'impact a pu toucher la cime d'un arbre) : fissures et débris partent bien du sol
        int gy = terrainTop(level, Mth.floor(at.x), Mth.floor(at.y) + 2, Mth.floor(at.z), VEGETATION_CLEAR + 8);
        if (gy != IceTerrain.NO_SURFACE) {
            at = new Vec3(at.x, gy + 1.0, at.z);
        }
        crater(level, at, radius, depth);
        if (crackRays > 0 && crackRadius > radius) {
            GuraTerrain.cracks(level, at, radius * 0.8, crackRadius, crackRays, Math.max(4, (int) (crackRadius - radius)));
        }
        if (debris > 0) {
            GuraTerrain.debris(level, at, radius + 1.5, debris, 0.6 + radius * 0.05);
        }
    }

    /** À chaque tick serveur (Goro) : avance les cratères en cours, dans la limite du budget. */
    static void tick() {
        int budget = BUDGET;          // blocs réellement modifiés
        int checks = BUDGET * 6;      // positions examinées (l'air au-dessus du sol ne coûte presque rien)
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext() && budget > 0 && checks > 0) {
            Job job = it.next();
            ServerLevel level = job.level;
            int particles = 0;
            while (job.next < job.edits.size() && budget > 0 && checks > 0) {
                Edit e = job.edits.get(job.next++);
                checks--;
                BlockPos pos = e.pos();
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!GuraTerrain.breakable(level, pos, state)) {
                    continue; // revérifié au dernier moment (un coffre a pu être posé entre-temps)
                }
                budget--;
                if (e.to() == null) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    if (particles < 24 && level.getRandom().nextFloat() < 0.08F) { // quelques éclats seulement (réseau)
                        particles++;
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5,
                                pos.getZ() + 0.5, 5, 0.4, 0.4, 0.4, 0.2);
                    }
                } else if (state.isCollisionShapeFullBlock(level, pos)) {
                    level.setBlock(pos, e.to(), 3);
                }
            }
            if (job.next >= job.edits.size()) {
                it.remove();
            }
        }
    }

    static void clear() {
        JOBS.clear();
    }
}
