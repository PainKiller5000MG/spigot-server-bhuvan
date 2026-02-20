package net.minecraft.commands.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.context.ContextChain.Stage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.EntryAction;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.network.chat.Component;

public class BuildContexts<T extends ExecutionCommandSource<T>> {

    @VisibleForTesting
    public static final DynamicCommandExceptionType ERROR_FORK_LIMIT_REACHED = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("command.forkLimit", object);
    });
    private final String commandInput;
    private final ContextChain<T> command;

    public BuildContexts(String commandInput, ContextChain<T> command) {
        this.commandInput = commandInput;
        this.command = command;
    }

    protected void execute(T originalSource, List<T> initialSources, ExecutionContext<T> context, Frame frame, ChainModifiers initialModifiers) {
        ContextChain<T> contextchain = this.command;
        ChainModifiers chainmodifiers1 = initialModifiers;
        List<T> list1 = initialSources;

        if (contextchain.getStage() != Stage.EXECUTE) {
            context.profiler().push(() -> {
                return "prepare " + this.commandInput;
            });

            try {
                for (int i = context.forkLimit(); contextchain.getStage() != Stage.EXECUTE; contextchain = contextchain.nextStage()) {
                    CommandContext<T> commandcontext = contextchain.getTopContext();

                    if (commandcontext.isForked()) {
                        chainmodifiers1 = chainmodifiers1.setForked();
                    }

                    RedirectModifier<T> redirectmodifier = commandcontext.getRedirectModifier();

                    if (redirectmodifier instanceof CustomModifierExecutor) {
                        CustomModifierExecutor<T> custommodifierexecutor = (CustomModifierExecutor) redirectmodifier;

                        custommodifierexecutor.apply(originalSource, list1, contextchain, chainmodifiers1, ExecutionControl.create(context, frame));
                        return;
                    }

                    if (redirectmodifier != null) {
                        context.incrementCost();
                        boolean flag = chainmodifiers1.isForked();
                        List<T> list2 = new ObjectArrayList();

                        for (T t1 : list1) {
                            try {
                                Collection<T> collection = ContextChain.runModifier(commandcontext, t1, (commandcontext1, flag1, j) -> {
                                }, flag);

                                if (list2.size() + collection.size() >= i) {
                                    originalSource.handleError(BuildContexts.ERROR_FORK_LIMIT_REACHED.create(i), flag, context.tracer());
                                    return;
                                }

                                list2.addAll(collection);
                            } catch (CommandSyntaxException commandsyntaxexception) {
                                t1.handleError(commandsyntaxexception, flag, context.tracer());
                                if (!flag) {
                                    return;
                                }
                            }
                        }

                        list1 = list2;
                    }
                }
            } finally {
                context.profiler().pop();
            }
        }

        if (list1.isEmpty()) {
            if (chainmodifiers1.isReturn()) {
                context.queueNext(new CommandQueueEntry(frame, FallthroughTask.instance()));
            }

        } else {
            CommandContext<T> commandcontext1 = contextchain.getTopContext();
            Command<T> command = commandcontext1.getCommand();

            if (command instanceof CustomCommandExecutor) {
                CustomCommandExecutor<T> customcommandexecutor = (CustomCommandExecutor) command;
                ExecutionControl<T> executioncontrol = ExecutionControl.<T>create(context, frame);

                for (T t2 : list1) {
                    customcommandexecutor.run(t2, contextchain, chainmodifiers1, executioncontrol);
                }
            } else {
                if (chainmodifiers1.isReturn()) {
                    T t3 = (T) (list1.get(0));

                    t3 = t3.withCallback(CommandResultCallback.chain(t3.callback(), frame.returnValueConsumer()));
                    list1 = List.of(t3);
                }

                ExecuteCommand<T> executecommand = new ExecuteCommand<T>(this.commandInput, chainmodifiers1, commandcontext1);

                ContinuationTask.schedule(context, frame, list1, (frame1, executioncommandsource) -> {
                    return new CommandQueueEntry(frame1, executecommand.bind(executioncommandsource));
                });
            }

        }
    }

    protected void traceCommandStart(ExecutionContext<T> context, Frame frame) {
        TraceCallbacks tracecallbacks = context.tracer();

        if (tracecallbacks != null) {
            tracecallbacks.onCommand(frame.depth(), this.commandInput);
        }

    }

    public String toString() {
        return this.commandInput;
    }

    public static class Unbound<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements UnboundEntryAction<T> {

        public Unbound(String commandInput, ContextChain<T> command) {
            super(commandInput, command);
        }

        public void execute(T sender, ExecutionContext<T> context, Frame frame) {
            this.traceCommandStart(context, frame);
            this.execute(sender, List.of(sender), context, frame, ChainModifiers.DEFAULT);
        }
    }

    public static class Continuation<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements EntryAction<T> {

        private final ChainModifiers modifiers;
        private final T originalSource;
        private final List<T> sources;

        public Continuation(String commandInput, ContextChain<T> command, ChainModifiers modifiers, T originalSource, List<T> sources) {
            super(commandInput, command);
            this.originalSource = originalSource;
            this.sources = sources;
            this.modifiers = modifiers;
        }

        @Override
        public void execute(ExecutionContext<T> context, Frame frame) {
            this.execute(this.originalSource, this.sources, context, frame, this.modifiers);
        }
    }

    public static class TopLevel<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements EntryAction<T> {

        private final T source;

        public TopLevel(String commandInput, ContextChain<T> command, T source) {
            super(commandInput, command);
            this.source = source;
        }

        @Override
        public void execute(ExecutionContext<T> context, Frame frame) {
            this.traceCommandStart(context, frame);
            this.execute(this.source, List.of(this.source), context, frame, ChainModifiers.DEFAULT);
        }
    }
}
