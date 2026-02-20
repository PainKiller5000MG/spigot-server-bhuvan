package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class FunctionArgument implements ArgumentType<FunctionArgument.Result> {

    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "#foo");
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("arguments.function.tag.unknown", object);
    });
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_FUNCTION = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("arguments.function.unknown", object);
    });

    public FunctionArgument() {}

    public static FunctionArgument functions() {
        return new FunctionArgument();
    }

    public FunctionArgument.Result parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '#') {
            reader.skip();
            final Identifier identifier = Identifier.read(reader);

            return new FunctionArgument.Result() {
                @Override
                public Collection<CommandFunction<CommandSourceStack>> create(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
                    return FunctionArgument.getFunctionTag(c, identifier);
                }

                @Override
                public Pair<Identifier, Either<CommandFunction<CommandSourceStack>, Collection<CommandFunction<CommandSourceStack>>>> unwrap(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                    return Pair.of(identifier, Either.right(FunctionArgument.getFunctionTag(context, identifier)));
                }

                @Override
                public Pair<Identifier, Collection<CommandFunction<CommandSourceStack>>> unwrapToCollection(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                    return Pair.of(identifier, FunctionArgument.getFunctionTag(context, identifier));
                }
            };
        } else {
            final Identifier identifier1 = Identifier.read(reader);

            return new FunctionArgument.Result() {
                @Override
                public Collection<CommandFunction<CommandSourceStack>> create(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
                    return Collections.singleton(FunctionArgument.getFunction(c, identifier1));
                }

                @Override
                public Pair<Identifier, Either<CommandFunction<CommandSourceStack>, Collection<CommandFunction<CommandSourceStack>>>> unwrap(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                    return Pair.of(identifier1, Either.left(FunctionArgument.getFunction(context, identifier1)));
                }

                @Override
                public Pair<Identifier, Collection<CommandFunction<CommandSourceStack>>> unwrapToCollection(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                    return Pair.of(identifier1, Collections.singleton(FunctionArgument.getFunction(context, identifier1)));
                }
            };
        }
    }

    private static CommandFunction<CommandSourceStack> getFunction(CommandContext<CommandSourceStack> c, Identifier id) throws CommandSyntaxException {
        return (CommandFunction) ((CommandSourceStack) c.getSource()).getServer().getFunctions().get(id).orElseThrow(() -> {
            return FunctionArgument.ERROR_UNKNOWN_FUNCTION.create(id.toString());
        });
    }

    private static Collection<CommandFunction<CommandSourceStack>> getFunctionTag(CommandContext<CommandSourceStack> c, Identifier id) throws CommandSyntaxException {
        Collection<CommandFunction<CommandSourceStack>> collection = ((CommandSourceStack) c.getSource()).getServer().getFunctions().getTag(id);

        if (collection == null) {
            throw FunctionArgument.ERROR_UNKNOWN_TAG.create(id.toString());
        } else {
            return collection;
        }
    }

    public static Collection<CommandFunction<CommandSourceStack>> getFunctions(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return ((FunctionArgument.Result) context.getArgument(name, FunctionArgument.Result.class)).create(context);
    }

    public static Pair<Identifier, Either<CommandFunction<CommandSourceStack>, Collection<CommandFunction<CommandSourceStack>>>> getFunctionOrTag(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return ((FunctionArgument.Result) context.getArgument(name, FunctionArgument.Result.class)).unwrap(context);
    }

    public static Pair<Identifier, Collection<CommandFunction<CommandSourceStack>>> getFunctionCollection(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return ((FunctionArgument.Result) context.getArgument(name, FunctionArgument.Result.class)).unwrapToCollection(context);
    }

    public Collection<String> getExamples() {
        return FunctionArgument.EXAMPLES;
    }

    public interface Result {

        Collection<CommandFunction<CommandSourceStack>> create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;

        Pair<Identifier, Either<CommandFunction<CommandSourceStack>, Collection<CommandFunction<CommandSourceStack>>>> unwrap(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;

        Pair<Identifier, Collection<CommandFunction<CommandSourceStack>>> unwrapToCollection(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }
}
