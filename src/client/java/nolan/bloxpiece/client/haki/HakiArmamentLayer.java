package nolan.bloxpiece.client.haki;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;

import nolan.bloxpiece.BloxPiece;

/**
 * Revêtement du Haki de l'Armement, dessiné par-dessus le joueur (même modèle, même pose : il épouse les animations) :
 * <ol>
 *   <li>phase « en fusion » : le membre s'embrase d'une lueur sombre violacée et rougeâtre qui vibre (lumineuse dans le noir) ;</li>
 *   <li>puis il se fige en un revêtement NOIR MÉTALLIQUE : noir profond quasi opaque et reflet brillant qui glisse dessus.</li>
 * </ol>
 * Mode bras : seulement le bras principal. Mode corps entier : le Haki part du bras et se propage au torse, à l'autre bras, à la
 * tête puis aux jambes. Aussi dessiné sur la main en première personne (AvatarRendererHandMixin -> {@link #submitHand}).
 */
public class HakiArmamentLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final Identifier SHEEN = BloxPiece.id("textures/entity/haki_sheen.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Part de la transition passée « en fusion » avant de devenir noir métallique. */
    private static final float MOLTEN_END = 0.55F;

    public HakiArmamentLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
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
        float[] progress = HakiClientState.armament(entity, state.ageInTicks);
        if (progress == null) {
            return;
        }
        PlayerModel model = this.getParentModel();
        Identifier skin = state.skin.body().texturePath();
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        boolean rightMain = !(entity instanceof net.minecraft.world.entity.LivingEntity living)
                || living.getMainArm() == HumanoidArm.RIGHT;
        ModelPart main = rightMain ? model.rightArm : model.leftArm;
        ModelPart other = rightMain ? model.leftArm : model.rightArm;
        float b = progress[1];
        coat(collector, pose, main, skin, light, overlay, progress[0], state.ageInTicks);
        coat(collector, pose, model.body, skin, light, overlay, b, state.ageInTicks);
        coat(collector, pose, other, skin, light, overlay, spread(b, 0.2F), state.ageInTicks);
        coat(collector, pose, model.head, skin, light, overlay, spread(b, 0.3F), state.ageInTicks);
        coat(collector, pose, model.rightLeg, skin, light, overlay, spread(b, 0.45F), state.ageInTicks);
        coat(collector, pose, model.leftLeg, skin, light, overlay, spread(b, 0.45F), state.ageInTicks);
    }

    /** Propagation : la partie commence plus tard ({@code delay}) et finit avec le reste. */
    private static float spread(float b, float delay) {
        return Mth.clamp((b - delay) / (1.0F - delay), 0.0F, 1.0F);
    }

    /** Une partie du corps : lueur en fusion, puis noir métallique brillant. */
    private static void coat(SubmitNodeCollector collector, PoseStack pose, ModelPart part, Identifier skin, int light, int overlay,
                             float p, float age) {
        if (p <= 0.0F) {
            return;
        }
        int overlayNone = overlay;
        // 1) phase en fusion : lueur violacée / rougeâtre qui vibre, de plus en plus forte puis qui s'éteint dans le noir
        float molten = p < MOLTEN_END ? p / MOLTEN_END : Math.max(0.0F, 1.0F - (p - MOLTEN_END) / (1.0F - MOLTEN_END));
        if (molten > 0.01F) {
            float pulse = 0.5F + 0.5F * Mth.sin(age * 1.7F);
            int r = (int) (150 + 80 * pulse), g = (int) (20 + 25 * pulse), bl = (int) (90 + 60 * (1 - pulse));
            int a = (int) (molten * 0.85F * 255);
            collector.order(1).submitModelPart(part, pose, RenderTypes.entityTranslucent(skin), FULL_BRIGHT, overlayNone, null,
                    (a << 24) | (r << 16) | (g << 8) | bl);
            int glow = (int) (molten * 255);
            collector.order(2).submitModelPart(part, pose, RenderTypes.energySwirl(SHEEN, (age * 0.03F) % 1.0F, (age * 0.05F) % 1.0F),
                    FULL_BRIGHT, overlayNone, null, 0xFF000000 | (glow << 16) | ((int) (glow * 0.15F) << 8) | (int) (glow * 0.55F));
        }
        // 2) revêtement noir métallique, figé, et reflet brillant qui glisse
        float metal = p <= MOLTEN_END ? 0.0F : (p - MOLTEN_END) / (1.0F - MOLTEN_END);
        if (metal > 0.01F) {
            int a = (int) (metal * 0.96F * 255);
            collector.order(3).submitModelPart(part, pose, RenderTypes.entityTranslucent(skin), light, overlayNone, null,
                    (a << 24) | 0x0C0B10);
            int s = (int) (metal * 70);
            collector.order(4).submitModelPart(part, pose, RenderTypes.energySwirl(SHEEN, (age * 0.006F) % 1.0F, (age * 0.002F) % 1.0F),
                    FULL_BRIGHT, overlayNone, null, 0xFF000000 | ((int) (s * 0.8F) << 16) | ((int) (s * 0.7F) << 8) | s);
        }
    }

    /** La main vue à la première personne : bras principal en mode bras, les deux bras en mode corps entier. */
    public static void submitHand(PoseStack pose, SubmitNodeCollector collector, ModelPart arm, Identifier skin, Entity player,
                                  boolean isMainArm, float age) {
        float[] progress = HakiClientState.armament(player, age);
        if (progress == null) {
            return;
        }
        float p = isMainArm ? progress[0] : spread(progress[1], 0.2F);
        coat(collector, pose, arm, skin, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, p, age);
    }
}
