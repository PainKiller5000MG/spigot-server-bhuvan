package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.function.Function;

public record InclusiveRange<T extends Comparable<T>>(T minInclusive, T maxInclusive) {

    public static final Codec<InclusiveRange<Integer>> INT = codec(Codec.INT);

    public InclusiveRange {
        if (minInclusive.compareTo(maxInclusive) > 0) {
            throw new IllegalArgumentException("min_inclusive must be less than or equal to max_inclusive");
        }
    }

    public InclusiveRange(T value) {
        this(value, value);
    }

    public static <T extends Comparable<T>> Codec<InclusiveRange<T>> codec(Codec<T> elementCodec) {
        return ExtraCodecs.intervalCodec(elementCodec, "min_inclusive", "max_inclusive", InclusiveRange::create, InclusiveRange::minInclusive, InclusiveRange::maxInclusive);
    }

    public static <T extends Comparable<T>> Codec<InclusiveRange<T>> codec(Codec<T> elementCodec, T minAllowedInclusive, T maxAllowedInclusive) {
        return codec(elementCodec).validate((inclusiverange) -> {
            return inclusiverange.minInclusive().compareTo(minAllowedInclusive) < 0 ? DataResult.error(() -> {
                String s = String.valueOf(minAllowedInclusive);

                return "Range limit too low, expected at least " + s + " [" + String.valueOf(inclusiverange.minInclusive()) + "-" + String.valueOf(inclusiverange.maxInclusive()) + "]";
            }) : (inclusiverange.maxInclusive().compareTo(maxAllowedInclusive) > 0 ? DataResult.error(() -> {
                String s = String.valueOf(maxAllowedInclusive);

                return "Range limit too high, expected at most " + s + " [" + String.valueOf(inclusiverange.minInclusive()) + "-" + String.valueOf(inclusiverange.maxInclusive()) + "]";
            }) : DataResult.success(inclusiverange));
        });
    }

    public static <T extends Comparable<T>> DataResult<InclusiveRange<T>> create(T minInclusive, T maxInclusive) {
        return minInclusive.compareTo(maxInclusive) <= 0 ? DataResult.success(new InclusiveRange(minInclusive, maxInclusive)) : DataResult.error(() -> {
            return "min_inclusive must be less than or equal to max_inclusive";
        });
    }

    public <S extends Comparable<S>> InclusiveRange<S> map(Function<? super T, ? extends S> mapper) {
        return new InclusiveRange<S>((Comparable) mapper.apply(this.minInclusive), (Comparable) mapper.apply(this.maxInclusive));
    }

    public boolean isValueInRange(T value) {
        return value.compareTo(this.minInclusive) >= 0 && value.compareTo(this.maxInclusive) <= 0;
    }

    public boolean contains(InclusiveRange<T> subRange) {
        return subRange.minInclusive().compareTo(this.minInclusive) >= 0 && subRange.maxInclusive.compareTo(this.maxInclusive) <= 0;
    }

    public String toString() {
        String s = String.valueOf(this.minInclusive);

        return "[" + s + ", " + String.valueOf(this.maxInclusive) + "]";
    }
}
