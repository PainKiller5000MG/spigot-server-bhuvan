package net.minecraft.util;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.Tlhelp32.MODULEENTRY32W;
import com.sun.jna.platform.win32.Version;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import net.minecraft.CrashReportCategory;
import org.slf4j.Logger;

public class NativeModuleLister {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LANG_MASK = 65535;
    private static final int DEFAULT_LANG = 1033;
    private static final int CODEPAGE_MASK = -65536;
    private static final int DEFAULT_CODEPAGE = 78643200;

    public NativeModuleLister() {}

    public static List<NativeModuleLister.NativeModuleInfo> listModules() {
        if (!Platform.isWindows()) {
            return ImmutableList.of();
        } else {
            int i = Kernel32.INSTANCE.GetCurrentProcessId();
            ImmutableList.Builder<NativeModuleLister.NativeModuleInfo> immutablelist_builder = ImmutableList.builder();

            for (MODULEENTRY32W moduleentry32w : Kernel32Util.getModules(i)) {
                String s = moduleentry32w.szModule();
                Optional<NativeModuleLister.NativeModuleVersion> optional = tryGetVersion(moduleentry32w.szExePath());

                immutablelist_builder.add(new NativeModuleLister.NativeModuleInfo(s, optional));
            }

            return immutablelist_builder.build();
        }
    }

    private static Optional<NativeModuleLister.NativeModuleVersion> tryGetVersion(String path) {
        try {
            IntByReference intbyreference = new IntByReference();
            int i = Version.INSTANCE.GetFileVersionInfoSize(path, intbyreference);

            if (i == 0) {
                int j = Native.getLastError();

                if (j != 1813 && j != 1812) {
                    throw new Win32Exception(j);
                } else {
                    return Optional.empty();
                }
            } else {
                Pointer pointer = new Memory((long) i);

                if (!Version.INSTANCE.GetFileVersionInfo(path, 0, i, pointer)) {
                    throw new Win32Exception(Native.getLastError());
                } else {
                    IntByReference intbyreference1 = new IntByReference();
                    Pointer pointer1 = queryVersionValue(pointer, "\\VarFileInfo\\Translation", intbyreference1);
                    int[] aint = pointer1.getIntArray(0L, intbyreference1.getValue() / 4);
                    OptionalInt optionalint = findLangAndCodepage(aint);

                    if (optionalint.isEmpty()) {
                        return Optional.empty();
                    } else {
                        int k = optionalint.getAsInt();
                        int l = k & '\uffff';
                        int i1 = (k & -65536) >> 16;
                        String s1 = queryVersionString(pointer, langTableKey("FileDescription", l, i1), intbyreference1);
                        String s2 = queryVersionString(pointer, langTableKey("CompanyName", l, i1), intbyreference1);
                        String s3 = queryVersionString(pointer, langTableKey("FileVersion", l, i1), intbyreference1);

                        return Optional.of(new NativeModuleLister.NativeModuleVersion(s1, s3, s2));
                    }
                }
            }
        } catch (Exception exception) {
            NativeModuleLister.LOGGER.info("Failed to find module info for {}", path, exception);
            return Optional.empty();
        }
    }

    private static String langTableKey(String key, int lang, int codepage) {
        return String.format(Locale.ROOT, "\\StringFileInfo\\%04x%04x\\%s", lang, codepage, key);
    }

    private static OptionalInt findLangAndCodepage(int[] langsAndCodepages) {
        OptionalInt optionalint = OptionalInt.empty();

        for (int i : langsAndCodepages) {
            if ((i & -65536) == 78643200 && (i & '\uffff') == 1033) {
                return OptionalInt.of(i);
            }

            optionalint = OptionalInt.of(i);
        }

        return optionalint;
    }

    private static Pointer queryVersionValue(Pointer lpData, String key, IntByReference outSize) {
        PointerByReference pointerbyreference = new PointerByReference();

        if (!Version.INSTANCE.VerQueryValue(lpData, key, pointerbyreference, outSize)) {
            throw new UnsupportedOperationException("Can't get version value " + key);
        } else {
            return pointerbyreference.getValue();
        }
    }

    private static String queryVersionString(Pointer lpData, String key, IntByReference outSize) {
        try {
            Pointer pointer1 = queryVersionValue(lpData, key, outSize);
            byte[] abyte = pointer1.getByteArray(0L, (outSize.getValue() - 1) * 2);

            return new String(abyte, StandardCharsets.UTF_16LE);
        } catch (Exception exception) {
            return "";
        }
    }

    public static void addCrashSection(CrashReportCategory category) {
        category.setDetail("Modules", () -> {
            return (String) listModules().stream().sorted(Comparator.comparing((nativemodulelister_nativemoduleinfo) -> {
                return nativemodulelister_nativemoduleinfo.name;
            })).map((nativemodulelister_nativemoduleinfo) -> {
                return "\n\t\t" + String.valueOf(nativemodulelister_nativemoduleinfo);
            }).collect(Collectors.joining());
        });
    }

    public static class NativeModuleVersion {

        public final String description;
        public final String version;
        public final String company;

        public NativeModuleVersion(String description, String version, String company) {
            this.description = description;
            this.version = version;
            this.company = company;
        }

        public String toString() {
            return this.description + ":" + this.version + ":" + this.company;
        }
    }

    public static class NativeModuleInfo {

        public final String name;
        public final Optional<NativeModuleLister.NativeModuleVersion> version;

        public NativeModuleInfo(String name, Optional<NativeModuleLister.NativeModuleVersion> version) {
            this.name = name;
            this.version = version;
        }

        public String toString() {
            return (String) this.version.map((nativemodulelister_nativemoduleversion) -> {
                return this.name + ":" + String.valueOf(nativemodulelister_nativemoduleversion);
            }).orElse(this.name);
        }
    }
}
