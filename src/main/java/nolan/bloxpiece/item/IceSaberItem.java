package nolan.bloxpiece.item;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Le trident de glace (attaque 1 du fruit de la Glace) : un item d'arme, avec des dégâts et une vitesse d'attaque affichés dans
 * son infobulle comme pour une épée ("En main principale : 16 de dégâts d'attaque, 1,4 de vitesse d'attaque").
 *
 * Les attributs utilisent les identifiants de base de Minecraft (ceux des épées), d'où cette petite sous-classe : ils ne sont
 * accessibles qu'aux classes d'items. Les vrais dégâts du coup sont des dégâts de glace (IceSaberAbility.onMelee) du même
 * montant : le coup vanilla qui suit ne rajoute rien (invulnérabilité de la cible), l'infobulle dit donc juste.
 */
public class IceSaberItem extends Item {

    /** Dégâts affichés et infligés par un coup (1 de base + 15). */
    public static final float DAMAGE = 16.0F;
    /** Vitesse d'attaque : 4 de base - 2,6 = 1,4 coup par seconde (épée : 1,6 ; trident : 1,1). */
    private static final double ATTACK_SPEED = -2.6;

    public IceSaberItem(Properties properties) {
        super(properties);
    }

    /** Les attributs d'arme du trident, à passer aux propriétés de l'item. */
    public static ItemAttributeModifiers attributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, DAMAGE - 1.0, Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, ATTACK_SPEED, Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }
}
