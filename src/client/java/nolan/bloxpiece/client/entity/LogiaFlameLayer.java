package nolan.bloxpiece.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.client.LogiaClient;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Passif Logia du fruit du Feu : cette couche ne DESSINE RIEN. Les membres dispersés sont entièrement invisibles (PlayerModelMixin) ;
 * elle se contente de relever, à chaque image, la pose finale des membres du joueur (après toutes les animations) pour que
 * LogiaClient fasse jaillir les flammes exactement à leur place.
 */
public class LogiaFlameLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public LogiaFlameLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level == null ? null : level.getEntity(state.id);
        if (entity == null || entity.getAttached(ModAttachments.LOGIA_PARTS) == null) {
            return;
        }
        PlayerModel model = this.getParentModel();
        LogiaClient.record(state.id, new ModelPart[] {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg});
    }
}
