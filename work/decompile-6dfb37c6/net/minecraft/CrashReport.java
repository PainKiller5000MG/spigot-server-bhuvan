package net.minecraft;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import net.minecraft.util.FileUtil;
import net.minecraft.util.MemoryReserve;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class CrashReport {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private final String title;
    private final Throwable exception;
    private final List<CrashReportCategory> details = Lists.newArrayList();
    private @Nullable Path saveFile;
    private boolean trackingStackTrace = true;
    private StackTraceElement[] uncategorizedStackTrace = new StackTraceElement[0];
    private final SystemReport systemReport = new SystemReport();

    public CrashReport(String title, Throwable t) {
        this.title = title;
        this.exception = t;
    }

    public String getTitle() {
        return this.title;
    }

    public Throwable getException() {
        return this.exception;
    }

    public String getDetails() {
        StringBuilder stringbuilder = new StringBuilder();

        this.getDetails(stringbuilder);
        return stringbuilder.toString();
    }

    public void getDetails(StringBuilder builder) {
        if ((this.uncategorizedStackTrace == null || this.uncategorizedStackTrace.length <= 0) && !this.details.isEmpty()) {
            this.uncategorizedStackTrace = (StackTraceElement[]) ArrayUtils.subarray(((CrashReportCategory) this.details.get(0)).getStacktrace(), 0, 1);
        }

        if (this.uncategorizedStackTrace != null && this.uncategorizedStackTrace.length > 0) {
            builder.append("-- Head --\n");
            builder.append("Thread: ").append(Thread.currentThread().getName()).append("\n");
            builder.append("Stacktrace:\n");

            for (StackTraceElement stacktraceelement : this.uncategorizedStackTrace) {
                builder.append("\t").append("at ").append(stacktraceelement);
                builder.append("\n");
            }

            builder.append("\n");
        }

        for (CrashReportCategory crashreportcategory : this.details) {
            crashreportcategory.getDetails(builder);
            builder.append("\n\n");
        }

        this.systemReport.appendToCrashReportString(builder);
    }

    public String getExceptionMessage() {
        StringWriter stringwriter = null;
        PrintWriter printwriter = null;
        Throwable throwable = this.exception;

        if (throwable.getMessage() == null) {
            if (throwable instanceof NullPointerException) {
                throwable = new NullPointerException(this.title);
            } else if (throwable instanceof StackOverflowError) {
                throwable = new StackOverflowError(this.title);
            } else if (throwable instanceof OutOfMemoryError) {
                throwable = new OutOfMemoryError(this.title);
            }

            throwable.setStackTrace(this.exception.getStackTrace());
        }

        String s;

        try {
            stringwriter = new StringWriter();
            printwriter = new PrintWriter(stringwriter);
            throwable.printStackTrace(printwriter);
            s = stringwriter.toString();
        } finally {
            IOUtils.closeQuietly(stringwriter);
            IOUtils.closeQuietly(printwriter);
        }

        return s;
    }

    public String getFriendlyReport(ReportType reportType, List<String> extraComments) {
        StringBuilder stringbuilder = new StringBuilder();

        reportType.appendHeader(stringbuilder, extraComments);
        stringbuilder.append("Time: ");
        stringbuilder.append(CrashReport.DATE_TIME_FORMATTER.format(ZonedDateTime.now()));
        stringbuilder.append("\n");
        stringbuilder.append("Description: ");
        stringbuilder.append(this.title);
        stringbuilder.append("\n\n");
        stringbuilder.append(this.getExceptionMessage());
        stringbuilder.append("\n\nA detailed walkthrough of the error, its code path and all known details is as follows:\n");

        for (int i = 0; i < 87; ++i) {
            stringbuilder.append("-");
        }

        stringbuilder.append("\n\n");
        this.getDetails(stringbuilder);
        return stringbuilder.toString();
    }

    public String getFriendlyReport(ReportType reportType) {
        return this.getFriendlyReport(reportType, List.of());
    }

    public @Nullable Path getSaveFile() {
        return this.saveFile;
    }

    public boolean saveToFile(Path saveFile, ReportType reportType, List<String> extraComments) {
        if (this.saveFile != null) {
            return false;
        } else {
            try {
                if (saveFile.getParent() != null) {
                    FileUtil.createDirectoriesSafe(saveFile.getParent());
                }

                try (Writer writer = Files.newBufferedWriter(saveFile, StandardCharsets.UTF_8)) {
                    writer.write(this.getFriendlyReport(reportType, extraComments));
                }

                this.saveFile = saveFile;
                return true;
            } catch (Throwable throwable) {
                CrashReport.LOGGER.error("Could not save crash report to {}", saveFile, throwable);
                return false;
            }
        }
    }

    public boolean saveToFile(Path file, ReportType reportType) {
        return this.saveToFile(file, reportType, List.of());
    }

    public SystemReport getSystemReport() {
        return this.systemReport;
    }

    public CrashReportCategory addCategory(String name) {
        return this.addCategory(name, 1);
    }

    public CrashReportCategory addCategory(String name, int nestedOffset) {
        CrashReportCategory crashreportcategory = new CrashReportCategory(name);

        if (this.trackingStackTrace) {
            int j = crashreportcategory.fillInStackTrace(nestedOffset);
            StackTraceElement[] astacktraceelement = this.exception.getStackTrace();
            StackTraceElement stacktraceelement = null;
            StackTraceElement stacktraceelement1 = null;
            int k = astacktraceelement.length - j;

            if (k < 0) {
                CrashReport.LOGGER.error("Negative index in crash report handler ({}/{})", astacktraceelement.length, j);
            }

            if (astacktraceelement != null && 0 <= k && k < astacktraceelement.length) {
                stacktraceelement = astacktraceelement[k];
                if (astacktraceelement.length + 1 - j < astacktraceelement.length) {
                    stacktraceelement1 = astacktraceelement[astacktraceelement.length + 1 - j];
                }
            }

            this.trackingStackTrace = crashreportcategory.validateStackTrace(stacktraceelement, stacktraceelement1);
            if (astacktraceelement != null && astacktraceelement.length >= j && 0 <= k && k < astacktraceelement.length) {
                this.uncategorizedStackTrace = new StackTraceElement[k];
                System.arraycopy(astacktraceelement, 0, this.uncategorizedStackTrace, 0, this.uncategorizedStackTrace.length);
            } else {
                this.trackingStackTrace = false;
            }
        }

        this.details.add(crashreportcategory);
        return crashreportcategory;
    }

    public static CrashReport forThrowable(Throwable t, String title) {
        while (t instanceof CompletionException && t.getCause() != null) {
            t = t.getCause();
        }

        CrashReport crashreport;

        if (t instanceof ReportedException reportedexception) {
            crashreport = reportedexception.getReport();
        } else {
            crashreport = new CrashReport(title, t);
        }

        return crashreport;
    }

    public static void preload() {
        MemoryReserve.allocate();
        (new CrashReport("Don't panic!", new Throwable())).getFriendlyReport(ReportType.CRASH);
    }
}
