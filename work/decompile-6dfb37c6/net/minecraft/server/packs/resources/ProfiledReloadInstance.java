package net.minecraft.server.packs.resources;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class ProfiledReloadInstance extends SimpleReloadInstance<ProfiledReloadInstance.State> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Stopwatch total = Stopwatch.createUnstarted();

    public static ReloadInstance of(ResourceManager resourceManager, List<PreparableReloadListener> listeners, Executor taskExecutor, Executor mainThreadExecutor, CompletableFuture<Unit> initialTask) {
        ProfiledReloadInstance profiledreloadinstance = new ProfiledReloadInstance(listeners);

        profiledreloadinstance.startTasks(taskExecutor, mainThreadExecutor, resourceManager, listeners, (preparablereloadlistener_sharedstate, preparablereloadlistener_preparationbarrier, preparablereloadlistener, executor2, executor3) -> {
            AtomicLong atomiclong = new AtomicLong();
            AtomicLong atomiclong1 = new AtomicLong();
            AtomicLong atomiclong2 = new AtomicLong();
            AtomicLong atomiclong3 = new AtomicLong();
            CompletableFuture<Void> completablefuture1 = preparablereloadlistener.reload(preparablereloadlistener_sharedstate, profiledExecutor(executor2, atomiclong, atomiclong1, preparablereloadlistener.getName()), preparablereloadlistener_preparationbarrier, profiledExecutor(executor3, atomiclong2, atomiclong3, preparablereloadlistener.getName()));

            return completablefuture1.thenApplyAsync((ovoid) -> {
                ProfiledReloadInstance.LOGGER.debug("Finished reloading {}", preparablereloadlistener.getName());
                return new ProfiledReloadInstance.State(preparablereloadlistener.getName(), atomiclong, atomiclong1, atomiclong2, atomiclong3);
            }, mainThreadExecutor);
        }, initialTask);
        return profiledreloadinstance;
    }

    private ProfiledReloadInstance(List<PreparableReloadListener> listeners) {
        super(listeners);
        this.total.start();
    }

    @Override
    protected CompletableFuture<List<ProfiledReloadInstance.State>> prepareTasks(Executor taskExecutor, Executor mainThreadExecutor, ResourceManager resourceManager, List<PreparableReloadListener> listeners, SimpleReloadInstance.StateFactory<ProfiledReloadInstance.State> stateFactory, CompletableFuture<?> initialTask) {
        return super.prepareTasks(taskExecutor, mainThreadExecutor, resourceManager, listeners, stateFactory, initialTask).thenApplyAsync(this::finish, mainThreadExecutor);
    }

    private static Executor profiledExecutor(Executor executor, AtomicLong accumulatedNanos, AtomicLong taskCount, String name) {
        return (runnable) -> {
            executor.execute(() -> {
                ProfilerFiller profilerfiller = Profiler.get();

                profilerfiller.push(name);
                long i = Util.getNanos();

                runnable.run();
                accumulatedNanos.addAndGet(Util.getNanos() - i);
                taskCount.incrementAndGet();
                profilerfiller.pop();
            });
        };
    }

    private List<ProfiledReloadInstance.State> finish(List<ProfiledReloadInstance.State> result) {
        this.total.stop();
        long i = 0L;

        ProfiledReloadInstance.LOGGER.info("Resource reload finished after {} ms", this.total.elapsed(TimeUnit.MILLISECONDS));

        for (ProfiledReloadInstance.State profiledreloadinstance_state : result) {
            long j = TimeUnit.NANOSECONDS.toMillis(profiledreloadinstance_state.preparationNanos.get());
            long k = profiledreloadinstance_state.preparationCount.get();
            long l = TimeUnit.NANOSECONDS.toMillis(profiledreloadinstance_state.reloadNanos.get());
            long i1 = profiledreloadinstance_state.reloadCount.get();
            long j1 = j + l;
            long k1 = k + i1;
            String s = profiledreloadinstance_state.name;

            ProfiledReloadInstance.LOGGER.info("{} took approximately {} tasks/{} ms ({} tasks/{} ms preparing, {} tasks/{} ms applying)", new Object[]{s, k1, j1, k, j, i1, l});
            i += l;
        }

        ProfiledReloadInstance.LOGGER.info("Total blocking time: {} ms", i);
        return result;
    }

    public static record State(String name, AtomicLong preparationNanos, AtomicLong preparationCount, AtomicLong reloadNanos, AtomicLong reloadCount) {

    }
}
