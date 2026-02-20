package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public interface ResourceManagerReloadListener extends PreparableReloadListener {

    @Override
    default CompletableFuture<Void> reload(PreparableReloadListener.SharedState currentReload, Executor taskExecutor, PreparableReloadListener.PreparationBarrier preparationBarrier, Executor reloadExecutor) {
        ResourceManager resourcemanager = currentReload.resourceManager();

        return preparationBarrier.wait(Unit.INSTANCE).thenRunAsync(() -> {
            ProfilerFiller profilerfiller = Profiler.get();

            profilerfiller.push("listener");
            this.onResourceManagerReload(resourcemanager);
            profilerfiller.pop();
        }, reloadExecutor);
    }

    void onResourceManagerReload(ResourceManager resourceManager);
}
