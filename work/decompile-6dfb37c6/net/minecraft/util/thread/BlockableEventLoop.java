package net.minecraft.util.thread;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;
import com.mojang.jtracy.TracyClient;
import com.mojang.jtracy.Zone;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.CheckReturnValue;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.util.profiling.metrics.MetricSampler;
import net.minecraft.util.profiling.metrics.MetricsRegistry;
import net.minecraft.util.profiling.metrics.ProfilerMeasured;
import org.slf4j.Logger;

public abstract class BlockableEventLoop<R extends Runnable> implements Executor, TaskScheduler<R>, ProfilerMeasured {

    public static final long BLOCK_TIME_NANOS = 100000L;
    private final String name;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Queue<R> pendingRunnables = Queues.newConcurrentLinkedQueue();
    private int blockingCount;

    protected BlockableEventLoop(String name) {
        this.name = name;
        MetricsRegistry.INSTANCE.add(this);
    }

    protected abstract boolean shouldRun(R task);

    public boolean isSameThread() {
        return Thread.currentThread() == this.getRunningThread();
    }

    protected abstract Thread getRunningThread();

    protected boolean scheduleExecutables() {
        return !this.isSameThread();
    }

    public int getPendingTasksCount() {
        return this.pendingRunnables.size();
    }

    @Override
    public String name() {
        return this.name;
    }

    public <V> CompletableFuture<V> submit(Supplier<V> supplier) {
        return this.scheduleExecutables() ? CompletableFuture.supplyAsync(supplier, this) : CompletableFuture.completedFuture(supplier.get());
    }

    private CompletableFuture<Void> submitAsync(Runnable runnable) {
        return CompletableFuture.supplyAsync(() -> {
            runnable.run();
            return null;
        }, this);
    }

    @CheckReturnValue
    public CompletableFuture<Void> submit(Runnable runnable) {
        if (this.scheduleExecutables()) {
            return this.submitAsync(runnable);
        } else {
            runnable.run();
            return CompletableFuture.completedFuture((Object) null);
        }
    }

    public void executeBlocking(Runnable runnable) {
        if (!this.isSameThread()) {
            this.submitAsync(runnable).join();
        } else {
            runnable.run();
        }

    }

    @Override
    public void schedule(R r) {
        this.pendingRunnables.add(r);
        LockSupport.unpark(this.getRunningThread());
    }

    public void execute(Runnable command) {
        R r0 = this.wrapRunnable(command);

        if (this.scheduleExecutables()) {
            this.schedule(r0);
        } else {
            this.doRunTask(r0);
        }

    }

    public void executeIfPossible(Runnable command) {
        this.execute(command);
    }

    protected void dropAllTasks() {
        this.pendingRunnables.clear();
    }

    protected void runAllTasks() {
        while (this.pollTask()) {
            ;
        }

    }

    protected boolean shouldRunAllTasks() {
        return this.blockingCount > 0;
    }

    protected boolean pollTask() {
        R r0 = (R) (this.pendingRunnables.peek());

        if (r0 == null) {
            return false;
        } else if (!this.shouldRunAllTasks() && !this.shouldRun(r0)) {
            return false;
        } else {
            this.doRunTask((Runnable) this.pendingRunnables.remove());
            return true;
        }
    }

    public void managedBlock(BooleanSupplier condition) {
        ++this.blockingCount;

        try {
            while (!condition.getAsBoolean()) {
                if (!this.pollTask()) {
                    this.waitForTasks();
                }
            }
        } finally {
            --this.blockingCount;
        }

    }

    protected void waitForTasks() {
        Thread.yield();
        LockSupport.parkNanos("waiting for tasks", 100000L);
    }

    protected void doRunTask(R task) {
        try (Zone zone = TracyClient.beginZone("Task", SharedConstants.IS_RUNNING_IN_IDE)) {
            task.run();
        } catch (Exception exception) {
            BlockableEventLoop.LOGGER.error(LogUtils.FATAL_MARKER, "Error executing task on {}", this.name(), exception);
            if (isNonRecoverable(exception)) {
                throw exception;
            }
        }

    }

    @Override
    public List<MetricSampler> profiledMetrics() {
        return ImmutableList.of(MetricSampler.create(this.name + "-pending-tasks", MetricCategory.EVENT_LOOPS, this::getPendingTasksCount));
    }

    public static boolean isNonRecoverable(Throwable t) {
        if (t instanceof ReportedException reportedexception) {
            return isNonRecoverable(reportedexception.getCause());
        } else {
            return t instanceof OutOfMemoryError || t instanceof StackOverflowError;
        }
    }
}
