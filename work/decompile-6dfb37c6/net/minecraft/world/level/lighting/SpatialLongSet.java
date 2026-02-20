package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.NoSuchElementException;
import net.minecraft.util.Mth;

public class SpatialLongSet extends LongLinkedOpenHashSet {

    private final SpatialLongSet.InternalMap map;

    public SpatialLongSet(int expected, float f) {
        super(expected, f);
        this.map = new SpatialLongSet.InternalMap(expected / 64, f);
    }

    public boolean add(long k) {
        return this.map.addBit(k);
    }

    public boolean rem(long k) {
        return this.map.removeBit(k);
    }

    public long removeFirstLong() {
        return this.map.removeFirstBit();
    }

    public int size() {
        throw new UnsupportedOperationException();
    }

    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    protected static class InternalMap extends Long2LongLinkedOpenHashMap {

        private static final int X_BITS = Mth.log2(60000000);
        private static final int Z_BITS = Mth.log2(60000000);
        private static final int Y_BITS = 64 - SpatialLongSet.InternalMap.X_BITS - SpatialLongSet.InternalMap.Z_BITS;
        private static final int Y_OFFSET = 0;
        private static final int Z_OFFSET = SpatialLongSet.InternalMap.Y_BITS;
        private static final int X_OFFSET = SpatialLongSet.InternalMap.Y_BITS + SpatialLongSet.InternalMap.Z_BITS;
        private static final long OUTER_MASK = 3L << SpatialLongSet.InternalMap.X_OFFSET | 3L | 3L << SpatialLongSet.InternalMap.Z_OFFSET;
        private int lastPos = -1;
        private long lastOuterKey;
        private final int minSize;

        public InternalMap(int expected, float f) {
            super(expected, f);
            this.minSize = expected;
        }

        static long getOuterKey(long key) {
            return key & ~SpatialLongSet.InternalMap.OUTER_MASK;
        }

        static int getInnerKey(long key) {
            int j = (int) (key >>> SpatialLongSet.InternalMap.X_OFFSET & 3L);
            int k = (int) (key >>> 0 & 3L);
            int l = (int) (key >>> SpatialLongSet.InternalMap.Z_OFFSET & 3L);

            return j << 4 | l << 2 | k;
        }

        static long getFullKey(long outerKey, int innerKey) {
            outerKey |= (long) (innerKey >>> 4 & 3) << SpatialLongSet.InternalMap.X_OFFSET;
            outerKey |= (long) (innerKey >>> 2 & 3) << SpatialLongSet.InternalMap.Z_OFFSET;
            outerKey |= (long) (innerKey >>> 0 & 3) << 0;
            return outerKey;
        }

        public boolean addBit(long key) {
            long j = getOuterKey(key);
            int k = getInnerKey(key);
            long l = 1L << k;
            int i1;

            if (j == 0L) {
                if (this.containsNullKey) {
                    return this.replaceBit(this.n, l);
                }

                this.containsNullKey = true;
                i1 = this.n;
            } else {
                if (this.lastPos != -1 && j == this.lastOuterKey) {
                    return this.replaceBit(this.lastPos, l);
                }

                long[] along = this.key;

                i1 = (int) HashCommon.mix(j) & this.mask;

                for (long j1 = along[i1]; j1 != 0L; j1 = along[i1]) {
                    if (j1 == j) {
                        this.lastPos = i1;
                        this.lastOuterKey = j;
                        return this.replaceBit(i1, l);
                    }

                    i1 = i1 + 1 & this.mask;
                }
            }

            this.key[i1] = j;
            this.value[i1] = l;
            if (this.size == 0) {
                this.first = this.last = i1;
                this.link[i1] = -1L;
            } else {
                this.link[this.last] ^= (this.link[this.last] ^ (long) i1 & 4294967295L) & 4294967295L;
                this.link[i1] = ((long) this.last & 4294967295L) << 32 | 4294967295L;
                this.last = i1;
            }

            if (this.size++ >= this.maxFill) {
                this.rehash(HashCommon.arraySize(this.size + 1, this.f));
            }

            return false;
        }

        private boolean replaceBit(int pos, long bitMask) {
            boolean flag = (this.value[pos] & bitMask) != 0L;

            this.value[pos] |= bitMask;
            return flag;
        }

        public boolean removeBit(long key) {
            long j = getOuterKey(key);
            int k = getInnerKey(key);
            long l = 1L << k;

            if (j == 0L) {
                return this.containsNullKey ? this.removeFromNullEntry(l) : false;
            } else if (this.lastPos != -1 && j == this.lastOuterKey) {
                return this.removeFromEntry(this.lastPos, l);
            } else {
                long[] along = this.key;
                int i1 = (int) HashCommon.mix(j) & this.mask;

                for (long j1 = along[i1]; j1 != 0L; j1 = along[i1]) {
                    if (j == j1) {
                        this.lastPos = i1;
                        this.lastOuterKey = j;
                        return this.removeFromEntry(i1, l);
                    }

                    i1 = i1 + 1 & this.mask;
                }

                return false;
            }
        }

        private boolean removeFromNullEntry(long bitMask) {
            if ((this.value[this.n] & bitMask) == 0L) {
                return false;
            } else {
                this.value[this.n] &= ~bitMask;
                if (this.value[this.n] != 0L) {
                    return true;
                } else {
                    this.containsNullKey = false;
                    --this.size;
                    this.fixPointers(this.n);
                    if (this.size < this.maxFill / 4 && this.n > 16) {
                        this.rehash(this.n / 2);
                    }

                    return true;
                }
            }
        }

        private boolean removeFromEntry(int pos, long bitMask) {
            if ((this.value[pos] & bitMask) == 0L) {
                return false;
            } else {
                this.value[pos] &= ~bitMask;
                if (this.value[pos] != 0L) {
                    return true;
                } else {
                    this.lastPos = -1;
                    --this.size;
                    this.fixPointers(pos);
                    this.shiftKeys(pos);
                    if (this.size < this.maxFill / 4 && this.n > 16) {
                        this.rehash(this.n / 2);
                    }

                    return true;
                }
            }
        }

        public long removeFirstBit() {
            if (this.size == 0) {
                throw new NoSuchElementException();
            } else {
                int i = this.first;
                long j = this.key[i];
                int k = Long.numberOfTrailingZeros(this.value[i]);

                this.value[i] &= ~(1L << k);
                if (this.value[i] == 0L) {
                    this.removeFirstLong();
                    this.lastPos = -1;
                }

                return getFullKey(j, k);
            }
        }

        protected void rehash(int newN) {
            if (newN > this.minSize) {
                super.rehash(newN);
            }

        }
    }
}
