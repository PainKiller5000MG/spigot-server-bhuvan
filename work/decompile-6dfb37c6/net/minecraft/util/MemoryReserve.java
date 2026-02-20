package net.minecraft.util;

import org.jspecify.annotations.Nullable;

public class MemoryReserve {

    private static byte @Nullable [] reserve;

    public MemoryReserve() {}

    public static void allocate() {
        MemoryReserve.reserve = new byte[10485760];
    }

    public static void release() {
        if (MemoryReserve.reserve != null) {
            MemoryReserve.reserve = null;

            try {
                System.gc();
                System.gc();
                System.gc();
            } catch (Throwable throwable) {
                ;
            }
        }

    }
}
