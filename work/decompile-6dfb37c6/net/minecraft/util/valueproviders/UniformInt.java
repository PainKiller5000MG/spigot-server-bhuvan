package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class UniformInt extends IntProvider {

    public static final MapCodec<UniformInt> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.INT.fieldOf("min_inclusive").forGetter((uniformint) -> {
            return uniformint.minInclusive;
        }), Codec.INT.fieldOf("max_inclusive").forGetter((uniformint) -> {
            return uniformint.maxInclusive;
        })).apply(instance, UniformInt::new);
    }).validate((uniformint) -> {
        return uniformint.maxInclusive < uniformint.minInclusive ? DataResult.error(() -> {
            return "Max must be at least min, min_inclusive: " + uniformint.minInclusive + ", max_inclusive: " + uniformint.maxInclusive;
        }) : DataResult.success(uniformint);
    });
    private final int minInclusive;
    private final int maxInclusive;

    private UniformInt(int minInclusive, int maxInclusive) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public static UniformInt of(int minInclusive, int maxInclusive) {
        return new UniformInt(minInclusive, maxInclusive);
    }

    @Override
    public int sample(RandomSource random) {
        return Mth.randomBetweenInclusive(random, this.minInclusive, this.maxInclusive);
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
        return IntProviderType.UNIFORM;
    }

    public String toString() {
        return "[" + this.minInclusive + "-" + this.maxInclusive + "]";
    }
}
