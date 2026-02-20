package net.minecraft.util;

import java.util.Locale;
import java.util.function.Consumer;

public class StaticCache2D<T> {

    private final int minX;
    private final int minZ;
    private final int sizeX;
    private final int sizeZ;
    private final Object[] cache;

    public static <T> StaticCache2D<T> create(int centerX, int centerZ, int range, StaticCache2D.Initializer<T> initializer) {
        int l = centerX - range;
        int i1 = centerZ - range;
        int j1 = 2 * range + 1;

        return new StaticCache2D<T>(l, i1, j1, j1, initializer);
    }

    private StaticCache2D(int minX, int minZ, int sizeX, int sizeZ, StaticCache2D.Initializer<T> initializer) {
        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.cache = new Object[this.sizeX * this.sizeZ];

        for (int i1 = minX; i1 < minX + sizeX; ++i1) {
            for (int j1 = minZ; j1 < minZ + sizeZ; ++j1) {
                this.cache[this.getIndex(i1, j1)] = initializer.get(i1, j1);
            }
        }

    }

    public void forEach(Consumer<T> consumer) {
        for (Object object : this.cache) {
            consumer.accept(object);
        }

    }

    public T get(int x, int z) {
        if (!this.contains(x, z)) {
            throw new IllegalArgumentException("Requested out of range value (" + x + "," + z + ") from " + String.valueOf(this));
        } else {
            return (T) this.cache[this.getIndex(x, z)];
        }
    }

    public boolean contains(int x, int z) {
        int k = x - this.minX;
        int l = z - this.minZ;

        return k >= 0 && k < this.sizeX && l >= 0 && l < this.sizeZ;
    }

    public String toString() {
        return String.format(Locale.ROOT, "StaticCache2D[%d, %d, %d, %d]", this.minX, this.minZ, this.minX + this.sizeX, this.minZ + this.sizeZ);
    }

    private int getIndex(int x, int z) {
        int k = x - this.minX;
        int l = z - this.minZ;

        return k * this.sizeZ + l;
    }

    @FunctionalInterface
    public interface Initializer<T> {

        T get(int x, int z);
    }
}
