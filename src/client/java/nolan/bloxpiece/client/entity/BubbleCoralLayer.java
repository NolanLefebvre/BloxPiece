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
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fruit.BubbleCoral;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Bulle d'air du Corail à Bulles, dessinée autour du joueur : une sur-peau diaphane, légèrement bleutée et brillante, moulée à
 * sa silhouette. Elle utilise son PROPRE modèle de joueur ({@link BubbleModel}) : même pose que le joueur (mêmes animations), mais
 * chaque partie du corps (tête, torse, bras, jambes) est agrandie autour de sa propre articulation. Chaque membre est ainsi
 * entièrement enveloppé, intérieur des bras compris (un simple agrandissement global décalait les bras vers l'extérieur et
 * laissait leur face intérieure à nu). Deux passes :
 * <ol>
 *   <li>une enveloppe translucide bleutée (textures/entity/bubble_coral.png), un peu plus grande que le corps ;</li>
 *   <li>un reflet irisé qui glisse sur la bulle (mélange additif), pour l'aspect "bulle de savon".</li>
 * </ol>
 * Formation progressive, en {@link #FORM_TICKS} ticks, pendant que le joueur souffle dans le corail (main devant la bouche, voir
 * PlayerModelMixin) : la bulle naît autour de la tête et descend petit à petit. Chaque partie du corps apparaît à son tour
 * (tête, puis bras, puis torse, puis jambes) et gonfle doucement jusqu'à sa taille finale (léger
 * rebond), pendant que l'enveloppe s'opacifie et que le reflet tourbillonne vite autour du joueur, comme si elle s'enroulait
 * autour de lui. L'instant de formation vient de
 * l'attachment BUBBLE_CORAL (synchronisé avec tous les joueurs).
 */
