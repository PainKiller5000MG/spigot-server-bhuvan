package net.minecraft.util.profiling.metrics.profiling;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.profiling.ActiveProfiler;
import net.minecraft.util.profiling.ProfileCollector;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.util.profiling.metrics.MetricSampler;

public class ProfilerSamplerAdapter {

    private final Set<String> previouslyFoundSamplerNames = new ObjectOpenHashSet();

    public ProfilerSamplerAdapter() {}

    public Set<MetricSampler> newSamplersFoundInProfiler(Supplier<ProfileCollector> profiler) {
        Set<MetricSampler> set = (Set) ((ProfileCollector) profiler.get()).getChartedPaths().stream().filter((pair) -> {
            return !this.previouslyFoundSamplerNames.contains(pair.getLeft());
        }).map((pair) -> {
            return samplerForProfilingPath(profiler, (String) pair.getLeft(), (MetricCategory) pair.getRight());
        }).collect(Collectors.toSet());

        for (MetricSampler metricsampler : set) {
            this.previouslyFoundSamplerNames.add(metricsampler.getName());
        }

        return set;
    }

    private static MetricSampler samplerForProfilingPath(Supplier<ProfileCollector> profiler, String profilerPath, MetricCategory category) {
        return MetricSampler.create(profilerPath, category, () -> {
            ActiveProfiler.PathEntry activeprofiler_pathentry = ((ProfileCollector) profiler.get()).getEntry(profilerPath);

            return activeprofiler_pathentry == null ? 0.0D : (double) activeprofiler_pathentry.getMaxDuration() / (double) TimeUtil.NANOSECONDS_PER_MILLISECOND;
        });
    }
}
