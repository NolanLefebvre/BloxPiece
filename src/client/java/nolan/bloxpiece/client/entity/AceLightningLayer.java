package nolan.bloxpiece.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Combattants armés (Gol D. Roger) : éclairs du Haki des Rois sur la lame d'Ace pendant la charge et la frappe de la Lame
 * dévastatrice, exactement ceux du joueur (AceChargeLightning, dans le repère de l'objet tenu en main droite). Rien sans Ace.
 */
public class AceLightningLayer extends RenderLayer<ArmedPirateRenderState, ArmedPirateModel> {

    public AceLightningLayer(RenderLayerParent<ArmedPirateRenderState, ArmedPirateModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, ArmedPirateRenderState state, float yRot, float xRot) {
        if (state.isInvisible || Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(state.entityId);
        if (entity instanceof LivingEntity living) {
            nolan.bloxpiece.client.AceChargeLightning.renderThirdPerson(pose, collector, this.getParentModel().rightArm, living, true,
                    false, Minecraft.getInstance().level.getGameTime() + (state.ageInTicks - entity.tickCount));
        }
    }
}
