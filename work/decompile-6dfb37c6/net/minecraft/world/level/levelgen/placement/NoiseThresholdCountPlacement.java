package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

public class NoiseThresholdCountPlacement extends RepeatingPlacement {

    public static final MapCodec<NoiseThresholdCountPlacement> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.DOUBLE.fieldOf("noise_level").forGetter((noisethresholdcountplacement) -> {
            return noisethresholdcountplacement.noiseLevel;
        }), Codec.INT.fieldOf("below_noise").forGetter((noisethresholdcountplacement) -> {
            return noisethresholdcountplacement.belowNoise;
        }), Codec.INT.fieldOf("above_noise").forGetter((noisethresholdcountplacement) -> {
            return noisethresholdcountplacement.aboveNoise;
        })).apply(instance, NoiseThresholdCountPlacement::new);
    });
    private final double noiseLevel;
    private final int belowNoise;
    private final int aboveNoise;

    private NoiseThresholdCountPlacement(double noiseLevel, int belowNoise, int aboveNoise) {
        this.noiseLevel = noiseLevel;
        this.belowNoise = belowNoise;
        this.aboveNoise = aboveNoise;
    }

    public static NoiseThresholdCountPlacement of(double noiseLevel, int belowNoise, int aboveNoise) {
        return new NoiseThresholdCountPlacement(noiseLevel, belowNoise, aboveNoise);
    }

    @Override
    protected int count(RandomSource random, BlockPos origin) {
        double d0 = Biome.BIOME_INFO_NOISE.getValue((double) origin.getX() / 200.0D, (double) origin.getZ() / 200.0D, false);

        return d0 < this.noiseLevel ? this.belowNoise : this.aboveNoise;
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.NOISE_THRESHOLD_COUNT;
    }
}
