package nolan.bloxpiece.client.cyborg;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import nolan.bloxpiece.ability.Cyborg;
import nolan.bloxpiece.ability.CyborgCannonArmAbility;
import nolan.bloxpiece.ability.CyborgEnergyShieldAbility;
import nolan.bloxpiece.ability.CyborgRadicalBeamAbility;

/**
 * Race Cyborg à la PREMIÈRE PERSONNE (même principe que la race Humaine : HumanFirstPerson) : les bras suivent les phases des
 * quatre attaques, avec les mêmes instants et les mêmes courbes que la pose vue de l'extérieur (CyborgPose).
 * <ul>
 *   <li>Cannon Arm : le bras MÉCANIQUE (gauche) monte dans le champ de vision et vise au centre, pendant que le bras se
 *       transforme en canon (CyborgArmLayer) ; recul au tir ; il redescend une fois replié. L'autre main se cale.</li>
 *   <li>Bouclier énergétique : les deux bras montent vers le ciel pendant le déploiement, puis redescendent.</li>
 *   <li>Surcharge moteur : les deux poings se serrent et se ramènent, de plus en plus tremblants pendant le démarrage.</li>
 *   <li>Radical Beam : les deux bras se tendent devant, les mains se rejoignent au centre ; tremblement pendant le tir.</li>
 * </ul>
 * Minecraft ne dessine pas une main vide qui n'est pas la main principale : pendant ces attaques, l'autre bras (souvent le
 * bras mécanique) est dessiné en plus par CyborgFirstPersonMixin ({@link #offArm}), à sa place normale, puis animé.
 * Repère de la caméra : x droite, y haut, -z devant ; les clés sont données côté droit et mises en miroir pour le bras gauche
 * (x positif = vers l'extérieur).
 */
public final class CyborgFirstPerson {

    /** Position de la main tenue par Minecraft (côté droit ; repère de la caméra). */
    private static final float HAND_X = 0.56F, HAND_Y = -0.52F, HAND_Z = -0.72F;

    private CyborgFirstPerson() {
    }

    /** Une pose de la main : déplacement (blocs), rotations (degrés) et visibilité (0 = hors de l'écran, sous le champ de vision). */
    private record Key(float x, float y, float z, float rx, float ry, float rz, float w) {
        static final Key REST = new Key(0, 0, 0, 0, 0, 0, 0);

        Key lerp(Key b, float k) {
            return new Key(Mth.lerp(k, x, b.x), Mth.lerp(k, y, b.y), Mth.lerp(k, z, b.z), Mth.lerp(k, rx, b.rx), Mth.lerp(k, ry, b.ry),
                    Mth.lerp(k, rz, b.rz), Mth.lerp(k, w, b.w));
        }

        Key shake(float amount, float age) {
            return new Key(x + Mth.sin(age * 3.7F) * amount, y + Mth.cos(age * 3.1F) * amount, z, rx, ry, rz, w);
        }

        Key plus(float dx, float dy, float dz, float drx) {
            return new Key(x + dx, y + dy, z + dz, rx + drx, ry, rz, w);
        }
    }

    private static float ramp(float t, float a, float b) {
        return CyborgClient.ramp(t, a, b);
    }

    /** Pose de l'attaque {@code kind} pour un bras ({@code mech} : le bras mécanique gauche), ou null. */
    private static Key key(int kind, float e, float age, boolean mech) {
        switch (kind) {
            case Cyborg.CANNON -> {
                float up = ramp(e, 0.0F, CyborgCannonArmAbility.RAISE) * (1.0F - ramp(e, CyborgCannonArmAbility.DURATION - 8,
                        CyborgCannonArmAbility.DURATION));
                if (mech) {
                    // le bras canon monte et vise au centre de l'écran ; recul au tir
                    Key aim = new Key(-0.24F, 0.16F, -0.08F, -6.0F, 12.0F, 4.0F, 1.0F);
                    Key k = Key.REST.lerp(aim, up);
                    float f = CyborgCannonArmAbility.FIRE;
                    if (e >= f && e < f + 6.0F) {
                        float r = 1.0F - (e - f) / 6.0F;
                        k = k.plus(0.0F, 0.04F * r, 0.2F * r, 14.0F * r);
                    }
                    if (e >= CyborgCannonArmAbility.RAISE && e < CyborgCannonArmAbility.ARMED) {
                        k = k.shake(0.004F, age); // les plaques s'ouvrent : le bras vibre
                    }
                    return k;
                }
                return Key.REST.lerp(new Key(0.06F, -0.06F, 0.06F, 6.0F, 0.0F, 0.0F, 1.0F), up); // l'autre main se cale
            }
            case Cyborg.SHIELD -> {
                float up = ramp(e, 0.0F, 6.0F) * (1.0F - ramp(e, CyborgEnergyShieldAbility.DEPLOY - 4, CyborgEnergyShieldAbility.DEPLOY));
                Key raised = new Key(-0.08F, 0.62F, -0.12F, 38.0F, 0.0F, -6.0F, 1.0F);
                return Key.REST.lerp(raised, up).shake(0.008F * up, age + (mech ? 1.3F : 0.0F));
            }
            case Cyborg.OVERDRIVE -> {
                float k = ramp(e, 0.0F, 8.0F) * (1.0F - ramp(e, Cyborg.OVERDRIVE_WINDUP, Cyborg.OVERDRIVE_WINDUP + 6.0F));
                Key flexed = new Key(0.06F, -0.1F, 0.14F, 18.0F, -14.0F, 26.0F, 1.0F);
                return Key.REST.lerp(flexed, k).shake(0.018F * ramp(e, 0.0F, Cyborg.OVERDRIVE_WINDUP) * k, age + (mech ? 2.1F : 0.0F));
            }
            case Cyborg.BEAM -> {
                float k = ramp(e, 0.0F, 6.0F) * (1.0F - ramp(e, CyborgRadicalBeamAbility.DURATION - 6, CyborgRadicalBeamAbility.DURATION));
                // les deux mains tendues se rejoignent au centre de l'écran
                Key joined = new Key(-0.36F, 0.12F, -0.12F, -4.0F, 18.0F, 0.0F, 1.0F);
                Key p = Key.REST.lerp(joined, k);
                float c = CyborgRadicalBeamAbility.CHARGE;
                if (e < c) {
                    p = p.shake(0.005F * ramp(e, 6.0F, c), age);
                } else if (e < c + CyborgRadicalBeamAbility.BEAM_LIFE) {
                    float r = e < c + 4.0F ? 1.0F - (e - c) / 4.0F : 0.0F;
                    p = p.plus(0.0F, 0.02F * r, 0.12F * r, 8.0F * r).shake(0.012F, age); // recul du tir, puis tremblement
                }
                return p;
            }
            default -> {
                return null;
            }
        }
    }

