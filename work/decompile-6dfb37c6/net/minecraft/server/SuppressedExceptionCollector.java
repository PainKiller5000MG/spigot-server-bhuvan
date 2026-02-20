package net.minecraft.server;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Queue;
import net.minecraft.util.ArrayListDeque;

public class SuppressedExceptionCollector {

    private static final int LATEST_ENTRY_COUNT = 8;
    private final Queue<SuppressedExceptionCollector.LongEntry> latestEntries = new ArrayListDeque<SuppressedExceptionCollector.LongEntry>();
    private final Object2IntLinkedOpenHashMap<SuppressedExceptionCollector.ShortEntry> entryCounts = new Object2IntLinkedOpenHashMap();

    public SuppressedExceptionCollector() {}

    private static long currentTimeMs() {
        return System.currentTimeMillis();
    }

    public synchronized void addEntry(String location, Throwable throwable) {
        long i = currentTimeMs();
        String s1 = throwable.getMessage();

        this.latestEntries.add(new SuppressedExceptionCollector.LongEntry(i, location, throwable.getClass(), s1));

        while (this.latestEntries.size() > 8) {
            this.latestEntries.remove();
        }

        SuppressedExceptionCollector.ShortEntry suppressedexceptioncollector_shortentry = new SuppressedExceptionCollector.ShortEntry(location, throwable.getClass());
        int j = this.entryCounts.getInt(suppressedexceptioncollector_shortentry);

        this.entryCounts.putAndMoveToFirst(suppressedexceptioncollector_shortentry, j + 1);
    }

    public synchronized String dump() {
        long i = currentTimeMs();
        StringBuilder stringbuilder = new StringBuilder();

        if (!this.latestEntries.isEmpty()) {
            stringbuilder.append("\n\t\tLatest entries:\n");

            for (SuppressedExceptionCollector.LongEntry suppressedexceptioncollector_longentry : this.latestEntries) {
                stringbuilder.append("\t\t\t").append(suppressedexceptioncollector_longentry.location).append(":").append(suppressedexceptioncollector_longentry.cls).append(": ").append(suppressedexceptioncollector_longentry.message).append(" (").append(i - suppressedexceptioncollector_longentry.timestampMs).append("ms ago)").append("\n");
            }
        }

        if (!this.entryCounts.isEmpty()) {
            if (stringbuilder.isEmpty()) {
                stringbuilder.append("\n");
            }

            stringbuilder.append("\t\tEntry counts:\n");
            ObjectIterator objectiterator = Object2IntMaps.fastIterable(this.entryCounts).iterator();

            while (objectiterator.hasNext()) {
                Object2IntMap.Entry<SuppressedExceptionCollector.ShortEntry> object2intmap_entry = (Entry) objectiterator.next();

                stringbuilder.append("\t\t\t").append(((SuppressedExceptionCollector.ShortEntry) object2intmap_entry.getKey()).location).append(":").append(((SuppressedExceptionCollector.ShortEntry) object2intmap_entry.getKey()).cls).append(" x ").append(object2intmap_entry.getIntValue()).append("\n");
            }
        }

        return stringbuilder.isEmpty() ? "~~NONE~~" : stringbuilder.toString();
    }

    private static record LongEntry(long timestampMs, String location, Class<? extends Throwable> cls, String message) {

    }

    private static record ShortEntry(String location, Class<? extends Throwable> cls) {

    }
}
