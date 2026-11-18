package nolan.bloxpiece.village;

import java.util.function.Consumer;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

import static nolan.bloxpiece.village.VillageBlocks.*;

/**
 * Bâtiment à colombages, entièrement paramétrable (repère local, voir Frame : façade en z = 0, porte au centre en x = 0, le
 * bâtiment s'étend vers -Z). Chaque bâtiment du village est une recette différente (dimensions, étages, matériaux, toit,
 * options) + un intérieur sur mesure (voir Interiors).
 * <p>
 * Détails dessinés : soubassement de pierre, poteaux d'angle et intermédiaires en rondins, poutres horizontales à chaque niveau,
 * remplissage plus clair entre les poutres (torchis / planches), fenêtres avec volets ouverts et appuis, étage en encorbellement
 * sur consoles (jetty), balcon, lucarne, cheminée, toit à forte pente avec débord, rive et faîtage, marche et lanternes à
 * l'entrée, jardinières.
 */
public final class Building {

    public static final int STOREY = 4; // plancher en y = 0, murs y 1..3, plancher suivant en y = 4

    /** Demi-largeur (largeur = 2 x halfWidth + 1) et profondeur. */
    public int halfWidth = 3;
    public int depth = 7;
    public int floors = 1;

    public Block foundation = STONE_BRICKS;
    public Block groundWall = OAK_PLANKS;
    public Block upperWall = WHITE_TERRACOTTA;
    public Block post = SPRUCE_LOG;
    public Block beam = STRIPPED_SPRUCE_LOG;
    public Block floor = SPRUCE_PLANKS;
    public Block roofStairs = SPRUCE_STAIRS;
    public Block roofSlab = SPRUCE_SLAB;
    public Block shutter = SPRUCE_TRAPDOOR;
    public Block door = SPRUCE_DOOR;
    public Block chimneyBlock = STONE_BRICKS;

    /** Étage avancé d'un bloc sur la façade, porté par des consoles (seulement avec 2 étages). */
    public boolean jetty;
    /** Balcon en bois devant la porte-fenêtre de l'étage (seulement avec 2 étages, sans jetty). */
    public boolean balcony;
    /** Lucarne sur le pan avant du toit. */
    public boolean dormer;
    /** Faîte perpendiculaire à la façade (pignon sur rue) au lieu de parallèle. */
    public boolean gableToStreet;
    /** Cheminée (x local), ou Integer.MIN_VALUE pour aucune. */
    public int chimneyX = Integer.MIN_VALUE;
    /** Devanture d'échoppe au rez-de-chaussée : comptoir ouvert sur la rue et petit auvent. */
    public boolean shopFront;
    /** Porte double (grands bâtiments). */
    public boolean doubleDoor;
    /** Distance (devant la façade) où l'allée rejoint l'entrée : 2 = juste après la marche ; plus loin s'il y a un porche. */
    public int approach = 2;
    /** Porche couvert devant la porte (auvent sur deux poteaux), pour les maisons de plain-pied. */
    public boolean porch;
    /** Jardinières sous les fenêtres de façade. */
    public boolean planters = true;
    /** Intérieur (appelé après le gros oeuvre). */
    public Consumer<Frame> interior = f -> { };

    public int backZ() {
        return -(this.depth - 1);
    }

    public int topY() {
        return this.floors * STOREY; // niveau des poutres sous le toit
    }

