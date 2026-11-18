package nolan.bloxpiece.village;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

import static nolan.bloxpiece.village.VillageBlocks.*;

/**
 * Plan du village de départ (style Fuchsia) : 11 bâtiments serrés autour d'une petite place, séparés par des ruelles de 2 à 4
 * blocs. TOUTES les positions sont RELATIVES au centre de la place (x, z ; y = 0 = niveau du sol).
 * <pre>
 *                        N (-z)
 *        [Maison 2]   [  TAVERNE  ]   [Maison 1]
 *   [Entrepôt]        [  place +   ]        [Forge]
 *                     [ puits/étals]
 *   [Mairie]     [Épicerie] | [Artisan]   [Maison 5]
 *                [Maison 4] | [Maison 3]
 *                        S (+z)
 * </pre>
 * Pour changer un bâtiment : sa recette (dimensions, matériaux, options : voir Building) et son intérieur (Interiors). Pour le
 * déplacer : son origine ; la rotation tourne sa façade vers la rue (NONE : sud, CLOCKWISE_90 : ouest, COUNTERCLOCKWISE_90 :
 * est, CLOCKWISE_180 : nord). Les places réservées aux futurs PNJ sont en coordonnées LOCALES du bâtiment (écrites en
 * coordonnées monde dans la console à la création du monde).
 */
public final class VillageLayout {

    /** Rayon aplani autour du centre (blocs), puis raccord en pente douce jusqu'à TERRAIN_BLEND. */
    public static final int TERRAIN_FLAT = 52;
    public static final int TERRAIN_BLEND = 62;
    /** Rayon moyen de la place pavée, ronde (bord légèrement ondulé, naturel). */
    public static final int PLAZA_RADIUS = 11;
    /** Demi-côté du carré qui contient la place (pour les parcours de cases). */
    public static final int PLAZA_HALF = PLAZA_RADIUS + 2;

    /** Rayon de la place dans la direction (dx, dz) : un cercle doucement ondulé (toujours le même pour un monde donné). */
    public static double plazaRadius(int dx, int dz) {
        double a = Math.atan2(dz, dx);
        return PLAZA_RADIUS + 0.7 * Math.sin(3 * a) + 0.4 * Math.sin(5 * a + 1.0);
    }

    /** La case (dx, dz) (relative au centre) est-elle sur la place ? */
    public static boolean onPlaza(int dx, int dz) {
        return dx * dx + dz * dz <= plazaRadius(dx, dz) * plazaRadius(dx, dz);
    }

    /** Écartement du plan : toutes les positions (bâtiments, allées, mobilier, arbres) sont multipliées par ce facteur. */
    public static final double SPREAD = 1.2;

    private static BlockPos at(int x, int z) {
        return new BlockPos((int) Math.round(x * SPREAD), 0, (int) Math.round(z * SPREAD));
    }

    /** Position déjà en coordonnées finales (non écartée), pour un élément recalé à la main. */
    private static BlockPos w(int x, int z) {
        return new BlockPos(x, 0, z);
    }

    /** Point de spawn des joueurs (si le village est au spawn) : sur la place, devant la taverne. */
    public static final BlockPos PLAZA_SPAWN = new BlockPos(0, 0, -7);

    /** Un bâtiment du plan : nom, origine, rotation, recette, places de PNJ (repère local). */
    public record Entry(String name, BlockPos origin, Rotation rotation, Supplier<Building> recipe, List<BlockPos> npcSpots) {
    }

