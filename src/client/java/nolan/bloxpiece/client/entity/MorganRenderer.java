package nolan.bloxpiece.client.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.mob.Morgan;
import nolan.bloxpiece.mob.ai.MorganAxeGoal;

/**
 * Affiche Morgan (MorganModel + morgan.png, générés par src/blockbench/make_morgan.py). Ombre à sa taille (3 blocs).
 * Sa barre de vie est une barre de boss en haut de l'écran (hud/MorganBossBar, même style que celle de Roger).
 */
public class MorganRenderer extends HumanoidMobRenderer<Morgan, MorganRenderState, MorganModel> {

    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/morgan.png");

    public MorganRenderer(EntityRendererProvider.Context context) {
        super(context, new MorganModel(context.bakeLayer(MorganModels.MORGAN)), 1.0F);
        this.addLayer(new MorganSpinTrailLayer(this, context.bakeLayer(MorganFx.LAYER).getChild("cube"))); // traînée de la rotation
    }

    @Override
    public MorganRenderState createRenderState() {
        return new MorganRenderState();
    }

    @Override
    public void extractRenderState(Morgan entity, MorganRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        float t = entity.axeSwingTicks(partialTick);
        state.axeKind = entity.axeSwingKind();
        state.axeSwing = t >= 0.0F && t < MorganAxeGoal.duration(state.axeKind) ? t : -1.0F;
    }

    @Override
    public Identifier getTextureLocation(MorganRenderState state) {
        return TEXTURE;
    }
}
