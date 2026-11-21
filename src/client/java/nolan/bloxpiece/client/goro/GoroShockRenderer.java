package nolan.bloxpiece.client.goro;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.client.haki.HakiBoltRenderer;
import nolan.bloxpiece.fx.GoroCloud;
import nolan.bloxpiece.fx.GoroShock;

/**
 * Cible électrisée par le Mamaragan (GoroShock), recalculé à CHAQUE image d'après la position interpolée de la cible (l'effet
 * colle à elle quand elle bouge) :
 * <ul>
 *   <li>LIEN : un éclair épais et ramifié relie en permanence le dessous du nuage à la poitrine de la cible (plus un second
 *       plus fin par moments) : on voit tout de suite quel nuage électrise quelle cible ;</li>
 *   <li>CORPS : plusieurs éclairs bleus traversent le corps d'un point à l'autre, et deux arcs s'enroulent en spirale des pieds
 *       à la tête, retracés à chaque tick ;</li>
 *   <li>AURA : une enveloppe de lumière bleue qui palpite autour du corps, plus vive à chaque coup de dégâts (flash) ;</li>
 *   <li>SOL : petits arcs qui crépitent autour des pieds (purement visuels : aucun bloc n'est touché).</li>
 * </ul>
 * Tailles calculées d'après la boîte de la cible : marche pour un poulet comme pour un joueur ou un mob géant.
 */
public class GoroShockRenderer extends EntityRenderer<GoroShock, GoroShockRenderer.State> {

    public static class State extends EntityRenderState {
        public boolean valid;
        public float age;
        public int life = 100;
        public int seed;
        public float flash;
        /** Pieds de la cible, relatifs à l'origine du rendu. */
        public float tx, ty, tz;
        public float width = 0.6F, height = 1.8F;
        /** Dessous du nuage, relatif (valide si hasCloud). */
        public boolean hasCloud;
        public float cx, cy, cz, cloudSize = 4.0F;
    }

    private final ModelPart segment;

