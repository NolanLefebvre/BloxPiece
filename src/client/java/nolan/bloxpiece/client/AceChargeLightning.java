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
 * Éclairs du Haki des Rois sur la lame d'Ace, pendant la CHARGE et pendant la FRAPPE (chargée ou non) : un seul type d'éclair,
 * qui JAILLIT de la lame (voir bursts), dessiné dans le repère même de l'objet tenu (comme le revêtement du Haki de l'Armement,
 * HakiItemCoat) : il part donc exactement de la lame, à la première et à la troisième personne.
 * <ul>
 *   <li>mêmes éclairs dans les deux cas (même tracé, même texture, coeur noir et gaine rouge vif du Haki des Rois) ; seuls leur
 *       fréquence et leur durée changent : charge = de plus en plus nombreux ; frappe = une salve brève et dense ;</li>
 *   <li>petites décharges compactes et fines (0,3 à 0,8 bloc environ), aux formes brisées, nombreuses plutôt qu'énormes ;</li>
 *   <li>nées sur la lame seulement (jamais sur la poignée ni la garde) ; elles crépitent (brisures tirées à nouveau tous les
 *       2 ticks).</li>
 * </ul>
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

    /** Charge en cours : niveau de 0 à 1 (-1 = pas de charge). */
    public static float chargeLevel(LivingEntity entity, float now) {
        if (!(entity.getMainHandItem().getItem() instanceof AceItem)) {
            return -1.0F;
        }
        Integer charge = entity.getAttached(ModAttachments.ACE_CHARGE);
        if (charge == null || (!entity.isUsingItem() && entity instanceof net.minecraft.world.entity.player.Player)
                || now - charge < AceSlash.RAISE_TICKS) { // (un mob comme Roger charge sans « utiliser » l'objet)
            return -1.0F; // pas de charge, ou l'épée se lève encore
        }
        return Math.min(1.0F, (now - charge - AceSlash.RAISE_TICKS) / AceSlash.MAX_CHARGE_TICKS);
    }

    /** Intensité des éclairs de frappe (0 = aucun) : 0,6, qui s'éteint juste après le lancer. */
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
        float charge = chargeLevel(entity, now);
        if ((intensity <= 0.0F && charge < 0.0F) || !arm.visible) {
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
        bursts(pose, collector, entity, charge, intensity, now, 16.0F / (0.85F * 0.9375F)); // éclairs à leur vraie taille
        pose.popPose();
    }

    /** 1re personne : repère de l'objet à l'écran, avant sa transformation d'affichage (voir ItemInHandHakiMixin). */
    public static void renderFirstPerson(PoseStack pose, SubmitNodeCollector collector, LivingEntity entity, boolean right, float now) {
        float intensity = intensity(entity, now);
        float charge = chargeLevel(entity, now);
        if (intensity <= 0.0F && charge < 0.0F) {
            return;
        }
        float side = right ? 1.0F : -1.0F;
        pose.pushPose();
        display(pose, 1.13F * side, 2.3F, 0.12F, -180.0F, 90.0F * side, -125.0F * side, 0.42F);
        bursts(pose, collector, entity, charge, intensity, now, 14.0F); // un peu plus courts à l'écran : ils restent visibles
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

    /** Début des éclairs qui jaillissent (0 = garde, 1 = pointe) : toujours sur la lame. */
    private static final float BURST_START = 0.2F;
    /** Ticks d'éclairs déjà nés encore visibles (vie maximale d'un éclair). */
    private static final int BURST_WINDOW = 8;

    /**
     * Éclairs du Haki des Rois qui JAILLISSENT de la lame, comme les éclairs HakiBolt (même cycle de vie que HakiBoltRenderer : la
     * tête jaillit, l'éclair file vers l'extérieur, la queue suit, puis il s'éteint), mais petits et nés sur la lame même, dans
     * le repère de l'épée. Tirage stable par tick de naissance (le même pour tous les joueurs).
     * <ul>
     *   <li>charge ({@code charge} de 0 à 1) : à chaque tick, 1 à 3 décharges peuvent naître, de plus en plus souvent et un peu
     *       plus grandes avec la charge ;</li>
     *   <li>frappe ({@code strike} > 0, charge absente) : salve brève et dense (2 à 3 par tick), éclairs plus courts en durée.</li>
     * </ul>
     * {@code pxPerBlock} : pixels du modèle d'Ace par bloc du monde dans ce repère.
     */
    private static void bursts(PoseStack pose, SubmitNodeCollector collector, LivingEntity entity, float charge, float strike,
                               float now, float pxPerBlock) {
        boolean charging = charge >= 0.0F;
        float level = charging ? charge : 0.5F;
        float rate = charging ? 0.35F + 0.55F * charge : 0.9F * Math.min(1.0F, strike / 0.6F);
        RenderType type = RenderTypes.entityTranslucent(TEXTURE);
        int tick = (int) Math.floor(now);
        for (int born = tick - BURST_WINDOW; born <= tick; born++) {
            Random shape = new Random(entity.getId() * 1_000_003L + born * 92_821L);
            if (shape.nextFloat() >= rate) {
                continue; // pas d'éclair né à ce tick
            }
            int count = charging ? 1 + Math.round(2.0F * charge * shape.nextFloat()) : 2 + shape.nextInt(2);
            for (int c = 0; c < count; c++) {
                float u = BURST_START + (1.0F - BURST_START) * shape.nextFloat();
                // petites décharges : 0,3 à 0,8 bloc de long environ (avant : 1,2 à 3,6)
                float size = 0.05F + 0.07F * level + shape.nextFloat() * 0.03F;
                int life = charging ? 4 + shape.nextInt(3) : 3 + shape.nextInt(2);
                // direction : au hasard, vers l'extérieur de la lame
                double theta = shape.nextDouble() * Math.PI * 2.0, z = shape.nextDouble() * 2.0 - 1.0, r = Math.sqrt(1.0 - z * z);
                float[] dir = {(float) (r * Math.cos(theta)), (float) (r * Math.sin(theta)), (float) z};
                float age = now - born;
                if (age < 0.0F || age >= life) {
                    continue;
                }
                float[] at = blade(u);
                burst(pose, collector, type, new float[] {at[0], at[1], Z}, dir, size * 5.4F * pxPerBlock, age / life,
                        shape.nextLong(), (int) (age / 2.0F), 0.4F * (1.0F + 0.3F * level)); // fins (charge et frappe)
            }
        }
    }

    /** Un éclair qui jaillit : comme HakiBoltRenderer (tête, queue, poussée vers l'extérieur, brisures qui crépitent). */
    private static void burst(PoseStack pose, SubmitNodeCollector collector, RenderType type, float[] origin, float[] dir, float length,
                              float life01, long seed, int crackle, float thickness) {
        float head = smoothstep(Math.min(1.0F, life01 / 0.4F));
        float tail = life01 < 0.22F ? 0.0F : smoothstep((life01 - 0.22F) / 0.78F) * 0.92F;
        float fade = life01 < 0.75F ? 1.0F : Math.max(0.0F, (1.0F - life01) / 0.25F);
        if (head - tail <= 0.02F || fade <= 0.0F) {
            return;
        }
        float push = life01 * life01 * length * 0.37F; // il file vers l'extérieur (HakiBoltRenderer : 2 blocs pour 5,4 de long)
        // deux directions perpendiculaires pour les brisures
        float[] ref = Math.abs(dir[1]) > 0.9F ? new float[] {1.0F, 0.0F, 0.0F} : new float[] {0.0F, 1.0F, 0.0F};
        float[] a = normalize(cross(dir, ref)), b = normalize(cross(dir, a));
        Random jag = new Random(seed + crackle * 15_485_863L);
        int points = 10;
        float[] prev = null;
        for (int i = 0; i < points; i++) {
            float t = tail + (head - tail) * i / (points - 1);
            float env = (float) Math.pow(t, 0.75);
            float ju = i == 0 ? 0.0F : (jag.nextFloat() - 0.5F) * 2.0F * 0.07F * length * env;
            float jv = i == 0 ? 0.0F : (jag.nextFloat() - 0.5F) * 2.0F * 0.07F * length * env;
            float along = push + t * length;
            float[] p = new float[3];
            for (int k = 0; k < 3; k++) {
                p[k] = origin[k] + dir[k] * along + a[k] * ju + b[k] * jv;
            }
            if (prev != null) {
                float u = (i - 0.5F) / (points - 1);
                float w = 0.95F * thickness * (0.3F + 0.7F * (float) Math.sin(Math.PI * u));
                segment(pose, collector, 4, type, prev, p, w * 1.7F, argb(fade, CORE));
                segment(pose, collector, 5, type, prev, p, w * 2.6F, argb(0.42F * fade, SHEATH));
            }
            prev = p;
        }
    }

    private static float smoothstep(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float[] normalize(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1.0E-6F ? new float[] {1.0F, 0.0F, 0.0F} : new float[] {v[0] / l, v[1] / l, v[2] / l};
    }

    /** Point de l'axe de la lame, u de 0 (garde) à 1 (pointe), en pixels du modèle. */
    private static float[] blade(float u) {
        float f = Math.max(0.0F, Math.min(1.0F, u)) * (BLADE.length - 1);
        int i = Math.min(BLADE.length - 2, (int) f);
        float r = f - i;
        return new float[] {BLADE[i][0] + (BLADE[i + 1][0] - BLADE[i][0]) * r, BLADE[i][1] + (BLADE[i + 1][1] - BLADE[i][1]) * r};
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
