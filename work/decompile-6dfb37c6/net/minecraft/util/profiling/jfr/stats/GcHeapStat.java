package net.minecraft.util.profiling.jfr.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jdk.jfr.consumer.RecordedEvent;

public record GcHeapStat(Instant timestamp, long heapUsed, GcHeapStat.Timing timing) {

    public static GcHeapStat from(RecordedEvent event) {
        return new GcHeapStat(event.getStartTime(), event.getLong("heapUsed"), event.getString("when").equalsIgnoreCase("before gc") ? GcHeapStat.Timing.BEFORE_GC : GcHeapStat.Timing.AFTER_GC);
    }

    public static GcHeapStat.Summary summary(Duration recordingDuration, List<GcHeapStat> heapStats, Duration gcTotalDuration, int totalGCs) {
        return new GcHeapStat.Summary(recordingDuration, gcTotalDuration, totalGCs, calculateAllocationRatePerSecond(heapStats));
    }

    private static double calculateAllocationRatePerSecond(List<GcHeapStat> heapStats) {
        long i = 0L;
        Map<GcHeapStat.Timing, List<GcHeapStat>> map = (Map) heapStats.stream().collect(Collectors.groupingBy((gcheapstat) -> {
            return gcheapstat.timing;
        }));
        List<GcHeapStat> list1 = (List) map.get(GcHeapStat.Timing.BEFORE_GC);
        List<GcHeapStat> list2 = (List) map.get(GcHeapStat.Timing.AFTER_GC);

        for (int j = 1; j < list1.size(); ++j) {
            GcHeapStat gcheapstat = (GcHeapStat) list1.get(j);
            GcHeapStat gcheapstat1 = (GcHeapStat) list2.get(j - 1);

            i += gcheapstat.heapUsed - gcheapstat1.heapUsed;
        }

        Duration duration = Duration.between(((GcHeapStat) heapStats.get(1)).timestamp, ((GcHeapStat) heapStats.get(heapStats.size() - 1)).timestamp);

        return (double) i / (double) duration.getSeconds();
    }

    public static record Summary(Duration duration, Duration gcTotalDuration, int totalGCs, double allocationRateBytesPerSecond) {

        public float gcOverHead() {
            return (float) this.gcTotalDuration.toMillis() / (float) this.duration.toMillis();
        }
    }

    static enum Timing {

        BEFORE_GC, AFTER_GC;

        private Timing() {}
    }
}
