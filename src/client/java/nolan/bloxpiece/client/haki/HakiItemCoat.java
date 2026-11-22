package nolan.bloxpiece.client.haki;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import nolan.bloxpiece.haki.ArmamentHaki;

/**
 * Haki de l'Armement sur l'OBJET TENU (arme, outil...) : le revêtement noir métallique part de la poignée (la main) et se
 * répand sur l'objet avec la même encre organique que sur le corps (cellules d'un pixel, tache de bruit à grappes, nervures
 * devant le front, reflets d'acier), puis reflue de la même façon quand le Haki est retiré.
 *
 * Arme en 3D (modèle à « elements » : Ace, épées des bandits, sabres...) : sa LAME est lue dans son modèle et recouverte sur
 * toutes ses faces (HakiShell : convention "#blade" / "bloxpiece_part", valable pour toute future arme), jamais la poignée.
 * Objet plat : sa forme exacte est lue dans sa texture (assets/&lt;mod&gt;/textures/item/&lt;id&gt;.png) : seuls ses pixels visibles sont
 * recouverts, sur ses deux faces ET ses tranches, juste au-dessus de sa surface. Sa position dans la main reprend le placement de Minecraft (couche
 * des objets en main + transformation « tenu en main » de son modèle). Objets en 3D (blocs, bouclier, trident...) ou sans
 * texture plate : pas de revêtement visuel (le bonus de dégâts s'applique quand même, voir ArmamentHaki).
 * Changement d'objet ou de case : le nouvel objet se recouvre à son tour depuis la main.
 */
public final class HakiItemCoat {

    /** Durée de la propagation sur l'objet et de son retrait (ticks). */
    private static final float SPREAD_TICKS = 16.0F;
    private static final float RELEASE_TICKS = 10.0F;
    /** Décalage des plaques au-dessus des faces de l'objet (px) : pas de clignotement avec l'objet lui-même. */
    private static final float LIFT = 0.07F;

    /**
     * Forme d'un objet : pixels visibles (16 x 16), objet tenu « comme un outil » (modèle handheld) ou non ; ou, pour une arme en
     * 3D, sa LAME lue dans son modèle ({@code shell}, voir HakiShell : toutes les faces de la lame, jamais la poignée).
     */
    private record Mask(boolean[][] solid, boolean handheld, boolean rod, float maxDistance, HakiShell.Shell shell) {
    }

    /** Revêtement de l'objet d'une entité : objet, début, retrait (-1 = pas de retrait) et couverture au retrait. */
    private record Coat(Item item, ItemStack stack, float start, float releaseStart, float coverAtRelease) {
    }

    /**
     * Objets LÂCHÉS encore recouverts (changement de case, objet déplacé dans l'inventaire) : leur revêtement reflue sur leur
     * icône jusqu'à disparaître. Repérés par l'objet lui-même (même instance que la case de l'inventaire).
     */
    private record Released(Mask mask, int seed, float releaseStart, float coverAtRelease) {
    }

    private static final Map<ItemStack, Released> RELEASED = new java.util.IdentityHashMap<>();

    private static final Map<Item, Optional<Mask>> MASKS = new HashMap<>();
    private static final Map<Integer, Coat> COATS = new HashMap<>();

    private HakiItemCoat() {
    }

    /** Point de départ de l'encre dans la texture : la poignée, en bas à gauche des objets tenus comme un outil. */
    private static final float GRIP_X = 1.5F;
    private static final float GRIP_Y = 14.5F;

    private static Optional<Mask> mask(Item item) {
        return MASKS.computeIfAbsent(item, HakiItemCoat::loadMask);
    }

