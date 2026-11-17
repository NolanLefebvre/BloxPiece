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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.client.GuraHand;
import nolan.bloxpiece.client.entity.GuraModels.QuakeRenderState;
import nolan.bloxpiece.fx.QuakeFx;

/**
 * Affiche les effets du Gura Gura no Mi (air translucide et fissures lumineuses, toujours éclairés). Un effet ancré sur une main
 * (QuakeFx.anchorTo) est replacé à CHAQUE IMAGE sur le poing du joueur (GuraHand) : il colle à la main en troisième personne, et se
 * place en bas de l'écran, côté main, en première personne (taille réduite pour ne pas boucher la vue).
 * Animations : apparition rapide, croissance (les fissures s'étendent), pulsation des bulles, rétrécissement en fin de vie.
 */
public class QuakeFxRenderer extends EntityRenderer<QuakeFx, QuakeRenderState> {

    /** Rayon maximal d'une bulle vue en première personne (blocs). */
    private static final float FIRST_PERSON_MAX = 0.2F;

    /** Durée de propagation des fissures au sol, du centre jusqu'au bout (ticks). */
    private static final float GROUND_PROPAGATE_TICKS = 14.0F;

    private final EntityModel<QuakeRenderState> disc;
    private final EntityModel<QuakeRenderState> ground;
    private final EntityModel<QuakeRenderState> orb;
    private final EntityModel<QuakeRenderState> shell;
    private final EntityModel<QuakeRenderState> tsunami;

    public QuakeFxRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.disc = new GuraModels.CrackDiscModel(context.bakeLayer(GuraModels.CrackDiscModel.LAYER));
        this.ground = new GuraModels.GroundCrackModel(context.bakeLayer(GuraModels.GroundCrackModel.LAYER));
        this.orb = new GuraModels.CrackOrbModel(context.bakeLayer(GuraModels.CrackOrbModel.LAYER));
        this.shell = new GuraModels.CrackOrbModel(context.bakeLayer(GuraModels.CrackOrbModel.SHELL_LAYER));
        this.tsunami = new GuraModels.TsunamiModel(context.bakeLayer(GuraModels.TsunamiModel.LAYER));
    }

    @Override
    public QuakeRenderState createRenderState() {
        return new QuakeRenderState();
    }

    @Override
    public void extractRenderState(QuakeFx entity, QuakeRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.kind = entity.kind(); // la lumière du monde est gardée pour les blocs des vagues ; le reste est lumineux (submit)
        state.yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        state.pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        float age = entity.tickCount + partialTick;
        state.age = age;
        float life = Math.max(1, entity.life());
        float end = Mth.clamp((life - age) / 4.0F, 0.0F, 1.0F);
        float grow = switch (state.kind) {
            case QuakeFx.PANEL -> ease(Mth.clamp(age / 10.0F, 0.0F, 1.0F));
            case QuakeFx.RING -> 1.0F; // taille fixe : ce sont les fissures qui avancent (reveal)
            case QuakeFx.BLAST -> ease(Mth.clamp(age / 10.0F, 0.0F, 1.0F));
            case QuakeFx.TEAR -> ease(Mth.clamp(age / 4.0F, 0.0F, 1.0F));   // la fissure se déchire d'un coup
            case QuakeFx.WAVE -> ease(Mth.clamp(age / 5.0F, 0.0F, 1.0F));   // le tsunami se soulève
            default -> ease(Mth.clamp(age / 3.0F, 0.0F, 1.0F)) * (1.0F + 0.06F * Mth.sin(age * 0.8F));
        };
        state.scale = entity.size() * grow * (state.kind == QuakeFx.RING ? 1.0F : end);
        state.reveal = state.kind == QuakeFx.RING ? Mth.clamp(age / GROUND_PROPAGATE_TICKS, 0.0F, 1.0F) : 1.0F;
        if (state.kind == QuakeFx.RING && end < 1.0F) {
            state.reveal *= end; // à la fin, les fissures se referment vers le centre
        }

        // Ancrage sur la main du joueur, recalculé à chaque image
        state.offX = state.offY = state.offZ = 0.0;
        Entity owner = entity.ownerId() >= 0 ? entity.level().getEntity(entity.ownerId()) : null;
        if (owner instanceof Player player && entity.hand() != 0) {
            Vec3 anchor = GuraHand.anchor(player, entity.hand(), partialTick);
            Vec3 here = entity.getPosition(partialTick);
            state.offX = anchor.x - here.x;
            state.offY = anchor.y - here.y;
            state.offZ = anchor.z - here.z;
            if (GuraHand.isFirstPerson(player)) {
                state.scale = Math.min(state.scale, FIRST_PERSON_MAX);
            }
        }
    }

    private static float ease(float t) {
        return t * (2.0F - t);
    }

    @Override
    public void submit(QuakeRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.scale > 0.01F && state.kind == QuakeFx.WAVE) {
            // Tsunami : eau et écume translucides, toujours bien visibles
            pose.pushPose();
            pose.rotateDegrees(Axis.YP, -state.yaw);
            pose.scale(state.scale, state.scale, state.scale);
            pose.scale(-1.0F, -1.0F, 1.0F);
            pose.translate(0.0F, -1.501F, 0.0F);
            collector.submitModel(this.tsunami, state, pose, RenderTypes.entityTranslucent(GuraModels.WATER_TEXTURE),
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
        } else if (state.scale > 0.01F) {
            EntityModel<QuakeRenderState> model = switch (state.kind) {
                case QuakeFx.ORB -> this.orb;
                case QuakeFx.BLAST -> this.shell;
                case QuakeFx.RING -> this.ground;
                default -> this.disc;
            };
            pose.pushPose();
            pose.translate(state.offX, state.offY, state.offZ);
            pose.rotateDegrees(Axis.YP, -state.yaw);
            pose.rotateDegrees(Axis.XP, state.pitch);
            if (state.kind == QuakeFx.TEAR) {
                pose.scale(state.scale * 0.45F, state.scale * 1.8F, state.scale); // fissure étirée à la verticale
            } else {
                pose.scale(state.scale, state.scale, state.scale);
            }
            pose.scale(-1.0F, -1.0F, 1.0F);
            pose.translate(0.0F, -1.501F, 0.0F); // centre du modèle (24 px) sur la position de l'entité
            collector.submitModel(model, state, pose, RenderTypes.entityTranslucent(GuraModels.TEXTURE),
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
        }
        super.submit(state, pose, collector, camera);
    }

    @Override
    protected AABB getBoundingBoxForCulling(QuakeFx entity, float partialTick) {
        return entity.getBoundingBox().inflate(entity.size() * 2.2 + 2.0);
    }
}
