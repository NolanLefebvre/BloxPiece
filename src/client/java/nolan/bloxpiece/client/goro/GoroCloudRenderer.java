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
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.client.haki.HakiBoltRenderer;
import nolan.bloxpiece.fx.GoroCloud;

/**
 * Nuage d'orage 3D du Goro Goro no Mi (GoroCloud), modélisé en volume :
 * <ul>
 *   <li>trois couches de nuées (style Minecraft : chaque nuée = deux blocs croisés à 45°, presque ronds) : une base large,
 *       plate et sombre, un corps bosselé gris-bleu, et un sommet en chou-fleur plus clair. Forme irrégulière tirée d'après
 *       l'identifiant (la même pour tous les joueurs) ;</li>
 *   <li>formation : les nuées naissent une à une en tourbillonnant vers le centre, puis le nuage respire, ondule et tourne
 *       lentement ;</li>
 *   <li>charge (jusqu'au tick GoroCloud.peak()) : lueurs bleues de plus en plus nombreuses à l'intérieur, arcs électriques qui
 *       traversent le nuage et courent sur sa surface, décharges qui pendent dessous, ventre du nuage qui s'illumine ;</li>
 *   <li>décharge (au pic) : tout le nuage s'embrase et un disque éblouissant marque, juste sous lui, le point d'où jaillit son
 *       éclair : on voit clairement que la foudre vient de CE nuage ;</li>
 *   <li>dissipation sur les dernières secondes.</li>
 * </ul>
 * Taille = rayon de base du nuage (blocs) : petit pour la pluie de foudre, grand pour Mamaragan, immense pour El Thor.
 */
public class GoroCloudRenderer extends EntityRenderer<GoroCloud, GoroCloudRenderer.State> {

    public static class State extends EntityRenderState {
        public float age;
        public int life = 40;
        public int peak = 24;
        public float size = 2.0F;
        public int seed;
    }

    private static final int BASE = 0x23273A, DARK = 0x2E3447, MID = 0x434B63, TOP = 0x67718A, BELLY = 0x284A9A, GLOW = 0x4E9BFF;
    private final ModelPart segment;

