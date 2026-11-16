package nolan.bloxpiece.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.ability.BuddhaTransformAbility;

/**
 * Enveloppe dorée du Bouddha, dessinée par-dessus le joueur (le skin reste visible en dessous) :
 * <ol>
 *   <li>le MÊME modèle et le MÊME skin que le joueur, teintés en or franc, lumineux et translucides : le skin reste reconnaissable, il est
 *       juste recouvert d'une matière dorée ;</li>
 *   <li>une texture d'or (textures/entity/buddha_gold.png : jaune clair, or, or foncé, éclats) en translucide, pour les nuances et le relief ;</li>
 *   <li>un reflet lumineux : la même texture, qui défile lentement sur le modèle, d'intensité constante, en mélange additif (brillance).</li>
 * </ol>
 * Les trois passes réutilisent le modèle du joueur, déjà posé par ses animations : la couche épouse donc exactement le corps, quelle que soit
 * la pose. L'intensité suit la taille du joueur (attribut SCALE, déjà synchronisé par Minecraft avec tous les clients) : la couche apparaît
 * donc en même temps qu'il grandit et s'efface en même temps qu'il rétrécit, sans rien synchroniser en plus.
 */
public class BuddhaGoldLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final Identifier SHIMMER = BloxPiece.id("textures/entity/buddha_gold.png");

    private static final int GOLD_RGB = 0xFFC41E;      // teinte de la couche translucide sur le skin (or franc)
    private static final float SKIN_ALPHA = 0.72F;      // opacité maximale du skin doré (le skin reste visible en dessous)
    private static final float TEXTURE_ALPHA = 0.42F;   // opacité maximale des nuances d'or (zones claires / foncées)
    private static final int FULL_BRIGHT = 0xF000F0;    // lumière maximale : la couche brille même dans l'ombre

    public BuddhaGoldLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    /** 0 = taille normale, 1 = taille maximale de la transformation (même calcul pour le corps et pour la main en première personne). */
    public static float progressFor(float scale) {
        return Mth.clamp((scale - 1.0F) / (float) (BuddhaTransformAbility.MAX_SCALE - 1.0), 0.0F, 1.0F);
    }

    /** Couleur du reflet lumineux (mélange additif) : même formule pour le corps et pour la main. */
    private static int shimmerTint(float progress) {
        int c = (int) (progress * 0.8F * 255.0F);
        return 0xFF000000 | (c << 16) | ((int) (c * 0.82F) << 8) | (int) (c * 0.2F);
    }

    private static RenderType shimmerType(float ageInTicks) {
        return RenderTypes.energySwirl(SHIMMER, (ageInTicks * 0.004F) % 1.0F, (ageInTicks * 0.012F) % 1.0F);
    }

    /**
     * La même couche dorée que sur le corps, appliquée à la main (bras + manche) vue à la première personne.
     * Mêmes trois passes, mêmes couleurs, mêmes opacités, même reflet : seule la méthode de dessin change (une partie du modèle au lieu
     * du modèle entier). Le bras garde sa forme, sa pose et son animation : on redessine simplement la même partie, déjà posée par Minecraft.
     */
    public static void submitHand(PoseStack pose, SubmitNodeCollector collector, ModelPart arm, Identifier skin, float scale, float ageInTicks) {
        float progress = progressFor(scale);
        if (progress < 0.01F) {
            return;
        }
        int overlay = OverlayTexture.NO_OVERLAY;

        // 1) Skin teinté d'or, translucide, lumineux
        int skinAlpha = (int) (progress * SKIN_ALPHA * 255.0F);
        collector.order(1).submitModelPart(arm, pose, RenderTypes.entityTranslucent(skin), FULL_BRIGHT, overlay, null, (skinAlpha << 24) | GOLD_RGB);

        // 2) Nuances d'or par-dessus
        int goldAlpha = (int) (progress * TEXTURE_ALPHA * 255.0F);
        collector.order(2).submitModelPart(arm, pose, RenderTypes.entityTranslucent(SHIMMER), FULL_BRIGHT, overlay, null, (goldAlpha << 24) | 0xFFFFFF);

        // 3) Reflet lumineux qui défile (mélange additif)
        collector.order(3).submitModelPart(arm, pose, shimmerType(ageInTicks), FULL_BRIGHT, overlay, null, shimmerTint(progress));
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        // 0 = taille normale, 1 = taille maximale de la transformation
        float progress = progressFor(state.scale);
        if (progress < 0.01F || state.isInvisible) {
            return;
        }
        PlayerModel model = this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);

        // 1) Skin du joueur teinté d'or, translucide, lumineux
        int skinAlpha = (int) (progress * SKIN_ALPHA * 255.0F);
        Identifier skin = state.skin.body().texturePath();
        collector.order(1).submitModel(model, state, pose, RenderTypes.entityTranslucent(skin),
                FULL_BRIGHT, overlay, (skinAlpha << 24) | GOLD_RGB, null, state.outlineColor);

        // 2) Nuances d'or par-dessus (jaune clair, or, or foncé) : donne du relief et rappelle le Bouddha de Sengoku
        int goldAlpha = (int) (progress * TEXTURE_ALPHA * 255.0F);
        collector.order(2).submitModel(model, state, pose, RenderTypes.entityTranslucent(SHIMMER),
                FULL_BRIGHT, overlay, (goldAlpha << 24) | 0xFFFFFF, null, state.outlineColor);

        // 3) Reflet lumineux qui défile (mélange additif)
        // Intensité constante (plus de pulsation) : c'est l'ancien maximum du clignotement (pulse = 1), conservé en permanence
        collector.order(3).submitModel(model, state, pose, shimmerType(state.ageInTicks), FULL_BRIGHT, overlay, shimmerTint(progress), null, state.outlineColor);
    }
}
