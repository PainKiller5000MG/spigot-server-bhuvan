package net.minecraft.util.profiling.jfr.parse;

import com.mojang.datafixers.util.Pair;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.util.profiling.jfr.stats.ChunkGenStat;
import net.minecraft.util.profiling.jfr.stats.ChunkIdentification;
import net.minecraft.util.profiling.jfr.stats.CpuLoadStat;
import net.minecraft.util.profiling.jfr.stats.FileIOStat;
import net.minecraft.util.profiling.jfr.stats.FpsStat;
import net.minecraft.util.profiling.jfr.stats.GcHeapStat;
import net.minecraft.util.profiling.jfr.stats.IoSummary;
import net.minecraft.util.profiling.jfr.stats.PacketIdentification;
import net.minecraft.util.profiling.jfr.stats.StructureGenStat;
import net.minecraft.util.profiling.jfr.stats.ThreadAllocationStat;
import net.minecraft.util.profiling.jfr.stats.TickTimeStat;
import org.jspecify.annotations.Nullable;

public class JfrStatsParser {

    private Instant recordingStarted;
    private Instant recordingEnded;
    private final List<ChunkGenStat> chunkGenStats;
    private final List<StructureGenStat> structureGenStats;
    private final List<CpuLoadStat> cpuLoadStat;
    private final Map<PacketIdentification, JfrStatsParser.MutableCountAndSize> receivedPackets;
    private final Map<PacketIdentification, JfrStatsParser.MutableCountAndSize> sentPackets;
    private final Map<ChunkIdentification, JfrStatsParser.MutableCountAndSize> readChunks;
    private final Map<ChunkIdentification, JfrStatsParser.MutableCountAndSize> writtenChunks;
    private final List<FileIOStat> fileWrites;
    private final List<FileIOStat> fileReads;
    private int garbageCollections;
    private Duration gcTotalDuration;
    private final List<GcHeapStat> gcHeapStats;
    private final List<ThreadAllocationStat> threadAllocationStats;
    private final List<FpsStat> fps;
    private final List<TickTimeStat> serverTickTimes;
    private @Nullable Duration worldCreationDuration;

    private JfrStatsParser(Stream<RecordedEvent> events) {
        this.recordingStarted = Instant.EPOCH;
        this.recordingEnded = Instant.EPOCH;
        this.chunkGenStats = new ArrayList();
        this.structureGenStats = new ArrayList();
        this.cpuLoadStat = new ArrayList();
        this.receivedPackets = new HashMap();
        this.sentPackets = new HashMap();
        this.readChunks = new HashMap();
        this.writtenChunks = new HashMap();
        this.fileWrites = new ArrayList();
        this.fileReads = new ArrayList();
        this.gcTotalDuration = Duration.ZERO;
        this.gcHeapStats = new ArrayList();
        this.threadAllocationStats = new ArrayList();
        this.fps = new ArrayList();
        this.serverTickTimes = new ArrayList();
        this.worldCreationDuration = null;
        this.capture(events);
    }

    public static JfrStatsResult parse(Path path) {
        try (final RecordingFile recordingfile = new RecordingFile(path)) {
            Iterator<RecordedEvent> iterator = new Iterator<RecordedEvent>() {
                public boolean hasNext() {
                    return recordingfile.hasMoreEvents();
                }

                public RecordedEvent next() {
                    if (!this.hasNext()) {
                        throw new NoSuchElementException();
                    } else {
                        try {
                            return recordingfile.readEvent();
                        } catch (IOException ioexception) {
                            throw new UncheckedIOException(ioexception);
                        }
                    }
                }
            };
            Stream<RecordedEvent> stream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 1297), false);

