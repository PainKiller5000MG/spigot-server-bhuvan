package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class TriggerCommand {

    private static final SimpleCommandExceptionType ERROR_NOT_PRIMED = new SimpleCommandExceptionType(Component.translatable("commands.trigger.failed.unprimed"));
    private static final SimpleCommandExceptionType ERROR_INVALID_OBJECTIVE = new SimpleCommandExceptionType(Component.translatable("commands.trigger.failed.invalid"));

    public TriggerCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) Commands.literal("trigger").then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("objective", ObjectiveArgument.objective()).suggests((commandcontext, suggestionsbuilder) -> {
            return suggestObjectives((CommandSourceStack) commandcontext.getSource(), suggestionsbuilder);
        }).executes((commandcontext) -> {
            return simpleTrigger((CommandSourceStack) commandcontext.getSource(), ((CommandSourceStack) commandcontext.getSource()).getPlayerOrException(), ObjectiveArgument.getObjective(commandcontext, "objective"));
        })).then(Commands.literal("add").then(Commands.argument("value", IntegerArgumentType.integer()).executes((commandcontext) -> {
            return addValue((CommandSourceStack) commandcontext.getSource(), ((CommandSourceStack) commandcontext.getSource()).getPlayerOrException(), ObjectiveArgument.getObjective(commandcontext, "objective"), IntegerArgumentType.getInteger(commandcontext, "value"));
        })))).then(Commands.literal("set").then(Commands.argument("value", IntegerArgumentType.integer()).executes((commandcontext) -> {
            return setValue((CommandSourceStack) commandcontext.getSource(), ((CommandSourceStack) commandcontext.getSource()).getPlayerOrException(), ObjectiveArgument.getObjective(commandcontext, "objective"), IntegerArgumentType.getInteger(commandcontext, "value"));
        })))));
    }

    public static CompletableFuture<Suggestions> suggestObjectives(CommandSourceStack source, SuggestionsBuilder builder) {
        ScoreHolder scoreholder = source.getEntity();
        List<String> list = Lists.newArrayList();

        if (scoreholder != null) {
            Scoreboard scoreboard = source.getServer().getScoreboard();

            for (Objective objective : scoreboard.getObjectives()) {
                if (objective.getCriteria() == ObjectiveCriteria.TRIGGER) {
                    ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(scoreholder, objective);

                    if (readonlyscoreinfo != null && !readonlyscoreinfo.isLocked()) {
                        list.add(objective.getName());
                    }
                }
            }
        }

        return SharedSuggestionProvider.suggest(list, builder);
    }

    private static int addValue(CommandSourceStack source, ServerPlayer player, Objective objective, int amount) throws CommandSyntaxException {
        ScoreAccess scoreaccess = getScore(source.getServer().getScoreboard(), player, objective);
        int j = scoreaccess.add(amount);

        source.sendSuccess(() -> {
            return Component.translatable("commands.trigger.add.success", objective.getFormattedDisplayName(), amount);
        }, true);
        return j;
    }

    private static int setValue(CommandSourceStack source, ServerPlayer player, Objective objective, int amount) throws CommandSyntaxException {
        ScoreAccess scoreaccess = getScore(source.getServer().getScoreboard(), player, objective);

        scoreaccess.set(amount);
        source.sendSuccess(() -> {
            return Component.translatable("commands.trigger.set.success", objective.getFormattedDisplayName(), amount);
        }, true);
        return amount;
    }

    private static int simpleTrigger(CommandSourceStack source, ServerPlayer player, Objective objective) throws CommandSyntaxException {
        ScoreAccess scoreaccess = getScore(source.getServer().getScoreboard(), player, objective);
        int i = scoreaccess.add(1);

        source.sendSuccess(() -> {
            return Component.translatable("commands.trigger.simple.success", objective.getFormattedDisplayName());
        }, true);
        return i;
    }

    private static ScoreAccess getScore(Scoreboard scoreboard, ScoreHolder scoreHolder, Objective objective) throws CommandSyntaxException {
        if (objective.getCriteria() != ObjectiveCriteria.TRIGGER) {
            throw TriggerCommand.ERROR_INVALID_OBJECTIVE.create();
        } else {
            ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective);

            if (readonlyscoreinfo != null && !readonlyscoreinfo.isLocked()) {
                ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);

                scoreaccess.lock();
                return scoreaccess;
            } else {
                throw TriggerCommand.ERROR_NOT_PRIMED.create();
            }
        }
    }
}
