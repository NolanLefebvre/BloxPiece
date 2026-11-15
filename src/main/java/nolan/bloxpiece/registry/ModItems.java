package nolan.bloxpiece.registry;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.item.DevilFruitItem;

public class ModItems {

    public static <T extends Item> T register(String name, Function<Item.Properties, T> itemFactory, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, BloxPiece.id(name));
        T item = itemFactory.apply(settings.setId(itemKey));
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);
        return item;
    }

    // Manger un fruit = comportement de nourriture normal. La malédiction n'est plus donnée ici :
    // elle est gérée par le fruit lui-même (voir DevilFruit.refreshCurse).
    public static final Consumable FRUIT_CONSUMABLE = Consumables.defaultFood().build();

    public static final FoodProperties FRUIT_FOOD = new FoodProperties.Builder()
            .alwaysEdible()
            .build();

    public static final Item FIRE_FRUIT = register(
            "fire_fruit",
            properties -> new DevilFruitItem(properties, Fruits.FIRE),
            new Item.Properties().food(FRUIT_FOOD, FRUIT_CONSUMABLE)
    );

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
                .register(tab -> tab.accept(FIRE_FRUIT));
    }
}
