package nolan.bloxpiece.client.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.mob.Rayleigh;

/** Affiche Silvers Rayleigh (RayleighModel + silvers_rayleigh.png, générés par src/blockbench/make_silvers_rayleigh.py). */
public class RayleighRenderer extends HumanoidMobRenderer<Rayleigh, HumanoidRenderState, RayleighModel> {

    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/silvers_rayleigh.png");

    public RayleighRenderer(EntityRendererProvider.Context context) {
        super(context, new RayleighModel(context.bakeLayer(RayleighModels.RAYLEIGH)), 0.5F);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }
}
