package net.minecraft.util.profiling.jfr.stats;

import com.google.common.base.MoreObjects;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

public record ThreadAllocationStat(Instant timestamp, String threadName, long totalBytes) {

    private static final String UNKNOWN_THREAD = "unknown";

    public static ThreadAllocationStat from(RecordedEvent event) {
        RecordedThread recordedthread = event.getThread("thread");
        String s = recordedthread == null ? "unknown" : (String) MoreObjects.firstNonNull(recordedthread.getJavaName(), "unknown");

        return new ThreadAllocationStat(event.getStartTime(), s, event.getLong("allocated"));
    }

    public static ThreadAllocationStat.Summary summary(List<ThreadAllocationStat> stats) {
        Map<String, Double> map = new TreeMap();
        Map<String, List<ThreadAllocationStat>> map1 = (Map) stats.stream().collect(Collectors.groupingBy((threadallocationstat) -> {
            return threadallocationstat.threadName;
        }));

        map1.forEach((s, list1) -> {
            if (list1.size() >= 2) {
                ThreadAllocationStat threadallocationstat = (ThreadAllocationStat) list1.get(0);
                ThreadAllocationStat threadallocationstat1 = (ThreadAllocationStat) list1.get(list1.size() - 1);
                long i = Duration.between(threadallocationstat.timestamp, threadallocationstat1.timestamp).getSeconds();
                long j = threadallocationstat1.totalBytes - threadallocationstat.totalBytes;

                map.put(s, (double) j / (double) i);
            }
        });
        return new ThreadAllocationStat.Summary(map);
    }

    public static record Summary(Map<String, Double> allocationsPerSecondByThread) {

    }
}
