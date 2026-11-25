package nolan.bloxpiece.client.cyborg;

import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;

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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.ability.Cyborg;
import nolan.bloxpiece.ability.CyborgCannonArmAbility;
import nolan.bloxpiece.ability.CyborgRadicalBeamAbility;
import nolan.bloxpiece.client.goro.GoroLightning;

/**
 * Race Cyborg : le BRAS GAUCHE MÉCANIQUE (CyborgArmModels) dessiné à la place du bras gauche vanilla (caché par CyborgPose), sur
 * sa pose exacte (ModelPart.translateAndRotate du vrai bras) : il suit la marche, la course, le saut, les coups et toutes les
 * poses. Chez tous les joueurs (race synchronisée). Animations :
 * <ul>
 *   <li>Cannon Arm : les quatre plaques de l'avant-bras s'ouvrent sur leurs charnières (en décalé), les deux volets de l'embout
 *       s'écartent, le canon caché sort de l'avant-bras et se verrouille ; recul au tir ; puis tout se replie dans l'ordre
 *       inverse.</li>
 *   <li>Surcharge moteur : le bras chauffe (seconde passe lumineuse orange qui pulse) ; le reste des effets (réacteur,
 *       conduits, gyroscopes, arcs) : CyborgOverdriveLayer.</li>
 *   <li>Radical Beam : une boule d'énergie jaune grossit entre les mains (rayons, arcs), puis éclate au tir.</li>
 * </ul>
 * Première personne : {@link #renderFirstPerson} (main gauche, quand Minecraft la dessine).
 */
