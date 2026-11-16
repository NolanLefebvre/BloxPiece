package nolan.bloxpiece.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import nolan.bloxpiece.client.entity.BuddhaGoldLayer;
import nolan.bloxpiece.client.entity.LogiaFlameLayer;

/**
 * Ajoute la couche dorée du Bouddha (BuddhaGoldLayer) au rendu de tous les joueurs, à la fin de la construction du renderer.
 * La couche ne dessine rien tant que le joueur n'est pas agrandi (voir BuddhaGoldLayer), donc aucun effet sur les autres joueurs.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Shadow
    @Final
    @SuppressWarnings("rawtypes")
    protected List layers;

    @Inject(method = "<init>", at = @At("TAIL"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void bloxpiece$addBuddhaGoldLayer(CallbackInfo info) {
        // Seulement le renderer des joueurs (les mobs, armures stands... gardent leur rendu normal)
        if ((Object) this instanceof AvatarRenderer) {
            RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this;
            RenderLayer<AvatarRenderState, PlayerModel> layer = new BuddhaGoldLayer(parent);
            this.layers.add(layer);
            // Passif Logia du feu : relève la pose des membres dispersés en flammes (ne dessine rien)
            RenderLayer<AvatarRenderState, PlayerModel> logia = new LogiaFlameLayer(parent);
            this.layers.add(logia);
        }
    }
}
