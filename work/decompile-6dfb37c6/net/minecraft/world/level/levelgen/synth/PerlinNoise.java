package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.IntStream;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.jspecify.annotations.Nullable;

public class PerlinNoise {

    private static final int ROUND_OFF = 33554432;
    private final @Nullable ImprovedNoise[] noiseLevels;
    private final int firstOctave;
    private final DoubleList amplitudes;
    private final double lowestFreqValueFactor;
    private final double lowestFreqInputFactor;
    private final double maxValue;

    /** @deprecated */
    @Deprecated
    public static PerlinNoise createLegacyForBlendedNoise(RandomSource random, IntStream octaves) {
        return new PerlinNoise(random, makeAmplitudes(new IntRBTreeSet((Collection) octaves.boxed().collect(ImmutableList.toImmutableList()))), false);
    }

    /** @deprecated */
    @Deprecated
    public static PerlinNoise createLegacyForLegacyNetherBiome(RandomSource random, int firstOctave, DoubleList amplitudes) {
        return new PerlinNoise(random, Pair.of(firstOctave, amplitudes), false);
    }

    public static PerlinNoise create(RandomSource random, IntStream octaves) {
        return create(random, (List) octaves.boxed().collect(ImmutableList.toImmutableList()));
    }

    public static PerlinNoise create(RandomSource random, List<Integer> octaveSet) {
        return new PerlinNoise(random, makeAmplitudes(new IntRBTreeSet(octaveSet)), true);
    }

    public static PerlinNoise create(RandomSource random, int firstOctave, double firstAmplitude, double... amplitudes) {
        DoubleArrayList doublearraylist = new DoubleArrayList(amplitudes);

        doublearraylist.add(0, firstAmplitude);
        return new PerlinNoise(random, Pair.of(firstOctave, doublearraylist), true);
    }

    public static PerlinNoise create(RandomSource random, int firstOctave, DoubleList amplitudes) {
        return new PerlinNoise(random, Pair.of(firstOctave, amplitudes), true);
    }

    private static Pair<Integer, DoubleList> makeAmplitudes(IntSortedSet octaveSet) {
        if (octaveSet.isEmpty()) {
            throw new IllegalArgumentException("Need some octaves!");
        } else {
            int i = -octaveSet.firstInt();
            int j = octaveSet.lastInt();
            int k = i + j + 1;

            if (k < 1) {
                throw new IllegalArgumentException("Total number of octaves needs to be >= 1");
            } else {
                DoubleList doublelist = new DoubleArrayList(new double[k]);
                IntBidirectionalIterator intbidirectionaliterator = octaveSet.iterator();

                while (intbidirectionaliterator.hasNext()) {
                    int l = intbidirectionaliterator.nextInt();

                    doublelist.set(l + i, 1.0D);
                }

                return Pair.of(-i, doublelist);
            }
        }
    }

