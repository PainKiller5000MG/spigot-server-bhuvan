package net.minecraft.util.profiling;

import com.mojang.jtracy.TracyClient;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

public final class Profiler {

    private static final ThreadLocal<TracyZoneFiller> TRACY_FILLER = ThreadLocal.withInitial(TracyZoneFiller::new);
    private static final ThreadLocal<@Nullable ProfilerFiller> ACTIVE = new ThreadLocal();
    private static final AtomicInteger ACTIVE_COUNT = new AtomicInteger();

    private Profiler() {}

    public static Profiler.Scope use(ProfilerFiller filler) {
        startUsing(filler);
        return Profiler::stopUsing;
    }

    private static void startUsing(ProfilerFiller filler) {
        if (Profiler.ACTIVE.get() != null) {
            throw new IllegalStateException("Profiler is already active");
        } else {
            ProfilerFiller profilerfiller1 = decorateFiller(filler);

            Profiler.ACTIVE.set(profilerfiller1);
            Profiler.ACTIVE_COUNT.incrementAndGet();
            profilerfiller1.startTick();
        }
    }

    private static void stopUsing() {
        ProfilerFiller profilerfiller = (ProfilerFiller) Profiler.ACTIVE.get();

        if (profilerfiller == null) {
            throw new IllegalStateException("Profiler was not active");
        } else {
            Profiler.ACTIVE.remove();
            Profiler.ACTIVE_COUNT.decrementAndGet();
            profilerfiller.endTick();
        }
    }

    private static ProfilerFiller decorateFiller(ProfilerFiller filler) {
        return ProfilerFiller.combine(getDefaultFiller(), filler);
    }

    public static ProfilerFiller get() {
        return Profiler.ACTIVE_COUNT.get() == 0 ? getDefaultFiller() : (ProfilerFiller) Objects.requireNonNullElseGet((ProfilerFiller) Profiler.ACTIVE.get(), Profiler::getDefaultFiller);
    }

    private static ProfilerFiller getDefaultFiller() {
        return (ProfilerFiller) (TracyClient.isAvailable() ? (ProfilerFiller) Profiler.TRACY_FILLER.get() : InactiveProfiler.INSTANCE);
    }

    public interface Scope extends AutoCloseable {

        void close();
    }
}
