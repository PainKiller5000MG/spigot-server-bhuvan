package net.minecraft.util.profiling.metrics.profiling;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.util.profiling.ActiveProfiler;
import net.minecraft.util.profiling.ContinuousProfiler;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfileCollector;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.metrics.MetricSampler;
import net.minecraft.util.profiling.metrics.MetricsSamplerProvider;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import net.minecraft.util.profiling.metrics.storage.RecordedDeviation;
import org.jspecify.annotations.Nullable;

public class ActiveMetricsRecorder implements MetricsRecorder {

    public static final int PROFILING_MAX_DURATION_SECONDS = 10;
    private static @Nullable Consumer<Path> globalOnReportFinished = null;
    private final Map<MetricSampler, List<RecordedDeviation>> deviationsBySampler = new Object2ObjectOpenHashMap();
    private final ContinuousProfiler taskProfiler;
    private final Executor ioExecutor;
    private final MetricsPersister metricsPersister;
    private final Consumer<ProfileResults> onProfilingEnd;
    private final Consumer<Path> onReportFinished;
    private final MetricsSamplerProvider metricsSamplerProvider;
    private final LongSupplier wallTimeSource;
    private final long deadlineNano;
    private int currentTick;
    private ProfileCollector singleTickProfiler;
    private volatile boolean killSwitch;
    private Set<MetricSampler> thisTickSamplers = ImmutableSet.of();

    private ActiveMetricsRecorder(MetricsSamplerProvider metricsSamplerProvider, LongSupplier timeSource, Executor ioExecutor, MetricsPersister metricsPersister, Consumer<ProfileResults> onProfilingEnd, Consumer<Path> onReportFinished) {
        this.metricsSamplerProvider = metricsSamplerProvider;
        this.wallTimeSource = timeSource;
        this.taskProfiler = new ContinuousProfiler(timeSource, () -> {
            return this.currentTick;
        }, () -> {
            return false;
        });
        this.ioExecutor = ioExecutor;
        this.metricsPersister = metricsPersister;
        this.onProfilingEnd = onProfilingEnd;
        this.onReportFinished = ActiveMetricsRecorder.globalOnReportFinished == null ? onReportFinished : onReportFinished.andThen(ActiveMetricsRecorder.globalOnReportFinished);
        this.deadlineNano = timeSource.getAsLong() + TimeUnit.NANOSECONDS.convert(10L, TimeUnit.SECONDS);
        this.singleTickProfiler = new ActiveProfiler(this.wallTimeSource, () -> {
            return this.currentTick;
        }, () -> {
            return true;
        });
        this.taskProfiler.enable();
    }

    public static ActiveMetricsRecorder createStarted(MetricsSamplerProvider metricsSamplerProvider, LongSupplier timeSource, Executor ioExecutor, MetricsPersister metricsPersister, Consumer<ProfileResults> onProfilingEnd, Consumer<Path> onReportFinished) {
        return new ActiveMetricsRecorder(metricsSamplerProvider, timeSource, ioExecutor, metricsPersister, onProfilingEnd, onReportFinished);
    }

    @Override
    public synchronized void end() {
        if (this.isRecording()) {
            this.killSwitch = true;
        }
    }

    @Override
    public synchronized void cancel() {
        if (this.isRecording()) {
            this.singleTickProfiler = InactiveProfiler.INSTANCE;
            this.onProfilingEnd.accept(EmptyProfileResults.EMPTY);
            this.cleanup(this.thisTickSamplers);
        }
    }

    @Override
    public void startTick() {
        this.verifyStarted();
        this.thisTickSamplers = this.metricsSamplerProvider.samplers(() -> {
            return this.singleTickProfiler;
        });

        for (MetricSampler metricsampler : this.thisTickSamplers) {
            metricsampler.onStartTick();
        }

        ++this.currentTick;
    }

    @Override
    public void endTick() {
        this.verifyStarted();
        if (this.currentTick != 0) {
            for (MetricSampler metricsampler : this.thisTickSamplers) {
                metricsampler.onEndTick(this.currentTick);
                if (metricsampler.triggersThreshold()) {
                    RecordedDeviation recordeddeviation = new RecordedDeviation(Instant.now(), this.currentTick, this.singleTickProfiler.getResults());

                    ((List) this.deviationsBySampler.computeIfAbsent(metricsampler, (metricsampler1) -> {
                        return Lists.newArrayList();
                    })).add(recordeddeviation);
                }
            }

            if (!this.killSwitch && this.wallTimeSource.getAsLong() <= this.deadlineNano) {
                this.singleTickProfiler = new ActiveProfiler(this.wallTimeSource, () -> {
                    return this.currentTick;
                }, () -> {
                    return true;
                });
            } else {
                this.killSwitch = false;
                ProfileResults profileresults = this.taskProfiler.getResults();

                this.singleTickProfiler = InactiveProfiler.INSTANCE;
                this.onProfilingEnd.accept(profileresults);
                this.scheduleSaveResults(profileresults);
            }
        }
    }

    @Override
    public boolean isRecording() {
        return this.taskProfiler.isEnabled();
    }

    @Override
    public ProfilerFiller getProfiler() {
        return ProfilerFiller.combine(this.taskProfiler.getFiller(), this.singleTickProfiler);
    }

    private void verifyStarted() {
        if (!this.isRecording()) {
            throw new IllegalStateException("Not started!");
        }
    }

    private void scheduleSaveResults(ProfileResults profilerResults) {
        HashSet<MetricSampler> hashset = new HashSet(this.thisTickSamplers);

        this.ioExecutor.execute(() -> {
            Path path = this.metricsPersister.saveReports(hashset, this.deviationsBySampler, profilerResults);

            this.cleanup(hashset);
            this.onReportFinished.accept(path);
        });
    }

    private void cleanup(Collection<MetricSampler> metricSamplers) {
        for (MetricSampler metricsampler : metricSamplers) {
            metricsampler.onFinished();
        }

        this.deviationsBySampler.clear();
        this.taskProfiler.disable();
    }

    public static void registerGlobalCompletionCallback(Consumer<Path> onFinished) {
        ActiveMetricsRecorder.globalOnReportFinished = onFinished;
    }
}
