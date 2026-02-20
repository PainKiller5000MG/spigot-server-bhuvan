package net.minecraft.util.thread;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.util.profiling.metrics.MetricsRegistry;

public class PriorityConsecutiveExecutor extends AbstractConsecutiveExecutor<StrictQueue.RunnableWithPriority> {

    public PriorityConsecutiveExecutor(int priorityCount, Executor executor, String name) {
        super(new StrictQueue.FixedPriorityQueue(priorityCount), executor, name);
        MetricsRegistry.INSTANCE.add(this);
    }

    @Override
    public StrictQueue.RunnableWithPriority wrapRunnable(Runnable runnable) {
        return new StrictQueue.RunnableWithPriority(0, runnable);
    }

    public <Source> CompletableFuture<Source> scheduleWithResult(int priority, Consumer<CompletableFuture<Source>> futureConsumer) {
        CompletableFuture<Source> completablefuture = new CompletableFuture();

        this.schedule(new StrictQueue.RunnableWithPriority(priority, () -> {
            futureConsumer.accept(completablefuture);
        }));
        return completablefuture;
    }
}
