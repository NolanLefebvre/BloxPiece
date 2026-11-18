package nolan.bloxpiece.treasure;

import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;

/**
 * Rareté des fruits du démon dans le Coffre au Trésor : la couleur du nom affiché par la roulette et la chance de tirage (poids).
 * Pour changer la rareté d'un fruit ou en ajouter un, modifie {@link #BY_ITEM}. Un fruit absent de la liste est Rare.
 */
public enum FruitRarity {
    COMMON("common", 0xFFD0D0D0, 40),
    RARE("rare", 0xFF4FA8FF, 30),
    LEGENDARY("legendary", 0xFFFFB028, 20),
    MYTHIC("mythic", 0xFFFF4468, 10);

    /** Rareté de chaque fruit (id de l'objet). */
    private static final Map<String, FruitRarity> BY_ITEM = Map.of(
            "bloxpiece:hie_hie_no_mi", RARE,
            "bloxpiece:mera_mera_no_mi", LEGENDARY,
            "bloxpiece:hito_hito_no_mi_model_daibutsu", LEGENDARY,
            "bloxpiece:gura_gura_no_mi", MYTHIC
    );

    public final String id;
    /** Couleur ARGB du nom et des effets de la roulette. */
    public final int color;
    /** Poids de tirage : un fruit Commun (40) sort 4 fois plus souvent qu'un fruit Mythique (10). */
    public final int weight;

    FruitRarity(String id, int color, int weight) {
        this.id = id;
        this.color = color;
        this.weight = weight;
    }

    public String translationKey() {
        return "rarity.bloxpiece." + this.id;
    }

    public static FruitRarity of(Item item) {
        return BY_ITEM.getOrDefault(BuiltInRegistries.ITEM.getKey(item).toString(), RARE);
    }

    /** Tire un fruit selon les poids de rareté. */
    public static Item pick(List<Item> fruits, RandomSource random) {
        int total = 0;
        for (Item fruit : fruits) {
            total += of(fruit).weight;
        }
        int roll = random.nextInt(total);
        for (Item fruit : fruits) {
            roll -= of(fruit).weight;
            if (roll < 0) {
                return fruit;
            }
        }
        return fruits.get(fruits.size() - 1);
    }
}
