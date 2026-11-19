package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.mob.ArmedHumanoid;

/**
 * Affiche un combattant armé (pirates : Sabreur, Tireur, Capitaine ; Marines : Épéiste, Fusilier) : modèle humanoïde avec équipement 3D (PirateGearModels, pose de visée via
 * ArmedPirateModel), et l'arme 3D tenue en main (HumanoidMobRenderer ajoute tout seul la couche des objets en main).
 * Chaque pirate a l'un des {@code variants} skins (peau, barbe, cicatrice, bandeau, couleurs...), choisi d'après son UUID :
 * toujours le même pour un pirate donné, et pas 15 clones identiques sur un navire.
 */
public class ArmedPirateRenderer extends HumanoidMobRenderer<ArmedHumanoid, ArmedPirateRenderState, ArmedPirateModel> {

    private final Identifier[] textures;
    /** Durée du recul après un coup de feu (ticks). */
    private static final float RECOIL_TICKS = 6.0F;

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
    public void extractRenderState(ArmedHumanoid entity, ArmedPirateRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.aiming = entity.isAiming();
        state.twoHanded = entity.twoHandedWeapon();
        // fusil dans le dos hors combat ; menotté : rien dans le dos (arme confisquée, et la chaîne passe à cet endroit)
        state.weaponStowed = entity.stowsWeapon() && entity.getMainHandItem().isEmpty()
                && !nolan.bloxpiece.handcuffs.Handcuffs.isCuffed(entity);
        float sinceShot = entity.ticksSinceShot() + partialTick;
        state.recoil = sinceShot < 0.0F || sinceShot > RECOIL_TICKS ? 0.0F : 1.0F - sinceShot / RECOIL_TICKS;
        state.variant = Math.floorMod(entity.getUUID().hashCode(), this.textures.length);
    }

    @Override
    public Identifier getTextureLocation(ArmedPirateRenderState state) {
        return this.textures[state.variant];
    }
}