    public GoroCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.segment = context.bakeLayer(HakiBoltRenderer.LAYER).getChild("segment");
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GoroCloud entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.life = Math.max(10, entity.life());
        state.peak = Math.max(1, entity.peak());
        state.size = entity.size();
        state.seed = entity.getId();
    }

    /** Une nuée : position, largeur et rapport hauteur/largeur, couche (0 base, 1 corps, 2 sommet). */
    private record Puff(float x, float y, float z, float w, float h, int layer, float phase) {
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float age = state.age, S = state.size;
        float form = Math.max(6.0F, Math.min(22.0F, state.peak * 0.8F));
        float out = Math.max(0.0F, Math.min(1.0F, (state.life - age) / 12.0F));
        float charge = Math.min(1.0F, age / state.peak);
        float flash = age >= state.peak - 1 && age <= state.peak + 4 ? 1.0F - Math.abs(age - state.peak - 0.5F) / 4.5F : 0.0F;
        if (out <= 0.01F) {
            super.submit(state, pose, collector, camera);
            return;
        }
        RenderType type = RenderTypes.entityTranslucent(GoroLightning.TEXTURE);
        Random r = new Random(state.seed * 7_919L);
        List<Puff> puffs = new ArrayList<>();
        int base = 8 + (int) (S * 4.0F), mid = 10 + (int) (S * 5.0F), top = 5 + (int) (S * 2.0F);
        for (int i = 0; i < base; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble());
            float stretch = 1.0F + 0.25F * (float) Math.sin(a * 2.0 + state.seed); // contour irrégulier, un peu allongé
            puffs.add(new Puff((float) (Math.cos(a) * d * 1.7 * S * stretch), -0.28F * S + (r.nextFloat() - 0.5F) * 0.08F * S,
                    (float) (Math.sin(a) * d * 1.7 * S), (0.75F + r.nextFloat() * 0.45F) * S, 0.42F, 0, r.nextFloat() * 6.28F));
        }
        for (int i = 0; i < mid; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble());
            puffs.add(new Puff((float) (Math.cos(a) * d * 1.3 * S), (float) ((r.nextDouble() * 0.45 - 0.1) * S * (1.0 - d * 0.55)),
                    (float) (Math.sin(a) * d * 1.3 * S), (0.6F + r.nextFloat() * 0.5F) * S, 0.72F, 1, r.nextFloat() * 6.28F));
        }
        for (int i = 0; i < top; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble());
            puffs.add(new Puff((float) (Math.cos(a) * d * 0.85 * S), (0.3F + r.nextFloat() * 0.3F) * S,
                    (float) (Math.sin(a) * d * 0.85 * S), (0.45F + r.nextFloat() * 0.4F) * S, 0.78F, 2, r.nextFloat() * 6.28F));
        }
        pose.pushPose();
        pose.rotateDegrees(Axis.YP, age * 0.4F);
        Random flicker = new Random(state.seed * 131L + (int) (age / 2.0F) * 977L);
        int n = puffs.size();
        for (int k = 0; k < n; k++) {
            Puff p = puffs.get(k);
            // formation : les nuées naissent une à une et tourbillonnent vers leur place
            float delay = form * 0.65F * k / n;
            float g = Math.max(0.0F, Math.min(1.0F, (age - delay) / (form * 0.35F)));
            if (g <= 0.0F) {
                continue;
            }
            g = 1.0F - (1.0F - g) * (1.0F - g);
            float spiral = (1.0F - g) * 1.2F;
            float cs = (float) Math.cos(spiral), sn = (float) Math.sin(spiral), spread = 1.0F + 0.5F * (1.0F - g);
            float breathe = 1.0F + 0.04F * (float) Math.sin(age * 0.07F + p.phase());
            float x = (p.x() * cs - p.z() * sn) * spread * breathe, z = (p.x() * sn + p.z() * cs) * spread * breathe;
            float y = p.y() + (float) Math.sin(age * 0.06F + p.phase()) * 0.05F * S;
            float w = p.w() * 16.0F * g * (0.6F + 0.4F * out);
            int color = p.layer() == 2 ? TOP : p.layer() == 1 ? (k % 3 == 0 ? MID : DARK) : BASE;
            if (p.layer() == 0 && charge > 0.3F) { // le ventre du nuage se teinte de bleu en se chargeant
                color = lerp(color, BELLY, Math.min(0.7F, (charge - 0.3F) + flash));
            }
            float alpha = 0.93F * out;
            this.cube(pose, collector, 0, type, x, y, z, w, p.h(), 0.0F, color, alpha);
            this.cube(pose, collector, 0, type, x, y, z, w * 0.86F, p.h() * 1.1F, 45.0F, color, alpha);
            // lueurs intérieures : plus nombreuses avec la charge, toutes allumées au moment de la décharge
            if (p.layer() < 2 && (flash > 0.0F || flicker.nextFloat() < 0.05F + 0.45F * charge * charge)) {
                float glow = flash > 0.0F ? 0.6F * flash + 0.2F : (0.2F + 0.45F * flicker.nextFloat()) * (0.4F + 0.6F * charge);
                this.cube(pose, collector, 1, type, x, y - 0.08F * S, z, w * 0.92F, p.h() * 0.9F, 22.0F, GLOW, glow * out);
            }
        }
        float ready = Math.max(0.0F, Math.min(1.0F, (age - form * 0.5F) / (form * 0.5F))); // les arcs viennent avec le volume
        Random shape = new Random(state.seed * 3L + (int) (age / 2.0F));
        Random jag = new Random(state.seed * 5L + (int) age * 11L);
        // arcs qui traversent le nuage, d'une nuée à l'autre
        int inner = (int) ((1 + charge * (2.0F + S)) * ready);
        for (int i = 0; i < inner; i++) {
            Puff a = puffs.get(shape.nextInt(n)), b = puffs.get(shape.nextInt(n));
            float[] from = {a.x(), a.y() - 0.1F * S, a.z()};
            float[] d = {b.x() - a.x(), b.y() - a.y(), b.z() - a.z()};
            List<GoroLightning.Path> paths = GoroLightning.bolt(from, d, 0.5F * Math.max(1.0F, S / 2.5F), age, state.seed + i, shape, jag, true);
            GoroLightning.draw(this.segment, pose, collector, paths, 1.0F, out, out);
        }
        // arcs qui courent sur la surface du nuage, par l'extérieur
        int around = (int) ((charge * (1.0F + S * 0.6F) + flash * 3.0F) * ready);
        for (int i = 0; i < around; i++) {
            float start = shape.nextFloat() * 6.28F, span = 0.6F + shape.nextFloat() * 1.0F;
            float yy = (shape.nextFloat() - 0.6F) * 0.5F * S, rr = 1.55F * S;
            int m = 9;
            List<float[]> pts = new ArrayList<>();
            float[] widths = new float[m];
            for (int k = 0; k < m; k++) {
                float a = start + span * k / (m - 1);
                float wob = k == 0 || k == m - 1 ? 1.0F : 1.0F + (jag.nextFloat() - 0.4F) * 0.14F;
                pts.add(new float[] {(float) Math.cos(a) * rr * wob, yy + (jag.nextFloat() - 0.5F) * 0.15F * S, (float) Math.sin(a) * rr * wob});
                widths[k] = (0.5F + 0.4F * (float) Math.sin(Math.PI * k / (m - 1))) * (float) Math.sqrt(S);
            }
            List<GoroLightning.Path> arc = new ArrayList<>();
            arc.add(new GoroLightning.Path(pts, widths));
            GoroLightning.draw(this.segment, pose, collector, arc, 1.0F, out, out);
        }
        // décharges qui pendent sous le nuage quand il est chargé
        if (charge > 0.5F) {
            int hang = (int) ((charge - 0.5F) * 2.0F * (1 + S * 0.4F));
            for (int i = 0; i < hang; i++) {
                if (jag.nextFloat() < 0.4F) {
                    continue;
                }
                double a = shape.nextDouble() * Math.PI * 2, d = Math.sqrt(shape.nextDouble()) * 1.3 * S;
                float[] from = {(float) (Math.cos(a) * d), -0.35F * S, (float) (Math.sin(a) * d)};
                float len = (0.4F + jag.nextFloat() * 0.6F) * (1.5F + S * 0.5F);
                List<GoroLightning.Path> paths = GoroLightning.bolt(from, new float[] {(jag.nextFloat() - 0.5F) * len * 0.5F, -len,
                        (jag.nextFloat() - 0.5F) * len * 0.5F}, 0.6F, age, state.seed + 50 + i, shape, jag, true);
                GoroLightning.draw(this.segment, pose, collector, paths, 1.0F, out, out);
            }
        }
        pose.popPose();
        // le point d'où jaillit la foudre : disque éblouissant sous le centre du nuage
        float origin = Math.max(flash, charge > 0.6F ? (charge - 0.6F) * 1.2F : 0.0F) * out;
        if (origin > 0.02F) {
            float w = (0.8F + 0.8F * flash) * S * 16.0F;
            this.cube(pose, collector, 2, type, 0.0F, -0.42F * S, 0.0F, w, 0.18F, age * 3.0F, GoroLightning.CORE, Math.min(0.95F, origin));
            this.cube(pose, collector, 2, type, 0.0F, -0.42F * S, 0.0F, w * 1.8F, 0.25F, -age * 2.0F, GoroLightning.SHEATH,
                    Math.min(0.5F, origin * 0.5F));
        }
        super.submit(state, pose, collector, camera);
    }

    /** Un bloc centré en (x, y, z) (blocs), de largeur {@code w} (px), hauteur {@code w * h}, tourné de {@code yaw} degrés. */
    private void cube(PoseStack pose, SubmitNodeCollector collector, int order, RenderType type, float x, float y, float z, float w,
                      float h, float yaw, int color, float alpha) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.rotateDegrees(Axis.YP, yaw);
        pose.translate(0.0F, 0.0F, -w / 32.0F);
        pose.scale(w, w * h, w);
        collector.order(order).submitModelPart(this.segment, pose, type, GoroLightning.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                GoroLightning.argb(alpha, color));
        pose.popPose();
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int r = (int) (((a >> 16) & 0xFF) + ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t));
        int g = (int) (((a >> 8) & 0xFF) + ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t));
        int bl = (int) ((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * t));
        return (r << 16) | (g << 8) | bl;
    }

    @Override
    protected AABB getBoundingBoxForCulling(GoroCloud entity, float partialTick) {
        return entity.getBoundingBox().inflate(entity.size() * 2.2 + 3.0).expandTowards(0.0, -entity.size() * 2.0 - 4.0, 0.0);
    }
}
