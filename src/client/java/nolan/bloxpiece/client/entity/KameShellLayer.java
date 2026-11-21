package nolan.bloxpiece.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.ability.Kame;
import nolan.bloxpiece.client.KamePose;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Kame Kame no Mi : la carapace (KameShellModel) sur le joueur, chez tous les joueurs (attachment KAME_SHELL synchronisé).
 * <ul>
 *   <li>Portée : accrochée à la pièce « body » du modèle (elle suit le torse dans toutes les animations), dos bombé et plastron.</li>
 *   <li>Elle POUSSE en Kame.GROW_TICKS : le dos se gonfle depuis le milieu du dos (épaisseur d'abord, puis largeur, petit
 *       rebond), le plastron suit un peu après ; elle RENTRE en Kame.SHRINK_TICKS (l'inverse).</li>
 *   <li>Carapace tournoyante : torse et membres rentrés (KamePose), la carapace seule, couchée dos en l'air près du sol, tourne
 *       sur elle-même.</li>
 * </ul>
 */
public class KameShellLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public static final Identifier TEXTURE = BloxPiece.id("textures/entity/kame_shell.png");

    private final KameShellModel shell;

    public KameShellLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ModelPart root) {
        super(parent);
        this.shell = new KameShellModel(root);
    }

    /** Croissance de la carapace (0 = absente, 1 = complète), d'après KAME_SHELL. */
    public static float growth(Entity entity, float now) {
        Integer v = entity.getAttached(ModAttachments.KAME_SHELL);
        if (v == null) {
            return 0.0F;
        }
        if (v > 0) {
            float t = Math.max(0.0F, Math.min(1.0F, (now - v) / Kame.GROW_TICKS));
            // petit rebond en fin de croissance (la carapace « s'installe »)
            return t < 1.0F ? t + 0.12F * (float) Math.sin(t * Math.PI) * t : 1.0F;
        }
        return 1.0F - Math.max(0.0F, Math.min(1.0F, (now + v) / Kame.SHRINK_TICKS));
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(state.id);
        if (entity == null) {
            return;
        }
        float now = Minecraft.getInstance().level.getGameTime() + (state.ageInTicks - entity.tickCount);
        float g = growth(entity, now);
        if (g <= 0.0F) {
            return;
        }
        RenderType type = RenderTypes.entityCutout(TEXTURE);
        pose.pushPose();
        if (KamePose.spinning(entity, now)) {
            // carapace tournoyante : couchée dos en l'air, à ~0,35 bloc du sol (modèle : y vers le bas, pieds en y = 24 px)
            pose.translate(0.0F, (24.0F - 5.6F) / 16.0F, 0.0F);
            pose.rotateDegrees(Axis.YP, state.ageInTicks * 47.0F);
            pose.rotateDegrees(Axis.XP, 90.0F);
            pose.translate(0.0F, -6.0F / 16.0F, -2.0F / 16.0F); // centre de la carapace sur l'axe de rotation
            this.full(pose, collector, type, light);
        } else {
            this.getParentModel().body.translateAndRotate(pose); // suit le torse
            float back = Math.max(0.0F, Math.min(1.2F, g));
            float belly = Math.max(0.0F, Math.min(1.2F, (g - 0.15F) / 0.85F));
            this.grow(this.shell.dorsal, back);
            this.grow(this.shell.ventral, belly);
            if (back > 0.0F) {
                collector.submitModelPart(this.shell.dorsal, pose, type, light, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
            }
            if (belly > 0.0F) {
                collector.submitModelPart(this.shell.ventral, pose, type, light, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
            }
        }
        pose.popPose();
    }

    /** La carapace complète (dos et plastron, taille normale) : posée, ou tournoyante. */
    private void full(PoseStack pose, SubmitNodeCollector collector, RenderType type, int light) {
        this.grow(this.shell.dorsal, 1.0F);
        this.grow(this.shell.ventral, 1.0F);
        collector.submitModelPart(this.shell.dorsal, pose, type, light, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
        collector.submitModelPart(this.shell.ventral, pose, type, light, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
    }

    /** Croissance d'une partie autour de son pivot : l'épaisseur d'abord, la largeur et la hauteur ensuite. */
    private void grow(ModelPart part, float g) {
        part.zScale = g;
        part.xScale = 0.35F + 0.65F * Math.min(1.0F, g);
        part.yScale = 0.35F + 0.65F * Math.min(1.0F, g);
    }
}
