package nolan.bloxpiece.registry;

import static net.minecraft.commands.arguments.EntityArgument.getPlayer;
import static net.minecraft.commands.arguments.EntityArgument.player;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.ability.AbilityManager;
import nolan.bloxpiece.progression.LevelCurve;
import nolan.bloxpiece.progression.PlayerProgress;
import nolan.bloxpiece.progression.Progression;

public class ModCommands {

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            dispatcher.register(
                    Commands.literal("removedf")
                            // Réservé aux opérateurs / game masters (modifiable via un mod de permissions)
                            .requires(PermissionPredicates.require(
                                    BloxPiece.id("command/removedf"), PermissionLevel.GAMEMASTERS))
                            // /removedf  -> retire ton propre fruit
                            .executes(context -> removeFruit(context.getSource(), context.getSource().getPlayerOrException()))
                            // /removedf <joueur>  -> retire le fruit d'un joueur
                            .then(Commands.argument("joueur", player())
                                    .executes(context -> removeFruit(context.getSource(), getPlayer(context, "joueur")))));

            // /bloxpiece addxp <joueur> <quantité>  -> commande de test du système de niveaux
            //   quantité > 0 : ajoute de l'XP ; quantité < 0 : en retire (le niveau peut baisser) ; 0 : ne change rien
            // /bloxpiece resetcooldowns [joueur]  -> remet à zéro les recharges des attaques du fruit et des trois Hakis
            // Les sous-commandes héritent de la permission de "bloxpiece" (opérateurs / game masters).
            dispatcher.register(
                    Commands.literal("bloxpiece")
                            .requires(PermissionPredicates.require(
                                    BloxPiece.id("command/bloxpiece"), PermissionLevel.GAMEMASTERS))
                            .then(Commands.literal("addxp")
                                    .then(Commands.argument("joueur", player())
                                            .then(Commands.argument("quantite", IntegerArgumentType.integer())
                                                    .executes(context -> addXp(
                                                            context.getSource(),
                                                            getPlayer(context, "joueur"),
                                                            IntegerArgumentType.getInteger(context, "quantite"))))))
                            .then(Commands.literal("resetcooldowns")
                                    // sans joueur : tes propres recharges
                                    .executes(context -> resetCooldowns(
                                            context.getSource(), context.getSource().getPlayerOrException()))
                                    .then(Commands.argument("joueur", player())
                                            .executes(context -> resetCooldowns(
                                                    context.getSource(), getPlayer(context, "joueur"))))));
        });
    }

    private static int removeFruit(CommandSourceStack source, ServerPlayer target) {
        if (target.getAttached(ModAttachments.FRUIT) == null) {
            source.sendFailure(Component.translatable("message.bloxpiece.removedf.none", target.getDisplayName()));
            return 0;
        }

        // Retirer le fruit suffit : pouvoirs, résistance et faiblesse disparaissent avec lui
        target.removeAttached(ModAttachments.FRUIT);
        AbilityManager.clearCooldowns(target);

        target.sendSystemMessage(Component.translatable("message.bloxpiece.removedf.lost"));
        source.sendSuccess(() -> Component.translatable("message.bloxpiece.removedf.success", target.getDisplayName()), true);
        return 1;
    }

    private static int addXp(CommandSourceStack source, ServerPlayer target, int amount) {
        Component name = target.getDisplayName();

        if (amount == 0) {
            source.sendSuccess(() -> Component.translatable("message.bloxpiece.addxp.none", name), true);
            return 1;
        }

        // Les changements de niveau (en plus comme en moins) sont gérés automatiquement par Progression.
        // Le client reçoit le nouveau niveau tout seul : HUD et cadenas des attaques se mettent à jour immédiatement.
        PlayerProgress progress = Progression.addXp(target, amount);

        if (amount < 0) {
            long removed = -(long) amount; // évite le débordement avec Integer.MIN_VALUE
            source.sendSuccess(() -> Component.translatable(
                    "message.bloxpiece.addxp.removed", removed, name, progress.level(), progress.xp(),
                    LevelCurve.xpForNextLevel(progress.level())), true);
        } else if (LevelCurve.isMaxLevel(progress.level())) {
            source.sendSuccess(() -> Component.translatable(
                    "message.bloxpiece.addxp.success.max", amount, name, progress.level()), true);
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "message.bloxpiece.addxp.success", amount, name, progress.level(), progress.xp(),
                    LevelCurve.xpForNextLevel(progress.level())), true);
        }
        return 1;
    }

    private static int resetCooldowns(CommandSourceStack source, ServerPlayer target) {
        // Efface les recharges côté serveur (là où elles comptent vraiment) ET prévient le HUD du joueur
        // attaques du fruit ET les trois Hakis (Armement, Observation, Rois)
        int reset = AbilityManager.clearCooldowns(target) + nolan.bloxpiece.haki.HakiManager.clearCooldowns(target);
        if (reset == 0) {
            source.sendFailure(Component.translatable("message.bloxpiece.resetcooldowns.none", target.getDisplayName()));
            return 0;
        }

        if (source.getEntity() != target) {
            target.sendSystemMessage(Component.translatable("message.bloxpiece.resetcooldowns.notify"));
        }
        source.sendSuccess(() -> Component.translatable(
                "message.bloxpiece.resetcooldowns.success", target.getDisplayName(), reset), true);
        return reset;
    }
}
