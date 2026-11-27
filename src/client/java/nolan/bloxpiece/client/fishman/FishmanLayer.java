package nolan.bloxpiece.client.fishman;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;

import nolan.bloxpiece.ability.Fishman;
import nolan.bloxpiece.ability.FishmanDepthsAbility;
import nolan.bloxpiece.ability.FishmanSpearAbility;
import nolan.bloxpiece.ability.FishmanTideAbility;
import nolan.bloxpiece.ability.FishmanWaveAbility;

import static nolan.bloxpiece.client.fishman.FishmanDraw.FOAM;
import static nolan.bloxpiece.client.fishman.FishmanDraw.LIGHT;
import static nolan.bloxpiece.client.fishman.FishmanDraw.SHINE;
import static nolan.bloxpiece.client.fishman.FishmanDraw.WATER;
import static nolan.bloxpiece.client.fishman.FishmanDraw.argb;

/**
 * Race Fishman, sur le joueur (troisième personne, et la main vue à la première personne via {@link #submitHand}) :
 * <ul>
 *   <li>APPARENCE : un aileron sur la face extérieure de chaque avant-bras (suit le bras) et un grand aileron dorsal (suit le
 *       buste), modèles 3D en plusieurs nuances de bleu (FishmanModels) ; le skin n'est pas modifié ; rien sur les autres races
 *       ni sur un joueur invisible ;</li>
 *   <li>EAU SUR LES BRAS pendant les attaques, aux mêmes instants que les poses (synchronisé) : tourbillon autour du poing
 *       (Onde aquatique), gouttes qui apparaissent et tournent autour des mains (Lance aquatique), eau qui s'accumule dans les
 *       mains (Vague dévastatrice), spirale d'eau qui monte le long du bras et enfle autour du poing (Perforation).</li>
 * </ul>
 */
