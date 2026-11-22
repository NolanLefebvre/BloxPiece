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
    private static final int COLOR_FRUIT = 0xFFC060FF; // violet : couleur des noms de fruits du démon
    private static final int COLOR_GOLD = 0xFFFFD25A;

    // Un seul ItemStack par fruit (inutile d'en recréer un à chaque image)
    private static final Map<String, ItemStack> FRUIT_STACKS = new HashMap<>();

    private CharacterInfoRows() {
    }

    public static List<CharacterInfoRow> create() {
        return List.of(
                // Job (à la place du pseudo, désormais en petit en haut à droite du menu) : cliquer ouvre le choix des jobs
                new CharacterInfoRow(icon("job"), label("job"), 0xFF6FA8FF,
                        CharacterInfoRows::jobValue)
                        // cliquable seulement tant qu'aucun job n'est choisi (ensuite : définitif, affiche juste le job)
                        .withClick(Component.translatable("screen.bloxpiece.character.job_tooltip"),
                                player -> nolan.bloxpiece.job.Jobs.get(player) == null,
                                CharacterScreen::openJobs)
                        // avant le niveau 5 : grisée, cadenas, pas de clic, infobulle « Niveau 5 requis... »
                        .withLock(player -> Progression.get(player).level() < nolan.bloxpiece.job.Jobs.REQUIRED_LEVEL,
                                player -> Component.translatable("screen.bloxpiece.character.job_locked_tooltip",
                                        nolan.bloxpiece.job.Jobs.REQUIRED_LEVEL, Progression.get(player).level())),

                new CharacterInfoRow(icon("fruit"), label("fruit"), 0xFFFF8A3D,
                        CharacterInfoRows::fruitValue)
                        .withItemIcon(CharacterInfoRows::fruitStack)
                        // Cliquer sur le fruit (icône ou nom) ouvre la page de ses attaques ; sans fruit, rien à voir
                        .withClick(Component.translatable("screen.bloxpiece.character.fruit_tooltip"),
                                player -> Fruits.get(player.getAttached(ModAttachments.FRUIT)) != null,
                                CharacterScreen::openAbilities),

                // Race : son icône et son nom ; infobulle = courte description ; clic = menu Race (bonus, attaques, reroll).
                // Relue à chaque image depuis la race synchronisée : à jour tout de suite après un reroll.
                new CharacterInfoRow(icon("race"), label("race"), 0xFF4FD1B5,
                        player -> {
                            Race race = Races.of(player);
                            return new Value(Component.translatable(race.translationKey()), race.color());
                        })
                        .withIconTexture(player -> Races.of(player).icon())
                        .withClick(Component.translatable("screen.bloxpiece.race.open"), player -> true, CharacterScreen::openRace)
                        .withTooltip(player -> Component.translatable(Races.of(player).descriptionKey())),

                new CharacterInfoRow(icon("level"), label("level"), COLOR_GOLD,
                        CharacterInfoRows::levelValue)
        );
    }

    private static Value jobValue(LocalPlayer player) {
        nolan.bloxpiece.job.Job job = nolan.bloxpiece.job.Jobs.get(player);
        if (job != null) {
            return new Value(Component.translatable(job.nameKey()), job.color);
        }
        if (Progression.get(player).level() < nolan.bloxpiece.job.Jobs.REQUIRED_LEVEL) {
            return new Value(Component.translatable("screen.bloxpiece.character.job_locked", nolan.bloxpiece.job.Jobs.REQUIRED_LEVEL),
                    COLOR_MUTED);
        }
        return new Value(Component.translatable("screen.bloxpiece.character.job_choose"), COLOR_GOLD);
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
