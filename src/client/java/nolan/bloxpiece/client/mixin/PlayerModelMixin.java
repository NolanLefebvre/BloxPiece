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
        if ((Object) this instanceof nolan.bloxpiece.client.haki.HakiAfterimage.GhostModel) {
            return; // image rémanente du Haki de l'Observation : sa pose figée est posée par le modèle lui-même
        }
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
        bloxpiece$hakiDodge(player, state);
        if (Handcuffs.isCuffed(player)) {
            bloxpiece$cuffedPose();
            return; // menotté : aucune autre pose (pas de pouvoirs)
        }
        if (player instanceof LivingEntity living && IceSaberAbility.isHeld(living)) {
            bloxpiece$saberStrike(player, level, state);
        }
        bloxpiece$blowBubble(player, level, state);
        bloxpiece$aceSlash(player, level, state);
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
     * Épée Ace, Lame dévastatrice du Haki des Rois (geste : AceSwing, d'après les attachments ACE_CHARGE et ACE_SLASH,
     * synchronisés avec tous les joueurs) :
     * <ol>
     *   <li>charge (clic droit maintenu) : le bras droit se lève en diagonale, épée en haut à droite et ramenée vers l'arrière,
     *       épaules tournées, et reste levé (léger tremblement : l'énergie s'accumule) jusqu'au relâchement ;</li>
     *   <li>frappe : dans la diagonale opposée, du haut à droite vers le bas à gauche, dans le plan même de la lame d'énergie
     *       (qui part à la fin de la frappe) ;</li>
     *   <li>retour en douceur à la pose normale.</li>
     * </ol>
     */
    private void bloxpiece$aceSlash(Entity player, ClientLevel level, AvatarRenderState state) {
        float partial = state.ageInTicks - player.tickCount;
        nolan.bloxpiece.client.AceSwing.Step step = nolan.bloxpiece.client.AceSwing.at(player, level.getGameTime() + partial);
        if (step == null) {
            return;
        }
        PlayerModel model = (PlayerModel) (Object) this;
        ModelPart arm = model.rightArm;
        // Le bras balaie LE PLAN DE LA LAME : zRot fixe = ce plan incliné de la diagonale (AceSlashShape.ROLL), seul xRot varie.
        // Levée : bras au-dessus de l'épaule droite, épée en haut à droite et vers l'arrière ; frappée : bras en bas à gauche,
        // devant. Entre les deux il passe droit devant : la lame part dans la direction du geste.
        boolean reverse = nolan.bloxpiece.client.AceSwing.reverse(player);
        float look = model.head.xRot * 0.7F; // suit un peu le regard (haut / bas)
        float tremble = (float) Math.sin(state.ageInTicks * 1.7F) * 0.03F; // l'énergie s'accumule : l'épée tremble
        float[][] poses;
        if (!reverse) {
            float plane = -(float) Math.toRadians(90.0 + nolan.bloxpiece.fx.AceSlashShape.ROLL);
            poses = new float[][] {
                    {arm.xRot, arm.yRot, arm.zRot, model.body.yRot},  // pose normale de Minecraft
                    {-3.7F + look + tremble, 0.0F, plane, 0.4F},      // levée
                    {-0.6F + look, 0.0F, plane, -0.4F},               // frappée
            };
        } else {
            // Deuxième diagonale (tirée au hasard par le serveur) : une vraie préparation de revers, pas un miroir. Le bras droit
            // est levé VERS L'AVANT (main devant le visage) et l'épée passe DERRIÈRE L'ÉPAULE GAUCHE, pointe en haut ; les épaules
            // tournent vers la gauche (élan). Frappe : l'épée revient par l'avant, en haut à gauche, traverse devant le joueur et
            // finit en bas à droite (pose intermédiaire « milieu » pour que la lame passe devant et non au-dessus de la tête).
            // Angles calculés avec la même chaîne que le rendu (bras + Ace en main) : la lame ne traverse ni la tête ni le corps.
            poses = new float[][] {
                    {arm.xRot, arm.yRot, arm.zRot, model.body.yRot},   // pose normale de Minecraft
                    {-2.2F + look + tremble, -0.2F, 1.15F, -0.35F},    // levée : main devant, lame derrière l'épaule gauche
                    {-0.6F + look, 0.65F, -1.3F, 0.35F},               // frappée : bras en bas à droite, pointe devant à droite
                    {-0.35F + look, -0.6F, -0.75F, 0.0F},              // milieu de la frappe : lame devant, en haut à gauche
            };
            if (step.from() == nolan.bloxpiece.client.AceSwing.RAISED && step.to() == nolan.bloxpiece.client.AceSwing.STRUCK) {
                float k2 = step.k();
                float[] a = k2 < 0.5F ? poses[1] : poses[3], b = k2 < 0.5F ? poses[3] : poses[2];
                float kk = k2 < 0.5F ? k2 * 2.0F : (k2 - 0.5F) * 2.0F;
                arm.xRot = a[0] + (b[0] - a[0]) * kk;
                arm.yRot = a[1] + (b[1] - a[1]) * kk;
                arm.zRot = a[2] + (b[2] - a[2]) * kk;
                model.body.yRot = a[3] + (b[3] - a[3]) * kk;
                return;
            }
        }
        float[] from = poses[step.from()], to = poses[step.to()];
        float k = step.k();
        float x = from[0] + (to[0] - from[0]) * k, y = from[1] + (to[1] - from[1]) * k;
        float z = from[2] + (to[2] - from[2]) * k, twist = from[3] + (to[3] - from[3]) * k;
        arm.xRot = x;
        arm.yRot = y;
        arm.zRot = z;
        model.body.yRot = twist; // rotation des épaules
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

    /**
     * Haki de l'Observation : à chaque attaque esquivée, une esquive parmi six (choisie par le serveur, jamais la même deux fois
     * de suite, côté au hasard), très rapide puis retour en place (HakiClientState.dodge). Chez tous les joueurs qui le voient.
     * Les hanches et les jambes restent en place : seuls le buste, la tête et les bras bougent.
     * <ol start="0">
     *   <li>latérale ample : le buste plonge d'un côté en pivotant sur les hanches ;</li>
     *   <li>recul : le buste part en arrière, menton relevé, bras qui s'écartent ;</li>
     *   <li>torsion : rotation sèche des épaules, la tête suit plus loin ;</li>
     *   <li>pivot de tête : seule la tête s'écarte et s'incline (balle qui frôle la joue) ;</li>
     *   <li>plongeon : le haut du corps s'abaisse et se penche en avant, bras en garde ;</li>
     *   <li>matrice : recul profond combiné à une torsion, bras ouverts.</li>
     * </ol>
     */
    private void bloxpiece$hakiDodge(Entity player, AvatarRenderState state) {
        nolan.bloxpiece.client.haki.HakiClientState.DodgePose dodge =
                nolan.bloxpiece.client.haki.HakiClientState.dodge(player, state.ageInTicks - player.tickCount);
        if (dodge == null) {
            return;
        }
        PlayerModel model = (PlayerModel) (Object) this;
        // image rémanente : la pose d'AVANT l'esquive est figée à la première image
        nolan.bloxpiece.client.haki.HakiAfterimage.capture(player, model, state.ageInTicks - (float) Math.floor(state.ageInTicks));
        float k = dodge.amount();
        float side = dodge.side();
        // buste et bras
        switch (dodge.variant()) {
            case 1 -> bloxpiece$leanBack(model, k * 0.55F, side * k * 0.12F);
            case 2 -> bloxpiece$twist(model, side * k * 0.85F);
            case 3 -> model.body.zRot += 0.08F * side * k; // la tête seule s'écarte, le corps bouge à peine
            case 4 -> {
                // angle négatif = buste penché en AVANT (le haut du corps plonge sous le coup), pivot aux hanches
                bloxpiece$leanBack(model, -0.55F * k, side * 0.15F * k);
                // bras levés en garde devant le visage
                model.rightArm.xRot += (-1.3F - model.rightArm.xRot) * k;
                model.leftArm.xRot += (-1.3F - model.leftArm.xRot) * k;
                model.rightArm.yRot += -0.35F * k;
                model.leftArm.yRot += 0.35F * k;
            }
            case 5 -> {
                bloxpiece$leanBack(model, k * 0.8F, 0.0F);
                bloxpiece$twist(model, side * k * 0.5F);
                model.rightArm.zRot += 0.9F * k;
                model.leftArm.zRot -= 0.9F * k;
            }
            default -> {
                float a = side * k * 0.42F;
                float shift = 12.0F * (float) Math.sin(a);   // le haut du buste se déplace, les hanches restent en place
                model.body.zRot += a;
                model.body.x += shift;
                model.rightArm.x += shift * 0.85F;
                model.leftArm.x += shift * 0.85F;
                model.rightArm.zRot += a;
                model.leftArm.zRot += a;
            }
        }
        // tête : même calcul que la traînée des yeux (HakiClientState.headDelta)
        float[] h = nolan.bloxpiece.client.haki.HakiClientState.headDelta(dodge);
        model.head.x += h[0];
        model.head.y += h[1];
        model.head.z += h[2];
        model.head.xRot += h[3];
        model.head.yRot += h[4];
        model.head.zRot += h[5];
        // pose finale de la tête relevée pour la traînée rouge des yeux
        // pose finale de la tête relevée pour le ruban rouge des yeux (HakiEyeTrailLayer), sauf pour le bras à la 1re personne
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (player != mc.player || !mc.options.getCameraType().isFirstPerson()) {
            nolan.bloxpiece.client.haki.HakiEyeTrail.record(player, model.head, state.ageInTicks - (float) Math.floor(state.ageInTicks));
        }
    }

    /** Buste penché en arrière (angle > 0) ou en avant (angle < 0), pivot aux hanches ; {@code roll} : inclinaison latérale. */
    private static void bloxpiece$leanBack(PlayerModel model, float angle, float roll) {
        float back = 12.0F * (float) Math.sin(angle);          // le haut du buste recule
        float down = 12.0F * (1.0F - (float) Math.cos(angle)); // et s'abaisse un peu
        model.body.xRot -= angle;
        model.body.z += back;
        model.body.y += down;
        model.rightArm.z += back * 0.85F;
        model.leftArm.z += back * 0.85F;
        model.rightArm.y += down;
        model.leftArm.y += down;
        model.rightArm.xRot -= angle * 0.5F;
        model.leftArm.xRot -= angle * 0.5F;
        model.rightArm.zRot += 0.35F * angle;
        model.leftArm.zRot -= 0.35F * angle;
        model.body.zRot += roll;
    }

    /** Torsion des épaules (radians, autour de la verticale) : les bras suivent leur épaule (la tête : headDelta). */
    private static void bloxpiece$twist(PlayerModel model, float yaw) {
        float sin = (float) Math.sin(yaw);
        float cos = (float) Math.cos(yaw);
        model.body.yRot += yaw;
        // épaules à 5 px du centre : elles tournent avec le buste
        model.rightArm.x += -5.0F * cos + 5.0F;
        model.rightArm.z += 5.0F * sin;
        model.leftArm.x += 5.0F * cos - 5.0F;
        model.leftArm.z += -5.0F * sin;
        model.rightArm.yRot += yaw;
        model.leftArm.yRot += yaw;
    }
}