public class FishmanLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static FishmanLayer shared;
    private final ModelPart finRight, finLeft, backFin;
    private final FishmanDraw water;

    public FishmanLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
        super(parent);
        this.finRight = context.bakeLayer(FishmanModels.ARM_FIN_RIGHT).getChild("fin");
        this.finLeft = context.bakeLayer(FishmanModels.ARM_FIN_LEFT).getChild("fin");
        this.backFin = context.bakeLayer(FishmanModels.BACK_FIN).getChild("fin");
        this.water = new FishmanDraw(context);
        shared = this;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector col, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(state.id);
        if (entity == null || !FishmanClient.isFishman(entity)) {
            return;
        }
        PlayerModel model = this.getParentModel();
        boolean slim = state.skin != null && state.skin.model() == PlayerModelType.SLIM;
        // 1. ailerons
        arm(pose, col, model.rightArm, false, slim, light);
        arm(pose, col, model.leftArm, true, slim, light);
        pose.pushPose();
        model.body.translateAndRotate(pose);
        pose.translate(0.0F, 0.0F, 2.0F / 16.0F);
        col.submitModelPart(this.backFin, pose, RenderTypes.entityCutout(FishmanModels.FIN_TEXTURE), light, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
        pose.popPose();
        // 2. l'eau sur les bras pendant les attaques
        int kind = FishmanClient.kind(entity);
        float e = FishmanClient.elapsed(entity, state.ageInTicks);
        if (kind != 0 && e >= 0.0F) {
            hands(pose, col, model.rightArm, false, slim, kind, e, state.ageInTicks, state.id, light);
            hands(pose, col, model.leftArm, true, slim, kind, e, state.ageInTicks, state.id + 5, light);
        }
    }

    /** Aileron d'un bras, posé sur la face extérieure (bras fin : un pixel plus près de l'axe). */
    private void arm(PoseStack pose, SubmitNodeCollector col, ModelPart arm, boolean left, boolean slim, int light) {
        pose.pushPose();
        arm.translateAndRotate(pose);
        float outer = (left ? 1.0F : -1.0F) * (slim ? 2.0F : 3.0F); // face extérieure du bras (px)
        pose.translate(outer / 16.0F, 0.0F, 0.0F);
        col.submitModelPart(left ? this.finLeft : this.finRight, pose, RenderTypes.entityCutout(FishmanModels.FIN_TEXTURE), light,
                OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
        pose.popPose();
    }

    /** Main au bout du bras (repère du bras, blocs). */
    private static float[] fist(boolean left, boolean slim) {
        return new float[] {(left ? 1.0F : -1.0F) * (slim ? 0.5F : 1.0F) / 16.0F, 10.5F / 16.0F, 0.0F};
    }

    /** L'eau qui accompagne chaque attaque, dans le repère du bras (le bras pointe vers +y, la main au bout). */
    private void hands(PoseStack pose, SubmitNodeCollector col, ModelPart arm, boolean left, boolean slim, int kind, float e, float age, int seed,
                       int light) {
        pose.pushPose();
        arm.translateAndRotate(pose);
        float[] f = fist(left, slim);
        switch (kind) {
            case Fishman.WAVE -> {
                if (!left) {
                    float st = FishmanWaveAbility.STANCE, s = FishmanWaveAbility.STRIKE;
                    float k = FishmanClient.ramp(e, 2.0F, s) * (1.0F - FishmanClient.ramp(e, s + 2.0F, s + 8.0F));
                    swirl(pose, col, f, 0.22F + 0.1F * k, 6, k, age * 0.5F, 0.07F, light);
                    if (e > st && e < s) { // la pression monte : petits filets d'eau qui convergent vers le poing
                        converge(pose, col, f, 0.5F * (1.0F - (e - st) / (s - st)) + 0.15F, k, age, seed, light);
                    }
                    if (e >= s && e < s + 5.0F) { // le coup : l'eau jaillit du poing dans le sens du coup
                        float b = (e - s) / 5.0F;
                        for (int i = 0; i < 6; i++) {
                            double an = i * Math.PI / 3;
                            float[] p = {f[0] + (float) Math.cos(an) * 0.18F * (1.0F + b), f[1] + 0.1F + 0.7F * b, f[2] + (float) Math.sin(an) * 0.18F * (1.0F + b)};
                            this.water.drop(pose, col, p, new float[] {(float) Math.cos(an) * 0.3F, 1.0F, (float) Math.sin(an) * 0.3F}, 0.07F, 1.4F, 1.0F - b, light);
                        }
                    }
                }
            }
            case Fishman.SPEAR -> {
                // de nombreuses gouttes apparaissent, se rassemblent et tournent autour de la main ; elles partent au jet
                float g = FishmanSpearAbility.GATHER, th = FishmanSpearAbility.THROW;
                float k = FishmanClient.ramp(e, 0.0F, g * 0.6F) * (1.0F - FishmanClient.snap(e, th - 0.5F, th + 0.5F));
                // entre deux salves : quelques gouttes se reforment vite autour des mains (moins que la première fois)
                int[] throws_ = FishmanSpearAbility.THROWS;
                for (int i = 1; i < throws_.length; i++) {
                    k = Math.max(k, 0.6F * FishmanClient.ramp(e, throws_[i - 1] + 1.5F, throws_[i] - 2.0F)
                            * (1.0F - FishmanClient.snap(e, throws_[i] - 0.5F, throws_[i] + 0.5F)));
                }
                if (k > 0.01F) {
                    float tight = FishmanClient.ramp(e, 0.0F, g);
                    Random r = new Random(seed * 13L);
                    int n = 4 + (int) (10 * FishmanClient.ramp(e, 0.0F, g));
                    for (int i = 0; i < n; i++) {
                        float rad = (0.55F - 0.3F * tight) * (0.7F + 0.6F * r.nextFloat());
                        double an = age * (0.35F + 0.25F * tight) * (r.nextBoolean() ? 1 : -1) + r.nextDouble() * Math.PI * 2;
                        float h = (r.nextFloat() - 0.5F) * 0.35F;
                        float[] p = {f[0] + (float) Math.cos(an) * rad, f[1] + 0.05F + h, f[2] + (float) Math.sin(an) * rad};
                        float[] tang = {(float) -Math.sin(an), 0.0F, (float) Math.cos(an)};
                        this.water.drop(pose, col, p, tang, 0.055F + 0.025F * r.nextFloat(), 0.8F, k, light);
                    }
                    swirl(pose, col, f, 0.16F, 4, 0.6F * k * tight, -age * 0.6F, 0.05F, light);
                }
            }
            case Fishman.TIDE -> {
                // l'eau s'accumule dans les deux mains jusqu'à la frappe
                float s = FishmanTideAbility.SLAM;
                float k = FishmanClient.ramp(e, 2.0F, s - 2.0F) * (1.0F - FishmanClient.snap(e, s - 0.5F, s + 1.0F));
                swirl(pose, col, f, 0.2F + 0.12F * k, 7, k, age * 0.45F * (left ? -1 : 1), 0.08F, light);
                if (k > 0.2F) {
                    this.water.blob(pose, col, new float[] {f[0], f[1] + 0.12F, f[2]}, 0.12F * k, age * 20.0F, 0.8F * k, light);
                }
            }
            case Fishman.DEPTHS -> {
                if (!left) {
                    depths(pose, col, f, e, age, seed, light);
                }
            }
            default -> {
            }
        }
        pose.popPose();
    }

    /** Anneau de petites masses d'eau qui tournent autour de la main (axe du bras). */
    private void swirl(PoseStack pose, SubmitNodeCollector col, float[] c, float rad, int n, float k, float spin, float size, int light) {
        if (k <= 0.01F) {
            return;
        }
        for (int i = 0; i < n; i++) {
            double an = spin + i * Math.PI * 2 / n;
            float[] p = {c[0] + (float) Math.cos(an) * rad, c[1] + 0.04F * Mth.sin(spin * 2.0F + i), c[2] + (float) Math.sin(an) * rad};
            float[] tang = {(float) -Math.sin(an), 0.0F, (float) Math.cos(an)};
            this.water.drop(pose, col, p, tang, size * (0.8F + 0.4F * k), 1.2F, k, light);
        }
        float[] a0 = {c[0] + rad, c[1], c[2]};
        this.water.glint(pose, col, a0, new float[] {c[0], c[1], c[2] + rad}, 0.4F, argb(0.4F * k, SHINE));
    }

    /** Filets d'eau qui convergent vers la main (la force se concentre). */
    private void converge(PoseStack pose, SubmitNodeCollector col, float[] c, float rad, float k, float age, int seed, int light) {
        Random r = new Random(seed * 7L + (long) (age * 0.5F));
        for (int i = 0; i < 4; i++) {
            double an = r.nextDouble() * Math.PI * 2;
            float y = (r.nextFloat() - 0.5F) * 0.4F;
            float[] p = {c[0] + (float) Math.cos(an) * rad, c[1] + y, c[2] + (float) Math.sin(an) * rad};
            this.water.drop(pose, col, p, new float[] {c[0] - p[0], c[1] - p[1], c[2] - p[2]}, 0.05F, 1.6F, 0.8F * k, light);
        }
    }

    /**
     * Perforation des profondeurs : deux brins d'eau montent en spirale le long du bras jusqu'au poing, de plus en plus épais ;
     * autour du poing, une grande spirale en cône (plusieurs couches qui tournent en sens opposés) enfle et file au-delà ; des
     * gouttes sont arrachées. À la frappe, tout est projeté (l'effet FishmanFx.SPIRAL prend le relais) et s'efface.
     */
    private void depths(PoseStack pose, SubmitNodeCollector col, float[] f, float e, float age, int seed, int light) {
        float c = FishmanDepthsAbility.CHARGE, s = FishmanDepthsAbility.STRIKE;
        float k = FishmanClient.ramp(e, 0.0F, c) * (1.0F - FishmanClient.snap(e, s - 0.5F, s + 3.0F));
        if (k <= 0.01F) {
            return;
        }
        float spin = age * (0.5F + 0.9F * k);
        // brins le long du bras (de l'épaule au poing)
        for (int st = 0; st < 2; st++) {
            float[] prev = null;
            for (int i = 0; i <= 10; i++) {
                float t = i / 10.0F;
                if (t > 0.25F + 0.75F * Math.min(1.0F, k * 1.6F)) {
                    break; // l'eau monte progressivement le long du bras
                }
                float rad = (0.2F + 0.1F * t) * (0.7F + 0.5F * k);
                double an = spin + t * 9.0 + st * Math.PI;
                float[] p = {f[0] + (float) Math.cos(an) * rad, -0.1F + t * (f[1] + 0.1F), f[2] + (float) Math.sin(an) * rad};
                if (prev != null) {
                    float th = 0.05F + 0.06F * k * t;
                    this.water.bar(pose, col, false, prev, p, th, th, 0.0F, argb(0.7F * k, st == 0 ? WATER : LIGHT), light);
                }
                prev = p;
            }
        }
        // grande spirale en cône autour du poing (deux couches en sens opposés)
        for (int layer = 0; layer < 2; layer++) {
            float dirSpin = layer == 0 ? 1.0F : -1.3F;
            for (int st = 0; st < 3; st++) {
                float[] prev = null;
                for (int i = 0; i <= 8; i++) {
                    float t = i / 8.0F;
                    float rad = (0.18F + (0.35F + 0.25F * layer) * k * (1.0F - 0.6F * t)) * (0.8F + 0.2F * Mth.sin(age * 0.7F + st));
                    double an = spin * 1.6F * dirSpin + t * 6.0 + st * Math.PI * 2 / 3;
                    float[] p = {f[0] + (float) Math.cos(an) * rad, f[1] - 0.15F + t * (0.45F + 0.55F * k), f[2] + (float) Math.sin(an) * rad};
                    if (prev != null) {
                        float th = (0.07F + 0.07F * k) * (1.0F - 0.4F * t);
                        this.water.bar(pose, col, layer == 1 && i % 3 == 0, prev, p, th, th * 0.8F, 0.0F,
                                argb((layer == 0 ? 0.7F : 0.5F) * k, layer == 1 && i % 3 == 0 ? FOAM : layer == 0 ? WATER : LIGHT), light);
                    }
                    prev = p;
                }
            }
        }
        this.water.blob(pose, col, new float[] {f[0], f[1] + 0.1F, f[2]}, 0.1F + 0.12F * k, spin * 30.0F, k, light);
        // gouttes arrachées par la rotation
        Random r = new Random(seed * 3L);
        for (int i = 0; i < (int) (8 * k); i++) {
            float ph = (age * 0.25F + r.nextFloat()) % 1.0F;
            double an = r.nextDouble() * Math.PI * 2 + spin;
            float rad = 0.35F + 0.8F * ph;
            float[] p = {f[0] + (float) Math.cos(an) * rad, f[1] + (r.nextFloat() - 0.3F) * 0.5F + 0.3F * ph, f[2] + (float) Math.sin(an) * rad};
            this.water.drop(pose, col, p, new float[] {(float) -Math.sin(an), 0.2F, (float) Math.cos(an)}, 0.05F, 1.0F, k * (1.0F - ph), light);
        }
    }

    // ------------------------------------------------------------------ première personne

    /** Première personne : aileron du bras vu et eau des attaques autour de la main (AvatarRendererHandMixin). */
    public static void submitHand(PoseStack pose, SubmitNodeCollector col, ModelPart arm, PlayerModel model, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (shared == null || mc.player == null || !FishmanClient.isFishman(mc.player)) {
            return;
        }
        boolean left = arm == model.leftArm;
        boolean slim = mc.player.getSkin().model() == PlayerModelType.SLIM;
        shared.arm(pose, col, arm, left, slim, light);
        float age = mc.player.tickCount + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int kind = FishmanClient.kind(mc.player);
        float e = FishmanClient.elapsed(mc.player, age);
        if (kind != 0 && e >= 0.0F) {
            shared.hands(pose, col, arm, left, slim, kind, e, age, mc.player.getId() + (left ? 5 : 0), light);
        }
    }
}
