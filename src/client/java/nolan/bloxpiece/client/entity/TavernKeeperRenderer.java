package nolan.bloxpiece.client.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.mob.TavernKeeper;

/** Affiche le Tavernier (TavernKeeperModel + tavern_keeper.png). */
public class TavernKeeperRenderer extends HumanoidMobRenderer<TavernKeeper, HumanoidRenderState, TavernKeeperModel> {

    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/tavern_keeper.png");

    public TavernKeeperRenderer(EntityRendererProvider.Context context) {
        super(context, new TavernKeeperModel(context.bakeLayer(TavernKeeperModel.LAYER)), 0.5F);
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
