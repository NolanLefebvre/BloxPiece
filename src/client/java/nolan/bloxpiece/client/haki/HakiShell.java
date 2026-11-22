package nolan.bloxpiece.client.haki;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;

/**
 * Haki de l'Armement sur les armes en 3D (modèles d'objet à « elements », comme Ace, les épées des bandits, les sabres...) :
 * la LAME est recouverte sur TOUTES ses faces (les deux plats, les tranches, le dos, le tranchant, les biseaux, la pointe), en
 * suivant la vraie géométrie du modèle, cube par cube, rotation de chaque cube comprise. La poignée, la garde et les décorations ne
 * sont jamais recouvertes.
 *
 * <h2>Convention (valable pour toutes les armes, actuelles et futures : rien à changer ici pour une nouvelle épée)</h2>
 * Dans le modèle JSON de l'objet (assets/&lt;mod&gt;/models/item/&lt;id&gt;.json), un cube fait partie de la lame si :
 * <ol>
 *   <li>il porte {@code "bloxpiece_part": "blade"} (marque explicite ; les autres valeurs, par ex. "grip", "guard", "decor",
 *       l'excluent) ;</li>
 *   <li>sinon, si toutes ses faces utilisent la texture {@code "#blade"} (convention déjà suivie par toutes les épées du mod :
 *       Ace, épées des bandits, sabres de pirate, de capitaine et de la Marine).</li>
 * </ol>
 * Le point de départ de l'encre (la main) est le centre des cubes de poignée ({@code "bloxpiece_part": "grip"} ou texture
 * {@code "#grip"}), sinon le bas de la lame. La position de l'arme en main reprend les transformations d'affichage de SON modèle
 * (« display » : 1re et 3e personne, main gauche comprise, comme Minecraft). Un modèle sans lame identifiée (pistolet, fusil...) :
 * pas de revêtement visuel (le bonus de dégâts du Haki s'applique quand même).
 *
 * Chaque face de la lame est découpée en cellules d'environ 1 pixel, posées juste au-dessus d'elle ({@link #LIFT}) et un peu
 * débordantes ({@link #LIFT}) pour fermer les arêtes ; la couleur de chaque cellule ne dépend que de sa position dans l'arme : deux
 * cubes qui se touchent ont la même encre au même endroit (pas de couture visible).
 */
final class HakiShell {

    /** Hauteur des cellules au-dessus des faces de la lame (px du modèle) : pas de clignotement avec l'arme elle-même. */
    static final float LIFT = 0.06F;
    /** Taille visée des cellules (px du modèle). */
    private static final float CELL = 1.0F;

    /** Transformation d'affichage d'un modèle (« display ») : rotation (degrés), translation (px), échelle. */
    record Display(float rx, float ry, float rz, float tx, float ty, float tz, float sx, float sy, float sz) {
        static final Display NONE = new Display(0, 0, 0, 0, 0, 0, 1, 1, 1);

        /** Comme ItemTransform.apply de Minecraft (main gauche : translation x, rotations y et z inversées). */
        void apply(PoseStack pose, boolean left) {
            float side = left ? -1.0F : 1.0F;
            pose.translate(side * tx / 16.0F, ty / 16.0F, tz / 16.0F);
            pose.rotateDegrees(Axis.XP, rx);
            pose.rotateDegrees(Axis.YP, ry * side);
            pose.rotateDegrees(Axis.ZP, rz * side);
            pose.scale(sx, sy, sz);
        }
    }

    /** Rotation propre d'un cube du modèle (origine en px, axe 0/1/2 = x/y/z, angle en degrés, « rescale »). */
    private record Turn(float ox, float oy, float oz, int axis, float angle, boolean rescale) {
    }

    /**
     * Une cellule de revêtement : cube de la lame (sa rotation), face (0 nord -z, 1 sud +z, 2 ouest -x, 3 est +x, 4 dessous -y,
     * 5 dessus +y), coin et taille (px, repère du cube), et ses données d'encre (distance à la main, coordonnées de bruit).
     */
    record Cell(Turn turn, int face, float x, float y, float z, float w, float h, float along, float u, float v, int grain) {
    }

