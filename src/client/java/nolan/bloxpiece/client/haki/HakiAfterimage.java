package nolan.bloxpiece.client.haki;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Haki de l'Observation : IMAGE RÉMANENTE. À chaque esquive, une copie translucide du joueur reste figée là où il se tenait, dans
 * la pose qu'il avait juste avant d'esquiver, pendant que son vrai corps s'écarte ; elle s'estompe en environ une seconde
 * (illusion de vitesse : le coup traverse un mirage).
 *
 * La pose (pivots et rotations des 6 parties du corps), la position et l'orientation du joueur sont relevées à la première image
 * de l'esquive (PlayerModelMixin, avant l'animation d'esquive). La copie est dessinée par HakiEyeTrailLayer avec son propre modèle
 * de joueur ({@link GhostModel}, standard ou fin selon le skin), qui reprend cette pose au moment du dessin.
 */
public final class HakiAfterimage {

    /** Durée de l'image rémanente (ticks). */
    private static final float LIFE = 20.0F;
    /** Opacité de départ et teinte (blanc légèrement bleuté). */
    private static final float START_ALPHA = 0.5F;
    private static final int TINT = 0xD2DEFF;
    /** Mise à l'échelle du modèle du joueur par Minecraft (15/16), et hauteur de son pivot. */
    private static final float PLAYER_SCALE = 0.9375F;
    private static final float MODEL_PIVOT = 1.501F;

    /** Pose figée : [partie][x, y, z, xRot, yRot, zRot] (tête, corps, bras droit, bras gauche, jambe droite, jambe gauche). */
    private record Snapshot(float[][] parts, double x, double y, double z, float bodyYaw, long tick, float partial) {
    }

    private static final Set<Integer> PENDING = new HashSet<>();
    private static final Map<Integer, Snapshot> GHOSTS = new HashMap<>();

    private HakiAfterimage() {
    }

    /** Paquet d'esquive reçu : la prochaine image de ce joueur sera figée. */
    public static void request(int entityId) {
        PENDING.add(entityId);
    }

    /** Appelé par PlayerModelMixin juste avant l'animation d'esquive : fige la pose actuelle si une image est attendue. */
    public static void capture(Entity entity, PlayerModel model, float partialTick) {
        if (!PENDING.remove(entity.getId()) || !(entity instanceof LivingEntity living)) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        ModelPart[] parts = parts(model);
        float[][] pose = new float[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            ModelPart p = parts[i];
            pose[i] = new float[] {p.x, p.y, p.z, p.xRot, p.yRot, p.zRot};
        }
        GHOSTS.put(entity.getId(), new Snapshot(pose,
                Mth.lerp(partialTick, living.xo, living.getX()),
                Mth.lerp(partialTick, living.yo, living.getY()),
                Mth.lerp(partialTick, living.zo, living.getZ()),
                Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot), level.getGameTime(), partialTick));
    }

    private static ModelPart[] parts(PlayerModel model) {
        return new ModelPart[] {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
    }

    /** Âge (ticks) de l'image rémanente de cette entité, ou -1 s'il n'y en a pas (ou plus). */
    private static float age(int entityId, float partialTick) {
        Snapshot snap = GHOSTS.get(entityId);
        ClientLevel level = Minecraft.getInstance().level;
        if (snap == null || level == null) {
            return -1.0F;
        }
        float age = (level.getGameTime() - snap.tick()) + partialTick - snap.partial();
        if (age > LIFE || age < -1.0F) {
            GHOSTS.remove(entityId);
            return -1.0F;
        }
        return Math.max(0.0F, age);
    }

    /**
     * Dessine l'image rémanente du joueur de {@code state}, s'il en a une, depuis le repère de son modèle (celui d'une couche de
     * rendu) : on revient au repère du monde, on se place là où il se tenait avec l'orientation qu'il avait, puis on reprend le
     * repère d'un modèle de joueur.
     */
    public static void render(PoseStack pose, SubmitNodeCollector collector, AvatarRenderState state, Entity entity, GhostModel model,
                              net.minecraft.resources.Identifier skin) {
        float partial = state.ageInTicks - (float) Math.floor(state.ageInTicks);
        float age = age(state.id, partial);
        if (age < 0.0F || !(entity instanceof LivingEntity living)) {
            return;
        }
        Snapshot snap = GHOSTS.get(state.id);
        float t = age / LIFE;
        float alpha = START_ALPHA * (1.0F - t) * (1.0F - t);
        if (alpha <= 0.01F) {
            return;
        }
        double x = Mth.lerp(partial, living.xo, living.getX());
        double y = Mth.lerp(partial, living.yo, living.getY());
        double z = Mth.lerp(partial, living.zo, living.getZ());
        float bodyYaw = Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot);
        pose.pushPose();
        // repère du modèle -> repère du joueur dans le monde (inverse de ce que fait le rendu des entités vivantes)
        pose.translate(0.0F, MODEL_PIVOT, 0.0F);
        pose.scale(1.0F / PLAYER_SCALE, 1.0F / PLAYER_SCALE, 1.0F / PLAYER_SCALE);
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.rotateDegrees(Axis.YP, -(180.0F - bodyYaw));
        // là où il se tenait, tourné comme il l'était
        pose.translate((float) (snap.x() - x), (float) (snap.y() - y), (float) (snap.z() - z));
        pose.rotateDegrees(Axis.YP, 180.0F - snap.bodyYaw());
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
        pose.translate(0.0F, -MODEL_PIVOT, 0.0F);
        int a = (int) (alpha * 255.0F);
        collector.order(7).submitModel(model, state, pose, RenderTypes.entityTranslucent(skin), 0xF000F0, OverlayTexture.NO_OVERLAY,
                (a << 24) | TINT, null, state.outlineColor);
        pose.popPose();
    }

    public static void clear() {
        PENDING.clear();
        GHOSTS.clear();
    }

    /** Modèle de joueur de l'image rémanente : pose normale (seconde couche du skin comprise), puis la pose figée de l'esquive. */
    public static final class GhostModel extends PlayerModel {

        public GhostModel(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void setupAnim(AvatarRenderState state) {
            super.setupAnim(state);
            Snapshot snap = GHOSTS.get(state.id);
            if (snap == null) {
                return;
            }
            ModelPart[] parts = parts(this);
            for (int i = 0; i < parts.length; i++) {
                float[] p = snap.parts()[i];
                ModelPart part = parts[i];
                part.x = p[0];
                part.y = p[1];
                part.z = p[2];
                part.xRot = p[3];
                part.yRot = p[4];
                part.zRot = p[5];
            }
        }
    }
}
