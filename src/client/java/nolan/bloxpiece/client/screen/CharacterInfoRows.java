package nolan.bloxpiece.client.screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.client.screen.CharacterInfoRow.Value;
import nolan.bloxpiece.fruit.DevilFruit;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.progression.LevelCurve;
import nolan.bloxpiece.progression.Progression;
import nolan.bloxpiece.race.Race;
import nolan.bloxpiece.race.Races;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * La liste des informations affichées dans le menu personnage, dans l'ordre.
 * Pour ajouter une information : ajouter UNE ligne ci-dessous (+ son icône et son texte dans la lang).
 * Le menu s'adapte tout seul.
 */
public final class CharacterInfoRows {

    private static final int COLOR_VALUE = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF7C7C88;
    private static final int COLOR_FRUIT = 0xFFFFA24A;
    private static final int COLOR_GOLD = 0xFFFFD25A;

    // Un seul ItemStack par fruit (inutile d'en recréer un à chaque image)
    private static final Map<String, ItemStack> FRUIT_STACKS = new HashMap<>();

    private CharacterInfoRows() {
    }

    public static List<CharacterInfoRow> create() {
        return List.of(
                new CharacterInfoRow(icon("pseudo"), label("pseudo"), 0xFF6FA8FF,
                        player -> new Value(player.getName(), COLOR_VALUE)),

                new CharacterInfoRow(icon("fruit"), label("fruit"), 0xFFFF8A3D,
                        CharacterInfoRows::fruitValue)
                        .withItemIcon(CharacterInfoRows::fruitStack),

                new CharacterInfoRow(icon("race"), label("race"), 0xFF4FD1B5,
                        player -> {
                            Race race = Races.of(player);
                            return new Value(Component.translatable(race.translationKey()), race.color());
                        }),

                new CharacterInfoRow(icon("level"), label("level"), COLOR_GOLD,
                        CharacterInfoRows::levelValue)
        );
    }

    private static Value fruitValue(LocalPlayer player) {
        DevilFruit fruit = Fruits.get(player.getAttached(ModAttachments.FRUIT));
        if (fruit == null) {
            return new Value(Component.translatable("screen.bloxpiece.character.no_fruit"), COLOR_MUTED);
        }
        return new Value(Component.translatable(fruit.translationKey()), COLOR_FRUIT);
    }

    private static ItemStack fruitStack(LocalPlayer player) {
        DevilFruit fruit = Fruits.get(player.getAttached(ModAttachments.FRUIT));
        if (fruit == null) {
            return ItemStack.EMPTY;
        }
        // Si l'item du fruit n'existe pas, le registre renvoie "air" : la ligne garde alors son icône normale
        return FRUIT_STACKS.computeIfAbsent(fruit.id(), id -> new ItemStack(BuiltInRegistries.ITEM.getValue(fruit.itemId())));
    }

    private static Value levelValue(LocalPlayer player) {
        int level = Progression.get(player).level();
        Component text = LevelCurve.isMaxLevel(level)
                ? Component.translatable("screen.bloxpiece.character.level_max", level)
                : Component.literal(Integer.toString(level));
        return new Value(text, COLOR_GOLD);
    }

    private static Identifier icon(String name) {
        return BloxPiece.id("textures/gui/character/" + name + ".png");
    }

    private static Component label(String name) {
        return Component.translatable("screen.bloxpiece.character." + name);
    }
}
