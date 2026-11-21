package nolan.bloxpiece.client.entity;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.fx.MorganQuake;
import nolan.bloxpiece.mob.ai.MorganAxeGoal;

/**
 * Impact du coup de hache au sol de Morgan (MorganQuake) : effet propre à Morgan, dans le repère de l'impact (devant lui : +z).
 * Les bords de l'effet sont ceux de la zone de dégâts (MorganAxeGoal : éventail jusqu'à SLAM_RANGE blocs devant Morgan).
 * <ol>
 *   <li>Cratère : dalles de sol fendues et soulevées autour du point d'impact, éclats sombres en étoile.</li>
 *   <li>Crevasses : 7 grandes fissures dentelées (et leurs ramifications) qui filent en éventail devant lui, découvertes au fur
 *       et à mesure que l'onde avance (cœur sombre, bord de terre plus clair), puis qui s'estompent.</li>
 *   <li>Onde de choc : un front de poussière et d'air (mur de terre soulevée, légèrement bombé, et une ligne claire au ras du
 *       sol) qui avance jusqu'au bout de la zone en s'élargissant comme elle, puis se dissipe ; un anneau d'air bref autour du
 *       point d'impact.</li>
 *   <li>Débris : blocs de roche et de terre arrachés qui sautent en tournoyant et retombent.</li>
 * </ol>
 * Tout est tiré au sort à partir de l'identifiant de l'entité : le même dessin pour tous les joueurs.
 */
public class MorganQuakeRenderer extends EntityRenderer<MorganQuake, MorganQuakeRenderer.QuakeState> {

    private static final int CRACK = 0x1C1814, CRACK_EDGE = 0x6E5E4C, DUST = 0xC8B596, DUST_LIGHT = 0xEDE2CC, ROCK = 0x7A736B,
            DIRT = 0x6A4E38;
    /** Zone de dégâts dans le repère de l'impact : de NEAR à FAR devant, demi-largeur HALF0 + SPREAD x distance. */
    private static final float NEAR = (float) (0.3 - MorganQuake.IMPACT_AHEAD);
    private static final float FAR = (float) (MorganAxeGoal.SLAM_RANGE - MorganQuake.IMPACT_AHEAD);
    private static final float SPREAD = (float) MorganAxeGoal.SLAM_SPREAD;
    private static final float HALF0 = (float) (MorganAxeGoal.SLAM_HALF_WIDTH + MorganAxeGoal.SLAM_SPREAD * MorganQuake.IMPACT_AHEAD);

    public static class QuakeState extends EntityRenderState {
        public float age;
        public float yaw;
        public int seed;
    }

    /** Un tronçon de fissure : de (x0, z0) à (x1, z1), à la distance « along » du départ, d'épaisseur relative w. */
    private record Seg(float x0, float z0, float x1, float z1, float along, float w) {
    }

    private final ModelPart cube;

