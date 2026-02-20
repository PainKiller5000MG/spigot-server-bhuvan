package net.minecraft.world.level.levelgen.synth;

import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.jspecify.annotations.Nullable;

public class PerlinSimplexNoise {

    private final @Nullable SimplexNoise[] noiseLevels;
    private final double highestFreqValueFactor;
    private final double highestFreqInputFactor;

    public PerlinSimplexNoise(RandomSource random, List<Integer> octaveSet) {
        this(random, new IntRBTreeSet(octaveSet));
    }

    private PerlinSimplexNoise(RandomSource random, IntSortedSet octaveSet) {
        if (octaveSet.isEmpty()) {
            throw new IllegalArgumentException("Need some octaves!");
        } else {
            int i = -octaveSet.firstInt();
            int j = octaveSet.lastInt();
            int k = i + j + 1;

            if (k < 1) {
                throw new IllegalArgumentException("Total number of octaves needs to be >= 1");
            } else {
                SimplexNoise simplexnoise = new SimplexNoise(random);
                int l = j;

                this.noiseLevels = new SimplexNoise[k];
                if (j >= 0 && j < k && octaveSet.contains(0)) {
                    this.noiseLevels[j] = simplexnoise;
                }

                for (int i1 = j + 1; i1 < k; ++i1) {
                    if (i1 >= 0 && octaveSet.contains(l - i1)) {
                        this.noiseLevels[i1] = new SimplexNoise(random);
                    } else {
                        random.consumeCount(262);
                    }
                }

                if (j > 0) {
                    long j1 = (long) (simplexnoise.getValue(simplexnoise.xo, simplexnoise.yo, simplexnoise.zo) * (double) Long.MAX_VALUE);
                    RandomSource randomsource1 = new WorldgenRandom(new LegacyRandomSource(j1));

                    for (int k1 = l - 1; k1 >= 0; --k1) {
                        if (k1 < k && octaveSet.contains(l - k1)) {
                            this.noiseLevels[k1] = new SimplexNoise(randomsource1);
                        } else {
                            randomsource1.consumeCount(262);
                        }
                    }
                }

                this.highestFreqInputFactor = Math.pow(2.0D, (double) j);
                this.highestFreqValueFactor = 1.0D / (Math.pow(2.0D, (double) k) - 1.0D);
            }
        }
    }

    public double getValue(double x, double y, boolean useNoiseStart) {
        double d2 = 0.0D;
        double d3 = this.highestFreqInputFactor;
        double d4 = this.highestFreqValueFactor;

        for (SimplexNoise simplexnoise : this.noiseLevels) {
            if (simplexnoise != null) {
                d2 += simplexnoise.getValue(x * d3 + (useNoiseStart ? simplexnoise.xo : 0.0D), y * d3 + (useNoiseStart ? simplexnoise.yo : 0.0D)) * d4;
            }

            d3 /= 2.0D;
            d4 *= 2.0D;
        }

        return d2;
    }
}