            return (new JfrStatsParser(stream)).results();
        } catch (IOException ioexception) {
            throw new UncheckedIOException(ioexception);
        }
    }

    private JfrStatsResult results() {
        Duration duration = Duration.between(this.recordingStarted, this.recordingEnded);

        return new JfrStatsResult(this.recordingStarted, this.recordingEnded, duration, this.worldCreationDuration, this.fps, this.serverTickTimes, this.cpuLoadStat, GcHeapStat.summary(duration, this.gcHeapStats, this.gcTotalDuration, this.garbageCollections), ThreadAllocationStat.summary(this.threadAllocationStats), collectIoStats(duration, this.receivedPackets), collectIoStats(duration, this.sentPackets), collectIoStats(duration, this.writtenChunks), collectIoStats(duration, this.readChunks), FileIOStat.summary(duration, this.fileWrites), FileIOStat.summary(duration, this.fileReads), this.chunkGenStats, this.structureGenStats);
    }

    private void capture(Stream<RecordedEvent> events) {
        events.forEach((recordedevent) -> {
            if (recordedevent.getEndTime().isAfter(this.recordingEnded) || this.recordingEnded.equals(Instant.EPOCH)) {
                this.recordingEnded = recordedevent.getEndTime();
            }

            if (recordedevent.getStartTime().isBefore(this.recordingStarted) || this.recordingStarted.equals(Instant.EPOCH)) {
                this.recordingStarted = recordedevent.getStartTime();
            }

            switch (recordedevent.getEventType().getName()) {
                case "minecraft.ChunkGeneration":
                    this.chunkGenStats.add(ChunkGenStat.from(recordedevent));
                    break;
                case "minecraft.StructureGeneration":
                    this.structureGenStats.add(StructureGenStat.from(recordedevent));
                    break;
                case "minecraft.LoadWorld":
                    this.worldCreationDuration = recordedevent.getDuration();
                    break;
                case "minecraft.ClientFps":
                    this.fps.add(FpsStat.from(recordedevent, "fps"));
                    break;
                case "minecraft.ServerTickTime":
                    this.serverTickTimes.add(TickTimeStat.from(recordedevent));
                    break;
                case "minecraft.PacketReceived":
                    this.incrementPacket(recordedevent, recordedevent.getInt("bytes"), this.receivedPackets);
                    break;
                case "minecraft.PacketSent":
                    this.incrementPacket(recordedevent, recordedevent.getInt("bytes"), this.sentPackets);
                    break;
                case "minecraft.ChunkRegionRead":
                    this.incrementChunk(recordedevent, recordedevent.getInt("bytes"), this.readChunks);
                    break;
                case "minecraft.ChunkRegionWrite":
                    this.incrementChunk(recordedevent, recordedevent.getInt("bytes"), this.writtenChunks);
                    break;
                case "jdk.ThreadAllocationStatistics":
                    this.threadAllocationStats.add(ThreadAllocationStat.from(recordedevent));
                    break;
                case "jdk.GCHeapSummary":
                    this.gcHeapStats.add(GcHeapStat.from(recordedevent));
                    break;
                case "jdk.CPULoad":
                    this.cpuLoadStat.add(CpuLoadStat.from(recordedevent));
                    break;
                case "jdk.FileWrite":
                    this.appendFileIO(recordedevent, this.fileWrites, "bytesWritten");
                    break;
                case "jdk.FileRead":
                    this.appendFileIO(recordedevent, this.fileReads, "bytesRead");
                    break;
                case "jdk.GarbageCollection":
                    ++this.garbageCollections;
                    this.gcTotalDuration = this.gcTotalDuration.plus(recordedevent.getDuration());
            }

        });
    }

    private void incrementPacket(RecordedEvent event, int packetSize, Map<PacketIdentification, JfrStatsParser.MutableCountAndSize> packets) {
        ((JfrStatsParser.MutableCountAndSize) packets.computeIfAbsent(PacketIdentification.from(event), (packetidentification) -> {
            return new JfrStatsParser.MutableCountAndSize();
        })).increment(packetSize);
    }

    private void incrementChunk(RecordedEvent event, int chunkSize, Map<ChunkIdentification, JfrStatsParser.MutableCountAndSize> packets) {
        ((JfrStatsParser.MutableCountAndSize) packets.computeIfAbsent(ChunkIdentification.from(event), (chunkidentification) -> {
            return new JfrStatsParser.MutableCountAndSize();
        })).increment(chunkSize);
    }

    private void appendFileIO(RecordedEvent event, List<FileIOStat> stats, String sizeField) {
        stats.add(new FileIOStat(event.getDuration(), event.getString("path"), event.getLong(sizeField)));
    }

    private static <T> IoSummary<T> collectIoStats(Duration recordingDuration, Map<T, JfrStatsParser.MutableCountAndSize> packetStats) {
        List<Pair<T, IoSummary.CountAndSize>> list = packetStats.entrySet().stream().map((entry) -> {
            return Pair.of(entry.getKey(), ((JfrStatsParser.MutableCountAndSize) entry.getValue()).toCountAndSize());
        }).toList();

        return new IoSummary<T>(recordingDuration, list);
    }

    public static final class MutableCountAndSize {

        private long count;
        private long totalSize;

        public MutableCountAndSize() {}

        public void increment(int bytes) {
            this.totalSize += (long) bytes;
            ++this.count;
        }

        public IoSummary.CountAndSize toCountAndSize() {
            return new IoSummary.CountAndSize(this.count, this.totalSize);
        }
    }
}
