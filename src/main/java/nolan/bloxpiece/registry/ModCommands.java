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
        // Toutes les commandes du mod sont rangées sous /bloxfruit (opérateurs / game masters ; chaque sous-commande a aussi sa
        // propre permission, modifiable via un mod de permissions). Pour en ajouter une : un .then(...) de plus ici.
        //   /bloxfruit removedf [joueur]                                          -> retire le fruit (le tien sans joueur)
        //   /bloxfruit addxp <joueur> <quantité>                                  -> ajoute (ou retire si < 0) de l'XP
        //   /bloxfruit resetcooldowns [joueur]                                    -> recharges des attaques et des Hakis
        //   /bloxfruit job set <joueur> <swordman|brawler|sniper|none>              -> impose (ou retire) le job
        //   /bloxfruit haki unlock|lock <joueur> <armament|observation|conqueror|all> -> débloque / reverrouille un Haki
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
                Commands.literal("bloxfruit")
                        .requires(PermissionPredicates.require(BloxPiece.id("command/bloxfruit"), PermissionLevel.GAMEMASTERS))
                        .then(Commands.literal("removedf")
                                .requires(PermissionPredicates.require(BloxPiece.id("command/removedf"), PermissionLevel.GAMEMASTERS))
                                .executes(context -> removeFruit(context.getSource(), context.getSource().getPlayerOrException()))
                                .then(Commands.argument("joueur", player())
                                        .executes(context -> removeFruit(context.getSource(), getPlayer(context, "joueur")))))
                        .then(Commands.literal("addxp")
                                .then(Commands.argument("joueur", player())
                                        .then(Commands.argument("quantite", IntegerArgumentType.integer())
                                                .executes(context -> addXp(
                                                        context.getSource(),
                                                        getPlayer(context, "joueur"),
                                                        IntegerArgumentType.getInteger(context, "quantite"))))))
                        .then(Commands.literal("resetcooldowns")
                                .executes(context -> resetCooldowns(context.getSource(), context.getSource().getPlayerOrException()))
                                .then(Commands.argument("joueur", player())
                                        .executes(context -> resetCooldowns(context.getSource(), getPlayer(context, "joueur")))))
                        .then(Commands.literal("job")
                                .requires(PermissionPredicates.require(BloxPiece.id("command/job"), PermissionLevel.GAMEMASTERS))
                                .then(jobChoices(Commands.literal("set"))))
                        .then(Commands.literal("haki")
                                .requires(PermissionPredicates.require(BloxPiece.id("command/haki"), PermissionLevel.GAMEMASTERS))
                                .then(hakiTypes(Commands.literal("unlock"), true))
                                .then(hakiTypes(Commands.literal("lock"), false)))));
    }

    /** /bloxfruit job set <joueur> <swordman|brawler|sniper|none> */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> jobChoices(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root) {
        com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> target = Commands.argument("joueur", player());
        for (nolan.bloxpiece.job.Job job : nolan.bloxpiece.job.Job.values()) {
            target.then(Commands.literal(job.id).executes(context -> setJob(context.getSource(), getPlayer(context, "joueur"), job)));
        }
        target.then(Commands.literal("none").executes(context -> setJob(context.getSource(), getPlayer(context, "joueur"), null)));
        return root.then(target);
    }

    private static int setJob(CommandSourceStack source, ServerPlayer target, nolan.bloxpiece.job.Job job) { // job null = aucun
        nolan.bloxpiece.job.Jobs.set(target, job);
        Component name = job == null ? Component.translatable("message.bloxpiece.job.none") : Component.translatable(job.nameKey());
        target.sendSystemMessage(Component.translatable("message.bloxpiece.job.set_notify", name));
        source.sendSuccess(() -> Component.translatable("message.bloxpiece.job.set_success", target.getDisplayName(), name), true);
        return 1;
    }

    /** Nom de chaque choix de la commande : les trois Hakis, puis « all » (les trois à la fois). */
    private static final String[][] HAKI_NAMES = {{"armament"}, {"observation"}, {"conqueror"}, {"all"}};

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> hakiTypes(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root, boolean unlock) {
        com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> target = Commands.argument("joueur", player());
        for (int i = 0; i < HAKI_NAMES.length; i++) {
            nolan.bloxpiece.haki.HakiType type = i < 3 ? nolan.bloxpiece.haki.HakiType.values()[i] : null;
            for (String name : HAKI_NAMES[i]) {
                target.then(Commands.literal(name).executes(context -> setHaki(context.getSource(), getPlayer(context, "joueur"),
                        type, unlock)));
            }
        }
        return root.then(target);
    }

    private static int setHaki(CommandSourceStack source, ServerPlayer target, nolan.bloxpiece.haki.HakiType type, boolean unlock) {
        int changed = 0;
        for (nolan.bloxpiece.haki.HakiType t : nolan.bloxpiece.haki.HakiType.values()) {
            if ((type == null || t == type) && nolan.bloxpiece.haki.HakiManager.setUnlocked(target, t, unlock)) {
                changed++;
                if (unlock) {
                    target.sendSystemMessage(Component.translatable("message.bloxpiece.haki.unlocked", Component.translatable(t.nameKey())));
                }
            }
        }
        Component what = type == null ? Component.translatable("message.bloxpiece.haki.all") : Component.translatable(type.nameKey());
        if (changed == 0) {
            source.sendFailure(Component.translatable(unlock ? "message.bloxpiece.haki.already_unlocked"
                    : "message.bloxpiece.haki.already_locked", target.getDisplayName(), what));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(unlock ? "message.bloxpiece.haki.unlock_success"
                : "message.bloxpiece.haki.lock_success", what, target.getDisplayName()), true);
        return changed;
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
