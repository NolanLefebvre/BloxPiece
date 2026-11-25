package nolan.bloxpiece.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

import nolan.bloxpiece.client.entity.BubbleCoralLayer;
import nolan.bloxpiece.client.entity.CuffedRenderState;
import nolan.bloxpiece.client.entity.MobHandcuffsLayer;
import nolan.bloxpiece.handcuffs.Handcuffs;
import nolan.bloxpiece.client.entity.BuddhaGoldLayer;
import nolan.bloxpiece.client.entity.HandcuffsLayer;
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
    private void bloxpiece$addBuddhaGoldLayer(EntityRendererProvider.Context context, EntityModel<?> model, float shadowRadius, CallbackInfo info) {
        // Seulement le renderer des joueurs (les mobs, armures stands... gardent leur rendu normal)
        if ((Object) this instanceof AvatarRenderer) {
            RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this;
            RenderLayer<AvatarRenderState, PlayerModel> layer = new BuddhaGoldLayer(parent);
            this.layers.add(layer);
            // Passif Logia du feu : relève la pose des membres dispersés en flammes (ne dessine rien)
            RenderLayer<AvatarRenderState, PlayerModel> logia = new LogiaFlameLayer(parent);
            this.layers.add(logia);
            // Corail à Bulles : bulle d'air diaphane autour du joueur (ne dessine rien sans bulle)
            // (son propre modèle de joueur : chaque partie du corps y est agrandie autour de son articulation, voir BubbleCoralLayer)
            RenderLayer<AvatarRenderState, PlayerModel> bubble = new BubbleCoralLayer(parent, context.bakeLayer(ModelLayers.PLAYER));
            this.layers.add(bubble);
            // Menottes en Granite Marin : bracelets aux poignets et chaîne (ne dessine rien si le joueur n'est pas menotté)
            RenderLayer<AvatarRenderState, PlayerModel> cuffs = new HandcuffsLayer(parent, context.bakeLayer(HandcuffsLayer.LAYER));
            this.layers.add(cuffs);
            // Haki de l'Armement : revêtement noir métallique (ne dessine rien sans Haki actif)
            RenderLayer<AvatarRenderState, PlayerModel> armament = new nolan.bloxpiece.client.haki.HakiArmamentLayer(parent,
                    context.bakeLayer(nolan.bloxpiece.client.haki.HakiArmamentLayer.LAYER));
            this.layers.add(armament);
            // Haki de l'Observation : ruban rouge des yeux pendant une esquive (ne dessine rien sinon)
            RenderLayer<AvatarRenderState, PlayerModel> eyeTrail = new nolan.bloxpiece.client.haki.HakiEyeTrailLayer(parent,
                    context.bakeLayer(nolan.bloxpiece.client.haki.HakiEyeTrailLayer.LAYER),
                    context.bakeLayer(ModelLayers.PLAYER), context.bakeLayer(ModelLayers.PLAYER)); // (même maillage : sûr d'exister)
            this.layers.add(eyeTrail);
            // Race Cyborg : bras gauche mécanique, ses transformations, l'aura de surcharge (ne dessine rien pour une autre race)
            this.layers.add(new nolan.bloxpiece.client.cyborg.CyborgArmLayer(parent,
                    context.bakeLayer(nolan.bloxpiece.client.cyborg.CyborgArmModels.ARM),
                    context.bakeLayer(nolan.bloxpiece.client.haki.HakiBoltRenderer.LAYER).getChild("segment")));
            // Race Humaine : aura du poing, traînées du bras, effets de la ruée (ne dessine rien hors de ses attaques)
            this.layers.add(new nolan.bloxpiece.client.human.HumanFistLayer(parent, context));
            // Race Cyborg : effets de la Surcharge moteur (réacteur, conduits, gyroscopes, arcs ; ne dessine rien hors surcharge)
            this.layers.add(new nolan.bloxpiece.client.cyborg.CyborgOverdriveLayer(parent, context));
            // Race Minks : oreilles, queue, griffes électriques, apparence et aura de Sulong (rien sur les autres races)
            this.layers.add(new nolan.bloxpiece.client.minks.MinksLayer(parent, context));
            // Kame Kame no Mi : la carapace (ne dessine rien sans carapace)
            this.layers.add(new nolan.bloxpiece.client.entity.KameShellLayer(parent,
                    context.bakeLayer(nolan.bloxpiece.client.entity.KameShellModel.LAYER)));
        } else if (model instanceof HumanoidModel) {
            // Mobs humanoïdes (zombies, squelettes, piglins, pirates...) : menottes aux poignets s'ils sont menottés
            this.layers.add(new MobHandcuffsLayer((RenderLayerParent) (Object) this, context.bakeLayer(HandcuffsLayer.LAYER)));
        }
    }

    /** Menottes : note dans l'état de rendu si l'entité est menottée (lu par la pose des bras et par les calques des menottes). */
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void bloxpiece$extractCuffed(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo info) {
        ((CuffedRenderState) state).bloxpiece$setCuffed(Handcuffs.isCuffed(entity));
        boolean unconscious = nolan.bloxpiece.haki.Unconscious.is(entity);
        nolan.bloxpiece.client.haki.UnconsciousRenderState lie = (nolan.bloxpiece.client.haki.UnconsciousRenderState) state;
        lie.bloxpiece$setUnconscious(unconscious);
        if (!unconscious) {
            lie.bloxpiece$setLie(nolan.bloxpiece.client.haki.UnconsciousRenderState.STANDING, 0.0F);
        } else {
            // bipède : modèle humanoïde (zombies, squelettes, piglins, endermen, PNJ...), joueur, ou bipède connu -> sur le dos ;
            // tout le reste (quadrupèdes, araignées...) -> sur le côté
            boolean biped = entity instanceof net.minecraft.world.entity.player.Player
                    || ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel() instanceof HumanoidModel
                    || nolan.bloxpiece.client.haki.UnconsciousRenderState.BIPEDS.contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());
            lie.bloxpiece$setLie(biped ? nolan.bloxpiece.client.haki.UnconsciousRenderState.ON_BACK
                    : nolan.bloxpiece.client.haki.UnconsciousRenderState.ON_SIDE, entity.getBbWidth());
        }
    }
}
