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
        List<BlockPos> doorFronts = new ArrayList<>();
        for (VillageLayout.Entry entry : VillageLayout.BUILDINGS) {
            Building building = entry.recipe().get();
            Frame frame = new Frame(b, center.offset(entry.origin()), entry.rotation());
            building.build(frame);
            doorFronts.add(frame.pos(building.doubleDoor ? -1 : 0, 0, building.approach));
        }
        Decor.well(new Frame(b, center, Rotation.NONE));
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
        connectDoors(b, center, doorFronts);
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
    private static void connectDoors(VillageBuilder b, BlockPos center, List<BlockPos> doorFronts) {
        List<BlockPos> network = new ArrayList<>();
        int half = VillageLayout.PLAZA_HALF;
        for (int i = -half; i <= half; i++) {
            network.add(center.offset(i, 0, -half));
            network.add(center.offset(i, 0, half));
            network.add(center.offset(-half, 0, i));
            network.add(center.offset(half, 0, i));
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
            int dx = Math.abs(start.getX() - center.getX()), dz = Math.abs(start.getZ() - center.getZ());
            if (Math.max(dx, dz) > half && Math.max(dx, dz) <= half + 6) {
                BlockPos edge = new BlockPos(center.getX() + clampHalf(start.getX() - center.getX(), half), start.getY(),
                        center.getZ() + clampHalf(start.getZ() - center.getZ(), half));
                Decor.path(b, edge, start);
            }
        }
        for (BlockPos door : doorFronts) {
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos n : network) {
                double d = (double) (n.getX() - door.getX()) * (n.getX() - door.getX())
                        + (double) (n.getZ() - door.getZ()) * (n.getZ() - door.getZ());
                if (d < bestD) {
                    bestD = d;
                    best = n;
                }
            }
            if (best != null) { // même tout près : le seuil lui-même devient chemin
                Decor.path(b, best, door);
            }
        }
    }

    private static int clampHalf(int v, int half) {
        return Math.max(-half, Math.min(half, v));
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
        int c = VillageLayout.PLAZA_HALF - 1;
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
        boolean onPlaza = Math.abs(lx) <= VillageLayout.PLAZA_HALF && Math.abs(lz) <= VillageLayout.PLAZA_HALF;
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

    /** Place pavée (pierre taillée, pavés, andésite, un peu de mousse), coins arrondis. */
    private static void plaza(VillageBuilder b, BlockPos center) {
        RandomSource random = b.random();
        int half = VillageLayout.PLAZA_HALF;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if (Math.abs(dx) == half && Math.abs(dz) == half) {
                    continue;
                }
                float r = random.nextFloat();
                BlockState s = (r < 0.5F ? STONE_BRICKS : r < 0.75F ? COBBLESTONE : r < 0.9F ? ANDESITE : MOSSY_STONE_BRICKS)
                        .defaultBlockState();
                b.set(center.offset(dx, -1, dz), s);
            }
        }
    }
}
