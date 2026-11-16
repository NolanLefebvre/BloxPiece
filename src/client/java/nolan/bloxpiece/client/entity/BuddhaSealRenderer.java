package nolan.bloxpiece.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

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
import nolan.bloxpiece.fx.BuddhaSeal;

/**
 * Affiche le sceau du Bouddha au sol (modèle plat BuddhaSealModel) : rendu émissif (il brille, de jour comme de nuit, sous tous
 * les angles), aplati à quelques centièmes de bloc au-dessus du sol.
 *
 * Animation, identique chez tous les joueurs (elle ne dépend que de l'âge de l'entité et de l'instant de l'impact, synchronisé) :
 * tracé progressif à l'apparition, rotation des anneaux, puis à l'impact le sceau s'élargit en onde de choc pendant que ses
 * plaques se détachent une à une.
 */
public class BuddhaSealRenderer extends EntityRenderer<BuddhaSeal, BuddhaSealRenderState> {

    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/buddha_seal.png");

    /** Rayon extérieur du modèle (px). */
    private static final float MODEL_RADIUS_PX = 24.0F;
    /** Épaisseur : le modèle est aplati (1 px de modèle -> ~0,09 bloc). */
    private static final float FLATTEN = 0.25F;
    /** Durée du tracé à l'apparition (ticks). */
    private static final float REVEAL_TICKS = 12.0F;

    private final BuddhaSealModel model;

    public BuddhaSealRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new BuddhaSealModel(context.bakeLayer(BuddhaSealModel.LAYER));
    }

    @Override
    public BuddhaSealRenderState createRenderState() {
        return new BuddhaSealRenderState();
    }

    @Override
    public void extractRenderState(BuddhaSeal entity, BuddhaSealRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        state.size = entity.size();
        float age = entity.tickCount + partialTick;
        float reveal = Mth.clamp(age / REVEAL_TICKS, 0.0F, 1.0F);
        state.reveal = 1.0F - (1.0F - reveal) * (1.0F - reveal);
        state.dissolve = 0.0F;
        state.expand = 1.0F;
        int impactAge = entity.impactAge();
        if (impactAge >= 0) {
            float t = Mth.clamp((age - impactAge) / BuddhaSeal.IMPACT_TICKS, 0.0F, 1.0F);
            state.reveal = 1.0F;
            state.expand = 1.0F + (BuddhaSeal.SHOCK_EXPAND - 1.0F) * (1.0F - (1.0F - t) * (1.0F - t));
            state.dissolve = Mth.clamp((t - 0.2F) / 0.8F, 0.0F, 1.0F);
        }
    }

    @Override
    public void submit(BuddhaSealRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        float scale = (float) BuddhaSeal.RADIUS * 16.0F / MODEL_RADIUS_PX * state.expand * state.size;
        poseStack.pushPose();
        poseStack.scale(scale, scale * FLATTEN, scale);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F); // le pivot du modèle (24 px) retombe sur la position de l'entité
        collector.submitModel(this.model, state, poseStack, RenderTypes.entityTranslucentEmissive(TEXTURE),
                state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    protected AABB getBoundingBoxForCulling(BuddhaSeal entity, float partialTick) {
        // Le sceau (jusqu'à ~19 blocs de rayon pendant l'onde de choc) dépasse très largement de la hitbox de l'entité
        double r = BuddhaSeal.RADIUS * BuddhaSeal.SHOCK_EXPAND * entity.size();
        return new AABB(entity.getX() - r, entity.getY() - 1.0, entity.getZ() - r, entity.getX() + r, entity.getY() + 1.0, entity.getZ() + r);
    }
}