    public void build(Frame f) {
        int hw = this.halfWidth;
        int zb = backZ();
        int top = topY();
        boolean jettyOn = this.jetty && this.floors >= 2;
        boolean balconyOn = this.balcony && this.floors >= 2 && !jettyOn;
        net.minecraft.util.RandomSource random = f.builder().random();
        f.texture = block -> texture(block, random);

        // ---- soubassement : fondation (y = -1), rang de pierre sous les murs (y = 0), plancher, plinthe en saillie
        f.foundation(-hw, zb, hw, 0, this.foundation);
        f.air(-hw + 1, 1, zb + 1, hw - 1, top + 12, -1);
        f.walls(-hw, zb, hw, 0, 0, 0, this.foundation);
        f.fill(-hw + 1, 0, zb + 1, hw - 1, 0, -1, this.floor);
        Block plinth = this.foundation == COBBLESTONE ? COBBLESTONE_STAIRS : STONE_BRICK_STAIRS;
        for (int x = -hw - 1; x <= hw + 1; x++) {
            f.stairs(x, 0, 1, plinth, Direction.NORTH, false);       // devant (sert aussi de marche devant la porte)
            f.stairs(x, 0, zb - 1, plinth, Direction.SOUTH, false);  // derrière
        }
        for (int z = zb; z <= 0; z++) {
            f.stairs(-hw - 1, 0, z, plinth, Direction.EAST, false);
            f.stairs(hw + 1, 0, z, plinth, Direction.WEST, false);
        }

        for (int s = 0; s < this.floors; s++) {
            int y0 = s * STOREY;
            int front = s > 0 && jettyOn ? 1 : 0;
            Block fill = s == 0 ? this.groundWall : this.upperWall;
            // remplissage des murs
            f.walls(-hw, zb, hw, front, y0 + 1, y0 + 3, fill);
            if (front == 1) {
                // étage en encorbellement : plancher et poutre avancés, consoles sous l'avancée
                for (int x = -hw; x <= hw; x++) {
                    f.log(x, y0, 1, this.beam, Direction.Axis.X);
                }
                f.fill(-hw + 1, y0, 0, hw - 1, y0, 0, this.floor);
                for (int x = -hw; x <= hw; x += 2) {
                    f.stairs(x, y0 - 1, 1, this.roofStairs, Direction.NORTH, true);
                }
                f.air(-hw + 1, y0 + 1, 0, hw - 1, y0 + 3, 0);
            }
            // poteaux d'angle et intermédiaires
            for (int y = y0 + (s == 0 ? 0 : 1); y <= y0 + 3; y++) {
                for (int x : new int[] {-hw, hw}) {
                    f.log(x, y, zb, this.post, Direction.Axis.Y);
                    f.log(x, y, front, this.post, Direction.Axis.Y);
                }
                if (hw >= 4) {
                    for (int x : new int[] {-(hw - 2), hw - 2}) {
                        f.log(x, y, front, this.post, Direction.Axis.Y);
                        f.log(x, y, zb, this.post, Direction.Axis.Y);
                    }
                }
                if (this.depth >= 8) {
                    int mid = zb / 2;
                    f.log(-hw, y, mid, this.post, Direction.Axis.Y);
                    f.log(hw, y, mid, this.post, Direction.Axis.Y);
                }
            }
            // poutre horizontale en haut de l'étage (ceinture / plancher du suivant)
            int beamY = y0 + STOREY;
            boolean lastStorey = s + 1 == this.floors;
            for (int x = -hw; x <= hw; x++) {
                f.log(x, beamY, zb, this.beam, Direction.Axis.X);
                f.log(x, beamY, front, this.beam, Direction.Axis.X);
            }
            for (int z = zb; z <= front; z++) {
                f.log(-hw, beamY, z, this.beam, Direction.Axis.Z);
                f.log(hw, beamY, z, this.beam, Direction.Axis.Z);
            }
            // abouts de poutres qui dépassent d'un bloc aux angles (et solives en façade entre deux étages)
            if (!(lastStorey && this.gableToStreet)) {
                for (int x : new int[] {-hw - 1, hw + 1}) {
                    f.log(x, beamY, zb, this.beam, Direction.Axis.X);
                    f.log(x, beamY, front, this.beam, Direction.Axis.X);
                }
            }
            if (!lastStorey && !jettyOn) {
                for (int x : postColumns(hw)) {
                    f.log(x, beamY, front + 1, this.beam, Direction.Axis.Z);
                    f.log(x, beamY, zb - 1, this.beam, Direction.Axis.Z);
                }
            }
            if (!lastStorey) {
                f.fill(-hw + 1, beamY, zb + 1, hw - 1, beamY, front - 1 + (front == 1 ? 1 : 0), this.floor);
            }
            // fenêtres (encadrées, avec volets) : façade, arrière, côtés
            int wy = y0 + 2;
            for (int x : windowColumns(hw)) {
                if (!(s == 0 && this.shopFront)) {
                    window(f, x, wy, front, Direction.SOUTH, this.planters, random);
                }
                boolean ladderColumn = s == 0 && this.floors >= 2 && x == hw - 1; // l'échelle intérieure s'appuie ici
                if (x != this.chimneyX && !ladderColumn) {
                    window(f, x, wy, zb, Direction.NORTH, false, random);
                }
            }
            for (int z = zb + 2; z <= front - 2; z += 3) {
                if (this.depth >= 8 && z == zb / 2) {
                    continue; // poteau intermédiaire
                }
                window(f, -hw, wy, z, Direction.WEST, false, random);
                window(f, hw, wy, z, Direction.EAST, false, random);
            }
        }

        // plafond plat sous le toit (on ne voit plus la charpente de l'intérieur ; l'extérieur du toit ne change pas)
        int topFront0 = jettyOn ? 1 : 0;
        f.fill(-hw + 1, top, zb + 1, hw - 1, top, topFront0 - 1, this.floor);

        // porte(s), sur un seuil de plancher
        if (this.doubleDoor) {
            f.set(-1, 0, 0, this.floor);
            f.set(0, 0, 0, this.floor);
            // vue de l'intérieur (la porte "regarde" vers +z), la gauche est à +x : charnières sur les bords extérieurs,
            // les deux battants se rejoignent au milieu
            f.door(-1, 1, 0, this.door, Direction.SOUTH, true);
            f.door(0, 1, 0, this.door, Direction.SOUTH, false);
        } else if (this.shopFront) {
            f.set(0, 0, 0, this.floor);
            f.air(0, 1, 0, 0, 2, 0);   // devanture ouverte : simple passage entre les deux comptoirs, sans porte
        } else {
            f.set(0, 0, 0, this.floor);
            f.door(0, 1, 0, this.door, Direction.SOUTH, false);
        }
        // devanture d'échoppe : comptoir ouvert sur la rue, petit auvent
        if (this.shopFront) {
            for (int x = -hw + 1; x <= hw - 1; x++) {
                if (x == 0) {
                    continue;
                }
                f.log(x, 1, 0, STRIPPED_OAK_LOG, Direction.Axis.X);
                f.air(x, 2, 0, x, 3, 0);
                f.slab(x, 2, 0, OAK_SLAB, false);
            }
            if (this.floors >= 2 && !jettyOn) {
                for (int x = -hw; x <= hw; x++) {
                    f.stairs(x, 4, 1, this.roofStairs, Direction.NORTH, false);
                }
            }
        }
        // balcon devant la porte-fenêtre de l'étage (plancher plein : la lanterne de l'entrée y est suspendue)
        if (balconyOn) {
            f.door(0, 5, 0, this.door, Direction.SOUTH, true);
            for (int x = -2; x <= 2; x++) {
                f.set(x, 4, 1, this.floor);
                f.set(x, 5, 2, SPRUCE_FENCE);
            }
            f.set(-2, 5, 1, SPRUCE_FENCE);
            f.set(2, 5, 1, SPRUCE_FENCE);
            f.stairs(-2, 3, 1, this.roofStairs, Direction.NORTH, true);
            f.stairs(2, 3, 1, this.roofStairs, Direction.NORTH, true);
            f.set(-1, 6, 2, POTTED_RED_TULIP);
            f.set(1, 6, 2, POTTED_AZALEA_BUSH);
        }

        // ---- toit (débord d'un bloc), corniche d'escaliers renversés sous les avant-toits
        int frontEdge = (jettyOn ? 1 : 0) + 1;
        int roofY = top + 1;
        int topFront = jettyOn ? 1 : 0;
        if (this.gableToStreet) {
            f.gableRoofZ(-hw - 1, hw + 1, zb - 1, frontEdge, roofY, this.roofStairs, this.roofSlab, this.upperWall);
            for (int z = zb; z <= topFront; z++) {
                f.stairs(-hw - 1, top, z, this.roofStairs, Direction.EAST, true);
                f.stairs(hw + 1, top, z, this.roofStairs, Direction.WEST, true);
            }
            // poutres apparentes sur le pignon de façade
            int steps = (2 * hw + 3) / 2;
            for (int i = 0; i < steps; i++) {
                f.log(-hw + i, roofY + i, topFront, this.beam, Direction.Axis.Y);
                f.log(hw - i, roofY + i, topFront, this.beam, Direction.Axis.Y);
            }
            window(f, 0, roofY + 1, topFront, Direction.SOUTH, false, random);
        } else {
            f.gableRoofX(-hw - 1, hw + 1, zb - 1, frontEdge, roofY, this.roofStairs, this.roofSlab, this.upperWall);
            for (int x = -hw - 1; x <= hw + 1; x++) {
                f.stairs(x, top, frontEdge, this.roofStairs, Direction.NORTH, true);
                f.stairs(x, top, zb - 1, this.roofStairs, Direction.SOUTH, true);
            }
            if (this.dormer) {
                int dz = frontEdge - 1;
                f.set(-1, roofY + 1, dz, this.upperWall);
                f.set(1, roofY + 1, dz, this.upperWall);
                f.set(0, roofY + 1, dz, GLASS_PANE);
                f.fill(-1, roofY + 2, dz, 1, roofY + 2, dz, this.beam);
                f.air(0, roofY + 1, dz - 1, 0, roofY + 2, dz - 1);
                f.fill(-1, roofY + 1, dz - 1, -1, roofY + 2, dz - 1, this.upperWall);
                f.fill(1, roofY + 1, dz - 1, 1, roofY + 2, dz - 1, this.upperWall);
                f.stairs(-1, roofY + 3, dz, this.roofStairs, Direction.EAST, false);
                f.stairs(1, roofY + 3, dz, this.roofStairs, Direction.WEST, false);
                f.slab(0, roofY + 3, dz, this.roofSlab, false);
                f.fill(-1, roofY + 3, dz - 1, 1, roofY + 3, dz - 1, this.roofSlab);
            }
        }
        // lanterne près de l'entrée : sous la corniche, l'avancée, le balcon, ou sous une petite potence
        if (!this.shopFront && !this.porch) {
            boolean covered = jettyOn || balconyOn || (this.floors == 1 && !this.gableToStreet);
            if (!covered) {
                f.set(1, 4, 1, SPRUCE_FENCE); // potence de la lanterne
            }
            f.lantern(1, 3, 1, true);
        }
        // porche couvert : auvent en dalles sur deux poteaux, lanterne au centre
        if (this.porch) {
            for (int x = -2; x <= 2; x++) {
                for (int z = 1; z <= 2; z++) {
                    if (z == 1 && this.floors == 1 && !this.gableToStreet) {
                        continue; // la corniche du toit fait déjà le bord de l'auvent
                    }
                    f.slab(x, 4, z, this.roofSlab, false);
                }
                f.stairs(x, 4, 3, this.roofStairs, Direction.NORTH, false);
            }
            for (int y = 0; y <= 3; y++) {   // les poteaux reposent sur le sol (y = 0 : niveau du terrain)
                f.set(-2, y, 2, SPRUCE_FENCE);
                f.set(2, y, 2, SPRUCE_FENCE);
            }
            f.lantern(0, 3, 2, true);
        }
        // cheminée de pierre : souche dans le mur du fond + coffre saillant à l'extérieur jusqu'à l'avant-toit
        if (this.chimneyX != Integer.MIN_VALUE) {
            int cx = this.chimneyX;
            int ridge = roofY + (this.gableToStreet ? hw + 2 : (this.depth + 2) / 2) + 1;
            for (int y = 1; y <= ridge; y++) {
                f.set(cx, y, zb, this.chimneyBlock);
            }
            for (int y = 0; y <= roofY; y++) {
                f.set(cx, y, zb - 1, this.chimneyBlock);
            }
            f.stairs(cx - 1, 0, zb - 1, plinth, Direction.EAST, false);
            f.stairs(cx + 1, 0, zb - 1, plinth, Direction.WEST, false);
            f.slab(cx, roofY + 1, zb - 1, STONE_BRICK_SLAB, false);
            f.set(cx, ridge + 1, zb, CAMPFIRE);
        }
        // passage dégagé devant la porte (la ruelle y arrive, pas de fleurs)
        for (int z = 1; z <= 3; z++) {
            for (int x = this.doubleDoor ? -1 : 0; x <= 0; x++) {
                net.minecraft.core.BlockPos p = f.pos(x, 0, z);
                f.builder().keepClear(p.getX(), p.getZ());
            }
        }
        f.texture = null;
        this.interior.accept(f);
    }

