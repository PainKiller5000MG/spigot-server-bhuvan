package net.minecraft.server.commands;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.datafixers.util.Pair;
import java.util.Collection;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.execution.tasks.FallthroughTask;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import org.jspecify.annotations.Nullable;

public class FunctionCommand {

    private static final DynamicCommandExceptionType ERROR_ARGUMENT_NOT_COMPOUND = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.function.error.argument_not_compound", object);
    });
    private static final DynamicCommandExceptionType ERROR_NO_FUNCTIONS = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.function.scheduled.no_functions", object);
    });
    @VisibleForTesting
    public static final Dynamic2CommandExceptionType ERROR_FUNCTION_INSTANTATION_FAILURE = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("commands.function.instantiationFailure", object, object1);
    });
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_FUNCTION = (commandcontext, suggestionsbuilder) -> {
        ServerFunctionManager serverfunctionmanager = ((CommandSourceStack) commandcontext.getSource()).getServer().getFunctions();

        SharedSuggestionProvider.suggestResource(serverfunctionmanager.getTagNames(), suggestionsbuilder, "#");
        return SharedSuggestionProvider.suggestResource(serverfunctionmanager.getFunctionNames(), suggestionsbuilder);
    };
    private static final FunctionCommand.Callbacks<CommandSourceStack> FULL_CONTEXT_CALLBACKS = new FunctionCommand.Callbacks<CommandSourceStack>() {
        public void signalResult(CommandSourceStack originalSource, Identifier id, int newValue) {
            originalSource.sendSuccess(() -> {
                return Component.translatable("commands.function.result", Component.translationArg(id), newValue);
            }, true);
        }
    };

    public FunctionCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = Commands.literal("with");

        for (DataCommands.DataProvider datacommands_dataprovider : DataCommands.SOURCE_PROVIDERS) {
            datacommands_dataprovider.wrap(literalargumentbuilder, (argumentbuilder) -> {
                return argumentbuilder.executes(new FunctionCommand.FunctionCustomExecutor() {
                    @Override
                    protected CompoundTag arguments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                        return datacommands_dataprovider.access(context).getData();
                    }
                }).then(Commands.argument("path", NbtPathArgument.nbtPath()).executes(new FunctionCommand.FunctionCustomExecutor() {
                    @Override
                    protected CompoundTag arguments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                        return FunctionCommand.getArgumentTag(NbtPathArgument.getPath(context, "path"), datacommands_dataprovider.access(context));
                    }
                }));
            });
        }

        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("function").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("name", FunctionArgument.functions()).suggests(FunctionCommand.SUGGEST_FUNCTION).executes(new FunctionCommand.FunctionCustomExecutor() {
            @Override
            protected @Nullable CompoundTag arguments(CommandContext<CommandSourceStack> context) {
                return null;
            }
        })).then(Commands.argument("arguments", CompoundTagArgument.compoundTag()).executes(new FunctionCommand.FunctionCustomExecutor() {
            @Override
            protected CompoundTag arguments(CommandContext<CommandSourceStack> context) {
                return CompoundTagArgument.getCompoundTag(context, "arguments");
            }
        }))).then(literalargumentbuilder)));
    }

    private static CompoundTag getArgumentTag(NbtPathArgument.NbtPath path, DataAccessor accessor) throws CommandSyntaxException {
        Tag tag = DataCommands.getSingleTag(path, accessor);

        if (tag instanceof CompoundTag compoundtag) {
            return compoundtag;
        } else {
            throw FunctionCommand.ERROR_ARGUMENT_NOT_COMPOUND.create(tag.getType().getName());
        }
    }

    public static CommandSourceStack modifySenderForExecution(CommandSourceStack sender) {
        return sender.withSuppressedOutput().withMaximumPermission(LevelBasedPermissionSet.GAMEMASTER);
    }

    public static <T extends ExecutionCommandSource<T>> void queueFunctions(Collection<CommandFunction<T>> functions, @Nullable CompoundTag arguments, T originalSource, T functionSource, ExecutionControl<T> output, FunctionCommand.Callbacks<T> callbacks, ChainModifiers modifiers) throws CommandSyntaxException {
        if (modifiers.isReturn()) {
            queueFunctionsAsReturn(functions, arguments, originalSource, functionSource, output, callbacks);
        } else {
            queueFunctionsNoReturn(functions, arguments, originalSource, functionSource, output, callbacks);
        }

    }

    private static <T extends ExecutionCommandSource<T>> void instantiateAndQueueFunctions(@Nullable CompoundTag arguments, ExecutionControl<T> output, CommandDispatcher<T> dispatcher, T noCallbackSource, CommandFunction<T> function, Identifier id, CommandResultCallback functionResultCollector, boolean returnParentFrame) throws CommandSyntaxException {
        try {
            InstantiatedFunction<T> instantiatedfunction = function.instantiate(arguments, dispatcher);

            output.queueNext((new CallFunction(instantiatedfunction, functionResultCollector, returnParentFrame)).bind(noCallbackSource));
        } catch (FunctionInstantiationException functioninstantiationexception) {
            throw FunctionCommand.ERROR_FUNCTION_INSTANTATION_FAILURE.create(id, functioninstantiationexception.messageComponent());
        }
    }

    private static <T extends ExecutionCommandSource<T>> CommandResultCallback decorateOutputIfNeeded(T originalSource, FunctionCommand.Callbacks<T> callbacks, Identifier id, CommandResultCallback callback) {
        return originalSource.isSilent() ? callback : (flag, i) -> {
            callbacks.signalResult(originalSource, id, i);
            callback.onResult(flag, i);
        };
    }

    private static <T extends ExecutionCommandSource<T>> void queueFunctionsAsReturn(Collection<CommandFunction<T>> functions, @Nullable CompoundTag arguments, T originalSource, T functionSource, ExecutionControl<T> output, FunctionCommand.Callbacks<T> callbacks) throws CommandSyntaxException {
        CommandDispatcher<T> commanddispatcher = originalSource.dispatcher();
        T t2 = functionSource.clearCallbacks();
        CommandResultCallback commandresultcallback = CommandResultCallback.chain(originalSource.callback(), output.currentFrame().returnValueConsumer());

        for (CommandFunction<T> commandfunction : functions) {
            Identifier identifier = commandfunction.id();
            CommandResultCallback commandresultcallback1 = decorateOutputIfNeeded(originalSource, callbacks, identifier, commandresultcallback);

            instantiateAndQueueFunctions(arguments, output, commanddispatcher, t2, commandfunction, identifier, commandresultcallback1, true);
        }

        output.queueNext(FallthroughTask.instance());
    }

    private static <T extends ExecutionCommandSource<T>> void queueFunctionsNoReturn(Collection<CommandFunction<T>> functions, @Nullable CompoundTag arguments, T originalSource, T functionSource, ExecutionControl<T> output, FunctionCommand.Callbacks<T> callbacks) throws CommandSyntaxException {
        CommandDispatcher<T> commanddispatcher = originalSource.dispatcher();
        T t2 = functionSource.clearCallbacks();
        CommandResultCallback commandresultcallback = originalSource.callback();

        if (!functions.isEmpty()) {
            if (functions.size() == 1) {
                CommandFunction<T> commandfunction = (CommandFunction)functions.iterator().next();
                Identifier identifier = commandfunction.id();
                CommandResultCallback commandresultcallback1 = decorateOutputIfNeeded(originalSource, callbacks, identifier, commandresultcallback);

                instantiateAndQueueFunctions(arguments, output, commanddispatcher, t2, commandfunction, identifier, commandresultcallback1, false);
            } else if (commandresultcallback == CommandResultCallback.EMPTY) {
                for(CommandFunction<T> commandfunction1 : functions) {
                    Identifier identifier1 = commandfunction1.id();
                    CommandResultCallback commandresultcallback2 = decorateOutputIfNeeded(originalSource, callbacks, identifier1, commandresultcallback);

                    instantiateAndQueueFunctions(arguments, output, commanddispatcher, t2, commandfunction1, identifier1, commandresultcallback2, false);
                }
            } else {
                class 1Accumulator {

                    private boolean anyResult;
                    private int sum;

                    _Accumulator/* $FF was: 1Accumulator*/() {
}

                    public void add(int result) {
                        this.anyResult = true;
                        this.sum += result;
                    }
                }

                1Accumulator 1accumulator = new 1Accumulator();
                CommandResultCallback commandresultcallback3 = (flag, i) -> {
                    1accumulator.add(i);
                };

                for(CommandFunction<T> commandfunction2 : functions) {
                    Identifier identifier2 = commandfunction2.id();
                    CommandResultCallback commandresultcallback4 = decorateOutputIfNeeded(originalSource, callbacks, identifier2, commandresultcallback3);

                    instantiateAndQueueFunctions(arguments, output, commanddispatcher, t2, commandfunction2, identifier2, commandresultcallback4, false);
                }

                output.queueNext((executioncontext, frame) -> {
                    if (1accumulator.anyResult) {
                        commandresultcallback.onSuccess(1accumulator.sum);
                    }

                });
            }

        }
    }

    private abstract static class FunctionCustomExecutor extends CustomCommandExecutor.WithErrorHandling<CommandSourceStack> implements CustomCommandExecutor.CommandAdapter<CommandSourceStack> {

        private FunctionCustomExecutor() {}

        protected abstract @Nullable CompoundTag arguments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;

        public void runGuarded(CommandSourceStack sender, ContextChain<CommandSourceStack> currentStep, ChainModifiers modifiers, ExecutionControl<CommandSourceStack> output) throws CommandSyntaxException {
            CommandContext<CommandSourceStack> commandcontext = currentStep.getTopContext().copyFor(sender);
            Pair<Identifier, Collection<CommandFunction<CommandSourceStack>>> pair = FunctionArgument.getFunctionCollection(commandcontext, "name");
            Collection<CommandFunction<CommandSourceStack>> collection = (Collection) pair.getSecond();

            if (collection.isEmpty()) {
                throw FunctionCommand.ERROR_NO_FUNCTIONS.create(Component.translationArg((Identifier) pair.getFirst()));
            } else {
                CompoundTag compoundtag = this.arguments(commandcontext);
                CommandSourceStack commandsourcestack1 = FunctionCommand.modifySenderForExecution(sender);

                if (collection.size() == 1) {
                    sender.sendSuccess(() -> {
                        return Component.translatable("commands.function.scheduled.single", Component.translationArg(((CommandFunction) collection.iterator().next()).id()));
                    }, true);
                } else {
                    sender.sendSuccess(() -> {
                        return Component.translatable("commands.function.scheduled.multiple", ComponentUtils.formatList(collection.stream().map(CommandFunction::id).toList(), Component::translationArg));
                    }, true);
                }

                FunctionCommand.queueFunctions(collection, compoundtag, sender, commandsourcestack1, output, FunctionCommand.FULL_CONTEXT_CALLBACKS, modifiers);
            }
        }
    }

    public interface Callbacks<T> {

        void signalResult(T originalSource, Identifier functionId, int newValue);
    }
}
