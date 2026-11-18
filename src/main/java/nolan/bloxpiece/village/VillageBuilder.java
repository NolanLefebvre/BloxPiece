package nolan.bloxpiece.village;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import nolan.bloxpiece.BloxPiece;

/**
 * Pose des blocs du village dans le monde (coordonnées MONDE). Les blocs sont posés sans mises à jour de voisinage (rapide,
 * l'eau du puits ne coule pas) puis, à la fin, {@link #finish} recalcule la forme des blocs qui dépendent de leurs voisins
 * (clôtures reliées, escaliers d'angle, vitres...). Les colonnes occupées par une construction sont notées : la verdure
 * aléatoire ne pousse jamais dessus.
 */
public final class VillageBuilder {

    private final ServerLevel level;
    private final RandomSource random;
    private final List<BlockPos> placed = new ArrayList<>();
    private final Set<Long> occupied = new HashSet<>();
    private final Set<Long> keepClear = new HashSet<>();
    /** Blocs posés par une construction (bâtiment, puits, étal) : le décor posé ensuite ne peut JAMAIS les remplacer. */
    private final Set<BlockPos> structure = new HashSet<>();
    private boolean structureMode;
    private int refused;

    public VillageBuilder(ServerLevel level) {
        this.level = level;
        this.random = level.getRandom();
    }

    public ServerLevel level() {
        return this.level;
    }

    public RandomSource random() {
        return this.random;
    }

    /**
     * Mode construction : tant qu'il est actif, chaque bloc posé est protégé. Hors de ce mode (allées, mobilier, arbres, fleurs),
     * un bloc protégé n'est jamais écrasé : la pose est simplement ignorée. Une façade reste donc toujours complète.
     */
    public void structureMode(boolean on) {
        this.structureMode = on;
    }

    public boolean isStructure(BlockPos pos) {
        return this.structure.contains(pos);
    }

    /** Nombre de poses de décor refusées parce qu'elles auraient écrasé une construction. */
    public int refused() {
        return this.refused;
    }

    /** Pose un bloc (et réserve la colonne : pas de fleurs ni d'herbe dessus). */
    public void set(BlockPos pos, BlockState state) {
        BlockPos p = pos.immutable();
        if (this.structureMode) {
            this.structure.add(p);
        } else if (this.structure.contains(p)) {
            this.refused++;
            return;
        }
        this.level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        this.placed.add(p);
        this.occupied.add(columnKey(pos.getX(), pos.getZ()));
    }

    /** Pose un bloc de terrain (terrassement) : ne réserve pas la colonne. */
    public void setTerrain(BlockPos pos, BlockState state) {
        if (this.structure.contains(pos)) {
            this.refused++;
            return;
        }
        this.level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    public BlockState get(BlockPos pos) {
        return this.level.getBlockState(pos);
    }

    public boolean isOccupied(int x, int z) {
        return this.occupied.contains(columnKey(x, z));
    }

    /** Réserve une colonne sans y poser de bloc (devant une porte, place réservée à un PNJ...). */
    public void reserve(int x, int z) {
        this.occupied.add(columnKey(x, z));
    }

    /** Passage à garder dégagé (devant une porte) : la ruelle y passe, jamais de fleurs ni de buisson. */
    public void keepClear(int x, int z) {
        this.keepClear.add(columnKey(x, z));
    }

    public boolean isKeptClear(int x, int z) {
        return this.keepClear.contains(columnKey(x, z));
    }

    /** Remplit un coffre déjà posé avec une table de butin du mod (data/bloxpiece/loot_table/chests/starter_village/<name>.json). */
    public void loot(BlockPos chest, String name) {
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, BloxPiece.id("chests/starter_village/" + name));
        RandomizableContainer.setBlockEntityLootTable(this.level, this.random, chest, key);
    }

    /** Fin de construction : chaque bloc posé reprend la forme qui correspond à ses voisins (clôtures, escaliers, vitres...). */
    public void finish() {
        for (BlockPos pos : this.placed) {
            BlockState state = this.level.getBlockState(pos);
            BlockState shaped = Block.updateFromNeighbourShapes(state, this.level, pos);
            if (shaped != state) {
                this.level.setBlock(pos, shaped, Block.UPDATE_CLIENTS);
            }
        }
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