    /**
     * Matières mêlées : enduit de terre cuite blanche piqué de calcite et de laine, pierres taillées / pavés avec mousse, fissures
     * et andésite, tuiles de deux teintes. Les planches et les rondins restent unis.
     */
    private static Block texture(Block block, net.minecraft.util.RandomSource random) {
        float r = random.nextFloat();
        if (block == WHITE_TERRACOTTA) {
            return r < 0.22F ? CALCITE : r < 0.30F ? WHITE_WOOL : block;
        }
        if (block == STONE_BRICKS) {
            return r < 0.14F ? MOSSY_STONE_BRICKS : r < 0.26F ? CRACKED_STONE_BRICKS : r < 0.34F ? ANDESITE : block;
        }
        if (block == COBBLESTONE) {
            return r < 0.2F ? MOSSY_COBBLESTONE : r < 0.32F ? ANDESITE : block;
        }
        if (block == MOSSY_COBBLESTONE) {
            return r < 0.35F ? COBBLESTONE : block;
        }
        if (block == BRICK_STAIRS) {
            return r < 0.15F ? POLISHED_GRANITE_STAIRS : block;
        }
        if (block == BRICK_SLAB) {
            return r < 0.15F ? POLISHED_GRANITE_SLAB : block;
        }
        if (block == SPRUCE_STAIRS) {
            return r < 0.2F ? DARK_OAK_STAIRS : block;
        }
        if (block == SPRUCE_SLAB) {
            return r < 0.2F ? DARK_OAK_SLAB : block;
        }
        if (block == DARK_OAK_STAIRS) {
            return r < 0.2F ? SPRUCE_STAIRS : block;
        }
        if (block == DARK_OAK_SLAB) {
            return r < 0.2F ? SPRUCE_SLAB : block;
        }
        if (block == OAK_STAIRS) {
            return r < 0.2F ? SPRUCE_STAIRS : block;
        }
        return block;
    }

