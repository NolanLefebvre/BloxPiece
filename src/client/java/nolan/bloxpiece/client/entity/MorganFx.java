package nolan.bloxpiece.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;

/**
 * Outils de dessin des effets de Morgan (impact au sol, traînée de la hache) : un cube unité (1 px) qu'on étire, oriente et
 * colore, avec la texture morgan_fx.png (grain de poussière et de pierre, blanc : la couleur vient de chaque pièce).
 */
public final class MorganFx {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("morgan_fx"), "main");
    public static final Identifier TEXTURE = BloxPiece.id("textures/entity/morgan_fx.png");
    public static final int FULL_BRIGHT = 0xF000F0;

    private MorganFx() {
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("cube", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    public static RenderType type() {
        return RenderTypes.entityTranslucent(TEXTURE);
    }

    /**
     * Une pièce : de (x, y, z) (blocs) sur {@code length} blocs dans la direction {@code yawDeg} (0 : +z), section {@code width}
     * x {@code height} (px), inclinée de {@code pitchDeg} (vers le haut si négatif).
     */
    public static void piece(ModelPart cube, PoseStack pose, SubmitNodeCollector collector, int order, int light, float x, float y,
                             float z, float yawDeg, float pitchDeg, float length, float width, float height, int color) {
        if (((color >>> 24) & 0xFF) < 3 || length <= 0.0F) {
            return;
        }
        pose.pushPose();
        pose.translate(x, y, z);
        pose.rotateDegrees(Axis.YP, yawDeg);
        if (pitchDeg != 0.0F) {
            pose.rotateDegrees(Axis.XP, pitchDeg);
        }
        pose.scale(width, height, length * 16.0F);
        collector.order(order).submitModelPart(cube, pose, type(), light, OverlayTexture.NO_OVERLAY, null, color);
        pose.popPose();
    }

    public static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
