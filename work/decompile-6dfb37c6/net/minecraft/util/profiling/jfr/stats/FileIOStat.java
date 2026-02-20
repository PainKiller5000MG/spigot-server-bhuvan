package net.minecraft.util.profiling.jfr.stats;

import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public record FileIOStat(Duration duration, @Nullable String path, long bytes) {

    public static FileIOStat.Summary summary(Duration recordingDuration, List<FileIOStat> ioStats) {
        long i = ioStats.stream().mapToLong((fileiostat) -> {
            return fileiostat.bytes;
        }).sum();

        return new FileIOStat.Summary(i, (double) i / (double) recordingDuration.getSeconds(), (long) ioStats.size(), (double) ioStats.size() / (double) recordingDuration.getSeconds(), (Duration) ioStats.stream().map(FileIOStat::duration).reduce(Duration.ZERO, Duration::plus), ((Map) ioStats.stream().filter((fileiostat) -> {
            return fileiostat.path != null;
        }).collect(Collectors.groupingBy((fileiostat) -> {
            return fileiostat.path;
        }, Collectors.summingLong((fileiostat) -> {
            return fileiostat.bytes;
        })))).entrySet().stream().sorted(Entry.comparingByValue().reversed()).map((entry) -> {
            return Pair.of((String) entry.getKey(), (Long) entry.getValue());
        }).limit(10L).toList());
    }

    public static record Summary(long totalBytes, double bytesPerSecond, long counts, double countsPerSecond, Duration timeSpentInIO, List<Pair<String, Long>> topTenContributorsByTotalBytes) {

    }
}