    public static final List<Entry> BUILDINGS = List.of(
            new Entry("Taverne", at(0, -12), Rotation.NONE, VillageLayout::tavern,
                    List.of(new BlockPos(0, 1, -8), new BlockPos(-2, 1, -5), new BlockPos(2, 1, -5), new BlockPos(0, 1, -2))),
            new Entry("Forge", at(13, 0), Rotation.CLOCKWISE_90, VillageLayout::forge,
                    List.of(new BlockPos(-1, 1, -4))),
            new Entry("Entrepôt", at(-13, 0), Rotation.COUNTERCLOCKWISE_90, VillageLayout::warehouse,
                    List.of(new BlockPos(0, 1, -4))),
            new Entry("Épicerie", at(-6, 13), Rotation.CLOCKWISE_180, VillageLayout::grocer,
                    List.of(new BlockPos(0, 1, -1))),
            new Entry("Échoppe de l'artisan", at(6, 13), Rotation.CLOCKWISE_180, VillageLayout::craftsman,
                    List.of(new BlockPos(0, 1, -1))),
            new Entry("Maison 1 (encorbellement)", at(17, -14), Rotation.NONE, VillageLayout::home1,
                    List.of(new BlockPos(0, 1, -3))),
            new Entry("Maison 2 (lucarne)", at(-17, -14), Rotation.NONE, VillageLayout::home2,
                    List.of(new BlockPos(0, 1, -3))),
            new Entry("Maison 3 (balcon)", at(8, 28), Rotation.CLOCKWISE_180, VillageLayout::home3,
                    List.of(new BlockPos(0, 1, -4))),
            new Entry("Maison 4 (pignon sur rue)", at(-8, 28), Rotation.CLOCKWISE_180, VillageLayout::home4,
                    List.of(new BlockPos(0, 1, -4))),
            new Entry("Maison 5 (cheminée)", at(21, 15), Rotation.CLOCKWISE_90, VillageLayout::home5,
                    List.of(new BlockPos(0, 1, -3))),
            new Entry("Mairie", at(-21, 16), Rotation.COUNTERCLOCKWISE_90, VillageLayout::mayor,
                    List.of(new BlockPos(0, 1, -5)))
    );

    /** Allées en terre battue / gravier / pavés (début -> fin) : de la place à chaque porte, et entre les quartiers. */
    public static final BlockPos[][] LANES = {
            {at(7, 0), at(12, 0)},          // forge
            {at(-7, 0), at(-12, 0)},        // entrepôt
            {at(-3, 7), at(-6, 12)},        // épicerie
            {at(3, 7), at(6, 12)},          // artisan
            {at(7, -6), at(17, -12)},       // maison 1
            {at(-7, -6), at(-17, -12)},     // maison 2
            {at(0, 7), at(0, 23)},          // ruelle entre les échoppes
            {at(-18, 23), at(18, 23)},      // rue du sud
            {at(8, 23), at(8, 26)},         // maison 3
            {at(-8, 23), at(-8, 26)},       // maison 4
            {at(7, 6), at(20, 15)},         // maison 5
            {at(-7, 6), at(-20, 16)},       // mairie
            {at(18, 23), at(21, 19)},       // rue du sud -> maison 5
            {at(-18, 23), at(-21, 20)},     // rue du sud -> mairie
            {at(12, -4), at(17, -10)}       // forge -> maison 1
    };

    /** Arbres (chênes, bouleaux) et palmiers côtiers, entre et autour des maisons. */
    public static final BlockPos[] OAKS = {at(-30, -24), at(30, -24), at(33, 35),
            at(0, -30), at(-31, 2), at(16, -28), at(-26, 7),
            at(30, -2), at(16, 37), at(-16, 38), at(-36, -12), at(36, 18)};
    public static final BlockPos[] BIRCHES = {at(-30, 33), at(-11, -27), at(31, 10),
            at(20, 36), w(-25, 43)};
    public static final BlockPos[] PALMS = {at(34, -6), at(-34, 14), at(26, 26)};

    /** Mobilier urbain et jardins : type, position, rotation. */
    public enum PropKind { GARDEN, BENCH, CART, CRATES, WELL, HEDGE_X, HEDGE_Z, WALL_X, WALL_Z }

    public record Prop(PropKind kind, BlockPos pos, Rotation rotation, int size) {
    }

