package nolan.bloxpiece.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.Entity;

import nolan.bloxpiece.ability.Kame;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Kame Kame no Mi : les membres du joueur RENTRENT dans sa carapace (et en ressortent), d'après les attachments synchronisés
 * KAME_RETRACT / KAME_RETRACT_KIND, donc pareil chez tous les joueurs.
 * <ul>
 *   <li>vers la poche : bras écartés presque en T (une demi-seconde), puis « ploup » : tête, bras et jambes ASPIRÉS ensemble
 *       en 6 ticks, chacun par SON ouverture de la carapace (épaules, bas, col) et par son extrémité (main, pied, haut de la
 *       tête en premier) ; il ne reste que la carapace ;</li>
 *   <li>carapace tournoyante : même entrée et même éjection que pour la poche ; entre les deux, seule la carapace reste et
 *       tourne (KameShellLayer) ;</li>
 *   <li>sortie de la poche : caché le temps que la carapace posée se redresse, puis les membres sont ÉJECTÉS par ces mêmes
 *       ouvertures en 12 ticks : le haut du membre d'abord, la main et le pied en dernier (ils dépassent un peu, puis se
 *       posent).</li>
 * </ul>
 */
public final class KamePose {

    private KamePose() {
    }

    /** Rétraction de 0 (membres sortis) à 1 (tout rentré). */
    public static float retraction(Entity entity, float now) {
        Integer at = entity.getAttached(ModAttachments.KAME_RETRACT);
        Integer kind = entity.getAttached(ModAttachments.KAME_RETRACT_KIND);
        if (at == null || kind == null) {
            return 0.0F;
        }
        float age = now - at;
        return switch (kind) {
            // « ploup » : rien pendant la posture bras écartés, puis tout est aspiré d'un coup (3 ticks, de plus en plus vite)
            case Kame.KIND_POCKET, Kame.KIND_SPIN -> {
                float t = Math.max(0.0F, Math.min(1.0F, (age - Kame.POCKET_SUCK_AT) / (Kame.POCKET_RETRACT_TICKS - Kame.POCKET_SUCK_AT)));
                yield t * t * t;
            }
            case Kame.KIND_EMERGE -> 1.0F - smooth(age / Kame.EMERGE_TICKS);
            case Kame.KIND_HIDDEN -> 1.0F;
            // sortie de la poche : les membres sont ÉJECTÉS de la carapace (très vite, ils dépassent leur place puis s'y posent)
            case Kame.KIND_EXIT -> {
                float t = Math.max(0.0F, Math.min(1.0F, age / Kame.EXIT_EMERGE_TICKS)) - 1.0F;
                float back = 2.4F;                                   // force du dépassement (courbe « easeOutBack »)
                yield -(t * t * ((back + 1.0F) * t + back));         // 1 -> environ -0,25 -> 0
            }
            default -> 0.0F;
        };
    }

    /** Posture « presque en T » avant l'aspiration (0 à 1) : vers la poche et pour la carapace tournoyante. */
    private static float spread(Entity entity, float now) {
        Integer at = entity.getAttached(ModAttachments.KAME_RETRACT);
        Integer kind = entity.getAttached(ModAttachments.KAME_RETRACT_KIND);
        if (at == null || kind == null || (kind != Kame.KIND_POCKET && kind != Kame.KIND_SPIN)) {
            return 0.0F;
        }
        return smooth((now - at) / Kame.POCKET_SPREAD_TICKS);
    }

    /** Carapace tournoyante en cours (membres rentrés, la carapace seule tourne). */
    public static boolean spinning(Entity entity, float now) {
        Integer at = entity.getAttached(ModAttachments.KAME_RETRACT);
        Integer kind = entity.getAttached(ModAttachments.KAME_RETRACT_KIND);
        float age = at == null ? -1.0F : now - at;
        return kind != null && kind == Kame.KIND_SPIN && age >= Kame.SPIN_RETRACT_TICKS;
    }

    public static void apply(PlayerModel model, Entity entity, float now) {
        float k = Math.max(-0.2F, Math.min(1.0F, retraction(entity, now)));
        float w = spread(entity, now);
        if (k == 0.0F && w <= 0.0F) {
            if (model.head.yScale != 1.0F) { // membres ressortis : tailles normales (au cas où le modèle ne les remettrait pas)
                for (ModelPart part : new ModelPart[] {model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
                    scale(part, 1.0F);
                }
            }
            return;
        }
        // 1) posture presque en T : bras écartés (pas complètement), jambes un peu ouvertes, tête relevée
        if (w > 0.0F) {
            model.rightArm.zRot += (1.15F - model.rightArm.zRot) * w;
            model.leftArm.zRot += (-1.15F - model.leftArm.zRot) * w;
            model.rightArm.xRot *= 1.0F - w;
            model.leftArm.xRot *= 1.0F - w;
            model.rightLeg.zRot += (0.12F - model.rightLeg.zRot) * w;
            model.leftLeg.zRot += (-0.12F - model.leftLeg.zRot) * w;
            model.rightLeg.xRot *= 1.0F - w;
            model.leftLeg.xRot *= 1.0F - w;
            model.head.xRot += (-0.2F - model.head.xRot) * w;
        }
        // 2) aspiration PAR LES OUVERTURES de la carapace : chaque membre reste accroché à son ouverture (son articulation : la
        //    fente de l'épaule pour les bras, l'ouverture du bas pour les jambes, le col pour la tête) et y est avalé par son
        //    EXTRÉMITÉ : il raccourcit le long de son propre axe vers l'ouverture (la main remonte en premier, puis l'avant-bras,
        //    puis le haut du bras ; le pied d'abord ; le haut de la tête descend vers le col). Rien n'est tiré vers le centre du
        //    torse, rien ne traverse les plaques. Sortie : l'inverse, le haut du membre sort en premier, la main ou le pied en
        //    dernier ; k négatif = petit dépassement (éjection), le membre sort un peu trop loin avant de se poser.
        float length = 1.0F - k;            // 1 = membre entier dehors, 0 = tout rentré, > 1 = éjecté un peu trop loin
        model.head.yScale = Math.max(0.0F, length);
        model.head.xScale = model.head.zScale = 1.0F;
        for (ModelPart limb : new ModelPart[] {model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
            limb.yScale = Math.max(0.0F, length);
            limb.xScale = limb.zScale = 1.0F;
        }
        if (k >= 0.97F) {               // entièrement avalés : plus rien ne dépasse des ouvertures
            model.head.visible = false;
            model.rightArm.visible = false;
            model.leftArm.visible = false;
            model.rightLeg.visible = false;
            model.leftLeg.visible = false;
            Integer kind = entity.getAttached(ModAttachments.KAME_RETRACT_KIND);
            if (spinning(entity, now) || (kind != null && (kind == Kame.KIND_POCKET || kind == Kame.KIND_HIDDEN))) {
                model.body.visible = false; // seule la carapace reste (tournoyante, posée, ou qui se redresse)
            }
        }
    }

    private static void scale(ModelPart part, float s) {
        part.xScale = s;
        part.yScale = s;
        part.zScale = s;
    }

    static float smooth(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }
}
