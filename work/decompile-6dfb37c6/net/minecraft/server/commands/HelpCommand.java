package net.minecraft.server.commands;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class HelpCommand {

    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.help.failed"));

    public HelpCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("help").executes((commandcontext) -> {
            Map<CommandNode<CommandSourceStack>, String> map = dispatcher.getSmartUsage(dispatcher.getRoot(), (CommandSourceStack) commandcontext.getSource());

            for (String s : map.values()) {
                ((CommandSourceStack) commandcontext.getSource()).sendSuccess(() -> {
                    return Component.literal("/" + s);
                }, false);
            }

            return map.size();
        })).then(Commands.argument("command", StringArgumentType.greedyString()).executes((commandcontext) -> {
            ParseResults<CommandSourceStack> parseresults = dispatcher.parse(StringArgumentType.getString(commandcontext, "command"), (CommandSourceStack) commandcontext.getSource());

            if (parseresults.getContext().getNodes().isEmpty()) {
                throw HelpCommand.ERROR_FAILED.create();
            } else {
                Map<CommandNode<CommandSourceStack>, String> map = dispatcher.getSmartUsage(((ParsedCommandNode) Iterables.getLast(parseresults.getContext().getNodes())).getNode(), (CommandSourceStack) commandcontext.getSource());

                for (String s : map.values()) {
                    ((CommandSourceStack) commandcontext.getSource()).sendSuccess(() -> {
                        String s1 = parseresults.getReader().getString();

                        return Component.literal("/" + s1 + " " + s);
                    }, false);
                }

                return map.size();
            }
        })));
    }
}
