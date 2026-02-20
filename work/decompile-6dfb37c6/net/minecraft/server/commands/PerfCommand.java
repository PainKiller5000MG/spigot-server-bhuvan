package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FileUtil;
import net.minecraft.util.FileZipper;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

public class PerfCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleCommandExceptionType ERROR_NOT_RUNNING = new SimpleCommandExceptionType(Component.translatable("commands.perf.notRunning"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_RUNNING = new SimpleCommandExceptionType(Component.translatable("commands.perf.alreadyRunning"));

    public PerfCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("perf").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))).then(Commands.literal("start").executes((commandcontext) -> {
            return startProfilingDedicatedServer((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.literal("stop").executes((commandcontext) -> {
            return stopProfilingDedicatedServer((CommandSourceStack) commandcontext.getSource());
        })));
    }

    private static int startProfilingDedicatedServer(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer minecraftserver = source.getServer();

        if (minecraftserver.isRecordingMetrics()) {
            throw PerfCommand.ERROR_ALREADY_RUNNING.create();
        } else {
            Consumer<ProfileResults> consumer = (profileresults) -> {
                whenStopped(source, profileresults);
            };
            Consumer<Path> consumer1 = (path) -> {
                saveResults(source, path, minecraftserver);
            };

            minecraftserver.startRecordingMetrics(consumer, consumer1);
            source.sendSuccess(() -> {
                return Component.translatable("commands.perf.started");
            }, false);
            return 0;
        }
    }

    private static int stopProfilingDedicatedServer(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer minecraftserver = source.getServer();

        if (!minecraftserver.isRecordingMetrics()) {
            throw PerfCommand.ERROR_NOT_RUNNING.create();
        } else {
            minecraftserver.finishRecordingMetrics();
            return 0;
        }
    }

    private static void saveResults(CommandSourceStack source, Path report, MinecraftServer server) {
        String s = String.format(Locale.ROOT, "%s-%s-%s", Util.getFilenameFormattedDateTime(), server.getWorldData().getLevelName(), SharedConstants.getCurrentVersion().id());

        String s1;

        try {
            s1 = FileUtil.findAvailableName(MetricsPersister.PROFILING_RESULTS_DIR, s, ".zip");
        } catch (IOException ioexception) {
            source.sendFailure(Component.translatable("commands.perf.reportFailed"));
            PerfCommand.LOGGER.error("Failed to create report name", ioexception);
            return;
        }

        try (FileZipper filezipper = new FileZipper(MetricsPersister.PROFILING_RESULTS_DIR.resolve(s1))) {
            filezipper.add(Paths.get("system.txt"), server.fillSystemReport(new SystemReport()).toLineSeparatedString());
            filezipper.add(report);
        }

        try {
            FileUtils.forceDelete(report.toFile());
        } catch (IOException ioexception1) {
            PerfCommand.LOGGER.warn("Failed to delete temporary profiling file {}", report, ioexception1);
        }

        source.sendSuccess(() -> {
            return Component.translatable("commands.perf.reportSaved", s1);
        }, false);
    }

    private static void whenStopped(CommandSourceStack source, ProfileResults results) {
        if (results != EmptyProfileResults.EMPTY) {
            int i = results.getTickDuration();
            double d0 = (double) results.getNanoDuration() / (double) TimeUtil.NANOSECONDS_PER_SECOND;

            source.sendSuccess(() -> {
                return Component.translatable("commands.perf.stopped", String.format(Locale.ROOT, "%.2f", d0), i, String.format(Locale.ROOT, "%.2f", (double) i / d0));
            }, false);
        }
    }
}
