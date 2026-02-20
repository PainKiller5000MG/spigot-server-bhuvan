package net.minecraft.commands.execution;

@FunctionalInterface
public interface UnboundEntryAction<T> {

    void execute(T sender, ExecutionContext<T> context, Frame frame);

    default EntryAction<T> bind(T sender) {
        return (executioncontext, frame) -> {
            this.execute(sender, executioncontext, frame);
        };
    }
}
