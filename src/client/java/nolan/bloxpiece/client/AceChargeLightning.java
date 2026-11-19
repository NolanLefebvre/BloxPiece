package nolan.bloxpiece.client;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.AceSlash;
import nolan.bloxpiece.item.AceItem;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Éclairs du Haki des Rois DANS LA LAME d'Ace pendant la frappe de la Lame dévastatrice (chargée ou non) : dessinés dans le repère même de l'objet tenu (comme le revêtement du Haki de l'Armement, HakiItemCoat), donc collés
 * à la lame et orientés comme elle, à la première et à la troisième personne.
 * <ul>
 *   <li>décharges qui parcourent la lame juste après la garde jusqu'à la pointe (tracé brisé le long de son axe) ;</li>
 *   <li>petites fourches qui jaillissent de la lame, tout près d'elle, plus nombreuses vers la pointe ;</li>
 *   <li>rien sur la poignée ni sur la garde : toute l'énergie sort de la lame ;</li>
 *   <li>tout crépite (tracés tirés à nouveau tous les 2 ticks) et s'intensifie avec la charge (nombre, épaisseur, éclat).</li>
 * </ul>
 * Rendu des éclairs : celui du Haki des Rois (HakiBoltRenderer) : coeur noir, gaine rouge vif translucide.
 */
public final class AceChargeLightning {

    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/haki_eye_trail.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int CORE = 0x050001;
    private static final int SHEATH = 0xFF1A1A;
    /** Axe de la lame dans le modèle d'Ace (pixels du modèle, de la garde à la pointe ; lu dans models/item/ace.json). */
    private static final float[][] BLADE = {
            {4.6F, 4.6F}, {6.98F, 6.99F}, {8.88F, 8.93F}, {10.74F, 10.9F}, {12.55F, 12.93F}, {14.28F, 15.02F},
            {15.91F, 17.19F}, {17.3F, 19.3F}, {18.3F, 21.4F}, {18.8F, 23.45F}};
    /** Début des éclairs sur la lame (0 = garde, 1 = pointe) : un peu après la garde, jamais sur la poignée. */
    private static final float BLADE_START = 0.12F;
    private static final float Z = 8.0F;

    private static ModelPart segment;

    private AceChargeLightning() {
    }

    private static ModelPart segment() {
        if (segment == null) {
            segment = nolan.bloxpiece.client.haki.HakiBoltRenderer.BoltModel.createLayer().bakeRoot().getChild("segment");
        }
        return segment;
    }

    /** Intensité des éclairs (0 = aucun) : pendant la frappe seulement (0,6, qui s'éteint juste après le lancer). Pendant la
     *  charge, ce sont les éclairs du Haki des Rois qui jaillissent de la lame (AceItem, envoyés par le serveur). */
    public static float intensity(LivingEntity entity, float now) {
        if (!(entity.getMainHandItem().getItem() instanceof AceItem)) {
            return 0.0F;
        }
        Integer strike = entity.getAttached(ModAttachments.ACE_SLASH);
        if (strike != null) {
            float t = now - strike;
            float end = AceSlash.STRIKE_TICKS + 3.0F;
            if (t > -AceSlash.RAISE_TICKS - 1.0F && t < end) {
                return 0.6F * Math.min(1.0F, (end - t) / 3.0F);
            }
        }
        return 0.0F;
    }

    /** 3e personne : depuis le repère du modèle du joueur (couche de rendu), comme HakiItemCoat.render. */
    public static void renderThirdPerson(PoseStack pose, SubmitNodeCollector collector, ModelPart arm, LivingEntity entity, boolean right,
                                         boolean slim, float now) {
        float intensity = intensity(entity, now);
        if (intensity <= 0.0F || !arm.visible) {
            return;
        }
        pose.pushPose();
        // 1) comme la couche des objets en main de Minecraft : main du bras, puis placement de l'objet dans la main
        float slimShift = slim ? 0.5F * (right ? 1.0F : -1.0F) : 0.0F;
        arm.x += slimShift;
        arm.translateAndRotate(pose);
        arm.x -= slimShift;
        pose.rotateDegrees(Axis.XP, -90.0F);
        pose.rotateDegrees(Axis.YP, 180.0F);
        pose.translate((right ? 1.0F : -1.0F) / 16.0F, 0.125F, -0.625F);
        // 2) transformation « tenu en main, 3e personne » du modèle d'Ace (models/item/ace.json ; miroir pour la main gauche)
        float side = right ? 1.0F : -1.0F;
        display(pose, 0.0F, 5.56F, 0.05F, -180.0F, 90.0F * side, -145.0F * side, 0.85F);
        draw(pose, collector, entity, intensity, now);
        pose.popPose();
    }

    /** 1re personne : repère de l'objet à l'écran, avant sa transformation d'affichage (voir ItemInHandHakiMixin). */
    public static void renderFirstPerson(PoseStack pose, SubmitNodeCollector collector, LivingEntity entity, boolean right, float now) {
        float intensity = intensity(entity, now);
        if (intensity <= 0.0F) {
            return;
        }
        float side = right ? 1.0F : -1.0F;
        pose.pushPose();
        display(pose, 1.13F * side, 2.3F, 0.12F, -180.0F, 90.0F * side, -125.0F * side, 0.42F);
        draw(pose, collector, entity, intensity, now);
        pose.popPose();
    }

    /** Transformation d'affichage d'un modèle d'objet (comme Minecraft : déplacement, rotation X Y Z, échelle, puis centrage). */
    private static void display(PoseStack pose, float tx, float ty, float tz, float rx, float ry, float rz, float scale) {
        pose.translate(tx / 16.0F, ty / 16.0F, tz / 16.0F);
        pose.rotateDegrees(Axis.XP, rx);
        pose.rotateDegrees(Axis.YP, ry);
        pose.rotateDegrees(Axis.ZP, rz);
        pose.scale(scale, scale, scale);
        pose.translate(-0.5F, -0.5F, -0.5F);
    }

    /** Les éclairs, en pixels du modèle d'Ace (pose : 1 bloc = 16 pixels du modèle). */
    private static void draw(PoseStack pose, SubmitNodeCollector collector, LivingEntity entity, float intensity, float now) {
        RenderType type = RenderTypes.entityTranslucent(TEXTURE);
        int crackle = (int) (now / 2.0F);
        Random random = new Random(entity.getId() * 31L + crackle * 7_919L);
        float alpha = Math.min(1.0F, 0.5F + 0.5F * intensity);
        // 1) décharges qui parcourent la lame : de la garde vers la pointe, la tête avance (elle « court » sur la lame)
        int runners = 1 + Math.round(2.0F * intensity);
        for (int r = 0; r < runners; r++) {
            float head = ((now * 0.35F + r / (float) runners) % 1.0F) * 1.4F;   // 0 = garde, 1 = pointe (puis repart)
            float tail = head - 0.55F;
            float[] prev = null;
            for (int i = 0; i <= 14; i++) {
                float u = BLADE_START + (1.0F - BLADE_START) * i / 14.0F;
                if (u < tail || u > head) {
                    prev = null;
                    continue;
                }
                float[] p = blade(u);
                float jag = (random.nextFloat() - 0.5F) * 1.1F * (0.5F + intensity);
                float[] q = {p[0] - jag * 0.707F, p[1] + jag * 0.707F, Z + (random.nextFloat() - 0.5F) * 0.8F};
                if (prev != null) {
                    bolt(pose, collector, type, prev, q, 0.35F + 0.35F * intensity, alpha);
                }
                prev = q;
            }
        }
        // 2) petites fourches qui jaillissent de la lame, tout près d'elle
        int forks = 2 + Math.round(6.0F * intensity);
        for (int f = 0; f < forks; f++) {
            float[] at = blade(BLADE_START + (1.0F - BLADE_START) * (float) Math.sqrt(random.nextFloat())); // plus vers la pointe
            float[] p = {at[0], at[1], Z};
            double angle = random.nextDouble() * Math.PI * 2.0;
            int steps = 2 + random.nextInt(2);
            float len = 0.7F + random.nextFloat() * (0.6F + 1.4F * intensity);
            for (int k = 0; k < steps; k++) {
                angle += (random.nextDouble() - 0.5) * 1.6;
                float[] n = {p[0] + (float) Math.cos(angle) * len, p[1] + (float) Math.sin(angle) * len,
                        p[2] + (random.nextFloat() - 0.5F) * len * 1.2F};
                bolt(pose, collector, type, p, n, (0.3F + 0.25F * intensity) * (1.0F - k * 0.3F), alpha);
                p = n;
            }
        }
    }

    /** Point de l'axe de la lame, u de 0 (garde) à 1 (pointe), en pixels du modèle. */
    private static float[] blade(float u) {
        float f = Math.max(0.0F, Math.min(1.0F, u)) * (BLADE.length - 1);
        int i = Math.min(BLADE.length - 2, (int) f);
        float r = f - i;
        return new float[] {BLADE[i][0] + (BLADE[i + 1][0] - BLADE[i][0]) * r, BLADE[i][1] + (BLADE[i + 1][1] - BLADE[i][1]) * r};
    }

    /** Un tronçon d'éclair (pixels du modèle) : coeur noir puis gaine rouge vif. */
    private static void bolt(PoseStack pose, SubmitNodeCollector collector, RenderType type, float[] a, float[] b, float width, float alpha) {
        segment(pose, collector, 4, type, a, b, width, argb(alpha, CORE));
        segment(pose, collector, 5, type, a, b, width * 1.9F, argb(0.5F * alpha, SHEATH));
    }

    /** Prisme de section {@code width} de a vers b (pixels du modèle), prolongé d'une demi-largeur aux deux bouts. */
    private static void segment(PoseStack pose, SubmitNodeCollector collector, int order, RenderType type, float[] a, float[] b, float width,
                                int color) {
        float dx = (b[0] - a[0]) / 16.0F, dy = (b[1] - a[1]) / 16.0F, dz = (b[2] - a[2]) / 16.0F;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5F) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float overlap = width * 0.5F / 16.0F;
        pose.pushPose();
        pose.translate(a[0] / 16.0F, a[1] / 16.0F, a[2] / 16.0F);
        pose.rotateDegrees(Axis.YP, yaw);
        pose.rotateDegrees(Axis.XP, pitch);
        pose.translate(0.0F, 0.0F, -overlap);
        pose.scale(width, width, (len + 2.0F * overlap) * 16.0F);
        collector.order(order).submitModelPart(segment(), pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
