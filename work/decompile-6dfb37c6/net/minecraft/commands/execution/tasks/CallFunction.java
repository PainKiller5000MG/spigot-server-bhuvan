package net.minecraft.commands.execution.tasks;

import java.util.List;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.commands.functions.InstantiatedFunction;

public class CallFunction<T extends ExecutionCommandSource<T>> implements UnboundEntryAction<T> {

    private final InstantiatedFunction<T> function;
    private final CommandResultCallback resultCallback;
    private final boolean returnParentFrame;

    public CallFunction(InstantiatedFunction<T> function, CommandResultCallback resultCallback, boolean returnParentFrame) {
        this.function = function;
        this.resultCallback = resultCallback;
        this.returnParentFrame = returnParentFrame;
    }

    public void execute(T sender, ExecutionContext<T> context, Frame frame) {
        context.incrementCost();
        List<UnboundEntryAction<T>> list = this.function.entries();
        TraceCallbacks tracecallbacks = context.tracer();

        if (tracecallbacks != null) {
            tracecallbacks.onCall(frame.depth(), this.function.id(), this.function.entries().size());
        }

        int i = frame.depth() + 1;
        Frame.FrameControl frame_framecontrol = this.returnParentFrame ? frame.frameControl() : context.frameControlForDepth(i);
        Frame frame1 = new Frame(i, this.resultCallback, frame_framecontrol);

        ContinuationTask.schedule(context, frame1, list, (frame2, unboundentryaction) -> {
            return new CommandQueueEntry(frame2, unboundentryaction.bind(sender));
        });
    }
}
