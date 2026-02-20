package net.minecraft.util.datafix;

import net.minecraft.util.Mth;
import org.apache.commons.lang3.Validate;

public class PackedBitStorage {

    private static final int BIT_TO_LONG_SHIFT = 6;
    private final long[] data;
    private final int bits;
    private final long mask;
    private final int size;

    public PackedBitStorage(int bits, int size) {
        this(bits, size, new long[Mth.roundToward(size * bits, 64) / 64]);
    }

    public PackedBitStorage(int bits, int size, long[] data) {
        Validate.inclusiveBetween(1L, 32L, (long) bits);
        this.size = size;
        this.bits = bits;
        this.data = data;
        this.mask = (1L << bits) - 1L;
        int k = Mth.roundToward(size * bits, 64) / 64;

        if (data.length != k) {
            throw new IllegalArgumentException("Invalid length given for storage, got: " + data.length + " but expected: " + k);
        }
    }

    public void set(int index, int value) {
        Validate.inclusiveBetween(0L, (long) (this.size - 1), (long) index);
        Validate.inclusiveBetween(0L, this.mask, (long) value);
        int k = index * this.bits;
        int l = k >> 6;
        int i1 = (index + 1) * this.bits - 1 >> 6;
        int j1 = k ^ l << 6;

        this.data[l] = this.data[l] & ~(this.mask << j1) | ((long) value & this.mask) << j1;
        if (l != i1) {
            int k1 = 64 - j1;
            int l1 = this.bits - k1;

            this.data[i1] = this.data[i1] >>> l1 << l1 | ((long) value & this.mask) >> k1;
        }

    }

    public int get(int index) {
        Validate.inclusiveBetween(0L, (long) (this.size - 1), (long) index);
        int j = index * this.bits;
        int k = j >> 6;
        int l = (index + 1) * this.bits - 1 >> 6;
        int i1 = j ^ k << 6;

        if (k == l) {
            return (int) (this.data[k] >>> i1 & this.mask);
        } else {
            int j1 = 64 - i1;

            return (int) ((this.data[k] >>> i1 | this.data[l] << j1) & this.mask);
        }
    }

    public long[] getRaw() {
        return this.data;
    }

    public int getBits() {
        return this.bits;
    }
}
