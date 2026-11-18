package nolan.bloxpiece.registry;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.item.BloxXpItem;
import nolan.bloxpiece.item.BubbleCoralItem;
import nolan.bloxpiece.handcuffs.HandcuffKeyItem;
import nolan.bloxpiece.handcuffs.SeastoneHandcuffsItem;
import nolan.bloxpiece.item.DevilFruitItem;
import nolan.bloxpiece.item.FlintlockItem;
import nolan.bloxpiece.item.IceSaberItem;
import nolan.bloxpiece.item.PirateSaberItem;
import nolan.bloxpiece.treasure.TreasureChestItem;

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
            new Item.Properties().food(FRUIT_FOOD, FRUIT_CONSUMABLE).rarity(Rarity.EPIC) // nom en violet
    );

    public static final Item BUDDHA_FRUIT = register(
            "buddha_fruit",
            properties -> new DevilFruitItem(properties, Fruits.BUDDHA),
            new Item.Properties().food(FRUIT_FOOD, FRUIT_CONSUMABLE).rarity(Rarity.EPIC) // nom en violet
    );

    public static final Item GURA_FRUIT = register(
            "gura_fruit",
            properties -> new DevilFruitItem(properties, Fruits.GURA),
            new Item.Properties().food(FRUIT_FOOD, FRUIT_CONSUMABLE).rarity(Rarity.EPIC) // nom en violet
    );

    public static final Item ICE_FRUIT = register(
            "ice_fruit",
            properties -> new DevilFruitItem(properties, Fruits.ICE),
            new Item.Properties().food(FRUIT_FOOD, FRUIT_CONSUMABLE).rarity(Rarity.EPIC) // nom en violet
    );

    // Trident de glace (attaque 1 du fruit de la Glace) : créé par l'attaque, jamais obtenu autrement (absent des onglets créatifs).
    // Il fond s'il est jeté (ItemEntityMixin), lancé (Partisan) ou si l'attaque est relancée.
    public static final Item ICE_SABER = register(
            "ice_saber",
            IceSaberItem::new,
            new Item.Properties().stacksTo(1).attributes(IceSaberItem.attributes()) // dégâts affichés comme une épée
    );

    // Armes des pirates : utilisables par le joueur, lâchées parfois par les pirates (voir leurs tables de butin)
    public static final Item PIRATE_SABER = register(
            "pirate_saber",
            PirateSaberItem::new,
            new Item.Properties().stacksTo(1).durability(400).attributes(PirateSaberItem.attributes())
    );

    /** Grand sabre du Capitaine pirate : lent mais très lourd (12 dégâts, 1,3 coup par seconde). */
    public static final Item CAPTAIN_SABER = register(
            "captain_saber",
            PirateSaberItem::new,
            new Item.Properties().stacksTo(1).durability(900).attributes(PirateSaberItem.attributes(12.0, -2.7))
    );

    public static final Item FLINTLOCK = register(
            "flintlock",
            FlintlockItem::new,
            new Item.Properties().stacksTo(1).durability(250)
    );

    // Cristaux d'XP BloxPiece : une seule famille, 4 paliers (rang pirate, couleur = rareté). XP du système de niveaux du mod,
    // voir LevelCurve : niveau 1 -> 2 = 100 XP, niveau 10 -> 11 = 3 162 XP, niveau 50 -> 51 = 35 355 XP
    public static final Item XP_CRYSTAL_CABIN_BOY = register("xp_crystal_cabin_boy",
            properties -> new BloxXpItem(properties, 500), new Item.Properties().stacksTo(64).rarity(Rarity.COMMON));
    public static final Item XP_CRYSTAL_PIRATE = register("xp_crystal_pirate",
            properties -> new BloxXpItem(properties, 5_000), new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON));
    public static final Item XP_CRYSTAL_SUPERNOVA = register("xp_crystal_supernova",
            properties -> new BloxXpItem(properties, 25_000), new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
    public static final Item XP_CRYSTAL_EMPEROR = register("xp_crystal_emperor",
            properties -> new BloxXpItem(properties, 100_000), new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));

    // Coffre au Trésor : la seule façon d'obtenir un fruit du démon (coffres du capitaine pirate). Clic droit dans le vide = roulette
    // des fruits (voir TreasureChests) ; clic droit sur un bloc = le poser, puis clic droit sur le coffre posé pour l'ouvrir.
    public static final Item TREASURE_CHEST = register("treasure_chest",
            properties -> new TreasureChestItem(ModBlocks.TREASURE_CHEST, properties),
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

    // Corail à Bulles : écrasé dans la main (clic droit), il entoure le joueur d'une bulle d'air qui lui permet de respirer sous
    // l'eau et isole un porteur de fruit du démon de l'eau ; elle éclate au moindre dégât (voir BubbleCoral)
    // 5 utilisations (barre d'usure), puis il est épuisé
    public static final Item BUBBLE_CORAL = register("bubble_coral", BubbleCoralItem::new,
            new Item.Properties().durability(BubbleCoralItem.USES).rarity(Rarity.UNCOMMON));

    // Granite Marin (Seastone) : minerai des fonds océaniques, fragment brut, lingot (cuisson), pépites, menottes (pas encore d'effet)
    public static final Item SEASTONE_ORE = register("seastone_ore",
            properties -> new BlockItem(ModBlocks.SEASTONE_ORE, properties), new Item.Properties());
    public static final Item RAW_SEASTONE = register("raw_seastone", Item::new, new Item.Properties());
    public static final Item SEASTONE_INGOT = register("seastone_ingot", Item::new, new Item.Properties());
    public static final Item SEASTONE_NUGGET = register("seastone_nugget", Item::new, new Item.Properties());
    // Menottes : clic droit sur un joueur pour le menotter ; la clé le libère (ou se libère soi-même). Voir Handcuffs.
    public static final Item SEASTONE_HANDCUFFS = register("seastone_handcuffs", SeastoneHandcuffsItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final Item HANDCUFF_KEY = register("handcuff_key", HandcuffKeyItem::new,
            new Item.Properties().stacksTo(1));

    /** Onglet créatif du mod : tous les objets BloxPiece, séparés des objets vanilla. */
    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, BloxPiece.id("bloxpiece"));

    public static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.bloxpiece"))
                .icon(() -> new ItemStack(FIRE_FRUIT))
                .displayItems((context, entries) -> {
                    // Fruits, armes, objets d'XP... puis tout autre objet du mod ajouté plus tard (sauf le trident de glace,
                    // qui n'existe qu'à travers l'attaque Ice Saber et fondrait aussitôt)
                    java.util.List<Item> first = java.util.List.of(TREASURE_CHEST, FIRE_FRUIT, ICE_FRUIT, BUDDHA_FRUIT, GURA_FRUIT,
                            PIRATE_SABER, CAPTAIN_SABER, FLINTLOCK,
                            XP_CRYSTAL_CABIN_BOY, XP_CRYSTAL_PIRATE, XP_CRYSTAL_SUPERNOVA, XP_CRYSTAL_EMPEROR);
                    first.forEach(entries::accept);
                    BuiltInRegistries.ITEM.stream()
                            .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(BloxPiece.MOD_ID))
                            .filter(item -> !first.contains(item) && item != ICE_SABER)
                            .forEach(entries::accept);
                })
                .build());
    }
}
