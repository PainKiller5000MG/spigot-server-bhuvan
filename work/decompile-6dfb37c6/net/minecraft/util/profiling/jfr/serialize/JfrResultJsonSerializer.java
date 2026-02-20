package net.minecraft.util.profiling.jfr.serialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.LongSerializationPolicy;
import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.jfr.Percentiles;
import net.minecraft.util.profiling.jfr.parse.JfrStatsResult;
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
import net.minecraft.util.profiling.jfr.stats.TimedStatSummary;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class JfrResultJsonSerializer {

    private static final String BYTES_PER_SECOND = "bytesPerSecond";
    private static final String COUNT = "count";
    private static final String DURATION_NANOS_TOTAL = "durationNanosTotal";
    private static final String TOTAL_BYTES = "totalBytes";
    private static final String COUNT_PER_SECOND = "countPerSecond";
    final Gson gson;

    public JfrResultJsonSerializer() {
        this.gson = (new GsonBuilder()).setPrettyPrinting().setLongSerializationPolicy(LongSerializationPolicy.DEFAULT).create();
    }

    private static void serializePacketId(PacketIdentification identifier, JsonObject output) {
        output.addProperty("protocolId", identifier.protocolId());
        output.addProperty("packetId", identifier.packetId());
    }

    private static void serializeChunkId(ChunkIdentification identifier, JsonObject output) {
        output.addProperty("level", identifier.level());
        output.addProperty("dimension", identifier.dimension());
        output.addProperty("x", identifier.x());
        output.addProperty("z", identifier.z());
    }

    public String format(JfrStatsResult jfrStats) {
        JsonObject jsonobject = new JsonObject();

        jsonobject.addProperty("startedEpoch", jfrStats.recordingStarted().toEpochMilli());
        jsonobject.addProperty("endedEpoch", jfrStats.recordingEnded().toEpochMilli());
        jsonobject.addProperty("durationMs", jfrStats.recordingDuration().toMillis());
        Duration duration = jfrStats.worldCreationDuration();

        if (duration != null) {
            jsonobject.addProperty("worldGenDurationMs", duration.toMillis());
        }

        jsonobject.add("heap", this.heap(jfrStats.heapSummary()));
        jsonobject.add("cpuPercent", this.cpu(jfrStats.cpuLoadStats()));
        jsonobject.add("network", this.network(jfrStats));
        jsonobject.add("fileIO", this.fileIO(jfrStats));
        jsonobject.add("fps", this.fps(jfrStats.fps()));
        jsonobject.add("serverTick", this.serverTicks(jfrStats.serverTickTimes()));
        jsonobject.add("threadAllocation", this.threadAllocations(jfrStats.threadAllocationSummary()));
        jsonobject.add("chunkGen", this.chunkGen(jfrStats.chunkGenSummary()));
        jsonobject.add("structureGen", this.structureGen(jfrStats.structureGenStats()));
        return this.gson.toJson(jsonobject);
    }

    private JsonElement heap(GcHeapStat.Summary heapSummary) {
        JsonObject jsonobject = new JsonObject();

        jsonobject.addProperty("allocationRateBytesPerSecond", heapSummary.allocationRateBytesPerSecond());
        jsonobject.addProperty("gcCount", heapSummary.totalGCs());
        jsonobject.addProperty("gcOverHeadPercent", heapSummary.gcOverHead());
        jsonobject.addProperty("gcTotalDurationMs", heapSummary.gcTotalDuration().toMillis());
        return jsonobject;
    }

    private JsonElement structureGen(List<StructureGenStat> structureGenStats) {
        JsonObject jsonobject = new JsonObject();
        Optional<TimedStatSummary<StructureGenStat>> optional = TimedStatSummary.summary(structureGenStats);

        if (optional.isEmpty()) {
            return jsonobject;
        } else {
            TimedStatSummary<StructureGenStat> timedstatsummary = (TimedStatSummary) optional.get();
            JsonArray jsonarray = new JsonArray();

            jsonobject.add("structure", jsonarray);
            ((Map) structureGenStats.stream().collect(Collectors.groupingBy(StructureGenStat::structureName))).forEach((s, list1) -> {
                Optional<TimedStatSummary<StructureGenStat>> optional1 = TimedStatSummary.summary(list1);

                if (!optional1.isEmpty()) {
                    TimedStatSummary<StructureGenStat> timedstatsummary1 = (TimedStatSummary) optional1.get();
                    JsonObject jsonobject1 = new JsonObject();

                    jsonarray.add(jsonobject1);
                    jsonobject1.addProperty("name", s);
                    jsonobject1.addProperty("count", timedstatsummary1.count());
                    jsonobject1.addProperty("durationNanosTotal", timedstatsummary1.totalDuration().toNanos());
                    jsonobject1.addProperty("durationNanosAvg", timedstatsummary1.totalDuration().toNanos() / (long) timedstatsummary1.count());
                    JsonObject jsonobject2 = (JsonObject) Util.make(new JsonObject(), (jsonobject3) -> {
                        jsonobject1.add("durationNanosPercentiles", jsonobject3);
                    });

                    timedstatsummary1.percentilesNanos().forEach((integer, odouble) -> {
                        jsonobject2.addProperty("p" + integer, odouble);
                    });
                    Function<StructureGenStat, JsonElement> function = (structuregenstat) -> {
                        JsonObject jsonobject3 = new JsonObject();

                        jsonobject3.addProperty("durationNanos", structuregenstat.duration().toNanos());
                        jsonobject3.addProperty("chunkPosX", structuregenstat.chunkPos().x);
                        jsonobject3.addProperty("chunkPosZ", structuregenstat.chunkPos().z);
                        jsonobject3.addProperty("structureName", structuregenstat.structureName());
                        jsonobject3.addProperty("level", structuregenstat.level());
                        jsonobject3.addProperty("success", structuregenstat.success());
                        return jsonobject3;
                    };

                    jsonobject.add("fastest", (JsonElement) function.apply(timedstatsummary.fastest()));
                    jsonobject.add("slowest", (JsonElement) function.apply(timedstatsummary.slowest()));
                    jsonobject.add("secondSlowest", (JsonElement) (timedstatsummary.secondSlowest() != null ? (JsonElement) function.apply(timedstatsummary.secondSlowest()) : JsonNull.INSTANCE));
                }
            });
            return jsonobject;
        }
    }

    private JsonElement chunkGen(List<Pair<ChunkStatus, TimedStatSummary<ChunkGenStat>>> chunkGenSummary) {
        JsonObject jsonobject = new JsonObject();

        if (chunkGenSummary.isEmpty()) {
            return jsonobject;
        } else {
            jsonobject.addProperty("durationNanosTotal", chunkGenSummary.stream().mapToDouble((pair) -> {
                return (double) ((TimedStatSummary) pair.getSecond()).totalDuration().toNanos();
            }).sum());
            JsonArray jsonarray = (JsonArray) Util.make(new JsonArray(), (jsonarray1) -> {
                jsonobject.add("status", jsonarray1);
            });

            for (Pair<ChunkStatus, TimedStatSummary<ChunkGenStat>> pair : chunkGenSummary) {
                TimedStatSummary<ChunkGenStat> timedstatsummary = (TimedStatSummary) pair.getSecond();
                JsonObject jsonobject1 = new JsonObject();

                Objects.requireNonNull(jsonarray);
                JsonObject jsonobject2 = (JsonObject) Util.make(jsonobject1, jsonarray::add);

                jsonobject2.addProperty("state", ((ChunkStatus) pair.getFirst()).toString());
                jsonobject2.addProperty("count", timedstatsummary.count());
                jsonobject2.addProperty("durationNanosTotal", timedstatsummary.totalDuration().toNanos());
                jsonobject2.addProperty("durationNanosAvg", timedstatsummary.totalDuration().toNanos() / (long) timedstatsummary.count());
                JsonObject jsonobject3 = (JsonObject) Util.make(new JsonObject(), (jsonobject4) -> {
                    jsonobject2.add("durationNanosPercentiles", jsonobject4);
                });

                timedstatsummary.percentilesNanos().forEach((integer, odouble) -> {
                    jsonobject3.addProperty("p" + integer, odouble);
                });
                Function<ChunkGenStat, JsonElement> function = (chunkgenstat) -> {
                    JsonObject jsonobject4 = new JsonObject();

                    jsonobject4.addProperty("durationNanos", chunkgenstat.duration().toNanos());
                    jsonobject4.addProperty("level", chunkgenstat.level());
                    jsonobject4.addProperty("chunkPosX", chunkgenstat.chunkPos().x);
                    jsonobject4.addProperty("chunkPosZ", chunkgenstat.chunkPos().z);
                    jsonobject4.addProperty("worldPosX", chunkgenstat.worldPos().x());
                    jsonobject4.addProperty("worldPosZ", chunkgenstat.worldPos().z());
                    return jsonobject4;
                };

                jsonobject2.add("fastest", (JsonElement) function.apply(timedstatsummary.fastest()));
                jsonobject2.add("slowest", (JsonElement) function.apply(timedstatsummary.slowest()));
                jsonobject2.add("secondSlowest", (JsonElement) (timedstatsummary.secondSlowest() != null ? (JsonElement) function.apply(timedstatsummary.secondSlowest()) : JsonNull.INSTANCE));
            }

            return jsonobject;
        }
    }

    private JsonElement threadAllocations(ThreadAllocationStat.Summary threadAllocationSummary) {
        JsonArray jsonarray = new JsonArray();

        threadAllocationSummary.allocationsPerSecondByThread().forEach((s, odouble) -> {
            jsonarray.add((JsonElement) Util.make(new JsonObject(), (jsonobject) -> {
                jsonobject.addProperty("thread", s);
                jsonobject.addProperty("bytesPerSecond", odouble);
            }));
        });
        return jsonarray;
    }

    private JsonElement serverTicks(List<TickTimeStat> tickTimeStats) {
        if (tickTimeStats.isEmpty()) {
            return JsonNull.INSTANCE;
        } else {
            JsonObject jsonobject = new JsonObject();
            double[] adouble = tickTimeStats.stream().mapToDouble((ticktimestat) -> {
                return (double) ticktimestat.currentAverage().toNanos() / 1000000.0D;
            }).toArray();
            DoubleSummaryStatistics doublesummarystatistics = DoubleStream.of(adouble).summaryStatistics();

            jsonobject.addProperty("minMs", doublesummarystatistics.getMin());
            jsonobject.addProperty("averageMs", doublesummarystatistics.getAverage());
            jsonobject.addProperty("maxMs", doublesummarystatistics.getMax());
            Map<Integer, Double> map = Percentiles.evaluate(adouble);

            map.forEach((integer, odouble) -> {
                jsonobject.addProperty("p" + integer, odouble);
            });
            return jsonobject;
        }
    }

    private JsonElement fps(List<FpsStat> fpsStats) {
        if (fpsStats.isEmpty()) {
            return JsonNull.INSTANCE;
        } else {
            JsonObject jsonobject = new JsonObject();
            int[] aint = fpsStats.stream().mapToInt(FpsStat::fps).toArray();
            IntSummaryStatistics intsummarystatistics = IntStream.of(aint).summaryStatistics();

            jsonobject.addProperty("minFPS", intsummarystatistics.getMin());
            jsonobject.addProperty("averageFPS", intsummarystatistics.getAverage());
            jsonobject.addProperty("maxFPS", intsummarystatistics.getMax());
            Map<Integer, Double> map = Percentiles.evaluate(aint);

            map.forEach((integer, odouble) -> {
                jsonobject.addProperty("p" + integer, odouble);
            });
            return jsonobject;
        }
    }

    private JsonElement fileIO(JfrStatsResult jfrStats) {
        JsonObject jsonobject = new JsonObject();

        jsonobject.add("write", this.fileIoSummary(jfrStats.fileWrites()));
        jsonobject.add("read", this.fileIoSummary(jfrStats.fileReads()));
        jsonobject.add("chunksRead", this.ioSummary(jfrStats.readChunks(), JfrResultJsonSerializer::serializeChunkId));
        jsonobject.add("chunksWritten", this.ioSummary(jfrStats.writtenChunks(), JfrResultJsonSerializer::serializeChunkId));
        return jsonobject;
    }

    private JsonElement fileIoSummary(FileIOStat.Summary io) {
        JsonObject jsonobject = new JsonObject();

        jsonobject.addProperty("totalBytes", io.totalBytes());
        jsonobject.addProperty("count", io.counts());
        jsonobject.addProperty("bytesPerSecond", io.bytesPerSecond());
        jsonobject.addProperty("countPerSecond", io.countsPerSecond());
        JsonArray jsonarray = new JsonArray();

        jsonobject.add("topContributors", jsonarray);
        io.topTenContributorsByTotalBytes().forEach((pair) -> {
            JsonObject jsonobject1 = new JsonObject();

            jsonarray.add(jsonobject1);
            jsonobject1.addProperty("path", (String) pair.getFirst());
            jsonobject1.addProperty("totalBytes", (Number) pair.getSecond());
        });
        return jsonobject;
    }

    private JsonElement network(JfrStatsResult jfrStats) {
        JsonObject jsonobject = new JsonObject();

        jsonobject.add("sent", this.ioSummary(jfrStats.sentPacketsSummary(), JfrResultJsonSerializer::serializePacketId));
        jsonobject.add("received", this.ioSummary(jfrStats.receivedPacketsSummary(), JfrResultJsonSerializer::serializePacketId));
        return jsonobject;
    }

    private <T> JsonElement ioSummary(IoSummary<T> summary, BiConsumer<T, JsonObject> elementWriter) {
        JsonObject jsonobject = new JsonObject();

        jsonobject.addProperty("totalBytes", summary.getTotalSize());
        jsonobject.addProperty("count", summary.getTotalCount());
        jsonobject.addProperty("bytesPerSecond", summary.getSizePerSecond());
        jsonobject.addProperty("countPerSecond", summary.getCountsPerSecond());
        JsonArray jsonarray = new JsonArray();

        jsonobject.add("topContributors", jsonarray);
        summary.largestSizeContributors().forEach((pair) -> {
            JsonObject jsonobject1 = new JsonObject();

            jsonarray.add(jsonobject1);
            T t0 = (T) pair.getFirst();
            IoSummary.CountAndSize iosummary_countandsize = (IoSummary.CountAndSize) pair.getSecond();

            elementWriter.accept(t0, jsonobject1);
            jsonobject1.addProperty("totalBytes", iosummary_countandsize.totalSize());
            jsonobject1.addProperty("count", iosummary_countandsize.totalCount());
            jsonobject1.addProperty("averageSize", iosummary_countandsize.averageSize());
        });
        return jsonobject;
    }

    private JsonElement cpu(List<CpuLoadStat> cpuStats) {
        JsonObject jsonobject = new JsonObject();
        BiFunction<List<CpuLoadStat>, ToDoubleFunction<CpuLoadStat>, JsonObject> bifunction = (list1, todoublefunction) -> {
            JsonObject jsonobject1 = new JsonObject();
            DoubleSummaryStatistics doublesummarystatistics = list1.stream().mapToDouble(todoublefunction).summaryStatistics();

            jsonobject1.addProperty("min", doublesummarystatistics.getMin());
            jsonobject1.addProperty("average", doublesummarystatistics.getAverage());
            jsonobject1.addProperty("max", doublesummarystatistics.getMax());
            return jsonobject1;
        };

        jsonobject.add("jvm", (JsonElement) bifunction.apply(cpuStats, CpuLoadStat::jvm));
        jsonobject.add("userJvm", (JsonElement) bifunction.apply(cpuStats, CpuLoadStat::userJvm));
        jsonobject.add("system", (JsonElement) bifunction.apply(cpuStats, CpuLoadStat::system));
        return jsonobject;
    }
}
