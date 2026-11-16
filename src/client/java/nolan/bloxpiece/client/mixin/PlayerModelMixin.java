package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import nolan.bloxpiece.ability.IceSaberAbility;
import nolan.bloxpiece.fruit.LogiaIntangibility;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Animation de Dai Enkai: Entei : pendant la charge, le bras droit du joueur se lève vers le ciel. Aussi : poses des attaques du
 * Bouddha, et membres cachés par le passif Logia du feu (voir les méthodes plus bas).
 *
 * Minecraft calcule la pose du modèle du joueur dans PlayerModel.setupAnim ; on laisse faire l'animation normale puis,
 * à la toute fin, on remplace la rotation du bras droit si le joueur est en train de charger l'attaque.
 * Le serveur indique la charge avec l'attachment ENTEI_CHARGING (synchronisé avec tous les joueurs proches), donc
 * chacun voit le bras levé des autres joueurs, pas seulement le sien.
 */
@Mixin(PlayerModel.class)
public class PlayerModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void bloxpiece$raiseArmWhileCharging(AvatarRenderState state, CallbackInfo info) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        // L'état de rendu contient l'identifiant de l'entité : on retrouve le joueur concerné
        Entity player = level.getEntity(state.id);
        if (player == null) {
            return;
        }
        bloxpiece$logiaParts(player);
        if (player instanceof LivingEntity living && IceSaberAbility.isHeld(living)) {
            bloxpiece$saberStrike(player, level, state);
        }
        Integer buddhaPose = player.getAttached(ModAttachments.BUDDHA_POSE);
        if (buddhaPose != null) {
            bloxpiece$buddhaPose(buddhaPose, state);
            return;
        }
        if (player.getAttached(ModAttachments.ENTEI_CHARGING) == null) {
            return;
        }

        ModelPart arm = ((PlayerModel) (Object) this).rightArm;
        arm.xRot = -3.0F + (float) Math.sin(state.ageInTicks * 0.9F) * 0.05F; // presque à la verticale, léger tremblement
        arm.yRot = 0.0F;
        arm.zRot = 0.25F;                                                      // un peu écarté du corps
    }

    /**
     * Ice Saber (fruit de la Glace), trident de glace en main principale : il se tient comme une lance, et chaque coup est un ESTOC au lieu du coup de poing.
     * Repos : bras droit un peu relevé vers l'avant, trident pointé devant. Coup (progression de l'attaque vanilla, 0 à 1) :
     * <ol>
     *   <li>0 - 0,3 : armé, le bras recule et l'épaule droite pivote en arrière ;</li>
     *   <li>0,3 - 0,55 : estoc, le bras se tend d'un coup droit devant, dans l'axe du regard, épaule projetée en avant ;</li>
     *   <li>0,55 - 1 : retour en garde.</li>
     * </ol>
     * L'item suit le bras : chez tous les joueurs.
     */
    private void bloxpiece$saberStrike(Entity player, ClientLevel level, AvatarRenderState state) {
        PlayerModel model = (PlayerModel) (Object) this;
        // Progression du coup (0 à 1) depuis l'instant du dernier coup, synchronisé par le serveur (attachment ICE_SABER_STRIKE)
        Integer strike = player.getAttached(ModAttachments.ICE_SABER_STRIKE);
        float partial = state.ageInTicks - player.tickCount;
        float since = strike == null ? Float.MAX_VALUE : (int) level.getGameTime() - strike + partial;
        float p = since >= 0.0F && since < STRIKE_TICKS ? since / STRIKE_TICKS : 0.0F;
        float guardX = -0.55F;
        float thrustX = -1.55F + model.head.xRot;
        float x, z, twist;
        if (p <= 0.0F) {
            x = guardX; z = 0.0F; twist = 0.0F;
        } else if (p < 0.3F) {
            float k = bloxpiece$smooth(p / 0.3F);
            x = guardX + (-1.05F - guardX) * k; z = 3.0F * k; twist = 0.35F * k;
        } else if (p < 0.55F) {
            float k = bloxpiece$smooth((p - 0.3F) / 0.25F);
            x = -1.05F + (thrustX + 1.05F) * k; z = 3.0F + (-5.0F - 3.0F) * k; twist = 0.35F + (-0.4F - 0.35F) * k;
        } else {
            float k = bloxpiece$smooth((p - 0.55F) / 0.45F);
            x = thrustX + (guardX - thrustX) * k; z = -5.0F * (1.0F - k); twist = -0.4F * (1.0F - k);
        }
        model.rightArm.xRot = x;
        model.rightArm.yRot = -0.1F + model.head.yRot * 0.5F;
        model.rightArm.zRot = 0.0F;
        model.rightArm.z += z;      // le bras recule puis se projette vers l'avant
        model.body.yRot = twist;    // rotation des épaules
        model.leftArm.xRot = -0.4F - twist * 0.6F; // le bras gauche équilibre le mouvement
    }

    /** Durée d'un estoc (ticks). */
    private static final float STRIKE_TICKS = 7.0F;

    private static float bloxpiece$smooth(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Passif Logia du fruit du Feu : les membres dispersés en flammes (attachment LOGIA_PARTS) sont cachés ; le serveur dessine
     * des flammes à leur place. Les autres membres redeviennent visibles (les couches du skin gardent le réglage de Minecraft).
     */
    private void bloxpiece$logiaParts(Entity player) {
        Integer parts = player.getAttached(ModAttachments.LOGIA_PARTS);
        int mask = parts == null ? 0 : parts;
        PlayerModel model = (PlayerModel) (Object) this;
        bloxpiece$show(model.head, model.hat, mask & LogiaIntangibility.HEAD);
        bloxpiece$show(model.body, model.jacket, mask & LogiaIntangibility.BODY);
        bloxpiece$show(model.rightArm, model.rightSleeve, mask & LogiaIntangibility.RIGHT_ARM);
        bloxpiece$show(model.leftArm, model.leftSleeve, mask & LogiaIntangibility.LEFT_ARM);
        bloxpiece$show(model.rightLeg, model.rightPants, mask & LogiaIntangibility.RIGHT_LEG);
        bloxpiece$show(model.leftLeg, model.leftPants, mask & LogiaIntangibility.LEFT_LEG);
    }

    private static void bloxpiece$show(ModelPart part, ModelPart overlay, int hidden) {
        part.visible = hidden == 0;
        if (hidden != 0) {
            overlay.visible = false;
        }
    }

    /**
     * Poses des attaques du Bouddha (attachment BUDDHA_POSE) : 1 = Onde de choc, bras droit tendu droit devant, paume vers
     * l'avant ; 100 à 200 = incantation de l'Onde de choc, bras qui se lève (100 + pourcentage) ; 2 = Déflagration, les deux bras
     * levés et écartés, qui tremblent sous l'énergie.
     */
    private void bloxpiece$buddhaPose(int pose, AvatarRenderState state) {
        PlayerModel model = (PlayerModel) (Object) this;
        float shiver = (float) Math.sin(state.ageInTicks * 1.3F) * 0.04F;
        if (pose == 1) {
            model.rightArm.xRot = -1.55F + model.head.xRot; // tendu devant, suit la direction du regard
            model.rightArm.yRot = -0.1F + model.head.yRot;
            model.rightArm.zRot = 0.0F;
        } else if (pose >= 100) {
            // Incantation de l'Onde de choc : le bras se lève lentement depuis sa pose normale jusqu'à être tendu devant
            float t = Math.min(1.0F, (pose - 100) / 100.0F);
            float e = t * t * (3.0F - 2.0F * t);
            model.rightArm.xRot += (-1.55F + model.head.xRot - model.rightArm.xRot) * e + shiver * t;
            model.rightArm.yRot += (-0.1F + model.head.yRot - model.rightArm.yRot) * e;
            model.rightArm.zRot *= 1.0F - e;
        } else if (pose == 3) {
            // Ère glaciaire (fruit de la Glace) : la main droite tendue vers le sol, la tête baissée vers elle
            model.rightArm.xRot = -0.55F + shiver;
            model.rightArm.yRot = 0.0F;
            model.rightArm.zRot = 0.15F;
            model.head.xRot = 0.6F;
        } else if (pose == 2) {
            model.rightArm.xRot = -2.6F + shiver;
            model.rightArm.yRot = 0.0F;
            model.rightArm.zRot = 0.55F;
            model.leftArm.xRot = -2.6F - shiver;
            model.leftArm.yRot = 0.0F;
            model.leftArm.zRot = -0.55F;
        }
    }
}