    public MorganQuakeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.cube = context.bakeLayer(MorganFx.LAYER).getChild("cube");
    }

    @Override
    public QuakeState createRenderState() {
        return new QuakeState();
    }

    @Override
    public void extractRenderState(MorganQuake entity, QuakeState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.yaw = entity.getYRot();
        state.seed = entity.getId();
    }

    private static float half(float along) {
        return HALF0 + SPREAD * along;
    }

    /** Distance atteinte par le front de l'onde (repère de l'impact) : jaillit vite, puis ralentit. */
    private static float front(float age) {
        float k = Mth.clamp(age / MorganQuake.SPREAD_TICKS, 0.0F, 1.0F);
        return NEAR + (FAR - NEAR) * (1.0F - (1.0F - k) * (1.0F - k));
    }

    private static List<Seg> cracks(int seed) {
        RandomSource r = RandomSource.create(seed * 341873128712L + 132897987541L);
        List<Seg> out = new ArrayList<>();
        int main = 7;
        for (int c = 0; c < main; c++) {
            float end = FAR * (0.72F + 0.28F * r.nextFloat());
            float side = ((c + 0.5F) / main * 2.0F - 1.0F) * 0.85F;      // -1 .. 1 : position finale en travers de l'éventail
            float x = 0.0F, z = 0.0F;
            int steps = 9;
            for (int s = 1; s <= steps; s++) {
                float zt = end * s / steps;
                float xt = side * half(zt) * ((float) s / steps) + (r.nextFloat() - 0.5F) * 0.55F;
                float w = 1.0F - 0.75F * s / steps;
                out.add(new Seg(x, z, xt, zt, z, w));
                if (s > 2 && s < steps && r.nextFloat() < 0.35F) {        // ramification
                    float bx = x, bz = z, dir = r.nextBoolean() ? 1.0F : -1.0F;
                    for (int b = 1; b <= 3; b++) {
                        float nz = bz + 0.45F + r.nextFloat() * 0.4F;
                        float nx = bx + dir * (0.35F + r.nextFloat() * 0.35F);
                        out.add(new Seg(bx, bz, nx, nz, bz, w * 0.55F * (1.0F - b / 4.0F)));
                        bx = nx;
                        bz = nz;
                    }
                }
                x = xt;
                z = zt;
            }
        }
        return out;
    }

    @Override
    public void submit(QuakeState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float age = state.age;
        float fade = age < 20.0F ? 1.0F : Math.max(0.0F, 1.0F - (age - 20.0F) / (MorganQuake.LIFE - 20.0F));
        if (fade > 0.0F) {
            pose.pushPose();
            pose.rotateDegrees(Axis.YP, -state.yaw);   // repère de l'impact : +z = devant Morgan
            int light = state.lightCoords;
            float reach = front(age);
            float y = 0.03F;
            // --- cratère : dalles fendues soulevées et éclats en étoile
            RandomSource r = RandomSource.create(state.seed * 9871L + 7L);
            for (int i = 0; i < 12; i++) {
                float a = (float) (Math.PI * 2.0 * (i + r.nextFloat() * 0.5F) / 12.0);
                float len = 0.7F + r.nextFloat() * 0.8F;
                MorganFx.piece(this.cube, pose, collector, 0, light, 0.0F, y, 0.0F, (float) Math.toDegrees(a), 0.0F, len, 2.5F, 0.6F,
                        MorganFx.argb(0.9F * fade, CRACK));
                float rr = 0.5F + r.nextFloat() * 0.7F;                   // dalle soulevée, inclinée
                float sx = (float) Math.sin(a + 0.26F) * rr, sz = (float) Math.cos(a + 0.26F) * rr;
                float rise = Math.min(1.0F, age / 2.0F);
                MorganFx.piece(this.cube, pose, collector, 0, light, sx, y + 0.06F * rise, sz, (float) Math.toDegrees(a) + 90.0F,
                        -(15.0F + r.nextFloat() * 20.0F) * rise, 0.35F + r.nextFloat() * 0.25F, 5.0F, 2.0F,
                        MorganFx.argb(fade, r.nextBoolean() ? ROCK : DIRT));
            }
            // --- crevasses : découvertes par le front de l'onde
            for (Seg s : cracks(state.seed)) {
                if (s.along() > reach) {
                    continue;
                }
                float dx = s.x1() - s.x0(), dz = s.z1() - s.z0();
                float len = (float) Math.sqrt(dx * dx + dz * dz);
                float shown = dz <= 0.01F ? len : len * Mth.clamp((reach - s.z0()) / dz, 0.0F, 1.0F); // découverte progressive
                float yawDeg = (float) Math.toDegrees(Math.atan2(dx, dz));
                MorganFx.piece(this.cube, pose, collector, 0, light, s.x0(), y - 0.005F, s.z0(), yawDeg, 0.0F, shown + 0.05F,
                        7.0F * s.w() + 1.5F, 0.4F, MorganFx.argb(0.55F * fade, CRACK_EDGE));
                MorganFx.piece(this.cube, pose, collector, 0, light, s.x0(), y, s.z0(), yawDeg, 0.0F, shown + 0.05F,
                        3.2F * s.w() + 0.8F, 0.5F, MorganFx.argb(0.92F * fade, CRACK));
            }
            // --- onde de choc : front de poussière bombé qui avance jusqu'au bout de la zone, puis se dissipe
            float k = Mth.clamp(age / MorganQuake.SPREAD_TICKS, 0.0F, 1.0F);
            float wave = age < MorganQuake.SPREAD_TICKS + 6.0F
                    ? (1.0F - Math.max(0.0F, age - MorganQuake.SPREAD_TICKS) / 6.0F) : 0.0F;
            if (wave > 0.0F) {
                float hw = half(reach);
                int parts = 14;
                for (int i = 0; i < parts; i++) {
                    float l0 = -hw + 2.0F * hw * i / parts, l1 = -hw + 2.0F * hw * (i + 1) / parts;
                    float z0 = reach - 0.05F * l0 * l0, z1 = reach - 0.05F * l1 * l1;   // centre en avance : front bombé
                    float dx = l1 - l0, dz = z1 - z0;
                    float len = (float) Math.sqrt(dx * dx + dz * dz);
                    float yawDeg = (float) Math.toDegrees(Math.atan2(dx, dz));
                    float tall = 26.0F * (1.0F - 0.45F * k);
                    MorganFx.piece(this.cube, pose, collector, 1, MorganFx.FULL_BRIGHT, l0, y + tall / 32.0F, z0, yawDeg, 0.0F,
                            len + 0.04F, 7.0F, tall, MorganFx.argb(0.42F * wave, DUST));
                    MorganFx.piece(this.cube, pose, collector, 1, MorganFx.FULL_BRIGHT, l0, y + 0.05F, z0, yawDeg, 0.0F, len + 0.04F,
                            14.0F, 1.6F, MorganFx.argb(0.7F * wave, DUST_LIGHT));
                }
            }
            // anneau d'air bref autour du point d'impact
            if (age < 7.0F) {
                float ra = 0.4F + 3.2F * (age / 7.0F);
                float aa = 0.5F * (1.0F - age / 7.0F);
                for (int i = 0; i < 24; i++) {
                    double a0 = Math.PI * 2.0 * i / 24, a1 = Math.PI * 2.0 * (i + 1) / 24;
                    float x0 = (float) Math.sin(a0) * ra, z0 = (float) Math.cos(a0) * ra;
                    float x1 = (float) Math.sin(a1) * ra, z1 = (float) Math.cos(a1) * ra;
                    MorganFx.piece(this.cube, pose, collector, 1, MorganFx.FULL_BRIGHT, x0, y + 0.3F, z0,
                            (float) Math.toDegrees(Math.atan2(x1 - x0, z1 - z0)), 0.0F,
                            (float) Math.hypot(x1 - x0, z1 - z0) + 0.03F, 3.0F, 9.0F, MorganFx.argb(aa, DUST_LIGHT));
                }
            }
            // --- débris : blocs arrachés qui sautent en tournoyant et retombent
            RandomSource d = RandomSource.create(state.seed * 55L + 3L);
            for (int i = 0; i < 14; i++) {
                float a = (float) ((d.nextFloat() - 0.5F) * 2.2F);          // surtout vers l'avant
                float speed = 0.06F + d.nextFloat() * 0.12F, up = 0.28F + d.nextFloat() * 0.22F;
                float t = Math.min(age, 26.0F);
                float h = up * t - 0.03F * t * t;
                if (h < -0.05F) {
                    continue;                                               // retombé au sol
                }
                float px = (float) Math.sin(a) * (0.3F + speed * t), pz = (float) Math.cos(a) * (0.3F + speed * t);
                float size = 3.0F + d.nextFloat() * 3.0F;
                MorganFx.piece(this.cube, pose, collector, 0, light, px, y + Math.max(0.0F, h) + size / 32.0F, pz,
                        age * (12.0F + d.nextFloat() * 20.0F), age * (9.0F + d.nextFloat() * 14.0F), size / 16.0F, size, size,
                        MorganFx.argb(fade, d.nextBoolean() ? ROCK : DIRT));
            }
            pose.popPose();
        }
        super.submit(state, pose, collector, camera);
    }

    @Override
    protected AABB getBoundingBoxForCulling(MorganQuake entity, float partialTick) {
        return entity.getBoundingBox().inflate(MorganAxeGoal.SLAM_RANGE + 4.0, 3.0, MorganAxeGoal.SLAM_RANGE + 4.0);
    }
}
