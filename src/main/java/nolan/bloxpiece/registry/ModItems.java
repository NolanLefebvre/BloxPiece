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

    // Identifiants = vrais noms des fruits (ceux de /give et de l'auto-complétion). Anciens identifiants gardés en alias
    // (voir registerAliases) : les fruits déjà présents dans un monde existant ne disparaissent pas.
    public static final Item FIRE_FRUIT = register(
            "mera_mera_no_mi",
            properties -> new DevilFruitItem(properties, Fruits.FIRE),
            new Item.Properties().food(FRUIT_FOOD, FRUIT_CONSUMABLE).rarity(Rarity.EPIC) // nom en violet
    );

    public static final Item BUDDHA_FRUIT = register(
            "hito_hito_no_mi_model_daibutsu",
            properties -> new DevilFruitItem(properties, Fruits.BUDDHA),
            new Item.Properties().food(FRUIT_FOOD, FRUIT_CONSUMABLE).rarity(Rarity.EPIC) // nom en violet
    );

    public static final Item GURA_FRUIT = register(
            "gura_gura_no_mi",
            properties -> new DevilFruitItem(properties, Fruits.GURA),
            new Item.Properties().food(FRUIT_FOOD, FRUIT_CONSUMABLE).rarity(Rarity.EPIC) // nom en violet
    );

    public static final Item KAME_FRUIT = register(
            "kame_kame_no_mi",
            properties -> new DevilFruitItem(properties, Fruits.KAME),
            new Item.Properties().food(FRUIT_FOOD, FRUIT_CONSUMABLE).rarity(Rarity.EPIC) // nom en violet
    );

    public static final Item GORO_FRUIT = register(
            "goro_goro_no_mi",
            properties -> new DevilFruitItem(properties, Fruits.GORO),
            new Item.Properties().food(FRUIT_FOOD, FRUIT_CONSUMABLE).rarity(Rarity.EPIC) // nom en violet ; modèle 3D
    );

    public static final Item ICE_FRUIT = register(
            "hie_hie_no_mi",
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

    /**
     * Ace, le sabre légendaire de Gol D. Roger (modèle 3D : models/item/ace.json, généré par src/blockbench/make_ace.py) :
     * 14 dégâts, 1,6 coup par seconde (comme une épée), très solide. Clic droit : attaque spéciale « Lame dévastatrice du Haki
     * des Rois » (voir AceItem).
     */
    public static final Item ACE = register(
            "ace",
            nolan.bloxpiece.item.AceItem::new,
            new Item.Properties().stacksTo(1).durability(3000).attributes(PirateSaberItem.attributes(14.0, -2.4))
    );

    /**
     * Épées des bandits : modèles 3D originaux (fauchon rustique du Bandit, grand dao à garde en S du Chef :
     * models/item/bandit_sword.json et bandit_chief_sword.json, générés par src/blockbench/bandit_swords/make_bandit_swords.py). Mêmes dégâts que les épées qu'ils avaient avant : fer pour le
     * Bandit (6), diamant pour le Chef (7), 1,6 coup par seconde.
     */
    public static final Item BANDIT_SWORD = register(
            "bandit_sword",
            PirateSaberItem::new,
            new Item.Properties().stacksTo(1).durability(250).attributes(PirateSaberItem.attributes(6.0, -2.4))
    );

    public static final Item BANDIT_CHIEF_SWORD = register(
            "bandit_chief_sword",
            PirateSaberItem::new,
            new Item.Properties().stacksTo(1).durability(1200).attributes(PirateSaberItem.attributes(7.0, -2.4))
    );

    public static final Item FLINTLOCK = register(
            "flintlock",
            FlintlockItem::new,
            new Item.Properties().stacksTo(1).durability(250)
    );

    // Armes de la Marine (modèles 3D à part, lâchées parfois par les Marines) : sabre réglementaire et fusil long à silex
    public static final Item MARINE_SABER = register(
            "marine_saber",
            PirateSaberItem::new,
            new Item.Properties().stacksTo(1).durability(500).attributes(PirateSaberItem.attributes(7.0, -2.3))
    );

    /** Fusil de la Marine : mousquet long, plus lent à recharger que le pistolet mais bien plus puissant et précis. */
    public static final Item MARINE_RIFLE = register(
            "marine_rifle",
            properties -> new FlintlockItem(properties, 50, 14.0F, 4.0),
            new Item.Properties().stacksTo(1).durability(300)
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

    // Race Reroll Token : dans le menu Race (bouton Reroll), il est consommé pour tirer une NOUVELLE race (voir RaceManager)
    public static final Item RACE_REROLL_TOKEN = register("race_reroll_token", Item::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

    /** Onglet créatif du mod : tous les objets BloxPiece, séparés des objets vanilla. */
    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, BloxPiece.id("bloxpiece"));

    /** Anciens identifiants des fruits (avant leurs vrais noms) -> nouveaux, pour les mondes déjà existants. */
    private static final String[][] OLD_IDS = {{"fire_fruit", "mera_mera_no_mi"}, {"buddha_fruit", "hito_hito_no_mi_model_daibutsu"},
            {"ice_fruit", "hie_hie_no_mi"}, {"gura_fruit", "gura_gura_no_mi"}};

    /**
     * Alias de registre : un objet enregistré sous un ancien identifiant (coffre, inventaire d'un monde existant) est lu comme le
     * nouveau. La méthode du registre change selon les versions : essais sans risque (sinon les anciens objets seraient perdus,
     * jamais de plantage).
     */
    private static void registerAliases() {
        for (String[] pair : OLD_IDS) {
            net.minecraft.resources.Identifier from = BloxPiece.id(pair[0]), to = BloxPiece.id(pair[1]);
            Object[][] attempts = {{from, to}, {ResourceKey.create(Registries.ITEM, from), ResourceKey.create(Registries.ITEM, to)}};
            boolean done = false;
            for (java.lang.reflect.Method m : BuiltInRegistries.ITEM.getClass().getMethods()) {
                if (done || !m.getName().equals("addAlias") || m.getParameterCount() != 2) {
                    continue;
                }
                for (Object[] args : attempts) {
                    try {
                        if (m.getParameterTypes()[0].isInstance(args[0]) && m.getParameterTypes()[1].isInstance(args[1])) {
                            m.invoke(BuiltInRegistries.ITEM, args);
                            done = true;
                            break;
                        }
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                        // essai suivant
                    }
                }
            }
            if (!done) {
                BloxPiece.LOGGER.warn("[BloxPiece] Alias d'objet impossible : {} -> {}", from, to);
            }
        }
    }

    public static void initialize() {
        registerAliases();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.bloxpiece"))
                .icon(() -> new ItemStack(FIRE_FRUIT))
                .displayItems((context, entries) -> {
                    // Fruits, armes, objets d'XP... puis tout autre objet du mod ajouté plus tard (sauf le trident de glace,
                    // qui n'existe qu'à travers l'attaque Ice Saber et fondrait aussitôt)
                    java.util.List<Item> first = java.util.List.of(TREASURE_CHEST, FIRE_FRUIT, ICE_FRUIT, BUDDHA_FRUIT, GURA_FRUIT, KAME_FRUIT, GORO_FRUIT,
                            PIRATE_SABER, CAPTAIN_SABER, ACE, BANDIT_SWORD, BANDIT_CHIEF_SWORD, FLINTLOCK, MARINE_SABER, MARINE_RIFLE,
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
