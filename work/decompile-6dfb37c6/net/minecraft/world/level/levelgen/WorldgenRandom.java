package net.minecraft.world.level.levelgen;

import java.util.function.LongFunction;
import net.minecraft.util.RandomSource;

public class WorldgenRandom extends LegacyRandomSource {

    private final RandomSource randomSource;
    private int count;

    public WorldgenRandom(RandomSource randomSource) {
        super(0L);
        this.randomSource = randomSource;
    }

    public int getCount() {
        return this.count;
    }

    @Override
    public RandomSource fork() {
        return this.randomSource.fork();
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return this.randomSource.forkPositional();
    }

    @Override
    public int next(int bits) {
        ++this.count;
        RandomSource randomsource = this.randomSource;

        if (randomsource instanceof LegacyRandomSource legacyrandomsource) {
            return legacyrandomsource.next(bits);
        } else {
            return (int) (this.randomSource.nextLong() >>> 64 - bits);
        }
    }

    @Override
    public synchronized void setSeed(long seed) {
        if (this.randomSource != null) {
            this.randomSource.setSeed(seed);
        }
    }

    public long setDecorationSeed(long seed, int chunkX, int chunkZ) {
        this.setSeed(seed);
        long l = this.nextLong() | 1L;
        long i1 = this.nextLong() | 1L;
        long j1 = (long) chunkX * l + (long) chunkZ * i1 ^ seed;

        this.setSeed(j1);
        return j1;
    }

    public void setFeatureSeed(long seed, int index, int step) {
        long l = seed + (long) index + (long) (10000 * step);

        this.setSeed(l);
    }

    public void setLargeFeatureSeed(long seed, int chunkX, int chunkZ) {
        this.setSeed(seed);
        long l = this.nextLong();
        long i1 = this.nextLong();
        long j1 = (long) chunkX * l ^ (long) chunkZ * i1 ^ seed;

        this.setSeed(j1);
    }

    public void setLargeFeatureWithSalt(long seed, int x, int z, int blend) {
        long i1 = (long) x * 341873128712L + (long) z * 132897987541L + seed + (long) blend;

        this.setSeed(i1);
    }

    public static RandomSource seedSlimeChunk(int x, int z, long seed, long salt) {
        return RandomSource.create(seed + (long) (x * x * 4987142) + (long) (x * 5947611) + (long) (z * z) * 4392871L + (long) (z * 389711) ^ salt);
    }

    public static enum Algorithm {

        LEGACY(LegacyRandomSource::new), XOROSHIRO(XoroshiroRandomSource::new);

        private final LongFunction<RandomSource> constructor;

        private Algorithm(LongFunction<RandomSource> constructor) {
            this.constructor = constructor;
        }

        public RandomSource newInstance(long seed) {
            return (RandomSource) this.constructor.apply(seed);
        }
    }
}