    private static Key current(LocalPlayer player, float partialTicks, boolean mech) {
        if (!CyborgClient.isCyborg(player)) {
            return null;
        }
        int kind = CyborgClient.kind(player);
        float age = player.tickCount + partialTicks;
        float e = CyborgClient.elapsed(player, age);
        return kind == 0 || e < 0.0F ? null : key(kind, e, age, mech);
    }

    /** Applique la pose à la main principale (CyborgFirstPersonMixin, début du dessin de la main). */
    public static void applyMain(PoseStack pose, float partialTicks) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        HumanoidArm side = player.getMainArm();
        Key k = current(player, partialTicks, side == HumanoidArm.LEFT);
        if (k != null) {
            transform(pose, k, side, false);
        }
    }

    /**
     * L'AUTRE bras doit-il être dessiné en plus (une attaque l'anime et la main secondaire est vide) ? Si oui, pose la
     * transformation et renvoie le côté à dessiner ; sinon null (rien n'est poussé).
     */
    public static HumanoidArm offArm(PoseStack pose, float partialTicks) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.getOffhandItem().isEmpty() || player.isInvisible()) {
            return null;
        }
        HumanoidArm side = player.getMainArm().getOpposite();
        Key k = current(player, partialTicks, side == HumanoidArm.LEFT);
        if (k == null || k.w <= 0.01F) {
            return null;
        }
        transform(pose, k, side, true);
        return side;
    }

    /**
     * Radical Beam à la première personne : la même boule d'énergie qu'à la troisième personne (CyborgArmLayer.drawCharge)
     * grossit ENTRE LES MAINS jointes, au centre de l'écran, suit leur tremblement, puis éclate au tir. Appelé à la fin du
     * dessin des mains (CyborgFirstPersonMixin), dans le repère de la caméra. Taille réduite : les mains sont tout près de l'œil.
     */
    public static void submitBeamCharge(PoseStack pose, SubmitNodeCollector collector, float partialTicks) {
        LocalPlayer player = Minecraft.getInstance().player;
        ModelPart segment = CyborgArmLayer.segment();
        if (player == null || segment == null || player.isInvisible() || !CyborgClient.isCyborg(player)
                || CyborgClient.kind(player) != Cyborg.BEAM) {
            return;
        }
        float age = player.tickCount + partialTicks;
        float e = CyborgClient.elapsed(player, age);
        Key k = e < 0.0F ? null : key(Cyborg.BEAM, e, age, false);
        if (k == null) {
            return;
        }
        pose.pushPose();
        // entre les deux mains (symétriques : x = 0), un peu au-dessus et devant les poings
        pose.translate(0.0F, HAND_Y + k.y + 0.1F, HAND_Z + k.z - 0.14F);
        CyborgArmLayer.drawCharge(segment, pose, collector, e, age, player.getId(), CHARGE_SCALE);
        pose.popPose();
    }

    /** Taille de la boule du Radical Beam à la première personne (par rapport à la vue de l'extérieur). */
    private static final float CHARGE_SCALE = 0.55F;

    /** Déplacement et rotations autour de la main ; un bras qui apparaît monte depuis le bas de l'écran ({@code rise}). */
    private static void transform(PoseStack pose, Key k, HumanoidArm arm, boolean rise) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float hx = HAND_X * side;
        float below = rise ? (1.0F - k.w) * 0.9F : 0.0F;
        pose.translate(k.x * side, k.y - below, k.z);
        pose.translate(hx, HAND_Y, HAND_Z);
        pose.rotateDegrees(Axis.XP, k.rx);
        pose.rotateDegrees(Axis.YP, k.ry * side);
        pose.rotateDegrees(Axis.ZP, k.rz * side);
        pose.translate(-hx, -HAND_Y, -HAND_Z);
    }
}
