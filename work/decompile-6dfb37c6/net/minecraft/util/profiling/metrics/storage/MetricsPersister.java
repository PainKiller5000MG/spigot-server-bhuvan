package net.minecraft.util.profiling.metrics.storage;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.util.profiling.metrics.MetricSampler;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class MetricsPersister {

    public static final Path PROFILING_RESULTS_DIR = Paths.get("debug/profiling");
    public static final String METRICS_DIR_NAME = "metrics";
    public static final String DEVIATIONS_DIR_NAME = "deviations";
    public static final String PROFILING_RESULT_FILENAME = "profiling.txt";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final String rootFolderName;

    public MetricsPersister(String rootFolderName) {
        this.rootFolderName = rootFolderName;
    }

    public Path saveReports(Set<MetricSampler> samplers, Map<MetricSampler, List<RecordedDeviation>> deviationsBySampler, ProfileResults profilerResults) {
        try {
            Files.createDirectories(MetricsPersister.PROFILING_RESULTS_DIR);
        } catch (IOException ioexception) {
            throw new UncheckedIOException(ioexception);
        }

        try {
            Path path = Files.createTempDirectory("minecraft-profiling");

            path.toFile().deleteOnExit();
            Files.createDirectories(MetricsPersister.PROFILING_RESULTS_DIR);
            Path path1 = path.resolve(this.rootFolderName);
            Path path2 = path1.resolve("metrics");

            this.saveMetrics(samplers, path2);
            if (!deviationsBySampler.isEmpty()) {
                this.saveDeviations(deviationsBySampler, path1.resolve("deviations"));
            }

            this.saveProfilingTaskExecutionResult(profilerResults, path1);
            return path;
        } catch (IOException ioexception1) {
            throw new UncheckedIOException(ioexception1);
        }
    }

    private void saveMetrics(Set<MetricSampler> samplers, Path dir) {
        if (samplers.isEmpty()) {
            throw new IllegalArgumentException("Expected at least one sampler to persist");
        } else {
            Map<MetricCategory, List<MetricSampler>> map = (Map) samplers.stream().collect(Collectors.groupingBy(MetricSampler::getCategory));

            map.forEach((metriccategory, list) -> {
                this.saveCategory(metriccategory, list, dir);
            });
        }
    }

    private void saveCategory(MetricCategory category, List<MetricSampler> samplers, Path dir) {
        String s = category.getDescription();
        Path path1 = dir.resolve(Util.sanitizeName(s, Identifier::validPathChar) + ".csv");
        Writer writer = null;

        try {
            Files.createDirectories(path1.getParent());
            writer = Files.newBufferedWriter(path1, StandardCharsets.UTF_8);
            CsvOutput.Builder csvoutput_builder = CsvOutput.builder();

            csvoutput_builder.addColumn("@tick");

            for (MetricSampler metricsampler : samplers) {
                csvoutput_builder.addColumn(metricsampler.getName());
            }

            CsvOutput csvoutput = csvoutput_builder.build(writer);
            List<MetricSampler.SamplerResult> list1 = (List) samplers.stream().map(MetricSampler::result).collect(Collectors.toList());
            int i = list1.stream().mapToInt(MetricSampler.SamplerResult::getFirstTick).summaryStatistics().getMin();
            int j = list1.stream().mapToInt(MetricSampler.SamplerResult::getLastTick).summaryStatistics().getMax();

            for (int k = i; k <= j; ++k) {
                Stream<String> stream = list1.stream().map((metricsampler_samplerresult) -> {
                    return String.valueOf(metricsampler_samplerresult.valueAtTick(k));
                });
                Object[] aobject = Stream.concat(Stream.of(String.valueOf(k)), stream).toArray((l) -> {
                    return new String[l];
                });

                csvoutput.writeRow(aobject);
            }

            MetricsPersister.LOGGER.info("Flushed metrics to {}", path1);
        } catch (Exception exception) {
            MetricsPersister.LOGGER.error("Could not save profiler results to {}", path1, exception);
        } finally {
            IOUtils.closeQuietly(writer);
        }

    }

    private void saveDeviations(Map<MetricSampler, List<RecordedDeviation>> deviationsBySampler, Path directory) {
        DateTimeFormatter datetimeformatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSS", Locale.UK).withZone(ZoneId.systemDefault());

        deviationsBySampler.forEach((metricsampler, list) -> {
            list.forEach((recordeddeviation) -> {
                String s = datetimeformatter.format(recordeddeviation.timestamp);
                Path path1 = directory.resolve(Util.sanitizeName(metricsampler.getName(), Identifier::validPathChar)).resolve(String.format(Locale.ROOT, "%d@%s.txt", recordeddeviation.tick, s));

                recordeddeviation.profilerResultAtTick.saveResults(path1);
            });
        });
    }

    private void saveProfilingTaskExecutionResult(ProfileResults results, Path directory) {
        results.saveResults(directory.resolve("profiling.txt"));
    }
}
