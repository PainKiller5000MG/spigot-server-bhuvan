package net.minecraft.util.profiling.jfr.stats;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.profiling.jfr.Percentiles;
import org.jspecify.annotations.Nullable;

public record TimedStatSummary<T extends TimedStat>(T fastest, T slowest, @Nullable T secondSlowest, int count, Map<Integer, Double> percentilesNanos, Duration totalDuration) {

    public static <T extends TimedStat> Optional<TimedStatSummary<T>> summary(List<T> values) {
        if (values.isEmpty()) {
            return Optional.empty();
        } else {
            List<T> list1 = values.stream().sorted(Comparator.comparing(TimedStat::duration)).toList();
            Duration duration = (Duration) list1.stream().map(TimedStat::duration).reduce(Duration::plus).orElse(Duration.ZERO);
            T t0 = (T) (list1.getFirst());
            T t1 = (T) (list1.getLast());
            T t2 = list1.size() > 1 ? (TimedStat) list1.get(list1.size() - 2) : null;
            int i = list1.size();
            Map<Integer, Double> map = Percentiles.evaluate(list1.stream().mapToLong((timedstat) -> {
                return timedstat.duration().toNanos();
            }).toArray());

            return Optional.of(new TimedStatSummary(t0, t1, t2, i, map, duration));
        }
    }
}
