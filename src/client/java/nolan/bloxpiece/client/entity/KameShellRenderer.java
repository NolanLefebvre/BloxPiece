package nolan.bloxpiece.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

import nolan.bloxpiece.fx.KameShellEntity;

/**
 * Carapace vide posée dans le monde (KameShellEntity) : le même modèle que sur le joueur (KameShellModel), couchée dos en
 * l'air sur le sol, tournée comme son propriétaire au moment où il est rentré dedans.
 */
public class KameShellRenderer extends EntityRenderer<KameShellEntity, KameShellRenderer.ShellState> {

    public static class ShellState extends EntityRenderState {
        public float yaw;
        /** Redressement (sortie de la poche) : 0 = couchée au sol, 1 = debout à hauteur du torse ; -1 = au sol. */
        public float rise = -1.0F;
    }

    private final KameShellModel shell;

    public KameShellRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shell = new KameShellModel(context.bakeLayer(KameShellModel.LAYER));
    }

    @Override
    public ShellState createRenderState() {
        return new ShellState();
    }

    @Override
    public void extractRenderState(KameShellEntity entity, ShellState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yaw = entity.getYRot();
        state.rise = entity.riseStart() < 0 ? -1.0F
                : Math.min(1.0F, (entity.tickCount + partialTick - entity.riseStart()) / nolan.bloxpiece.ability.Kame.RISE_TICKS);
    }

    @Override
    public void submit(ShellState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.rotateDegrees(Axis.YP, 180.0F - state.yaw);
        pose.scale(-1.0F, -1.0F, 1.0F);                       // repère des modèles (y vers le bas)
        // posée au sol, dos en l'air ; à la sortie de la poche, elle se redresse jusqu'à sa place sur le torse du joueur (cou à
        // 24 px au-dessus des pieds, modèle du joueur réduit à 0,9375) : là, elle se confond avec la carapace portée
        float t = state.rise < 0.0F ? 0.0F : state.rise * state.rise * (3.0F - 2.0F * state.rise);
        float scale = 1.0F + (0.9375F - 1.0F) * t;
        float centerY = -5.6F / 16.0F + (-24.0F * 0.9375F / 16.0F + 6.0F * 0.9375F / 16.0F + 5.6F / 16.0F) * t;
        pose.translate(0.0F, centerY, 2.0F * 0.9375F / 16.0F * t);
        pose.scale(scale, scale, scale);
        pose.rotateDegrees(Axis.XP, 90.0F * (1.0F - t));
        pose.translate(0.0F, -6.0F / 16.0F, -2.0F / 16.0F);
        this.shell.dorsal.xScale = this.shell.dorsal.yScale = this.shell.dorsal.zScale = 1.0F;
        this.shell.ventral.xScale = this.shell.ventral.yScale = this.shell.ventral.zScale = 1.0F;
        collector.submitModelPart(this.shell.dorsal, pose, RenderTypes.entityCutout(KameShellLayer.TEXTURE), state.lightCoords,
                OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
        collector.submitModelPart(this.shell.ventral, pose, RenderTypes.entityCutout(KameShellLayer.TEXTURE), state.lightCoords,
                OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
}
