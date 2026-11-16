package nolan.bloxpiece.client.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.EnteiSun;

/**
 * Affiche le soleil de Dai Enkai: Entei avec le modèle Blockbench (EnteiSunModel) :
 * pleine luminosité + rendu opaque (cutout).
 */
public class EnteiSunRenderer extends MobRenderer<EnteiSun, EnteiSunRenderState, EnteiSunModel> {

    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/dai_enkai_entei.png");

    /**
     * false (par défaut) : rendu opaque avec profondeur (les plaques se cachent correctement entre elles, léger ombrage par face).
     * true : rendu émissif sans ombrage (plus "brillant") mais SANS profondeur : l'ordre des couches peut sembler mélangé.
     */
    private static final boolean EMISSIVE = false;

    public EnteiSunRenderer(EntityRendererProvider.Context context) {
        super(context, new EnteiSunModel(context.bakeLayer(EnteiSunModel.LAYER)), 0.0F);
    }

    @Override
    public EnteiSunRenderState createRenderState() {
        return new EnteiSunRenderState();
    }

    @Override
    public Identifier getTextureLocation(EnteiSunRenderState state) {
        return TEXTURE;
    }

    @Override
    protected RenderType getRenderType(EnteiSunRenderState state, boolean bodyVisible, boolean translucent, boolean glowing) {
        return EMISSIVE ? RenderTypes.entityTranslucentEmissive(TEXTURE) : RenderTypes.entityCutout(TEXTURE);
    }

    @Override
    public void extractRenderState(EnteiSun entity, EnteiSunRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        state.hasRedOverlay = false;
        state.deathTime = 0;
    }

    @Override
    protected AABB getBoundingBoxForCulling(EnteiSun entity, float partialTick) {
        // Les langues de feu et les braises dépassent largement de la hitbox de l'entité.
        // Une marge plus large évite qu'elles disparaissent au bord de l'écran.
        return entity.getBoundingBox().inflate(entity.getBbWidth() * 4.0);
    }
}
