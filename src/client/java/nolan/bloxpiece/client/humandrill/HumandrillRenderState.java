package nolan.bloxpiece.client.humandrill;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/**
 * État de rendu du Humandrill : épées en main (ArmedEntityRenderState), épée de la bouche, attaque synchronisée en cours,
 * garde, réception, virage.
 */
public class HumandrillRenderState extends ArmedEntityRenderState {
    /** Épée serrée entre les mâchoires (vide sauf configuration à 3 épées). */
    public final ItemStackRenderState mouthItemState = new ItemStackRenderState();
    public int swords = 1;
    /** Ticks depuis le début de l'attaque en cours (-1 : aucune), son type (sans drapeau) et la main. */
    public float attackT = -1.0F;
    public int attackKind;
    public boolean attackLeft;
    public float guard;
    public float landT = 100.0F;
    public float landPower;
    public float air;
    /** Rotation du corps (degrés par tick). */
    public float turn;
    /** Taille propre à chaque individu (d'après son UUID). */
    public float size = 1.0F;
}
