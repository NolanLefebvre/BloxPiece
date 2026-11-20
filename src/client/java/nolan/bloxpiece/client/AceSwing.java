package nolan.bloxpiece.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import nolan.bloxpiece.fx.AceSlash;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Où en est le geste de la Lame dévastatrice (épée Ace), pour la troisième personne (PlayerModelMixin) et la première personne
 * (AceSlashFirstPersonMixin) : les deux lisent les mêmes attachments synchronisés (ACE_CHARGE, ACE_SLASH) et les mêmes durées
 * (AceSlash), donc bras, épée et départ de la lame restent synchronisés.
 * <p>
 * Le geste passe d'une pose clé à une autre : {@link #REST} (tenue normale), {@link #RAISED} (épée levée en haut à droite,
 * tenue pendant toute la charge), {@link #STRUCK} (fin de la frappe, en bas à gauche : la lame part à cet instant).
 */
public final class AceSwing {

    public static final int REST = 0, RAISED = 1, STRUCK = 2;

    /** De la pose {@code from} vers la pose {@code to}, avancée {@code k} (0 à 1, déjà lissée). */
    public record Step(int from, int to, float k) {
    }

    private AceSwing() {
    }

    /** Deuxième diagonale (haut gauche -> bas droite) choisie pour ce coup ? */
    public static boolean reverse(Entity entity) {
        return Boolean.TRUE.equals(entity.getAttached(ModAttachments.ACE_REVERSE));
    }

    /** Le geste en cours, ou null (pose normale de Minecraft). {@code now} = temps de jeu + fraction de tick. */
    public static Step at(Entity entity, float now) {
        Integer strike = entity.getAttached(ModAttachments.ACE_SLASH);
        if (strike != null) {
            float t = now - strike;
            if (t < 0.0F && t > -AceSlash.RAISE_TICKS - 1.0F) {
                return new Step(RAISED, RAISED, 1.0F); // relâché tôt : la frappe attend que l'épée soit levée
            }
            if (t >= 0.0F && t < AceSlash.STRIKE_TICKS) {
                return new Step(RAISED, STRUCK, smooth(t / AceSlash.STRIKE_TICKS));
            }
            if (t >= AceSlash.STRIKE_TICKS && t < AceSlash.STRIKE_TICKS + AceSlash.RECOVER_TICKS) {
                return new Step(STRUCK, REST, smooth((t - AceSlash.STRIKE_TICKS) / AceSlash.RECOVER_TICKS));
            }
        }
        Integer charge = entity.getAttached(ModAttachments.ACE_CHARGE);
        if (charge != null && entity instanceof LivingEntity living && (living.isUsingItem() || !(living instanceof Player))) {
            return new Step(REST, RAISED, smooth((now - charge) / AceSlash.RAISE_TICKS)); // levée, puis tenue pendant la charge
        }
        return null;
    }

    private static float smooth(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Le geste à la troisième personne (bras droit, épaules) sur un modèle humanoïde : joueur (PlayerModelMixin) ou Gol D. Roger
     * (ArmedPirateModel), même squelette, mêmes angles (Roger n'est qu'agrandi). Rien si aucun geste en cours.
     * <ol>
     *   <li>charge : le bras droit se lève (diagonale 1 : épée en haut à droite ; diagonale 2 : main devant, épée derrière
     *       l'épaule gauche) et reste levé, avec un léger tremblement ;</li>
     *   <li>frappe : dans la diagonale opposée, dans le plan même de la lame d'énergie (qui part à la fin de la frappe) ;</li>
     *   <li>retour en douceur à la pose normale.</li>
     * </ol>
     */
    public static void applyThirdPerson(HumanoidModel<?> model, Entity entity, float now, float ageInTicks) {
        Step step = at(entity, now);
        if (step == null) {
            return;
        }
        ModelPart arm = model.rightArm;
        // Le bras balaie LE PLAN DE LA LAME : zRot fixe = ce plan incliné de la diagonale (AceSlashShape.ROLL), seul xRot varie.
        // Levée : bras au-dessus de l'épaule droite, épée en haut à droite et vers l'arrière ; frappée : bras en bas à gauche,
        // devant. Entre les deux il passe droit devant : la lame part dans la direction du geste.
        boolean reverse = reverse(entity);
        float look = model.head.xRot * 0.7F; // suit un peu le regard (haut / bas)
        float tremble = (float) Math.sin(ageInTicks * 1.7F) * 0.03F; // l'énergie s'accumule : l'épée tremble
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
            // tournent vers la gauche (élan). Frappe : l'épée revient par l'avant, en haut à gauche, traverse devant le combattant et
            // finit en bas à droite (pose intermédiaire « milieu » pour que la lame passe devant et non au-dessus de la tête).
            // Angles calculés avec la même chaîne que le rendu (bras + Ace en main) : la lame ne traverse ni la tête ni le corps.
            poses = new float[][] {
                    {arm.xRot, arm.yRot, arm.zRot, model.body.yRot},   // pose normale de Minecraft
                    {-2.2F + look + tremble, -0.2F, 1.15F, -0.35F},    // levée : main devant, lame derrière l'épaule gauche
                    {-0.6F + look, 0.65F, -1.3F, 0.35F},               // frappée : bras en bas à droite, pointe devant à droite
                    {-0.35F + look, -0.6F, -0.75F, 0.0F},              // milieu de la frappe : lame devant, en haut à gauche
            };
            if (step.from() == RAISED && step.to() == STRUCK) {
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
}
