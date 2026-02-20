package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class ClampedNormalInt extends IntProvider {

    public static final MapCodec<ClampedNormalInt> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.FLOAT.fieldOf("mean").forGetter((clampednormalint) -> {
            return clampednormalint.mean;
        }), Codec.FLOAT.fieldOf("deviation").forGetter((clampednormalint) -> {
            return clampednormalint.deviation;
        }), Codec.INT.fieldOf("min_inclusive").forGetter((clampednormalint) -> {
            return clampednormalint.minInclusive;
        }), Codec.INT.fieldOf("max_inclusive").forGetter((clampednormalint) -> {
            return clampednormalint.maxInclusive;
        })).apply(instance, ClampedNormalInt::new);
    }).validate((clampednormalint) -> {
        return clampednormalint.maxInclusive < clampednormalint.minInclusive ? DataResult.error(() -> {
            return "Max must be larger than min: [" + clampednormalint.minInclusive + ", " + clampednormalint.maxInclusive + "]";
        }) : DataResult.success(clampednormalint);
    });
    private final float mean;
    private final float deviation;
    private final int minInclusive;
    private final int maxInclusive;

    public static ClampedNormalInt of(float mean, float deviation, int min_inclusive, int max_inclusive) {
        return new ClampedNormalInt(mean, deviation, min_inclusive, max_inclusive);
    }

    private ClampedNormalInt(float mean, float deviation, int minInclusive, int maxInclusive) {
        this.mean = mean;
        this.deviation = deviation;
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    @Override
    public int sample(RandomSource random) {
        return sample(random, this.mean, this.deviation, (float) this.minInclusive, (float) this.maxInclusive);
    }

    public static int sample(RandomSource random, float mean, float deviation, float min_inclusive, float max_inclusive) {
        return (int) Mth.clamp(Mth.normal(random, mean, deviation), min_inclusive, max_inclusive);
    }

    @Override
    public int getMinValue() {
        return this.minInclusive;
    }

    @Override
    public int getMaxValue() {
        return this.maxInclusive;
    }

    @Override
    public IntProviderType<?> getType() {
        return IntProviderType.CLAMPED_NORMAL;
    }

    public String toString() {
        return "normal(" + this.mean + ", " + this.deviation + ") in [" + this.minInclusive + "-" + this.maxInclusive + "]";
    }
}
