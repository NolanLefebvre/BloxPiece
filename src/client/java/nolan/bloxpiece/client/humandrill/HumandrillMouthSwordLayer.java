package nolan.bloxpiece.client.humandrill;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Troisième épée du Humandrill, serrée entre ses mâchoires (configuration à 3 épées) : même objet et même dessin que les épées
 * en main (modèle 3D humandrill_sword), posé par HumandrillModel.translateToMouthGrip puis par les mêmes transformations
 * qu'ItemInHandLayer pour une main droite. Attachée à la tête : elle suit chaque mouvement de tête (regard, coups, balayage du
 * coup des trois épées), ne flotte jamais et ne traverse pas le visage (poignée entre les crocs, garde contre la joue droite).
 */
public class HumandrillMouthSwordLayer extends RenderLayer<HumandrillRenderState, HumandrillModel> {

    public HumandrillMouthSwordLayer(RenderLayerParent<HumandrillRenderState, HumandrillModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, HumandrillRenderState state, float yRot, float xRot) {
        if (state.mouthItemState.isEmpty()) {
            return;
        }
        pose.pushPose();
        this.getParentModel().translateToMouthGrip(pose);
        // mêmes transformations qu'ItemInHandLayer (main droite)
        pose.rotateDegrees(Axis.XP, -90.0F);
        pose.rotateDegrees(Axis.YP, 180.0F);
        pose.translate(1.0F / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);
        state.mouthItemState.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
    }
}
