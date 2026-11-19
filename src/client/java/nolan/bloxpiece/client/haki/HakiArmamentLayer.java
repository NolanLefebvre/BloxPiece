package nolan.bloxpiece.client.haki;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;

import nolan.bloxpiece.BloxPiece;

/**
 * Revêtement du Haki de l'Armement : une GAINE posée sur chaque membre (même pose que le joueur : elle épouse ses animations),
 * faite de tranches d'un pixel le long du membre. L'encre noire avance de tranche en tranche comme un liquide qui recouvre la
 * peau : un front adouci, légèrement irrégulier et ondulant, précédé de fines nervures sombres, puis le noir métallique se fige.
 * <ul>
 *   <li><b>Appui simple</b> : depuis les doigts du bras principal jusqu'à MI-BRAS.</li>
 *   <li><b>Maintien (corps entier)</b> : l'encre finit de remonter le bras, déborde sur le torse (des épaules vers la taille),
 *       descend l'autre bras, monte sur la tête et coule le long des jambes, chaque partie partant de sa jonction avec la
 *       précédente, avec des départs qui se chevauchent : un seul flux continu.</li>
 * </ul>
 * Pendant l'apparition : front de tension rouge-violet qui vibre, ondes d'énergie qui se détachent, vague claire qui parcourt
 * le noir, puis éclat métallique au durcissement.
 *
 * <b>Stabilité</b> : une seule passe par tranche (plus de seconde passe sur la même géométrie) et une gaine gonflée nettement
 * au-delà de la seconde couche du skin (manches, veste, pantalon : 0,25 px ; chapeau : 0,5 px) : plus de clignotement.
 * <b>Morphologie</b> : bras de 3 px (skin fin, Alex) ou 4 px (Steve), lu sur le modèle (PlayerModelSlimAccessor).
 * Aussi dessiné sur les mains à la première personne (AvatarRendererHandMixin -> {@link #submitHand}).
 */