    protected PerlinNoise(RandomSource random, Pair<Integer, DoubleList> pair, boolean useNewInitialization) {
        this.firstOctave = (Integer) pair.getFirst();
        this.amplitudes = (DoubleList) pair.getSecond();
        int i = this.amplitudes.size();
        int j = -this.firstOctave;

        this.noiseLevels = new ImprovedNoise[i];
        if (useNewInitialization) {
            PositionalRandomFactory positionalrandomfactory = random.forkPositional();

            for (int k = 0; k < i; ++k) {
                if (this.amplitudes.getDouble(k) != 0.0D) {
                    int l = this.firstOctave + k;

                    this.noiseLevels[k] = new ImprovedNoise(positionalrandomfactory.fromHashOf("octave_" + l));
                }
            }
        } else {
            ImprovedNoise improvednoise = new ImprovedNoise(random);

            if (j >= 0 && j < i) {
                double d0 = this.amplitudes.getDouble(j);

                if (d0 != 0.0D) {
                    this.noiseLevels[j] = improvednoise;
                }
            }

            for (int i1 = j - 1; i1 >= 0; --i1) {
                if (i1 < i) {
                    double d1 = this.amplitudes.getDouble(i1);

                    if (d1 != 0.0D) {
                        this.noiseLevels[i1] = new ImprovedNoise(random);
                    } else {
                        skipOctave(random);
                    }
                } else {
                    skipOctave(random);
                }
            }

            if (Arrays.stream(this.noiseLevels).filter(Objects::nonNull).count() != this.amplitudes.stream().filter((odouble) -> {
                return odouble != 0.0D;
            }).count()) {
                throw new IllegalStateException("Failed to create correct number of noise levels for given non-zero amplitudes");
            }

            if (j < i - 1) {
                throw new IllegalArgumentException("Positive octaves are temporarily disabled");
            }
        }

        this.lowestFreqInputFactor = Math.pow(2.0D, (double) (-j));
        this.lowestFreqValueFactor = Math.pow(2.0D, (double) (i - 1)) / (Math.pow(2.0D, (double) i) - 1.0D);
        this.maxValue = this.edgeValue(2.0D);
    }

    protected double maxValue() {
        return this.maxValue;
    }

    private static void skipOctave(RandomSource random) {
        random.consumeCount(262);
    }

    public double getValue(double x, double y, double z) {
        return this.getValue(x, y, z, 0.0D, 0.0D, false);
    }

    /** @deprecated */
    @Deprecated
    public double getValue(double x, double y, double z, double yScale, double yFudge, boolean yFlatHack) {
        double d5 = 0.0D;
        double d6 = this.lowestFreqInputFactor;
        double d7 = this.lowestFreqValueFactor;

        for (int i = 0; i < this.noiseLevels.length; ++i) {
            ImprovedNoise improvednoise = this.noiseLevels[i];

            if (improvednoise != null) {
                double d8 = improvednoise.noise(wrap(x * d6), yFlatHack ? -improvednoise.yo : wrap(y * d6), wrap(z * d6), yScale * d6, yFudge * d6);

                d5 += this.amplitudes.getDouble(i) * d8 * d7;
            }

            d6 *= 2.0D;
            d7 /= 2.0D;
        }

        return d5;
    }

    public double maxBrokenValue(double yScale) {
        return this.edgeValue(yScale + 2.0D);
    }

    private double edgeValue(double noiseValue) {
        double d1 = 0.0D;
        double d2 = this.lowestFreqValueFactor;

        for (int i = 0; i < this.noiseLevels.length; ++i) {
            ImprovedNoise improvednoise = this.noiseLevels[i];

            if (improvednoise != null) {
                d1 += this.amplitudes.getDouble(i) * noiseValue * d2;
            }

            d2 /= 2.0D;
        }

        return d1;
    }

    public @Nullable ImprovedNoise getOctaveNoise(int i) {
        return this.noiseLevels[this.noiseLevels.length - 1 - i];
    }

    public static double wrap(double x) {
        return x - (double) Mth.lfloor(x / 3.3554432E7D + 0.5D) * 3.3554432E7D;
    }

    protected int firstOctave() {
        return this.firstOctave;
    }

    protected DoubleList amplitudes() {
        return this.amplitudes;
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder sb) {
        sb.append("PerlinNoise{");
        List<String> list = this.amplitudes.stream().map((odouble) -> {
            return String.format(Locale.ROOT, "%.2f", odouble);
        }).toList();

        sb.append("first octave: ").append(this.firstOctave).append(", amplitudes: ").append(list).append(", noise levels: [");

        for (int i = 0; i < this.noiseLevels.length; ++i) {
            sb.append(i).append(": ");
            ImprovedNoise improvednoise = this.noiseLevels[i];

            if (improvednoise == null) {
                sb.append("null");
            } else {
                improvednoise.parityConfigString(sb);
            }

            sb.append(", ");
        }

        sb.append("]");
        sb.append("}");
    }
}
