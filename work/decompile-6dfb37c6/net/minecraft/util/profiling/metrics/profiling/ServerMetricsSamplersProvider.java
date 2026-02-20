package net.minecraft.util.profiling.metrics.profiling;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.SystemReport;
import net.minecraft.util.profiling.ProfileCollector;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.util.profiling.metrics.MetricSampler;
import net.minecraft.util.profiling.metrics.MetricsRegistry;
import net.minecraft.util.profiling.metrics.MetricsSamplerProvider;
import org.slf4j.Logger;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

public class ServerMetricsSamplersProvider implements MetricsSamplerProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Set<MetricSampler> samplers = new ObjectOpenHashSet();
    private final ProfilerSamplerAdapter samplerFactory = new ProfilerSamplerAdapter();

    public ServerMetricsSamplersProvider(LongSupplier wallTimeSource, boolean isDedicatedServer) {
        this.samplers.add(tickTimeSampler(wallTimeSource));
        if (isDedicatedServer) {
            this.samplers.addAll(runtimeIndependentSamplers());
        }

    }

    public static Set<MetricSampler> runtimeIndependentSamplers() {
        ImmutableSet.Builder<MetricSampler> immutableset_builder = ImmutableSet.builder();

        try {
            ServerMetricsSamplersProvider.CpuStats servermetricssamplersprovider_cpustats = new ServerMetricsSamplersProvider.CpuStats();
            Stream stream = IntStream.range(0, servermetricssamplersprovider_cpustats.nrOfCpus).mapToObj((i) -> {
                return MetricSampler.create("cpu#" + i, MetricCategory.CPU, () -> {
                    return servermetricssamplersprovider_cpustats.loadForCpu(i);
                });
            });

            Objects.requireNonNull(immutableset_builder);
            stream.forEach(immutableset_builder::add);
        } catch (Throwable throwable) {
            ServerMetricsSamplersProvider.LOGGER.warn("Failed to query cpu, no cpu stats will be recorded", throwable);
        }

        immutableset_builder.add(MetricSampler.create("heap MiB", MetricCategory.JVM, () -> {
            return (double) SystemReport.sizeInMiB(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        }));
        immutableset_builder.addAll(MetricsRegistry.INSTANCE.getRegisteredSamplers());
        return immutableset_builder.build();
    }

    @Override
    public Set<MetricSampler> samplers(Supplier<ProfileCollector> profiler) {
        this.samplers.addAll(this.samplerFactory.newSamplersFoundInProfiler(profiler));
        return this.samplers;
    }

    public static MetricSampler tickTimeSampler(final LongSupplier timeSource) {
        Stopwatch stopwatch = Stopwatch.createUnstarted(new Ticker() {
            public long read() {
                return timeSource.getAsLong();
            }
        });
        ToDoubleFunction<Stopwatch> todoublefunction = (stopwatch1) -> {
            if (stopwatch1.isRunning()) {
                stopwatch1.stop();
            }

            long i = stopwatch1.elapsed(TimeUnit.NANOSECONDS);

            stopwatch1.reset();
            return (double) i;
        };
        MetricSampler.ValueIncreasedByPercentage metricsampler_valueincreasedbypercentage = new MetricSampler.ValueIncreasedByPercentage(2.0F);

        return MetricSampler.builder("ticktime", MetricCategory.TICK_LOOP, todoublefunction, stopwatch).withBeforeTick(Stopwatch::start).withThresholdAlert(metricsampler_valueincreasedbypercentage).build();
    }

    static class CpuStats {

        private final SystemInfo systemInfo = new SystemInfo();
        private final CentralProcessor processor;
        public final int nrOfCpus;
        private long[][] previousCpuLoadTick;
        private double[] currentLoad;
        private long lastPollMs;

        CpuStats() {
            this.processor = this.systemInfo.getHardware().getProcessor();
            this.nrOfCpus = this.processor.getLogicalProcessorCount();
            this.previousCpuLoadTick = this.processor.getProcessorCpuLoadTicks();
            this.currentLoad = this.processor.getProcessorCpuLoadBetweenTicks(this.previousCpuLoadTick);
        }

        public double loadForCpu(int i) {
            long j = System.currentTimeMillis();

            if (this.lastPollMs == 0L || this.lastPollMs + 501L < j) {
                this.currentLoad = this.processor.getProcessorCpuLoadBetweenTicks(this.previousCpuLoadTick);
                this.previousCpuLoadTick = this.processor.getProcessorCpuLoadTicks();
                this.lastPollMs = j;
            }

            return this.currentLoad[i] * 100.0D;
        }
    }
}
