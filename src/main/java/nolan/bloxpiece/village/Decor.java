package nolan.bloxpiece.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import static nolan.bloxpiece.village.VillageBlocks.*;

/**
 * Éléments d'ambiance du village : puits, lampadaires, étals de marché, arbres, palmiers, chemins sinueux et verdure.
 * Les positions de chaque élément sont dans VillageLayout.
 */
public final class Decor {

    private static final Block[] FLOWERS = {POPPY, DANDELION, AZURE_BLUET, OXEYE_DAISY,
            CORNFLOWER, ALLIUM, RED_TULIP, ORANGE_TULIP};

    private Decor() {
    }

    /**
     * Puits couvert au centre de la place (emprise 7 x 7) : socle en marches de pierre, margelle en pierre taillée (murets sur
     * deux côtés, rebord en dalles côté accès), quatre piliers en bois, traverse avec corde (clôture) qui descend vers l'eau,
     * toit en croupe à trois rangs d'escaliers, seau (chaudron) suspendu à la corde, lanternes, pots de fleurs et tonneau autour.
     */
    public static void well(Frame f) {
        // fût et eau
        f.fill(-2, -4, -2, 2, -4, 2, STONE_BRICKS);
        f.fill(-1, -3, -1, 1, -1, 1, WATER);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                    f.fill(x, -3, z, x, -1, z, STONE_BRICKS);
                }
            }
        }
        f.air(-3, 0, -3, 3, 7, 3);
        // socle : marches de pierre tout autour (relief)
        for (int i = -3; i <= 3; i++) {
            f.stairs(i, 0, -3, STONE_BRICK_STAIRS, Direction.SOUTH, false);
            f.stairs(i, 0, 3, STONE_BRICK_STAIRS, Direction.NORTH, false);
            f.stairs(-3, 0, i, STONE_BRICK_STAIRS, Direction.EAST, false);
            f.stairs(3, 0, i, STONE_BRICK_STAIRS, Direction.WEST, false);
        }
        // margelle
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean ring = Math.abs(x) == 2 || Math.abs(z) == 2;
                if (!ring) {
                    continue;
                }
                f.set(x, 0, z, (Block) ((x * 3 + z) % 4 == 0 ? MOSSY_STONE_BRICKS : POLISHED_ANDESITE));
                boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
                if (corner) {
                    continue;
                }
                if (Math.abs(x) == 2) {
                    f.set(x, 1, z, STONE_BRICK_WALL);           // murets à l'est et à l'ouest
                } else {
                    f.slab(x, 1, z, STONE_BRICK_SLAB, false);  // rebord côté accès (nord, sud)
                }
            }
        }
        // piliers d'angle et traverse
        for (int x : new int[] {-2, 2}) {
            for (int z : new int[] {-2, 2}) {
                f.fill(x, 1, z, x, 3, z, STRIPPED_SPRUCE_LOG);
            }
            f.log(x, 3, -1, SPRUCE_LOG, Direction.Axis.Z);
            f.log(x, 3, 0, SPRUCE_LOG, Direction.Axis.Z);
            f.log(x, 3, 1, SPRUCE_LOG, Direction.Axis.Z);
        }
        for (int x = -1; x <= 1; x++) {
            f.log(x, 3, 0, STRIPPED_SPRUCE_LOG, Direction.Axis.X);   // treuil
        }
        f.set(0, 2, 0, SPRUCE_FENCE);                               // corde sous le treuil
        f.set(0, 1, 0, CAULDRON);                                   // seau suspendu au bout de la corde
        f.set(0, 0, 0, AIR);
        // toit en croupe : trois rangs d'escaliers + faîte
        for (int r = 3; r >= 1; r--) {
            int y = 4 + (3 - r);
            for (int i = -r; i <= r; i++) {
                f.stairs(i, y, -r, SPRUCE_STAIRS, Direction.SOUTH, false);
                f.stairs(i, y, r, SPRUCE_STAIRS, Direction.NORTH, false);
                f.stairs(-r, y, i, SPRUCE_STAIRS, Direction.EAST, false);
                f.stairs(r, y, i, SPRUCE_STAIRS, Direction.WEST, false);
            }
            if (r > 1) {
                f.fill(-r + 1, y, -r + 1, r - 1, y, r - 1, SPRUCE_PLANKS);
            }
        }
        f.set(0, 6, 0, SPRUCE_PLANKS);
        f.slab(0, 7, 0, SPRUCE_SLAB, false);
        // lanternes sous l'avant-toit
        f.lantern(0, 3, -3, true);
        f.lantern(0, 3, 3, true);
        // abords : pots, chaudron, tonneaux
        f.set(-4, 0, -2, POTTED_RED_TULIP);
        f.set(-4, 0, 2, POTTED_AZALEA_BUSH);
        f.set(4, 0, -2, POTTED_FERN);
        f.barrel(4, 0, 2, Direction.UP);
        f.set(4, 0, 1, POTTED_DANDELION);
    }

    /**
     * Fontaine au centre de la place (emprise ronde de rayon 5,6) : marche circulaire en escaliers de pierre taillée, margelle
     * en pierre taillée et pierre sculptée avec rebord en dalles, bassin d'eau, colonne centrale sculptée (pierre sculptée,
     * murets) coiffée d'une vasque d'où l'eau jaillit et retombe en cascade dans le bassin, quatre lanternes sur des bornes,
     * azalées en pot sur la margelle.
     */
    public static void fountain(Frame f) {
        // fond et dégagement
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 5.6) {
                    continue;
                }
                f.air(x, 0, z, x, 6, z);
                if (d <= 3.6) {
                    f.set(x, -2, z, STONE_BRICKS);
                    f.set(x, -1, z, WATER);
                    f.set(x, 0, z, WATER);
                } else if (d <= 4.6) {
                    f.set(x, -2, z, STONE_BRICKS);
                    f.set(x, -1, z, STONE_BRICKS);
                    f.set(x, 0, z, (Block) ((x + z) % 3 == 0 ? CHISELED_STONE_BRICKS : STONE_BRICKS));
                    f.slab(x, 1, z, STONE_BRICK_SLAB, false);                   // rebord pour s'asseoir
                } else {
                    // marche tout autour, tournée vers le centre
                    Direction toward = Math.abs(x) >= Math.abs(z) ? (x > 0 ? Direction.WEST : Direction.EAST)
                            : (z > 0 ? Direction.NORTH : Direction.SOUTH);
                    f.stairs(x, 0, z, STONE_BRICK_STAIRS, toward, false);
                }
            }
        }
        // colonne centrale sculptée
        f.set(0, -1, 0, STONE_BRICKS);
        f.set(0, 0, 0, CHISELED_STONE_BRICKS);
        f.set(0, 1, 0, STONE_BRICK_WALL);
        f.set(0, 2, 0, STONE_BRICK_WALL);
        f.set(0, 3, 0, CHISELED_STONE_BRICKS);
        // vasque : escaliers renversés autour du sommet
        f.stairs(1, 3, 0, STONE_BRICK_STAIRS, Direction.WEST, true);
        f.stairs(-1, 3, 0, STONE_BRICK_STAIRS, Direction.EAST, true);
        f.stairs(0, 3, 1, STONE_BRICK_STAIRS, Direction.NORTH, true);
        f.stairs(0, 3, -1, STONE_BRICK_STAIRS, Direction.SOUTH, true);
        f.slab(0, 4, 0, STONE_BRICK_SLAB, false);
        // l'eau jaillit au sommet et retombe en cascade aux quatre coins de la vasque (posée « vivante » pour couler tout de suite)
        f.live(0, 5, 0, WATER);
        // bornes à lanterne sur la margelle (diagonales) et azalées en pot (points cardinaux)
        for (int[] c : new int[][] {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            f.set(c[0], 1, c[1], STONE_BRICK_WALL);
            f.lantern(c[0], 2, c[1], false);
        }
        f.set(4, 1, 0, POTTED_AZALEA_BUSH);
        f.set(-4, 1, 0, POTTED_AZALEA_BUSH);
        f.set(0, 1, 4, POTTED_RED_TULIP);
        f.set(0, 1, -4, POTTED_RED_TULIP);
    }

    /** Lampadaire en bois : poteau sur socle de pierre, bras et lanterne suspendue (bras vers +x local). */
    public static void lamppost(Frame f) {
        f.set(0, 0, 0, COBBLESTONE_WALL);
        f.fill(0, 1, 0, 0, 3, 0, SPRUCE_FENCE);
        f.set(1, 3, 0, SPRUCE_FENCE);
        f.lantern(1, 2, 0, true);
    }

    private static final Block[][] AWNINGS = {{RED_WOOL, WHITE_WOOL}, {YELLOW_WOOL, WHITE_WOOL}, {BLUE_WOOL, WHITE_WOOL},
            {ORANGE_WOOL, LIME_WOOL}, {RED_WOOL, YELLOW_WOOL}, {CYAN_WOOL, WHITE_WOOL}};
    private static final Block[][] GOODS = {{POTTED_RED_TULIP, RED_CARPET, LANTERN}, {LANTERN, GREEN_CARPET, POTTED_AZALEA_BUSH},
            {POTTED_DANDELION, YELLOW_CARPET, POTTED_FERN}, {LANTERN, LIGHT_BLUE_CARPET, POTTED_POPPY}};

    /**
     * Étal de marché couvert (repère local : clients côté +z, comptoir en z = 0, vendeur en z = -1, réserve en z = -3 ;
     * emprise x -3..3, z -3..2). Quatre poteaux en rondins, comptoir en escaliers renversés et dalles mêlés habillé de trappes,
     * détails légers dessus (pots de fleurs, tapis, lanternes selon {@code variant}), étagère de tonneaux au fond, auvent en laine
     * rayée à deux pentes qui déborde vers les clients avec un lambrequin de trappes, lanterne, caisses et tonneaux sur les côtés.
     * La place du vendeur (0, z = -1) reste libre pour un futur PNJ marchand.
     */
    public static void stall(Frame f, int variant) {
        Block[] awning = AWNINGS[Math.floorMod(variant, AWNINGS.length)];
        Block[] goods = GOODS[Math.floorMod(variant, GOODS.length)];
        // poteaux robustes
        for (int x : new int[] {-2, 2}) {
            for (int z : new int[] {0, -3}) {
                f.fill(x, 0, z, x, 2, z, SPRUCE_LOG);
            }
        }
        // comptoir épuré : tonneaux intégrés aux bouts, dalle haute au centre habillée d'une trappe
        f.barrel(-1, 0, 0, Direction.SOUTH);
        f.slab(0, 0, 0, SPRUCE_SLAB, true);
        f.trapdoor(0, 0, 1, DARK_OAK_TRAPDOOR, Direction.SOUTH, true, false);
        f.barrel(1, 0, 0, Direction.SOUTH);
        // côtés du comptoir (retours vers le fond)
        f.stairs(-2, 0, -1, SPRUCE_STAIRS, Direction.EAST, true);
        f.stairs(2, 0, -1, SPRUCE_STAIRS, Direction.WEST, true);
        // détails légers sur le comptoir : pots de fleurs, tapis, lanternes
        for (int x = -1; x <= 1; x++) {
            Block g = goods[x + 1];
            if (g == LANTERN) {
                f.lantern(x, 1, 0, false);
            } else {
                f.set(x, 1, 0, g);
            }
        }
        f.set(-2, 1, -1, POTTED_POPPY);
        // réserve au fond : tonneaux et étagère
        for (int x = -1; x <= 1; x++) {
            f.barrel(x, 0, -3, Direction.UP);
            f.slab(x, 1, -3, SPRUCE_SLAB, false);
        }
        f.set(1, 2, -3, POTTED_FERN);
        // auvent rayé (bandes dans la profondeur), deux pentes, débord vers les clients
        for (int x = -3; x <= 3; x++) {
            Block wool = awning[Math.floorMod(x, 2)];
            int y = Math.abs(x) >= 2 ? 3 : 4;
            for (int z = -3; z <= 1; z++) {
                f.set(x, y, z, wool);
            }
            if (Math.abs(x) == 2) {
                for (int z = -3; z <= 1; z++) {
                    f.set(x, 3, z, wool);
                }
            }
            if (Math.abs(x) <= 1) {
                for (int z = -3; z <= 1; z++) {
                    f.set(x, 3, z, AIR);
                }
            }
            f.trapdoor(x, Math.abs(x) >= 2 ? 2 : 3, 2, SPRUCE_TRAPDOOR, Direction.SOUTH, true, false);
        }
        f.fill(-2, 3, -3, 2, 3, -3, SPRUCE_PLANKS);   // pignon arrière
        f.lantern(0, 3, -1, true);
        // caisses et tonneaux sur les côtés
        f.barrel(3, 0, -2, Direction.UP);
        f.barrel(3, 1, -2, Direction.UP);
        f.barrel(3, 0, -1, Direction.UP);
        f.barrel(-3, 0, -2, Direction.NORTH);
        f.set(-3, 0, -1, POTTED_AZALEA_BUSH);
    }

    /** Petit arbre feuillu (chêne ou bouleau), feuillage permanent. */
    public static void tree(VillageBuilder b, BlockPos ground, boolean birch) {
        Frame f = new Frame(b, ground, Rotation.NONE);
        Block log = birch ? BIRCH_LOG : OAK_LOG;
        Block leaves = birch ? BIRCH_LEAVES : OAK_LEAVES;
        int h = 4 + b.random().nextInt(2);
        for (int y = 0; y < h; y++) {
            f.log(0, y, 0, log, Direction.Axis.Y);
        }
        for (int y = h - 2; y <= h + 1; y++) {
            int r = y >= h ? 1 : 2;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    boolean corner = Math.abs(x) == r && Math.abs(z) == r;
                    if ((x != 0 || z != 0 || y >= h) && (!corner || b.random().nextInt(3) == 0)) {
                        f.leaves(x, y, z, leaves);
                    }
                }
            }
        }
    }

    /** Palmier côtier : tronc légèrement penché, palmes en étoile qui retombent. */
    public static void palm(VillageBuilder b, BlockPos ground) {
        Frame f = new Frame(b, ground, Rotation.NONE);
        int[][] trunk = {{0, 0}, {0, 0}, {0, 0}, {1, 0}, {1, 0}, {1, 0}, {2, 0}};
        for (int y = 0; y < trunk.length; y++) {
            f.log(trunk[y][0], y, trunk[y][1], JUNGLE_LOG, Direction.Axis.Y);
        }
        int tx = 2, ty = trunk.length;
        f.leaves(tx, ty, 0, JUNGLE_LEAVES);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i <= 3; i++) {
                f.leaves(tx + d.getStepX() * i, ty - (i == 3 ? 1 : 0), d.getStepZ() * i, JUNGLE_LEAVES);
            }
        }
        for (int[] diag : new int[][] {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            f.leaves(tx + diag[0], ty, diag[1], JUNGLE_LEAVES);
            f.leaves(tx + diag[0] * 2, ty - 1, diag[1] * 2, JUNGLE_LEAVES);
        }
    }

    /**
     * Chemin sinueux de {@code from} à {@code to} (coordonnées monde, y = niveau du sol du village) : terre battue surtout,
     * un peu de gravier et de pavés, sur 3 blocs de large. Il remplace le bloc d'herbe du dessus du terrain.
     */
    public static void path(VillageBuilder b, BlockPos from, BlockPos to) {
        path(b, from, to, true);
    }

    /** Matière d'une case de chemin : terre battue surtout, gravier et quelques pavés. */
    public static BlockState pathBlock(RandomSource random) {
        float r = random.nextFloat();
        return r < 0.65F ? DIRT_PATH.defaultBlockState() : r < 0.85F ? GRAVEL.defaultBlockState() : COBBLESTONE.defaultBlockState();
    }

    /** Une construction (bâtiment, puits, étal) à moins d'un bloc de la colonne (x, z), au niveau du sol ? */
    public static boolean nearStructure(VillageBuilder b, int x, int y0, int z, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    if (b.isStructure(new BlockPos(x + dx, y0 + dy, z + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Chemin sinueux de 3 de large. {@code keepAway} : jamais collé à un mur, une plinthe ou une porte (une bande d'herbe d'un
     * bloc reste entre le chemin et la construction : deux blocs d'herbe).
     */
    public static void path(VillageBuilder b, BlockPos from, BlockPos to, boolean keepAway) {
        RandomSource random = b.random();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        double nx = -dz / Math.max(1.0, length);
        double nz = dx / Math.max(1.0, length);
        double phase = random.nextDouble() * Math.PI * 2;
        int y = from.getY() - 1;
        for (double t = 0; t <= length; t += 0.5) {
            double wobble = Math.sin(t / 4.0 + phase) * 1.3 * Math.sin(Math.PI * t / Math.max(1.0, length)); // sinueux, droit aux bouts
            double cx = from.getX() + dx * t / length + nx * wobble;
            double cz = from.getZ() + dz * t / length + nz * wobble;
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    int x = Mth.floor(cx) + ox;
                    int z = Mth.floor(cz) + oz;
                    if (b.isOccupied(x, z) && !b.get(new BlockPos(x, y, z)).is(GRASS_BLOCK)) {
                        continue; // ne pas abîmer une fondation
                    }
                    if (keepAway && nearStructure(b, x, y + 1, z, 2)) {
                        continue; // de l'air (de l'herbe) entre le chemin et les murs
                    }
                    float r = random.nextFloat();
                    BlockState material = r < 0.65F ? DIRT_PATH.defaultBlockState()
                            : r < 0.85F ? GRAVEL.defaultBlockState() : COBBLESTONE.defaultBlockState();
                    b.set(new BlockPos(x, y, z), material);
                }
            }
        }
    }

    /**
     * Ruelles du coeur du village : tout le sol libre entre les bâtiments devient une ruelle (terre battue, gravier, pavés), sauf
     * des plates-bandes fleuries le long des murs (jamais devant une porte).
     */
    public static void streets(VillageBuilder b, BlockPos center, int half) {
        RandomSource random = b.random();
        int y = center.getY() - 1;
        boolean[][] built = new boolean[2 * half + 3][2 * half + 3];
        for (int dx = -half - 1; dx <= half + 1; dx++) {
            for (int dz = -half - 1; dz <= half + 1; dz++) {
                built[dx + half + 1][dz + half + 1] = b.isOccupied(center.getX() + dx, center.getZ() + dz);
            }
        }
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if (built[dx + half + 1][dz + half + 1]) {
                    continue;
                }
                int x = center.getX() + dx, z = center.getZ() + dz;
                boolean alongWall = false;
                for (int ox = -1; ox <= 1 && !alongWall; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        if (built[dx + ox + half + 1][dz + oz + half + 1]) {
                            alongWall = true;
                            break;
                        }
                    }
                }
                BlockPos ground = new BlockPos(x, y, z);
                if (alongWall && !b.isKeptClear(x, z) && random.nextFloat() < 0.3F) {
                    // plate-bande : herbe + fleur ou petit buisson
                    BlockPos above = ground.above();
                    b.set(ground, GRASS_BLOCK.defaultBlockState());
                    if (random.nextInt(4) == 0) {
                        b.set(above, OAK_LEAVES.defaultBlockState().trySetValue(
                                net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT, true));
                    } else {
                        b.set(above, FLOWERS[random.nextInt(FLOWERS.length)].defaultBlockState());
                    }
                    continue;
                }
                float r = random.nextFloat();
                Block material = r < 0.55F ? DIRT_PATH : r < 0.75F ? GRAVEL : r < 0.92F ? COBBLESTONE : MOSSY_COBBLESTONE;
                b.set(ground, material.defaultBlockState());
            }
        }
    }

    /**
     * Verdure naturelle sur l'herbe libre : herbes, hautes herbes, fleurs variées, petits buissons. Jamais sur une construction,
     * un chemin ou la place (colonnes réservées).
     */
    public static void greenery(VillageBuilder b, BlockPos center, int radius) {
        RandomSource random = b.random();
        int y = center.getY();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                if (b.isOccupied(x, z) || b.isKeptClear(x, z)) {
                    continue;
                }
                BlockPos ground = new BlockPos(x, y - 1, z);
                BlockPos above = ground.above();
                if (!b.get(ground).is(GRASS_BLOCK) || !b.get(above).isAir()) {
                    continue;
                }
                float r = random.nextFloat();
                Frame f = new Frame(b, above, Rotation.NONE);
                if (r < 0.26F) {
                    b.setTerrain(above, SHORT_GRASS.defaultBlockState());
                } else if (r < 0.27F) {
                    if (b.get(above.above()).isAir()) {
                        f.tallPlant(0, 0, 0, TALL_GRASS);
                    }
                } else if (r < 0.33F) {
                    b.setTerrain(above, FLOWERS[random.nextInt(FLOWERS.length)].defaultBlockState());
                } else if (r < 0.355F) {
                    f.leaves(0, 0, 0, OAK_LEAVES);               // buisson
                    if (random.nextBoolean() && !b.isOccupied(x + 1, z) && !b.isKeptClear(x + 1, z) && b.get(above.offset(1, 0, 0)).isAir()) {
                        f.leaves(1, 0, 0, OAK_LEAVES);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ mobilier urbain et jardins (repère local d'un Frame)

    public static net.minecraft.world.level.block.Block randomFlower(RandomSource random) {
        return FLOWERS[random.nextInt(FLOWERS.length)];
    }

    /** Muret de pierre (pavés moussus et pavés) de (x1, z1) à (x2, z2), en ligne droite. */
    public static void lowWall(Frame f, int x1, int z1, int x2, int z2) {
        int n = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        for (int i = 0; i <= n; i++) {
            int x = x1 + (n == 0 ? 0 : (x2 - x1) * i / n);
            int z = z1 + (n == 0 ? 0 : (z2 - z1) * i / n);
            f.set(x, 0, z, (Block) (f.builder().random().nextInt(3) == 0 ? MOSSY_COBBLESTONE_WALL : COBBLESTONE_WALL));
        }
    }

    /** Haie taillée (feuillage permanent, 1 bloc de haut) de (x1, z1) à (x2, z2). */
    public static void hedge(Frame f, int x1, int z1, int x2, int z2) {
        int n = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        for (int i = 0; i <= n; i++) {
            f.leaves(x1 + (n == 0 ? 0 : (x2 - x1) * i / n), 0, z1 + (n == 0 ? 0 : (z2 - z1) * i / n), OAK_LEAVES);
        }
    }

    /** Banc en bois (3 places), dossier vers -Z, accoudoirs en trappes. */
    public static void bench(Frame f) {
        for (int x = -1; x <= 1; x++) {
            f.stairs(x, 0, 0, SPRUCE_STAIRS, net.minecraft.core.Direction.NORTH, false);
        }
        f.trapdoor(-2, 0, 0, SPRUCE_TRAPDOOR, net.minecraft.core.Direction.WEST, true, false);
        f.trapdoor(2, 0, 0, SPRUCE_TRAPDOOR, net.minecraft.core.Direction.EAST, true, false);
    }

    /** Charrette à bras : plateau en dalles, roues (trappes), brancards (clôtures), chargement de foin et de tonneaux. */
    public static void cart(Frame f) {
        for (int z = -1; z <= 1; z++) {
            f.slab(0, 0, z, SPRUCE_SLAB, true);
            f.slab(1, 0, z, SPRUCE_SLAB, true);
        }
        f.trapdoor(-1, 0, 0, DARK_OAK_TRAPDOOR, net.minecraft.core.Direction.WEST, true, false);
        f.trapdoor(2, 0, 0, DARK_OAK_TRAPDOOR, net.minecraft.core.Direction.EAST, true, false);
        f.set(0, 0, 2, SPRUCE_FENCE);
        f.set(1, 0, 2, SPRUCE_FENCE);
        f.set(0, 1, -1, HAY_BLOCK);
        f.barrel(1, 1, 0, net.minecraft.core.Direction.UP);
        f.set(0, 1, 0, HAY_BLOCK);
    }

    /** Pile de caisses et de tonneaux (marchandises). */
    public static void crates(Frame f) {
        f.set(0, 0, 0, SPRUCE_PLANKS);
        f.trapdoor(0, 1, 0, SPRUCE_TRAPDOOR, net.minecraft.core.Direction.NORTH, false, false);
        f.barrel(1, 0, 0, net.minecraft.core.Direction.UP);
        f.barrel(1, 1, 0, net.minecraft.core.Direction.UP);
        f.barrel(0, 0, 1, net.minecraft.core.Direction.SOUTH);
        f.set(1, 0, 1, HAY_BLOCK);
    }

    /**
     * Jardin fleuri clos (w x d, coin en (0, 0)) : muret sur le pourtour avec une entrée au milieu du côté +Z, massifs de fleurs,
     * rosiers, buissons de baies et un petit arbre fruitier au centre.
     */
    public static void garden(Frame f, int w, int d) {
        RandomSource random = f.builder().random();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (edge) {
                    if (!(z == d - 1 && x == w / 2)) {
                        f.set(x, 0, z, (Block) ((x + z) % 3 == 0 ? MOSSY_COBBLESTONE_WALL : COBBLESTONE_WALL));
                    }
                    continue;
                }
                float r = random.nextFloat();
                if (r < 0.45F) {
                    f.set(x, 0, z, FLOWERS[random.nextInt(FLOWERS.length)]);
                } else if (r < 0.6F && z < d - 2) {
                    f.tallPlant(x, 0, z, ROSE_BUSH);
                } else if (r < 0.7F) {
                    f.set(x, 0, z, SWEET_BERRY_BUSH.defaultBlockState()
                            .trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3, 2));
                }
            }
        }
    }
}