public class CyborgArmLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public static final Identifier TEXTURE = BloxPiece.id("textures/entity/cyborg_arm.png");
    private static final int HOT = 0xFF8A2A, ENERGY = 0xFFE24A, ENERGY_CORE = 0xFFF8D0;

    /** Dernier modèle construit (aussi pour la vue à la première personne). */
    private static Rig shared;
    private final Rig rig;
    private final ModelPart segment;

    /** Les pièces du bras mécanique, et leur animation. */
    static final class Rig {
        final ModelPart arm, plateFront, plateBack, plateOuter, plateInner, barrel, capLeft, capRight;
        final List<ModelPart> animated;

        Rig(ModelPart root) {
            this.arm = root.getChild("arm");
            this.plateFront = arm.getChild("plate_front");
            this.plateBack = arm.getChild("plate_back");
            this.plateOuter = arm.getChild("plate_outer");
            this.plateInner = arm.getChild("plate_inner");
            this.barrel = arm.getChild("barrel");
            this.capLeft = arm.getChild("cap_left");
            this.capRight = arm.getChild("cap_right");
            this.animated = List.of(plateFront, plateBack, plateOuter, plateInner, barrel, capLeft, capRight);
            // pose de repos de chaque pièce animée (telle que générée), remise à chaque image avant l'animation
            this.rest = new float[this.animated.size()][];
            for (int i = 0; i < this.rest.length; i++) {
                ModelPart p = this.animated.get(i);
                this.rest[i] = new float[] {p.x, p.y, p.z, p.xRot, p.yRot, p.zRot};
            }
        }

        private final float[][] rest;

        /**
         * Pose du bras pour la transformation en canon : {@code k} 0 = repos, 1 = canon déployé ; {@code recoil} 0..1. Les
         * pièces s'enchaînent : plaques d'abord, puis volets de l'embout, puis canon (et l'inverse au repli).
         */
        void pose(float k, float recoil) {
            for (int i = 0; i < animated.size(); i++) { // retour à la pose de repos
                ModelPart p = animated.get(i);
                float[] r = rest[i];
                p.x = r[0];
                p.y = r[1];
                p.z = r[2];
                p.xRot = r[3];
                p.yRot = r[4];
                p.zRot = r[5];
            }
            float plates = ramp(k, 0.0F, 0.55F), caps = ramp(k, 0.2F, 0.7F), gun = ramp(k, 0.45F, 1.0F);
            float a = 0.95F * plates;
            plateFront.xRot = -a;
            plateBack.xRot = a;
            plateOuter.zRot = -0.95F * ramp(k, 0.08F, 0.6F); // plaques latérales : un peu en décalé
            plateInner.zRot = a;
            capLeft.zRot = 1.35F * caps;                     // les volets de l'embout s'écartent : la sortie du canon s'ouvre
            capRight.zRot = -1.35F * caps;
            this.barrel.visible = k > 0.02F;
            this.barrel.y += 4.2F * gun - 1.1F * recoil;  // le canon sort de l'avant-bras (et recule au tir)
        }

        private static float ramp(float t, float a, float b) {
            return CyborgClient.ramp(t, a, b);
        }
    }

    public CyborgArmLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ModelPart root, ModelPart segment) {
        super(parent);
        this.rig = new Rig(root);
        this.segment = segment;
        shared = this.rig;
    }

    /** Transformation en canon (0..1) et recul (0..1) de ce joueur à cet instant. */
    static float[] cannon(Entity entity, float ageInTicks) {
        if (CyborgClient.kind(entity) != Cyborg.CANNON) {
            return new float[] {0.0F, 0.0F};
        }
        float e = CyborgClient.elapsed(entity, ageInTicks);
        float k = CyborgClient.ramp(e, CyborgCannonArmAbility.RAISE, CyborgCannonArmAbility.ARMED)
                * (1.0F - CyborgClient.ramp(e, CyborgCannonArmAbility.RETRACT, CyborgCannonArmAbility.DURATION - 8));
        float recoil = e >= CyborgCannonArmAbility.FIRE && e < CyborgCannonArmAbility.FIRE + 6
                ? 1.0F - (e - CyborgCannonArmAbility.FIRE) / 6.0F : 0.0F;
        return new float[] {k, recoil};
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(state.id);
        if (entity == null || !CyborgClient.isCyborg(entity)) {
            return;
        }
        PlayerModel model = this.getParentModel();
        float age = state.ageInTicks;
        float[] cannon = cannon(entity, age);
        float engine = CyborgClient.engine(entity, age);
        pose.pushPose();
        model.leftArm.translateAndRotate(pose); // exactement sur le bras gauche (même quand il est caché)
        drawArm(this.rig, pose, collector, light, cannon[0], cannon[1], engine, age);
        pose.popPose();

        if (CyborgClient.kind(entity) == Cyborg.BEAM) {
            drawBeamCharge(pose, collector, model, CyborgClient.elapsed(entity, age), age, state.id);
        }
    }

    private static void drawArm(Rig rig, PoseStack pose, SubmitNodeCollector collector, int light, float k, float recoil, float engine,
                                float age) {
        rig.pose(k, recoil);
        rig.arm.visible = true;
        collector.submitModelPart(rig.arm, pose, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
        if (engine > 0.0F) {
            // surcharge : le métal chauffe (passe lumineuse orange qui pulse)
            float pulse = 0.55F + 0.45F * Mth.sin(age * 0.9F);
            collector.order(1).submitModelPart(rig.arm, pose, RenderTypes.entityTranslucentEmissive(TEXTURE), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, null, GoroLightning.argb(0.25F + 0.4F * engine * pulse, HOT));
        }
    }

    /** Boule d'énergie jaune entre les mains tendues (Radical Beam). */
    private void drawBeamCharge(PoseStack pose, SubmitNodeCollector collector, PlayerModel model, float e, float age, int seed) {
        float charge = CyborgClient.ramp(e, 2.0F, CyborgRadicalBeamAbility.CHARGE);
        float burst = e >= CyborgRadicalBeamAbility.CHARGE ? Math.max(0.0F, 1.0F - (e - CyborgRadicalBeamAbility.CHARGE) / 6.0F) : 1.0F;
        if (charge <= 0.0F || burst <= 0.0F) {
            return;
        }
        RenderType glow = RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
        pose.pushPose();
        model.body.translateAndRotate(pose);
        pose.translate(0.0F, 6.0F / 16.0F, -12.0F / 16.0F); // devant la poitrine, là où les mains se rejoignent
        float r = (0.06F + 0.32F * charge) * (e >= CyborgRadicalBeamAbility.CHARGE ? 1.0F + (1.0F - burst) * 1.5F : 1.0F);
        float pulse = 0.85F + 0.15F * Mth.sin(age * 2.2F);
        float[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0.7F, 0.7F, 0}, {0.7F, 0, 0.7F}, {0, 0.7F, 0.7F}};
        for (int layer = 0; layer < 3; layer++) {
            float lr = r * (0.45F + 0.35F * layer) * pulse;
            int color = layer == 0 ? ENERGY_CORE : layer == 1 ? ENERGY : HOT;
            float alpha = (layer == 0 ? 0.95F : layer == 1 ? 0.5F : 0.2F) * burst;
            for (float[] ax : axes) {
                GoroLightning.segment(this.segment, pose, collector, layer, glow, new float[] {-ax[0] * lr, -ax[1] * lr, -ax[2] * lr},
                        new float[] {ax[0] * lr, ax[1] * lr, ax[2] * lr}, lr * 16.0F * 1.1F, GoroLightning.argb(alpha, color));
            }
        }
        // petits arcs jaunes qui jaillissent de la boule
        Random random = new Random(seed * 17L + (long) (age * 2.0F));
        for (int i = 0; i < 3 + (int) (5 * charge); i++) {
            float[] d = GoroLightning.normalize(new float[] {random.nextFloat() - 0.5F, random.nextFloat() - 0.5F, random.nextFloat() - 0.5F});
            float len = r * (1.4F + random.nextFloat());
            GoroLightning.segment(this.segment, pose, collector, 2, glow, new float[] {d[0] * r, d[1] * r, d[2] * r},
                    new float[] {d[0] * len, d[1] * len, d[2] * len}, 0.5F, GoroLightning.argb(0.8F * burst, ENERGY));
        }
        pose.popPose();
    }

    /**
     * Première personne : la main gauche du joueur local Cyborg, dessinée par Minecraft (AvatarRenderer.renderHand), est remplacée
     * par le bras mécanique, sur la même pose (CyborgHandMixin).
     */
    public static boolean renderFirstPerson(PoseStack pose, SubmitNodeCollector collector, int light, ModelPart arm) {
        Minecraft mc = Minecraft.getInstance();
        if (shared == null || mc.player == null || !CyborgClient.isCyborg(mc.player)) {
            return false;
        }
        float age = mc.player.tickCount + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float[] cannon = cannon(mc.player, age);
        pose.pushPose();
        arm.translateAndRotate(pose);
        drawArm(shared, pose, collector, light, cannon[0], cannon[1], CyborgClient.engine(mc.player, age), age);
        pose.popPose();
        return true;
    }
}