    public GoroShockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.segment = context.bakeLayer(HakiBoltRenderer.LAYER).getChild("segment");
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GoroShock entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.life = Math.max(4, entity.life());
        state.seed = entity.getId();
        float since = state.age - entity.pulseTick();
        state.flash = since >= 0.0F && since < 6.0F ? 1.0F - since / 6.0F : 0.0F;
        Entity target = entity.level().getEntity(entity.targetId());
        state.valid = target != null && target.isAlive();
        if (!state.valid) {
            return;
        }
        Vec3 origin = entity.getPosition(partialTick);
        Vec3 t = target.getPosition(partialTick);
        state.tx = (float) (t.x - origin.x);
        state.ty = (float) (t.y - origin.y);
        state.tz = (float) (t.z - origin.z);
        state.width = target.getBbWidth();
        state.height = target.getBbHeight();
        Entity cloud = entity.level().getEntity(entity.cloudId());
        state.hasCloud = cloud instanceof GoroCloud;
        if (state.hasCloud) {
            GoroCloud gc = (GoroCloud) cloud;
            Vec3 c = gc.getPosition(partialTick);
            float size = gc.size();
            // point de départ : le dessous du nuage, du côté de la cible (le lien tombe presque à la verticale)
            double hx = t.x - c.x, hz = t.z - c.z, hd = Math.sqrt(hx * hx + hz * hz);
            double reach = Math.min(hd, size * 1.3);
            double ox = hd < 1.0E-3 ? 0.0 : hx / hd * reach, oz = hd < 1.0E-3 ? 0.0 : hz / hd * reach;
            state.cx = (float) (c.x + ox - origin.x);
            state.cy = (float) (c.y - size * 0.35 - origin.y);
            state.cz = (float) (c.z + oz - origin.z);
            state.cloudSize = size;
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float in = Math.min(1.0F, state.age / 3.0F);
        float out = Math.max(0.0F, Math.min(1.0F, (state.life - state.age) / 6.0F));
        float a = in * out;
        if (state.valid && a > 0.01F) {
            float boost = 1.0F + 0.6F * state.flash;
            float W = Math.max(0.4F, state.width), Hh = Math.max(0.4F, state.height);
            float size = (float) Math.sqrt(Math.max(0.5F, W * Hh)); // échelle des éclairs selon la taille de la cible
            float[] chest = {state.tx, state.ty + Hh * 0.6F, state.tz};
            Random jag = new Random(state.seed * 92_821L + (int) state.age * 15_485_863L);
            Random shape = new Random(state.seed * 1_000_003L + (int) (state.age / 2.0F) * 7_919L);
            // 1. le lien nuage -> cible
            if (state.hasCloud) {
                float[] from = {state.cx, state.cy, state.cz};
                float[] d = {chest[0] - from[0], chest[1] - from[1], chest[2] - from[2]};
                List<GoroLightning.Path> tether = GoroLightning.bolt(from, d, 1.0F * boost, state.age, state.seed, shape, jag, true);
                GoroLightning.draw(this.segment, pose, collector, tether, 1.0F, a * boost, a);
                if (jag.nextFloat() < 0.5F + 0.5F * state.flash) {
                    float[] f2 = {from[0] + (jag.nextFloat() - 0.5F) * state.cloudSize, from[1], from[2] + (jag.nextFloat() - 0.5F) * state.cloudSize};
                    float[] d2 = {state.tx - f2[0], state.ty + Hh * jag.nextFloat() - f2[1], state.tz - f2[2]};
                    GoroLightning.draw(this.segment, pose, collector, GoroLightning.bolt(f2, d2, 0.5F, state.age, state.seed + 7, jag, jag, true),
                            1.0F, a, a);
                }
            }
            // 2. éclairs qui traversent le corps d'un point à l'autre
            int across = 3 + (int) Math.min(4, size * 1.5F) + (state.flash > 0.0F ? 2 : 0);
            for (int i = 0; i < across; i++) {
                float[] p = this.onBody(state, W, Hh, jag), q = this.onBody(state, W, Hh, jag);
                float[] d = {q[0] - p[0], q[1] - p[1], q[2] - p[2]};
                List<GoroLightning.Path> paths = GoroLightning.bolt(p, d, 0.4F * size * boost, state.age, state.seed + 11 * i, jag, jag, true);
                GoroLightning.draw(this.segment, pose, collector, paths, 1.0F, a * boost, a);
            }
            // 3. deux arcs en spirale des pieds à la tête
            List<GoroLightning.Path> spirals = new ArrayList<>();
            for (int k = 0; k < 2; k++) {
                int n = 14;
                List<float[]> pts = new ArrayList<>(n);
                float[] widths = new float[n];
                float start = k * 3.1416F + state.age * (0.5F + 0.2F * k);
                for (int i = 0; i < n; i++) {
                    float t = i / (float) (n - 1);
                    float ang = start + t * 7.5F;
                    float r = W * (0.62F + 0.18F * jag.nextFloat());
                    pts.add(new float[] {state.tx + (float) Math.cos(ang) * r, state.ty + t * Hh * 1.05F, state.tz + (float) Math.sin(ang) * r});
                    widths[i] = (0.5F + 0.5F * (float) Math.sin(Math.PI * t)) * size * 0.45F * boost;
                }
                spirals.add(new GoroLightning.Path(pts, widths));
            }
            GoroLightning.draw(this.segment, pose, collector, spirals, 1.0F, a * boost, a);
            // 4. aura qui palpite autour du corps
            RenderType glow = RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
            float pulse = 0.85F + 0.15F * (float) Math.sin(state.age * 1.7F);
            this.aura(pose, collector, glow, state, W * 1.35F * pulse, Hh * 1.12F, state.age * 6.0F,
                    GoroLightning.argb(Math.min(0.6F, (0.22F + 0.3F * state.flash) * a), GoroLightning.SHEATH));
            this.aura(pose, collector, glow, state, W * 1.9F, Hh * 1.3F, -state.age * 3.0F,
                    GoroLightning.argb(Math.min(0.35F, (0.08F + 0.2F * state.flash) * a), GoroLightning.HAZE));
            // 5. arcs qui crépitent au sol autour des pieds (visuels)
            for (int i = 0; i < 3; i++) {
                if (jag.nextFloat() < 0.35F) {
                    continue;
                }
                double ang = jag.nextDouble() * Math.PI * 2;
                float[] from = {state.tx + (float) Math.cos(ang) * W * 0.4F, state.ty + 0.05F, state.tz + (float) Math.sin(ang) * W * 0.4F};
                float len = 1.0F + jag.nextFloat() * (1.0F + size);
                float[] d = {(float) Math.cos(ang) * len, 0.05F, (float) Math.sin(ang) * len};
                GoroLightning.draw(this.segment, pose, collector, GoroLightning.bolt(from, d, 0.35F, state.age, state.seed + 40 + i, jag, jag,
                        true), 1.0F, a, a);
            }
        }
        super.submit(state, pose, collector, camera);
    }

    /** Un point sur la surface du corps de la cible (cylindre autour de sa boîte). */
    private float[] onBody(State state, float W, float H, Random r) {
        double ang = r.nextDouble() * Math.PI * 2;
        float rad = W * (0.45F + 0.2F * r.nextFloat());
        return new float[] {state.tx + (float) Math.cos(ang) * rad, state.ty + H * (0.05F + 0.95F * r.nextFloat()),
                state.tz + (float) Math.sin(ang) * rad};
    }

    /** Enveloppe lumineuse : deux blocs croisés autour du corps (largeur {@code w}, hauteur {@code h}, en blocs). */
    private void aura(PoseStack pose, SubmitNodeCollector collector, RenderType type, State state, float w, float h, float spin, int color) {
        float px = w * 16.0F;
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.translate(state.tx, state.ty + state.height * 0.5F, state.tz); // centre du corps
            pose.rotateDegrees(Axis.YP, spin + i * 45.0F);
            pose.translate(0.0F, 0.0F, -w / 2.0F); // le bloc unité va de z = 0 à 1 : on le recentre
            pose.scale(px, h * 16.0F, px);
            collector.order(2).submitModelPart(this.segment, pose, type, GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
            pose.popPose();
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(GoroShock entity, float partialTick) {
        // la cible, et le lien jusqu'au nuage (au-dessus, parfois sur le côté)
        return entity.getBoundingBox().inflate(16.0, 3.0, 16.0).expandTowards(0.0, 14.0, 0.0);
    }
}
