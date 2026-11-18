package nolan.bloxpiece.village;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import static nolan.bloxpiece.village.VillageBlocks.*;

/**
 * Assemble tout le village de départ dans le monde, d'après VillageLayout (après le terrassement, fait par StarterVillage) :
 * place pavée, bâtiments, allées, étals, puits, mobilier urbain et jardins, lampadaires, arbres, verdure, puis finitions.
 * Un élément de mobilier qui tomberait sur une construction est ignoré (et signalé), jamais posé par-dessus.
 */
public final class VillagePlan {

    private VillagePlan() {
    }

    /**
     * @param groundY hauteur du sol réel (x, z) -> y, pour les arbres au bord du village (zone en pente) ; au coeur du
     *                village le sol est à center.getY().
     * @return les éléments ignorés (texte), vide si tout a été posé
     */
    public static List<String> buildAll(VillageBuilder b, BlockPos center, IntBinaryOperator groundY) {
        List<String> skipped = new ArrayList<>();
        STALLS_PLACED.clear();
        plaza(b, center);
        b.structureMode(true);
        List<Frame> doorFrames = new ArrayList<>();
        List<Building> doorBuildings = new ArrayList<>();
        for (VillageLayout.Entry entry : VillageLayout.BUILDINGS) {
            Building building = entry.recipe().get();
            Frame frame = new Frame(b, center.offset(entry.origin()), entry.rotation());
            building.build(frame);
            doorFrames.add(frame);
            doorBuildings.add(building);
        }
        Decor.fountain(new Frame(b, center, Rotation.NONE));
        b.structureMode(false);
        BlockPos spawn = center.offset(VillageLayout.PLAZA_SPAWN);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                b.keepClear(spawn.getX() + dx, spawn.getZ() + dz);
            }
        }
        for (BlockPos[] lane : VillageLayout.LANES) {
            Decor.path(b, center.offset(lane[0]), center.offset(lane[1]));
        }
        connectDoors(b, center, doorFrames, doorBuildings);
        repairNetwork(b, center);
        b.structureMode(true);
        stalls(b, center);
        b.structureMode(false);
        for (VillageLayout.Prop prop : VillageLayout.PROPS) {
            placeProp(b, center, prop, skipped);
        }
        lampposts(b, center);
        for (BlockPos p : VillageLayout.OAKS) {
            tree(b, center, p, groundY, false, skipped);
        }
        for (BlockPos p : VillageLayout.BIRCHES) {
            tree(b, center, p, groundY, true, skipped);
        }
        for (BlockPos p : VillageLayout.PALMS) {
            BlockPos at = center.offset(p);
            if (free(b, at, 1, 1, skipped, "palmier " + p)) {
                Decor.palm(b, new BlockPos(at.getX(), groundY.applyAsInt(at.getX(), at.getZ()), at.getZ()));
            }
        }
        exitRoads(b, center, groundY);
        repairNetwork(b, center);
        flowerBeds(b, center, 46);
        Decor.greenery(b, center, VillageLayout.TERRAIN_FLAT);
        b.finish();
        if (b.refused() > 0) {
            skipped.add(b.refused() + " bloc(s) de décor non posés pour ne pas abîmer une construction");
        }
        return skipped;
    }

    /**
     * Plates-bandes au pied des murs : sur l'herbe qui touche une construction (jamais une allée ni l'entrée d'une porte),
     * fleurs, rosiers et petits buissons. Les abords des maisons ne restent jamais nus.
     */
    private static void flowerBeds(VillageBuilder b, BlockPos center, int radius) {
        RandomSource random = b.random();
        int y = center.getY();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                if (b.isOccupied(x, z) || b.isKeptClear(x, z) || !b.get(new BlockPos(x, y - 1, z)).is(GRASS_BLOCK)
                        || !b.get(new BlockPos(x, y, z)).isAir()) {
                    continue;
                }
                boolean againstWall = false;
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    BlockState s = b.get(new BlockPos(x + d[0], y, z + d[1]));
                    if (!s.isAir() && !s.is(SHORT_GRASS) && b.isOccupied(x + d[0], z + d[1])) {
                        againstWall = true;
                    }
                }
                if (!againstWall || random.nextFloat() > 0.55F) {
                    continue;
                }
                Frame f = new Frame(b, new BlockPos(x, y, z), Rotation.NONE);
                float r = random.nextFloat();
                if (r < 0.6F) {
                    f.set(0, 0, 0, Decor.randomFlower(random));
                } else if (r < 0.75F) {
                    f.tallPlant(0, 0, 0, ROSE_BUSH);
                } else {
                    f.leaves(0, 0, 0, OAK_LEAVES);
                }
            }
        }
    }

    /**
     * Raccords : chaque seuil de porte est relié au point le plus proche du réseau (allées et bord de la place) par un petit
     * chemin, pour des jonctions continues jusqu'à chaque entrée. Les allées partant de la place y sont aussi raccordées.
     */
    private static void connectDoors(VillageBuilder b, BlockPos center, List<Frame> frames, List<Building> buildings) {
        List<BlockPos> network = new ArrayList<>();
        for (int k = 0; k < 96; k++) { // bord de la place ronde
            double a = Math.PI * 2 * k / 96;
            int ex = (int) Math.round(Math.cos(a) * (VillageLayout.PLAZA_RADIUS - 1));
            int ez = (int) Math.round(Math.sin(a) * (VillageLayout.PLAZA_RADIUS - 1));
            network.add(center.offset(ex, 0, ez));
        }
        for (BlockPos[] lane : VillageLayout.LANES) {
            BlockPos a = center.offset(lane[0]), c = center.offset(lane[1]);
            int steps = Math.max(Math.abs(c.getX() - a.getX()), Math.abs(c.getZ() - a.getZ()));
            for (int s = 0; s <= steps; s++) {
                network.add(new BlockPos(a.getX() + (c.getX() - a.getX()) * s / Math.max(1, steps), a.getY(),
                        a.getZ() + (c.getZ() - a.getZ()) * s / Math.max(1, steps)));
            }
        }
        // allées qui commencent près de la place : prolongées jusqu'au pavé
        for (BlockPos[] lane : VillageLayout.LANES) {
            BlockPos start = center.offset(lane[0]);
            int dx = start.getX() - center.getX(), dz = start.getZ() - center.getZ();
            double len = Math.sqrt(dx * dx + dz * dz), edgeR = VillageLayout.plazaRadius(dx, dz) - 1;
            if (len > edgeR && len <= edgeR + 7) {
                BlockPos edge = new BlockPos(center.getX() + (int) Math.round(dx / len * edgeR), start.getY(),
                        center.getZ() + (int) Math.round(dz / len * edgeR));
                Decor.path(b, edge, start);
            }
        }
        for (int i = 0; i < frames.size(); i++) {
            Frame f = frames.get(i);
            Building building = buildings.get(i);
            int x0 = building.doubleDoor ? -1 : 0;
            BlockPos end = f.pos(x0, 0, building.approach + 2);
            BlockPos target = nearestPath(b, end, 24);
            // perron / sentier d'un bloc de large, droit depuis la porte, puis jusqu'à l'allée la plus proche
            for (int z = building.approach; z <= building.approach + 2; z++) {
                trailCell(b, f.pos(x0, -1, z), false);
            }
            if (target != null) {
                trail(b, end, target);
            }
        }
    }

    /** Case d'allée la plus proche (terre battue, gravier, pavés, place) dans un rayon donné, ou null. */
    private static BlockPos nearestPath(VillageBuilder b, BlockPos from, int radius) {
        BlockPos best = null;
        int bestD = Integer.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d = dx * dx + dz * dz;
                if (d >= bestD || d > radius * radius) {
                    continue;
                }
                BlockPos g = new BlockPos(from.getX() + dx, from.getY() - 1, from.getZ() + dz);
                if (isPathBlock(b.get(g)) && !b.isStructure(g.above())) {
                    bestD = d;
                    best = g.above();
                }
            }
        }
        return best;
    }

    /** Sentier d'un bloc de large en ligne (presque) droite, qui s'arrête dès qu'il touche une allée existante. */
    private static void trail(VillageBuilder b, BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        int n = Math.max(Math.abs(dx), Math.abs(dz));
        for (int s = 1; s <= n; s++) {
            int x = from.getX() + Math.round((float) dx * s / n);
            int z = from.getZ() + Math.round((float) dz * s / n);
            BlockPos g = new BlockPos(x, from.getY() - 1, z);
            if (isPathBlock(b.get(g))) {
                return; // jonction faite
            }
            trailCell(b, g, true);
        }
    }

    private static void trailCell(VillageBuilder b, BlockPos ground, boolean keepAway) {
        if (!isSoil(b.get(ground)) || b.isStructure(ground.above())) {
            return;
        }
        if (keepAway && Decor.nearStructure(b, ground.getX(), ground.getY() + 1, ground.getZ(), 1)) {
            return;
        }
        float r = b.random().nextFloat();
        b.set(ground, (r < 0.8F ? DIRT_PATH : r < 0.92F ? GRAVEL : COARSE_DIRT).defaultBlockState());
        BlockState above = b.get(ground.above());
        if (!above.isAir() && above.canBeReplaced()) {
            b.set(ground.above(), AIR.defaultBlockState());
        }
    }

    private static boolean isPathBlock(BlockState s) {
        return s.is(DIRT_PATH) || s.is(GRAVEL) || s.is(COBBLESTONE) || s.is(STONE_BRICKS) || s.is(MOSSY_STONE_BRICKS) || s.is(ANDESITE);
    }

    private static boolean isSoil(BlockState s) {
        return s.is(GRASS_BLOCK) || s.is(DIRT) || s.is(PODZOL) || s.is(COARSE_DIRT) || s.is(MYCELIUM);
    }

    /** Rayon jusqu'où les routes de sortie se prolongent (au-delà du village, dans la nature). */
    private static final int EXIT_RADIUS = 74;

    /**
     * Routes de sortie vers le nord, l'est, le sud et l'ouest : chacune part du réseau d'allées et file vers l'extérieur jusqu'à
     * EXIT_RADIUS, bien au-delà du terrain aplani, en suivant le relief. Le tracé est calculé (plus court chemin sur la grille)
     * pour contourner les bâtiments à distance, l'eau et la lave ; il ne traverse jamais une construction.
     */
    private static void exitRoads(VillageBuilder b, BlockPos center, IntBinaryOperator groundY) {
        double[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        java.util.Set<Long> taken = new java.util.HashSet<>();
        for (double[] d : dirs) {
            // secteur de départ ±35°, élargi seulement si aucune allée ne s'y trouve
            for (double minDot : new double[] {0.82, 0.64, 0.42}) {
                if (exitRoad(b, center, groundY, d, taken, minDot)) {
                    break;
                }
            }
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** Une route de sortie dans la direction {@code d} : part d'une allée du même côté du village. */
    private static boolean exitRoad(VillageBuilder b, BlockPos center, IntBinaryOperator groundY, double[] d, java.util.Set<Long> taken,
            double minDot) {
        int r = EXIT_RADIUS, size = 2 * r + 1, y0 = center.getY();
        int[] groundCache = new int[size * size];
        byte[] pass = new byte[size * size]; // 0 inconnu, 1 libre, 2 interdit
        java.util.Arrays.fill(groundCache, Integer.MIN_VALUE);
        double[] dist = new double[size * size];
        int[] prev = new int[size * size];
        java.util.Arrays.fill(dist, Double.MAX_VALUE);
        java.util.Arrays.fill(prev, -1);
        java.util.PriorityQueue<double[]> queue = new java.util.PriorityQueue<>(java.util.Comparator.comparingDouble(a -> a[0]));
        // sources : les cases d'allée situées du côté de la sortie (secteur de ±35°), loin du centre
        int flat = VillageLayout.TERRAIN_FLAT;
        for (int dx = -flat; dx <= flat; dx++) {
            for (int dz = -flat; dz <= flat; dz++) {
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len < 10 || (dx * d[0] + dz * d[1]) / len < minDot) {
                    continue;
                }
                BlockState g = b.get(new BlockPos(center.getX() + dx, y0 - 1, center.getZ() + dz));
                if (g.is(DIRT_PATH) || g.is(GRAVEL)) {
                    int i = (dx + r) * size + (dz + r);
                    dist[i] = 0;
                    groundCache[i] = y0;
                    queue.add(new double[] {0, i});
                }
            }
        }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        while (!queue.isEmpty()) {
            double[] top = queue.poll();
            int i = (int) top[1];
            if (top[0] > dist[i]) {
                continue;
            }
            int cx = i / size - r, cz = i % size - r;
            for (int[] st : steps) {
                int nx = cx + st[0], nz = cz + st[1];
                if (nx < -r || nx > r || nz < -r || nz > r || nx * nx + nz * nz > r * r) {
                    continue;
                }
                int j = (nx + r) * size + (nz + r);
                if (pass[j] == 0) {
                    pass[j] = passable(b, center, nx, nz, groundY, groundCache, j) ? (byte) 1 : (byte) 2;
                }
                if (pass[j] == 2) {
                    continue;
                }
                if (nearTaken(taken, nx, nz, 6)) {
                    continue; // jamais collée à une autre route de sortie
                }
                double cost = (st[0] != 0 && st[1] != 0 ? 1.414 : 1.0)
                        + (Decor.nearStructure(b, center.getX() + nx, y0, center.getZ() + nz, 3) ? 1.5 : 0.0);
                int h1 = groundCache[i] == Integer.MIN_VALUE ? y0 : groundCache[i];
                if (Math.abs(groundCache[j] - h1) > 1) {
                    continue; // jamais de marche de plus d'un bloc : la route contourne les pentes raides
                }
                cost += Math.abs(groundCache[j] - h1) * 2.0; // évite les pentes
                if (dist[i] + cost < dist[j]) {
                    dist[j] = dist[i] + cost;
                    prev[j] = i;
                    queue.add(new double[] {dist[j], j});
                }
            }
        }
        // arrivée : la case du bord la moins coûteuse dans un secteur de ±15° autour de la direction
        {
            int best = -1;
            for (int i = 0; i < size * size; i++) {
                int x = i / size - r, z = i % size - r;
                double len = Math.sqrt(x * x + z * z);
                if (dist[i] == Double.MAX_VALUE || len < r - 2) {
                    continue;
                }
                if ((x * d[0] + z * d[1]) / len < Math.cos(Math.toRadians(15))) {
                    continue;
                }
                if (best < 0 || dist[i] < dist[best]) {
                    best = i;
                }
            }
            if (best < 0) {
                return false;
            }
            for (int i = best; i >= 0 && dist[i] > 0; i = prev[i]) {
                int x = i / size - r, z = i % size - r;
                taken.add(key(x, z));
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        int wx = center.getX() + x + ox, wz = center.getZ() + z + oz;
                        if (Decor.nearStructure(b, wx, y0, wz, 1)) {
                            continue;
                        }
                        int gy = surface(center, wx, wz, groundY);
                        BlockPos g = new BlockPos(wx, gy - 1, wz);
                        BlockState onTop = b.get(g.above());
                        int centreY = groundCache[i] == Integer.MIN_VALUE ? y0 : groundCache[i];
                        boolean axis = ox == 0 && oz == 0; // la case du tracé lui-même (pentes déjà limitées à 1 bloc)
                        if (!axis && (Math.abs(gy - centreY) > 1 || stepTooHigh(b, center, wx, wz, gy, groundY))) {
                            continue; // bord de route trop haut ou trop bas : la route se resserre plutôt que de faire une marche
                        }
                        if (isSoil(b.get(g)) && (onTop.isAir() || onTop.canBeReplaced())) {
                            b.set(g, Decor.pathBlock(b.random()));
                            for (int up = 1; up <= 2; up++) {   // herbes hautes, fleurs, buissons doubles
                                BlockState above = b.get(g.offset(0, up, 0));
                                if (!above.isAir() && above.canBeReplaced()) {
                                    b.set(g.offset(0, up, 0), AIR.defaultBlockState());
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    /** Composantes de réseau plus petites que ça (bouts de chemin isolés) : effacées si on ne peut pas les relier. */
    private static final int MIN_FRAGMENT = 12;

    /**
     * Réparation du réseau d'allées (dans le terrain aplani) : chaque morceau d'allée séparé du réseau principal (celui de la
     * place) y est relié par un sentier d'un bloc, par le plus court passage libre (jamais collé à un mur) ; un petit bout isolé
     * qu'on ne peut pas relier redevient de l'herbe. Plus aucune coupure, plus aucune porte isolée.
     */
    private static void repairNetwork(VillageBuilder b, BlockPos center) {
        int r = VillageLayout.TERRAIN_FLAT - 1, size = 2 * r + 1, y0 = center.getY();
        for (int pass = 0; pass < 12; pass++) {
            int[] comp = new int[size * size];
            java.util.Arrays.fill(comp, -1);
            List<List<Integer>> comps = new ArrayList<>();
            for (int i = 0; i < size * size; i++) {
                if (comp[i] != -1 || !isNetwork(b, center, i / size - r, i % size - r, r)) {
                    continue;
                }
                List<Integer> cells = new ArrayList<>();
                java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
                q.add(i);
                comp[i] = comps.size();
                while (!q.isEmpty()) {
                    int c = q.poll();
                    cells.add(c);
                    int cx = c / size - r, cz = c % size - r;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int nx = cx + dx, nz = cz + dz;
                            if (Math.abs(nx) > r || Math.abs(nz) > r) {
                                continue;
                            }
                            int j = (nx + r) * size + (nz + r);
                            if (comp[j] == -1 && isNetwork(b, center, nx, nz, r)) {
                                comp[j] = comps.size();
                                q.add(j);
                            }
                        }
                    }
                }
                comps.add(cells);
            }
            int main = comp[r * size + r]; // la place
            if (main < 0 || comps.size() <= 1) {
                return;
            }
            boolean changed = false;
            for (int k = 0; k < comps.size(); k++) {
                if (k == main) {
                    continue;
                }
                List<Integer> cells = comps.get(k);
                if (link(b, center, cells, comp, main, r, size)) {
                    changed = true;
                    break; // les composantes ont changé : on recommence l'analyse
                }
                if (cells.size() < MIN_FRAGMENT) {
                    for (int c : cells) {
                        BlockPos g = new BlockPos(center.getX() + c / size - r, y0 - 1, center.getZ() + c % size - r);
                        b.set(g, GRASS_BLOCK.defaultBlockState());
                    }
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
        }
    }

    private static boolean isNetwork(VillageBuilder b, BlockPos center, int x, int z, int r) {
        if (x * x + z * z > r * r) {
            return false;
        }
        if (VillageLayout.onPlaza(x, z)) {
            return true; // la place
        }
        BlockPos g = new BlockPos(center.getX() + x, center.getY() - 1, center.getZ() + z);
        BlockState s = b.get(g);
        return (s.is(DIRT_PATH) || s.is(GRAVEL) || s.is(COBBLESTONE) || s.is(COARSE_DIRT)) && !b.isStructure(g);
    }

    /** Relie une composante au réseau principal par le plus court sentier libre (largeur 1). */
    private static boolean link(VillageBuilder b, BlockPos center, List<Integer> cells, int[] comp, int main, int r, int size) {
        int y0 = center.getY();
        int[] prev = new int[size * size];
        java.util.Arrays.fill(prev, -2);
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        for (int c : cells) {
            prev[c] = -1;
            q.add(c);
        }
        int found = -1, steps = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty() && found < 0 && steps < 40000) {
            int c = q.poll();
            steps++;
            int cx = c / size - r, cz = c % size - r;
            for (int[] d : dirs) {
                int nx = cx + d[0], nz = cz + d[1];
                if (Math.abs(nx) > r || Math.abs(nz) > r || nx * nx + nz * nz > r * r) {
                    continue;
                }
                int j = (nx + r) * size + (nz + r);
                if (prev[j] != -2) {
                    continue;
                }
                if (comp[j] == main) {
                    prev[j] = c;
                    found = j;
                    break;
                }
                int wx = center.getX() + nx, wz = center.getZ() + nz;
                BlockPos g = new BlockPos(wx, y0 - 1, wz);
                BlockState above = b.get(g.above());
                boolean free = (isSoil(b.get(g)) || comp[j] >= 0) && (above.isAir() || above.canBeReplaced())
                        && !Decor.nearStructure(b, wx, y0, wz, 1);
                if (!free) {
                    continue;
                }
                prev[j] = c;
                q.add(j);
            }
        }
        if (found < 0) {
            return false;
        }
        for (int c = prev[found]; c >= 0 && prev[c] != -1; c = prev[c]) {
            trailCell(b, new BlockPos(center.getX() + c / size - r, y0 - 1, center.getZ() + c % size - r), false);
        }
        return true;
    }

    private static int surface(BlockPos center, int wx, int wz, IntBinaryOperator groundY) {
        int ddx = wx - center.getX(), ddz = wz - center.getZ();
        int flat = VillageLayout.TERRAIN_FLAT;
        return ddx * ddx + ddz * ddz <= flat * flat ? center.getY() : groundY.applyAsInt(wx, wz);
    }

    /** Une case de route voisine déjà posée est-elle à plus d'un bloc de hauteur de celle-ci ? */
    private static boolean stepTooHigh(VillageBuilder b, BlockPos center, int wx, int wz, int gy, IntBinaryOperator groundY) {
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int ny = surface(center, wx + d[0], wz + d[1], groundY);
            if (Math.abs(ny - gy) > 1 && isPathBlock(b.get(new BlockPos(wx + d[0], ny - 1, wz + d[1])))) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearTaken(java.util.Set<Long> taken, int x, int z, int r) {
        if (taken.isEmpty()) {
            return false;
        }
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (taken.contains(key(x + dx, z + dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Case franchissable par une route de sortie : sol naturel (pas d'eau ni de lave), aucune construction à côté. */
    private static boolean passable(VillageBuilder b, BlockPos center, int x, int z, IntBinaryOperator groundY, int[] cache, int j) {
        int wx = center.getX() + x, wz = center.getZ() + z;
        int gy = x * x + z * z <= VillageLayout.TERRAIN_FLAT * VillageLayout.TERRAIN_FLAT ? center.getY() : groundY.applyAsInt(wx, wz);
        cache[j] = gy;
        BlockState g = b.get(new BlockPos(wx, gy - 1, wz));
        if (!g.getFluidState().isEmpty() || g.is(ICE) || g.is(PACKED_ICE) || !(isSoil(g) || isPathBlock(g) || g.is(SAND))) {
            return false; // eau, lave, glace, tronc d'arbre, roche...
        }
        for (int dy = 0; dy <= 1; dy++) {
            BlockState s = b.get(new BlockPos(wx, gy + dy, wz));
            if (!s.isAir() && !s.canBeReplaced()) {
                return false; // mobilier, arbre, lampadaire
            }
        }
        return !Decor.nearStructure(b, wx, center.getY(), wz, 1);
    }

    /** Étals posés (position du vendeur, coordonnées monde), pour la console. */
    public static final List<BlockPos> STALLS_PLACED = new ArrayList<>();
    private static final int MAX_STALLS = 6;
    private static final int STALL_SPACING = 14;
    /** Les étals restent hors du coeur du village : la place et ses carrefours restent libres. */
    private static final int STALL_MIN_RADIUS = 30;
    private static final int[][] DIRS = {{0, 1}, {-1, 0}, {1, 0}, {0, -1}};
    private static final Rotation[] DIR_ROTATIONS = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.COUNTERCLOCKWISE_90,
            Rotation.CLOCKWISE_180};

    /**
     * Étals placés automatiquement le long des allées, en commençant par celles qui partent de la place : en retrait du chemin
     * (jamais dessus), comptoir tourné vers l'allée, à STALL_SPACING blocs l'un de l'autre. Interdit : sur un chemin, sur une
     * construction, à moins de 4 blocs de l'entrée d'un bâtiment, ou si l'espace des clients devant le comptoir est encombré.
     */
    private static void stalls(VillageBuilder b, BlockPos center) {
        List<BlockPos[]> lanes = new ArrayList<>(java.util.Arrays.asList(VillageLayout.LANES));
        lanes.sort(java.util.Comparator.comparingDouble(l -> Math.hypot(l[0].getX() + l[1].getX(), l[0].getZ() + l[1].getZ())));
        int variant = 0;
        for (BlockPos[] lane : lanes) {
            double dx = lane[1].getX() - lane[0].getX();
            double dz = lane[1].getZ() - lane[0].getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 6) {
                continue;
            }
            double ux = dx / length, uz = dz / length;
            for (double t = 3; t <= length - 2 && STALLS_PLACED.size() < MAX_STALLS; t += 1) {
                placed:
                for (int side : new int[] {1, -1}) {
                    for (int off = 5; off <= 9; off++) {
                        int x = (int) Math.round(lane[0].getX() + ux * t - uz * off * side);
                        int z = (int) Math.round(lane[0].getZ() + uz * t + ux * off * side);
                        double ax = uz * side, az = -ux * side; // vers l'allée
                        int d = Math.abs(ax) >= Math.abs(az) ? (ax > 0 ? 2 : 1) : (az > 0 ? 0 : 3);
                        Frame f = new Frame(b, center.offset(x, 0, z), DIR_ROTATIONS[d]);
                        if (stallFits(b, center, f)) {
                            Decor.stall(f, variant++);
                            STALLS_PLACED.add(f.pos(0, 0, -1));
                            break placed;
                        }
                    }
                }
            }
        }
    }

    private static boolean stallFits(VillageBuilder b, BlockPos center, Frame f) {
        BlockPos origin = f.pos(0, 0, 0);
        for (BlockPos other : STALLS_PLACED) {
            int ddx = other.getX() - origin.getX(), ddz = other.getZ() - origin.getZ();
            if (ddx * ddx + ddz * ddz < STALL_SPACING * STALL_SPACING) {
                return false;
            }
        }
        for (BlockPos[] lane : VillageLayout.LANES) {
            for (BlockPos end : lane) {
                int ex = center.getX() + end.getX() - origin.getX(), ez = center.getZ() + end.getZ() - origin.getZ();
                if (ex * ex + ez * ez < 7 * 7) {
                    return false; // loin des croisements et des débuts d'allée
                }
            }
        }
        int cdx = origin.getX() - center.getX(), cdz = origin.getZ() - center.getZ();
        if (cdx * cdx + cdz * cdz < STALL_MIN_RADIUS * STALL_MIN_RADIUS) {
            return false; // pas dans le coeur du village (place, croisements devant la taverne et les échoppes)
        }
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                BlockPos p = f.pos(x, 0, z);
                boolean footprint = Math.abs(x) <= 3 && z >= -3 && z <= 2;
                boolean margin = Math.abs(x) <= 4 && z >= -4 && z <= 2;
                if (margin && isPath(b.get(p.below()))) {
                    return false; // ni comptoir ni auvent près d'une allée : un bloc de marge sur les côtés et à l'arrière
                }
                boolean customers = Math.abs(x) <= 2 && z >= 3;
                if (footprint) {
                    BlockState ground = b.get(p.below());
                    if (ground.is(DIRT_PATH) || ground.is(GRAVEL) || ground.is(COBBLESTONE) || ground.is(WATER)) {
                        return false; // jamais sur un chemin
                    }
                }
                if (footprint || customers) {
                    for (int y = 0; y <= 4; y++) {
                        if (!b.get(p.offset(0, y, 0)).isAir()) {
                            return false;
                        }
                    }
                }
                if (b.isKeptClear(p.getX(), p.getZ())) {
                    return false; // devant une entrée
                }
                for (int y = 0; y <= 3; y++) {
                    if (Math.abs(x) <= 5 && z >= -4 && b.isStructure(p.offset(0, y, 0))) {
                        return false; // au moins un bloc d'air entre l'étal et une autre construction
                    }
                }
            }
        }
        return true;
    }

    /** Écart minimal entre deux lampadaires. */
    private static final int LAMP_SPACING = 10;

    /**
     * Lampadaires répartis automatiquement : aux coins de la place, puis le long de chaque allée tous les ~10 blocs, sur le
     * bas-côté (jamais sur le chemin), bras tourné vers l'allée. Un lampadaire n'est posé que s'il reste à LAMP_SPACING blocs
     * des autres, à 3 blocs au moins de l'entrée d'un bâtiment, sans toucher ni façade ni mobilier.
     */
    private static void lampposts(VillageBuilder b, BlockPos center) {
        List<BlockPos> lamps = new ArrayList<>();
        int c = (int) Math.round((VillageLayout.PLAZA_RADIUS - 2) / Math.sqrt(2)); // en diagonale, près du bord de la place
        for (int[] corner : new int[][] {{-c, -c}, {c, -c}, {-c, c}, {c, c}}) {
            tryLamp(b, center, lamps, corner[0], corner[1], -Integer.signum(corner[0]), 0);
        }
        for (BlockPos[] lane : VillageLayout.LANES) {
            double dx = lane[1].getX() - lane[0].getX();
            double dz = lane[1].getZ() - lane[0].getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1) {
                continue;
            }
            double ux = dx / length, uz = dz / length;
            for (double t = 2; t <= length; t += 1) {
                done:
                for (int side : new int[] {1, -1}) {
                    for (int off = 3; off <= 5; off++) {
                        int x = (int) Math.round(lane[0].getX() + ux * t - uz * off * side);
                        int z = (int) Math.round(lane[0].getZ() + uz * t + ux * off * side);
                        // bras vers l'allée : direction dominante de (-normale)
                        double ax = uz * side, az = -ux * side;
                        int armX = Math.abs(ax) >= Math.abs(az) ? (int) Math.signum(ax) : 0;
                        int armZ = armX == 0 ? (int) Math.signum(az) : 0;
                        if (tryLamp(b, center, lamps, x, z, armX, armZ)) {
                            break done;
                        }
                    }
                }
            }
        }
    }

    private static boolean tryLamp(VillageBuilder b, BlockPos center, List<BlockPos> lamps, int lx, int lz, int armX, int armZ) {
        BlockPos at = center.offset(lx, 0, lz);
        for (BlockPos other : lamps) {
            int ddx = other.getX() - at.getX(), ddz = other.getZ() - at.getZ();
            if (ddx * ddx + ddz * ddz < LAMP_SPACING * LAMP_SPACING) {
                return false;
            }
        }
        int y = at.getY();
        // pas sur le chemin ni sur la place
        BlockState under = b.get(at.below());
        boolean onPlaza = VillageLayout.onPlaza(lx, lz);
        if (!onPlaza && !under.is(GRASS_BLOCK) && !under.is(DIRT)) {
            return false;
        }
        // loin des entrées, des façades et du mobilier
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (b.isKeptClear(at.getX() + x, at.getZ() + z)) {
                    return false;
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int dy = 0; dy <= 4; dy++) {
                    if (!b.get(new BlockPos(at.getX() + x, y + dy, at.getZ() + z)).isAir()) {
                        return false;
                    }
                }
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int dy = 2; dy <= 3; dy++) {
                    if (!b.get(new BlockPos(at.getX() + x, y + dy, at.getZ() + z)).isAir()) {
                        return false; // trop près d'une façade
                    }
                }
            }
        }
        Rotation rotation = armX > 0 ? Rotation.NONE : armX < 0 ? Rotation.CLOCKWISE_180 : armZ > 0 ? Rotation.CLOCKWISE_90
                : Rotation.COUNTERCLOCKWISE_90;
        Decor.lamppost(new Frame(b, at, rotation));
        lamps.add(at);
        return true;
    }

    private static void tree(VillageBuilder b, BlockPos center, BlockPos p, IntBinaryOperator groundY, boolean birch, List<String> skipped) {
        BlockPos at = center.offset(p);
        if (free(b, at, 2, 2, skipped, "arbre " + p)) {
            Decor.tree(b, new BlockPos(at.getX(), groundY.applyAsInt(at.getX(), at.getZ()), at.getZ()), birch);
        }
    }

    private static void placeProp(VillageBuilder b, BlockPos center, VillageLayout.Prop prop, List<String> skipped) {
        BlockPos at = center.offset(prop.pos());
        Frame f = new Frame(b, at, prop.rotation());
        int n = prop.size();
        switch (prop.kind()) {
            case GARDEN -> {
                if (freeBox(b, at, 0, 0, n - 1, n - 1, skipped, "jardin " + prop.pos())) {
                    Decor.garden(f, n, n);
                }
            }
            case BENCH -> {
                if (free(b, at, 2, 0, skipped, "banc " + prop.pos())) {
                    Decor.bench(f);
                }
            }
            case CART -> {
                if (freeBox(b, at, -1, -1, 2, 2, skipped, "charrette " + prop.pos())) {
                    Decor.cart(f);
                }
            }
            case CRATES -> {
                if (freeBox(b, at, 0, 0, 1, 1, skipped, "caisses " + prop.pos())) {
                    Decor.crates(f);
                }
            }
            case WELL -> {
                if (free(b, at, 3, 3, skipped, "puits " + prop.pos())) {
                    Decor.well(f);
                }
            }
            case WALL_X -> Decor.lowWall(f, 0, 0, n - 1, 0);
            case WALL_Z -> Decor.lowWall(f, 0, 0, 0, n - 1);
            case HEDGE_X -> Decor.hedge(f, 0, 0, n - 1, 0);
            case HEDGE_Z -> Decor.hedge(f, 0, 0, 0, n - 1);
        }
    }

    /** Les colonnes autour de {@code at} (±rx, ±rz) sont-elles libres de toute construction ? Sinon l'élément est ignoré. */
    private static boolean free(VillageBuilder b, BlockPos at, int rx, int rz, List<String> skipped, String what) {
        return freeBox(b, at, -rx, -rz, rx, rz, skipped, what);
    }

    /** Libre = rien de construit au niveau du sol ni juste au-dessus, et pas sur une allée (le passage reste dégagé). */
    private static boolean freeBox(VillageBuilder b, BlockPos at, int x1, int z1, int x2, int z2, List<String> skipped, String what) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (isPath(b.get(new BlockPos(at.getX() + x, at.getY() - 1, at.getZ() + z)))) {
                    skipped.add(what + " (sur une allée)");
                    return false;
                }
                for (int y = 0; y <= 1; y++) {
                    if (!b.get(new BlockPos(at.getX() + x, at.getY() + y, at.getZ() + z)).isAir()) {
                        skipped.add(what);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isPath(BlockState ground) {
        return ground.is(DIRT_PATH) || ground.is(GRAVEL) || ground.is(COBBLESTONE);
    }

    /**
     * Place pavée RONDE (bord doucement ondulé) : pierre taillée, pavés, andésite, un peu de mousse, un anneau d'andésite polie
     * autour de la fontaine et une bordure de pavés moussus sur le pourtour.
     */
    private static void plaza(VillageBuilder b, BlockPos center) {
        RandomSource random = b.random();
        int half = VillageLayout.PLAZA_HALF;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if (!VillageLayout.onPlaza(dx, dz)) {
                    continue;
                }
                double d = Math.sqrt(dx * dx + dz * dz), edge = VillageLayout.plazaRadius(dx, dz);
                BlockState s;
                if (d >= 6.5 && d < 7.5) {
                    s = POLISHED_ANDESITE.defaultBlockState();          // anneau autour de la fontaine
                } else if (d > edge - 1) {
                    s = (random.nextFloat() < 0.5F ? MOSSY_COBBLESTONE : COBBLESTONE).defaultBlockState(); // bordure
                } else {
                    float r = random.nextFloat();
                    s = (r < 0.5F ? STONE_BRICKS : r < 0.75F ? COBBLESTONE : r < 0.9F ? ANDESITE : MOSSY_STONE_BRICKS)
                            .defaultBlockState();
                }
                b.set(center.offset(dx, -1, dz), s);
            }
        }
    }
}