public class BubbleCoralLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/bubble_coral.png");

    private static final float FORM_TICKS = BubbleCoral.FORM_TICKS; // durée de la formation (2 secondes) : le joueur souffle dedans
    private static final int TINT_RGB = 0xC8ECFF;           // bleu très pâle
    private static final float SHELL_ALPHA = 0.55F;         // opacité de l'enveloppe (multipliée par la texture)
    private static final float INFLATE = 0.13F;             // chaque partie du corps est agrandie de 13 % (bulle bien visible)
    private static final int FULL_BRIGHT = 0xF000F0;

    // Ordre d'apparition des parties (fraction de la formation où chacune commence) : le joueur souffle, la bulle naît autour de
    // sa tête puis descend sur les bras, le torse et enfin les jambes
    private static final float HEAD_START = 0.05F;
    private static final float ARMS_START = 0.25F;
    private static final float BODY_START = 0.40F;
    private static final float LEGS_START = 0.60F;
    private static final float PART_DURATION = 0.35F;    // durée de gonflement de chaque partie (fraction de la formation)
    private static final float START_INFLATE = 0.02F;    // taille de départ d'une partie (juste au-dessus de la peau)

    private final BubbleModel bubble;

    public BubbleCoralLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ModelPart playerRoot) {
        super(parent);
        this.bubble = new BubbleModel(playerRoot);
    }

    /**
     * Modèle de joueur de la bulle : la pose est calculée comme celle du joueur (setupAnim vanilla), puis chaque partie est agrandie
     * autour de son articulation selon l'avancement de la formation de la bulle de CE joueur (retrouvé par state.id). Calculé dans
     * setupAnim, donc juste au moment du dessin, même quand plusieurs joueurs ont une bulle en même temps.
     */
    static final class BubbleModel extends PlayerModel {
        BubbleModel(ModelPart root) {
            super(root, false);
        }

        @Override
        public void setupAnim(AvatarRenderState state) {
            super.setupAnim(state); // remet la pose d'origine (échelles comprises) puis anime comme le joueur
            ClientLevel level = Minecraft.getInstance().level;
            Entity entity = level == null ? null : level.getEntity(state.id);
            float t = entity == null ? 1.0F : Math.max(0.0F, formation(entity, state.ageInTicks - (float) Math.floor(state.ageInTicks)));
            apply(this.rightLeg, t, LEGS_START);
            apply(this.leftLeg, t, LEGS_START);
            apply(this.body, t, BODY_START);
            apply(this.rightArm, t, ARMS_START);
            apply(this.leftArm, t, ARMS_START);
            apply(this.head, t, HEAD_START);
        }

        /** Cache la partie tant que la bulle ne l'a pas atteinte, puis la fait gonfler doucement jusqu'à sa taille finale. */
        private static void apply(ModelPart part, float t, float start) {
            float p = partProgress(t, start);
            part.visible = p > 0.0F;
            float s = partScale(p);
            part.xScale = s;
            part.yScale = s;
            part.zScale = s;
        }
    }

    /** Avancement 0..1 de la formation de la bulle, ou -1 si cette entité n'a pas de bulle. */
    public static float formation(Entity entity, float partialTick) {
        Integer formedAt = entity.getAttached(ModAttachments.BUBBLE_CORAL);
        if (formedAt == null) {
            return -1.0F;
        }
        float age = (float) (entity.level().getGameTime() - formedAt) + partialTick;
        return Mth.clamp(age / FORM_TICKS, 0.0F, 1.0F);
    }

    /** Avancement 0..1 d'une partie du corps qui commence à se recouvrir à {@code start} (fraction de la formation). */
    private static float partProgress(float t, float start) {
        return t >= 1.0F ? 1.0F : Mth.clamp((t - start) / PART_DURATION, 0.0F, 1.0F);
    }

    /** Taille d'une partie selon son avancement : de juste au-dessus de la peau jusqu'à la bulle complète. */
    private static float partScale(float p) {
        return 1.0F + START_INFLATE + (INFLATE - START_INFLATE) * grow(p);
    }

    /** Gonflement avec un léger rebond (easeOutBack) : la bulle dépasse un peu sa taille puis se stabilise. */
    private static float grow(float t) {
        float c = 1.7F;
        float x = t - 1.0F;
        return 1.0F + (c + 1.0F) * x * x * x + c * x * x;
    }

    private static RenderType shimmer(float ageInTicks, float t) {
        // Pendant la formation, le reflet tourbillonne beaucoup plus vite : la bulle "s'enroule" autour du joueur
        float speed = 1.0F + 6.0F * (1.0F - t);
        return RenderTypes.energySwirl(TEXTURE, (ageInTicks * 0.006F * speed) % 1.0F, (ageInTicks * 0.004F * speed) % 1.0F);
    }

    private static int shimmerTint(float t) {
        int c = (int) (t * 0.35F * 255.0F);
        return 0xFF000000 | ((int) (c * 0.75F) << 16) | ((int) (c * 0.95F) << 8) | c;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level == null ? null : level.getEntity(state.id);
        if (entity == null || state.isInvisible) {
            return;
        }
        float t = formation(entity, state.ageInTicks - (float) Math.floor(state.ageInTicks));
        if (t < 0.0F) {
            return;
        }
        PlayerModel model = this.bubble; // agrandi partie par partie dans BubbleModel.setupAnim
        int overlay = OverlayTexture.NO_OVERLAY;

        pose.pushPose();

        // 1) Enveloppe diaphane bleutée
        int alpha = (int) ((0.35F + 0.65F * t) * SHELL_ALPHA * 255.0F);
        collector.order(1).submitModel(model, state, pose, RenderTypes.entityTranslucent(TEXTURE),
                FULL_BRIGHT, overlay, (alpha << 24) | TINT_RGB, null, state.outlineColor);
        // 2) Reflet irisé qui glisse sur la bulle (additif)
        collector.order(2).submitModel(model, state, pose, shimmer(state.ageInTicks, t),
                FULL_BRIGHT, overlay, shimmerTint(Math.max(t, 0.6F)), null, state.outlineColor);
        pose.popPose();
    }

    /** La même bulle sur la main vue à la première personne (bras légèrement agrandi pour ne pas se confondre avec la peau). */
    public static void submitHand(PoseStack pose, SubmitNodeCollector collector, ModelPart arm, Entity player, float partialTick) {
        float t = formation(player, partialTick);
        if (t < 0.0F) {
            return;
        }
        float p = partProgress(t, ARMS_START); // la main se recouvre en même temps que les bras
        if (p <= 0.0F) {
            return;
        }
        float ageInTicks = player.tickCount + partialTick;
        pose.pushPose();
        float s = 1.0F + 0.08F * grow(p);
        pose.scale(s, s, s);
        int alpha = (int) ((0.35F + 0.65F * t) * SHELL_ALPHA * 255.0F);
        collector.order(1).submitModelPart(arm, pose, RenderTypes.entityTranslucent(TEXTURE), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                null, (alpha << 24) | TINT_RGB);
        collector.order(2).submitModelPart(arm, pose, shimmer(ageInTicks, t), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                null, shimmerTint(Math.max(t, 0.6F)));
        pose.popPose();
    }
}
