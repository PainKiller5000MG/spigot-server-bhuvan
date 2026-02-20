package net.minecraft.commands.execution.tasks;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.commands.execution.UnboundEntryAction;

public class ExecuteCommand<T extends ExecutionCommandSource<T>> implements UnboundEntryAction<T> {

    private final String commandInput;
    private final ChainModifiers modifiers;
    private final CommandContext<T> executionContext;

    public ExecuteCommand(String commandInput, ChainModifiers modifiers, CommandContext<T> executionContext) {
        this.commandInput = commandInput;
        this.modifiers = modifiers;
        this.executionContext = executionContext;
    }

    public void execute(T sender, ExecutionContext<T> context, Frame frame) {
        context.profiler().push(() -> {
            return "execute " + this.commandInput;
        });

        try {
            context.incrementCost();
            int i = ContextChain.runExecutable(this.executionContext, sender, ExecutionCommandSource.resultConsumer(), this.modifiers.isForked());
            TraceCallbacks tracecallbacks = context.tracer();

            if (tracecallbacks != null) {
                tracecallbacks.onReturn(frame.depth(), this.commandInput, i);
            }
        } catch (CommandSyntaxException commandsyntaxexception) {
            sender.handleError(commandsyntaxexception, this.modifiers.isForked(), context.tracer());
        } finally {
            context.profiler().pop();
        }

    }
}
