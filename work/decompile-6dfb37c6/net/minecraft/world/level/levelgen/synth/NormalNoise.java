package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleListIterator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;

public class NormalNoise {

    private static final double INPUT_FACTOR = 1.0181268882175227D;
    private static final double TARGET_DEVIATION = 0.3333333333333333D;
    private final double valueFactor;
    private final PerlinNoise first;
    private final PerlinNoise second;
    private final double maxValue;
    private final NormalNoise.NoiseParameters parameters;

    /** @deprecated */
    @Deprecated
    public static NormalNoise createLegacyNetherBiome(RandomSource random, NormalNoise.NoiseParameters parameters) {
        return new NormalNoise(random, parameters, false);
    }

    public static NormalNoise create(RandomSource random, int firstOctave, double... amplitudes) {
        return create(random, new NormalNoise.NoiseParameters(firstOctave, new DoubleArrayList(amplitudes)));
    }

    public static NormalNoise create(RandomSource random, NormalNoise.NoiseParameters parameters) {
        return new NormalNoise(random, parameters, true);
    }

    private NormalNoise(RandomSource random, NormalNoise.NoiseParameters parameters, boolean useNewInitialization) {
        int i = parameters.firstOctave;
        DoubleList doublelist = parameters.amplitudes;

        this.parameters = parameters;
        if (useNewInitialization) {
            this.first = PerlinNoise.create(random, i, doublelist);
            this.second = PerlinNoise.create(random, i, doublelist);
        } else {
            this.first = PerlinNoise.createLegacyForLegacyNetherBiome(random, i, doublelist);
            this.second = PerlinNoise.createLegacyForLegacyNetherBiome(random, i, doublelist);
        }

        int j = Integer.MAX_VALUE;
        int k = Integer.MIN_VALUE;
        DoubleListIterator doublelistiterator = doublelist.iterator();

        while (doublelistiterator.hasNext()) {
            int l = doublelistiterator.nextIndex();
            double d0 = doublelistiterator.nextDouble();

            if (d0 != 0.0D) {
                j = Math.min(j, l);
                k = Math.max(k, l);
            }
        }

        this.valueFactor = 0.16666666666666666D / expectedDeviation(k - j);
        this.maxValue = (this.first.maxValue() + this.second.maxValue()) * this.valueFactor;
    }

    public double maxValue() {
        return this.maxValue;
    }

    private static double expectedDeviation(int octaveSpan) {
        return 0.1D * (1.0D + 1.0D / (double) (octaveSpan + 1));
    }

    public double getValue(double x, double y, double z) {
        double d3 = x * 1.0181268882175227D;
        double d4 = y * 1.0181268882175227D;
        double d5 = z * 1.0181268882175227D;

        return (this.first.getValue(x, y, z) + this.second.getValue(d3, d4, d5)) * this.valueFactor;
    }

    public NormalNoise.NoiseParameters parameters() {
        return this.parameters;
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder sb) {
        sb.append("NormalNoise {");
        sb.append("first: ");
        this.first.parityConfigString(sb);
        sb.append(", second: ");
        this.second.parityConfigString(sb);
        sb.append("}");
    }

    public static record NoiseParameters(int firstOctave, DoubleList amplitudes) {

        public static final Codec<NormalNoise.NoiseParameters> DIRECT_CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.INT.fieldOf("firstOctave").forGetter(NormalNoise.NoiseParameters::firstOctave), Codec.DOUBLE.listOf().fieldOf("amplitudes").forGetter(NormalNoise.NoiseParameters::amplitudes)).apply(instance, NormalNoise.NoiseParameters::new);
        });
        public static final Codec<Holder<NormalNoise.NoiseParameters>> CODEC = RegistryFileCodec.<Holder<NormalNoise.NoiseParameters>>create(Registries.NOISE, NormalNoise.NoiseParameters.DIRECT_CODEC);

        public NoiseParameters(int firstOctave, List<Double> amplitudes) {
            this(firstOctave, new DoubleArrayList(amplitudes));
        }

        public NoiseParameters(int firstOctave, double firstAmplitude, double... amplitudes) {
            this(firstOctave, (DoubleList) Util.make(new DoubleArrayList(amplitudes), (doublearraylist) -> {
                doublearraylist.add(0, firstAmplitude);
            }));
        }
    }
}
