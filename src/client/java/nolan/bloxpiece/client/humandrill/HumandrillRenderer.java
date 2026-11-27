package nolan.bloxpiece.client.humandrill;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.mob.Humandrill;
import nolan.bloxpiece.mob.ai.HumandrillAttackGoal;
import nolan.bloxpiece.registry.ModItems;

/**
 * Affiche un Humandrill : HumandrillModel + humandrill.png (générés par src/blockbench/humandrill/MakeHumandrill.java), épées en
 * main (couche vanilla des objets tenus, attachée au poing par HumandrillModel.translateToHand), épée de la bouche
 * (HumandrillMouthSwordLayer). Tout ce qui anime le modèle vient de données synchronisées (nombre d'épées, attaque en cours,
 * agressivité) ou du mouvement vu par le client (marche, saut, réception, virage) : tous les joueurs voient la même chose.
 */
public class HumandrillRenderer extends MobRenderer<Humandrill, HumandrillRenderState, HumandrillModel> {

    /**
     * Échelle du modèle : sa géométrie (HumandrillModels) mesure 32,6 px au repos ; x 1,96 = 64 px = 4 blocs. Échelle uniforme
     * (proportions, articulations, épées en main et dans la bouche agrandies ensemble) ; hitbox : ModEntities.HUMANDRILL.
     */
    public static final float SCALE = 1.96F;
    private static final Identifier TEXTURE = BloxPiece.id("textures/entity/humandrill.png");
    private ItemStack mouthStack;

    public HumandrillRenderer(EntityRendererProvider.Context context) {
        super(context, new HumandrillModel(context.bakeLayer(HumandrillModels.HUMANDRILL)), 0.55F * SCALE);
        this.addLayer(new ItemInHandLayer<>(this));
        this.addLayer(new HumandrillMouthSwordLayer(this));
    }

    @Override
    public HumandrillRenderState createRenderState() {
        return new HumandrillRenderState();
    }

    @Override
    public void extractRenderState(Humandrill entity, HumandrillRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModelResolver, partialTick);
        state.swords = entity.swords();
        if (entity.hasMouthSword()) {
            if (this.mouthStack == null) {
                this.mouthStack = new ItemStack(ModItems.HUMANDRILL_SWORD);
            }
            this.itemModelResolver.updateForLiving(state.mouthItemState, this.mouthStack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
        } else {
            state.mouthItemState.clear();
        }
        int kind = entity.attackKind();
        float t = entity.attackTicks(partialTick);
        state.attackKind = kind & 15;
        state.attackLeft = (kind & HumandrillAttackGoal.LEFT) != 0;
        state.attackT = t >= 0.0F && t < HumandrillAttackGoal.duration(kind) ? t : -1.0F;
        state.guard = entity.guardAmount(partialTick);
        state.landT = entity.landingTicks(partialTick);
        state.landPower = entity.landingPower();
        state.air = Mth.clamp((entity.airTicks() - 2 + partialTick) / 4.0F, 0.0F, 1.0F);
        state.turn = Mth.wrapDegrees(entity.yBodyRot - entity.yBodyRotO);
        state.size = 0.94F + 0.12F * ((entity.getUUID().hashCode() & 0xFF) / 255.0F);
    }

    @Override
    protected void scale(HumandrillRenderState state, PoseStack pose) {
        float s = state.size * SCALE;
        pose.scale(s, s, s);
    }

    @Override
    public Identifier getTextureLocation(HumandrillRenderState state) {
        return TEXTURE;
    }
}
