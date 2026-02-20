package net.minecraft.util.profiling.metrics;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public class MetricsRegistry {

    public static final MetricsRegistry INSTANCE = new MetricsRegistry();
    private final WeakHashMap<ProfilerMeasured, Void> measuredInstances = new WeakHashMap();

    private MetricsRegistry() {}

    public void add(ProfilerMeasured profilerMeasured) {
        this.measuredInstances.put(profilerMeasured, (Object) null);
    }

    public List<MetricSampler> getRegisteredSamplers() {
        Map<String, List<MetricSampler>> map = (Map) this.measuredInstances.keySet().stream().flatMap((profilermeasured) -> {
            return profilermeasured.profiledMetrics().stream();
        }).collect(Collectors.groupingBy(MetricSampler::getName));

        return aggregateDuplicates(map);
    }

    private static List<MetricSampler> aggregateDuplicates(Map<String, List<MetricSampler>> potentialDuplicates) {
        return (List) potentialDuplicates.entrySet().stream().map((entry) -> {
            String s = (String) entry.getKey();
            List<MetricSampler> list = (List) entry.getValue();

            return (MetricSampler) (list.size() > 1 ? new MetricsRegistry.AggregatedMetricSampler(s, list) : (MetricSampler) list.get(0));
        }).collect(Collectors.toList());
    }

    private static class AggregatedMetricSampler extends MetricSampler {

        private final List<MetricSampler> delegates;

        private AggregatedMetricSampler(String name, List<MetricSampler> delegates) {
            super(name, ((MetricSampler) delegates.get(0)).getCategory(), () -> {
                return averageValueFromDelegates(delegates);
            }, () -> {
                beforeTick(delegates);
            }, thresholdTest(delegates));
            this.delegates = delegates;
        }

        private static MetricSampler.ThresholdTest thresholdTest(List<MetricSampler> delegates) {
            return (d0) -> {
                return delegates.stream().anyMatch((metricsampler) -> {
                    return metricsampler.thresholdTest != null ? metricsampler.thresholdTest.test(d0) : false;
                });
            };
        }

        private static void beforeTick(List<MetricSampler> delegates) {
            for (MetricSampler metricsampler : delegates) {
                metricsampler.onStartTick();
            }

        }

        private static double averageValueFromDelegates(List<MetricSampler> delegates) {
            double d0 = 0.0D;

            for (MetricSampler metricsampler : delegates) {
                d0 += metricsampler.getSampler().getAsDouble();
            }

            return d0 / (double) delegates.size();
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            } else if (o != null && this.getClass() == o.getClass()) {
                if (!super.equals(o)) {
                    return false;
                } else {
                    MetricsRegistry.AggregatedMetricSampler metricsregistry_aggregatedmetricsampler = (MetricsRegistry.AggregatedMetricSampler) o;

                    return this.delegates.equals(metricsregistry_aggregatedmetricsampler.delegates);
                }
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(new Object[]{super.hashCode(), this.delegates});
        }
    }
}
