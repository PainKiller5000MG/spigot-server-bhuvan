package net.minecraft.server.packs.resources;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class SimpleReloadInstance<S> implements ReloadInstance {

    private static final int PREPARATION_PROGRESS_WEIGHT = 2;
    private static final int EXTRA_RELOAD_PROGRESS_WEIGHT = 2;
    private static final int LISTENER_PROGRESS_WEIGHT = 1;
    private final CompletableFuture<Unit> allPreparations = new CompletableFuture();
    private @Nullable CompletableFuture<List<S>> allDone;
    private final Set<PreparableReloadListener> preparingListeners;
    private final int listenerCount;
    private final AtomicInteger startedTasks = new AtomicInteger();
    private final AtomicInteger finishedTasks = new AtomicInteger();
    private final AtomicInteger startedReloads = new AtomicInteger();
    private final AtomicInteger finishedReloads = new AtomicInteger();

    public static ReloadInstance of(ResourceManager resourceManager, List<PreparableReloadListener> listeners, Executor taskExecutor, Executor mainThreadExecutor, CompletableFuture<Unit> initialTask) {
        SimpleReloadInstance<Void> simplereloadinstance = new SimpleReloadInstance<Void>(listeners);

        simplereloadinstance.startTasks(taskExecutor, mainThreadExecutor, resourceManager, listeners, SimpleReloadInstance.StateFactory.SIMPLE, initialTask);
        return simplereloadinstance;
    }

    protected SimpleReloadInstance(List<PreparableReloadListener> listeners) {
        this.listenerCount = listeners.size();
        this.preparingListeners = new HashSet(listeners);
    }

    protected void startTasks(Executor taskExecutor, Executor mainThreadExecutor, ResourceManager resourceManager, List<PreparableReloadListener> listeners, SimpleReloadInstance.StateFactory<S> stateFactory, CompletableFuture<?> initialTask) {
        this.allDone = this.prepareTasks(taskExecutor, mainThreadExecutor, resourceManager, listeners, stateFactory, initialTask);
    }

    protected CompletableFuture<List<S>> prepareTasks(Executor taskExecutor, Executor mainThreadExecutor, ResourceManager resourceManager, List<PreparableReloadListener> listeners, SimpleReloadInstance.StateFactory<S> stateFactory, CompletableFuture<?> initialTask) {
        Executor executor2 = (runnable) -> {
            this.startedTasks.incrementAndGet();
            taskExecutor.execute(() -> {
                runnable.run();
                this.finishedTasks.incrementAndGet();
            });
        };
        Executor executor3 = (runnable) -> {
            this.startedReloads.incrementAndGet();
            mainThreadExecutor.execute(() -> {
                runnable.run();
                this.finishedReloads.incrementAndGet();
            });
        };

        this.startedTasks.incrementAndGet();
        AtomicInteger atomicinteger = this.finishedTasks;

        Objects.requireNonNull(this.finishedTasks);
        initialTask.thenRun(atomicinteger::incrementAndGet);
        PreparableReloadListener.SharedState preparablereloadlistener_sharedstate = new PreparableReloadListener.SharedState(resourceManager);

        listeners.forEach((preparablereloadlistener) -> {
            preparablereloadlistener.prepareSharedState(preparablereloadlistener_sharedstate);
        });
        CompletableFuture<?> completablefuture1 = initialTask;
        List<CompletableFuture<S>> list1 = new ArrayList();

        for (PreparableReloadListener preparablereloadlistener : listeners) {
            PreparableReloadListener.PreparationBarrier preparablereloadlistener_preparationbarrier = this.createBarrierForListener(preparablereloadlistener, completablefuture1, mainThreadExecutor);
            CompletableFuture<S> completablefuture2 = stateFactory.create(preparablereloadlistener_sharedstate, preparablereloadlistener_preparationbarrier, preparablereloadlistener, executor2, executor3);

            list1.add(completablefuture2);
            completablefuture1 = completablefuture2;
        }

        return Util.sequenceFailFast(list1);
    }

    private PreparableReloadListener.PreparationBarrier createBarrierForListener(final PreparableReloadListener listener, final CompletableFuture<?> previousBarrier, final Executor mainThreadExecutor) {
        return new PreparableReloadListener.PreparationBarrier() {
            @Override
            public <T> CompletableFuture<T> wait(T t) {
                mainThreadExecutor.execute(() -> {
                    SimpleReloadInstance.this.preparingListeners.remove(listener);
                    if (SimpleReloadInstance.this.preparingListeners.isEmpty()) {
                        SimpleReloadInstance.this.allPreparations.complete(Unit.INSTANCE);
                    }

                });
                return SimpleReloadInstance.this.allPreparations.thenCombine(previousBarrier, (unit, object) -> {
                    return t;
                });
            }
        };
    }

    @Override
    public CompletableFuture<?> done() {
        return (CompletableFuture) Objects.requireNonNull(this.allDone, "not started");
    }

    @Override
    public float getActualProgress() {
        int i = this.listenerCount - this.preparingListeners.size();
        float f = (float) weightProgress(this.finishedTasks.get(), this.finishedReloads.get(), i);
        float f1 = (float) weightProgress(this.startedTasks.get(), this.startedReloads.get(), this.listenerCount);

        return f / f1;
    }

    private static int weightProgress(int preparationTasks, int reloadTasks, int listeners) {
        return preparationTasks * 2 + reloadTasks * 2 + listeners * 1;
    }

    public static ReloadInstance create(ResourceManager resourceManager, List<PreparableReloadListener> listeners, Executor backgroundExecutor, Executor mainThreadExecutor, CompletableFuture<Unit> initialTask, boolean enableProfiling) {
        return enableProfiling ? ProfiledReloadInstance.of(resourceManager, listeners, backgroundExecutor, mainThreadExecutor, initialTask) : of(resourceManager, listeners, backgroundExecutor, mainThreadExecutor, initialTask);
    }

    @FunctionalInterface
    protected interface StateFactory<S> {

        SimpleReloadInstance.StateFactory<Void> SIMPLE = (preparablereloadlistener_sharedstate, preparablereloadlistener_preparationbarrier, preparablereloadlistener, executor, executor1) -> {
            return preparablereloadlistener.reload(preparablereloadlistener_sharedstate, executor, preparablereloadlistener_preparationbarrier, executor1);
        };

        CompletableFuture<S> create(PreparableReloadListener.SharedState sharedState, PreparableReloadListener.PreparationBarrier previousStep, PreparableReloadListener listener, Executor taskExecutor, Executor reloadExecutor);
    }
}
