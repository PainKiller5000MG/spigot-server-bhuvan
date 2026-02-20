package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class LpVec3 {

    private static final int DATA_BITS = 15;
    private static final int DATA_BITS_MASK = 32767;
    private static final double MAX_QUANTIZED_VALUE = 32766.0D;
    private static final int SCALE_BITS = 2;
    private static final int SCALE_BITS_MASK = 3;
    private static final int CONTINUATION_FLAG = 4;
    private static final int X_OFFSET = 3;
    private static final int Y_OFFSET = 18;
    private static final int Z_OFFSET = 33;
    public static final double ABS_MAX_VALUE = 1.7179869183E10D;
    public static final double ABS_MIN_VALUE = 3.051944088384301E-5D;

    public LpVec3() {}

    public static boolean hasContinuationBit(int in) {
        return (in & 4) == 4;
    }

    public static Vec3 read(ByteBuf input) {
        int i = input.readUnsignedByte();

        if (i == 0) {
            return Vec3.ZERO;
        } else {
            int j = input.readUnsignedByte();
            long k = input.readUnsignedInt();
            long l = k << 16 | (long) (j << 8) | (long) i;
            long i1 = (long) (i & 3);

            if (hasContinuationBit(i)) {
                i1 |= ((long) VarInt.read(input) & 4294967295L) << 2;
            }

            return new Vec3(unpack(l >> 3) * (double) i1, unpack(l >> 18) * (double) i1, unpack(l >> 33) * (double) i1);
        }
    }

    public static void write(ByteBuf output, Vec3 value) {
        double d0 = sanitize(value.x);
        double d1 = sanitize(value.y);
        double d2 = sanitize(value.z);
        double d3 = Mth.absMax(d0, Mth.absMax(d1, d2));

        if (d3 < 3.051944088384301E-5D) {
            output.writeByte(0);
        } else {
            long i = Mth.ceilLong(d3);
            boolean flag = (i & 3L) != i;
            long j = flag ? i & 3L | 4L : i;
            long k = pack(d0 / (double) i) << 3;
            long l = pack(d1 / (double) i) << 18;
            long i1 = pack(d2 / (double) i) << 33;
            long j1 = j | k | l | i1;

            output.writeByte((byte) ((int) j1));
            output.writeByte((byte) ((int) (j1 >> 8)));
            output.writeInt((int) (j1 >> 16));
            if (flag) {
                VarInt.write(output, (int) (i >> 2));
            }

        }
    }

    private static double sanitize(double value) {
        return Double.isNaN(value) ? 0.0D : Math.clamp(value, -1.7179869183E10D, 1.7179869183E10D);
    }

    private static long pack(double value) {
        return Math.round((value * 0.5D + 0.5D) * 32766.0D);
    }

    private static double unpack(long value) {
        return Math.min((double) (value & 32767L), 32766.0D) * 2.0D / 32766.0D - 1.0D;
    }
}
