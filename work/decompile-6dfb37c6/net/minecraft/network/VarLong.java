package net.minecraft.network;

import io.netty.buffer.ByteBuf;

public class VarLong {

    private static final int MAX_VARLONG_SIZE = 10;
    private static final int DATA_BITS_MASK = 127;
    private static final int CONTINUATION_BIT_MASK = 128;
    private static final int DATA_BITS_PER_BYTE = 7;

    public VarLong() {}

    public static int getByteSize(long value) {
        for (int j = 1; j < 10; ++j) {
            if ((value & -1L << j * 7) == 0L) {
                return j;
            }
        }

        return 10;
    }

    public static boolean hasContinuationBit(byte in) {
        return (in & 128) == 128;
    }

    public static long read(ByteBuf input) {
        long i = 0L;
        int j = 0;

        byte b0;

        do {
            b0 = input.readByte();
            i |= (long) (b0 & 127) << j++ * 7;
            if (j > 10) {
                throw new RuntimeException("VarLong too big");
            }
        } while (hasContinuationBit(b0));

        return i;
    }

    public static ByteBuf write(ByteBuf output, long value) {
        while ((value & -128L) != 0L) {
            output.writeByte((int) (value & 127L) | 128);
            value >>>= 7;
        }

        output.writeByte((int) value);
        return output;
    }
}
