package net.minecraft.world.level.levelgen;

import net.minecraft.util.RandomSource;

public interface BitRandomSource extends RandomSource {

    float FLOAT_MULTIPLIER = 5.9604645E-8F;
    double DOUBLE_MULTIPLIER = (double) 1.110223E-16F;

    int next(int bits);

    @Override
    default int nextInt() {
        return this.next(32);
    }

    @Override
    default int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        } else if ((bound & bound - 1) == 0) {
            return (int) ((long) bound * (long) this.next(31) >> 31);
        } else {
            int j;
            int k;

            do {
                j = this.next(31);
                k = j % bound;
            } while (j - k + (bound - 1) < 0);

            return k;
        }
    }

    @Override
    default long nextLong() {
        int i = this.next(32);
        int j = this.next(32);
        long k = (long) i << 32;

        return k + (long) j;
    }

    @Override
    default boolean nextBoolean() {
        return this.next(1) != 0;
    }

    @Override
    default float nextFloat() {
        return (float) this.next(24) * 5.9604645E-8F;
    }

    @Override
    default double nextDouble() {
        int i = this.next(26);
        int j = this.next(27);
        long k = ((long) i << 27) + (long) j;

        return (double) k * (double) 1.110223E-16F;
    }
}
