package nolan.bloxpiece.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.client.entity.IceModels.IceRenderState;
import nolan.bloxpiece.fx.IceEagle;
import nolan.bloxpiece.fx.IceSpike;
import nolan.bloxpiece.fx.IceStatue;
import nolan.bloxpiece.fx.IceTrident;
import nolan.bloxpiece.fx.ModFxEntity;

/**
 * Renderers des entités du fruit de la Glace : même principe que ceux des autres fruits (pose du modèle = rotation de l'entité,
 * pivot du modèle ramené sur sa position), en rendu translucide de glace. Le trident et l'aigle en vol brillent un peu (pleine
 * lumière) ; les pics et les statues prennent la lumière du monde, comme de la vraie glace.
 */
public final class IceRenderers {

    private IceRenderers() {
    }

    /** Base commune : orientation, échelle, et dessin translucide du modèle. */
    private abstract static class Base<T extends ModFxEntity> extends EntityRenderer<T, IceRenderState> {
        private final EntityModel<IceRenderState> model;
        private final boolean bright;

        Base(EntityRendererProvider.Context context, EntityModel<IceRenderState> model, boolean bright) {
            super(context);
            this.model = model;
            this.bright = bright;
        }

        @Override
        public IceRenderState createRenderState() {
            return new IceRenderState();
        }

        @Override
        public void extractRenderState(T entity, IceRenderState state, float partialTick) {
            super.extractRenderState(entity, state, partialTick);
            if (this.bright) {
                state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
            }
            state.yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            state.pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            state.scale = 1.0F;
        }

        /** Transformations propres à chaque entité, avant le retournement du modèle (repère monde, centré sur l'entité). */
        protected abstract void transform(IceRenderState state, PoseStack pose);

        @Override
        public void submit(IceRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            if (state.scale > 0.01F) {
                pose.pushPose();
                this.transform(state, pose);
                pose.scale(-1.0F, -1.0F, 1.0F);
                pose.translate(0.0F, -1.501F, 0.0F); // pivot du modèle (24 px) sur la position de l'entité
                collector.submitModel(this.model, state, pose, RenderTypes.entityTranslucent(IceModels.TEXTURE),
                        state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
                pose.popPose();
            }
            super.submit(state, pose, collector, camera);
        }
    }

    /** Trident lancé : orienté dans le sens du vol. */
    public static class Trident extends Base<IceTrident> {
        private static final float SCALE = 1.6F;

        public Trident(EntityRendererProvider.Context context) {
            super(context, new IceModels.TridentModel(context.bakeLayer(IceModels.TridentModel.LAYER)), true);
        }

        @Override
        protected void transform(IceRenderState state, PoseStack pose) {
            pose.scale(SCALE, SCALE, SCALE);
            pose.rotateDegrees(Axis.YP, 180.0F - state.yaw);
        }

        @Override
        protected AABB getBoundingBoxForCulling(IceTrident entity, float partialTick) {
            return entity.getBoundingBox().inflate(4.0);
        }
    }

    /** Aigle : orienté dans le sens du vol, il jaillit des cristaux (apparition en 5 ticks). */
    public static class Eagle extends Base<IceEagle> {
        public Eagle(EntityRendererProvider.Context context) {
            super(context, new IceModels.EagleModel(context.bakeLayer(IceModels.EagleModel.LAYER)), true);
        }

        @Override
        public void extractRenderState(IceEagle entity, IceRenderState state, float partialTick) {
            super.extractRenderState(entity, state, partialTick);
            float t = Mth.clamp((entity.tickCount + partialTick) / 5.0F, 0.0F, 1.0F);
            state.scale = t * (2.0F - t);
        }

        @Override
        protected void transform(IceRenderState state, PoseStack pose) {
            float s = (float) IceEagle.MODEL_SCALE * state.scale;
            pose.scale(s, s, s);
            pose.rotateDegrees(Axis.YP, 180.0F - state.yaw);
        }

        @Override
        protected AABB getBoundingBoxForCulling(IceEagle entity, float partialTick) {
            return entity.getBoundingBox().inflate(5.0);
        }
    }

    /** Pic : sort du sol (croissance), incliné vers l'extérieur, puis rentre un peu avant de se briser. */
    public static class Spike extends Base<IceSpike> {
        private static final float PX_SCALE = 1.5F;

        public Spike(EntityRendererProvider.Context context) {
            super(context, new IceModels.SpikeModel(context.bakeLayer(IceModels.SpikeModel.LAYER)), false);
        }

        @Override
        public void extractRenderState(IceSpike entity, IceRenderState state, float partialTick) {
            super.extractRenderState(entity, state, partialTick);
            float age = entity.tickCount + partialTick;
            float grow = Mth.clamp(age / IceSpike.GROW_TICKS, 0.0F, 1.0F);
            float end = Mth.clamp((entity.life() - age) / 6.0F, 0.0F, 1.0F);
            state.scale = entity.size() * grow * (2.0F - grow) * (0.6F + 0.4F * end);
        }

        @Override
        protected void transform(IceRenderState state, PoseStack pose) {
            pose.rotateDegrees(Axis.YP, 180.0F - state.yaw);
            pose.rotateDegrees(Axis.XP, -state.pitch); // inclinaison vers l'extérieur (+Z local = vers le centre)
            float s = PX_SCALE * state.scale;
            pose.scale(s, s, s);
        }

        @Override
        protected AABB getBoundingBoxForCulling(IceSpike entity, float partialTick) {
            return entity.getBoundingBox().inflate(3.0 * entity.size());
        }
    }

    /** Statue : le bloc de glace étiré à la taille de l'entité gelée (un peu plus grand, pour bien l'envelopper). */
    public static class Statue extends Base<IceStatue> {
        public Statue(EntityRendererProvider.Context context) {
            super(context, new IceModels.StatueModel(context.bakeLayer(IceModels.StatueModel.LAYER)), false);
        }

        @Override
        public void extractRenderState(IceStatue entity, IceRenderState state, float partialTick) {
            super.extractRenderState(entity, state, partialTick);
            state.width = entity.width();
            state.height = entity.height();
            float t = Mth.clamp((entity.tickCount + partialTick) / 4.0F, 0.0F, 1.0F); // la glace se forme en 4 ticks
            state.scale = t * (2.0F - t);
        }

        @Override
        protected void transform(IceRenderState state, PoseStack pose) {
            float w = (state.width + 0.35F) * state.scale;
            float h = (state.height + 0.25F) * state.scale;
            pose.scale(w, h, w);
        }

        @Override
        protected AABB getBoundingBoxForCulling(IceStatue entity, float partialTick) {
            return entity.getBoundingBox().inflate(Math.max(entity.width(), entity.height()) + 1.0);
        }
    }
}
