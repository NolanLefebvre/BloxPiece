package nolan.bloxpiece.client.goro;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.client.haki.HakiBoltRenderer;
import nolan.bloxpiece.fx.GoroBolt;

/**
 * Éclair bleu du Goro Goro no Mi (GoroBolt) :
 * <ul>
 *   <li>descente (si GoroBolt.descend() > 0) : la pointe fonce de l'origine (dessous d'un nuage, boule...) vers le point visé ;
 *       seul le tracé déjà parcouru est allumé (traînée), une tête éblouissante et des petites ramifications tâtonnantes
 *       ouvrent le chemin devant elle ;</li>
 *   <li>frappe : quand la pointe arrive, éclair complet avec un flash plus large (décharge principale), puis il crépite à
 *       chaque tick, pulse avec des surcharges, s'amincit et s'éteint (GoroLightning).</li>
 * </ul>
 */
public class GoroBoltRenderer extends EntityRenderer<GoroBolt, GoroBoltRenderer.State> {

    public static class State extends EntityRenderState {
        public float age;
        public int life = 6;
        public int seed;
        public float width = 1.0F;
        public float[] reach = {0.0F, -1.0F, 0.0F};
        public int descend;
    }

    private final ModelPart segment;

    public GoroBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.segment = context.bakeLayer(HakiBoltRenderer.LAYER).getChild("segment"); // même prisme unité que le Haki des Rois
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GoroBolt entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.age = entity.tickCount + partialTick;
        state.life = Math.max(2, entity.life());
        state.seed = entity.seed();
        state.width = entity.width();
        state.reach = entity.reach();
        state.descend = entity.descend();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float progress = state.descend <= 0 ? 1.0F : Math.min(1.0F, state.age / state.descend);
        float after = state.age - state.descend; // temps depuis la frappe (négatif pendant la descente)
        int hold = Math.max(2, state.life - state.descend);
        float life01 = after <= 0.0F ? 0.0F : Math.min(1.0F, after / hold);
        float fade = life01 < 0.45F ? 1.0F : Math.max(0.0F, (1.0F - life01) / 0.55F);
        if (fade > 0.0F) {
            int crackle = (int) state.age; // nouveau tracé à chaque tick : décharge rapide
            Random flash = new Random(state.seed * 31L + crackle * 7919L);
            float surge = 0.75F + 0.25F * (float) Math.sin(state.age * 6.1F + state.seed) + (flash.nextFloat() < 0.3F ? 0.4F : 0.0F);
            Random shape = new Random(state.seed * 1_000_003L);
            Random jag = new Random(state.seed * 92_821L + crackle * 15_485_863L);
            float root = 1.05F * state.width;
            List<GoroLightning.Path> paths = GoroLightning.bolt(new float[] {0.0F, 0.0F, 0.0F}, state.reach, root, state.age, state.seed,
                    shape, jag, true);
            if (progress < 1.0F) {
                this.leader(paths, progress, state, pose, collector, jag);
            } else {
                // la décharge principale : flash plus large juste après la frappe
                float strike = after < 2.0F && state.descend > 0 ? 1.5F - after * 0.25F : 1.0F;
                GoroLightning.draw(this.segment, pose, collector, paths, strike, Math.min(1.3F, surge) * fade, fade);
            }
        }
        super.submit(state, pose, collector, camera);
    }

    /** Descente : tracé parcouru jusqu'à {@code progress}, tête lumineuse et ramifications qui tâtonnent devant elle. */
    private void leader(List<GoroLightning.Path> paths, float progress, State state, PoseStack pose, SubmitNodeCollector collector,
                        Random jag) {
        GoroLightning.Path main = paths.get(0);
        List<float[]> pts = main.points();
        float reachIndex = progress * (pts.size() - 1);
        List<GoroLightning.Path> lit = new ArrayList<>();
        lit.add(GoroLightning.truncate(main, reachIndex));
        for (int f = 1; f < paths.size(); f++) {
            // une ramification ne s'allume que lorsque la pointe a dépassé son point de départ
            float[] start = paths.get(f).points().get(0);
            if (GoroLightning.nearestIndex(main, start) <= reachIndex) {
                lit.add(paths.get(f));
            }
        }
        GoroLightning.draw(this.segment, pose, collector, lit, 0.8F, 1.0F, 0.8F);
        float[] tip = GoroLightning.pointAt(main, reachIndex);
        // tête éblouissante
        float head = 6.0F + 3.0F * state.width;
        GoroLightning.segment(this.segment, pose, collector, 0, net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(
                GoroLightning.TEXTURE), new float[] {tip[0], tip[1] + head / 64.0F, tip[2]}, new float[] {tip[0], tip[1] - head / 64.0F, tip[2]},
                head, GoroLightning.argb(0.9F, GoroLightning.CORE));
        // ramifications tâtonnantes, dirigées vers la cible
        float[] rest = {state.reach[0] - tip[0], state.reach[1] - tip[1], state.reach[2] - tip[2]};
        float restLen = (float) Math.sqrt(rest[0] * rest[0] + rest[1] * rest[1] + rest[2] * rest[2]);
        if (restLen > 0.5F) {
            float[] dir = GoroLightning.normalize(rest);
            List<GoroLightning.Path> feelers = new ArrayList<>();
            for (int k = 0; k < 3; k++) {
                float len = Math.min(restLen, 1.5F + jag.nextFloat() * 2.5F) * (0.6F + 0.2F * state.width / 3.0F);
                feelers.add(GoroLightning.channel(tip, GoroLightning.deviate(dir, 0.3F + jag.nextFloat() * 0.6F, jag), len, 6,
                        0.5F * state.width, 0.12F, state.age, state.seed + k, jag, jag, false));
            }
            GoroLightning.draw(this.segment, pose, collector, feelers, 0.8F, 0.9F, 0.7F);
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(GoroBolt entity, float partialTick) {
        float[] r = entity.reach();
        return entity.getBoundingBox().expandTowards(r[0], r[1], r[2]).inflate(2.0);
    }
}
