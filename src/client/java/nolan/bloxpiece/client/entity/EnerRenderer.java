package nolan.bloxpiece.client.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.mob.Ener;

/** Affiche Ener (EnerModel + ener.png, générés par src/blockbench/make_ener.py). Même rendu que Silvers Rayleigh. */
public class EnerRenderer extends HumanoidMobRenderer<Ener, HumanoidRenderState, EnerModel> {

    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/ener.png");

    public EnerRenderer(EntityRendererProvider.Context context) {
        super(context, new EnerModel(context.bakeLayer(EnerModels.ENER)), 0.5F);
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
