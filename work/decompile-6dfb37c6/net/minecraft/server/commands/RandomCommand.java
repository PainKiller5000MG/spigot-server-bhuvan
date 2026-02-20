package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequences;
import org.jspecify.annotations.Nullable;

public class RandomCommand {

    private static final SimpleCommandExceptionType ERROR_RANGE_TOO_LARGE = new SimpleCommandExceptionType(Component.translatable("commands.random.error.range_too_large"));
    private static final SimpleCommandExceptionType ERROR_RANGE_TOO_SMALL = new SimpleCommandExceptionType(Component.translatable("commands.random.error.range_too_small"));

    public RandomCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("random").then(drawRandomValueTree("value", false))).then(drawRandomValueTree("roll", true))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(((LiteralArgumentBuilder) Commands.literal("*").executes((commandcontext) -> {
            return resetAllSequences((CommandSourceStack) commandcontext.getSource());
        })).then(((RequiredArgumentBuilder) Commands.argument("seed", IntegerArgumentType.integer()).executes((commandcontext) -> {
            return resetAllSequencesAndSetNewDefaults((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "seed"), true, true);
        })).then(((RequiredArgumentBuilder) Commands.argument("includeWorldSeed", BoolArgumentType.bool()).executes((commandcontext) -> {
            return resetAllSequencesAndSetNewDefaults((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "seed"), BoolArgumentType.getBool(commandcontext, "includeWorldSeed"), true);
        })).then(Commands.argument("includeSequenceId", BoolArgumentType.bool()).executes((commandcontext) -> {
            return resetAllSequencesAndSetNewDefaults((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "seed"), BoolArgumentType.getBool(commandcontext, "includeWorldSeed"), BoolArgumentType.getBool(commandcontext, "includeSequenceId"));
        })))))).then(((RequiredArgumentBuilder) Commands.argument("sequence", IdentifierArgument.id()).suggests(RandomCommand::suggestRandomSequence).executes((commandcontext) -> {
            return resetSequence((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "sequence"));
        })).then(((RequiredArgumentBuilder) Commands.argument("seed", IntegerArgumentType.integer()).executes((commandcontext) -> {
            return resetSequence((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "sequence"), IntegerArgumentType.getInteger(commandcontext, "seed"), true, true);
        })).then(((RequiredArgumentBuilder) Commands.argument("includeWorldSeed", BoolArgumentType.bool()).executes((commandcontext) -> {
            return resetSequence((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "sequence"), IntegerArgumentType.getInteger(commandcontext, "seed"), BoolArgumentType.getBool(commandcontext, "includeWorldSeed"), true);
        })).then(Commands.argument("includeSequenceId", BoolArgumentType.bool()).executes((commandcontext) -> {
            return resetSequence((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "sequence"), IntegerArgumentType.getInteger(commandcontext, "seed"), BoolArgumentType.getBool(commandcontext, "includeWorldSeed"), BoolArgumentType.getBool(commandcontext, "includeSequenceId"));
        })))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> drawRandomValueTree(String name, boolean announce) {
        return (LiteralArgumentBuilder) Commands.literal(name).then(((RequiredArgumentBuilder) Commands.argument("range", RangeArgument.intRange()).executes((commandcontext) -> {
            return randomSample((CommandSourceStack) commandcontext.getSource(), RangeArgument.Ints.getRange(commandcontext, "range"), (Identifier) null, announce);
        })).then(((RequiredArgumentBuilder) Commands.argument("sequence", IdentifierArgument.id()).suggests(RandomCommand::suggestRandomSequence).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).executes((commandcontext) -> {
            return randomSample((CommandSourceStack) commandcontext.getSource(), RangeArgument.Ints.getRange(commandcontext, "range"), IdentifierArgument.getId(commandcontext, "sequence"), announce);
        })));
    }

    private static CompletableFuture<Suggestions> suggestRandomSequence(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> list = Lists.newArrayList();

        ((CommandSourceStack) context.getSource()).getLevel().getRandomSequences().forAllSequences((identifier, randomsequence) -> {
            list.add(identifier.toString());
        });
        return SharedSuggestionProvider.suggest(list, builder);
    }

    private static int randomSample(CommandSourceStack source, MinMaxBounds.Ints range, @Nullable Identifier sequence, boolean announce) throws CommandSyntaxException {
        RandomSource randomsource;

        if (sequence != null) {
            randomsource = source.getLevel().getRandomSequence(sequence);
        } else {
            randomsource = source.getLevel().getRandom();
        }

        int i = (Integer) range.min().orElse(Integer.MIN_VALUE);
        int j = (Integer) range.max().orElse(Integer.MAX_VALUE);
        long k = (long) j - (long) i;

        if (k == 0L) {
            throw RandomCommand.ERROR_RANGE_TOO_SMALL.create();
        } else if (k >= 2147483647L) {
            throw RandomCommand.ERROR_RANGE_TOO_LARGE.create();
        } else {
            int l = Mth.randomBetweenInclusive(randomsource, i, j);

            if (announce) {
                source.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("commands.random.roll", source.getDisplayName(), l, i, j), false);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.random.sample.success", l);
                }, false);
            }

            return l;
        }
    }

    private static int resetSequence(CommandSourceStack source, Identifier sequence) throws CommandSyntaxException {
        ServerLevel serverlevel = source.getLevel();

        serverlevel.getRandomSequences().reset(sequence, serverlevel.getSeed());
        source.sendSuccess(() -> {
            return Component.translatable("commands.random.reset.success", Component.translationArg(sequence));
        }, false);
        return 1;
    }

    private static int resetSequence(CommandSourceStack source, Identifier sequence, int salt, boolean includeWorldSeed, boolean includeSequenceId) throws CommandSyntaxException {
        ServerLevel serverlevel = source.getLevel();

        serverlevel.getRandomSequences().reset(sequence, serverlevel.getSeed(), salt, includeWorldSeed, includeSequenceId);
        source.sendSuccess(() -> {
            return Component.translatable("commands.random.reset.success", Component.translationArg(sequence));
        }, false);
        return 1;
    }

    private static int resetAllSequences(CommandSourceStack source) {
        int i = source.getLevel().getRandomSequences().clear();

        source.sendSuccess(() -> {
            return Component.translatable("commands.random.reset.all.success", i);
        }, false);
        return i;
    }

    private static int resetAllSequencesAndSetNewDefaults(CommandSourceStack source, int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        RandomSequences randomsequences = source.getLevel().getRandomSequences();

        randomsequences.setSeedDefaults(salt, includeWorldSeed, includeSequenceId);
        int j = randomsequences.clear();

        source.sendSuccess(() -> {
            return Component.translatable("commands.random.reset.all.success", j);
        }, false);
        return j;
    }
}
