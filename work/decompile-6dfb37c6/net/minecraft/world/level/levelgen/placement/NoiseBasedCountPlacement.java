package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

public class NoiseBasedCountPlacement extends RepeatingPlacement {

    public static final MapCodec<NoiseBasedCountPlacement> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.INT.fieldOf("noise_to_count_ratio").forGetter((noisebasedcountplacement) -> {
            return noisebasedcountplacement.noiseToCountRatio;
        }), Codec.DOUBLE.fieldOf("noise_factor").forGetter((noisebasedcountplacement) -> {
            return noisebasedcountplacement.noiseFactor;
        }), Codec.DOUBLE.fieldOf("noise_offset").orElse(0.0D).forGetter((noisebasedcountplacement) -> {
            return noisebasedcountplacement.noiseOffset;
        })).apply(instance, NoiseBasedCountPlacement::new);
    });
    private final int noiseToCountRatio;
    private final double noiseFactor;
    private final double noiseOffset;

    private NoiseBasedCountPlacement(int noiseToCountRatio, double noiseFactor, double noiseOffset) {
        this.noiseToCountRatio = noiseToCountRatio;
        this.noiseFactor = noiseFactor;
        this.noiseOffset = noiseOffset;
    }

    public static NoiseBasedCountPlacement of(int noiseToCountRatio, double noiseFactor, double noiseOffset) {
        return new NoiseBasedCountPlacement(noiseToCountRatio, noiseFactor, noiseOffset);
    }

    @Override
    protected int count(RandomSource random, BlockPos origin) {
        double d0 = Biome.BIOME_INFO_NOISE.getValue((double) origin.getX() / this.noiseFactor, (double) origin.getZ() / this.noiseFactor, false);

        return (int) Math.ceil((d0 + this.noiseOffset) * (double) this.noiseToCountRatio);
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.NOISE_BASED_COUNT;
    }
}
