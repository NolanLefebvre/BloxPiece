package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Gel TEMPORAIRE du terrain (fruit de la Glace) : l'eau devient de la glace et la surface du sol de la glace compactée, puis tout
 * redevient comme avant au bout d'un moment (chaque bloc avec un petit décalage aléatoire, pour un dégel progressif).
 *
 * Sécurités : seulement si la règle mobGriefing est active ; jamais de bloc avec un contenu (coffre...), ni de bloc incassable,
 * ni de bloc qui n'est pas plein ; seulement si le dessus est libre (une fleur ou une torche ne casse jamais). Un bloc gelé que
 * quelqu'un a cassé ou remplacé entre-temps n'est pas restauré par-dessus. Tout est restauré à l'arrêt du serveur.
 */
public final class IceTerrain {

    private record Frozen(ServerLevel level, BlockState original, BlockState placed, long until) {
    }

    private static final Map<BlockPos, Frozen> BLOCKS = new HashMap<>();

    private IceTerrain() {
    }

    /**
     * Gèle la colonne (x, z) : le premier bloc de surface trouvé entre {@code y + 2} et {@code y - 4}. Renvoie la hauteur du dessus
     * de la surface gelée (ou NaN si rien n'a été trouvé), utile pour poser des pics de glace.
     */
    public static double freezeColumn(ServerLevel level, int x, int y, int z, int durationTicks) {
        boolean allowed = level.getGameRules().get(GameRules.MOB_GRIEFING);
        for (int dy = 2; dy >= -4; dy--) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            BlockState top = level.getBlockState(pos.above());
            if (state.is(Blocks.WATER)) {
                if (!top.getFluidState().isEmpty()) {
                    continue; // eau profonde : seule la surface gèle
                }
                if (top.isAir() && state.getFluidState().isSource() && allowed) {
                    place(level, pos, state, Blocks.ICE.defaultBlockState(), durationTicks);
                }
                return pos.getY() + 1.0;
            }
            if (!state.getFluidState().isEmpty()) {
                return Double.NaN; // lave, eau courante... : rien
            }
            boolean freezable = allowed && top.isAir() && state.isCollisionShapeFullBlock(level, pos)
                    && level.getBlockEntity(pos) == null && state.getDestroySpeed(level, pos) >= 0.0F
                    && !state.is(Blocks.ICE) && !state.is(Blocks.PACKED_ICE) && !state.is(Blocks.SNOW_BLOCK) && !BLOCKS.containsKey(pos);
            if (freezable) {
                // surtout de la glace compactée, avec des plaques de neige pour un rendu naturel
                BlockState ice = level.getRandom().nextFloat() < 0.25F ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
                place(level, pos, state, ice, durationTicks);
            }
            return pos.getY() + 1.0;
        }
        return Double.NaN;
    }

    /** Aucune surface trouvée par {@link #findSurface}. */
    public static final int NO_SURFACE = Integer.MIN_VALUE;

    /**
     * Le sol de la colonne (x, z) autour de la hauteur {@code y0} (recherche vers le haut ET vers le bas, jusqu'à
     * {@code range} blocs) : un bloc plein (ou une surface d'eau) avec un espace libre juste au-dessus. Les feuilles ne comptent pas
     * comme sol (on gèle le sol sous les arbres) ; l'herbe haute, les fleurs, la neige fine... comptent comme espace libre.
     * Renvoie la hauteur du bloc de sol, ou {@link #NO_SURFACE}. Permet à une vague de suivre collines, falaises et creux.
     */
    public static int findSurface(ServerLevel level, int x, int y0, int z, int range) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Départ dans le vide (creux, falaise vers le bas) : on descend d'abord. Départ dans le sol (colline) : on monte d'abord.
        // Ainsi la vague reste à la surface au lieu de plonger dans une grotte ou de sauter sur un surplomb.
        int first = isFree(level, pos.set(x, y0 + 1, z)) ? -1 : 1;
        for (int pass = 0; pass < 2; pass++) {
            int dir = pass == 0 ? first : -first;
            for (int k = pass; k <= range; k++) {
                int y = y0 + dir * k;
                if (isGround(level, pos.set(x, y, z)) && isFree(level, pos.set(x, y + 1, z))) {
                    return y;
                }
            }
        }
        return NO_SURFACE;
    }

    private static boolean isGround(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER)) {
            return true;
        }
        return state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES) && state.isCollisionShapeFullBlock(level, pos);
    }

    /** Case traversable : air, herbe haute, fleurs... (rien de solide, pas de liquide). */
    private static boolean isFree(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Gèle le bloc de sol {@code ground} (trouvé par {@link #findSurface}) : eau en glace, sol en glace compactée / neige, mêmes
     * sécurités que {@link #freezeColumn}. Rien n'est gelé sous une plante (elle casserait). Renvoie vrai si la surface est de la
     * glace dégagée (dessus vide d'air) : on peut y planter un pic.
     */
    public static boolean freezeGround(ServerLevel level, BlockPos ground, int durationTicks) {
        boolean allowed = level.getGameRules().get(GameRules.MOB_GRIEFING);
        BlockState state = level.getBlockState(ground);
        BlockState top = level.getBlockState(ground.above());
        if (!top.isAir()) {
            return false; // plante, neige fine... : ni gel ni pic (pas de pic planté dans l'herbe)
        }
        if (state.is(Blocks.WATER)) {
            if (state.getFluidState().isSource() && allowed && !BLOCKS.containsKey(ground)) {
                place(level, ground, state, Blocks.ICE.defaultBlockState(), durationTicks);
            }
            return isIce(level.getBlockState(ground));
        }
        boolean freezable = allowed && level.getBlockEntity(ground) == null && state.getDestroySpeed(level, ground) >= 0.0F
                && !isIce(state) && !BLOCKS.containsKey(ground);
        if (freezable) {
            BlockState ice = level.getRandom().nextFloat() < 0.25F ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
            place(level, ground, state, ice, durationTicks);
        }
        return isIce(level.getBlockState(ground));
    }

    /**
     * Givre sur les blocs en hauteur (murs, arbres, constructions...) : la colonne au-dessus du sol {@code ground}, sur
     * {@code height} blocs, voit ses blocs pleins EXPOSÉS (au moins une face à l'air) changés en glace, temporairement. Feuilles
     * comprises (les arbres deviennent des arbres de glace). Mêmes sécurités que le sol : mobGriefing, pas de coffre, rien
     * d'incassable, rien de non plein (portes, plantes, torches...).
     */
    public static void freezeAbove(ServerLevel level, BlockPos ground, int height, int durationTicks) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return;
        }
        for (int i = 1; i <= height; i++) {
            BlockPos pos = ground.above(i);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || isIce(state) || BLOCKS.containsKey(pos)
                    || !state.isCollisionShapeFullBlock(level, pos) || level.getBlockEntity(pos) != null
                    || state.getDestroySpeed(level, pos) < 0.0F || !exposed(level, pos)) {
                continue;
            }
            BlockState ice = level.getRandom().nextFloat() < 0.65F ? Blocks.ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
            place(level, pos, state, ice, durationTicks);
        }
    }

    /** Le bloc touche-t-il l'air par au moins une face ? */
    private static boolean exposed(ServerLevel level, BlockPos pos) {
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIce(BlockState state) {
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.BLUE_ICE);
    }

    /** Vrai si les {@code height} cases au-dessus de {@code ground} sont de l'air (place pour un pic, sans traverser de bloc). */
    public static boolean hasAirAbove(ServerLevel level, BlockPos ground, int height) {
        for (int i = 1; i <= height; i++) {
            if (!level.getBlockState(ground.above(i)).isAir()) {
                return false;
            }
        }
        return true;
    }

    /** Gèle un disque de sol et d'eau de rayon {@code radius} autour de {@code center} (impact du trident, de l'aigle). */
    public static void freezeDisc(ServerLevel level, net.minecraft.world.phys.Vec3 center, double radius, int durationTicks) {
        int r = (int) Math.ceil(radius);
        int cx = net.minecraft.util.Mth.floor(center.x), cy = net.minecraft.util.Mth.floor(center.y), cz = net.minecraft.util.Mth.floor(center.z);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    freezeColumn(level, cx + dx, cy, cz + dz, durationTicks);
                }
            }
        }
    }

    /**
     * Amas de glace dans l'air autour de {@code center} (impact de l'aigle) : des blocs de glace poussent dans les cases vides
     * jusqu'à {@code radius} blocs, de plus en plus clairsemés vers le bord, puis fondent (redeviennent de l'air). Jamais dans une
     * case occupée par une entité (personne n'étouffe dans la glace).
     */
    public static void iceCluster(ServerLevel level, net.minecraft.world.phys.Vec3 center, double radius, int durationTicks) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return;
        }
        int r = (int) Math.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -1; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
                    if (d > radius || level.getRandom().nextDouble() > 0.85 * (1.0 - d / radius) + 0.05) {
                        continue;
                    }
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() || BLOCKS.containsKey(pos)
                            || !level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(pos)).isEmpty()) {
                        continue;
                    }
                    BlockState ice = level.getRandom().nextFloat() < 0.6F ? Blocks.ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
                    place(level, pos, state, ice, durationTicks);
                }
            }
        }
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState original, BlockState placed, int durationTicks) {
        if (nolan.bloxpiece.village.SafeZone.isProtected(level, pos)) {
            return; // village de départ : pas de glace posée dans le décor
        }
        long until = level.getGameTime() + durationTicks + level.getRandom().nextInt(60);
        BLOCKS.put(pos.immutable(), new Frozen(level, original, placed, until));
        level.setBlock(pos, placed, 3);
    }

    /** À appeler à chaque tick serveur (ModEvents) : dégèle les blocs arrivés à échéance. */
    public static void tick() {
        if (BLOCKS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<BlockPos, Frozen>> it = BLOCKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Frozen> entry = it.next();
            Frozen f = entry.getValue();
            if (f.level().getGameTime() >= f.until()) {
                restore(entry.getKey(), f);
                it.remove();
            }
        }
    }

    private static void restore(BlockPos pos, Frozen f) {
        if (f.level().getBlockState(pos) == f.placed()) {
            f.level().setBlock(pos, f.original(), 3);
        }
    }

    /** Ce bloc est-il de la glace posée par une attaque (elle ne doit pas fondre : IceMeltMixin) ? */
    public static boolean isFrozen(BlockPos pos) {
        return BLOCKS.containsKey(pos);
    }

    /** Restaure immédiatement tout le terrain gelé (arrêt du serveur). */
    public static void clear() {
        BLOCKS.forEach(IceTerrain::restore);
        BLOCKS.clear();
    }
}