    /** Lame d'une arme : ses cellules, la distance maximale à la main, ses transformations d'affichage. */
    record Shell(List<Cell> cells, float maxDistance, Display thirdRight, Display thirdLeft, Display firstRight,
                 Display firstLeft) {
    }

    private HakiShell() {
    }

    // ------------------------------------------------------------------ lecture du modèle

    /** Lame du modèle de cet objet, ou vide (modèle plat, sans « elements », ou sans lame identifiée). */
    static Optional<Shell> load(Identifier key) {
        try {
            Identifier model = Identifier.fromNamespaceAndPath(key.getNamespace(), "models/item/" + key.getPath() + ".json");
            var resource = Minecraft.getInstance().getResourceManager().getResource(model);
            if (resource.isEmpty()) {
                return Optional.empty();
            }
            JsonObject json;
            try (InputStream in = resource.get().open()) {
                json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            if (!json.has("elements")) {
                return Optional.empty();
            }
            List<JsonObject> blades = new ArrayList<>();
            float gx = 0, gy = 0, gz = 0;
            int grips = 0;
            for (JsonElement e : json.getAsJsonArray("elements")) {
                JsonObject el = e.getAsJsonObject();
                String part = part(el);
                if ("blade".equals(part)) {
                    blades.add(el);
                } else if ("grip".equals(part)) {
                    float[] c = center(el);
                    gx += c[0];
                    gy += c[1];
                    gz += c[2];
                    grips++;
                }
            }
            if (blades.isEmpty()) {
                return Optional.empty();
            }
            float[] grip;
            if (grips > 0) {
                grip = new float[] {gx / grips, gy / grips, gz / grips};
            } else {
                grip = null; // pas de poignée identifiée : départ au point le plus bas de la lame
                for (JsonObject el : blades) {
                    float[] c = center(el);
                    if (grip == null || c[1] < grip[1]) {
                        grip = c;
                    }
                }
            }
            List<Cell> cells = new ArrayList<>();
            float max = 0.0F;
            for (int i = 0; i < blades.size(); i++) {
                max = Math.max(max, cells(blades.get(i), grip, cells, i));
            }
            JsonObject display = json.has("display") ? json.getAsJsonObject("display") : new JsonObject();
            Display tr = display(display, "thirdperson_righthand", null);
            Display fr = display(display, "firstperson_righthand", null);
            return Optional.of(new Shell(cells, max, tr, display(display, "thirdperson_lefthand", tr), fr,
                    display(display, "firstperson_lefthand", fr)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Rôle d'un cube : marque "bloxpiece_part", sinon "blade" / "grip" d'après la texture de toutes ses faces, sinon "". */
    private static String part(JsonObject el) {
        if (el.has("bloxpiece_part")) {
            return el.get("bloxpiece_part").getAsString();
        }
        if (!el.has("faces")) {
            return "";
        }
        String common = null;
        for (var face : el.getAsJsonObject("faces").entrySet()) {
            JsonObject f = face.getValue().getAsJsonObject();
            String tex = f.has("texture") ? f.get("texture").getAsString() : "";
            if (common == null) {
                common = tex;
            } else if (!common.equals(tex)) {
                return "";
            }
        }
        if ("#blade".equals(common)) {
            return "blade";
        }
        return "#grip".equals(common) ? "grip" : "";
    }

    private static float[] vec(JsonObject el, String name) {
        JsonArray a = el.getAsJsonArray(name);
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static Turn turn(JsonObject el) {
        if (!el.has("rotation")) {
            return null;
        }
        JsonObject r = el.getAsJsonObject("rotation");
        float[] o = r.has("origin") ? vec(r, "origin") : new float[] {8, 8, 8};
        String axis = r.has("axis") ? r.get("axis").getAsString() : "y";
        float angle = r.has("angle") ? r.get("angle").getAsFloat() : 0.0F;
        boolean rescale = r.has("rescale") && r.get("rescale").getAsBoolean();
        return angle == 0.0F ? null : new Turn(o[0], o[1], o[2], "x".equals(axis) ? 0 : "y".equals(axis) ? 1 : 2, angle, rescale);
    }

    /** Point du cube après sa rotation propre (px). */
    private static float[] turned(Turn t, float x, float y, float z) {
        if (t == null) {
            return new float[] {x, y, z};
        }
        double a = Math.toRadians(t.angle());
        double c = Math.cos(a), s = Math.sin(a);
        double k = t.rescale() ? 1.0 / Math.cos(Math.min(Math.abs(a), Math.toRadians(45.0))) : 1.0;
        double px = x - t.ox(), py = y - t.oy(), pz = z - t.oz();
        double nx = px, ny = py, nz = pz;
        switch (t.axis()) {
            case 0 -> { ny = (py * c - pz * s) * k; nz = (py * s + pz * c) * k; }
            case 1 -> { nx = (px * c + pz * s) * k; nz = (-px * s + pz * c) * k; }
            default -> { nx = (px * c - py * s) * k; ny = (px * s + py * c) * k; }
        }
        return new float[] {(float) (nx + t.ox()), (float) (ny + t.oy()), (float) (nz + t.oz())};
    }

    private static float[] center(JsonObject el) {
        float[] f = vec(el, "from"), t = vec(el, "to");
        return turned(turn(el), (f[0] + t[0]) / 2, (f[1] + t[1]) / 2, (f[2] + t[2]) / 2);
    }

    /** Découpe les faces existantes du cube en cellules ; renvoie la plus grande distance à la main. */
    private static float cells(JsonObject el, float[] grip, List<Cell> out, int index) {
        // hauteur un peu différente d'un cube à l'autre : deux cubes de lame qui se touchent sur le même plan ne se disputent
        // jamais le même pixel (pas de scintillement là où leurs revêtements se chevauchent)
        float lift = LIFT + 0.012F * (index % 4);
        float[] f = vec(el, "from"), t = vec(el, "to");
        Turn turn = turn(el);
        JsonObject faces = el.has("faces") ? el.getAsJsonObject("faces") : new JsonObject();
        String[] names = {"north", "south", "west", "east", "down", "up"};
        float max = 0.0F;
        for (int face = 0; face < 6; face++) {
            if (!faces.has(names[face])) {
                continue; // face absente du modèle : rien à recouvrir
            }
            // plan de la face (légèrement au-dessus) et ses deux côtés, élargis de lift pour fermer les arêtes
            float a0, a1, b0, b1;
            switch (face) {
                case 0, 1 -> { a0 = f[0]; a1 = t[0]; b0 = f[1]; b1 = t[1]; }  // x, y
                case 2, 3 -> { a0 = f[2]; a1 = t[2]; b0 = f[1]; b1 = t[1]; }  // z, y
                default -> { a0 = f[0]; a1 = t[0]; b0 = f[2]; b1 = t[2]; }    // x, z
            }
            a0 -= lift;
            a1 += lift;
            b0 -= lift;
            b1 += lift;
            float plane = switch (face) {
                case 0 -> f[2] - lift;
                case 1 -> t[2] + lift;
                case 2 -> f[0] - lift;
                case 3 -> t[0] + lift;
                case 4 -> f[1] - lift;
                default -> t[1] + lift;
            };
            int na = Math.max(1, Math.round((a1 - a0) / CELL)), nb = Math.max(1, Math.round((b1 - b0) / CELL));
            float da = (a1 - a0) / na, db = (b1 - b0) / nb;
            for (int i = 0; i < na; i++) {
                for (int j = 0; j < nb; j++) {
                    float ca = a0 + i * da, cb = b0 + j * db;
                    float x, y, z;
                    switch (face) {
                        case 0, 1 -> { x = ca; y = cb; z = plane; }
                        case 2, 3 -> { x = plane; y = cb; z = ca; }
                        default -> { x = ca; y = plane; z = cb; }
                    }
                    // centre de la cellule dans l'arme (rotation du cube comprise) : encre, bruit
                    float ma = ca + da / 2, mb = cb + db / 2;
                    float[] p = switch (face) {
                        case 0, 1 -> turned(turn, ma, mb, plane);
                        case 2, 3 -> turned(turn, plane, mb, ma);
                        default -> turned(turn, ma, plane, mb);
                    };
                    float along = (float) Math.sqrt((p[0] - grip[0]) * (p[0] - grip[0]) + (p[1] - grip[1]) * (p[1] - grip[1])
                            + (p[2] - grip[2]) * (p[2] - grip[2]));
                    max = Math.max(max, along);
                    out.add(new Cell(turn, face, x, y, z, da, db, along, p[0] + 0.6F * p[2], p[1],
                            Math.round(p[0] * 3) * 7919 + Math.round(p[1] * 3) * 104729 + Math.round(p[2] * 3)));
                }
            }
        }
        return max;
    }

    private static Display display(JsonObject display, String name, Display fallback) {
        if (!display.has(name)) {
            return fallback != null ? fallback : Display.NONE;
        }
        JsonObject d = display.getAsJsonObject(name);
        float[] r = d.has("rotation") ? vec(d, "rotation") : new float[] {0, 0, 0};
        float[] t = d.has("translation") ? vec(d, "translation") : new float[] {0, 0, 0};
        float[] s = d.has("scale") ? vec(d, "scale") : new float[] {1, 1, 1};
        return new Display(r[0], r[1], r[2], t[0], t[1], t[2], s[0], s[1], s[2]);
    }

    // ------------------------------------------------------------------ dessin

    /** Dessine les cellules (repère : modèle de l'objet après sa transformation d'affichage). colors[i] : couleur de la cellule i. */
    static void draw(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector, Shell shell, int[] colors,
                     int light) {
        ModelPart xy = HakiArmamentLayer.cellXY;
        if (xy == null) {
            return;
        }
        var type = net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(HakiArmamentLayer.WHITE_TEXTURE);
        pose.pushPose();
        pose.translate(-0.5F, -0.5F, -0.5F);
        List<Cell> cells = shell.cells();
        for (int i = 0; i < cells.size(); i++) {
            int color = colors[i];
            if ((color >>> 24) == 0) {
                continue;
            }
            Cell c = cells.get(i);
            pose.pushPose();
            Turn t = c.turn();
            if (t != null) {
                // rotation propre du cube, comme Minecraft (autour de son origine, « rescale » compris)
                pose.translate(t.ox() / 16.0F, t.oy() / 16.0F, t.oz() / 16.0F);
                pose.rotateDegrees(t.axis() == 0 ? Axis.XP : t.axis() == 1 ? Axis.YP : Axis.ZP, t.angle());
                if (t.rescale()) {
                    float k = (float) (1.0 / Math.cos(Math.min(Math.abs(Math.toRadians(t.angle())), Math.toRadians(45.0))));
                    pose.scale(t.axis() == 0 ? 1.0F : k, t.axis() == 1 ? 1.0F : k, t.axis() == 2 ? 1.0F : k);
                }
                pose.translate(-t.ox() / 16.0F, -t.oy() / 16.0F, -t.oz() / 16.0F);
            }
            pose.translate(c.x() / 16.0F, c.y() / 16.0F, c.z() / 16.0F);
            switch (c.face()) {
                case 0, 1 -> pose.scale(c.w(), c.h(), 1.0F);                       // plan XY
                case 2, 3 -> {                                                      // plan ZY : la cellule XY tournée
                    pose.rotateDegrees(Axis.YP, -90.0F);
                    pose.scale(c.w(), c.h(), 1.0F);
                }
                default -> {                                                        // plan XZ
                    pose.rotateDegrees(Axis.XP, 90.0F);
                    pose.scale(c.w(), c.h(), 1.0F);
                }
            }
            collector.order(3).submitModelPart(xy, pose, type, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    null, color);
            pose.popPose();
        }
        pose.popPose();
    }
}
