package net.minecraft.commands.execution;

import com.google.common.collect.Queues;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ExecutionContext<T> implements AutoCloseable {

    private static final int MAX_QUEUE_DEPTH = 10000000;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final int commandLimit;
    private final int forkLimit;
    private final ProfilerFiller profiler;
    private @Nullable TraceCallbacks tracer;
    private int commandQuota;
    private boolean queueOverflow;
    private final Deque<CommandQueueEntry<T>> commandQueue = Queues.newArrayDeque();
    private final List<CommandQueueEntry<T>> newTopCommands = new ObjectArrayList();
    private int currentFrameDepth;

    public ExecutionContext(int commandLimit, int forkLimit, ProfilerFiller profiler) {
        this.commandLimit = commandLimit;
        this.forkLimit = forkLimit;
        this.profiler = profiler;
        this.commandQuota = commandLimit;
    }

    private static <T extends ExecutionCommandSource<T>> Frame createTopFrame(ExecutionContext<T> context, CommandResultCallback frameResult) {
        if (context.currentFrameDepth == 0) {
            Deque deque = context.commandQueue;

            Objects.requireNonNull(context.commandQueue);
            return new Frame(0, frameResult, deque::clear);
        } else {
            int i = context.currentFrameDepth + 1;

            return new Frame(i, frameResult, context.frameControlForDepth(i));
        }
    }

    public static <T extends ExecutionCommandSource<T>> void queueInitialFunctionCall(ExecutionContext<T> context, InstantiatedFunction<T> function, T sender, CommandResultCallback functionReturn) {
        context.queueNext(new CommandQueueEntry(createTopFrame(context, functionReturn), (new CallFunction(function, sender.callback(), false)).bind(sender)));
    }

    public static <T extends ExecutionCommandSource<T>> void queueInitialCommandExecution(ExecutionContext<T> context, String command, ContextChain<T> executionChain, T sender, CommandResultCallback commandReturn) {
        context.queueNext(new CommandQueueEntry(createTopFrame(context, commandReturn), new BuildContexts.TopLevel(command, executionChain, sender)));
    }

    private void handleQueueOverflow() {
        this.queueOverflow = true;
        this.newTopCommands.clear();
        this.commandQueue.clear();
    }

    public void queueNext(CommandQueueEntry<T> entry) {
        if (this.newTopCommands.size() + this.commandQueue.size() > 10000000) {
            this.handleQueueOverflow();
        }

        if (!this.queueOverflow) {
            this.newTopCommands.add(entry);
        }

    }

    public void discardAtDepthOrHigher(int depthToDiscard) {
        while (!this.commandQueue.isEmpty() && ((CommandQueueEntry) this.commandQueue.peek()).frame().depth() >= depthToDiscard) {
            this.commandQueue.removeFirst();
        }

    }

    public Frame.FrameControl frameControlForDepth(int depthToDiscard) {
        return () -> {
            this.discardAtDepthOrHigher(depthToDiscard);
        };
    }

    public void runCommandQueue() {
        this.pushNewCommands();

        while (true) {
            if (this.commandQuota <= 0) {
                ExecutionContext.LOGGER.info("Command execution stopped due to limit (executed {} commands)", this.commandLimit);
                break;
            }

            CommandQueueEntry<T> commandqueueentry = (CommandQueueEntry) this.commandQueue.pollFirst();

            if (commandqueueentry == null) {
                return;
            }

            this.currentFrameDepth = commandqueueentry.frame().depth();
            commandqueueentry.execute(this);
            if (this.queueOverflow) {
                ExecutionContext.LOGGER.error("Command execution stopped due to command queue overflow (max {})", 10000000);
                break;
            }

            this.pushNewCommands();
        }

        this.currentFrameDepth = 0;
    }

    private void pushNewCommands() {
        for (int i = this.newTopCommands.size() - 1; i >= 0; --i) {
            this.commandQueue.addFirst((CommandQueueEntry) this.newTopCommands.get(i));
        }

        this.newTopCommands.clear();
    }

    public void tracer(@Nullable TraceCallbacks tracer) {
        this.tracer = tracer;
    }

    public @Nullable TraceCallbacks tracer() {
        return this.tracer;
    }

    public ProfilerFiller profiler() {
        return this.profiler;
    }

    public int forkLimit() {
        return this.forkLimit;
    }

    public void incrementCost() {
        --this.commandQuota;
    }

    public void close() {
        if (this.tracer != null) {
            this.tracer.close();
        }

    }
}
