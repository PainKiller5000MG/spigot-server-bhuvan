package net.minecraft.util.profiling;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.ReportType;
import net.minecraft.SharedConstants;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;

public class FilledProfileResults implements ProfileResults {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ProfilerPathEntry EMPTY = new ProfilerPathEntry() {
        @Override
        public long getDuration() {
            return 0L;
        }

        @Override
        public long getMaxDuration() {
            return 0L;
        }

        @Override
        public long getCount() {
            return 0L;
        }

        @Override
        public Object2LongMap<String> getCounters() {
            return Object2LongMaps.emptyMap();
        }
    };
    private static final Splitter SPLITTER = Splitter.on('\u001e');
    private static final Comparator<Map.Entry<String, FilledProfileResults.CounterCollector>> COUNTER_ENTRY_COMPARATOR = Entry.comparingByValue(Comparator.comparingLong((filledprofileresults_countercollector) -> {
        return filledprofileresults_countercollector.totalValue;
    })).reversed();
    private final Map<String, ? extends ProfilerPathEntry> entries;
    private final long startTimeNano;
    private final int startTimeTicks;
    private final long endTimeNano;
    private final int endTimeTicks;
    private final int tickDuration;

    public FilledProfileResults(Map<String, ? extends ProfilerPathEntry> entries, long startTimeNano, int startTimeTicks, long endTimeNano, int endTimeTicks) {
        this.entries = entries;
        this.startTimeNano = startTimeNano;
        this.startTimeTicks = startTimeTicks;
        this.endTimeNano = endTimeNano;
        this.endTimeTicks = endTimeTicks;
        this.tickDuration = endTimeTicks - startTimeTicks;
    }

    private ProfilerPathEntry getEntry(String path) {
        ProfilerPathEntry profilerpathentry = (ProfilerPathEntry) this.entries.get(path);

        return profilerpathentry != null ? profilerpathentry : FilledProfileResults.EMPTY;
    }

    @Override
    public List<ResultField> getTimes(String path) {
        String s1 = path;
        ProfilerPathEntry profilerpathentry = this.getEntry("root");
        long i = profilerpathentry.getDuration();
        ProfilerPathEntry profilerpathentry1 = this.getEntry(path);
        long j = profilerpathentry1.getDuration();
        long k = profilerpathentry1.getCount();
        List<ResultField> list = Lists.newArrayList();

        if (!path.isEmpty()) {
            path = path + "\u001e";
        }

        long l = 0L;

        for (String s2 : this.entries.keySet()) {
            if (isDirectChild(path, s2)) {
                l += this.getEntry(s2).getDuration();
            }
        }

        float f = (float) l;

        if (l < j) {
            l = j;
        }

        if (i < l) {
            i = l;
        }

        for (String s3 : this.entries.keySet()) {
            if (isDirectChild(path, s3)) {
                ProfilerPathEntry profilerpathentry2 = this.getEntry(s3);
                long i1 = profilerpathentry2.getDuration();
                double d0 = (double) i1 * 100.0D / (double) l;
                double d1 = (double) i1 * 100.0D / (double) i;
                String s4 = s3.substring(path.length());

                list.add(new ResultField(s4, d0, d1, profilerpathentry2.getCount()));
            }
        }

        if ((float) l > f) {
            list.add(new ResultField("unspecified", (double) ((float) l - f) * 100.0D / (double) l, (double) ((float) l - f) * 100.0D / (double) i, k));
        }

        Collections.sort(list);
        list.add(0, new ResultField(s1, 100.0D, (double) l * 100.0D / (double) i, k));
        return list;
    }

    private static boolean isDirectChild(String path, String test) {
        return test.length() > path.length() && test.startsWith(path) && test.indexOf(30, path.length() + 1) < 0;
    }

    private Map<String, FilledProfileResults.CounterCollector> getCounterValues() {
        Map<String, FilledProfileResults.CounterCollector> map = Maps.newTreeMap();

        this.entries.forEach((s, profilerpathentry) -> {
            Object2LongMap<String> object2longmap = profilerpathentry.getCounters();

            if (!object2longmap.isEmpty()) {
                List<String> list = FilledProfileResults.SPLITTER.splitToList(s);

                object2longmap.forEach((s1, i) -> {
                    ((FilledProfileResults.CounterCollector) map.computeIfAbsent(s1, (s2) -> {
                        return new FilledProfileResults.CounterCollector();
                    })).addValue(list.iterator(), i);
                });
            }

        });
        return map;
    }

    @Override
    public long getStartTimeNano() {
        return this.startTimeNano;
    }

    @Override
    public int getStartTimeTicks() {
        return this.startTimeTicks;
    }

    @Override
    public long getEndTimeNano() {
        return this.endTimeNano;
    }

    @Override
    public int getEndTimeTicks() {
        return this.endTimeTicks;
    }

    @Override
    public boolean saveResults(Path file) {
        Writer writer = null;

        boolean flag;

        try {
            Files.createDirectories(file.getParent());
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            writer.write(this.getProfilerResults(this.getNanoDuration(), this.getTickDuration()));
            boolean flag1 = true;

            return flag1;
        } catch (Throwable throwable) {
            FilledProfileResults.LOGGER.error("Could not save profiler results to {}", file, throwable);
            flag = false;
        } finally {
            IOUtils.closeQuietly(writer);
        }

        return flag;
    }

