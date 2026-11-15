package nolan.bloxpiece.registry;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

import nolan.bloxpiece.BloxPiece;

public class ModItems {

    public static <T extends Item> T register(String name, Function<Item.Properties, T> itemFactory, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, BloxPiece.id(name));
        T item = itemFactory.apply(settings.setId(itemKey));
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);
        return item;
    }

    // Ce qui se passe quand on mange le fruit : on reçoit la malédiction de l'eau (durée infinie)
    public static final Consumable FIRE_FRUIT_CONSUMABLE = Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(
                    new MobEffectInstance(ModEffects.WATER_CURSE, MobEffectInstance.INFINITE_DURATION, 0), 1.0f))
            .build();

    public static final FoodProperties FIRE_FRUIT_FOOD = new FoodProperties.Builder()
            .alwaysEdible()
            .build();

    public static final Item FIRE_FRUIT = register(
            "fire_fruit",
            Item::new,
            new Item.Properties().food(FIRE_FRUIT_FOOD, FIRE_FRUIT_CONSUMABLE)
    );

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
                .register(tab -> tab.accept(FIRE_FRUIT));
    }
}
