package net.minecraft.network.protocol;

import com.mojang.logging.LogUtils;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class PacketUtils {

    private static final Logger LOGGER = LogUtils.getLogger();

    public PacketUtils() {}

    public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> packet, T listener, ServerLevel level) throws RunningOnDifferentThreadException {
        ensureRunningOnSameThread(packet, listener, level.getServer().packetProcessor());
    }

    public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> packet, T listener, PacketProcessor packetProcessor) throws RunningOnDifferentThreadException {
        if (!packetProcessor.isSameThread()) {
            packetProcessor.scheduleIfPossible(listener, packet);
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
    }

    public static <T extends PacketListener> ReportedException makeReportedException(Exception cause, Packet<T> packet, T listener) {
        if (cause instanceof ReportedException reportedexception) {
            fillCrashReport(reportedexception.getReport(), listener, packet);
            return reportedexception;
        } else {
            CrashReport crashreport = CrashReport.forThrowable(cause, "Main thread packet handler");

            fillCrashReport(crashreport, listener, packet);
            return new ReportedException(crashreport);
        }
    }

    public static <T extends PacketListener> void fillCrashReport(CrashReport report, T listener, @Nullable Packet<T> packet) {
        if (packet != null) {
            CrashReportCategory crashreportcategory = report.addCategory("Incoming Packet");

            crashreportcategory.setDetail("Type", () -> {
                return packet.type().toString();
            });
            crashreportcategory.setDetail("Is Terminal", () -> {
                return Boolean.toString(packet.isTerminal());
            });
            crashreportcategory.setDetail("Is Skippable", () -> {
                return Boolean.toString(packet.isSkippable());
            });
        }

        listener.fillCrashReport(report);
    }
}