    public static final List<Prop> PROPS = List.of(
            // jardins fleuris clos entre les maisons
            new Prop(PropKind.GARDEN, at(8, -31), Rotation.NONE, 6),
            new Prop(PropKind.GARDEN, at(-21, -30), Rotation.NONE, 6),
            new Prop(PropKind.GARDEN, at(-32, 24), Rotation.NONE, 5),
            new Prop(PropKind.GARDEN, at(24, 28), Rotation.NONE, 5),
            new Prop(PropKind.GARDEN, at(-2, 37), Rotation.NONE, 5),
            // bancs face à la place et le long de la rue du sud
            new Prop(PropKind.BENCH, w(-12, -16), Rotation.NONE, 0),
            new Prop(PropKind.BENCH, w(12, -16), Rotation.NONE, 0),   // hors de l'allée devant la taverne
            new Prop(PropKind.BENCH, w(-13, 16), Rotation.CLOCKWISE_90, 0),
            new Prop(PropKind.BENCH, at(-27, 1), Rotation.CLOCKWISE_90, 0),
            // charrettes, caisses et tonneaux
            new Prop(PropKind.CART, w(20, 8), Rotation.NONE, 0),
            new Prop(PropKind.CRATES, w(-13, 4), Rotation.NONE, 0),
            new Prop(PropKind.CRATES, w(13, 17), Rotation.NONE, 0),
            new Prop(PropKind.CRATES, at(-24, -9), Rotation.NONE, 0),
            new Prop(PropKind.CRATES, at(23, -8), Rotation.NONE, 0),
            new Prop(PropKind.CRATES, at(-13, 17), Rotation.NONE, 0),
            new Prop(PropKind.CRATES, at(5, -26), Rotation.NONE, 0),
            new Prop(PropKind.CRATES, at(26, 22), Rotation.NONE, 0),
            new Prop(PropKind.BENCH, at(24, -3), Rotation.COUNTERCLOCKWISE_90, 0),
            new Prop(PropKind.BENCH, at(-30, 9), Rotation.CLOCKWISE_90, 0),
            // murets et haies le long des allées
            new Prop(PropKind.WALL_X, at(-6, -26), Rotation.NONE, 12),
            new Prop(PropKind.WALL_Z, at(-26, -12), Rotation.NONE, 10),
            new Prop(PropKind.WALL_Z, at(27, -12), Rotation.NONE, 10),
            new Prop(PropKind.HEDGE_X, at(-17, 26), Rotation.NONE, 5),
            new Prop(PropKind.HEDGE_X, at(13, 26), Rotation.NONE, 5),
            new Prop(PropKind.HEDGE_Z, at(-25, 22), Rotation.NONE, 8),
            new Prop(PropKind.HEDGE_Z, at(26, 6), Rotation.NONE, 6)
    );

    private VillageLayout() {
    }

    // ------------------------------------------------------------------ recettes des bâtiments

    static Building tavern() {
        Building b = new Building();
        b.halfWidth = 7;
        b.depth = 11;
        b.floors = 2;
        b.groundWall = OAK_PLANKS;
        b.upperWall = WHITE_TERRACOTTA;
        b.post = DARK_OAK_LOG;
        b.beam = STRIPPED_DARK_OAK_LOG;
        b.roofStairs = BRICK_STAIRS;
        b.roofSlab = BRICK_SLAB;
        b.shutter = DARK_OAK_TRAPDOOR;
        b.door = DARK_OAK_DOOR;
        b.doubleDoor = true;
        b.approach = 5; // devant les marches du porche
        b.dormer = true;
        b.chimneyX = -5;
        b.planters = false;
        b.interior = f -> {
            Interiors.tavern(f, b);
            Interiors.tavernPorch(f, b);
        };
        return b;
    }

    static Building forge() {
        Building b = new Building();
        b.halfWidth = 4;
        b.depth = 7;
        b.foundation = COBBLESTONE;
        b.groundWall = COBBLESTONE;
        b.upperWall = COBBLESTONE;
        b.post = SPRUCE_LOG;
        b.beam = STRIPPED_SPRUCE_LOG;
        b.roofStairs = DARK_OAK_STAIRS;
        b.roofSlab = DARK_OAK_SLAB;
        b.shutter = DARK_OAK_TRAPDOOR;
        b.chimneyX = -3;
        b.planters = false;
        b.interior = f -> Interiors.forge(f, b);
        return b;
    }

    static Building warehouse() {
        Building b = new Building();
        b.halfWidth = 5;
        b.depth = 9;
        b.groundWall = SPRUCE_PLANKS;
        b.upperWall = SPRUCE_PLANKS;
        b.post = OAK_LOG;
        b.beam = STRIPPED_OAK_LOG;
        b.roofStairs = OAK_STAIRS;
        b.roofSlab = OAK_SLAB;
        b.shutter = OAK_TRAPDOOR;
        b.door = SPRUCE_DOOR;
        b.gableToStreet = true;
        b.planters = false;
        b.interior = f -> Interiors.warehouse(f, b);
        return b;
    }

    static Building grocer() {
        Building b = new Building();
        b.halfWidth = 3;
        b.depth = 7;
        b.floors = 2;
        b.groundWall = BIRCH_PLANKS;
        b.upperWall = WHITE_TERRACOTTA;
        b.post = DARK_OAK_LOG;
        b.beam = STRIPPED_DARK_OAK_LOG;
        b.roofStairs = BRICK_STAIRS;
        b.roofSlab = BRICK_SLAB;
        b.shutter = BIRCH_TRAPDOOR;
        b.door = BIRCH_DOOR;
        b.dormer = true;
        b.interior = f -> Interiors.grocer(f, b);
        return b;
    }