    /** Colonnes des poteaux de façade (angles et intermédiaires). */
    private static int[] postColumns(int hw) {
        return hw >= 4 ? new int[] {-hw, -(hw - 2), hw - 2, hw} : new int[] {-hw, hw};
    }

    /** Colonnes des fenêtres sur la façade et l'arrière (jamais devant la porte ni sur un poteau). */
    private static int[] windowColumns(int hw) {
        if (hw >= 5) {
            return new int[] {-(hw - 1), -(hw - 3), hw - 3, hw - 1};
        }
        return new int[] {-(hw - 1), hw - 1};
    }

    /**
     * Fenêtre d'un bloc dans un mur : linteau en poutre au-dessus, appui en escalier renversé qui dépasse, volets ouverts de chaque
     * côté (à l'extérieur), pot de fleurs sur l'appui en option.
     */
    private void window(Frame f, int x, int y, int z, Direction out, boolean planter, net.minecraft.util.RandomSource random) {
        f.set(x, y, z, GLASS_PANE);
        int ox = out.getStepX(), oz = out.getStepZ();
        Direction side = out.getClockWise();
        f.log(x, y + 1, z, this.beam, side.getStepX() != 0 ? Direction.Axis.X : Direction.Axis.Z);
        f.stairs(x + ox, y - 1, z + oz, this.roofStairs, out.getOpposite(), true);
        for (int sgn : new int[] {-1, 1}) {
            int sx = x + side.getStepX() * sgn + ox;
            int sz = z + side.getStepZ() * sgn + oz;
            boolean beforeDoor = sz == 1 && y <= 2 && (sx == 0 || (this.doubleDoor && sx == -1));
            if (beforeDoor) {
                continue; // un volet ouvert devant la porte gênerait le passage
            }
            f.trapdoor(sx, y, sz, this.shutter, out, true, false);
        }
        if (planter) {
            Block[] pots = {POTTED_POPPY, POTTED_DANDELION, POTTED_AZALEA_BUSH, POTTED_RED_TULIP, POTTED_FERN, POTTED_BLUE_ORCHID};
            f.set(x + ox, y, z + oz, pots[random.nextInt(pots.length)]);
        }
    }
}