public class HakiArmamentLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("haki_armament_coat"), "main");
    /** Texture de la gaine (noir métallique et nervures), teintée à chaque tranche : reflets et lueurs sans seconde passe. */
    private static final Identifier COAT = BloxPiece.id("textures/entity/haki_coat.png");
    /** Texture blanche unie, pour les anneaux de lueur (géométrie plus large : jamais superposée à la gaine). */
    private static final Identifier WHITE = BloxPiece.id("textures/entity/haki_eye_trail.png");
    static final Identifier WHITE_TEXTURE = WHITE;
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Gonflement de la gaine au-delà du membre : au-dessus de la seconde couche du skin, avec une marge sans clignotement. */
    private static final float LIMB_INFLATE = 0.36F;
    private static final float HEAD_INFLATE = 0.62F;
    /** Hauteur couverte par un appui simple : la moitié du bras (6 px sur 12). */
    private static final float HALF_ARM = 6.0F;
    /** Largeur de la bande de nervures devant le front de l'encre (px). */
    private static final float VEIN_BAND = 2.4F;
    /** Douceur du bord de l'encre (px). */
    private static final float INK_EDGE = 1.6F;

    /** Formes de gaine : largeur x profondeur (px), nombre de tranches, haut du membre (y du modèle), gonflement. */
    private enum Shape {
        ARM_WIDE(4, 4, 12, -2, LIMB_INFLATE),
        ARM_SLIM(3, 4, 12, -2, LIMB_INFLATE),
        BODY(8, 4, 12, 0, LIMB_INFLATE),
        HEAD(8, 8, 8, -8, HEAD_INFLATE),
        LEG(4, 4, 12, 0, LIMB_INFLATE);

        final int w, d, count, top;
        final float inflate;

        Shape(int w, int d, int count, int top, float inflate) {
            this.w = w;
            this.d = d;
            this.count = count;
            this.top = top;
            this.inflate = inflate;
        }
    }

    /** Faces d'une tranche, dans l'ordre du tour du membre (avant, droite, arrière, gauche) : le flux peut y tourner en spirale. */
    private static final int FACES = 4;

    /**
     * Plaques de chaque forme : [tranche][face], du haut du membre (tranche 0) vers le bas, plus deux couvercles (haut, bas).
     * Uniquement des SURFACES extérieures (plaques sans épaisseur) : aucune face interne superposée, donc aucun clignotement.
     * Partagées avec la vue à la première personne.
     */
    private static Map<Shape, ModelPart[][]> plates;
    private static Map<Shape, ModelPart[]> caps;
    /** Cellule d'un pixel (plaque sans épaisseur) : face avant/arrière (plan XY) et face de côté (plan ZY). */
    static ModelPart cellXY;
    private static ModelPart cellZY;

    public HakiArmamentLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ModelPart root) {
        super(parent);
        Map<Shape, ModelPart[][]> map = new EnumMap<>(Shape.class);
        Map<Shape, ModelPart[]> capMap = new EnumMap<>(Shape.class);
        for (Shape shape : Shape.values()) {
            String name = shape.name().toLowerCase();
            ModelPart[][] parts = new ModelPart[shape.count][FACES];
            for (int k = 0; k < shape.count; k++) {
                for (int f = 0; f < FACES; f++) {
                    parts[k][f] = root.getChild(name + "_" + k + "_" + f);
                }
            }
            map.put(shape, parts);
            capMap.put(shape, new ModelPart[] {root.getChild(name + "_cap_top"), root.getChild(name + "_cap_bottom")});
        }
        plates = map;
        caps = capMap;
        cellXY = root.getChild("cell_xy");
        cellZY = root.getChild("cell_zy");
    }

    /**
     * Pour chaque forme : 4 plaques d'un pixel de haut par tranche, posées sur la surface gonflée du membre (au-delà de la seconde
     * couche du skin), et un couvercle à chaque bout. Les tranches des extrémités descendent jusqu'au couvercle (pas de jour).
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (Shape shape : Shape.values()) {
            String name = shape.name().toLowerCase();
            float hw = shape.w / 2.0F + shape.inflate;
            float hd = shape.d / 2.0F + shape.inflate;
            for (int k = 0; k < shape.count; k++) {
                float y0 = shape.top + k - (k == 0 ? shape.inflate : 0.0F);
                float h = 1.0F + (k == 0 ? shape.inflate : 0.0F) + (k == shape.count - 1 ? shape.inflate : 0.0F);
                int v = k % 12;
                root.addOrReplaceChild(name + "_" + k + "_0", CubeListBuilder.create().texOffs(0, v)
                        .addBox(-hw, y0, -hd, hw * 2.0F, h, 0.0F), PartPose.ZERO);                 // avant
                root.addOrReplaceChild(name + "_" + k + "_1", CubeListBuilder.create().texOffs(8, v)
                        .addBox(hw, y0, -hd, 0.0F, h, hd * 2.0F), PartPose.ZERO);                  // côté
                root.addOrReplaceChild(name + "_" + k + "_2", CubeListBuilder.create().texOffs(16, v)
                        .addBox(-hw, y0, hd, hw * 2.0F, h, 0.0F), PartPose.ZERO);                  // arrière
                root.addOrReplaceChild(name + "_" + k + "_3", CubeListBuilder.create().texOffs(24, v)
                        .addBox(-hw, y0, -hd, 0.0F, h, hd * 2.0F), PartPose.ZERO);                 // autre côté
            }
            root.addOrReplaceChild(name + "_cap_top", CubeListBuilder.create().texOffs(0, 20)
                    .addBox(-hw, shape.top - shape.inflate, -hd, hw * 2.0F, 0.0F, hd * 2.0F), PartPose.ZERO);
            root.addOrReplaceChild(name + "_cap_bottom", CubeListBuilder.create().texOffs(0, 20)
                    .addBox(-hw, shape.top + shape.count + shape.inflate, -hd, hw * 2.0F, 0.0F, hd * 2.0F), PartPose.ZERO);
        }
        root.addOrReplaceChild("cell_xy", CubeListBuilder.create().texOffs(0, 24).addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("cell_zy", CubeListBuilder.create().texOffs(0, 24).addBox(0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    public static boolean isSlim(PlayerModel model) {
        return model instanceof nolan.bloxpiece.client.mixin.PlayerModelSlimAccessor accessor && accessor.bloxpiece$isSlim();
    }

    /** Centre du bras en x (px) dans son repère : les bras sont décalés vers l'extérieur du corps. */
    private static float armOffset(boolean right, boolean slim) {
        float o = slim ? 0.5F : 1.0F;
        return right ? -o : o;
    }

    // ---- Propagation

    /** Ease in-out doux, avec une légère houle à mi-course : le liquide accélère, ralentit, puis se pose. */
    static float flow(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        float e = t * t * (3.0F - 2.0F * t);
        return Mth.clamp(e + 0.06F * Mth.sin(t * (float) Math.PI * 2.0F) * (1.0F - t), 0.0F, 1.0F);
    }

    /** Progression d'une partie qui démarre à {@code from} et finit à {@code to} (fractions de la propagation du corps). */
    private static float window(float b, float from, float to) {
        return flow((b - from) / (to - from));
    }

    /** Longueur (px) couverte sur le bras principal : mi-bras à l'appui, puis le reste en début de corps entier. */
    private static float mainArmCover(float[] progress) {
        float arm = flow(progress[0]) * HALF_ARM;
        return progress[3] < 0.0F ? arm : arm + window(progress[1], 0.0F, 0.32F) * (12.0F - HALF_ARM);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || Minecraft.getInstance().level == null || plates == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(state.id);
        if (entity == null) {
            return;
        }
        float[] progress = HakiClientState.armament(entity, state.ageInTicks);
        if (progress == null) {
            return;
        }
        PlayerModel model = this.getParentModel();
        boolean slim = isSlim(model);
        boolean rightMain = !(entity instanceof net.minecraft.world.entity.LivingEntity living)
                || living.getMainArm() == HumanoidArm.RIGHT;
        ModelPart main = rightMain ? model.rightArm : model.leftArm;
        ModelPart other = rightMain ? model.leftArm : model.rightArm;
        Shape arm = slim ? Shape.ARM_SLIM : Shape.ARM_WIDE;
        float age = state.ageInTicks;
        float b = progress[1];
        int seed = state.id;

        // bras principal : depuis les doigts (bas du bras) vers l'épaule
        part(collector, pose, main, arm, armOffset(rightMain, slim), mainArmCover(progress), false, progress, light, age, seed);
        // l'objet tenu (arme, outil...) se couvre à son tour, depuis la poignée
        if (entity instanceof net.minecraft.world.entity.LivingEntity holder) {
            HakiItemCoat.render(pose, collector, main, holder, rightMain, slim, light, age);
        }
        if (progress[3] >= 0.0F) {
            // corps entier : torse depuis les épaules, autre bras depuis l'épaule, tête depuis le cou, jambes depuis les hanches
            part(collector, pose, model.body, Shape.BODY, 0.0F, window(b, 0.18F, 0.62F) * 12.0F, true, progress, light, age, seed + 1);
            part(collector, pose, other, arm, armOffset(!rightMain, slim), window(b, 0.34F, 0.80F) * 12.0F, true, progress, light,
                    age, seed + 2);
            part(collector, pose, model.head, Shape.HEAD, 0.0F, window(b, 0.42F, 0.86F) * 8.0F, false, progress, light, age, seed + 3);
            part(collector, pose, model.rightLeg, Shape.LEG, 0.0F, window(b, 0.52F, 1.0F) * 12.0F, true, progress, light, age, seed + 4);
            part(collector, pose, model.leftLeg, Shape.LEG, 0.0F, window(b, 0.56F, 1.0F) * 12.0F, true, progress, light, age, seed + 5);
        }
    }

    /** Une partie du corps, dans son propre repère (pose du modèle). */
    private static void part(SubmitNodeCollector collector, PoseStack pose, ModelPart limb, Shape shape, float offsetX, float cover,
                             boolean fromTop, float[] progress, int light, float age, int seed) {
        if (!limb.visible || cover <= 0.0F) {
            return;
        }
        pose.pushPose();
        limb.translateAndRotate(pose);
        coat(collector, pose, shape, offsetX, cover, fromTop, progress, light, age, seed);
        pose.popPose();
    }

    // ---- Bruit (masque organique de l'encre)

    /** Hachage entier -> [0, 1). */
    static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Bruit de valeur 2D lissé (grille de {@code cell} px), périodique sur le tour du membre ({@code period} px). */
    static float noise(float u, float v, float cell, int period, int seed) {
        float x = u / cell, y = v / cell;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        int wrap = Math.max(1, Math.round(period / cell));
        float a = hash(Math.floorMod(x0, wrap), y0, seed), b = hash(Math.floorMod(x0 + 1, wrap), y0, seed);
        float c = hash(Math.floorMod(x0, wrap), y0 + 1, seed), d = hash(Math.floorMod(x0 + 1, wrap), y0 + 1, seed);
        float sx = fx * fx * (3.0F - 2.0F * fx), sy = fy * fy * (3.0F - 2.0F * fy);
        return Mth.lerp(sy, Mth.lerp(sx, a, b), Mth.lerp(sx, c, d));
    }

    /**
     * Gaine d'une partie, dans son repère (déjà appliqué à {@code pose}) : {@code cover} pixels recouverts, depuis le haut
     * ({@code fromTop}) ou depuis le bas du membre.
     *
     * La surface est découpée en CELLULES d'un pixel, et chaque cellule a son propre délai d'apparition : distance au départ du
     * flux + une tache de bruit à grosses grappes (3 px, ±2 px de retard) + du bruit fin (1,5 px) + une spirale autour du membre + une légère
     * respiration dans le temps. L'encre s'étend donc comme une tache vivante, en coulées et en grappes asymétriques, sans
     * colonnes ni paliers ; son bord final (mi-bras en appui simple) reste irrégulier. Devant l'encre, seules les cellules des
     * nervures s'assombrissent. Teinte procédurale par cellule : noir métallique, nervures en relief, reflets.
     */
    private static void coat(SubmitNodeCollector collector, PoseStack pose, Shape shape, float offsetX, float cover, boolean fromTop,
                             float[] progress, int light, float age, int seed) {
        int n = shape.count;
        boolean settling = cover < n - 0.05F && (progress[3] >= 0.0F || cover < HALF_ARM - 0.05F);
        float elapsed = progress[3] >= 0.0F ? progress[3] : progress[2];
        float settleTime = progress[3] >= 0.0F ? HakiClientState.TRANSITION_TICKS * 1.4F : HakiClientState.TRANSITION_TICKS;
        float hardenAge = elapsed - settleTime;
        float harden = hardenAge < 0.0F || hardenAge > 8.0F ? 0.0F : 1.0F - hardenAge / 8.0F;
        float shimmer = 0.5F + 0.5F * Mth.sin(age * 0.25F);
        RenderType type = RenderTypes.entityTranslucent(WHITE);
        float hw = shape.w / 2.0F + shape.inflate;
        float hd = shape.d / 2.0F + shape.inflate;
        int perimeter = 2 * (shape.w + shape.d);
        float spinTurn = (seed & 1) == 0 ? 1.0F : -1.0F;
        float spinStart = hash(seed, 7, 3);
        // même rendu partout (bras, torse, tête, jambes) : toujours cellule par cellule, même grain, mêmes nervures, mêmes reflets
        boolean bulk = false;
        // respiration du bord : seulement pendant que l'encre avance, puis elle s'éteint en douceur -> une fois posé, le bord est
        // FIXE (avant, il oscillait en permanence et les cellules de la limite du demi-bras clignotaient)
        float motion = 1.0F - flow(progress[3] >= 0.0F ? progress[1] : progress[0]);
        // le bruit retarde certaines cellules de près de 4 px : le front est poussé d'autant à mesure qu'il approche du bout,
        // pour que le membre finisse entièrement couvert (et que mi-bras reste à peu près à mi-bras)
        float reach = cover + 4.2F * (cover / n) * (cover / n);
        float rawCover = cover;
        cover = reach;

        pose.pushPose();
        pose.translate(offsetX / 16.0F, 0.0F, 0.0F);
        float frontAlong = Float.MAX_VALUE;
        float[] endInk = new float[2];
        int[] endCount = new int[2];
        for (int i = 0; i < n; i++) {
            int k = fromTop ? i : n - 1 - i;
            float y0 = shape.top + k - (k == 0 ? shape.inflate : 0.0F);
            float ch = 1.0F + (k == 0 ? shape.inflate : 0.0F) + (k == n - 1 ? shape.inflate : 0.0F);
            int cu = 0; // position sur le tour du membre (px)
            for (int f = 0; f < FACES; f++) {
                boolean xy = f % 2 == 0;
                int cells = xy ? shape.w : shape.d;
                float span = xy ? hw * 2.0F : hd * 2.0F;
                float cw = span / cells;
                boolean rowDone = bulk;
                int[] colors = new int[cells];
                for (int c = 0; c < cells; c++, cu++) {
                    float u = cu + 0.5F;
                    float along = i + 0.5F;
                    float spiral = (((u / perimeter) * spinTurn + spinStart) % 1.0F + 1.0F) % 1.0F;
                    float blob = noise(u, along, 3.0F, perimeter, seed) - 0.5F;
                    float fine = noise(u, along, 1.5F, perimeter, seed + 17) - 0.5F;
                    float breathe = 0.25F * motion * Mth.sin(age * 0.6F + u * 0.7F + along * 0.5F);
                    float delay = along + 4.2F * blob + 1.3F * fine + 2.2F * (spiral - 0.5F) + breathe;
                    float ink = Mth.clamp((cover - delay) / 0.9F + 0.5F, 0.0F, 1.0F);
                    float ridge = 1.0F - Math.abs(2.0F * noise(u * 1.3F, along * 0.7F, 2.0F, perimeter, seed + 31) - 1.0F);
                    if (i == 0 || i == n - 1) {
                        int e = i == 0 ? 0 : 1;
                        endInk[e] += ink;
                        endCount[e]++;
                    }
                    if (ink < 1.0F) {
                        rowDone = false;
                        if (ink > 0.0F || delay - cover < VEIN_BAND) {
                            frontAlong = Math.min(frontAlong, delay);
                        }
                    }
                    // bord de face (0 = arête du membre, 1 = milieu de la face) : les arêtes s'assombrissent -> contours lisibles
                    float edge = Math.min(c + 0.5F, cells - c - 0.5F) / (cells / 2.0F);
                    float height = fromTop ? 1.0F - along / n : along / n; // 1 = haut du membre (lumière venant d'en haut)
                    colors[c] = cellColor(ink, cover - delay, ridge, hash(cu, i, seed + 5), along, cover, elapsed, settling,
                            harden, shimmer, f, edge, height, u, age);
                }
                if (rowDone) {
                    // rangée entièrement figée : une seule plaque, teinte moyenne
                    collector.order(3).submitModelPart(plates.get(shape)[k][f], pose, type, light, OverlayTexture.NO_OVERLAY, null,
                            colors[cells / 2]);
                    continue;
                }
                for (int c = 0; c < cells; c++) {
                    if ((colors[c] >>> 24) == 0) {
                        continue;
                    }
                    pose.pushPose();
                    if (f == 0) {
                        pose.translate((-hw + c * cw) / 16.0F, y0 / 16.0F, -hd / 16.0F);
                    } else if (f == 1) {
                        pose.translate(hw / 16.0F, y0 / 16.0F, (-hd + c * cw) / 16.0F);
                    } else if (f == 2) {
                        pose.translate((hw - (c + 1) * cw) / 16.0F, y0 / 16.0F, hd / 16.0F);
                    } else {
                        pose.translate(-hw / 16.0F, y0 / 16.0F, (hd - (c + 1) * cw) / 16.0F);
                    }
                    if (xy) {
                        pose.scale(cw, ch, 1.0F);
                    } else {
                        pose.scale(1.0F, ch, cw);
                    }
                    collector.order(3).submitModelPart(xy ? cellXY : cellZY, pose, type, light, OverlayTexture.NO_OVERLAY, null,
                            colors[c]);
                    pose.popPose();
                }
            }
        }
        // couvercles des deux bouts (bout des doigts, sommet du crâne...) : quand l'encre a recouvert la rangée du bout
        ModelPart[] ends = caps.get(shape);
        float startCap = endCount[0] == 0 ? 0.0F : endInk[0] / endCount[0];
        float endCap = endCount[1] == 0 ? 0.0F : endInk[1] / endCount[1];
        float topInk = fromTop ? startCap : endCap;
        float bottomInk = fromTop ? endCap : startCap;
        int capColor = cellColor(1.0F, 3.0F, 0.0F, 0.5F, 0.0F, cover, elapsed, false, harden, shimmer, 0, 0.6F, 1.0F, 0.0F, age);
        if (topInk > 0.5F) {
            collector.order(3).submitModelPart(ends[0], pose, type, light, OverlayTexture.NO_OVERLAY, null,
                    argb((topInk - 0.5F) * 2.0F, capColor));
        }
        if (bottomInk > 0.5F) {
            collector.order(3).submitModelPart(ends[1], pose, type, light, OverlayTexture.NO_OVERLAY, null,
                    argb((bottomInk - 0.5F) * 2.0F, capColor));
        }
        if (settling && frontAlong < Float.MAX_VALUE) {
            ModelPart[][] parts = plates.get(shape);
            int frontRow = Mth.clamp((int) frontAlong, 0, n - 1);
            int frontIndex = fromTop ? frontRow : n - 1 - frontRow;
            // front de tension : lueur violet électrique qui vibre là où avance l'encre
            float pulse = 0.5F + 0.5F * Mth.sin(age * 2.3F);
            ring(collector, pose, parts, shape, frontIndex, 1.07F, 1.4F, argb(0.24F + 0.24F * pulse, 0x8A2CFF), 5); // violet électrique
            // ondes d'énergie : un anneau se détache du front tous les 3 ticks et s'élargit en s'effaçant
            for (int w = 0; w < 3; w++) {
                float birth = (float) Math.floor(elapsed / 3.0F) * 3.0F - w * 3.0F;
                float r = elapsed - birth;
                if (birth < 0.0F || r > 7.0F) {
                    continue;
                }
                int step = (int) (r * 0.3F);
                int at = Mth.clamp(fromTop ? frontIndex - step : frontIndex + step, 0, n - 1);
                ring(collector, pose, parts, shape, at, 1.12F + r * 0.18F, 0.5F, argb(0.36F * (1.0F - r / 7.0F), 0x5A1AC8), 6); // violet sombre
            }
        }
        pose.popPose();
    }

    /**
     * Teinte d'une cellule (ARGB, texture blanche) : 0 si rien ; devant l'encre, seules les nervures s'assombrissent ; dans
     * l'encre, noir métallique (légères variations par cellule, faces avant plus brillantes), nervures en relief plus claires,
     * éclairci par la vague de flux et l'éclat du durcissement.
     */
    static int cellColor(float ink, float lead, float ridge, float grain, float along, float cover, float elapsed,
                                 boolean settling, float harden, float shimmer, int face, float edge, float height, float u,
                                 float age) {
        if (ink <= 0.01F) {
            // lead < 0 : la cellule n'est pas encore atteinte ; -lead = distance au front
            float near = 1.0F - Math.min(1.0F, -lead / VEIN_BAND);
            if (near <= 0.0F) {
                return 0;
            }
            float veinAlpha = ridge > 0.72F ? near * near * 0.6F : near * near * near * 0.12F;
            return argb(veinAlpha, 0x24183A);
        }
        float wave = 0.0F;
        if (settling) {
            float w = (elapsed * 0.9F) % (cover + 3.0F);
            wave = Math.max(0.0F, 1.0F - Math.abs(along - w) / 1.5F);
        }
        // NOIR MÉTALLIQUE POLI (et non du charbon mat) : surface lisse (très peu de grain), dégradé doux d'éclairage (face avant et haut des
        // membres plus clairs, comme le reflet du ciel), liseré lumineux sur les arêtes (effet de bord du métal) qui dessine les
        // contours du corps, nervures en relief, et deux reflets SPÉCULAIRES qui glissent : une bande nette et brillante et un
        // lustre large et doux, avec des reflets violacés.
        float faceLight = face == 0 ? 1.0F : face == 2 ? 0.5F : 0.72F;
        float e = Mth.clamp(edge, 0.0F, 1.0F);
        // NOIR profond (thème du Haki de l'Armement) : la base reste presque noire, seuls les reflets éclairent le métal
        float lum = 0.035F + 0.025F * faceLight + 0.025F * height + 0.006F * grain;
        lum *= 0.82F + 0.18F * (float) Math.sqrt(e);           // léger creux avant l'arête...
        if (e < 0.3F) {
            float rim = 1.0F - e / 0.3F;
            lum += (0.06F + 0.16F * rim * rim) * (0.55F + 0.45F * faceLight); // rim light : l'arête brille comme du métal
        }
        if (ridge > 0.78F) {
            lum += 0.13F * (ridge - 0.78F) / 0.22F;             // nervure en relief qui accroche la lumière
        } else if (ridge < 0.18F) {
            lum *= 0.88F;                                       // creux
        }
        // reflet net : bande étroite en diagonale qui défile ; lustre : vague large et douce
        float band = ((u * 0.35F + along * 0.6F - age * 0.12F) / 5.5F) % 1.0F;
        band = band < 0.0F ? band + 1.0F : band;
        float sharp = (float) Math.pow(Math.max(0.0F, 1.0F - Math.abs(band - 0.5F) * 6.0F), 2.0);
        // chrome sombre : comme un métal poli qui reflète l'horizon, une fine ligne claire horizontale barre chaque membre
        float h = (height - 0.64F) / 0.08F;
        float horizon = (float) Math.exp(-h * h) * faceLight;
        float broad = 0.5F + 0.5F * Mth.sin(u * 0.25F - along * 0.35F + age * 0.05F);
        float spec = (0.75F * sharp + 0.18F * broad * broad) * faceLight + 0.05F * shimmer * faceLight;
        float flash = Math.max(wave * 0.25F, harden * 0.35F);
        // reflets : brillance de laque noire (nette mais sombre), pour que le noir ne paraisse pas mat comme du charbon
        // reflets francs et contrastés sur un fond noir : alliage sombre et réfléchissant, pas une surface satinée
        lum = Mth.clamp(lum + 0.62F * sharp * faceLight + 0.12F * broad * broad * faceLight + 0.24F * horizon
                + 0.04F * shimmer * faceLight + flash * 0.8F, 0.0F, 1.0F);
        // couleur : noir à peine bleuté, qui vire au violet dans les reflets
        float violet = Mth.clamp(sharp * 1.2F + flash, 0.0F, 1.0F);
        float rr = lum * (0.86F - 0.06F * violet);
        float gg = lum * (0.86F - 0.26F * violet);
        float bb = lum * (1.0F + 0.16F * violet);
        int rgb = ((int) (Mth.clamp(rr, 0.0F, 1.0F) * 255) << 16) | ((int) (Mth.clamp(gg, 0.0F, 1.0F) * 255) << 8)
                | (int) (Mth.clamp(bb, 0.0F, 1.0F) * 255);
        return argb(ink, rgb);
    }

    /**
     * Anneau lumineux autour de la tranche {@code k} : ses 4 plaques agrandies autour du centre du membre (plus larges que la
     * gaine : jamais superposées à elle, et sans faces internes).
     */
    private static void ring(SubmitNodeCollector collector, PoseStack pose, ModelPart[][] parts, Shape shape, int k, float scale,
                             float thick, int color, int order) {
        float cy = (shape.top + k + 0.5F) / 16.0F;
        pose.pushPose();
        pose.translate(0.0F, cy, 0.0F);
        pose.scale(scale, thick, scale);
        pose.translate(0.0F, -cy, 0.0F);
        for (int f = 0; f < FACES; f++) {
            collector.order(order).submitModelPart(parts[k][f], pose, RenderTypes.entityTranslucent(WHITE), FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, null, color);
        }
        pose.popPose();
    }

    /** La main vue à la première personne : bras principal dès l'appui, l'autre bras en mode corps entier. */
    public static void submitHand(PoseStack pose, SubmitNodeCollector collector, ModelPart arm, Identifier skin, Entity player,
                                  boolean isMainArm, boolean rightArm, PlayerModel model, float age) {
        if (plates == null) {
            return;
        }
        float[] progress = HakiClientState.armament(player, age);
        if (progress == null) {
            return;
        }
        boolean slim = isSlim(model);
        Shape shape = slim ? Shape.ARM_SLIM : Shape.ARM_WIDE;
        float cover = isMainArm ? mainArmCover(progress)
                : progress[3] < 0.0F ? 0.0F : window(progress[1], 0.34F, 0.80F) * 12.0F;
        part(collector, pose, arm, shape, armOffset(rightArm, slim), cover, !isMainArm, progress, FULL_BRIGHT, age, player.getId());
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
