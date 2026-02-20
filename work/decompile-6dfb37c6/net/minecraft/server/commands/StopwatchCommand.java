package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Stopwatch;
import net.minecraft.world.Stopwatches;

public class StopwatchCommand {

    private static final DynamicCommandExceptionType ERROR_ALREADY_EXISTS = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.stopwatch.already_exists", object);
    });
    public static final DynamicCommandExceptionType ERROR_DOES_NOT_EXIST = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.stopwatch.does_not_exist", object);
    });
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_STOPWATCHES = (commandcontext, suggestionsbuilder) -> {
        return SharedSuggestionProvider.suggestResource(((CommandSourceStack) commandcontext.getSource()).getServer().getStopwatches().ids(), suggestionsbuilder);
    };

    public StopwatchCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("stopwatch").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("create").then(Commands.argument("id", IdentifierArgument.id()).executes((commandcontext) -> {
            return createStopwatch((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "id"));
        })))).then(Commands.literal("query").then(((RequiredArgumentBuilder) Commands.argument("id", IdentifierArgument.id()).suggests(StopwatchCommand.SUGGEST_STOPWATCHES).then(Commands.argument("scale", DoubleArgumentType.doubleArg()).executes((commandcontext) -> {
            return queryStopwatch((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "id"), DoubleArgumentType.getDouble(commandcontext, "scale"));
        }))).executes((commandcontext) -> {
            return queryStopwatch((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "id"), 1.0D);
        })))).then(Commands.literal("restart").then(Commands.argument("id", IdentifierArgument.id()).suggests(StopwatchCommand.SUGGEST_STOPWATCHES).executes((commandcontext) -> {
            return restartStopwatch((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "id"));
        })))).then(Commands.literal("remove").then(Commands.argument("id", IdentifierArgument.id()).suggests(StopwatchCommand.SUGGEST_STOPWATCHES).executes((commandcontext) -> {
            return removeStopwatch((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "id"));
        }))));
    }

    private static int createStopwatch(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        MinecraftServer minecraftserver = source.getServer();
        Stopwatches stopwatches = minecraftserver.getStopwatches();
        Stopwatch stopwatch = new Stopwatch(Stopwatches.currentTime());

        if (!stopwatches.add(id, stopwatch)) {
            throw StopwatchCommand.ERROR_ALREADY_EXISTS.create(id);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.stopwatch.create.success", Component.translationArg(id));
            }, true);
            return 1;
        }
    }

    private static int queryStopwatch(CommandSourceStack source, Identifier id, double scale) throws CommandSyntaxException {
        MinecraftServer minecraftserver = source.getServer();
        Stopwatches stopwatches = minecraftserver.getStopwatches();
        Stopwatch stopwatch = stopwatches.get(id);

        if (stopwatch == null) {
            throw StopwatchCommand.ERROR_DOES_NOT_EXIST.create(id);
        } else {
            long i = Stopwatches.currentTime();
            double d1 = stopwatch.elapsedSeconds(i);

            source.sendSuccess(() -> {
                return Component.translatable("commands.stopwatch.query", Component.translationArg(id), d1);
            }, true);
            return (int) (d1 * scale);
        }
    }

    private static int restartStopwatch(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        MinecraftServer minecraftserver = source.getServer();
        Stopwatches stopwatches = minecraftserver.getStopwatches();

        if (!stopwatches.update(id, (stopwatch) -> {
            return new Stopwatch(Stopwatches.currentTime());
        })) {
            throw StopwatchCommand.ERROR_DOES_NOT_EXIST.create(id);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.stopwatch.restart.success", Component.translationArg(id));
            }, true);
            return 1;
        }
    }

    private static int removeStopwatch(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        MinecraftServer minecraftserver = source.getServer();
        Stopwatches stopwatches = minecraftserver.getStopwatches();

        if (!stopwatches.remove(id)) {
            throw StopwatchCommand.ERROR_DOES_NOT_EXIST.create(id);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.stopwatch.remove.success", Component.translationArg(id));
            }, true);
            return 1;
        }
    }
}
