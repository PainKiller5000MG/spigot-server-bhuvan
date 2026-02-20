package net.minecraft.network.chat;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.SignedArgument;
import org.jspecify.annotations.Nullable;

public record SignableCommand<S>(List<SignableCommand.Argument<S>> arguments) {

    public static <S> boolean hasSignableArguments(ParseResults<S> command) {
        return !of(command).arguments().isEmpty();
    }

    public static <S> SignableCommand<S> of(ParseResults<S> command) {
        String s = command.getReader().getString();
        CommandContextBuilder<S> commandcontextbuilder = command.getContext();
        CommandContextBuilder<S> commandcontextbuilder1 = commandcontextbuilder;

        List<SignableCommand.Argument<S>> list;
        CommandContextBuilder<S> commandcontextbuilder2;

        for (list = collectArguments(s, commandcontextbuilder); (commandcontextbuilder2 = commandcontextbuilder1.getChild()) != null && commandcontextbuilder2.getRootNode() != commandcontextbuilder.getRootNode(); commandcontextbuilder1 = commandcontextbuilder2) {
            list.addAll(collectArguments(s, commandcontextbuilder2));
        }

        return new SignableCommand<S>(list);
    }

    private static <S> List<SignableCommand.Argument<S>> collectArguments(String commandString, CommandContextBuilder<S> context) {
        List<SignableCommand.Argument<S>> list = new ArrayList();

        for (ParsedCommandNode<S> parsedcommandnode : context.getNodes()) {
            CommandNode commandnode = parsedcommandnode.getNode();

            if (commandnode instanceof ArgumentCommandNode<S, ?> argumentcommandnode) {
                if (argumentcommandnode.getType() instanceof SignedArgument) {
                    ParsedArgument<S, ?> parsedargument = (ParsedArgument) context.getArguments().get(argumentcommandnode.getName());

                    if (parsedargument != null) {
                        String s1 = parsedargument.getRange().get(commandString);

                        list.add(new SignableCommand.Argument(argumentcommandnode, s1));
                    }
                }
            }
        }

        return list;
    }

    public SignableCommand.@Nullable Argument<S> getArgument(String name) {
        for (SignableCommand.Argument<S> signablecommand_argument : this.arguments) {
            if (name.equals(signablecommand_argument.name())) {
                return signablecommand_argument;
            }
        }

        return null;
    }

    public static record Argument<S>(ArgumentCommandNode<S, ?> node, String value) {

        public String name() {
            return this.node.getName();
        }
    }
}