    static Building craftsman() {
        Building b = new Building();
        b.halfWidth = 3;
        b.depth = 7;
        b.floors = 2;
        b.gableToStreet = true;
        b.groundWall = SPRUCE_PLANKS;
        b.upperWall = TERRACOTTA;
        b.post = SPRUCE_LOG;
        b.beam = STRIPPED_SPRUCE_LOG;
        b.roofStairs = DARK_OAK_STAIRS;
        b.roofSlab = DARK_OAK_SLAB;
        b.interior = f -> Interiors.craftsman(f, b);
        return b;
    }

    static Building home1() {
        Building b = new Building();
        b.halfWidth = 3;
        b.depth = 7;
        b.floors = 2;
        b.jetty = true;
        b.foundation = COBBLESTONE;
        b.groundWall = COBBLESTONE;
        b.upperWall = WHITE_TERRACOTTA;
        b.chimneyX = 2;
        b.dormer = true;
        b.interior = f -> Interiors.home(f, b, BLUE_BED, LIGHT_BLUE_CARPET);
        return b;
    }

    static Building home2() {
        Building b = new Building();
        b.halfWidth = 3;
        b.depth = 7;
        b.dormer = true;
        b.groundWall = OAK_PLANKS;
        b.upperWall = OAK_PLANKS;
        b.roofStairs = BRICK_STAIRS;
        b.roofSlab = BRICK_SLAB;
        b.shutter = OAK_TRAPDOOR;
        b.door = OAK_DOOR;
        b.porch = true;
        b.chimneyX = -2;
        b.interior = f -> Interiors.home(f, b, YELLOW_BED, YELLOW_CARPET);
        return b;
    }

    static Building home3() {
        Building b = new Building();
        b.halfWidth = 4;
        b.depth = 8;
        b.floors = 2;
        b.balcony = true;
        b.foundation = STONE_BRICKS;
        b.groundWall = MOSSY_COBBLESTONE;
        b.upperWall = BIRCH_PLANKS;
        b.post = OAK_LOG;
        b.beam = STRIPPED_OAK_LOG;
        b.door = BIRCH_DOOR;
        b.shutter = BIRCH_TRAPDOOR;
        b.dormer = true;
        b.chimneyX = -3;
        b.interior = f -> Interiors.home(f, b, GREEN_BED, GREEN_CARPET);
        return b;
    }

    static Building home4() {
        Building b = new Building();
        b.halfWidth = 3;
        b.depth = 8;
        b.floors = 2;
        b.jetty = true;
        b.gableToStreet = true;
        b.groundWall = SPRUCE_PLANKS;
        b.upperWall = WHITE_TERRACOTTA;
        b.post = DARK_OAK_LOG;
        b.beam = STRIPPED_DARK_OAK_LOG;
        b.roofStairs = DARK_OAK_STAIRS;
        b.roofSlab = DARK_OAK_SLAB;
        b.interior = f -> Interiors.home(f, b, CYAN_BED, CYAN_CARPET);
        return b;
    }

    static Building home5() {
        Building b = new Building();
        b.halfWidth = 4;
        b.depth = 7;
        b.gableToStreet = true;
        b.groundWall = BIRCH_PLANKS;
        b.upperWall = BIRCH_PLANKS;
        b.post = SPRUCE_LOG;
        b.roofStairs = BRICK_STAIRS;
        b.roofSlab = BRICK_SLAB;
        b.chimneyX = 3;
        b.porch = true;
        b.interior = f -> Interiors.home(f, b, BROWN_BED, BROWN_CARPET);
        return b;
    }

    static Building mayor() {
        Building b = new Building();
        b.halfWidth = 4;
        b.depth = 9;
        b.floors = 2;
        b.balcony = true;
        b.dormer = true;
        b.foundation = STONE_BRICKS;
        b.groundWall = STONE_BRICKS;
        b.upperWall = WHITE_TERRACOTTA;
        b.post = DARK_OAK_LOG;
        b.beam = STRIPPED_DARK_OAK_LOG;
        b.roofStairs = BRICK_STAIRS;
        b.roofSlab = BRICK_SLAB;
        b.door = DARK_OAK_DOOR;
        b.shutter = DARK_OAK_TRAPDOOR;
        b.chimneyX = -3;
        b.interior = f -> Interiors.mayor(f, b);
        return b;
    }
}
