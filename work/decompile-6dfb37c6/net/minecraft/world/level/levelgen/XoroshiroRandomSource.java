package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class XoroshiroRandomSource implements RandomSource {

    private static final float FLOAT_UNIT = 5.9604645E-8F;
    private static final double DOUBLE_UNIT = (double) 1.110223E-16F;
    public static final Codec<XoroshiroRandomSource> CODEC = Xoroshiro128PlusPlus.CODEC.xmap((xoroshiro128plusplus) -> {
        return new XoroshiroRandomSource(xoroshiro128plusplus);
    }, (xoroshirorandomsource) -> {
        return xoroshirorandomsource.randomNumberGenerator;
    });
    private Xoroshiro128PlusPlus randomNumberGenerator;
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public XoroshiroRandomSource(long seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
    }

    public XoroshiroRandomSource(RandomSupport.Seed128bit seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(seed);
    }

    public XoroshiroRandomSource(long seedLo, long seedHi) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(seedLo, seedHi);
    }

    private XoroshiroRandomSource(Xoroshiro128PlusPlus randomNumberGenerator) {
        this.randomNumberGenerator = randomNumberGenerator;
    }

    @Override
    public RandomSource fork() {
        return new XoroshiroRandomSource(this.randomNumberGenerator.nextLong(), this.randomNumberGenerator.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(this.randomNumberGenerator.nextLong(), this.randomNumberGenerator.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
        this.gaussianSource.reset();
    }

    @Override
    public int nextInt() {
        return (int) this.randomNumberGenerator.nextLong();
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        } else {
            long j = Integer.toUnsignedLong(this.nextInt());
            long k = j * (long) bound;
            long l = k & 4294967295L;

            if (l < (long) bound) {
                for (int i1 = Integer.remainderUnsigned(~bound + 1, bound); l < (long) i1; l = k & 4294967295L) {
                    j = Integer.toUnsignedLong(this.nextInt());
                    k = j * (long) bound;
                }
            }

            long j1 = k >> 32;

            return (int) j1;
        }
    }

    @Override
    public long nextLong() {
        return this.randomNumberGenerator.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return (this.randomNumberGenerator.nextLong() & 1L) != 0L;
    }

    @Override
    public float nextFloat() {
        return (float) this.nextBits(24) * 5.9604645E-8F;
    }

    @Override
    public double nextDouble() {
        return (double) this.nextBits(53) * (double) 1.110223E-16F;
    }

    @Override
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }

    @Override
    public void consumeCount(int rounds) {
        for (int j = 0; j < rounds; ++j) {
            this.randomNumberGenerator.nextLong();
        }

    }

    private long nextBits(int bits) {
        return this.randomNumberGenerator.nextLong() >>> 64 - bits;
    }

    public static class XoroshiroPositionalRandomFactory implements PositionalRandomFactory {

        private final long seedLo;
        private final long seedHi;

        public XoroshiroPositionalRandomFactory(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            long l = Mth.getSeed(x, y, z);
            long i1 = l ^ this.seedLo;

            return new XoroshiroRandomSource(i1, this.seedHi);
        }

        @Override
        public RandomSource fromHashOf(String name) {
            RandomSupport.Seed128bit randomsupport_seed128bit = RandomSupport.seedFromHashOf(name);

            return new XoroshiroRandomSource(randomsupport_seed128bit.xor(this.seedLo, this.seedHi));
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new XoroshiroRandomSource(seed ^ this.seedLo, seed ^ this.seedHi);
        }

        @VisibleForTesting
        @Override
        public void parityConfigString(StringBuilder sb) {
            sb.append("seedLo: ").append(this.seedLo).append(", seedHi: ").append(this.seedHi);
        }
    }
}
