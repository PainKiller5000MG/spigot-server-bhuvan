package net.minecraft;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.CentralProcessor.ProcessorIdentifier;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PhysicalMemory;
import oshi.hardware.VirtualMemory;

public class SystemReport {

    public static final long BYTES_PER_MEBIBYTE = 1048576L;
    private static final long ONE_GIGA = 1000000000L;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String OPERATING_SYSTEM;
    private static final String JAVA_VERSION;
    private static final String JAVA_VM_VERSION;
    private final Map<String, String> entries = Maps.newLinkedHashMap();

    public SystemReport() {
        this.setDetail("Minecraft Version", SharedConstants.getCurrentVersion().name());
        this.setDetail("Minecraft Version ID", SharedConstants.getCurrentVersion().id());
        this.setDetail("Operating System", SystemReport.OPERATING_SYSTEM);
        this.setDetail("Java Version", SystemReport.JAVA_VERSION);
        this.setDetail("Java VM Version", SystemReport.JAVA_VM_VERSION);
        this.setDetail("Memory", () -> {
            Runtime runtime = Runtime.getRuntime();
            long i = runtime.maxMemory();
            long j = runtime.totalMemory();
            long k = runtime.freeMemory();
            long l = i / 1048576L;
            long i1 = j / 1048576L;
            long j1 = k / 1048576L;

            return k + " bytes (" + j1 + " MiB) / " + j + " bytes (" + i1 + " MiB) up to " + i + " bytes (" + l + " MiB)";
        });
        this.setDetail("CPUs", () -> {
            return String.valueOf(Runtime.getRuntime().availableProcessors());
        });
        this.ignoreErrors("hardware", () -> {
            this.putHardware(new SystemInfo());
        });
        this.setDetail("JVM Flags", () -> {
            return printJvmFlags((s) -> {
                return s.startsWith("-X");
            });
        });
        this.setDetail("Debug Flags", () -> {
            return printJvmFlags((s) -> {
                return s.startsWith("-DMC_DEBUG_");
            });
        });
    }

    private static String printJvmFlags(Predicate<String> selector) {
        List<String> list = ManagementFactory.getRuntimeMXBean().getInputArguments();
        List<String> list1 = list.stream().filter(selector).toList();

        return String.format(Locale.ROOT, "%d total; %s", list1.size(), String.join(" ", list1));
    }

    public void setDetail(String key, String value) {
        this.entries.put(key, value);
    }

    public void setDetail(String key, Supplier<String> valueSupplier) {
        try {
            this.setDetail(key, (String) valueSupplier.get());
        } catch (Exception exception) {
            SystemReport.LOGGER.warn("Failed to get system info for {}", key, exception);
            this.setDetail(key, "ERR");
        }

    }

    private void putHardware(SystemInfo systemInfo) {
        HardwareAbstractionLayer hardwareabstractionlayer = systemInfo.getHardware();

        this.ignoreErrors("processor", () -> {
            this.putProcessor(hardwareabstractionlayer.getProcessor());
        });
        this.ignoreErrors("graphics", () -> {
            this.putGraphics(hardwareabstractionlayer.getGraphicsCards());
        });
        this.ignoreErrors("memory", () -> {
            this.putMemory(hardwareabstractionlayer.getMemory());
        });
        this.ignoreErrors("storage", this::putStorage);
    }

