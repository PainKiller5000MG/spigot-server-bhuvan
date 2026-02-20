package net.minecraft.world.level.chunk.storage;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.BitSet;

public class RegionBitmap {

    private final BitSet used = new BitSet();

    public RegionBitmap() {}

    public void force(int position, int size) {
        this.used.set(position, position + size);
    }

    public void free(int position, int size) {
        this.used.clear(position, position + size);
    }

    public int allocate(int size) {
        int j = 0;

        while (true) {
            int k = this.used.nextClearBit(j);
            int l = this.used.nextSetBit(k);

            if (l == -1 || l - k >= size) {
                this.force(k, size);
                return k;
            }

            j = l;
        }
    }

    @VisibleForTesting
    public IntSet getUsed() {
        return (IntSet) this.used.stream().collect(IntArraySet::new, IntCollection::add, IntCollection::addAll);
    }
}
