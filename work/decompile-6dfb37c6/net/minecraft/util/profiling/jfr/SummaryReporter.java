package net.minecraft.util.profiling.jfr;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.profiling.jfr.parse.JfrStatsParser;
import net.minecraft.util.profiling.jfr.parse.JfrStatsResult;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class SummaryReporter {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Runnable onDeregistration;

    protected SummaryReporter(Runnable onDeregistration) {
        this.onDeregistration = onDeregistration;
    }

    public void recordingStopped(@Nullable Path result) {
        if (result != null) {
            this.onDeregistration.run();
            infoWithFallback(() -> {
                return "Dumped flight recorder profiling to " + String.valueOf(result);
            });

            JfrStatsResult jfrstatsresult;

            try {
                jfrstatsresult = JfrStatsParser.parse(result);
            } catch (Throwable throwable) {
                warnWithFallback(() -> {
                    return "Failed to parse JFR recording";
                }, throwable);
                return;
            }

            try {
                Objects.requireNonNull(jfrstatsresult);
                infoWithFallback(jfrstatsresult::asJson);
                String s = result.getFileName().toString();
                Path path1 = result.resolveSibling("jfr-report-" + StringUtils.substringBefore(s, ".jfr") + ".json");

                Files.writeString(path1, jfrstatsresult.asJson(), StandardOpenOption.CREATE);
                infoWithFallback(() -> {
                    return "Dumped recording summary to " + String.valueOf(path1);
                });
            } catch (Throwable throwable1) {
                warnWithFallback(() -> {
                    return "Failed to output JFR report";
                }, throwable1);
            }

        }
    }

    private static void infoWithFallback(Supplier<String> message) {
        if (LogUtils.isLoggerActive()) {
            SummaryReporter.LOGGER.info((String) message.get());
        } else {
            Bootstrap.realStdoutPrintln((String) message.get());
        }

    }

    private static void warnWithFallback(Supplier<String> message, Throwable t) {
        if (LogUtils.isLoggerActive()) {
            SummaryReporter.LOGGER.warn((String) message.get(), t);
        } else {
            Bootstrap.realStdoutPrintln((String) message.get());
            t.printStackTrace(Bootstrap.STDOUT);
        }

    }
}