    protected String getProfilerResults(long timespan, int tickspan) {
        StringBuilder stringbuilder = new StringBuilder();

        ReportType.PROFILE.appendHeader(stringbuilder, List.of());
        stringbuilder.append("Version: ").append(SharedConstants.getCurrentVersion().id()).append('\n');
        stringbuilder.append("Time span: ").append(timespan / 1000000L).append(" ms\n");
        stringbuilder.append("Tick span: ").append(tickspan).append(" ticks\n");
        stringbuilder.append("// This is approximately ").append(String.format(Locale.ROOT, "%.2f", (float) tickspan / ((float) timespan / 1.0E9F))).append(" ticks per second. It should be ").append(20).append(" ticks per second\n\n");
        stringbuilder.append("--- BEGIN PROFILE DUMP ---\n\n");
        this.appendProfilerResults(0, "root", stringbuilder);
        stringbuilder.append("--- END PROFILE DUMP ---\n\n");
        Map<String, FilledProfileResults.CounterCollector> map = this.getCounterValues();

        if (!map.isEmpty()) {
            stringbuilder.append("--- BEGIN COUNTER DUMP ---\n\n");
            this.appendCounters(map, stringbuilder, tickspan);
            stringbuilder.append("--- END COUNTER DUMP ---\n\n");
        }

        return stringbuilder.toString();
    }

    @Override
    public String getProfilerResults() {
        StringBuilder stringbuilder = new StringBuilder();

        this.appendProfilerResults(0, "root", stringbuilder);
        return stringbuilder.toString();
    }

    private static StringBuilder indentLine(StringBuilder builder, int depth) {
        builder.append(String.format(Locale.ROOT, "[%02d] ", depth));

        for (int j = 0; j < depth; ++j) {
            builder.append("|   ");
        }

        return builder;
    }

    private void appendProfilerResults(int depth, String path, StringBuilder builder) {
        List<ResultField> list = this.getTimes(path);
        Object2LongMap<String> object2longmap = ((ProfilerPathEntry) ObjectUtils.firstNonNull(new ProfilerPathEntry[]{(ProfilerPathEntry) this.entries.get(path), FilledProfileResults.EMPTY})).getCounters();

        object2longmap.forEach((s1, j) -> {
            indentLine(builder, depth).append('#').append(s1).append(' ').append(j).append('/').append(j / (long) this.tickDuration).append('\n');
        });
        if (list.size() >= 3) {
            for (int j = 1; j < list.size(); ++j) {
                ResultField resultfield = (ResultField) list.get(j);

                indentLine(builder, depth).append(resultfield.name).append('(').append(resultfield.count).append('/').append(String.format(Locale.ROOT, "%.0f", (float) resultfield.count / (float) this.tickDuration)).append(')').append(" - ").append(String.format(Locale.ROOT, "%.2f", resultfield.percentage)).append("%/").append(String.format(Locale.ROOT, "%.2f", resultfield.globalPercentage)).append("%\n");
                if (!"unspecified".equals(resultfield.name)) {
                    try {
                        this.appendProfilerResults(depth + 1, path + "\u001e" + resultfield.name, builder);
                    } catch (Exception exception) {
                        builder.append("[[ EXCEPTION ").append(exception).append(" ]]");
                    }
                }
            }

        }
    }

    private void appendCounterResults(int depth, String name, FilledProfileResults.CounterCollector result, int tickspan, StringBuilder builder) {
        indentLine(builder, depth).append(name).append(" total:").append(result.selfValue).append('/').append(result.totalValue).append(" average: ").append(result.selfValue / (long) tickspan).append('/').append(result.totalValue / (long) tickspan).append('\n');
        result.children.entrySet().stream().sorted(FilledProfileResults.COUNTER_ENTRY_COMPARATOR).forEach((entry) -> {
            this.appendCounterResults(depth + 1, (String) entry.getKey(), (FilledProfileResults.CounterCollector) entry.getValue(), tickspan, builder);
        });
    }

    private void appendCounters(Map<String, FilledProfileResults.CounterCollector> counters, StringBuilder builder, int tickspan) {
        counters.forEach((s, filledprofileresults_countercollector) -> {
            builder.append("-- Counter: ").append(s).append(" --\n");
            this.appendCounterResults(0, "root", (FilledProfileResults.CounterCollector) filledprofileresults_countercollector.children.get("root"), tickspan, builder);
            builder.append("\n\n");
        });
    }

    @Override
    public int getTickDuration() {
        return this.tickDuration;
    }

    private static class CounterCollector {

        private long selfValue;
        private long totalValue;
        private final Map<String, FilledProfileResults.CounterCollector> children = Maps.newHashMap();

        private CounterCollector() {}

        public void addValue(Iterator<String> path, long value) {
            this.totalValue += value;
            if (!path.hasNext()) {
                this.selfValue += value;
            } else {
                ((FilledProfileResults.CounterCollector) this.children.computeIfAbsent((String) path.next(), (s) -> {
                    return new FilledProfileResults.CounterCollector();
                })).addValue(path, value);
            }

        }
    }
}
