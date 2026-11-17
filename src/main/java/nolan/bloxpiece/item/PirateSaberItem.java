package nolan.bloxpiece.item;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Sabre pirate (arme du Pirate Sabreur, lootable) : une arme rapide. Dégâts et vitesse affichés comme une épée
 * (identifiants d'attributs de base de Minecraft, accessibles seulement aux classes d'items, d'où cette sous-classe).
 */
public class PirateSaberItem extends Item {

    /** 7 dégâts (épée en fer : 6), 1,8 coup par seconde (épée : 1,6). */
    private static final double DAMAGE = 7.0;
    private static final double ATTACK_SPEED = -2.2;

    public PirateSaberItem(Properties properties) {
        super(properties);
    }

    public static ItemAttributeModifiers attributes() {
        return attributes(DAMAGE, ATTACK_SPEED);
    }

    /** Attributs d'un sabre : dégâts affichés et vitesse d'attaque (0 = 4 coups/s, -2,4 = épée...). */
    public static ItemAttributeModifiers attributes(double damage, double attackSpeed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage - 1.0, Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, attackSpeed, Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }
}
