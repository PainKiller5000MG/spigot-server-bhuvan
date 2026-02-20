package net.minecraft.network;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketUtils;

public interface PacketListener {

    PacketFlow flow();

    ConnectionProtocol protocol();

    void onDisconnect(DisconnectionDetails details);

    default void onPacketError(Packet packet, Exception cause) throws ReportedException {
        throw PacketUtils.makeReportedException(cause, packet, this);
    }

    default DisconnectionDetails createDisconnectionInfo(Component reason, Throwable cause) {
        return new DisconnectionDetails(reason);
    }

    boolean isAcceptingMessages();

    default boolean shouldHandleMessage(Packet<?> packet) {
        return this.isAcceptingMessages();
    }

    default void fillCrashReport(CrashReport crashReport) {
        CrashReportCategory crashreportcategory = crashReport.addCategory("Connection");

        crashreportcategory.setDetail("Protocol", () -> {
            return this.protocol().id();
        });
        crashreportcategory.setDetail("Flow", () -> {
            return this.flow().toString();
        });
        this.fillListenerSpecificCrashDetails(crashReport, crashreportcategory);
    }

    default void fillListenerSpecificCrashDetails(CrashReport report, CrashReportCategory connectionDetails) {}
}
