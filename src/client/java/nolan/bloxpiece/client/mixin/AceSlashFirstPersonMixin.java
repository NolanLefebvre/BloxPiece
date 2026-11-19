package nolan.bloxpiece.client.mixin;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import nolan.bloxpiece.fx.AceSlashShape;
import nolan.bloxpiece.item.AceItem;

/**
 * Épée Ace à la PREMIÈRE PERSONNE : l'animation de la Lame dévastatrice du Haki des Rois (même geste que la troisième
 * personne : AceSwing, et mêmes instants que le lancer, voir AceSlash).
 * <p>
 * Tout le geste se fait DANS LE PLAN DE LA LAME D'ÉNERGIE : vu de la caméra, ce plan contient la direction du regard et la
 * diagonale haut-droite / bas-gauche (AceSlashShape.ROLL). Le bras et l'épée tournent autour de l'axe perpendiculaire à ce plan,
 * autour de la main, et la main glisse le long de la diagonale :
 * <ol>
 *   <li>charge (clic droit maintenu) : la main monte en haut à droite, l'épée se lève et bascule vers l'arrière, et reste levée ;</li>
 *   <li>frappe (relâchement) : grand mouvement continu vers le bas à gauche, dans la diagonale opposée ; la lame part à la fin ;</li>
 *   <li>récupération : retour en douceur à la tenue normale.</li>
 * </ol>
 * On s'insère juste après l'ouverture de la pose de la main (premier pushPose de submitArmWithItem) : toute la suite (bras,
 * épée, revêtement du Haki) est donc déplacée ensemble. Mixin facultatif (require = 0) : sans effet, mais sans plantage, si la
 * méthode change.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class AceSlashFirstPersonMixin {

    /** Main droite tenue à la première personne (position de Minecraft, repère de la caméra : x droite, y haut, -z devant). */
    private static final float HAND_X = 0.56F, HAND_Y = -0.52F, HAND_Z = -0.72F;
    /** Angle de l'épée dans le plan du coup (degrés, négatif = vers le haut à droite) et glissement de la main sur la diagonale. */
    private static final float RAISED_ANGLE = -75.0F, STRUCK_ANGLE = 60.0F;
    private static final float RAISED_SLIDE = 0.15F, STRUCK_SLIDE = -0.42F;
    private static final float RAISED_BACK = 0.05F, STRUCK_BACK = -0.12F;

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
            shift = At.Shift.AFTER, ordinal = 0), require = 0)
    private void bloxpiece$aceSlashSwing(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks,
                                         float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight,
                                         PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo info) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || hand != InteractionHand.MAIN_HAND || !(itemStack.getItem() instanceof AceItem)) {
            return;
        }
        nolan.bloxpiece.client.AceSwing.Step step = nolan.bloxpiece.client.AceSwing.at(player,
                player.level().getGameTime() + partialTicks);
        if (step == null) {
            return;
        }
        // poses clés : normale, levée (avec un léger tremblement pendant la charge), frappée
        float tremble = (float) Math.sin((player.tickCount + partialTicks) * 1.7F) * 1.5F;
        float[][] poses = {{0.0F, 0.0F, 0.0F}, {RAISED_ANGLE + tremble, RAISED_SLIDE, RAISED_BACK}, {STRUCK_ANGLE, STRUCK_SLIDE, STRUCK_BACK}};
        float[] from = poses[step.from()], to = poses[step.to()];
        float k = step.k();
        float angle = from[0] + (to[0] - from[0]) * k;
        float slide = from[1] + (to[1] - from[1]) * k;
        float back = from[2] + (to[2] - from[2]) * k;
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        float side = right ? 1.0F : -1.0F;
        // diagonale du coup vue de la caméra (haut-droite), et normale du plan du coup (diagonale x direction du regard)
        double roll = Math.toRadians(-AceSlashShape.ROLL);
        float ux = (float) Math.cos(roll) * side, uy = (float) Math.sin(roll);
        Vector3f normal = new Vector3f(-uy, ux, 0.0F).normalize();
        float hx = HAND_X * side;
        pose.translate(hx + ux * slide, HAND_Y + uy * slide, HAND_Z + back);
        pose.rotateDegrees(Axis.of(normal), angle); // main gauche : axe miroir, même sens visuel
        pose.translate(-hx, -HAND_Y, -HAND_Z);
    }
}
