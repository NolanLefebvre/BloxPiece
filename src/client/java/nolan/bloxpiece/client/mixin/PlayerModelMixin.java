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
import nolan.bloxpiece.fruit.BubbleCoral;
import nolan.bloxpiece.handcuffs.Handcuffs;
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
        if (Handcuffs.isCuffed(player)) {
            bloxpiece$cuffedPose();
            return; // menotté : aucune autre pose (pas de pouvoirs)
        }
        if (player instanceof LivingEntity living && IceSaberAbility.isHeld(living)) {
            bloxpiece$saberStrike(player, level, state);
        }
        bloxpiece$blowBubble(player, level, state);
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
     * Ice Saber (fruit de la Glace), trident de glace en main principale : chaque coup est un ESTOC au lieu du coup de poing.
     * Hors frappe, rien n'est modifié (pose normale de Minecraft). Coup (progression de l'attaque vanilla, 0 à 1) :
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
        if (p <= 0.0F) {
            return; // pas de coup en cours : la pose normale de Minecraft (le trident se tient comme une épée)
        }
        float guardX = -0.55F;
        float thrustX = -1.55F + model.head.xRot;
        float x, z, twist;
        if (p < 0.3F) {
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
        model.body.yRot = twist;    // rotation des épaules (le bras gauche garde sa pose normale)
    }

    /**
     * Corail à Bulles : pendant la formation de la bulle, le joueur porte la main droite (le corail écrasé) devant sa bouche et
     * souffle dedans. La main monte en {@link #BLOW_RAISE_TICKS} ticks, reste devant la bouche pendant toute la formation (léger
     * mouvement à chaque souffle), puis redescend en {@link #BLOW_LOWER_TICKS} ticks. Tête légèrement baissée vers la main.
     */
    private void bloxpiece$blowBubble(Entity player, ClientLevel level, AvatarRenderState state) {
        Integer formedAt = player.getAttached(ModAttachments.BUBBLE_CORAL);
        if (formedAt == null) {
            return;
        }
        float partial = state.ageInTicks - player.tickCount;
        float age = (float) (level.getGameTime() - formedAt) + partial;
        float total = BubbleCoral.FORM_TICKS;
        float w;
        if (age < 0.0F || age >= total + BLOW_LOWER_TICKS) {
            return;
        } else if (age < BLOW_RAISE_TICKS) {
            w = bloxpiece$smooth(age / BLOW_RAISE_TICKS);
        } else if (age < total) {
            w = 1.0F;
        } else {
            w = 1.0F - bloxpiece$smooth((age - total) / BLOW_LOWER_TICKS);
        }
        PlayerModel model = (PlayerModel) (Object) this;
        float puff = (float) Math.sin(age * 0.8F) * 0.04F; // petit mouvement à chaque souffle
        float targetX = model.head.xRot - 1.75F + puff;    // bras plié vers le visage (comme la longue-vue, un peu plus bas)
        float targetY = model.head.yRot - 0.45F;           // main ramenée devant la bouche
        model.rightArm.xRot += (targetX - model.rightArm.xRot) * w;
        model.rightArm.yRot += (targetY - model.rightArm.yRot) * w;
        model.rightArm.zRot *= 1.0F - w;
        model.head.xRot += 0.12F * w;                      // tête légèrement penchée vers la main
    }

    private static final float BLOW_RAISE_TICKS = 6.0F;
    private static final float BLOW_LOWER_TICKS = 8.0F;

    /**
     * Menottes en Granite Marin : les deux bras ramenés dans le dos, poignets rapprochés (les bracelets et la chaîne sont dessinés
     * par HandcuffsLayer, dans le repère des bras). Bras droit vers l'intérieur = zRot négatif, bras gauche = zRot positif.
     */
    private void bloxpiece$cuffedPose() {
        PlayerModel model = (PlayerModel) (Object) this;
        model.rightArm.xRot = 0.62F;
        model.rightArm.yRot = 0.0F;
        model.rightArm.zRot = -0.36F;
        model.leftArm.xRot = 0.62F;
        model.leftArm.yRot = 0.0F;
        model.leftArm.zRot = 0.36F;
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
     * levés et écartés, qui tremblent sous l'énergie ; 4 puis 5 = Kabutowari (Gura Gura no Mi), poing levé puis abattu vers le sol.
     */
    private void bloxpiece$buddhaPose(int pose, AvatarRenderState state) {
        PlayerModel model = (PlayerModel) (Object) this;
        float shiver = (float) Math.sin(state.ageInTicks * 1.3F) * 0.04F;
        if (pose >= 600 && pose <= 700) {
            // Shima Yurashi (Gura Gura no Mi), fin de charge : les bras levés s'écartent jusqu'à la T-pose
            float t = (pose - 600) / 100.0F;
            float e = t * t * (3.0F - 2.0F * t);
            model.rightArm.xRot = -2.6F * (1.0F - e) + shiver * (1.0F - e);
            model.rightArm.yRot = 0.0F;
            model.rightArm.zRot = 0.55F + (1.5708F - 0.55F) * e;
            model.leftArm.xRot = -2.6F * (1.0F - e) - shiver * (1.0F - e);
            model.leftArm.yRot = 0.0F;
            model.leftArm.zRot = -0.55F - (1.5708F - 0.55F) * e;
        } else if (pose == 1) {
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
        } else if (pose == 4) {
            // Kabutowari, armé : poing droit levé haut au-dessus de la tête, tête relevée
            model.rightArm.xRot = -2.9F + shiver;
            model.rightArm.yRot = 0.0F;
            model.rightArm.zRot = 0.35F;
            model.head.xRot = -0.2F;
        } else if (pose == 5) {
            // Kabutowari, frappe : le poing s'abat violemment vers le sol, tête baissée vers l'impact
            model.rightArm.xRot = -0.35F;
            model.rightArm.yRot = 0.0F;
            model.rightArm.zRot = 0.1F;
            model.leftArm.xRot = 0.4F;
            model.head.xRot = 0.7F;
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
