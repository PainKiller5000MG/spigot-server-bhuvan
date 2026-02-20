package net.minecraft;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class CrashReportCategory {

    private final String title;
    private final List<CrashReportCategory.Entry> entries = Lists.newArrayList();
    private StackTraceElement[] stackTrace = new StackTraceElement[0];

    public CrashReportCategory(String title) {
        this.title = title;
    }

    public static String formatLocation(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", x, y, z);
    }

    public static String formatLocation(LevelHeightAccessor levelHeightAccessor, double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f - %s", x, y, z, formatLocation(levelHeightAccessor, BlockPos.containing(x, y, z)));
    }

    public static String formatLocation(LevelHeightAccessor levelHeightAccessor, BlockPos pos) {
        return formatLocation(levelHeightAccessor, pos.getX(), pos.getY(), pos.getZ());
    }

    public static String formatLocation(LevelHeightAccessor levelHeightAccessor, int x, int y, int z) {
        StringBuilder stringbuilder = new StringBuilder();

        try {
            stringbuilder.append(String.format(Locale.ROOT, "World: (%d,%d,%d)", x, y, z));
        } catch (Throwable throwable) {
            stringbuilder.append("(Error finding world loc)");
        }

        stringbuilder.append(", ");

        try {
            int l = SectionPos.blockToSectionCoord(x);
            int i1 = SectionPos.blockToSectionCoord(y);
            int j1 = SectionPos.blockToSectionCoord(z);
            int k1 = x & 15;
            int l1 = y & 15;
            int i2 = z & 15;
            int j2 = SectionPos.sectionToBlockCoord(l);
            int k2 = levelHeightAccessor.getMinY();
            int l2 = SectionPos.sectionToBlockCoord(j1);
            int i3 = SectionPos.sectionToBlockCoord(l + 1) - 1;
            int j3 = levelHeightAccessor.getMaxY();
            int k3 = SectionPos.sectionToBlockCoord(j1 + 1) - 1;

            stringbuilder.append(String.format(Locale.ROOT, "Section: (at %d,%d,%d in %d,%d,%d; chunk contains blocks %d,%d,%d to %d,%d,%d)", k1, l1, i2, l, i1, j1, j2, k2, l2, i3, j3, k3));
        } catch (Throwable throwable1) {
            stringbuilder.append("(Error finding chunk loc)");
        }

        stringbuilder.append(", ");

        try {
            int l3 = x >> 9;
            int i4 = z >> 9;
            int j4 = l3 << 5;
            int k4 = i4 << 5;
            int l4 = (l3 + 1 << 5) - 1;
            int i5 = (i4 + 1 << 5) - 1;
            int j5 = l3 << 9;
            int k5 = levelHeightAccessor.getMinY();
            int l5 = i4 << 9;
            int i6 = (l3 + 1 << 9) - 1;
            int j6 = levelHeightAccessor.getMaxY();
            int k6 = (i4 + 1 << 9) - 1;

            stringbuilder.append(String.format(Locale.ROOT, "Region: (%d,%d; contains chunks %d,%d to %d,%d, blocks %d,%d,%d to %d,%d,%d)", l3, i4, j4, k4, l4, i5, j5, k5, l5, i6, j6, k6));
        } catch (Throwable throwable2) {
            stringbuilder.append("(Error finding world loc)");
        }

        return stringbuilder.toString();
    }

    public CrashReportCategory setDetail(String key, CrashReportDetail<String> callback) {
        try {
            this.setDetail(key, callback.call());
        } catch (Throwable throwable) {
            this.setDetailError(key, throwable);
        }

        return this;
    }

    public CrashReportCategory setDetail(String key, Object value) {
        this.entries.add(new CrashReportCategory.Entry(key, value));
        return this;
    }

    public void setDetailError(String key, Throwable t) {
        this.setDetail(key, t);
    }

    public int fillInStackTrace(int nestedOffset) {
        StackTraceElement[] astacktraceelement = Thread.currentThread().getStackTrace();

        if (astacktraceelement.length <= 0) {
            return 0;
        } else {
            this.stackTrace = new StackTraceElement[astacktraceelement.length - 3 - nestedOffset];
            System.arraycopy(astacktraceelement, 3 + nestedOffset, this.stackTrace, 0, this.stackTrace.length);
            return this.stackTrace.length;
        }
    }

    public boolean validateStackTrace(StackTraceElement source, StackTraceElement next) {
        if (this.stackTrace.length != 0 && source != null) {
            StackTraceElement stacktraceelement2 = this.stackTrace[0];

            if (stacktraceelement2.isNativeMethod() == source.isNativeMethod() && stacktraceelement2.getClassName().equals(source.getClassName()) && stacktraceelement2.getFileName().equals(source.getFileName()) && stacktraceelement2.getMethodName().equals(source.getMethodName())) {
                if (next != null != this.stackTrace.length > 1) {
                    return false;
                } else if (next != null && !this.stackTrace[1].equals(next)) {
                    return false;
                } else {
                    this.stackTrace[0] = source;
                    return true;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void trimStacktrace(int length) {
        StackTraceElement[] astacktraceelement = new StackTraceElement[this.stackTrace.length - length];

        System.arraycopy(this.stackTrace, 0, astacktraceelement, 0, astacktraceelement.length);
        this.stackTrace = astacktraceelement;
    }

    public void getDetails(StringBuilder builder) {
        builder.append("-- ").append(this.title).append(" --\n");
        builder.append("Details:");

        for (CrashReportCategory.Entry crashreportcategory_entry : this.entries) {
            builder.append("\n\t");
            builder.append(crashreportcategory_entry.getKey());
            builder.append(": ");
            builder.append(crashreportcategory_entry.getValue());
        }

        if (this.stackTrace != null && this.stackTrace.length > 0) {
            builder.append("\nStacktrace:");

            for (StackTraceElement stacktraceelement : this.stackTrace) {
                builder.append("\n\tat ");
                builder.append(stacktraceelement);
            }
        }

    }

    public StackTraceElement[] getStacktrace() {
        return this.stackTrace;
    }

    public static void populateBlockDetails(CrashReportCategory category, LevelHeightAccessor levelHeightAccessor, BlockPos pos, BlockState state) {
        Objects.requireNonNull(state);
        category.setDetail("Block", state::toString);
        populateBlockLocationDetails(category, levelHeightAccessor, pos);
    }

    public static CrashReportCategory populateBlockLocationDetails(CrashReportCategory category, LevelHeightAccessor levelHeightAccessor, BlockPos pos) {
        return category.setDetail("Block location", () -> {
            return formatLocation(levelHeightAccessor, pos);
        });
    }

    private static class Entry {

        private final String key;
        private final String value;

        public Entry(String key, @Nullable Object value) {
            this.key = key;
            if (value == null) {
                this.value = "~~NULL~~";
            } else if (value instanceof Throwable) {
                Throwable throwable = (Throwable) value;
                String s1 = throwable.getClass().getSimpleName();

                this.value = "~~ERROR~~ " + s1 + ": " + throwable.getMessage();
            } else {
                this.value = value.toString();
            }

        }

        public String getKey() {
            return this.key;
        }

        public String getValue() {
            return this.value;
        }
    }
}