    private void ignoreErrors(String group, Runnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            SystemReport.LOGGER.warn("Failed retrieving info for group {}", group, throwable);
        }

    }

    public static float sizeInMiB(long bytes) {
        return (float) bytes / 1048576.0F;
    }

    private void putPhysicalMemory(List<PhysicalMemory> memoryPackages) {
        int i = 0;

        for (PhysicalMemory physicalmemory : memoryPackages) {
            String s = String.format(Locale.ROOT, "Memory slot #%d ", i++);

            this.setDetail(s + "capacity (MiB)", () -> {
                return String.format(Locale.ROOT, "%.2f", sizeInMiB(physicalmemory.getCapacity()));
            });
            this.setDetail(s + "clockSpeed (GHz)", () -> {
                return String.format(Locale.ROOT, "%.2f", (float) physicalmemory.getClockSpeed() / 1.0E9F);
            });
            String s1 = s + "type";

            Objects.requireNonNull(physicalmemory);
            this.setDetail(s1, physicalmemory::getMemoryType);
        }

    }

    private void putVirtualMemory(VirtualMemory virtualMemory) {
        this.setDetail("Virtual memory max (MiB)", () -> {
            return String.format(Locale.ROOT, "%.2f", sizeInMiB(virtualMemory.getVirtualMax()));
        });
        this.setDetail("Virtual memory used (MiB)", () -> {
            return String.format(Locale.ROOT, "%.2f", sizeInMiB(virtualMemory.getVirtualInUse()));
        });
        this.setDetail("Swap memory total (MiB)", () -> {
            return String.format(Locale.ROOT, "%.2f", sizeInMiB(virtualMemory.getSwapTotal()));
        });
        this.setDetail("Swap memory used (MiB)", () -> {
            return String.format(Locale.ROOT, "%.2f", sizeInMiB(virtualMemory.getSwapUsed()));
        });
    }

    private void putMemory(GlobalMemory memory) {
        this.ignoreErrors("physical memory", () -> {
            this.putPhysicalMemory(memory.getPhysicalMemory());
        });
        this.ignoreErrors("virtual memory", () -> {
            this.putVirtualMemory(memory.getVirtualMemory());
        });
    }

    private void putGraphics(List<GraphicsCard> graphicsCards) {
        int i = 0;

        for (GraphicsCard graphicscard : graphicsCards) {
            String s = String.format(Locale.ROOT, "Graphics card #%d ", i++);
            String s1 = s + "name";

            Objects.requireNonNull(graphicscard);
            this.setDetail(s1, graphicscard::getName);
            s1 = s + "vendor";
            Objects.requireNonNull(graphicscard);
            this.setDetail(s1, graphicscard::getVendor);
            this.setDetail(s + "VRAM (MiB)", () -> {
                return String.format(Locale.ROOT, "%.2f", sizeInMiB(graphicscard.getVRam()));
            });
            s1 = s + "deviceId";
            Objects.requireNonNull(graphicscard);
            this.setDetail(s1, graphicscard::getDeviceId);
            s1 = s + "versionInfo";
            Objects.requireNonNull(graphicscard);
            this.setDetail(s1, graphicscard::getVersionInfo);
        }

    }

    private void putProcessor(CentralProcessor processor) {
        ProcessorIdentifier processoridentifier = processor.getProcessorIdentifier();

        Objects.requireNonNull(processoridentifier);
        this.setDetail("Processor Vendor", processoridentifier::getVendor);
        Objects.requireNonNull(processoridentifier);
        this.setDetail("Processor Name", processoridentifier::getName);
        Objects.requireNonNull(processoridentifier);
        this.setDetail("Identifier", processoridentifier::getIdentifier);
        Objects.requireNonNull(processoridentifier);
        this.setDetail("Microarchitecture", processoridentifier::getMicroarchitecture);
        this.setDetail("Frequency (GHz)", () -> {
            return String.format(Locale.ROOT, "%.2f", (float) processoridentifier.getVendorFreq() / 1.0E9F);
        });
        this.setDetail("Number of physical packages", () -> {
            return String.valueOf(processor.getPhysicalPackageCount());
        });
        this.setDetail("Number of physical CPUs", () -> {
            return String.valueOf(processor.getPhysicalProcessorCount());
        });
        this.setDetail("Number of logical CPUs", () -> {
            return String.valueOf(processor.getLogicalProcessorCount());
        });
    }

    private void putStorage() {
        this.putSpaceForProperty("jna.tmpdir");
        this.putSpaceForProperty("org.lwjgl.system.SharedLibraryExtractPath");
        this.putSpaceForProperty("io.netty.native.workdir");
        this.putSpaceForProperty("java.io.tmpdir");
        this.putSpaceForPath("workdir", () -> {
            return "";
        });
    }

    private void putSpaceForProperty(String env) {
        this.putSpaceForPath(env, () -> {
            return System.getProperty(env);
        });
    }

    private void putSpaceForPath(String id, Supplier<@Nullable String> pathSupplier) {
        String s1 = "Space in storage for " + id + " (MiB)";

        try {
            String s2 = (String) pathSupplier.get();

            if (s2 == null) {
                this.setDetail(s1, "<path not set>");
                return;
            }

            FileStore filestore = Files.getFileStore(Path.of(s2));

            this.setDetail(s1, String.format(Locale.ROOT, "available: %.2f, total: %.2f", sizeInMiB(filestore.getUsableSpace()), sizeInMiB(filestore.getTotalSpace())));
        } catch (InvalidPathException invalidpathexception) {
            SystemReport.LOGGER.warn("{} is not a path", id, invalidpathexception);
            this.setDetail(s1, "<invalid path>");
        } catch (Exception exception) {
            SystemReport.LOGGER.warn("Failed retrieving storage space for {}", id, exception);
            this.setDetail(s1, "ERR");
        }

    }

    public void appendToCrashReportString(StringBuilder sb) {
        sb.append("-- ").append("System Details").append(" --\n");
        sb.append("Details:");
        this.entries.forEach((s, s1) -> {
            sb.append("\n\t");
            sb.append(s);
            sb.append(": ");
            sb.append(s1);
        });
    }

    public String toLineSeparatedString() {
        return (String) this.entries.entrySet().stream().map((entry) -> {
            String s = (String) entry.getKey();

            return s + ": " + (String) entry.getValue();
        }).collect(Collectors.joining(System.lineSeparator()));
    }

    static {
        String s = System.getProperty("os.name");

        OPERATING_SYSTEM = s + " (" + System.getProperty("os.arch") + ") version " + System.getProperty("os.version");
        s = System.getProperty("java.version");
        JAVA_VERSION = s + ", " + System.getProperty("java.vendor");
        s = System.getProperty("java.vm.name");
        JAVA_VM_VERSION = s + " (" + System.getProperty("java.vm.info") + "), " + System.getProperty("java.vm.vendor");
    }
}
