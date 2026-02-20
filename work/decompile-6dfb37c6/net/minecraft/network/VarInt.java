package net.minecraft.network;

import io.netty.buffer.ByteBuf;

public class VarInt {

    public static final int MAX_VARINT_SIZE = 5;
    private static final int DATA_BITS_MASK = 127;
    private static final int CONTINUATION_BIT_MASK = 128;
    private static final int DATA_BITS_PER_BYTE = 7;

    public VarInt() {}

    public static int getByteSize(int value) {
        for (int j = 1; j < 5; ++j) {
            if ((value & -1 << j * 7) == 0) {
                return j;
            }
        }

        return 5;
    }

    public static boolean hasContinuationBit(byte in) {
        return (in & 128) == 128;
    }

    public static int read(ByteBuf input) {
        int i = 0;
        int j = 0;

        byte b0;

        do {
            b0 = input.readByte();
            i |= (b0 & 127) << j++ * 7;
            if (j > 5) {
                throw new RuntimeException("VarInt too big");
            }
        } while (hasContinuationBit(b0));

        return i;
    }

    public static ByteBuf write(ByteBuf output, int value) {
        while ((value & Byte.MIN_VALUE) != 0) {
            output.writeByte(value & 127 | 128);
            value >>>= 7;
        }

        output.writeByte(value);
        return output;
    }
}
