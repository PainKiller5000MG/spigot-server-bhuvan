package net.minecraft.server.packs.resources;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public abstract class SimplePreparableReloadListener<T> implements PreparableReloadListener {

    public SimplePreparableReloadListener() {}

    @Override
    public final CompletableFuture<Void> reload(PreparableReloadListener.SharedState currentReload, Executor taskExecutor, PreparableReloadListener.PreparationBarrier preparationBarrier, Executor reloadExecutor) {
        ResourceManager resourcemanager = currentReload.resourceManager();
        CompletableFuture completablefuture = CompletableFuture.supplyAsync(() -> {
            return this.prepare(resourcemanager, Profiler.get());
        }, taskExecutor);

        Objects.requireNonNull(preparationBarrier);
        return completablefuture.thenCompose(preparationBarrier::wait).thenAcceptAsync((object) -> {
            this.apply(object, resourcemanager, Profiler.get());
        }, reloadExecutor);
    }

    protected abstract T prepare(ResourceManager manager, ProfilerFiller profiler);

    protected abstract void apply(T preparations, ResourceManager manager, ProfilerFiller profiler);
}
