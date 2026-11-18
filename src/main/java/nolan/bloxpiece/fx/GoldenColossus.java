package nolan.bloxpiece.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModParticles;

/**
 * La PAUME COLOSSALE de l'Onde de choc du Bouddha : une main géante (paume, quatre doigts, pouce, poignet) faite de vrais blocs
 * massifs et brillants (or, bloc de minerai d'or brut, cuivre ciré doré, glowstone au coeur), lumineux en pleine nuit et
 * entourés d'un halo doré, qui file droit devant avec le front de l'onde puis se dissout dans une gerbe de lumière.
 * <p>
 * Chaque morceau est une entité d'affichage de bloc (block_display) agrandie, créée par commande (format stable) et déplacée en
 * douceur (téléportation interpolée). Purement visuel : aucune collision, aucun bloc du monde n'est touché. Les morceaux portent
 * le tag {@link #TAG} : ils sont supprimés à la fin de l'attaque et à l'arrêt du serveur (jamais de main qui reste figée).
 */
public final class GoldenColossus {

    public static final String TAG = "bloxpiece_colossus";

    /** Silhouette de la main : cases (u = côté, v = hauteur) et matière (0 or, 1 or brut, 2 coeur lumineux, 3 métal doré). */
    private static final int[][] SHAPE = buildShape();

    private static final String[] MATERIALS = {"minecraft:gold_block", "minecraft:raw_gold_block", "minecraft:glowstone",
            "minecraft:waxed_cut_copper"};

    private static final class Active {
        final ServerLevel level;
        final String tag;
        final Vec3 step;
        final int moveTicks;
        final int lingerTicks;
        Vec3 center;
        int age;

        Active(ServerLevel level, String tag, Vec3 center, Vec3 step, int moveTicks, int lingerTicks) {
            this.level = level;
            this.tag = tag;
            this.center = center;
            this.step = step;
            this.moveTicks = moveTicks;
            this.lingerTicks = lingerTicks;
        }
    }

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static int counter;

    private GoldenColossus() {
    }

    private static int[][] buildShape() {
        List<int[]> cells = new ArrayList<>();
        for (int u = -2; u <= 1; u++) {
            for (int v = -2; v <= 1; v++) {
                boolean core = (u == -1 || u == 0) && (v == -1 || v == 0);
                cells.add(new int[] {u, v, core ? 2 : 0});              // paume, coeur lumineux
            }
        }
        int[][] fingers = {{-2, 4}, {-1, 5}, {0, 5}, {1, 4}};          // colonne, hauteur du bout
        for (int[] f : fingers) {
            for (int v = 2; v <= f[1]; v++) {
                cells.add(new int[] {f[0], v, v == f[1] ? 1 : (v == 2 ? 3 : 0)}); // phalanges, bout en or brut
            }
        }
        int[][] thumb = {{2, -1}, {2, 0}, {3, 0}, {3, 1}};
        for (int[] t : thumb) {
            cells.add(new int[] {t[0], t[1], t[0] == 3 && t[1] == 1 ? 1 : 0});
        }
        for (int u = -2; u <= 1; u++) {
            cells.add(new int[] {u, -3, 3});                           // poignet en métal doré
        }
        cells.add(new int[] {-1, -4, 3});
        cells.add(new int[] {0, -4, 3});
        return cells.toArray(new int[0][]);
    }

    /**
     * Fait apparaître la paume colossale centrée en {@code center}, face à {@code dir}, chaque case mesurant {@code cell} blocs
     * (2,5 : main d'environ 25 blocs de haut). Elle avance de {@code speed} blocs par tick pendant {@code moveTicks} ticks, reste
     * suspendue {@code lingerTicks} ticks, puis disparaît dans un éclat de lumière.
     */
    public static void spawn(ServerLevel level, Vec3 center, Vec3 dir, double cell, double speed, int moveTicks, int lingerTicks) {
        Vec3 n = dir.normalize();
        Vec3 ref = Math.abs(n.y) > 0.95 ? new Vec3(0.0, 0.0, 1.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = n.cross(ref).normalize();
        Vec3 up = side.cross(n).normalize();
        if (up.y < 0) {
            up = up.scale(-1.0);
            side = side.scale(-1.0);
        }
        String tag = TAG + "_" + (counter++);
        float s = (float) cell;
        for (int[] c : SHAPE) {
            Vec3 p = center.add(side.scale(c[0] * cell)).add(up.scale(c[1] * cell)).subtract(cell / 2.0, cell / 2.0, cell / 2.0);
            String nbt = String.format(Locale.ROOT,
                    "{block_state:{Name:\"%s\"},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                            + "translation:[0f,0f,0f],scale:[%sf,%sf,%sf]},brightness:{sky:15,block:15},Glowing:1b,"
                            + "glow_color_override:16766720,teleport_duration:2,view_range:4f,Tags:[\"%s\",\"%s\"]}",
                    MATERIALS[c[2]], s, s, s, TAG, tag);
            run(level, String.format(Locale.ROOT, "summon minecraft:block_display %.3f %.3f %.3f %s", p.x, p.y, p.z, nbt));
        }
        ACTIVE.add(new Active(level, tag, center, n.scale(speed), moveTicks, lingerTicks));
    }

    /** À chaque tick serveur (ModEvents). */
    public static void tick(MinecraftServer server) {
        for (int i = 0; i < ACTIVE.size(); i++) {
            Active a = ACTIVE.get(i);
            a.age++;
            if (a.age <= a.moveTicks) {
                a.center = a.center.add(a.step);
                run(a.level, String.format(Locale.ROOT, "execute as @e[tag=%s] at @s run tp @s ~%.3f ~%.3f ~%.3f",
                        a.tag, a.step.x, a.step.y, a.step.z));
                if (a.age % 2 == 0) { // traînée de poussière d'or derrière la main
                    a.level.sendParticles(ModParticles.BUDDHA_GLOW_PALE, true, true, a.center.x, a.center.y, a.center.z,
                            30, 4.0, 5.0, 4.0, 0.0);
                    a.level.sendParticles(ModParticles.BUDDHA_SPARK, true, true, a.center.x, a.center.y, a.center.z,
                            40, 5.0, 6.0, 5.0, 0.3);
                }
            } else if (a.age >= a.moveTicks + a.lingerTicks) {
                // dissolution : la main éclate en lumière
                a.level.sendParticles(ModParticles.BUDDHA_FLASH, true, true, a.center.x, a.center.y, a.center.z, 3, 3.0, 4.0, 3.0, 0.0);
                a.level.sendParticles(ModParticles.BUDDHA_GLOW, true, true, a.center.x, a.center.y, a.center.z, 160, 5.0, 7.0, 5.0, 0.15);
                a.level.sendParticles(ModParticles.BUDDHA_PETAL, true, true, a.center.x, a.center.y, a.center.z, 60, 5.0, 7.0, 5.0, 0.1);
                run(a.level, "kill @e[tag=" + a.tag + "]");
                ACTIVE.remove(i--);
            }
        }
    }

    /** Arrêt du serveur : plus aucune main colossale dans le monde. */
    public static void clear(MinecraftServer server) {
        for (Active a : ACTIVE) {
            run(a.level, "kill @e[tag=" + a.tag + "]");
        }
        ACTIVE.clear();
        try {
            run(server.overworld(), "kill @e[tag=" + TAG + "]");
        } catch (RuntimeException ignored) {
            // serveur déjà en cours d'arrêt
        }
    }

    private static void run(ServerLevel level, String command) {
        MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), command);
    }
}
