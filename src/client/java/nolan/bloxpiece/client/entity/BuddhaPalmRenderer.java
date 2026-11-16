package nolan.bloxpiece.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.BuddhaPalm;

/**
 * Affiche la Paume du Bouddha avec le modèle 3D BuddhaPalmModel : pleine luminosité (l'énergie dorée éclaire d'elle-même, même la
 * nuit) + rendu opaque (cutout). Même principe et même repère que HikenFistRenderer (le projectile de Hiken).
 *
 * Animation (identique chez tous les joueurs : elle ne dépend que de l'âge de l'entité et de l'instant de l'impact, synchronisé
 * par le serveur) :
 * <ul>
 *   <li>apparition : la main jaillit du sceau doré dans le ciel, de 0 à sa taille réelle avec un léger rebond ;</li>
 *   <li>chute : les doigts se déploient peu à peu ;</li>
 *   <li>impact : la main reste écrasée au sol un instant, puis se dissout dans la lumière (le serveur émet les particules).</li>
 * </ul>
 */
public class BuddhaPalmRenderer extends EntityRenderer<BuddhaPalm, BuddhaPalmRenderState> {

    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/buddha_palm.png");

    /** Durée de l'apparition (ticks). */
    private static final float APPEAR_TICKS = 6.0F;
    /** Durée de l'ouverture des doigts pendant la chute (ticks). */
    private static final float SPREAD_TICKS = 16.0F;

    private final BuddhaPalmModel model;

    public BuddhaPalmRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new BuddhaPalmModel(context.bakeLayer(BuddhaPalmModel.LAYER));
    }

    @Override
    public BuddhaPalmRenderState createRenderState() {
        return new BuddhaPalmRenderState();
    }

    @Override
    public void extractRenderState(BuddhaPalm entity, BuddhaPalmRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        // Direction de la main = rotation de l'entité (envoyée par le serveur) : la paume regarde toujours dans le sens du vol
        state.yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        state.pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        float age = entity.tickCount + partialTick;

        // Apparition avec un léger dépassement (sensation de puissance qui "jaillit")
        float appear = Mth.clamp(age / APPEAR_TICKS, 0.0F, 1.0F);
        float scale = easeOutBack(appear);

        // Ouverture progressive des doigts pendant la chute
        float spread = Mth.clamp((age - 2.0F) / SPREAD_TICKS, 0.0F, 1.0F);
        state.spread = spread * spread * (3.0F - 2.0F * spread);

        // Après l'impact : la main reste plaquée au sol (légèrement écrasée), puis se dissout
        int impactAge = entity.impactAge();
        if (impactAge >= 0) {
            state.spread = 1.0F;
            float t = Mth.clamp((age - impactAge) / BuddhaPalm.LINGER_TICKS, 0.0F, 1.0F);
            float dissolve = t < 0.35F ? 0.0F : (t - 0.35F) / 0.65F;
            scale = (1.0F - dissolve * dissolve) * (t < 0.2F ? 1.0F + 0.08F * (float) Math.sin(t / 0.2F * Math.PI) : 1.0F);
        }
        state.scale = Math.max(0.0F, scale);
    }

    @Override
    public void submit(BuddhaPalmRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.scale > 0.01F) {
            float scale = (float) BuddhaPalm.MODEL_SCALE * state.scale;
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);
            poseStack.rotateDegrees(Axis.YP, 180.0F - state.yaw);
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0F, -1.501F, 0.0F); // le pivot du modèle (24 px) retombe sur la position de l'entité = centre de la paume
            collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE),
                    state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }

    /** Courbe d'apparition avec un petit dépassement (~10 %) avant de se stabiliser à 1. */
    private static float easeOutBack(float x) {
        float c1 = 1.4F;
        float c3 = c1 + 1.0F;
        float k = x - 1.0F;
        return 1.0F + c3 * k * k * k + c1 * k * k;
    }

    @Override
    protected AABB getBoundingBoxForCulling(BuddhaPalm entity, float partialTick) {
        // L'auréole dépasse largement de la hitbox (la paume) : sans cette marge, l'ensemble disparaîtrait au bord de l'écran
        return entity.getBoundingBox().inflate(BuddhaPalm.MODEL_SCALE * 2.0);
    }
}