    private static Optional<Mask> loadMask(Item item) {
        try {
            Identifier key = BuiltInRegistries.ITEM.getKey(item);
            // arme en 3D (modèle à « elements ») : sa lame, toutes faces (convention commune : HakiShell)
            Optional<HakiShell.Shell> shell = HakiShell.load(key);
            if (shell.isPresent()) {
                return Optional.of(new Mask(null, false, false, shell.get().maxDistance(), shell.get()));
            }
            Identifier texture = Identifier.fromNamespaceAndPath(key.getNamespace(), "textures/item/" + key.getPath() + ".png");
            var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isEmpty()) {
                return Optional.empty();
            }
            BufferedImage image;
            try (InputStream in = resource.get().open()) {
                image = ImageIO.read(in);
            }
            if (image == null || image.getWidth() < 16 || image.getHeight() < image.getWidth()) {
                return Optional.empty();
            }
            int size = image.getWidth(); // textures animées : on ne lit que la première image (carrée)
            boolean[][] solid = new boolean[16][16];
            int count = 0;
            float max = 0.0F;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int px = x * size / 16 + size / 32, py = y * size / 16 + size / 32;
                    int alpha = (image.getRGB(Math.min(size - 1, px), Math.min(size - 1, py)) >>> 24) & 0xFF;
                    if (alpha > 16) {
                        solid[x][y] = true;
                        count++;
                        max = Math.max(max, (float) Math.hypot(x + 0.5F - GRIP_X, y + 0.5F - GRIP_Y));
                    }
                }
            }
            if (count == 0) {
                return Optional.empty();
            }
            // modèle de l'objet : « handheld » (outils, armes) ou objet plat ordinaire
            boolean handheld = false;
            boolean rod = false;
            Identifier model = Identifier.fromNamespaceAndPath(key.getNamespace(), "models/item/" + key.getPath() + ".json");
            var json = Minecraft.getInstance().getResourceManager().getResource(model);
            if (json.isPresent()) {
                try (InputStream in = json.get().open()) {
                    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    handheld = text.contains("handheld");
                    rod = text.contains("handheld_rod");
                }
            }
            return Optional.of(new Mask(solid, handheld, rod, max, null));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Couverture actuelle (px depuis la poignée) de l'objet de cette entité, ou -1 sans revêtement. */
    private static float spreadCover(Coat coat, Mask mask, float age) {
        return HakiArmamentLayer.flow((age - coat.start()) / SPREAD_TICKS) * (mask.maxDistance() + 4.5F);
    }

    /** L'objet {@code coat} vient d'être lâché alors qu'il était recouvert : son revêtement reflue sur son icône. */
    private static void releaseDropped(LivingEntity entity, Coat coat, float age) {
        if (coat == null || coat.stack() == null || entity != Minecraft.getInstance().player) {
            return;
        }
        Optional<Mask> m = mask(coat.item());
        if (m.isEmpty() || m.get().shell() != null) {
            return; // arme en 3D : pas d'icône plate à recouvrir
        }
        float now = coat.releaseStart() >= 0.0F
                ? coat.coverAtRelease() * (1.0F - HakiArmamentLayer.flow((age - coat.releaseStart()) / RELEASE_TICKS))
                : spreadCover(coat, m.get(), age);
        if (now > 0.0F) {
            RELEASED.put(coat.stack(), new Released(m.get(), seedOf(entity, coat.item()), age, now));
        }
    }

    private static int seedOf(LivingEntity entity, Item item) {
        return entity.getId() * 31 + BuiltInRegistries.ITEM.getId(item);
    }

    /** Couverture actuelle (px depuis la poignée) de l'objet en main de cette entité, ou -1 sans revêtement. */
    private static float cover(LivingEntity entity, ItemStack stack, Item held, boolean active, Mask mask, float age) {
        int id = entity.getId();
        Coat coat = COATS.get(id);
        if (coat != null && coat.item() != held) {
            // changement d'objet en main (case, inventaire) : l'ancien reflue sur son icône, le nouveau repart de la poignée
            releaseDropped(entity, coat, age);
            COATS.remove(id);
            coat = null;
        } else if (coat != null && coat.stack() != stack) {
            // même objet, mais nouvelle instance (synchronisation du serveur, usure...) : on garde l'animation en cours
            coat = new Coat(coat.item(), stack, coat.start(), coat.releaseStart(), coat.coverAtRelease());
            COATS.put(id, coat);
        }
        if (held == null) {
            return -1.0F;
        }
        RELEASED.remove(stack); // repris en main : il n'est plus « lâché »
        if (active) {
            if (coat == null || coat.releaseStart() >= 0.0F) {
                coat = new Coat(held, stack, age, -1.0F, 0.0F);
                COATS.put(id, coat);
            }
            return spreadCover(coat, mask, age);
        }
        if (coat == null) {
            return -1.0F;
        }
        if (coat.releaseStart() < 0.0F) {
            coat = new Coat(coat.item(), stack, coat.start(), age, spreadCover(coat, mask, age));
            COATS.put(id, coat);
        }
        float t = (age - coat.releaseStart()) / RELEASE_TICKS;
        if (t >= 1.0F) {
            COATS.remove(id);
            return -1.0F;
        }
        return coat.coverAtRelease() * (1.0F - HakiArmamentLayer.flow(t));
    }

    /** Couleurs (ARGB, 0 = rien) des 16 x 16 pixels de l'objet à cet instant (objet plat), ou de chaque cellule de la lame (arme
     * en 3D : {@code shellColors}), et sa forme. */
    private record Frame(int[][] colors, int[] shellColors, Mask mask) {
    }

    /**
     * Revêtement de l'objet en main principale de {@code entity} à l'instant {@code age} (ticks), ou null s'il n'y en a pas.
     * Même calcul pour la 3e personne, la 1re personne et l'icône : les trois montrent exactement la même encre.
     */
    private static Frame frame(LivingEntity entity, float age) {
        ItemStack stack = entity.getMainHandItem();
        Item held = ArmamentHaki.isImbued(entity, stack) ? stack.getItem() : null;
        Optional<Mask> found = held == null ? Optional.empty() : mask(held);
        if (found.isEmpty()) {
            // plus d'objet recouvrable en main : si l'ancien était recouvert, il reflue sur son icône
            Coat old = COATS.remove(entity.getId());
            if (old != null) {
                releaseDropped(entity, old, age);
            }
            return null;
        }
        Mask mask = found.get();
        boolean active = ArmamentHaki.mode(entity) != 0;
        float cover = cover(entity, stack, held, active, mask, age);
        if (cover <= 0.0F) {
            return null;
        }
        int seed = seedOf(entity, held);
        if (mask.shell() != null) {
            return new Frame(null, shellColors(mask, cover, seed, !active, age), mask);
        }
        return new Frame(colors(mask, cover, seed, !active, age), null, mask);
    }

    /** Couleur de chaque cellule de la lame d'une arme en 3D : même encre, même propagation depuis la main que sur un objet plat. */
    private static int[] shellColors(Mask mask, float cover, int seed, boolean releasing, float age) {
        float shimmer = 0.5F + 0.5F * Mth.sin(age * 0.25F);
        float motion = releasing ? 1.0F : 1.0F - Mth.clamp(cover / (mask.maxDistance() + 4.5F), 0.0F, 1.0F);
        java.util.List<HakiShell.Cell> cells = mask.shell().cells();
        int[] colors = new int[cells.size()];
        for (int i = 0; i < colors.length; i++) {
            HakiShell.Cell c = cells.get(i);
            float u = c.u(), v = c.v();
            float blob = HakiArmamentLayer.noise(u, v, 3.0F, 1 << 20, seed) - 0.5F;
            float fine = HakiArmamentLayer.noise(u, v, 1.5F, 1 << 20, seed + 17) - 0.5F;
            float breathe = 0.25F * motion * Mth.sin(age * 0.6F + u * 0.7F + v * 0.5F);
            float delay = c.along() + 4.2F * blob + 1.3F * fine + breathe;
            float ink = Mth.clamp((cover - delay) / 0.9F + 0.5F, 0.0F, 1.0F);
            float ridge = 1.0F - Math.abs(2.0F * HakiArmamentLayer.noise(u * 1.3F, v * 0.7F, 2.0F, 1 << 20, seed + 31) - 1.0F);
            colors[i] = HakiArmamentLayer.cellColor(ink, cover - delay, ridge, HakiArmamentLayer.hash(c.grain(), 0, seed + 5),
                    c.along(), cover, 1000.0F, !releasing && motion > 0.0F, 0.0F, shimmer, 0, 1.0F,
                    Mth.clamp(1.0F - v / 32.0F, 0.0F, 1.0F), u + v, age);
        }
        return colors;
    }

    /** Couleurs des 16 x 16 pixels pour une couverture donnée (même encre que sur le corps). */
    private static int[][] colors(Mask mask, float cover, int seed, boolean releasing, float age) {
        float shimmer = 0.5F + 0.5F * Mth.sin(age * 0.25F);
        float motion = releasing ? 1.0F : 1.0F - Mth.clamp(cover / (mask.maxDistance() + 4.5F), 0.0F, 1.0F);
        int[][] colors = new int[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (!mask.solid()[x][y]) {
                    continue;
                }
                float u = x + 0.5F, v = y + 0.5F;
                float along = (float) Math.hypot(u - GRIP_X, v - GRIP_Y);
                float blob = HakiArmamentLayer.noise(u, v, 3.0F, 1 << 20, seed) - 0.5F;
                float fine = HakiArmamentLayer.noise(u, v, 1.5F, 1 << 20, seed + 17) - 0.5F;
                float breathe = 0.25F * motion * Mth.sin(age * 0.6F + u * 0.7F + v * 0.5F);
                float delay = along + 4.2F * blob + 1.3F * fine + breathe;
                float ink = Mth.clamp((cover - delay) / 0.9F + 0.5F, 0.0F, 1.0F);
                float ridge = 1.0F - Math.abs(2.0F * HakiArmamentLayer.noise(u * 1.3F, v * 0.7F, 2.0F, 1 << 20, seed + 31) - 1.0F);
                float edge = Math.min(1.0F, Math.min(Math.min(x, 15 - x), Math.min(y, 15 - y)) / 4.0F + 0.25F);
                colors[x][y] = HakiArmamentLayer.cellColor(ink, cover - delay, ridge, HakiArmamentLayer.hash(x, y, seed + 5), along,
                        cover, 1000.0F, !releasing && motion > 0.0F, 0.0F, shimmer, 0, edge, 1.0F - v / 16.0F, u + v, age);
            }
        }
        return colors;
    }

    /**
     * 3e personne : depuis le repère du modèle du joueur (couche de rendu). {@code arm} : bras principal ; {@code right} : main
     * droite ; {@code slim} : skin à bras fins.
     */
    public static void render(PoseStack pose, SubmitNodeCollector collector, ModelPart arm, LivingEntity entity, boolean right,
                              boolean slim, int light, float age) {
        if (HakiArmamentLayer.cellXY == null || !arm.visible) {
            return;
        }
        Frame frame = frame(entity, age);
        if (frame == null) {
            return;
        }
        Mask mask = frame.mask();
        pose.pushPose();
        // 1) comme la couche des objets en main de Minecraft : main du bras, puis placement de l'objet dans la main
        float slimShift = slim ? 0.5F * (right ? 1.0F : -1.0F) : 0.0F;
        arm.x += slimShift;
        arm.translateAndRotate(pose);
        arm.x -= slimShift;
        pose.rotateDegrees(Axis.XP, -90.0F);
        pose.rotateDegrees(Axis.YP, 180.0F);
        pose.translate((right ? 1.0F : -1.0F) / 16.0F, 0.125F, -0.625F);
        // 2) transformation « tenu en main, 3e personne » du modèle de l'objet (miroir pour la main gauche)
        float side = right ? 1.0F : -1.0F;
        if (mask.shell() != null) {
            // arme en 3D : transformation « tenu en main, 3e personne » de SON modèle, puis sa lame
            (right ? mask.shell().thirdRight() : mask.shell().thirdLeft()).apply(pose, !right);
            HakiShell.draw(pose, collector, mask.shell(), frame.shellColors(), light);
            pose.popPose();
            return;
        }
        if (mask.rod()) {
            pose.translate(0.0F, 4.0F / 16.0F, 2.5F / 16.0F);
            rotate(pose, 0.0F, 90.0F * side, 55.0F * side);
            pose.scale(0.85F, 0.85F, 0.85F);
        } else if (mask.handheld()) {
            pose.translate(0.0F, 4.0F / 16.0F, 0.5F / 16.0F);
            rotate(pose, 0.0F, -90.0F * side, 55.0F * side);
            pose.scale(0.85F, 0.85F, 0.85F);
        } else {
            pose.translate(0.0F, 3.0F / 16.0F, 1.0F / 16.0F);
            pose.scale(0.55F, 0.55F, 0.55F);
        }
        cells(pose, collector, frame, light);
        pose.popPose();
    }

    /**
     * 1re personne : appelé par ItemInHandHakiMixin juste après l'envoi de l'objet en main au rendu (repère de l'objet à l'écran,
     * avant sa transformation d'affichage, appliquée ici comme le fait Minecraft).
     */
    public static void renderFirstPerson(PoseStack pose, SubmitNodeCollector collector, LivingEntity entity, boolean right,
                                         int light) {
        if (HakiArmamentLayer.cellXY == null) {
            return;
        }
        float age = entity.tickCount + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Frame frame = frame(entity, age);
        if (frame == null) {
            return;
        }
        float side = right ? 1.0F : -1.0F;
        pose.pushPose();
        if (frame.mask().shell() != null) {
            // arme en 3D : transformation « tenu en main, 1re personne » de SON modèle, puis sa lame
            HakiShell.Shell shell = frame.mask().shell();
            (right ? shell.firstRight() : shell.firstLeft()).apply(pose, !right);
            HakiShell.draw(pose, collector, shell, frame.shellColors(), light);
            pose.popPose();
            return;
        }
        // transformation « tenu en main, 1re personne » (identique pour les outils et les objets plats)
        pose.translate(side * 1.13F / 16.0F, 3.2F / 16.0F, 1.13F / 16.0F);
        rotate(pose, 0.0F, (frame.mask().rod() ? 90.0F : -90.0F) * side, 25.0F * side);
        pose.scale(0.68F, 0.68F, 0.68F);
        cells(pose, collector, frame, light);
        pose.popPose();
    }

    /**
     * Icône (barre d'objets, inventaire...) : appelé par GuiItemHakiMixin après le dessin d'un objet. Seulement pour l'objet en
     * main principale du joueur (le même objet), pixel par pixel sur son icône.
     */
    public static void renderIcon(net.minecraft.client.gui.GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        LivingEntity player = Minecraft.getInstance().player;
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        boolean inHand = stack == player.getMainHandItem();
        if (!inHand && !RELEASED.containsKey(stack)) {
            return;
        }
        float age = player.tickCount + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        // une variante de dessin peut en appeler une autre : une seule peinture par icône et par image
        if (stack == lastIconStack && x == lastIconX && y == lastIconY && age == lastIconAge) {
            return;
        }
        lastIconStack = stack;
        lastIconX = x;
        lastIconY = y;
        lastIconAge = age;
        Frame frame = inHand ? frame(player, age) : released(stack, age);
        if (frame == null || frame.colors() == null) {
            return; // arme en 3D : son icône n'est pas recouverte (pas de forme plate)
        }
        for (int py = 0; py < 16; py++) {
            for (int px = 0; px < 16; px++) {
                int color = frame.colors()[px][py];
                if ((color >>> 24) != 0) {
                    g.fill(x + px, y + py, x + px + 1, y + py + 1, color);
                }
            }
        }
    }

    /** Revêtement qui reflue sur l'icône d'un objet lâché, ou null quand il a disparu. */
    private static Frame released(ItemStack stack, float age) {
        Released rel = RELEASED.get(stack);
        if (rel == null) {
            return null;
        }
        float t = (age - rel.releaseStart()) / RELEASE_TICKS;
        if (t >= 1.0F || t < -2.0F) {
            RELEASED.remove(stack);
            return null;
        }
        float cover = rel.coverAtRelease() * (1.0F - HakiArmamentLayer.flow(Math.max(0.0F, t)));
        return cover <= 0.0F ? null : new Frame(colors(rel.mask(), cover, rel.seed(), true, age), null, rel.mask());
    }

    private static ItemStack lastIconStack;
    private static int lastIconX;
    private static int lastIconY;
    private static float lastIconAge = -1.0F;

    /**
     * Objet plat : une cellule par pixel recouvert sur ses deux faces (1 px d'épaisseur, centré), juste au-dessus de lui, plus ses
     * tranches (bords du pixel qui donnent sur le vide).
     */
    private static void cells(PoseStack pose, SubmitNodeCollector collector, Frame frame, int light) {
        pose.translate(-0.5F, -0.5F, -0.5F);
        RenderType type = RenderTypes.entityTranslucent(HakiArmamentLayer.WHITE_TEXTURE);
        ModelPart cell = HakiArmamentLayer.cellXY;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = frame.colors()[x][y];
                if ((color >>> 24) == 0) {
                    continue;
                }
                for (int face = 0; face < 2; face++) {
                    float z = face == 0 ? 8.5F + LIFT : 7.5F - LIFT;
                    pose.pushPose();
                    pose.translate(x / 16.0F, (15 - y) / 16.0F, z / 16.0F);
                    collector.order(3).submitModelPart(cell, pose, type, light, OverlayTexture.NO_OVERLAY, null, color);
                    pose.popPose();
                }
                // TRANCHES de l'objet plat (épaisseur d'un pixel, z 7,5 à 8,5) : chaque bord du pixel qui donne sur le vide (comme
                // les côtés que Minecraft extrude) est recouvert aussi ; débord de LIFT aux deux bouts pour fermer les arêtes
                float depth = 1.0F + 2.0F * LIFT;
                for (int side = 0; side < 4; side++) {
                    int nx = x + (side == 0 ? -1 : side == 1 ? 1 : 0), ny = y + (side == 2 ? -1 : side == 3 ? 1 : 0);
                    if (nx >= 0 && nx < 16 && ny >= 0 && ny < 16 && frame.mask().solid()[nx][ny]) {
                        continue; // bord intérieur : caché par le pixel voisin
                    }
                    pose.pushPose();
                    switch (side) {
                        case 0, 1 -> { // gauche / droite : plan vertical ZY
                            float px = side == 0 ? x - LIFT : x + 1 + LIFT;
                            pose.translate(px / 16.0F, (15 - y) / 16.0F, (7.5F - LIFT) / 16.0F);
                            pose.rotateDegrees(Axis.YP, -90.0F);
                            pose.scale(depth, 1.0F, 1.0F);
                        }
                        default -> { // haut / bas (la texture a l'axe y vers le bas) : plan horizontal XZ
                            float py = side == 2 ? 16 - y + LIFT : 15 - y - LIFT;
                            pose.translate(x / 16.0F, py / 16.0F, (7.5F - LIFT) / 16.0F);
                            pose.rotateDegrees(Axis.XP, 90.0F);
                            pose.scale(1.0F, depth, 1.0F);
                        }
                    }
                    collector.order(3).submitModelPart(cell, pose, type, light, OverlayTexture.NO_OVERLAY, null, color);
                    pose.popPose();
                }
            }
        }
    }

    /** Rotations X, puis Y, puis Z (degrés), comme les transformations d'affichage des modèles d'objets. */
    private static void rotate(PoseStack pose, float x, float y, float z) {
        pose.rotateDegrees(Axis.XP, x);
        pose.rotateDegrees(Axis.YP, y);
        pose.rotateDegrees(Axis.ZP, z);
    }

    /**
     * Chaque tick client : met à jour l'état du revêtement de l'objet en main du joueur, même quand aucune icône n'est dessinée
     * (passage sur une case vide) : l'objet lâché commence alors bien à refluer sur son icône.
     */
    public static void tick(Minecraft client) {
        if (client.player != null && client.level != null && !client.isPaused()) {
            frame(client.player, client.player.tickCount);
        }
        if (client.player == null) {
            RELEASED.clear();
        }
    }

    public static void clear() {
        COATS.clear();
        RELEASED.clear();
        MASKS.clear();
    }
}
