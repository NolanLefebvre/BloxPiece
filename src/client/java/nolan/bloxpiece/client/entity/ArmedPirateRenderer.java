package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.mob.ArmedPirate;

/**
 * Affiche un pirate armé (Sabreur, Tireur) : modèle humanoïde avec équipement 3D (PirateGearModels, pose de visée via
 * ArmedPirateModel), et l'arme 3D tenue en main (HumanoidMobRenderer ajoute tout seul la couche des objets en main).
 * Chaque pirate a l'un des {@code variants} skins (peau, barbe, cicatrice, bandeau, couleurs...), choisi d'après son UUID :
 * toujours le même pour un pirate donné, et pas 15 clones identiques sur un navire.
 */
public class ArmedPirateRenderer extends HumanoidMobRenderer<ArmedPirate, ArmedPirateRenderState, ArmedPirateModel> {

    private final Identifier[] textures;

    public ArmedPirateRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, String skin, int variants) {
        super(context, new ArmedPirateModel(context.bakeLayer(layer)), 0.5F);
        this.textures = new Identifier[variants];
        for (int i = 0; i < variants; i++) {
            this.textures[i] = BloxPiece.id("textures/entity/" + skin + "_" + i + ".png");
        }
    }

    @Override
    public ArmedPirateRenderState createRenderState() {
        return new ArmedPirateRenderState();
    }

    @Override
    public void extractRenderState(ArmedPirate entity, ArmedPirateRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.aiming = entity.isAiming();
        state.variant = Math.floorMod(entity.getUUID().hashCode(), this.textures.length);
    }

    @Override
    public Identifier getTextureLocation(ArmedPirateRenderState state) {
        return this.textures[state.variant];
    }
}
